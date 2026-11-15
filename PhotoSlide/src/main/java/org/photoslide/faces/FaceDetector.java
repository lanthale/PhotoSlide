package org.photoslide.faces;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.objdetect.FaceDetectorYN;
import org.photoslide.datamodel.FaceRegion;
import org.photoslide.datamodel.Image16;

/**
 * Finds faces with YuNet, run by OpenCV's FaceDetectorYN. The image is turned by the PhotoSlide rotation
 * first (the model only finds upright faces) and the results are turned back,
 * so they are relative to the source image. Not thread safe: used on the
 * thread of the FaceService only.
 */
final class FaceDetector implements AutoCloseable {

    /** The model takes 640 x 640 pixels (fixed): the image is scaled to fit and padded. */
    static final int INPUT_SIDE = 640;
    /** Photos larger than this (long side in pixels) are also searched in tiles. */
    private static final int TILE_SIDE = 1600;
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

    private final FaceDetectorYN yunet;

    FaceDetector() throws IOException {
        FaceModels.loadOpenCv();
        yunet = FaceDetectorYN.create(FaceModels.path(FaceModels.YUNET).toString(), "",
                new Size(INPUT_SIDE, INPUT_SIDE), SCORE_THRESHOLD, NMS_THRESHOLD, 5000);
    }

    /**
     * Faces in the whole image. quarters = PhotoSlide rotation (clockwise).
     * Large photos are also searched in overlapping tiles: scaled to 640
     * pixels, the faces of a group photo would be too small for the model.
     */
    List<Detection> detect(Image16 source, int quarters) {
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
    List<Detection> detect(Image16 source, int quarters, FaceRegion part) {
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

    private List<Detection> detect(Rgb8 unturned, int quarters) {
        Rgb8 img = unturned.rotated(quarters);
        List<Detection> found = new ArrayList<>();
        // top left in a black 640 x 640 image, like the blob OpenCV pads
        Mat input = Mat.zeros(INPUT_SIDE, INPUT_SIDE, CvType.CV_8UC3);
        Mat pixels = img.toMat();
        Mat target = input.submat(new Rect(0, 0, img.width(), img.height()));
        Mat faces = new Mat();
        try {
            pixels.copyTo(target);
            yunet.detect(input, faces);
            float[] row = new float[15];
            for (int r = 0; r < faces.rows(); r++) {
                // x, y, w, h, right eye, left eye, nose tip, right and left mouth corner, score
                faces.get(r, 0, row);
                double[] lm = new double[10];
                for (int k = 0; k < 10; k += 2) {
                    lm[k] = row[4 + k] / img.width();
                    lm[k + 1] = row[5 + k] / img.height();
                }
                FaceRegion shown = new FaceRegion(row[0] / img.width(), row[1] / img.height(),
                        row[2] / img.width(), row[3] / img.height(), null, false);
                found.add(new Detection(shown, lm, row[14]));
            }
        } finally {
            target.release();
            input.release();
            pixels.release();
            faces.release();
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
        // FaceDetectorYN has no close: its native object is freed by the garbage collector
    }
}
