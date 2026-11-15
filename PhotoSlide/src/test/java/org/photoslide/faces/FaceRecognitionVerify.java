package org.photoslide.faces;

import java.nio.file.Path;
import java.util.List;
import org.opencv.core.Mat;
import org.photoslide.datamodel.AwtImage16;
import org.photoslide.datamodel.FaceRegion;
import org.photoslide.datamodel.Image16;
import org.photoslide.faces.FaceDetector.Detection;

/**
 * Runs detection and recognition on photos without the user interface:
 *
 * <pre>
 * java -Dphotoslide.faceModels=&lt;dir&gt; ... org.photoslide.faces.FaceRecognitionVerify a.jpg b.jpg
 * </pre>
 *
 * Prints the faces of each photo and checks, with both recognizers
 * (AuraFace and SFace), that every face is found again in the photo turned by
 * 90 degrees, that it is recognized in a smaller copy and that the faces of
 * the first and second photo are different people. Prints the similarities
 * and the time per face.
 */
public final class FaceRecognitionVerify {

    private FaceRecognitionVerify() {
    }

    public static void main(String[] args) throws Exception {
        try (FaceDetector detector = new FaceDetector(); FaceAligner aligner = new FaceAligner()) {
            for (FaceEmbedder embedder : new FaceEmbedder[]{new AuraFaceEmbedder(), new SFaceEmbedder(aligner)}) {
                try (embedder) {
                    run(detector, aligner, embedder, args);
                }
            }
        }
        System.out.println("FaceRecognitionVerify OK");
    }

    private static void run(FaceDetector detector, FaceAligner aligner, FaceEmbedder embedder, String[] files)
            throws Exception {
        EmbedderType type = embedder.type();
        double threshold = type.matchThreshold();
        System.out.println("== " + type + " (threshold " + threshold + ")");
        embed(aligner, embedder, AwtImage16.read8(Path.of(files[0]), p -> {
        }), detector.detect(AwtImage16.read8(Path.of(files[0]), p -> {
        }), 0).get(0).landmarks());  // warm up
        float[] first = null;
        for (String file : files) {
            Image16 img = AwtImage16.read8(Path.of(file), p -> {
            });
            long t = System.nanoTime();
            List<Detection> faces = detector.detect(img, 0);
            System.out.printf("%s: %d faces in %d ms%n", file, faces.size(), (System.nanoTime() - t) / 1_000_000);
            check(!faces.isEmpty(), "a face in " + file);
            Detection d = faces.get(0);
            System.out.println("  " + d.region() + " score " + d.score());

            t = System.nanoTime();
            float[] e = embed(aligner, embedder, img, d.landmarks());
            System.out.printf("  align + embed: %d ms, %d values%n", (System.nanoTime() - t) / 1_000_000, e.length);
            check(e.length == type.dimension(), "dimension");
            // the same face in a copy of half the size
            Image16 half = img.scaledTo(img.width() / 2, img.height() / 2);
            Detection dh = detector.detect(half, 0).get(0);
            double same = FaceEmbedder.similarity(e, embed(aligner, embedder, half, dh.landmarks()));
            System.out.printf("  same face, half size: %.3f%n", same);
            check(same > threshold, "recognized in the smaller copy");

            // the photo as if it were stored sideways and turned by PhotoSlide
            Image16 sideways = turnLeft(img);
            List<Detection> turned = detector.detect(sideways, 1);
            check(!turned.isEmpty(), "face found with rotation");
            FaceRegion back = turned.get(0).region().rotated(1);   // source of sideways -> shown = img
            double iou = FaceDetector.iou(back, d.region());
            System.out.printf("  rotation: IoU %.3f%n", iou);
            check(iou > 0.7, "rotated face at the same place");
            double rs = FaceEmbedder.similarity(e, embed(aligner, embedder, sideways, turned.get(0).landmarks()));
            System.out.printf("  same face, rotated: %.3f%n", rs);
            check(rs > threshold, "recognized when rotated");

            // without landmarks (rectangle drawn by hand)
            double guess = FaceEmbedder.similarity(e, embed(aligner, embedder, img,
                    FaceAligner.guessLandmarks(d.region(), 0)));
            System.out.printf("  same face, guessed landmarks: %.3f%n", guess);

            if (first == null) {
                first = e;
            } else {
                double other = FaceEmbedder.similarity(first, e);
                System.out.printf("different people: %.3f%n", other);
                check(other < threshold, "different people are not matched");
            }
        }
    }

    private static float[] embed(FaceAligner aligner, FaceEmbedder embedder, Image16 img, double[] landmarks)
            throws Exception {
        Mat aligned = aligner.align(img, landmarks);
        try {
            return embedder.embed(aligned);
        } finally {
            aligned.release();
        }
    }

    /** Turns counter clockwise, so a clockwise PhotoSlide rotation shows it upright. */
    private static Image16 turnLeft(Image16 img) {
        int w = img.width();
        int h = img.height();
        Image16 out = Image16.blank(h, w, img.isLinear());
        short[] s = img.data();
        short[] d = out.data();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int nx = y;
                int ny = w - 1 - x;
                System.arraycopy(s, (y * w + x) * 3, d, (ny * h + nx) * 3, 3);
            }
        }
        return out;
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAILED: " + what);
            System.exit(1);
        }
    }
}
