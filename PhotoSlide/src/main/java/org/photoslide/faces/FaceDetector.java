package org.photoslide.faces;

import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.photoslide.datamodel.FaceRegion;
import org.photoslide.datamodel.Image16;

/**
 * Finds faces with YuNet. The image is turned by the PhotoSlide rotation
 * first (the model only finds upright faces) and the results are turned back,
 * so they are relative to the source image. Not thread safe: used on the
 * thread of the FaceService only.
 */
final class FaceDetector implements AutoCloseable {

    /** The model takes 640 x 640 pixels: the image is scaled to fit and padded. */
    static final int INPUT_SIDE = 640;
    /** Photos larger than this (long side in pixels) are also searched in tiles. */
    private static final int TILE_SIDE = 1600;
    private static final int[] STRIDES = {8, 16, 32};
    private static final float SCORE_THRESHOLD = 0.75f;
    private static final float NMS_THRESHOLD = 0.3f;

    /**
     * A face found in the image. Rectangle and landmarks are normalized and
     * relative to the source; landmarks: right eye, left eye, nose tip, right
     * and left mouth corner (x, y each, as seen in the image: the first eye is
     * the one on the left side).
     */
    record Detection(FaceRegion region, double[] landmarks, float score) {
    }

    private final ZooModel<NDList, NDList> model;
    private final Predictor<NDList, NDList> predictor;

    FaceDetector() throws IOException {
        model = FaceModels.load(FaceModels.DETECTOR);
        predictor = model.newPredictor();
    }

    /**
     * Faces in the whole image. quarters = PhotoSlide rotation (clockwise).
     * Large photos are also searched in overlapping tiles: scaled to 640
     * pixels, the faces of a group photo would be too small for the model.
     */
    List<Detection> detect(Image16 source, int quarters) throws TranslateException {
        List<Detection> all = new ArrayList<>(detect(Rgb8.of(source, INPUT_SIDE), quarters));
        int tiles = (int) Math.min(3, Math.ceil(Math.max(source.width(), source.height()) / (double) TILE_SIDE));
        if (tiles >= 2) {
            double step = 1.0 / tiles;
            double overlap = step * 0.2;
            for (int ty = 0; ty < tiles; ty++) {
                for (int tx = 0; tx < tiles; tx++) {
                    FaceRegion tile = new FaceRegion(tx * step - overlap, ty * step - overlap,
                            step + 2 * overlap, step + 2 * overlap, null, false);
                    all.addAll(detect(source, quarters, tile));
                }
            }
        }
        return nms(all);
    }

    /**
     * Faces in a part of the image, e.g. to find the landmarks of a face whose
     * rectangle is known. Results are relative to the whole source.
     */
    List<Detection> detect(Image16 source, int quarters, FaceRegion part) throws TranslateException {
        int x = (int) Math.floor(part.x() * source.width());
        int y = (int) Math.floor(part.y() * source.height());
        int w = (int) Math.ceil(part.w() * source.width());
        int h = (int) Math.ceil(part.h() * source.height());
        Image16 crop = source.crop(x, y, w, h);
        // crop() clips: the real rectangle of the crop
        int cx = Math.max(0, x);
        int cy = Math.max(0, y);
        List<Detection> result = new ArrayList<>();
        for (Detection d : detect(Rgb8.of(crop, INPUT_SIDE), quarters)) {
            FaceRegion r = d.region();
            double sx = (double) crop.width() / source.width();
            double sy = (double) crop.height() / source.height();
            double ox = (double) cx / source.width();
            double oy = (double) cy / source.height();
            double[] lm = d.landmarks().clone();
            for (int i = 0; i < lm.length; i += 2) {
                lm[i] = ox + lm[i] * sx;
                lm[i + 1] = oy + lm[i + 1] * sy;
            }
            result.add(new Detection(r.withBounds(ox + r.x() * sx, oy + r.y() * sy, r.w() * sx, r.h() * sy), lm,
                    d.score()));
        }
        return result;
    }

