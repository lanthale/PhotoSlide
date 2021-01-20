/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browserlighttable;

import org.photoslide.MainViewController;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.browsermetadata.MetadataController;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import org.photoslide.datamodel.MediaFileLoader;

/**
 *
 * @author selfemp
 */
public class MediaLoadingTask extends Task<Void> {

    private final Path selectedPath;
    private final Label mediaQTYLabel;
    private final MainViewController mainController;
    private final String sort;
    private final MetadataController metadataController;
    private final MediaGridCellFactory factory;
    private final ObservableList<MediaFile> fullMediaList;
    private final MediaFileLoader fileLoader;

    public MediaLoadingTask(ObservableList<MediaFile> fullMediaList, MediaGridCellFactory factory, Path sPath, MainViewController mainControllerParam, Label mediaQTYLabelParam, String sortParm, MetadataController metaControllerParam) {
        selectedPath = sPath;
        fileLoader = new MediaFileLoader();
        mediaQTYLabel = mediaQTYLabelParam;
        mainController = mainControllerParam;
        sort = sortParm;
        metadataController = metaControllerParam;
        this.factory = factory;
        this.fullMediaList = fullMediaList;
    }

    @Override
    protected Void call() throws Exception {
        final long qty;
        List<MediaFile> content = new ArrayList<>();
        try {
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
                    mediaQTYLabel.setText(qty + " media files.");
                });
            }

            Stream<Path> fileList = Files.list(selectedPath).filter((t) -> {
                return FileTypes.isValidType(t.getFileName().toString());
            }).sorted();
            AtomicInteger iatom = new AtomicInteger(1);
            fileList.forEachOrdered((fileItem) -> {
                if (this.isCancelled() == false) {
                    if (Files.isDirectory(fileItem) == false) {
                        if (FileTypes.isValidType(fileItem.toString())) {
                            MediaFile m = new MediaFile();
                            m.setName(fileItem.getFileName().toString());
                            m.setPathStorage(fileItem);
                            m.readEdits();
                            m.getCreationTime();
                            if (FileTypes.isValidVideo(fileItem.toString())) {
                                m.setMediaType(MediaFile.MediaTypes.VIDEO);
                                Platform.runLater(() -> {
                                    fullMediaList.add(m);
                                });                                
                                m.setMedia(fileLoader.loadVideo(m), m.getVideoSupported());                                
                            } else if (FileTypes.isValidImge(fileItem.toString())) {
                                m.setMediaType(MediaFile.MediaTypes.IMAGE);
                                if (sort.equalsIgnoreCase("Capture time")) {
                                    metadataController.setActualMediaFile(m);
                                    try {
                                        metadataController.readBasicMetadata(this);
                                    } catch (IOException ex) {
                                        Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                }  
                                Platform.runLater(() -> {
                                    fullMediaList.add(m);
                                });
                                m.setImage(fileLoader.loadImage(m));                                
                            } else {
                                m.setMediaType(MediaFile.MediaTypes.NONE);
                            }
                        }
                    }
                    updateProgress(iatom.get(), qty);
                    updateMessage(iatom.get() + " / " + qty);
                    iatom.addAndGet(1);
                }
            });
        } catch (IOException ex) {
            Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, ex);
        }
        switch (sort) {
            case "filename":
                break;
            case "File creation time":
                content.sort(Comparator.comparing(MediaFile::getCreationTime));
                break;
        }
        return null;
    }

    

}
