/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;


import org.photoslide.I18n;
import org.photoslide.browsermetadata.MetadataResult;
import fr.dudie.nominatim.model.Address;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.concurrent.Task;
import org.photoslide.App;
import org.photoslide.MainViewController;
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.browsermetadata.GeoCoding;
import org.photoslide.datamodel.FaceRegion;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.faces.FaceService;
import org.photoslide.faces.FaceStore;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.datamodel.XmpSidecar;
import org.photoslide.browsermetadata.MetadataController;

/**
 * Keeps the search database in sync with the collections. A scan compares the
 * modification times of the files (and their XMP sidecars) with the indexed
 * ones and reads the metadata of new and changed files only; files that no
 * longer exist are removed. Saved edits are written into the index at once.
 *
 * @author selfemp
 */
public class SearchIndex {

    private static final Logger LOG = Logger.getLogger(SearchIndex.class.getName());
    private static final int COMMIT_ROWS = 200;
    // Nominatim allows one request per second
    private static final long GEOCODING_INTERVAL_MS = 1100;
    private static final String MERGE_SQL = "MERGE INTO MEDIAFILES (PATHSTORAGE, COLLECTIONNAME, NAME, TITLE, KEYWORDS, CAMERA, RATING, RECORDTIME, CREATIONTIME, PLACES, GPSPOSITION, FACES, METADATA, LASTMODIFIED, FILESIZE) KEY(PATHSTORAGE) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private final MetadataController metadataController;
    private final MainViewController mainViewController;
    // one thread: collections are scanned one after another, edits in between
    private final ExecutorService executor;
    private final ExecutorService geoExecutor;
    // background face scans, one collection after another
    private final ExecutorService faceExecutor;
    private final Map<Task<Void>, String> tasks = new ConcurrentHashMap<>();
    // collections whose places are being resolved
    private final Set<String> geoPending = ConcurrentHashMap.newKeySet();
    private volatile boolean terminated;
    /** told (FX thread) when the files of a collection in the index may have changed */
    private volatile java.util.function.Consumer<String> indexChangedListener;
    // used on the executor thread only
    private Connection connection;

