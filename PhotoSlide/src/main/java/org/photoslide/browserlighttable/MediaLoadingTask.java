/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browserlighttable;


import org.photoslide.I18n;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.photoslide.MainViewController;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.browsermetadata.MetadataController;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.Utility;
import org.photoslide.browsercollections.FilenameComparator;

/**
 *
 * @author selfemp
 */
public class MediaLoadingTask extends Task<MediaFile> {

    private final Path selectedPath;
    private final Label mediaQTYLabel;
    private final MainViewController mainController;
    private final String sort;
    private final MetadataController metadataController;
    private final MediaGridCellFactory factory;
    private final ObservableList<MediaFile> fullMediaList;
    // the work per file blocks on the file system (few small reads), it is
    // not CPU bound: 4 threads also on a dual core, at most 8 so that a
    // network share or a hard disk is not flooded with requests.
    // Platform threads on purpose: file I/O pins virtual threads, the JDK
    // would add carrier threads and the number of requests would be unbounded.
    private static final int LOADING_THREADS = Math.clamp(Runtime.getRuntime().availableProcessors(), 4, 8);
    // own pool: the common pool stays free, daemon threads do not keep the JVM alive on exit
    private final ExecutorService loadingPool;
    private final List<MediaFile> cacheList;
    private boolean loadedFromCache;
    // paths in cacheList, so the loading loop never searches a list
    private final Set<Path> cachedPaths;
    // sort keys are computed here so the FX thread only compares
    private final MediaFilenameComparator sortKeys;
    // loaded media files are handed to the FX thread in batches: one
    // runLater per file floods the FX thread for large folders
    // each batch makes the grid rebuild its visible cells
    private static final long FLUSH_INTERVAL_MS = 300;
    private final ConcurrentLinkedQueue<MediaFile> pendingAdds;
    private final AtomicBoolean flushScheduled;
    private volatile long lastFlush;

    /**
     * Result of the one directory listing of the folder: on a network share
     * every access takes milliseconds, so the folder is listed once and the
     * file attributes and the edit files found there are used for all files.
     *
     * @param media valid media files, sorted by file name
     * @param attributes attributes of the media files from the listing
     * @param editFileNames lower case names of the .xmp and .edit files
     */
    private record FolderScan(List<Path> media, Map<Path, BasicFileAttributes> attributes, Set<String> editFileNames) {
    }

    public MediaLoadingTask(ObservableList<MediaFile> fullMediaList, MediaGridCellFactory factory, Path sPath, MainViewController mainControllerParam, Label mediaQTYLabelParam, String sortParm, MetadataController metaControllerParam, LighttableController lightcontroller) {
        selectedPath = sPath;
        mediaQTYLabel = mediaQTYLabelParam;
        mainController = mainControllerParam;
        sort = sortParm;
        metadataController = metaControllerParam;
        this.factory = factory;
        this.fullMediaList = fullMediaList;
        cacheList = Collections.synchronizedList(new ArrayList<>());
        cachedPaths = ConcurrentHashMap.newKeySet();
        pendingAdds = new ConcurrentLinkedQueue<>();
        flushScheduled = new AtomicBoolean(false);
        lastFlush = 0;
        loadedFromCache = false;
        sortKeys = lightcontroller.getFilenameComparator();
        loadingPool = Executors.newFixedThreadPool(LOADING_THREADS,
                new ThreadFactoryBuilder().setNamePrefix("mediaLoading").setDaemon(true).build());
    }

