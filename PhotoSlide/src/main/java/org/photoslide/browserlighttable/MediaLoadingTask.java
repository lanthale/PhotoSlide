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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
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
    private final MediaFileLoader fileLoader;
    private ExecutorService executorParallel;
    private final int loadingLimit;

    public MediaLoadingTask(ObservableList<MediaFile> fullMediaList, MediaGridCellFactory factory, Path sPath, MainViewController mainControllerParam, Label mediaQTYLabelParam, String sortParm, MetadataController metaControllerParam) {
        selectedPath = sPath;
        fileLoader = new MediaFileLoader();
        mediaQTYLabel = mediaQTYLabelParam;
        mainController = mainControllerParam;
        sort = sortParm;
        metadataController = metaControllerParam;
        this.factory = factory;
        this.fullMediaList = fullMediaList;
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setPriority(8).setNamePrefix("lightTableControllerSelectionMediaLoading").build());
        if (Utility.nativeMemorySize > 4194500) {
            loadingLimit = 75;
        } else {
            loadingLimit = 100;
        }
    }

    @Override
    protected MediaFile call() throws Exception {
        final long qty;
        List<MediaFile> content = new ArrayList<>();
        ArrayList<MediaFile> cacheList = new ArrayList<>();
        try {

            updateTitle("Reading cache...");
            //restore cache            
            File inPath = new File(Utility.getAppData() + File.separatorChar + "cache" + File.separatorChar + createMD5Hash(selectedPath.toString()) + "-" + selectedPath.toFile().getName() + ".bin");
            FileInputStream fileInputStream;
            try {
                fileInputStream = new FileInputStream(inPath);
                ObjectInputStream objectInputStream
                        = new ObjectInputStream(fileInputStream);
                List<MediaFile> e2 = (ArrayList<MediaFile>) objectInputStream.readObject();
                objectInputStream.close();
                cacheList.addAll(e2);
                Platform.runLater(() -> {
                    factory.setListFilesActive(false);
                    fullMediaList.addAll(cacheList);
                });
            } catch (IOException | ClassNotFoundException ex) {
            }
            updateTitle("Reading cache...finished");

            updateTitle("Counting mediafiles...");

            Stream<Path> fileList = Files.list(selectedPath).filter((t) -> {
                return FileTypes.isValidType(t.getFileName().toString());
            }).sorted(new FilenameComparator());

            Stream<Path> fileListCount = Files.list(selectedPath).filter((t) -> {
                return FileTypes.isValidType(t.getFileName().toString());
            });

            qty = fileListCount.count();
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
            Logger.getLogger(LighttableController.class.getName()).log(Level.INFO, "Starting collecting..." + selectedPath);
            long starttime = System.currentTimeMillis();

            AtomicInteger iatom = new AtomicInteger(1);
            if (qty != cacheList.size()) {
                if (!cacheList.isEmpty()) {
                    Platform.runLater(() -> {
                        mainController.getProgressPane().setVisible(false);
                        mainController.getStatusLabelLeft().setVisible(false);
                    });
                }
                fileList.parallel().forEach((fileItem) -> {
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
                                        Thread.ofVirtual().start(() -> {
                                            try {
                                                loadItem(fileItem, m);
                                                updateValue(m);
                                            } catch (IOException ex) {
                                                m.setMediaType(MediaFile.MediaTypes.NONE);
                                            }
                                        });
                                    } else {
                                        try {
                                            loadItem(fileItem, m);
                                            updateValue(m);
                                        } catch (IOException ex) {
                                            m.setMediaType(MediaFile.MediaTypes.NONE);
                                        }
                                    }
                                }
                                if (cacheList.contains(m) == false) {
                                    cacheList.add(m);
                                }
                            }
                        }
                        updateMessage(iatom.get() + " / " + qty);
                        iatom.addAndGet(1);
                        if (qty > 1000) {
                            double percentage = (double) iatom.get() / qty * 100;
                            if (percentage >= loadingLimit) {
                                factory.setListFilesActive(false);
                            }
                        }
                    }
                });
                if (this.isCancelled()) {
                    return null;
                }
            }
            //save cache to disk            
            File outpath = new File(Utility.getAppData() + File.separatorChar + "cache" + File.separatorChar + createMD5Hash(selectedPath.toString()) + "-" + selectedPath.toFile().getName() + ".bin");

            FileOutputStream fileOutputStream;
            try {
                fileOutputStream = new FileOutputStream(outpath, false);
                ObjectOutputStream objectOutputStream
                        = new ObjectOutputStream(fileOutputStream);
                updateTitle("Save cache..." + cacheList.size());
                objectOutputStream.writeObject(cacheList);
                objectOutputStream.flush();
                objectOutputStream.close();
                updateTitle("Save cache...finished.");
            } catch (FileNotFoundException ex) {
                Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, ex);
            }
            //end save to disk

            long endtime = System.currentTimeMillis();
            Logger.getLogger(LighttableController.class.getName()).log(Level.INFO, "Collect Time in s: " + (endtime - starttime) / 1000 + " " + selectedPath);
        } catch (IOException ex) {
            Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, ex);
        }
        switch (sort) {
            case "filename":
                Comparator<MediaFile> comparing2 = Comparator.comparing((MediaFile t) -> {
                    return t.getName();
                });
                content.sort(comparing2);
                break;
            case "File creation time":
                Comparator<MediaFile> comparing = Comparator.comparing((MediaFile t) -> {
                    try {
                        return t.getCreationTime();
                    } catch (IOException ex) {
                        Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    return null;
                });
                content.sort(comparing);
                break;

        }
        return null;
    }

    public void loadItem(Path fileItem, MediaFile m) throws IOException {
        if (this.isCancelled()) {
            return;
        }
        //TODO: load in background or load during real media loading to speed up
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