    public SearchIndex(MetadataController metc, MainViewController c) {
        this.metadataController = metc;
        mainViewController = c;
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setPriority(1).setNamePrefix("searchIndexExecutor").build());
        geoExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setPriority(1).setNamePrefix("searchIndexGeoExecutor").build());
        faceExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setPriority(1).setNamePrefix("searchIndexFaceExecutor").build());
        MediaFile.setEditsSavedListener(this::updateEdits);
    }

    /**
     * Brings the index of the collection up to date: new and changed files
     * are indexed, removed files are deleted from the index.
     */
    public void createCheckSearchIndex(String searchPath) {
        if (terminated || App.getSearchDBConnection() == null) {
            return;
        }
        String collectionName = searchPath;
        Path root = Paths.get(searchPath);
        ScanTask task = new ScanTask(root, collectionName);
        tasks.put(task, collectionName);
        task.setOnScheduled((t) -> {
            mainViewController.getTaskProgressView().getTasks().add(task);
        });
        task.setOnSucceeded((t) -> {
            finished(task);
        });
        task.setOnCancelled((t) -> {
            finished(task);
        });
        task.setOnFailed((t) -> {
            finished(task);
            LOG.log(Level.SEVERE, "Error creating searchIndexDB", t.getSource().getException());
        });
        executor.submit(task);
    }

    /**
     * Searches the photos of the collection for faces in the background (only
     * new and changed files, see FaceService.scanFile), unless switched off
     * in the preferences or the models are missing.
     */
    private void createFaceScan(String collectionName, boolean force) {
        FaceService faces = FaceService.get();
        if (terminated || App.getSearchDBConnection() == null || !faces.modelsAvailable()
                || (!force && !faces.isAutoScan())) {
            return;
        }
        if (force) {
            // a scan of the collection that is waiting or running would only be repeated
            tasks.forEach((t, name) -> {
                if (t instanceof FaceScanTask && name.equals(collectionName)) {
                    t.cancel(false);
                }
            });
        }
        FaceScanTask task = new FaceScanTask(collectionName, force);
        tasks.put(task, collectionName);
        task.setOnSucceeded((t) -> finished(task));
        task.setOnCancelled((t) -> finished(task));
        task.setOnFailed((t) -> {
            finished(task);
            LOG.log(Level.WARNING, "Face scan of " + collectionName + " failed", t.getSource().getException());
        });
        try {
            faceExecutor.submit(task);
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            tasks.remove(task);
        }
    }

    /**
     * Searches all photos of the collection for faces again (menu of the
     * collections), also if the background scan is switched off. Faces in the
     * XMP files stay as they are.
     */
    public void rescanFaces(String collectionName) {
        createFaceScan(collectionName, true);
    }

    /**
     * Number of files of the collection in the search index, 0 if it is not
     * indexed (own connection: does not wait for a running scan).
     */
    public java.util.concurrent.CompletableFuture<Integer> fileCount(String collectionName) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            if (terminated || App.getSearchDBConnection() == null) {
                return 0;
            }
            try (Connection con = App.openSearchDBConnection();
                    PreparedStatement stm = con.prepareStatement("SELECT COUNT(*) FROM MEDIAFILES WHERE COLLECTIONNAME=?")) {
                stm.setString(1, collectionName);
                try (ResultSet rs = stm.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (SQLException ex) {
                LOG.log(Level.FINE, "Cannot count the files of " + collectionName, ex);
                return 0;
            }
        });
    }

    /** Called on the FX thread with the collection whose indexed files may have changed. */
    public void setIndexChangedListener(java.util.function.Consumer<String> listener) {
        indexChangedListener = listener;
    }

    private void fireIndexChanged(String collectionName) {
        java.util.function.Consumer<String> listener = indexChangedListener;
        if (listener != null && !terminated) {
            javafx.application.Platform.runLater(() -> listener.accept(collectionName));
        }
    }

    private void finished(Task<Void> task) {
        tasks.remove(task);
        mainViewController.getTaskProgressView().getTasks().remove(task);
    }

    private void scanCollection(ScanTask task, Path root, String collectionName) throws IOException, SQLException {
        Connection con = connection();
        Map<String, Long> indexed = loadIndexed(con, collectionName);
        List<Path> failedDirs = new ArrayList<>();
        con.setAutoCommit(false);
        try (PreparedStatement merge = con.prepareStatement(MERGE_SQL)) {
            int[] pending = {0};
            // for the task list: files checked so far
            int[] checked = {0};
            // files indexed before: the total to expect (unknown at the first scan)
            int total = indexed.size();
            // files and sidecars of the folders being visited: a sidecar may come after its image
            Deque<Map<String, BasicFileAttributes>> folders = new ArrayDeque<>();
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (terminated || task.isCancelled()) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (!dir.equals(root) && isIgnored(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    folders.push(new HashMap<>());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path fileItem, final BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile() && !isIgnored(fileItem)) {
                        folders.peek().put(fileItem.getFileName().toString(), attrs);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                    Map<String, BasicFileAttributes> files = folders.pop();
                    if (e != null) {
                        failedDirs.add(dir);
                    }
                    for (Map.Entry<String, BasicFileAttributes> entry : files.entrySet()) {
                        if (terminated || task.isCancelled()) {
                            return FileVisitResult.TERMINATE;
                        }
                        Path fileItem = dir.resolve(entry.getKey());
                        String path = fileItem.toString();
                        if (FileTypes.isValidType(path) == false) {
                            continue;
                        }
                        long modified = entry.getValue().lastModifiedTime().toMillis();
                        BasicFileAttributes sidecar = files.get(sidecarName(entry.getKey()));
                        if (sidecar != null) {
                            modified = Math.max(modified, sidecar.lastModifiedTime().toMillis());
                        }
                        checked[0]++;
                        Long known = indexed.remove(path);
                        if (known != null && known == modified) {
                            continue;
                        }
                        task.report(progressTitle(checked[0], total), folderOf(collectionName, path));
                        task.progress(checked[0], total);
                        try {
                            if (addToBatch(task, merge, collectionName, fileItem, entry.getValue(), modified)) {
                                pending[0]++;
                            }
                            if (pending[0] >= COMMIT_ROWS) {
                                merge.executeBatch();
                                con.commit();
                                pending[0] = 0;
                            }
                        } catch (SQLException ex) {
                            throw new IOException(ex);
                        }
                    }
                    if (!files.isEmpty()) {
                        task.report(progressTitle(checked[0], total), folderName(collectionName, dir));
                        task.progress(checked[0], total);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    failedDirs.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });
            if (pending[0] > 0) {
                merge.executeBatch();
            }
            if (!failedDirs.isEmpty()) {
                // e.g. a network volume without access permission: its files stay as they are in the index
                LOG.log(Level.WARNING, "Search index of {0}: {1} folders not readable, e.g. {2}", new Object[]{collectionName, failedDirs.size(), failedDirs.get(0)});
            }
            boolean complete = !terminated && !task.isCancelled();
            if (complete) {
                removeVanished(con, indexed.keySet(), failedDirs);
            }
            con.commit();
            if (complete) {
                App.setSEARCHINDEXFINISHED(collectionName, LocalDate.now());
            }
            // e.g. the number of files shown at the collection
            fireIndexChanged(collectionName);
        } catch (IOException | SQLException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(true);
        }
        if (!terminated && !task.isCancelled()) {
            requestPlaces(collectionName);
            // embeddings of named faces, e.g. after the database was built again from the XMP files
            FaceService.get().learnMissingEmbeddings();
            createFaceScan(collectionName, false);
        }
    }

    private static boolean isIgnored(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(".") || name.contains("@");
    }

    /** Name of the sidecar of a file in the same folder (see XmpSidecar). */
    private static String sidecarName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot > 0 ? fileName.substring(0, dot) : fileName) + ".xmp";
    }

    private static Map<String, Long> loadIndexed(Connection con, String collectionName) throws SQLException {
        Map<String, Long> indexed = new HashMap<>();
        try (PreparedStatement stm = con.prepareStatement("SELECT PATHSTORAGE, LASTMODIFIED FROM MEDIAFILES WHERE COLLECTIONNAME=?")) {
            stm.setString(1, collectionName);
            try (ResultSet rs = stm.executeQuery()) {
                while (rs.next()) {
                    indexed.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        return indexed;
    }

    /**
     * Deletes the indexed files that were not found, except those below a
     * folder that could not be read.
     */
    private static void removeVanished(Connection con, Iterable<String> paths, List<Path> failedDirs) throws SQLException {
        try (PreparedStatement delete = con.prepareStatement("DELETE FROM MEDIAFILES WHERE PATHSTORAGE=?")) {
            int pending = 0;
            for (String path : paths) {
                Path p = Path.of(path);
                if (failedDirs.stream().anyMatch(p::startsWith)) {
                    continue;
                }
                delete.setString(1, path);
                delete.addBatch();
                FaceStore.deleteFile(con, path);
                if (++pending >= COMMIT_ROWS) {
                    delete.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                delete.executeBatch();
            }
        }
    }

    /**
     * Reads edits and metadata of the file and adds it to the batch.
     *
     * @return false if the file is no image or video
     */
    private boolean addToBatch(Task<Void> task, PreparedStatement merge, String collectionName, Path fileItem, BasicFileAttributes attrs, long modified) throws SQLException {
        String path = fileItem.toString();
        MediaFile m = new MediaFile();
        m.setName(fileItem.getFileName().toString());
        m.setPathStorage(fileItem);
        m.setCreationTime(attrs.creationTime());
        m.setLastModifyTime(attrs.lastModifiedTime());
        if (FileTypes.isValidVideo(path)) {
            m.setMediaType(MediaFile.MediaTypes.VIDEO);
        } else if (FileTypes.isValidImage(path)) {
            m.setMediaType(MediaFile.MediaTypes.IMAGE);
        } else {
            return false;
        }
        m.readEdits();
        MetadataResult metadata = null;
        try {
            metadata = metadataController.readMetadata(task, m);
        } catch (Throwable ex) {
            // indexed without metadata: name, path and edits are still found
            LOG.log(Level.FINEST, "Cannot read " + m.getName() + " - " + m.getPathStorage(), ex);
        }
        FaceStore.writeFaces(merge.getConnection(), path, m.getFaceRegions());
        setValues(merge, collectionName, m, metadata, modified, attrs.size());
        if (m.getFaceRegions().isEmpty()) {
            // the faces found by the background scan stay searchable
            setText(merge, 12, FaceStore.searchText(merge.getConnection(), path));
        }
        merge.addBatch();
        return true;
    }

    private static void setValues(PreparedStatement stm, String collectionName, MediaFile m, MetadataResult metadata, long modified, long size) throws SQLException {
        stm.setString(1, m.getPathStorage().toString());
        stm.setString(2, collectionName);
        stm.setString(3, m.getName());
        setText(stm, 4, m.titleProperty().get());
        setText(stm, 5, m.getKeywords());
        setText(stm, 6, m.cameraProperty().get());
        stm.setInt(7, m.getRatingProperty().getValue());
        stm.setObject(8, m.getRecordTime());
        FileTime creationTime = null;
        try {
            creationTime = m.getCreationTime();
        } catch (IOException ex) {
        }
        stm.setObject(9, creationTime == null ? null : LocalDateTime.ofInstant(creationTime.toInstant(), ZoneId.systemDefault()));
        setText(stm, 10, m.placeProperty().get());
        setText(stm, 11, m.hasGpsPosition() ? m.getGpsLatPosAsDouble() + ";" + m.getGpsLonPosAsDouble() : null);
        setText(stm, 12, m.facesProperty().get());
        setText(stm, 13, metadata != null ? metadata.asSearchString() : null);
        stm.setLong(14, modified);
        stm.setLong(15, size);
    }

    private static void setText(PreparedStatement stm, int index, String value) throws SQLException {
        if (isBlank(value)) {
            stm.setNull(index, Types.VARCHAR);
        } else {
            stm.setString(index, value);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Called after the edits of a file were saved: writes them into the index
     * and stores the new modification time, so the next scan skips the file.
     * Files not yet indexed are added by the next scan.
     */
    private void updateEdits(MediaFile m) {
        if (terminated || App.getSearchDBConnection() == null || m.getPathStorage() == null) {
            return;
        }
        // read on the calling thread: the properties may change meanwhile
        String path = m.getPathStorage().toString();
        String title = m.titleProperty().get();
        String keywords = m.getKeywords();
        int rating = m.getRatingProperty().getValue();
        String places = m.placeProperty().get();
        String faces = m.facesProperty().get();
        List<FaceRegion> faceRegions = m.getFaceRegions();
        Path sidecar = XmpSidecar.sidecarPath(m);
        try {
            executor.submit(() -> {
                try {
                    long modified = Files.getLastModifiedTime(Path.of(path)).toMillis();
                    if (Files.exists(sidecar)) {
                        modified = Math.max(modified, Files.getLastModifiedTime(sidecar).toMillis());
                    }
                    try (PreparedStatement stm = connection().prepareStatement("UPDATE MEDIAFILES SET TITLE=?, KEYWORDS=?, RATING=?, PLACES=COALESCE(?, PLACES), FACES=?, LASTMODIFIED=? WHERE PATHSTORAGE=?")) {
                        setText(stm, 1, title);
                        setText(stm, 2, keywords);
                        stm.setInt(3, rating);
                        setText(stm, 4, places);
                        setText(stm, 5, faces);
                        stm.setLong(6, modified);
                        stm.setString(7, path);
                        stm.executeUpdate();
                    }
                    // also for files of collections without search: the names teach the recognition
                    FaceStore.writeFaces(connection(), path, faceRegions);
                    if (faceRegions.isEmpty()) {
                        FaceStore.refreshSearchText(connection(), path);
                    } else {
                        // the new names may fit faces the background scan found in other photos
                        FaceService.get().rematchLater();
                    }
                } catch (IOException | SQLException ex) {
                    LOG.log(Level.WARNING, "Cannot update search index of " + path, ex);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            // shut down
        }
    }

    /**
     * Resolves the places of the files of the collection with GPS position
     * but without place, one request per second. Runs apart from the scans:
     * it may take long. The files are taken from the index, so files left
     * by a cancelled run are resolved by the next one; an empty place marks
     * a position without address.
     */
    private void requestPlaces(String collectionName) {
        if (geoPending.add(collectionName) == false) {
            return;
        }
        try {
            geoExecutor.submit(() -> {
                try {
                    resolvePlaces(collectionName);
                } finally {
                    geoPending.remove(collectionName);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            geoPending.remove(collectionName);
        }
    }

    private void resolvePlaces(String collectionName) {
        GeoCoding geoCoding = new GeoCoding();
        try (Connection con = App.openSearchDBConnection();
                PreparedStatement query = con.prepareStatement("SELECT PATHSTORAGE, GPSPOSITION FROM MEDIAFILES WHERE COLLECTIONNAME=? AND PLACES IS NULL AND GPSPOSITION IS NOT NULL");
                PreparedStatement update = con.prepareStatement("UPDATE MEDIAFILES SET PLACES=? WHERE PATHSTORAGE=? AND PLACES IS NULL")) {
            Map<String, String> positions = new HashMap<>();
            query.setString(1, collectionName);
            try (ResultSet rs = query.executeQuery()) {
                while (rs.next()) {
                    positions.put(rs.getString(1), rs.getString(2));
                }
            }
            for (Map.Entry<String, String> position : positions.entrySet()) {
                if (terminated) {
                    return;
                }
                String[] latLon = position.getValue().split(";");
                Address address;
                try {
                    // not geoSearchForGPS: it hides a missing connection and skips west and south
                    address = geoCoding.getNominatimClient().getAddress(Double.parseDouble(latLon[1]), Double.parseDouble(latLon[0]));
                } catch (IOException ex) {
                    // offline: the next scan tries again
                    LOG.log(Level.INFO, "Places of search index not resolved: {0}", ex.getMessage());
                    return;
                } catch (RuntimeException ex) {
                    address = null;
                }
                update.setString(1, address == null || isBlank(address.getDisplayName()) ? "" : address.getDisplayName());
                update.setString(2, position.getKey());
                update.executeUpdate();
                Thread.sleep(GEOCODING_INTERVAL_MS);
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Cannot store places in search index", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = App.openSearchDBConnection();
        }
        return connection;
    }

    public void shutdown() {
        terminated = true;
        // no interrupt: it could hit H2 while writing; the scan checks isCancelled after each file
        tasks.keySet().forEach(task -> task.cancel(false));
        try {
            executor.submit(() -> {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException ex) {
                    }
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            // already shut down
        }
        executor.shutdown();
        geoExecutor.shutdownNow();
        faceExecutor.shutdown();
    }

    /**
     * Removes all files of the collection from the index.
     */
    public void removeCollectionFromSearchDB(String colname) {
        if (App.getSearchDBConnection() == null) {
            return;
        }
        // a running scan of the collection would add the files again
        tasks.forEach((task, collectionName) -> {
            if (collectionName.equals(colname)) {
                task.cancel(false);
            }
        });
        executor.submit(() -> {
            try (PreparedStatement stm = connection().prepareStatement("DELETE FROM MEDIAFILES WHERE COLLECTIONNAME=?")) {
                FaceStore.deleteCollection(connection(), colname);
                stm.setString(1, colname);
                stm.executeUpdate();
                fireIndexChanged(colname);
            } catch (SQLException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        });
    }

    /**
     * Checks some indexed files of the collection at the new location.
     *
     * @return {found, checked}; checked is 0 if the collection is not indexed
     */
    public int[] checkRelocation(String oldRoot, String newRoot, int sample) {
        int[] result = {0, 0};
        Connection con = App.getSearchDBConnection();
        if (con == null) {
            return result;
        }
        try (PreparedStatement stm = con.prepareStatement("SELECT PATHSTORAGE FROM MEDIAFILES WHERE COLLECTIONNAME=? LIMIT " + sample)) {
            stm.setString(1, oldRoot);
            try (ResultSet rs = stm.executeQuery()) {
                while (rs.next()) {
                    String path = rs.getString(1);
                    if (path.startsWith(oldRoot)) {
                        result[1]++;
                        if (Files.exists(Path.of(newRoot + path.substring(oldRoot.length())))) {
                            result[0]++;
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Cannot check relocation of " + oldRoot, ex);
        }
        return result;
    }

    /**
     * The files of the collection were moved to another folder (drive, SMB
     * share): rewrites their paths and the collection instead of indexing
     * them again, then checks the collection at the new location (only files
     * whose modification time changed by the move are read).
     */
    public void relocateCollection(String oldRoot, String newRoot) {
        if (terminated || App.getSearchDBConnection() == null) {
            return;
        }
        tasks.forEach((task, collectionName) -> {
            if (collectionName.equals(oldRoot)) {
                task.cancel(false);
            }
        });
        executor.submit(() -> {
            try {
                Connection con = connection();
                con.setAutoCommit(false);
                try (PreparedStatement stm = con.prepareStatement("UPDATE MEDIAFILES SET PATHSTORAGE = ? || SUBSTRING(PATHSTORAGE, ?), COLLECTIONNAME=? WHERE COLLECTIONNAME=? AND LEFT(PATHSTORAGE, ?) = ?")) {
                    stm.setString(1, newRoot);
                    stm.setInt(2, oldRoot.length() + 1);
                    stm.setString(3, newRoot);
                    stm.setString(4, oldRoot);
                    stm.setInt(5, oldRoot.length());
                    stm.setString(6, oldRoot);
                    int moved = stm.executeUpdate();
                    FaceStore.relocate(con, oldRoot, newRoot);
                    con.commit();
                    LOG.log(Level.INFO, "Search index: {0} files relocated from {1} to {2}", new Object[]{moved, oldRoot, newRoot});
                } catch (SQLException ex) {
                    con.rollback();
                    throw ex;
                } finally {
                    con.setAutoCommit(true);
                }
            } catch (SQLException ex) {
                // e.g. files of the new location are indexed already: drop the old
                // collection, the scan below indexes the new location
                LOG.log(Level.WARNING, "Cannot relocate search index of " + oldRoot + ", it is indexed again", ex);
                try (PreparedStatement stm = connection().prepareStatement("DELETE FROM MEDIAFILES WHERE COLLECTIONNAME=?")) {
                    FaceStore.deleteCollection(connection(), oldRoot);
                    stm.setString(1, oldRoot);
                    stm.executeUpdate();
                } catch (SQLException e) {
                    LOG.log(Level.SEVERE, null, e);
                }
            }
        });
        createCheckSearchIndex(newRoot);
    }

    /**
     * Background face scan of a collection. Gentle on slow computers: starts
     * a minute after the program, does one file at a time and pauses after
     * each file as long as it took; it is shown in the task list only if
     * there is something to do. Cancelled files are done by the next scan.
     */
    private final class FaceScanTask extends Task<Void> {

        private final String collectionName;
        /** started by the user: at once, all files, also if the background scan is switched off */
        private final boolean force;

        FaceScanTask(String collectionName, boolean force) {
            this.collectionName = collectionName;
            this.force = force;
        }

        @Override
        protected Void call() throws Exception {
            FaceService faces = FaceService.get();
            long wait = force ? 0 : faces.scanStartDelay();
            while (wait > 0 && !isCancelled()) {
                Thread.sleep(Math.min(wait, 1000));
                wait = faces.scanStartDelay();
            }
            if (isCancelled() || terminated || (!force && !faces.isAutoScan()) || !faces.modelsAvailable()) {
                return null;
            }
            List<FaceStore.ScanItem> items;
            try (Connection con = App.openSearchDBConnection()) {
                if (force) {
                    FaceStore.forgetScans(con, collectionName);
                }
                items = FaceStore.unscannedFiles(con, collectionName, faces.embedderType().id());
            }
            if (items.isEmpty()) {
                return null;
            }
            updateTitle(I18n.t("faces.scanTitle"));
            javafx.application.Platform.runLater(() -> {
                if (tasks.containsKey(this)) {
                    mainViewController.getTaskProgressView().getTasks().add(this);
                }
            });
            long start = System.currentTimeMillis();
            int done = 0;
            for (FaceStore.ScanItem item : items) {
                if (isCancelled() || terminated || (!force && !faces.isAutoScan())) {
                    break;
                }
                // the counter in the title: the message (folder) is cut off at its start if too long
                updateTitle(I18n.t("faces.scanProgress", done + 1, items.size()));
                updateMessage(folderOf(collectionName, item.path()));
                updateProgress(done, items.size());
                long t0 = System.nanoTime();
                faces.scanFile(item).join();
                done++;
                // pause as long as the file took: half of one core at most
                long took = (System.nanoTime() - t0) / 1_000_000;
                if (took > 0 && !isCancelled()) {
                    Thread.sleep(Math.min(took, 5000));
                }
            }
            faces.rematchLater();
            LOG.log(Level.FINE, "Face scan of {0}: {1} of {2} files in {3} ms",
                    new Object[]{collectionName, done, items.size(), System.currentTimeMillis() - start});
            return null;
        }
    }

    /**
     * The folder of the file from the folder of the collection on (e.g.
     * "Bergfotos/2023/Juli"), for the task list.
     */
    static String folderOf(String collectionName, String path) {
        Path folder = Path.of(path).getParent();
        return folder == null ? "" : folderName(collectionName, folder);
    }

    /** "Search index: 1234 / 5300" (without total at the first scan of a collection). */
    private static String progressTitle(int checked, int total) {
        return total > 0 ? I18n.t("search.checkedOf", checked, Math.max(checked, total))
                : I18n.t("search.checkedCount", checked);
    }

    /** The folder from the folder of the collection on (see {@link #folderOf}). */
    static String folderName(String collectionName, Path folder) {
        Path root = Path.of(collectionName);
        if (folder.startsWith(root)) {
            Path name = root.getFileName();
            Path rel = root.relativize(folder);
            String head = name == null ? root.toString() : name.toString();
            return rel.toString().isEmpty() ? head : head + "/" + rel.toString().replace('\\', '/');
        }
        return folder.toString();
    }

    private final class ScanTask extends Task<Void> {

        private final Path root;
        private final String collectionName;

        ScanTask(Path root, String collectionName) {
            this.root = root;
            this.collectionName = collectionName;
        }

        @Override
        protected Void call() throws Exception {
            if (isCancelled()) {
                return null;
            }
            // an unmounted drive must not empty the index of the collection
            if (Files.isDirectory(root) == false) {
                return null;
            }
            long start = System.currentTimeMillis();
            updateTitle(I18n.t("search.checkingIndex"));
            scanCollection(this, root, collectionName);
            LOG.log(Level.FINE, "Search index of {0} {1} after {2} ms", new Object[]{collectionName, isCancelled() ? "cancelled" : "checked", System.currentTimeMillis() - start});
            return null;
        }

        void report(String title, String message) {
            updateTitle(title);
            updateMessage(message);
        }

        /** Progress bar; stays indeterminate without total. */
        void progress(int done, int total) {
            if (total > 0) {
                updateProgress(Math.min(done, total), total);
            }
        }
    }

}
