package org.photoslide.faces;

import java.io.IOException;
import org.opencv.core.Mat;

/**
 * Computes the feature vector of an aligned face (see FaceAligner). The
 * cosine similarity of two vectors tells whether the faces are the same
 * person. Implementations are not thread safe.
 */
interface FaceEmbedder extends AutoCloseable {

    EmbedderType type();

    /**
     * @param aligned the face aligned onto the 112 x 112 ArcFace template, 8
     * bit BGR
     * @return the feature vector, length 1
     */
    float[] embed(Mat aligned) throws IOException;

    @Override
    void close();

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

    /** Cosine similarity of two normalized vectors (0 if they come from different models). */
    static double similarity(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }
}
