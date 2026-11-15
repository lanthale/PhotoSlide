package org.photoslide.faces;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.Map;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * AuraFace (fal.ai, Apache 2.0), a ResNet100 ArcFace, run by ONNX Runtime:
 * 512 values. Input as for InsightFace: RGB, (value - 127.5) / 127.5 (the
 * graph starts with a convolution, no normalization of its own).
 */
final class AuraFaceEmbedder implements FaceEmbedder {

    private static final int SIZE = 112;
    private static final float MEAN = 127.5f;
    private static final float STD = 127.5f;

    private final OrtSession session;
    private final String input;

    AuraFaceEmbedder() throws IOException {
        session = FaceModels.openOrt(FaceModels.AURAFACE);
        input = session.getInputNames().iterator().next();
    }

    @Override
    public EmbedderType type() {
        return EmbedderType.AURAFACE;
    }

    @Override
    public float[] embed(Mat aligned) throws IOException {
        if (aligned.rows() != SIZE || aligned.cols() != SIZE || aligned.type() != CvType.CV_8UC3) {
            throw new IllegalArgumentException("aligned face expected (112 x 112, 8 bit BGR)");
        }
        byte[] bgr = new byte[SIZE * SIZE * 3];
        aligned.get(0, 0, bgr);
        int plane = SIZE * SIZE;
        float[] data = new float[3 * plane];
        for (int i = 0; i < plane; i++) {
            data[i] = ((bgr[3 * i + 2] & 0xFF) - MEAN) / STD;
            data[plane + i] = ((bgr[3 * i + 1] & 0xFF) - MEAN) / STD;
            data[2 * plane + i] = ((bgr[3 * i] & 0xFF) - MEAN) / STD;
        }
        try (OnnxTensor tensor = OnnxTensor.createTensor(FaceModels.ortEnvironment(), FloatBuffer.wrap(data),
                new long[]{1, 3, SIZE, SIZE}); OrtSession.Result result = session.run(Map.of(input, tensor))) {
            float[][] out = (float[][]) result.get(0).getValue();
            return FaceEmbedder.normalize(out[0]);
        } catch (OrtException ex) {
            throw new IOException("AuraFace failed", ex);
        }
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException ex) {
            // nothing to do
        }
    }
}
