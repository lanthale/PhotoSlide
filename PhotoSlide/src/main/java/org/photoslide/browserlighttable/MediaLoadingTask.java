/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browserlighttable;

import org.photoslide.datamodel.MediaFileLoader;
import org.photoslide.MainViewController;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.browsermetadata.MetadataController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    
    public MediaLoadingTask(ObservableList<MediaFile> fullMediaList, MediaGridCellFactory factory, Path sPath, MainViewController mainControllerParam, Label mediaQTYLabelParam, String sortParm, MetadataController metaControllerParam) {
        selectedPath = sPath;
        fileLoader = new MediaFileLoader();
        mediaQTYLabel = mediaQTYLabelParam;
        mainController = mainControllerParam;
        sort = sortParm;
        metadataController = metaControllerParam;
        this.factory = factory;
        this.fullMediaList = fullMediaList;
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNamePrefix("lightTableControllerSelectionMediaLoading").build());
    }
    
    @Override
    protected MediaFile call() throws Exception {
        final long qty;
        List<MediaFile> content = new ArrayList<>();
        try {
            updateTitle("Reading mediafiles...");
            qty = Files.list(selectedPath).filter((t) -> {
                return FileTypes.isValidType(t.getFileName().toString());
            }).count();
            if (qty == 0) {
                Platform.runLater(() -> {
                    mediaQTYLabel.setText(qty + " media files.");
                    mainController.getProgressPane().setVisible(false);
                    mainController.getStatusLabelLeft().setVisible(false);
                });
                return null;
            } else {
                Platform.runLater(() -> {
                    mainController.getStatusLabelLeft().setVisible(true);
                    mainController.getProgressPane().setVisible(true);
                    mainController.getProgressbarLabel().setText(qty + " files found - Loading...");
                    mediaQTYLabel.setText(qty + " media files");
                    mainController.getStatusLabelRight().setVisible(true);
                });
            }
            Logger.getLogger(LighttableController.class.getName()).log(Level.INFO, "Starting collecting..." + selectedPath);
            long starttime = System.currentTimeMillis();
                        
            Stream<Path> fileList = Files.list(selectedPath).filter((t) -> {
                return FileTypes.isValidType(t.getFileName().toString());
            }).sorted(new FilenameComparator());
            AtomicInteger iatom = new AtomicInteger(1);            
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
                            Thread.ofVirtual().start(() -> {
                                try {
                                    loadItem(fileItem, m);
                                    updateValue(m);
                                } catch (IOException ex) {
                                    m.setMediaType(MediaFile.MediaTypes.NONE);
                                }
                            });
                        }
                    }
                    updateMessage(iatom.get() + " / " + qty);
                    iatom.addAndGet(1);
                    double percentage = (double)iatom.get() / qty * 100;                    
                    if (percentage > 75) {
                        factory.setListFilesActive(false);
                    }
                }
            });
            if (this.isCancelled()) {
                return null;
            }
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
    
}
