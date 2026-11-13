package org.photoslide.faces;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.photoslide.App;
import org.photoslide.datamodel.FaceRegion;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.editpipeline.EditPipeline;
import org.photoslide.faces.FaceDetector.Detection;
import org.photoslide.faces.FaceStore.KnownFace;
import org.photoslide.faces.FaceStore.PendingFace;

/**
 * Face recognition of PhotoSlide: finds the faces of a photo, suggests names
 * from the faces named before and learns from the names the user confirms.
 *
 * The names live in the XMP sidecars (see XmpFaceRegions); the search
 * database holds a copy (FACES) and the feature vectors (FACE_EMBEDDINGS),
 * which are computed again from the photos if the database is new. All work
 * runs on one background thread, the models are loaded on first use.
 */
public final class FaceService {

    private static final Logger LOG = Logger.getLogger(FaceService.class.getName());
    private static final FaceService INSTANCE = new FaceService();

    /** Minimum cosine similarity for a suggestion (OpenCV's value for SFace). */
    static final double MATCH_THRESHOLD = 0.363;
    /** A new detection overlapping a known face more than this is the same face. */
    private static final double SAME_FACE_IOU = 0.4;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("faceRecognition").daemon().priority(Thread.NORM_PRIORITY - 1).factory());
    private final AtomicBoolean learning = new AtomicBoolean();
    private final List<DoubleConsumer> progressListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile double downloadProgress;
    private CompletableFuture<Void> download;
    // used on the executor thread only
    private FaceDetector detector;
    private FaceEmbedder embedder;
    private Connection connection;

    private FaceService() {
    }

    public static FaceService get() {
        return INSTANCE;
    }

    /** true if the models are on disk (else {@link #downloadModels} first). */
    public boolean modelsAvailable() {
        return FaceModels.available();
    }

    /** Size of the download in bytes. */
    public long modelDownloadSize() {
        return FaceModels.DETECTOR.size() + FaceModels.RECOGNIZER.size();
    }

    /**
     * Downloads the missing models on a background thread. A download that
     * is already running is shared: the progress (0..1, background thread)
     * of it is reported to every caller.
     */
    public synchronized CompletableFuture<Void> downloadModels(DoubleConsumer progress) {
        progressListeners.add(progress);
        progress.accept(downloadProgress);
        if (download == null || download.isCompletedExceptionally()) {
            downloadProgress = 0;
            download = CompletableFuture.runAsync(() -> {
                try {
                    FaceModels.download(p -> {
                        downloadProgress = p;
                        progressListeners.forEach(l -> l.accept(p));
                    });
                } catch (IOException ex) {
                    throw new java.util.concurrent.CompletionException(ex);
                }
            }, runnable -> Thread.ofPlatform().name("faceModelDownload").daemon()
                    .priority(Thread.MIN_PRIORITY).start(runnable));
            download.whenComplete((v, ex) -> progressListeners.clear());
        }
        return download;
    }

    /** true while the models are being downloaded. */
    public synchronized boolean isDownloading() {
        return download != null && !download.isDone();
    }

    /**
     * The faces of the photo with suggestions for unknown ones. If the photo
     * was not scanned yet, the faces are detected (faces already in the XMP,
     * e.g. from another program, are kept). Confirmed faces stay as they are.
     * Does not change the file: the caller sets the result and saves it.
     */
    public CompletableFuture<List<FaceRegion>> analyze(MediaFile file) {
        List<FaceRegion> current = file.getFaceRegions();
        boolean scanned = file.isFacesScanned();
        int quarters = FaceRegion.quarters(file.getRotationAngleProperty().get());
        String path = file.getPathStorage().toString();
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureModels();
                Image16 source = EditPipeline.loadSource(file);
                List<FaceRegion> faces = new ArrayList<>(current);
                Map<Integer, double[]> landmarks = new HashMap<>();
                if (!scanned) {
                    for (Detection d : detector.detect(source, quarters)) {
                        int same = indexOfSame(faces, d.region());
                        if (same < 0) {
                            landmarks.put(faces.size(), d.landmarks());
                            faces.add(d.region());
                        } else {
                            landmarks.putIfAbsent(same, d.landmarks());
                        }
                    }
                }
                List<KnownFace> known = knownFaces();
                List<FaceRegion> result = new ArrayList<>();
                for (int i = 0; i < faces.size(); i++) {
                    FaceRegion face = faces.get(i);
                    float[] embedding = storedEmbedding(path, face);
                    if (embedding == null) {
                        embedding = embed(source, quarters, face, landmarks.get(i));
                        storeEmbedding(path, face, embedding);
                    }
                    if (!face.confirmed()) {
                        String name = match(known, embedding);
                        face = face.withName(name, false);
                    }
                    result.add(face);
                }
                commit();
                return result;
            } catch (Exception ex) {
                rollback();
                throw new java.util.concurrent.CompletionException(ex);
            }
        }, executor);
    }

    /** Suggestion for one face, e.g. a rectangle the user drew. null = no match. */
    public CompletableFuture<String> suggest(MediaFile file, FaceRegion face) {
        int quarters = FaceRegion.quarters(file.getRotationAngleProperty().get());
        String path = file.getPathStorage().toString();
        return CompletableFuture.supplyAsync(() -> {
            try {
                ensureModels();
                Image16 source = EditPipeline.loadSource(file);
                float[] embedding = embed(source, quarters, face, null);
                storeEmbedding(path, face, embedding);
                commit();
                return match(knownFaces(), embedding);
            } catch (Exception ex) {
                rollback();
                throw new java.util.concurrent.CompletionException(ex);
            }
        }, executor);
    }

    /** Names of all confirmed faces (sorted), for the input suggestions. */
    public CompletableFuture<List<String>> names() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Connection con = connection();
                return con == null ? List.<String>of() : FaceStore.names(con);
            } catch (SQLException ex) {
                LOG.log(Level.WARNING, "Cannot read face names", ex);
                return List.<String>of();
            }
        }, executor);
    }

    /**
     * Computes the missing feature vectors of named faces in the background,
     * e.g. after the search database was built again from the XMP files on
     * another computer. Does nothing while the models are not downloaded.
     */
    public void learnMissingEmbeddings() {
        if (!FaceModels.available() || App.getSearchDBConnection() == null || !learning.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.submit(() -> {
                try {
                    learnMissing();
                } finally {
                    learning.set(false);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            learning.set(false);
        }
    }

    private void learnMissing() {
        Set<String> failed = new HashSet<>();
        try {
            ensureModels();
            while (true) {
                List<PendingFace> pending = FaceStore.confirmedWithoutEmbedding(connection(), 50 + failed.size());
                pending.removeIf(p -> failed.contains(p.path()));
                if (pending.isEmpty()) {
                    return;
                }
                Map<String, List<FaceRegion>> byPath = new HashMap<>();
                for (PendingFace p : pending) {
                    byPath.computeIfAbsent(p.path(), k -> new ArrayList<>()).add(p.region());
                }
                for (Map.Entry<String, List<FaceRegion>> e : byPath.entrySet()) {
                    try {
                        MediaFile m = mediaFile(Path.of(e.getKey()));
                        int quarters = FaceRegion.quarters(m.getRotationAngleProperty().get());
                        Image16 source = EditPipeline.loadSourceUncached(m);
                        for (FaceRegion r : e.getValue()) {
                            storeEmbedding(e.getKey(), r, embed(source, quarters, r, null));
                        }
                        commit();
                    } catch (Exception ex) {
                        LOG.log(Level.FINE, "No face embedding for " + e.getKey(), ex);
                        failed.add(e.getKey());
                        rollback();
                    }
                }
            }
        } catch (IOException | SQLException ex) {
            LOG.log(Level.WARNING, "Cannot compute face embeddings", ex);
        }
    }

    private static MediaFile mediaFile(Path path) {
        MediaFile m = new MediaFile();
        m.setName(path.getFileName().toString());
        m.setPathStorage(path);
        m.setMediaType(FileTypes.isValidVideo(path.toString()) ? MediaFile.MediaTypes.VIDEO : MediaFile.MediaTypes.IMAGE);
        m.readEdits();
        return m;
    }

    // ------------------------------------------------------------------
    // executor thread
    // ------------------------------------------------------------------
    private void ensureModels() throws IOException {
        if (!FaceModels.available()) {
            throw new IOException("The face recognition models are not downloaded");
        }
        if (detector == null) {
            detector = new FaceDetector();
        }
        if (embedder == null) {
            embedder = new FaceEmbedder();
        }
    }

    /**
     * Feature vector of a face. Without landmarks the face is searched again
     * in the area around its rectangle; if none is found there, the standard
     * positions in the rectangle are taken.
     */
    private float[] embed(Image16 source, int quarters, FaceRegion face, double[] landmarks) throws Exception {
        if (landmarks == null) {
            FaceRegion around = face.withBounds(face.x() - face.w(), face.y() - face.h(), face.w() * 3, face.h() * 3);
            double best = 0.3;
            for (Detection d : detector.detect(source, quarters, around)) {
                double iou = FaceDetector.iou(d.region(), face);
                if (iou > best) {
                    best = iou;
                    landmarks = d.landmarks();
                }
            }
            if (landmarks == null) {
                landmarks = FaceEmbedder.guessLandmarks(face, quarters);
            }
        }
        return embedder.embed(source, landmarks);
    }

    /** The name of the most similar known face, null if none is similar enough. */
    static String match(List<KnownFace> known, float[] embedding) {
        String best = null;
        double bestScore = MATCH_THRESHOLD;
        for (KnownFace k : known) {
            double s = FaceEmbedder.similarity(k.embedding(), embedding);
            if (s >= bestScore) {
                bestScore = s;
                best = k.name();
            }
        }
        return best;
    }

    private static int indexOfSame(List<FaceRegion> faces, FaceRegion r) {
        for (int i = 0; i < faces.size(); i++) {
            if (FaceDetector.iou(faces.get(i), r) > SAME_FACE_IOU) {
                return i;
            }
        }
        return -1;
    }

    private List<KnownFace> knownFaces() throws SQLException {
        Connection con = connection();
        return con == null ? List.of() : FaceStore.knownFaces(con);
    }

    private float[] storedEmbedding(String path, FaceRegion face) throws SQLException {
        Connection con = connection();
        return con == null ? null : FaceStore.embedding(con, path, face);
    }

    private void storeEmbedding(String path, FaceRegion face, float[] embedding) throws SQLException {
        Connection con = connection();
        if (con != null) {
            FaceStore.putEmbedding(con, path, face, embedding);
        }
    }

    /** null if there is no search database. */
    private Connection connection() throws SQLException {
        if (App.getSearchDBConnection() == null) {
            return null;
        }
        if (connection == null || connection.isClosed()) {
            connection = App.openSearchDBConnection();
            connection.setAutoCommit(false);
        }
        return connection;
    }

    private void commit() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.commit();
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Cannot store face embeddings", ex);
        }
    }

    private void rollback() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.rollback();
            }
        } catch (SQLException ex) {
            LOG.log(Level.FINE, null, ex);
        }
    }
}
