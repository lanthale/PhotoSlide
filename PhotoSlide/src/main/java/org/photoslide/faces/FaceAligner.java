package org.photoslide.faces;

import java.io.IOException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.objdetect.FaceRecognizerSF;
import org.photoslide.datamodel.FaceRegion;
import org.photoslide.datamodel.Image16;

/**
 * Aligns a face by its 5 landmarks onto the standard 112 x 112 face of
 * ArcFace with OpenCV's FaceRecognizerSF.alignCrop (the same template as
 * InsightFace's norm_crop, so the crop suits SFace and AuraFace). Owns the
 * SFace recognizer, which OpenCV needs for the alignment. Not thread safe.
 */
final class FaceAligner implements AutoCloseable {

    static final int SIZE = 112;
    /** Standard positions of the landmarks in the 112 x 112 face (ArcFace). */
    private static final double[] TEMPLATE = {
        38.2946, 51.6963, 73.5318, 51.5014, 56.0252, 71.7366, 41.5493, 92.3655, 70.7299, 92.2041};

    private final FaceRecognizerSF recognizer;

    FaceAligner() throws IOException {
        FaceModels.loadOpenCv();
        recognizer = FaceRecognizerSF.create(FaceModels.path(FaceModels.SFACE).toString(), "");
    }

    FaceRecognizerSF recognizer() {
        return recognizer;
    }

    /**
     * @param landmarks 5 points, normalized and relative to the source (see
     * FaceDetector.Detection)
     * @return the aligned face, 112 x 112, 8 bit BGR; the caller releases it
     */
    Mat align(Image16 source, double[] landmarks) {
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
        // 4 x the face span is about 3 x the 112 pixel crop: enough for the interpolation
        int maxSide = Math.min(Math.max(crop.width(), crop.height()), SIZE * 3);
        Rgb8 img = Rgb8.of(crop, maxSide);
        double k = (double) img.width() / crop.width();
        // a detection row of FaceDetectorYN: x, y, w, h, 5 landmarks, score; alignCrop uses the landmarks
        float[] box = new float[15];
        for (int i = 0; i < 10; i += 2) {
            box[4 + i] = (float) ((p[i] - x0) * k);
            box[5 + i] = (float) ((p[i + 1] - y0) * k);
        }
        box[14] = 1;
        Mat mat = img.toMat();
        Mat row = new Mat(1, 15, CvType.CV_32F);
        Mat aligned = new Mat();
        try {
            row.put(0, 0, box);
            recognizer.alignCrop(mat, row, aligned);
            return aligned;
        } finally {
            mat.release();
            row.release();
        }
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

    @Override
    public void close() {
        // FaceRecognizerSF has no close: its native object is freed by the garbage collector
    }
}
