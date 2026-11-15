package org.photoslide.faces;

import org.opencv.core.Mat;

/** SFace run by OpenCV's FaceRecognizerSF (the one of the FaceAligner): 128 values. */
final class SFaceEmbedder implements FaceEmbedder {

    private final FaceAligner aligner;

    SFaceEmbedder(FaceAligner aligner) {
        this.aligner = aligner;
    }

    @Override
    public EmbedderType type() {
        return EmbedderType.SFACE;
    }

    @Override
    public float[] embed(Mat aligned) {
        Mat feature = new Mat();
        try {
            aligner.recognizer().feature(aligned, feature);
            float[] v = new float[(int) feature.total()];
            feature.get(0, 0, v);
            return FaceEmbedder.normalize(v);
        } finally {
            feature.release();
        }
    }

    @Override
    public void close() {
        // the recognizer belongs to the aligner
    }
}
