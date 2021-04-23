/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.bookmarksboard;

import org.photoslide.search.*;
import java.io.File;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import org.controlsfx.control.GridView;
import org.photoslide.App;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.browserlighttable.MediaFileLoaderLT;

/**
 *
 * @author selfemp
 */
public class BMBMediaLoadingTask extends Task<Void> {

    private final BookmarkBoardController bmbController;
    private final ObservableList<MediaFile> fullMediaList;
    private final GridView<MediaFile> imageGrid;
    private final MediaFileLoaderBMB fileLoader;
    private final List<String> queryList;

    public BMBMediaLoadingTask(List<String> queryList, BookmarkBoardController control, ObservableList<MediaFile> fullMediaList, GridView<MediaFile> imageGrid) {
        this.bmbController = control;
        this.fullMediaList = fullMediaList;
        this.imageGrid = imageGrid;
        fileLoader = new MediaFileLoaderBMB(bmbController.getFactory());
        this.queryList = queryList;
    }

    @Override
    protected Void call() throws Exception {
        for (String query : queryList) {
            if (this.isCancelled()) {
                return null;
            }
            String mediaURL = query;
            MediaFile mediaItem = new MediaFile();
            mediaItem.setName(Path.of(mediaURL).getFileName().toString());
            mediaItem.setPathStorage(Path.of(mediaURL));
            if (this.isCancelled() == true) {
                return null;
            }
            mediaItem.readEdits();
            mediaItem.getCreationTime();
            if (this.isCancelled() == true) {
                return null;
            }
            Tooltip t = new Tooltip(mediaItem.getName());
            if (FileTypes.isValidVideo(mediaURL)) {
                if (this.isCancelled() == true) {
                    return null;
                }
                mediaItem.setMediaType(MediaFile.MediaTypes.VIDEO);
                Platform.runLater(() -> {
                    fullMediaList.add(mediaItem);
                });
                mediaItem.setMedia(fileLoader.loadVideo(mediaItem), mediaItem.getVideoSupported());
            } else if (FileTypes.isValidImge(mediaURL)) {
                mediaItem.setMediaType(MediaFile.MediaTypes.IMAGE);
                Platform.runLater(() -> {
                    fullMediaList.add(mediaItem);
                });
                if (this.isCancelled() == true) {
                    return null;
                }
                mediaItem.setImage(fileLoader.loadImage(mediaItem));
                if (this.isCancelled() == true) {
                    return null;
                }
            } else {
                mediaItem.setMediaType(MediaFile.MediaTypes.NONE);
            }

        }
        return null;
    }

}
