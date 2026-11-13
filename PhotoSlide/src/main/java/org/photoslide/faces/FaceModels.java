package org.photoslide.faces;

import ai.djl.MalformedModelException;
import ai.djl.ndarray.NDList;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.NoopTranslator;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.DoubleConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.photoslide.Utility;

/**
 * The two ONNX models of the face recognition, run by Amazon DJL with the
 * ONNX Runtime engine (no PyTorch). They are not part of the installation
 * (about 39 MB): they are downloaded on first use into the application data
 * folder and checked by SHA-256.
 * <ul>
 * <li>YuNet (OpenCV Zoo, MIT license): finds faces and 5 landmarks.</li>
 * <li>SFace (OpenCV Zoo, Apache 2.0): 128 dimensional feature vector of an
 * aligned face; the cosine similarity tells whether two faces are the same
 * person.</li>
 * </ul>
 */
final class FaceModels {

    private static final Logger LOG = Logger.getLogger(FaceModels.class.getName());

    record Spec(String file, String url, String sha256, long size) {
    }

    static final Spec DETECTOR = new Spec("face_detection_yunet_2023mar.onnx",
            "https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx",
            "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4", 232_589);
    static final Spec RECOGNIZER = new Spec("face_recognition_sface_2021dec.onnx",
            "https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx",
            "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79", 38_696_353);

    static {
        // DJL must not look for models or engines on the internet
        System.setProperty("ai.djl.offline", "true");
        // no fall back to the PyTorch engine (it would download its native library)
        System.setProperty("ai.djl.onnx.disable_alternative", "true");
    }

    private FaceModels() {
    }

    /** The folder of the models; the system property photoslide.faceModels overrides it (tests). */
    static Path dir() {
        String dir = System.getProperty("photoslide.faceModels");
        return dir != null ? Path.of(dir) : Path.of(Utility.getAppData(), "models");
    }

    static Path path(Spec spec) {
        return dir().resolve(spec.file());
    }

    /** true if both models are on disk (the hash is checked after the download only). */
    static boolean available() {
        return isComplete(DETECTOR) && isComplete(RECOGNIZER);
    }

    private static boolean isComplete(Spec spec) {
        try {
            return Files.size(path(spec)) == spec.size();
        } catch (IOException ex) {
            return false;
        }
    }

    /** Downloads the missing models. Progress 0..1 over both files. Not on the JavaFX thread. */
    static void download(DoubleConsumer progress) throws IOException {
        Files.createDirectories(dir());
        long total = DETECTOR.size() + RECOGNIZER.size();
        long done = 0;
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build();
        for (Spec spec : new Spec[]{DETECTOR, RECOGNIZER}) {
            if (!isComplete(spec)) {
                download(client, spec, done, total, progress);
            }
            done += spec.size();
            progress.accept((double) done / total);
        }
    }

    private static void download(HttpClient client, Spec spec, long before, long total, DoubleConsumer progress)
            throws IOException {
        Path part = path(spec).resolveSibling(spec.file() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(spec.url())).timeout(Duration.ofMinutes(10)).build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("Download of " + spec.file() + " failed: HTTP " + response.statusCode());
            }
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
                byte[] buffer = new byte[64 * 1024];
                long read = 0;
                int n;
                while ((n = in.read(buffer)) > 0) {
                    out.write(buffer, 0, n);
                    sha.update(buffer, 0, n);
                    read += n;
                    progress.accept((double) (before + Math.min(read, spec.size())) / total);
                }
            }
            String hash = HexFormat.of().formatHex(sha.digest());
            if (!hash.equalsIgnoreCase(spec.sha256())) {
                throw new IOException("Download of " + spec.file() + " is damaged (SHA-256 " + hash + ")");
            }
            Files.move(part, path(spec), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    /** Errors only: the SFace model would log hundreds of harmless warnings. */
    private static ai.onnxruntime.OrtSession.SessionOptions sessionOptions() throws IOException {
        try {
            ai.onnxruntime.OrtSession.SessionOptions options = new ai.onnxruntime.OrtSession.SessionOptions();
            options.setSessionLogLevel(ai.onnxruntime.OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
            return options;
        } catch (ai.onnxruntime.OrtException ex) {
            throw new IOException(ex);
        }
    }

    /** Loads a model with plain tensors in and out (pre- and post-processing are done in Java). */
    static ZooModel<NDList, NDList> load(Spec spec) throws IOException {
        Criteria<NDList, NDList> criteria = Criteria.builder()
                .setTypes(NDList.class, NDList.class)
                .optModelPath(path(spec))
                .optEngine("OnnxRuntime")
                .optTranslator(new NoopTranslator())
                .optArgument("sessionOptions", sessionOptions())
                .build();
        try {
            return criteria.loadModel();
        } catch (ModelNotFoundException | MalformedModelException ex) {
            throw new IOException("Cannot load " + spec.file(), ex);
        }
    }
}
