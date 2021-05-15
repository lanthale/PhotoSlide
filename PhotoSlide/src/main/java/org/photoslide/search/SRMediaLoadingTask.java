/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Tooltip;
import org.controlsfx.control.GridView;
import org.photoslide.App;
import org.photoslide.MainViewController;
import org.photoslide.browserlighttable.MediaLoadingTask;
import org.photoslide.browsermetadata.MetadataController;
import org.photoslide.datamodel.MediaFileLoader;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFile;

/**
 *
 * @author selfemp
 */
public class SRMediaLoadingTask extends Task<Void> {

    private final SearchToolsController searchController;
    private final ObservableList<MediaFile> fullMediaList;
    private final GridView<MediaFile> imageGrid;
    private final MediaFileLoader fileLoader;
    private final ArrayList<String> queryList;
    private final MainViewController mainController;
    private final MetadataController metaController;

    public SRMediaLoadingTask(ArrayList<String> queryList, SearchToolsController control, ObservableList<MediaFile> fullMediaList, GridView<MediaFile> imageGrid, MetadataController mc, MainViewController mv) {
        this.searchController = control;
        this.fullMediaList = fullMediaList;
        this.imageGrid = imageGrid;
        fileLoader = new MediaFileLoader();
        this.queryList = queryList;
        mainController = mv;
        metaController = mc;
    }

    @Override
    protected Void call() throws Exception {
        for (String query : queryList) {
            if (this.isCancelled()) {
                return null;
            }
            try (Statement stm = App.getSearchDBConnection().createStatement(); ResultSet rs = stm.executeQuery(query)) {
                rs.next();
                String mediaURL = rs.getString("pathStorage");
                MediaFile mediaItem = new MediaFile();
                mediaItem.setName(Path.of(mediaURL).getFileName().toString());
                mediaItem.setPathStorage(Path.of(mediaURL));
                if (this.isCancelled() == true) {
                    return null;
                }
                mediaItem.readEdits();
                mediaItem.getCreationTime();
                if (mainController.isMediaFileBookmarked(mediaItem)) {
                    mediaItem.setBookmarked(true);
                }
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
                    try {
                        metaController.readBasicMetadata(this, mediaItem);
                    } catch (IOException ex) {
                        Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
                    }
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
        }
        return null;
    }

}
