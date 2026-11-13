package org.photoslide.faces;

import ai.djl.inference.Predictor;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;
import java.io.IOException;
import org.photoslide.datamodel.FaceRegion;
import org.photoslide.datamodel.Image16;

/**
 * Computes the feature vector of a face with SFace. The face is aligned by
 * its 5 landmarks onto the standard 112 x 112 face (similarity transform,
 * like OpenCV's FaceRecognizerSF.alignCrop). Not thread safe.
 */
final class FaceEmbedder implements AutoCloseable {

    private static final int SIZE = 112;
    /** Standard positions of the landmarks in the 112 x 112 face (ArcFace). */
    private static final double[] TEMPLATE = {
        38.2946, 51.6963, 73.5318, 51.5014, 56.0252, 71.7366, 41.5493, 92.3655, 70.7299, 92.2041};

    private final ZooModel<NDList, NDList> model;
    private final Predictor<NDList, NDList> predictor;

    FaceEmbedder() throws IOException {
        model = FaceModels.load(FaceModels.RECOGNIZER);
        predictor = model.newPredictor();
    }

    /**
     * @param landmarks 5 points, normalized and relative to the source (see
     * FaceDetector.Detection)
     * @return the feature vector, length 1
     */
    float[] embed(Image16 source, double[] landmarks) throws TranslateException {
        int sw = source.width();
        int sh = source.height();
        double[] p = new double[10];
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 10; i += 2) {
            p[i] = landmarks[i] * sw;
            p[i + 1] = landmarks[i + 1] * sh;
            minX = Math.min(minX, p[i]);
            maxX = Math.max(maxX, p[i]);
            minY = Math.min(minY, p[i + 1]);
            maxY = Math.max(maxY, p[i + 1]);
        }
        // only the area around the face is converted, in a size near the 112 pixels of the model
        double span = Math.max(Math.max(maxX - minX, maxY - minY), 4);
        int margin = (int) Math.ceil(span * 1.5);
        int x0 = Math.max(0, (int) Math.floor(minX) - margin);
        int y0 = Math.max(0, (int) Math.floor(minY) - margin);
        int x1 = Math.min(sw, (int) Math.ceil(maxX) + margin);
        int y1 = Math.min(sh, (int) Math.ceil(maxY) + margin);
        Image16 crop = source.crop(x0, y0, x1 - x0, y1 - y0);
        // 4 x the face span is about 3 x the 112 pixel crop: enough for bilinear sampling
        int maxSide = (int) Math.min(Math.max(crop.width(), crop.height()), Math.max(SIZE * 3, 1));
        Rgb8 img = Rgb8.of(crop, maxSide);
        double k = (double) img.width() / crop.width();
        for (int i = 0; i < 10; i += 2) {
            p[i] = (p[i] - x0) * k;
            p[i + 1] = (p[i + 1] - y0) * k;
        }
        double[] t = similarity(p, TEMPLATE);    // image -> template: x' = a x - b y + tx, y' = b x + a y + ty
        double a = t[0];
        double b = t[1];
        double tx = t[2];
        double ty = t[3];
        double det = a * a + b * b;
        // planar RGB, 0..255 (SFace takes RGB without normalization)
        float[] data = new float[3 * SIZE * SIZE];
        int plane = SIZE * SIZE;
        for (int v = 0; v < SIZE; v++) {
            for (int u = 0; u < SIZE; u++) {
                double du = u - tx;
                double dv = v - ty;
                double x = (a * du + b * dv) / det;
                double y = (-b * du + a * dv) / det;
                int i = v * SIZE + u;
                data[i] = img.sample(x, y, 0);
                data[plane + i] = img.sample(x, y, 1);
                data[2 * plane + i] = img.sample(x, y, 2);
            }
        }
        float[] feature;
        try (NDManager manager = model.getNDManager().newSubManager()) {
            NDList out = predictor.predict(new NDList(manager.create(data, new Shape(1, 3, SIZE, SIZE))));
            feature = out.singletonOrThrow().toFloatArray();
        }
        return normalize(feature);
    }

    /**
     * Landmarks for a face of which only the rectangle is known (drawn by
     * hand, or no face found again in it): the standard positions in the
     * rectangle, upright in the image as shown.
     */
    static double[] guessLandmarks(FaceRegion r, int quarters) {
        double[] lm = new double[10];
        FaceRegion shown = r.rotated(quarters);
        for (int i = 0; i < 10; i += 2) {
            double sx = shown.x() + TEMPLATE[i] / SIZE * shown.w();
            double sy = shown.y() + TEMPLATE[i + 1] / SIZE * shown.h();
            double[] q = FaceDetector.unturn(sx, sy, quarters);
            lm[i] = q[0];
            lm[i + 1] = q[1];
        }
        return lm;
    }

    /** Least squares similarity transform (scale, rotation, shift) from src to dst points. */
    static double[] similarity(double[] src, double[] dst) {
        int n = src.length / 2;
        double sx = 0;
        double sy = 0;
        double dx = 0;
        double dy = 0;
        for (int i = 0; i < n; i++) {
            sx += src[2 * i];
            sy += src[2 * i + 1];
            dx += dst[2 * i];
            dy += dst[2 * i + 1];
        }
        sx /= n;
        sy /= n;
        dx /= n;
        dy /= n;
        double num1 = 0;
        double num2 = 0;
        double den = 0;
        for (int i = 0; i < n; i++) {
            double px = src[2 * i] - sx;
            double py = src[2 * i + 1] - sy;
            double qx = dst[2 * i] - dx;
            double qy = dst[2 * i + 1] - dy;
            num1 += px * qx + py * qy;
            num2 += px * qy - py * qx;
            den += px * px + py * py;
        }
        double a = num1 / den;
        double b = num2 / den;
        double tx = dx - (a * sx - b * sy);
        double ty = dy - (b * sx + a * sy);
        return new double[]{a, b, tx, ty};
    }

    static float[] normalize(float[] v) {
        double sum = 0;
        for (float f : v) {
            sum += f * f;
        }
        double len = Math.sqrt(sum);
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / (len == 0 ? 1 : len));
        }
        return out;
    }

    /** Cosine similarity of two normalized vectors. */
    static double similarity(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            s += a[i] * b[i];
        }
        return s;
    }

    @Override
    public void close() {
        predictor.close();
        model.close();
    }
}