    @Override
    protected MediaFile call() throws Exception {
        final long qty;
        try {
            updateTitle(I18n.t("loading.readingFolder"));
            FolderScan scan = scanFolder();
            if (this.isCancelled()) {
                return null;
            }
            qty = scan.media().size();
            updateTitle(I18n.t("loading.readingFolderFinished"));

            updateTitle(I18n.t("loading.readingCache"));
            File inPath = new File(Utility.getAppData() + File.separatorChar + "cache" + File.separatorChar + createMD5Hash(selectedPath.toString()) + "-" + selectedPath.toFile().getName() + ".bin");
            restoreCacheFromDisk(inPath, scan);
            if (this.isCancelled()) {
                return null;
            }
            updateTitle(I18n.t("loading.readingCacheFinished"));

            updateTitle(I18n.t("loading.preparing"));
            List<Path> toLoad;
            if (cacheList.isEmpty()) {
                toLoad = scan.media();
            } else if (qty < cacheList.size()) {
                // files were deleted since the cache was written: load everything again
                toLoad = scan.media();
                loadedFromCache = false;
                cacheList.clear();
                cachedPaths.clear();
                Platform.runLater(() -> {
                    fullMediaList.clear();
                });
                File outpath = new File(Utility.getAppData() + File.separatorChar + "cache" + File.separatorChar + createMD5Hash(selectedPath.toString()) + "-" + selectedPath.toFile().getName() + ".bin");
                outpath.delete();
            } else {
                toLoad = scan.media().stream().filter(p -> !cachedPaths.contains(p)).toList();
                loadedFromCache = true;
            }
            updateTitle(I18n.t("loading.preparingFinished"));
            if (qty == 0) {
                updateTitle(I18n.t("loading.noneFound"));
                Platform.runLater(() -> {
                    mainController.getProgressPane().setVisible(false);
                    mainController.getStatusLabelLeft().setVisible(false);
                    mediaQTYLabel.setText(I18n.t("lighttable.mediaQty", qty));
                });
                return null;
            } else {
                Platform.runLater(() -> {
                    mainController.getProgressPane().setVisible(true);
                    mainController.getStatusLabelLeft().setVisible(true);
                    mediaQTYLabel.setText(I18n.t("lighttable.mediaQty", qty));
                    mainController.getStatusLabelRight().setVisible(true);
                });
                updateTitle(I18n.t("lighttable.filesFoundLoading", qty));
            }
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.INFO, "Starting collecting..." + selectedPath);
            long starttime = System.currentTimeMillis();

            AtomicInteger iatom = new AtomicInteger(1);
            try {
                processInOrder(toLoad, (fileItem) -> {
                    MediaFile m = new MediaFile();
                    m.setName(fileItem.getFileName().toString());
                    m.setPathStorage(fileItem);
                    m.setMediaType(MediaFile.MediaTypes.IMAGE);
                    BasicFileAttributes attr = scan.attributes().get(fileItem);
                    if (attr != null) {
                        m.setFileTimes(attr);
                    }
                    try {
                        loadItem(fileItem, m, scan.editFileNames());
                        updateValue(m);
                        if (cachedPaths.add(fileItem)) {
                            cacheList.add(m);
                        }
                    } catch (IOException ex) {
                        m.setMediaType(MediaFile.MediaTypes.NONE);
                    }
                    if (loadedFromCache == false) {
                        updateTitle(I18n.t("loading.progress", iatom.get(), qty));
                        updateMessage(iatom.get() + " / " + qty);
                    } else {
                        updateTitle(I18n.t("loading.checkingCache", iatom.get(), cacheList.size()));
                        updateMessage(I18n.t("loading.checkingCache", iatom.get(), cacheList.size()));
                    }
                    iatom.incrementAndGet();
                });
            } catch (InterruptedException | ExecutionException ex) {
                // cancel() interrupts this thread
                if (this.isCancelled()) {
                    return null;
                }
                Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
            }
            if (this.isCancelled()) {
                return null;
            }
            long endtime = System.currentTimeMillis();
            updateMessage(I18n.t("lighttable.loadingFinished"));
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.FINE, "Collect Time in ms: " + (endtime - starttime) + " " + selectedPath);
            updateMessage(I18n.t("lighttable.sorting"));
        } catch (IOException ex) {
            Platform.runLater(() -> {
                new Utility().hideNodeAfterTime(mainController.getStatusLabelRight(), 2, true);
            });
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    /**
     * Lists the folder once. Under Windows the attributes come with the
     * listing, elsewhere it is one access per file (instead of one for the
     * times plus three for the edit files of every media file).
     */
    private FolderScan scanFolder() throws IOException {
        List<Path> media = new ArrayList<>();
        Map<Path, BasicFileAttributes> attributes = new HashMap<>();
        Set<String> editFileNames = new HashSet<>();
        Files.walkFileTree(selectedPath, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
                if (isCancelled()) {
                    return FileVisitResult.TERMINATE;
                }
                String name = file.getFileName().toString();
                String lower = name.toLowerCase();
                if (lower.endsWith(".xmp") || lower.endsWith(".edit")) {
                    editFileNames.add(lower);
                } else if (!attr.isDirectory() && FileTypes.isValidType(name)) {
                    media.add(file);
                    // a link has the times of the link, not of the file: read them later
                    if (!attr.isSymbolicLink()) {
                        attributes.put(file, attr);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
        media.sort(new FilenameComparator());
        return new FolderScan(media, attributes, editFileNames);
    }

    /**
     * Runs the action for all items with the loading threads, in the order of
     * the list: the files are sorted like the grid, so the first rows are
     * filled first (a parallel stream splits the list into chunks and fills
     * the grid at several places at once).
     */
    private <T> void processInOrder(List<T> items, Consumer<T> action) throws InterruptedException, ExecutionException {
        AtomicInteger next = new AtomicInteger();
        int workers = Math.min(LOADING_THREADS, items.size());
        List<Future<?>> running = new ArrayList<>(workers);
        for (int w = 0; w < workers; w++) {
            running.add(loadingPool.submit(() -> {
                int i;
                while (!isCancelled() && (i = next.getAndIncrement()) < items.size()) {
                    action.accept(items.get(i));
                }
            }));
        }
        for (Future<?> f : running) {
            f.get();
        }
    }

    private void restoreCacheFromDisk(File inPath, FolderScan scan) {
        if (!inPath.exists()) {
            return;
        }
        //restore cache
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(inPath);
            List<MediaFile> e2;
            try (ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
                e2 = (List<MediaFile>) objectInputStream.readObject();
                // the cache is only written after loading a folder: the edits
                // made since then are in the XMP sidecars, which are the reference;
                // only files with an edit file in the listing are read
                processInOrder(e2, (m) -> {
                    m.readEdits(scan.editFileNames());
                    BasicFileAttributes attr = scan.attributes().get(m.getPathStorage());
                    if (attr != null) {
                        m.setFileTimes(attr);
                    }
                });
                if (this.isCancelled()) {
                    return;
                }
                cacheList.addAll(e2);
                e2.forEach((m) -> {
                    cachedPaths.add(m.getPathStorage());
                    sortKeys.prepare(m);
                });
                Platform.runLater(() -> {
                    fullMediaList.addAll(e2);
                    factory.setListFilesActive(false);
                });
            }
        } catch (InterruptedException ex) {
            // cancelled while reading the edits
            Thread.currentThread().interrupt();
        } catch (IOException | ClassNotFoundException | ClassCastException | NullPointerException | ExecutionException ex) {
            inPath.delete();
        }
    }

    public synchronized void saveCacheToDisk() {
        String hash = "";        
        try {
            hash = createMD5Hash(selectedPath.toString());
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, "Hash calculation algorithm exception", ex);
        }
        File outpath = new File(Utility.getAppData() + File.separatorChar + "cache" + File.separatorChar + hash + "-" + selectedPath.toFile().getName() + ".bin");
        Task<Void> safeDiskTask = new Task() {
            @Override
            protected Object call() throws Exception {
                updateMessage(I18n.t("loading.prepareSaveCacheFinished"));
                updateMessage(I18n.t("loading.savingCache"));                
                //save cache to disk
                long start = System.currentTimeMillis();

                FileOutputStream fileOutputStream;
                try {
                    fileOutputStream = new FileOutputStream(outpath, false);
                    try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(fileOutputStream))) {
                        updateMessage(I18n.t("loading.saveCacheWriting"));
                        // show progress during writing wiht filteredOutputStream                         
                        final List<MediaFile> finalCache = Collections.synchronizedList(cacheList);
                        objectOutputStream.writeObject(finalCache);
                        objectOutputStream.flush();
                    }
                    updateMessage(I18n.t("loading.saveCacheFinished"));
                } catch (IOException | NullPointerException ex) {
                    outpath.deleteOnExit();
                    Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
                }
                long end = System.currentTimeMillis();
                Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.INFO, "Save to disk took " + (end - start) / 1000 + "s");
                //end save to disk                
                return null;
            }
        };
        safeDiskTask.setOnSucceeded((p2) -> {
            new Utility().hideNodeAfterTime(mainController.getStatusLabelRight(), 5, true);
        });
        safeDiskTask.setOnFailed((p3) -> {
            new Utility().hideNodeAfterTime(mainController.getStatusLabelRight(), 5, true);
        });
        safeDiskTask.setOnCancelled((t) -> {
            outpath.deleteOnExit();
        });
        // still bound to the loading task when called later, e.g. after the capture time sort
        mainController.getStatusLabelRight().textProperty().unbind();
        mainController.getStatusLabelRight().setText(I18n.t("loading.prepareSaveCacheChecking"));
        mainController.getStatusLabelRight().textProperty().bind(safeDiskTask.messageProperty());
        Thread.ofVirtual().start(safeDiskTask);

    }

    private void loadItem(Path fileItem, MediaFile m, Set<String> editFileNames) throws IOException {
        if (this.isCancelled()) {
            return;
        }
        m.readEdits(editFileNames);
        if (this.isCancelled()) {
            return;
        }
        // no file access if the times came with the folder listing
        m.getCreationTime();
        if (mainController.isMediaFileBookmarked(m)) {
            m.setBookmarked(true);
        }
        if (this.isCancelled()) {
            return;
        }
        if (FileTypes.isValidVideo(fileItem.toString())) {
            m.setMediaType(MediaFile.MediaTypes.VIDEO);
        } else if (FileTypes.isValidImage(fileItem.toString())) {
            m.setMediaType(MediaFile.MediaTypes.IMAGE);
            if (sort.equalsIgnoreCase(I18n.t("lighttable.sort.captureTime"))) {
                try {
                    if (MetadataController.readsCaptureTime(m)) {
                        m.setRecordTime(MetadataController.readCaptureTime(m));
                    } else {
                        metadataController.readBasicMetadata(this, m);
                    }
                } catch (IOException ex) {
                    Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } else {
            m.setMediaType(MediaFile.MediaTypes.NONE);
        }
    }

    @Override
    protected void updateValue(MediaFile v) {
        if (v != null) {
            super.updateValue(v);
            sortKeys.prepare(v);
            pendingAdds.add(v);
            scheduleFlush();
        }
    }

    private void scheduleFlush() {
        if (flushScheduled.compareAndSet(false, true)) {
            long wait = FLUSH_INTERVAL_MS - (System.currentTimeMillis() - lastFlush);
            if (wait <= 0) {
                Platform.runLater(this::flushPendingAdds);
            } else {
                // Platform::runLater as executor: the default would run it in the
                // common pool, which can be busy with parallel streams
                CompletableFuture.delayedExecutor(wait, TimeUnit.MILLISECONDS, Platform::runLater).execute(this::flushPendingAdds);
            }
        }
    }

    private void flushPendingAdds() {
        flushScheduled.set(false);
        lastFlush = System.currentTimeMillis();
        if (this.isCancelled()) {
            pendingAdds.clear();
            return;
        }
        List<MediaFile> batch = new ArrayList<>();
        MediaFile m;
        while ((m = pendingAdds.poll()) != null) {
            batch.add(m);
        }
        if (!batch.isEmpty()) {
            fullMediaList.addAll(batch);
        }
    }

    @Override
    protected void succeeded() {
        super.succeeded();
        loadingPool.shutdown();
    }

    @Override
    protected void cancelled() {
        super.cancelled();
        loadingPool.shutdownNow();
    }

    @Override
    protected void failed() {
        super.failed();
        loadingPool.shutdownNow();
    }

    public String createMD5Hash(final String input)
            throws NoSuchAlgorithmException {

        String hashtext = null;
        MessageDigest md = MessageDigest.getInstance("MD5");
        // Compute message digest of the input
        byte[] messageDigest = md.digest(input.getBytes());

        hashtext = convertToHex(messageDigest);

        return hashtext;
    }

    private String convertToHex(final byte[] messageDigest) {
        BigInteger bigint = new BigInteger(1, messageDigest);
        String hexText = bigint.toString(16);
        while (hexText.length() < 32) {
            hexText = "0".concat(hexText);
        }
        return hexText;
    }

}
