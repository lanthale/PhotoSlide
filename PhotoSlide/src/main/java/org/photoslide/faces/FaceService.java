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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.opencv.core.Mat;
import org.photoslide.App;
import org.photoslide.datamodel.FaceRegion;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.editpipeline.EditPipeline;
import org.photoslide.faces.FaceDetector.Detection;
import org.photoslide.faces.FaceStore.KnownFace;
import org.photoslide.faces.FaceStore.PendingFace;
import org.photoslide.faces.FaceStore.AutoFace;
import org.photoslide.faces.FaceStore.ScanItem;

/**
 * Face recognition of PhotoSlide: finds the faces of a photo, suggests names
 * from the faces named before and learns from the names the user confirms.
 *
 * The names live in the XMP sidecars (see XmpFaceRegions); the search
 * database holds a copy (FACES) and the feature vectors (FACE_EMBEDDINGS),
 * which are computed again from the photos if the database is new. All work
 * runs on one background thread. The models are loaded on first use and
 * released again after two minutes without use (AuraFace takes some hundred
 * MB of native memory).
 */
public final class FaceService {

    private static final Logger LOG = Logger.getLogger(FaceService.class.getName());
    private static final FaceService INSTANCE = new FaceService();

    private static final String PREF_FAST_MODEL = "faces.fastModel";
    private static final String PREF_AUTO_SCAN = "faces.autoScan";
    /** The background scan waits this long after the start of the program. */
    private static final long SCAN_START_DELAY_MS = 60_000;
    /** Delay of the new matching after a name was changed (several changes in a row: once). */
    private static final long REMATCH_DELAY_SECONDS = 5;
    private static final long IDLE_SECONDS = 120;
    /** A new detection overlapping a known face more than this is the same face. */
    private static final double SAME_FACE_IOU = 0.4;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("faceRecognition").daemon().priority(Thread.NORM_PRIORITY - 1).factory());
    private final Preferences preferences = Preferences.userRoot().node("PhotoSlide");
    private volatile EmbedderType type = preferences.getBoolean(PREF_FAST_MODEL, false)
            ? EmbedderType.SFACE : EmbedderType.AURAFACE;
    private final AtomicBoolean learning = new AtomicBoolean();
    private final List<DoubleConsumer> progressListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile double downloadProgress;
    private CompletableFuture<Void> download;
    // used on the executor thread only
    private FaceDetector detector;
    private FaceAligner aligner;
    private FaceEmbedder embedder;
    private Connection connection;
    private ScheduledFuture<?> idleRelease;
    private volatile Runnable downloadStarter;
    private final long startTime = System.currentTimeMillis();
    private ScheduledFuture<?> pendingRematch;

    private FaceService() {
    }

    public static FaceService get() {
        return INSTANCE;
    }

    /** The recognizer in use: AuraFace, or SFace if the fast model is chosen in the preferences. */
    public EmbedderType embedderType() {
        return type;
    }

    /** true if the fast but less accurate model (SFace) is used instead of AuraFace. */
    public boolean isFastModel() {
        return type == EmbedderType.SFACE;
    }

    /**
     * Switches the recognizer (preferences). The vectors of each model are
     * kept apart in the database; missing ones of the new model are computed
     * in the background as soon as its files are there.
     */
    public void setFastModel(boolean fast) {
        EmbedderType newType = fast ? EmbedderType.SFACE : EmbedderType.AURAFACE;
        preferences.putBoolean(PREF_FAST_MODEL, fast);
        if (newType != type) {
            type = newType;
            executor.execute(this::releaseModels);
            Runnable starter = downloadStarter;
            if (!FaceModels.available(newType) && starter != null) {
                starter.run();
            } else {
                learnMissingEmbeddings();
            }
        }
    }

    /**
     * How a missing model is downloaded when the recognizer is changed (the
     * main window shows the download in its task list).
     */
    public void setDownloadStarter(Runnable starter) {
        downloadStarter = starter;
    }

    /** true if the photos of the indexed collections are searched for faces in the background (preferences). */
    public boolean isAutoScan() {
        return preferences.getBoolean(PREF_AUTO_SCAN, true);
    }

    public void setAutoScan(boolean autoScan) {
        preferences.putBoolean(PREF_AUTO_SCAN, autoScan);
    }

    /**
     * Milliseconds the background scan should still wait so that it does not
     * slow down the start of the program.
     */
    public long scanStartDelay() {
        return Math.max(0, startTime + SCAN_START_DELAY_MS - System.currentTimeMillis());
    }

    /** true if the models of the recognizer in use are on disk (else {@link #downloadModels} first). */
    public boolean modelsAvailable() {
        return FaceModels.available(type);
    }

    /** Bytes still to download for the recognizer in use. */
    public long modelDownloadSize() {
        return FaceModels.missingSize(type);
    }

    /**
     * Downloads the missing models on a background thread. A download that
     * is already running is shared: the progress (0..1, background thread)
     * of it is reported to every caller.
     */
    public synchronized CompletableFuture<Void> downloadModels(DoubleConsumer progress) {
        progressListeners.add(progress);
        progress.accept(downloadProgress);
        if (download == null || download.isCompletedExceptionally()
                || (download.isDone() && !FaceModels.available(type))) {
            downloadProgress = 0;
            EmbedderType forType = type;
            download = CompletableFuture.runAsync(() -> {
                try {
                    FaceModels.download(forType, p -> {
                        downloadProgress = p;
                        progressListeners.forEach(l -> l.accept(p));
                    });
                } catch (IOException ex) {
                    throw new java.util.concurrent.CompletionException(ex);
                }
            }, runnable -> Thread.ofPlatform().name("faceModelDownload").daemon()
                    .priority(Thread.MIN_PRIORITY).start(runnable));
            download.whenComplete((v, ex) -> {
                progressListeners.clear();
                if (ex == null) {
                    learnMissingEmbeddings();
                }
            });
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
                double threshold = embedder.type().matchThreshold();
                List<FaceRegion> result = new ArrayList<>();
                for (int i = 0; i < faces.size(); i++) {
                    FaceRegion face = faces.get(i);
                    float[] embedding = storedEmbedding(path, face);
                    if (embedding == null) {
                        embedding = embed(source, quarters, face, landmarks.get(i));
                        storeEmbedding(path, face, embedding);
                    }
                    if (!face.confirmed()) {
                        String name = match(known, embedding, threshold);
                        face = face.withName(name, false);
                    }
                    result.add(face);
                }
                commit();
                return result;
            } catch (Exception ex) {
                rollback();
                throw new java.util.concurrent.CompletionException(ex);
            } finally {
                scheduleRelease();
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
                return match(knownFaces(), embedding, embedder.type().matchThreshold());
            } catch (Exception ex) {
                rollback();
                throw new java.util.concurrent.CompletionException(ex);
            } finally {
                scheduleRelease();
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
        if (!FaceModels.available(type) || App.getSearchDBConnection() == null || !learning.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.submit(() -> {
                try {
                    learnMissing();
                } finally {
                    learning.set(false);
                    scheduleRelease();
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
                List<PendingFace> pending = FaceStore.confirmedWithoutEmbedding(connection(), embedder.type().id(),
                        50 + failed.size());
                pending.removeIf(p -> failed.contains(p.path()));
                if (pending.isEmpty()) {
                    rematch();
                    return;
                }
                Map<String, List<FaceRegion>> byPath = new HashMap<>();
                for (PendingFace p : pending) {
                    byPath.computeIfAbsent(p.path(), k -> new ArrayList<>()).add(p.region());
                }
                for (Map.Entry<String, List<FaceRegion>> e : byPath.entrySet()) {
                    // the user waits for a folder (only then: this thread also serves the user)
                    try {
                        org.photoslide.BackgroundPause.awaitResumed(() -> false);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return;
                    }
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

    /**
     * Background scan of one file (see SearchIndex): finds the faces of a
     * photo without faces in its XMP and stores them with the suggested names
     * in the search database only, so that the names are found by the search.
     * The XMP is not changed. Each file is a job of its own: the photo the
     * user is looking at comes in between.
     */
    public CompletableFuture<Void> scanFile(ScanItem item) {
        return CompletableFuture.runAsync(() -> {
            String path = item.path();
            try {
                Connection con = connection();
                if (con == null) {
                    return;
                }
                String model = type.id();
                MediaFile m = mediaFile(Path.of(path));
                if (m.getMediaType() != MediaFile.MediaTypes.IMAGE || FaceStore.hasXmpFaces(con, path)
                        || !m.getFaceRegions().isEmpty() || m.isFacesScanned()) {
                    // the faces of the XMP count (also none, if the user removed all)
                    FaceStore.writeAutoFaces(con, path, List.of());
                } else {
                    ensureModels();
                    int quarters = FaceRegion.quarters(m.getRotationAngleProperty().get());
                    Image16 source = EditPipeline.loadSourceUncached(m);
                    List<KnownFace> known = FaceStore.knownFaces(con, embedder.type().id());
                    double threshold = embedder.type().matchThreshold();
                    List<FaceRegion> faces = new ArrayList<>();
                    for (Detection d : detector.detect(source, quarters)) {
                        if (indexOfSame(faces, d.region()) >= 0) {
                            continue;
                        }
                        float[] embedding = embed(source, quarters, d.region(), d.landmarks());
                        storeEmbedding(path, d.region(), embedding);
                        faces.add(d.region().withName(match(known, embedding, threshold), false));
                    }
                    FaceStore.writeAutoFaces(con, path, faces);
                }
                FaceStore.refreshSearchText(con, path);
                FaceStore.markScanned(con, path, model, item.lastModified());
                commit();
            } catch (Exception ex) {
                // e.g. a damaged file: not again until it changes
                LOG.log(Level.FINE, "No faces scanned in " + path, ex);
                rollback();
                try {
                    Connection con = connection();
                    if (con != null) {
                        FaceStore.markScanned(con, path, type.id(), item.lastModified());
                        commit();
                    }
                } catch (SQLException ex2) {
                    rollback();
                }
            } finally {
                scheduleRelease();
            }
        }, executor);
    }

    /**
     * Matches the faces of the background scan again with the confirmed
     * faces, a few seconds later (after a name was given or changed). Needs no
     * models, only the stored feature vectors.
     */
    public void rematchLater() {
        if (App.getSearchDBConnection() == null) {
            return;
        }
        try {
            executor.execute(() -> {
                if (pendingRematch != null) {
                    pendingRematch.cancel(false);
                }
                pendingRematch = executor.schedule(this::rematch, REMATCH_DELAY_SECONDS, TimeUnit.SECONDS);
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            // shut down
        }
    }

    /** Matches the faces of the background scan again (on the executor thread). */
    private void rematch() {
        pendingRematch = null;
        try {
            Connection con = connection();
            if (con == null) {
                return;
            }
            EmbedderType t = type;
            List<KnownFace> known = FaceStore.knownFaces(con, t.id());
            Set<String> changed = new HashSet<>();
            for (AutoFace a : FaceStore.autoFaces(con, t.id())) {
                String name = match(known, a.embedding(), t.matchThreshold());
                if (!java.util.Objects.equals(name, a.name())) {
                    FaceStore.setAutoName(con, a.path(), a.idx(), name);
                    changed.add(a.path());
                }
            }
            for (String path : changed) {
                FaceStore.refreshSearchText(con, path);
            }
            commit();
            if (!changed.isEmpty()) {
                LOG.log(Level.FINE, "Face suggestions changed in {0} files", changed.size());
            }
        } catch (SQLException ex) {
            rollback();
            LOG.log(Level.WARNING, "Cannot match the scanned faces", ex);
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
        EmbedderType wanted = type;
        if (!FaceModels.available(wanted)) {
            throw new IOException("The face recognition models are not downloaded");
        }
        if (idleRelease != null) {
            idleRelease.cancel(false);
            idleRelease = null;
        }
        if (embedder != null && embedder.type() != wanted) {
            embedder.close();
            embedder = null;
        }
        if (detector == null) {
            detector = new FaceDetector();
        }
        if (aligner == null) {
            aligner = new FaceAligner();
        }
        if (embedder == null) {
            embedder = wanted == EmbedderType.SFACE ? new SFaceEmbedder(aligner) : new AuraFaceEmbedder();
        }
    }

    /** Releases the models after a while without use. */
    private void scheduleRelease() {
        if (idleRelease != null) {
            idleRelease.cancel(false);
        }
        try {
            idleRelease = executor.schedule(this::releaseModels, IDLE_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            idleRelease = null;
        }
    }

    private void releaseModels() {
        idleRelease = null;
        if (embedder != null) {
            embedder.close();
            embedder = null;
        }
        if (aligner != null) {
            aligner.close();
            aligner = null;
        }
        if (detector != null) {
            detector.close();
            detector = null;
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
                landmarks = FaceAligner.guessLandmarks(face, quarters);
            }
        }
        Mat aligned = aligner.align(source, landmarks);
        try {
            return embedder.embed(aligned);
        } finally {
            aligned.release();
        }
    }

    /** The name of the most similar known face, null if none is similar enough. */
    static String match(List<KnownFace> known, float[] embedding, double threshold) {
        String best = null;
        double bestScore = threshold;
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
        return con == null ? List.of() : FaceStore.knownFaces(con, embedder.type().id());
    }

    private float[] storedEmbedding(String path, FaceRegion face) throws SQLException {
        Connection con = connection();
        return con == null ? null : FaceStore.embedding(con, path, face, embedder.type().id());
    }

    private void storeEmbedding(String path, FaceRegion face, float[] embedding) throws SQLException {
        Connection con = connection();
        if (con != null) {
            FaceStore.putEmbedding(con, path, face, embedder.type().id(), embedding);
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
