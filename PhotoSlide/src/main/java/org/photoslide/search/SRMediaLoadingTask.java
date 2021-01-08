/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;

import java.io.File;
import java.nio.file.Path;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import org.controlsfx.control.GridView;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFile;

/**
 *
 * @author selfemp
 */
public class SRMediaLoadingTask extends Task<Void> {

    private final String mediaURL;
    private final SearchToolsController searchController;
    private final ObservableList<MediaFile> fullMediaList;
    private final GridView<MediaFile> imageGrid;

    public SRMediaLoadingTask(String mediaURL, SearchToolsController control, ObservableList<MediaFile> fullMediaList, GridView<MediaFile> imageGrid) {
        this.mediaURL = mediaURL;
        this.searchController = control;
        this.fullMediaList = fullMediaList;
        this.imageGrid = imageGrid;
    }

    @Override
    protected Void call() throws Exception {
        MediaFile mediaItem = new MediaFile();
        mediaItem.setName(mediaURL);
        mediaItem.setPathStorage(Path.of(mediaURL));
        mediaItem.readEdits();
        mediaItem.getCreationTime();
        MediaFile item = null;
        Tooltip t = new Tooltip(mediaItem.getName());        
        if (FileTypes.isValidVideo(mediaURL)) {
            mediaItem.setMediaType(MediaFile.MediaTypes.VIDEO);
            Platform.runLater(() -> {
                fullMediaList.add(mediaItem);
            });
            Media video = null;
            try {
                video = new Media(mediaItem.getPathStorage().toUri().toURL().toExternalForm());                
                item = fullMediaList.get(fullMediaList.indexOf(mediaItem));
                item.setVideoSupported(MediaFile.VideoTypes.SUPPORTED);
                item.setMedia(video, MediaFile.VideoTypes.SUPPORTED);
            } catch (MediaException e) {
                if (e.getType() == MediaException.Type.MEDIA_UNSUPPORTED) {
                    item = fullMediaList.get(fullMediaList.indexOf(mediaItem));
                    item.setVideoSupported(MediaFile.VideoTypes.UNSUPPORTED);
                    item.setMedia(video, MediaFile.VideoTypes.UNSUPPORTED);
                }
            }
            final MediaFile itemVideo = item;
            Platform.runLater(() -> {
                fullMediaList.set(fullMediaList.indexOf(mediaItem), itemVideo);
            });
        } else if (FileTypes.isValidImge(mediaURL)) {
            mediaItem.setMediaType(MediaFile.MediaTypes.IMAGE);
            Platform.runLater(() -> {
                fullMediaList.add(mediaItem);
            });

            Image img = new Image(mediaItem.getPathStorage().toUri().toURL().toString(), imageGrid.getCellWidth() + 100, imageGrid.getCellHeight() + 100, true, false, false);           
            try {
                item = fullMediaList.get(fullMediaList.indexOf(mediaItem));
                item.setImage(img);
                final MediaFile itemImage = item;
                Platform.runLater(() -> {
                    fullMediaList.set(fullMediaList.indexOf(mediaItem), itemImage);                    
                });
            } catch (IndexOutOfBoundsException e) {

            }
        } else {
            mediaItem.setMediaType(MediaFile.MediaTypes.NONE);
        }
        return null;
    }

}
