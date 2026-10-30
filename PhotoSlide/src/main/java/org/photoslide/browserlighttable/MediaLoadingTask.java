/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browserlighttable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.photoslide.datamodel.MediaFileLoader;
import org.photoslide.MainViewController;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.browsermetadata.MetadataController;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
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
    private final MediaFileLoader fileLoader;
    // own pool for the parallel loading: the common pool stays free, and its
    // daemon threads are bounded and do not keep the JVM alive on exit
    private final ForkJoinPool loadingPool;
    private final int loadingLimit;
    private List<MediaFile> cacheList;
    private List<Thread> loadingThreads;
    private boolean loadedFromCache;
    private final LighttableController lightcontroller;
    private List<Path> dummyfileList;    
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

    public MediaLoadingTask(ObservableList<MediaFile> fullMediaList, MediaGridCellFactory factory, Path sPath, MainViewController mainControllerParam, Label mediaQTYLabelParam, String sortParm, MetadataController metaControllerParam, LighttableController lightcontroller) {
        selectedPath = sPath;        
        fileLoader = new MediaFileLoader();
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
        dummyfileList = new ArrayList<>();
        loadingThreads = new ArrayList<>();
        loadedFromCache = false;
        this.lightcontroller = lightcontroller;
        sortKeys = lightcontroller.getFilenameComparator();
        loadingPool = new ForkJoinPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
        if (Utility.nativeMemorySize > 4194500) {
            loadingLimit = 75;
        } else {
            loadingLimit = 100;
        }
    }

    @Override
    protected MediaFile call() throws Exception {
        final long qty;
        try {

            updateTitle("Reading cache...");
            File inPath = new File(Utility.getAppData() + File.separatorChar + "cache" + File.separatorChar + createMD5Hash(selectedPath.toString()) + "-" + selectedPath.toFile().getName() + ".bin");
            restoreCacheFromDisk(inPath);
            updateTitle("Reading cache...finished");

            updateTitle("Counting mediafiles...");

            try (Stream<Path> fileListCount = Files.list(selectedPath)) {
                qty = fileListCount.filter((t5) -> FileTypes.isValidType(t5.getFileName().toString())).count();
            }
            updateTitle("Counting mediafiles...finished");
                        
            
            updateTitle("Preparing mediafile list...");

            Stream<Path> fileList = Files.list(selectedPath).filter((t) -> {
                return FileTypes.isValidType(t.getFileName().toString());
            }).sorted(new FilenameComparator());

            Stream<Path> finalFileList;
            if (cacheList.isEmpty() == true) {
                finalFileList = fileList;
            } else {
                Stream<Path> diffFileList = fileList
                        .filter(element -> !cachedPaths.contains(element));
                //list of edit files                
                long start = System.currentTimeMillis();

                //edit file list
                dummyfileList = Files.list(selectedPath).filter((t) -> {
                    String n = t.getFileName().toString();
                    return n.startsWith("Ω") || n.toLowerCase().endsWith(".xmp");
                }).sorted(new FilenameComparator()).collect(Collectors.toList());
                Stream<Path> editFileList = cacheList.stream().filter((t) -> {
                    boolean retVal = false;
                    if (t != null) {
                        if (!t.getFilterList().isEmpty()) {
                            retVal = true;
                        }
                        if (dummyfileList.contains(Path.of(FileTypes.convertEditNameToFilename(t.getPathStorage().toString())))) {
                            retVal = true;
                        }
                    }
                    return retVal;
                }).map(MediaFile::getPathStorage);
                finalFileList = diffFileList;//Stream.concat(diffFileList, editFileList);
                long end = System.currentTimeMillis();
                loadedFromCache = true;
                Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.FINE, "Calculating difference between cache and disk took " + (end - start) / 1000 + "s");
            }

            if (qty < cacheList.size()) {
                Stream<Path> fileListNew = Files.list(selectedPath).filter((t) -> {
                    return FileTypes.isValidType(t.getFileName().toString());
                }).sorted(new FilenameComparator());
                finalFileList = fileListNew;
                loadedFromCache = false;
                cacheList.clear();
                cachedPaths.clear();
                Platform.runLater(() -> {
                    fullMediaList.clear();
                });
                File outpath = new File(Utility.getAppData() + File.separatorChar + "cache" + File.separatorChar + createMD5Hash(selectedPath.toString()) + "-" + selectedPath.toFile().getName() + ".bin");
                outpath.delete();
            }
            updateTitle("Preparing mediafile list...finished");
            if (qty == 0) {
                updateTitle("0 mediafiles found.");
                Platform.runLater(() -> {
                    mainController.getProgressPane().setVisible(false);
                    mainController.getStatusLabelLeft().setVisible(false);
                    mediaQTYLabel.setText(qty + " media files.");
                });
                return null;
            } else {
                Platform.runLater(() -> {
                    mainController.getProgressPane().setVisible(true);
                    mainController.getStatusLabelLeft().setVisible(true);
                    mediaQTYLabel.setText(qty + " media files");
                    mainController.getStatusLabelRight().setVisible(true);
                });
                updateTitle(qty + " files found - Loading...");
            }
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.INFO, "Starting collecting..." + selectedPath);
            long starttime = System.currentTimeMillis();

            AtomicInteger iatom = new AtomicInteger(1);
            List<Path> finalCacheList = finalFileList.toList();

            ForkJoinTask<?> loading = loadingPool.submit(() -> finalCacheList.parallelStream().forEach((fileItem) -> {
                if (this.isCancelled()) {
                    return;
                }
                if (this.isCancelled() == false) {
                    if (Files.isDirectory(fileItem) == false) {
                        if (FileTypes.isValidType(fileItem.toString())) {
                            MediaFile m = new MediaFile();
                            m.setName(fileItem.getFileName().toString());
                            m.setPathStorage(fileItem);
                            m.setMediaType(MediaFile.MediaTypes.IMAGE);

                            if (cachedPaths.contains(fileItem) == false) {
                                try {
                                    loadItem(fileItem, m, null);
                                    updateValue(m);
                                    if (cachedPaths.add(fileItem)) {
                                        cacheList.add(m);
                                    }
                                } catch (IOException ex) {
                                    m.setMediaType(MediaFile.MediaTypes.NONE);
                                }
                            } else {
                                m.readEdits();
                                updateValue(m);
                                if (cachedPaths.add(fileItem)) {
                                    cacheList.add(m);
                                } else {
                                    cacheList.set(cacheList.indexOf(m), m);
                                }
                            }
                        }
                    }
                    if (loadedFromCache == false) {
                        updateTitle("Loading..." + iatom.get() + " / " + qty);
                        updateMessage(iatom.get() + " / " + qty);
                    } else {
                        updateTitle("Checking cache..." + iatom.get() + "/" + cacheList.size());
                        updateMessage("Checking cache..." + iatom.get() + "/" + cacheList.size());
                    }
                    iatom.addAndGet(1);
                }
            }));
            try {
                loading.get();
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
            fileList.close();
            updateMessage("Finished MediaLoading Task.");
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.FINE, "Collect Time in s: " + (endtime - starttime) / 1000 + " " + selectedPath);
            updateMessage("Sorting media list...");
        } catch (IOException ex) {
            Platform.runLater(() -> {
                new Utility().hideNodeAfterTime(mainController.getStatusLabelRight(), 2, true);
            });
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private void restoreCacheFromDisk(File inPath) {        
        //restore cache   
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(inPath);
            List<MediaFile> e2;
            try (ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
                e2 = (List<MediaFile>) objectInputStream.readObject();
                // the cache is only written after loading a folder: the edits
                // made since then are in the XMP sidecars, which are the reference
                e2.parallelStream().forEach(MediaFile::readEdits);
                //List<MediaFile> collect = e2.parallelStream().filter((t) -> Files.exists(t.getPathStorage()) == true).collect(Collectors.toList());                
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
        } catch (IOException | ClassNotFoundException | ClassCastException | NullPointerException ex) {
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
                updateMessage("Prepare saving cache...finished.");
                updateMessage("Saving cache...");                
                //save cache to disk
                long start = System.currentTimeMillis();

                FileOutputStream fileOutputStream;
                try {
                    fileOutputStream = new FileOutputStream(outpath, false);
                    try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(new BufferedOutputStream(fileOutputStream))) {
                        updateMessage("Save cache...Writing to disk");
                        // show progress during writing wiht filteredOutputStream                         
                        final List<MediaFile> finalCache = Collections.synchronizedList(cacheList);
                        objectOutputStream.writeObject(finalCache);
                        objectOutputStream.flush();
                    }
                    updateMessage("Save cache...finished");
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
        mainController.getStatusLabelRight().setText("Prepare saving cache.....Checking directory");
        mainController.getStatusLabelRight().textProperty().bind(safeDiskTask.messageProperty());
        Thread.ofVirtual().start(safeDiskTask);

    }

    public void loadItem(Path fileItem, MediaFile m, Thread th) throws IOException {
        if (this.isCancelled()) {
            return;
        }
        m.readEdits();
        if (this.isCancelled()) {
            return;
        }
        m.getCreationTime();
        if (this.isCancelled()) {
            return;
        }
        if (mainController.isMediaFileBookmarked(m)) {
            m.setBookmarked(true);
        }
        if (this.isCancelled()) {
            return;
        }
        if (FileTypes.isValidVideo(fileItem.toString())) {
            m.setMediaType(MediaFile.MediaTypes.VIDEO);
            if (this.isCancelled()) {
                return;
            }
            if (this.isCancelled()) {
                return;
            }
        } else if (FileTypes.isValidImage(fileItem.toString())) {
            m.setMediaType(MediaFile.MediaTypes.IMAGE);
            if (sort.equalsIgnoreCase("Capture time")) {
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
            if (this.isCancelled()) {
                return;
            }
            if (this.isCancelled()) {
                return;
            }
        } else {
            m.setMediaType(MediaFile.MediaTypes.NONE);
        }
        if (th != null) {
            loadingThreads.remove(th);
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
