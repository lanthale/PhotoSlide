/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;

import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Tooltip;
import org.controlsfx.control.GridView;
import org.photoslide.App;
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

    public SRMediaLoadingTask(ArrayList<String> queryList, SearchToolsController control, ObservableList<MediaFile> fullMediaList, GridView<MediaFile> imageGrid) {
        this.searchController = control;
        this.fullMediaList = fullMediaList;
        this.imageGrid = imageGrid;
        fileLoader = new MediaFileLoader();
        this.queryList = queryList;
    }

    @Override
    protected Void call() throws Exception {
        for (String query : queryList) {
            if (this.isCancelled()) {
                return null;
            }
            try ( Statement stm = App.getSearchDBConnection().createStatement();  ResultSet rs = stm.executeQuery(query)) {
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
        }
        return null;
    }

}
