/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browserlighttable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private ExecutorService executorParallel;
    private final int loadingLimit;
    private List<MediaFile> cacheList;
    private List<Thread> loadingThreads;
    private boolean loadedFromCache;
    
    public MediaLoadingTask(ObservableList<MediaFile> fullMediaList, MediaGridCellFactory factory, Path sPath, MainViewController mainControllerParam, Label mediaQTYLabelParam, String sortParm, MetadataController metaControllerParam) {
        selectedPath = sPath;
        fileLoader = new MediaFileLoader();
        mediaQTYLabel = mediaQTYLabelParam;
        mainController = mainControllerParam;
        sort = sortParm;
        metadataController = metaControllerParam;
        this.factory = factory;
        this.fullMediaList = fullMediaList;
        cacheList = new ArrayList<>();
        loadingThreads = new ArrayList<>();
        loadedFromCache = false;
        executorParallel = Executors.newVirtualThreadPerTaskExecutor();
        //executorParallel = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setPriority(8).setNamePrefix("lightTableControllerSelectionMediaLoading").build());
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
            restoreCacheFromDisk(cacheList, inPath);
            updateTitle("Reading cache...finished");
            
            updateTitle("Counting mediafiles...");
            
            Stream<Path> fileList = Files.list(selectedPath).filter((t) -> {
                return FileTypes.isValidType(t.getFileName().toString());
            }).sorted(new FilenameComparator());
            
            Stream<Path> fileListCount = Files.list(selectedPath).filter((t) -> {
                return FileTypes.isValidType(t.getFileName().toString());
            }).sorted(new FilenameComparator());
            
            Stream<Path> finalFileList;
            if (cacheList.isEmpty() == true) {
                finalFileList = fileList;
            } else {
                List<Path> cacheListStream = cacheList.stream().map(MediaFile::getPathStorage).collect(Collectors.toList());
                Stream<Path> diffFileList = fileList
                        .filter(element -> !cacheListStream.contains(element));
                //list of edit files                
                long start = System.currentTimeMillis();
                Stream<Path> editFileList = cacheList.stream().filter((t) -> {
                    if (t != null) {
                        if (!t.getFilterList().isEmpty()) {
                            return true;
                        }
                    }
                    return false;
                }).map(MediaFile::getPathStorage);
                finalFileList = Stream.concat(diffFileList, editFileList);
                long end = System.currentTimeMillis();
                loadedFromCache = true;
                factory.setListFilesActive(false);
                Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.FINE, "Calculatiing difference between cache and disk took " + (end - start) / 1000 + "s");
            }
            
            qty = fileListCount.count();
            
            if (qty < cacheList.size()) {
                Stream<Path> fileListNew = Files.list(selectedPath).filter((t) -> {
                    return FileTypes.isValidType(t.getFileName().toString());
                }).sorted(new FilenameComparator());
                finalFileList = fileListNew;
                loadedFromCache = false;
                cacheList.clear();
                Platform.runLater(() -> {
                    fullMediaList.clear();
                });
                File outpath = new File(Utility.getAppData() + File.separatorChar + "cache" + File.separatorChar + createMD5Hash(selectedPath.toString()) + "-" + selectedPath.toFile().getName() + ".bin");
                outpath.delete();
            }
            
            updateTitle("Counting mediafiles...finished");
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
            finalFileList.parallel().forEach((fileItem) -> {
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
                            
                            if (fullMediaList.contains(m) == false) {
                                if (Utility.nativeMemorySize > 4194500) {
                                    executorParallel.submit(() -> {
                                        try {
                                            loadItem(fileItem, m, Thread.currentThread());
                                            updateValue(m);
                                            if (cacheList.contains(m) == false) {
                                                cacheList.add(m);
                                            }
                                        } catch (IOException ex) {
                                            m.setMediaType(MediaFile.MediaTypes.NONE);
                                        }
                                    });
                                } else {
                                    try {
                                        loadItem(fileItem, m, null);
                                        updateValue(m);
                                        if (cacheList.contains(m) == false) {
                                            cacheList.add(m);
                                        }
                                    } catch (IOException ex) {
                                        m.setMediaType(MediaFile.MediaTypes.NONE);
                                    }
                                }
                            }
                        }
                    }
                    if (loadedFromCache == false) {
                        updateTitle("Loading..." + iatom.get() + " / " + qty);
                        updateMessage(iatom.get() + " / " + qty);
                    } else {
                        updateTitle("Checking cache..." + iatom.get());
                        updateMessage(iatom.get() + "");
                    }
                    iatom.addAndGet(1);
                }
            });
            if (this.isCancelled()) {
                return null;
            }

            //Save cache...
            Thread.ofVirtual().start(() -> {
                saveCacheToDisk();
            });
            long endtime = System.currentTimeMillis();
            updateMessage("Finished MediaLoading Task.");
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.INFO, "Collect Time in s: " + (endtime - starttime) / 1000 + " " + selectedPath);
        } catch (IOException ex) {
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    private void restoreCacheFromDisk(List<MediaFile> cacheList, File inPath) throws NoSuchAlgorithmException {
        //restore cache        
        FileInputStream fileInputStream;
        try {
            fileInputStream = new FileInputStream(inPath);
            ObjectInputStream objectInputStream
                    = new ObjectInputStream(fileInputStream);
            List<MediaFile> e2 = (ArrayList<MediaFile>) objectInputStream.readObject();
            objectInputStream.close();
            cacheList.addAll(e2);
            Platform.runLater(() -> {
                fullMediaList.addAll(cacheList);
            });
        } catch (IOException | ClassNotFoundException ex) {
            inPath.delete();
        }
    }
    
    public void saveCacheToDisk() {
        updateTitle("Saving cache...");
        updateMessage("Saving cache...");
        try {
            //save cache to disk
            File outpath = new File(Utility.getAppData() + File.separatorChar + "cache" + File.separatorChar + createMD5Hash(selectedPath.toString()) + "-" + selectedPath.toFile().getName() + ".bin");
            long start = System.currentTimeMillis();
            executorParallel.close();
            
            FileOutputStream fileOutputStream;
            try {
                fileOutputStream = new FileOutputStream(outpath, false);
                ObjectOutputStream objectOutputStream
                        = new ObjectOutputStream(fileOutputStream);
                updateMessage("Save cache...Writing to disk");
                objectOutputStream.writeObject(cacheList);
                objectOutputStream.flush();
                objectOutputStream.close();
                updateMessage("Save cache...finished");
            } catch (FileNotFoundException ex) {
                Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
            }
            long end = System.currentTimeMillis();
            Platform.runLater(() -> {
                new Utility().hideNodeAfterTime(mainController.getStatusLabelRight(), 2, true);
            });
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.INFO, "Save to disk took " + (end - start) / 1000 + "s");
            //end save to disk
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
        }
        //end save to disk
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
                    metadataController.readBasicMetadata(this, m);
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
            Platform.runLater(() -> {
                fullMediaList.add(v);
            });
        }
    }
    
    @Override
    protected void succeeded() {
        super.succeeded();
        executorParallel.shutdown();
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