    private List<Detection> detect(Rgb8 unturned, int quarters) throws TranslateException {
        Rgb8 img = unturned.rotated(quarters);
        int pw = INPUT_SIDE;
        int ph = INPUT_SIDE;
        // planar BGR, 0..255, zero padded at the right and bottom (like OpenCV's blobFromImage)
        float[] data = new float[3 * pw * ph];
        int plane = pw * ph;
        int[] px = img.pixels();
        for (int y = 0; y < img.height(); y++) {
            for (int x = 0; x < img.width(); x++) {
                int rgb = px[y * img.width() + x];
                int i = y * pw + x;
                data[i] = rgb & 0xFF;
                data[plane + i] = (rgb >> 8) & 0xFF;
                data[2 * plane + i] = (rgb >> 16) & 0xFF;
            }
        }
        List<Detection> found = new ArrayList<>();
        try (NDManager manager = model.getNDManager().newSubManager()) {
            NDList out = predictor.predict(new NDList(manager.create(data, new Shape(1, 3, ph, pw))));
            for (int stride : STRIDES) {
                float[] cls = values(out, "cls_" + stride);
                float[] obj = values(out, "obj_" + stride);
                float[] box = values(out, "bbox_" + stride);
                float[] kps = values(out, "kps_" + stride);
                int cols = pw / stride;
                int rows = ph / stride;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        int idx = r * cols + c;
                        float score = (float) Math.sqrt(clamp(cls[idx]) * clamp(obj[idx]));
                        if (score < SCORE_THRESHOLD) {
                            continue;
                        }
                        double cx = (c + box[idx * 4]) * stride;
                        double cy = (r + box[idx * 4 + 1]) * stride;
                        double bw = Math.exp(box[idx * 4 + 2]) * stride;
                        double bh = Math.exp(box[idx * 4 + 3]) * stride;
                        double[] lm = new double[10];
                        for (int k = 0; k < 5; k++) {
                            lm[2 * k] = (kps[idx * 10 + 2 * k] + c) * stride / img.width();
                            lm[2 * k + 1] = (kps[idx * 10 + 2 * k + 1] + r) * stride / img.height();
                        }
                        FaceRegion shown = new FaceRegion((cx - bw / 2) / img.width(), (cy - bh / 2) / img.height(),
                                bw / img.width(), bh / img.height(), null, false);
                        found.add(new Detection(shown, lm, score));
                    }
                }
            }
        }
        List<Detection> result = new ArrayList<>();
        for (Detection d : nms(found)) {
            double[] lm = d.landmarks();
            for (int i = 0; i < lm.length; i += 2) {
                double[] p = unturn(lm[i], lm[i + 1], quarters);
                lm[i] = p[0];
                lm[i + 1] = p[1];
            }
            result.add(new Detection(clip(d.region().rotated(-quarters)), lm, d.score()));
        }
        return result;
    }

    private static float[] values(NDList out, String name) {
        NDArray a = out.get(name);
        if (a == null) {
            throw new IllegalStateException("YuNet output " + name + " missing");
        }
        return a.toFloatArray();
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** Point of the turned image -> source (both normalized). */
    static double[] unturn(double x, double y, int quarters) {
        return switch (Math.floorMod(quarters, 4)) {
            case 1 -> new double[]{y, 1 - x};
            case 2 -> new double[]{1 - x, 1 - y};
            case 3 -> new double[]{1 - y, x};
            default -> new double[]{x, y};
        };
    }

    private static FaceRegion clip(FaceRegion r) {
        double x0 = Math.max(0, r.x());
        double y0 = Math.max(0, r.y());
        double x1 = Math.min(1, r.x() + r.w());
        double y1 = Math.min(1, r.y() + r.h());
        return r.withBounds(x0, y0, Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    private static List<Detection> nms(List<Detection> found) {
        found.sort(Comparator.comparingDouble(Detection::score).reversed());
        List<Detection> kept = new ArrayList<>();
        for (Detection d : found) {
            boolean overlaps = false;
            for (Detection k : kept) {
                if (iou(d.region(), k.region()) > NMS_THRESHOLD || contained(d.region(), k.region()) > 0.6) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                kept.add(d);
            }
        }
        return kept;
    }

    /** Share of the smaller rectangle inside the other (a face cut by a tile edge). */
    private static double contained(FaceRegion a, FaceRegion b) {
        double ix = Math.max(0, Math.min(a.x() + a.w(), b.x() + b.w()) - Math.max(a.x(), b.x()));
        double iy = Math.max(0, Math.min(a.y() + a.h(), b.y() + b.h()) - Math.max(a.y(), b.y()));
        double smaller = Math.min(a.w() * a.h(), b.w() * b.h());
        return smaller <= 0 ? 0 : ix * iy / smaller;
    }

    /** Intersection over union of two rectangles. */
    static double iou(FaceRegion a, FaceRegion b) {
        double ix = Math.max(0, Math.min(a.x() + a.w(), b.x() + b.w()) - Math.max(a.x(), b.x()));
        double iy = Math.max(0, Math.min(a.y() + a.h(), b.y() + b.h()) - Math.max(a.y(), b.y()));
        double inter = ix * iy;
        double union = a.w() * a.h() + b.w() * b.h() - inter;
        return union <= 0 ? 0 : inter / union;
    }

    @Override
    public void close() {
        predictor.close();
        model.close();
    }
}
