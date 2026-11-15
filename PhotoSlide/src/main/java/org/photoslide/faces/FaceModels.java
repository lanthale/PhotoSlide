package org.photoslide.faces;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.photoslide.Utility;

/**
 * The ONNX models of the face recognition.
 * <ul>
 * <li>YuNet (OpenCV Zoo, MIT license): finds faces and 5 landmarks, run by
 * OpenCV's FaceDetectorYN.</li>
 * <li>SFace (OpenCV Zoo, Apache 2.0): aligns the faces (alignCrop) and is the
 * fast recognizer (128 values), run by OpenCV's FaceRecognizerSF.</li>
 * <li>AuraFace (fal.ai, Apache 2.0): the accurate recognizer, a ResNet100
 * ArcFace (512 values), run by ONNX Runtime.</li>
 * </ul>
 * The installation ships them in the folder "models" next to the application
 * jar. Missing ones (e.g. when started from the IDE) are downloaded into the
 * application data folder and checked by SHA-256.
 */
final class FaceModels {

    private static final Logger LOG = Logger.getLogger(FaceModels.class.getName());

    record Spec(String file, String url, String sha256, long size) {
    }

    static final Spec YUNET = new Spec("face_detection_yunet_2023mar.onnx",
            "https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx",
            "8f2383e4dd3cfbb4553ea8718107fc0423210dc964f9f4280604804ed2552fa4", 232_589);
    static final Spec SFACE = new Spec("face_recognition_sface_2021dec.onnx",
            "https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx",
            "0ba9fbfa01b5270c96627c4ef784da859931e02f04419c829e83484087c34e79", 38_696_353);
    static final Spec AURAFACE = new Spec("auraface_glintr100.onnx",
            "https://huggingface.co/fal/AuraFace-v1/resolve/main/glintr100.onnx",
            "a7933ea5330113b01c9b60351d8f4c33003f145d8470ac5f0e52ee2effe25c60", 260_694_151);

    private static boolean openCvLoaded;
    private static OrtEnvironment ortEnvironment;

    private FaceModels() {
    }

    /** The models needed for the recognizer: YuNet and SFace always (alignment), AuraFace if chosen. */
    static List<Spec> required(EmbedderType type) {
        List<Spec> specs = new ArrayList<>(List.of(YUNET, SFACE));
        if (type == EmbedderType.AURAFACE) {
            specs.add(AURAFACE);
        }
        return specs;
    }

    /** Folder of the models shipped with the installation, null if there is none. */
    static Path bundledDir() {
        String dir = System.getProperty("photoslide.faceModels");
        if (dir != null) {
            return null;
        }
        try {
            Path code = Path.of(FaceModels.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path models = code.getParent() == null ? null : code.getParent().resolve("models");
            return models != null && Files.isDirectory(models) ? models : null;
        } catch (URISyntaxException | RuntimeException ex) {
            return null;
        }
    }

    /** The folder for downloaded models; the system property photoslide.faceModels overrides it (tests). */
    static Path dir() {
        String dir = System.getProperty("photoslide.faceModels");
        return dir != null ? Path.of(dir) : Path.of(Utility.getAppData(), "models");
    }

    /** The shipped model if it is complete, else the downloaded one. */
    static Path path(Spec spec) {
        Path bundled = bundledDir();
        if (bundled != null && isComplete(bundled.resolve(spec.file()), spec)) {
            return bundled.resolve(spec.file());
        }
        return dir().resolve(spec.file());
    }

    /** true if all models of the recognizer are on disk (the hash is checked after the download only). */
    static boolean available(EmbedderType type) {
        return required(type).stream().allMatch(s -> isComplete(path(s), s));
    }

    /** Bytes still to download for the recognizer. */
    static long missingSize(EmbedderType type) {
        return required(type).stream().filter(s -> !isComplete(path(s), s)).mapToLong(Spec::size).sum();
    }

    private static boolean isComplete(Path file, Spec spec) {
        try {
            return Files.size(file) == spec.size();
        } catch (IOException ex) {
            return false;
        }
    }

    /** Downloads the missing models of the recognizer. Progress 0..1 over all files. Not on the JavaFX thread. */
    static void download(EmbedderType type, DoubleConsumer progress) throws IOException {
        List<Spec> missing = required(type).stream().filter(s -> !isComplete(path(s), s)).toList();
        if (missing.isEmpty()) {
            progress.accept(1);
            return;
        }
        Files.createDirectories(dir());
        long total = missing.stream().mapToLong(Spec::size).sum();
        long done = 0;
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20)).build();
        for (Spec spec : missing) {
            download(client, spec, done, total, progress);
            done += spec.size();
            progress.accept((double) done / total);
        }
    }

    private static void download(HttpClient client, Spec spec, long before, long total, DoubleConsumer progress)
            throws IOException {
        Path target = dir().resolve(spec.file());
        Path part = target.resolveSibling(spec.file() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(spec.url())).timeout(Duration.ofMinutes(30)).build();
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
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        } finally {
            Files.deleteIfExists(part);
        }
    }

    /** Loads the native OpenCV library (once). */
    static synchronized void loadOpenCv() throws IOException {
        if (!openCvLoaded) {
            try {
                nu.pattern.OpenCV.loadLocally();
                openCvLoaded = true;
            } catch (RuntimeException | UnsatisfiedLinkError ex) {
                throw new IOException("Cannot load OpenCV", ex);
            }
        }
    }

    /** An ONNX Runtime session of the model; half of the cores, so the user interface stays smooth. */
    static synchronized OrtSession openOrt(Spec spec) throws IOException {
        try {
            if (ortEnvironment == null) {
                ortEnvironment = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR, "PhotoSlide");
            }
            try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() / 2));
                options.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
                return ortEnvironment.createSession(path(spec).toString(), options);
            }
        } catch (OrtException ex) {
            throw new IOException("Cannot load " + spec.file(), ex);
        }
    }

    static OrtEnvironment ortEnvironment() {
        return ortEnvironment;
    }
}
