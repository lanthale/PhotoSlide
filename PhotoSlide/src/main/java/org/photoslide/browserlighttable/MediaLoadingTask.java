/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browserlighttable;

import java.util.ArrayList;
import java.util.List;
import org.photoslide.MainViewController;
import org.photoslide.datamodel.MediaFile;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import org.controlsfx.control.GridView;

/**
 *
 * @author selfemp
 */
public class MediaLoadingTask extends Task<Boolean> {

    private final ObservableList<MediaFile> list;
    private final MainViewController mainController;
    private final GridView<MediaFile> imageGrid;
    private final SortedList<MediaFile> sortedList;

    public MediaLoadingTask(ObservableList<MediaFile> listParam, SortedList<MediaFile> sortParam, MainViewController mainControllerParam, GridView<MediaFile> imageGridParam) {
        this.list = listParam;
        this.sortedList=sortParam;
        this.mainController = mainControllerParam;
        this.imageGrid = imageGridParam;
    }

    @Override
    protected Boolean call() throws Exception {
        while (list.isEmpty()) {
            Thread.sleep(100);            
        }

        Platform.runLater(() -> {
            mainController.getStatusLabelLeft().setVisible(false);
            mainController.getProgressPane().setVisible(false);
            mainController.getProgressbarLabel().setText("");
        });
        List<MediaFile> newList = new ArrayList<>(list);
        for (MediaFile fileItem : newList) {
            if (this.isCancelled() == false) {
                MediaFile item = null;
                switch (fileItem.getMediaType()) {
                    case VIDEO -> {
                        Media video = null;
                        try {
                            video = new Media(fileItem.getPathStorage().toUri().toURL().toExternalForm());
                            item = list.get(list.indexOf(fileItem));
                            item.setVideoSupported(MediaFile.VideoTypes.SUPPORTED);
                            item.setMedia(video, MediaFile.VideoTypes.SUPPORTED);
                        } catch (MediaException e) {
                            if (e.getType() == MediaException.Type.MEDIA_UNSUPPORTED) {
                                item = list.get(list.indexOf(fileItem));
                                item.setVideoSupported(MediaFile.VideoTypes.UNSUPPORTED);
                                item.setMedia(video, MediaFile.VideoTypes.UNSUPPORTED);
                            }
                        }
                        final MediaFile itemVideo = item;
                        Platform.runLater(() -> {
                            list.set(list.indexOf(fileItem), itemVideo);
                        });
                        updateMessage("Retrieve video... " + fileItem.getName());
                    }

                    case IMAGE -> {
                        Image img = null;
                        img = new Image(fileItem.getPathStorage().toUri().toURL().toString(), imageGrid.getCellWidth() + 300, imageGrid.getCellHeight() + 300, true, false, false);
                        updateMessage("Retrieve image... " + fileItem.getName());
                        try {
                            item = list.get(list.indexOf(fileItem));
                            item.setImage(img);
                            final MediaFile itemImage = item;
                            Platform.runLater(() -> {
                                list.set(list.indexOf(fileItem), itemImage);
                            });
                        } catch (IndexOutOfBoundsException e) {

                        }
                    }

                    default -> {
                    }
                }
            } else {
                return false;
            }
        }
        updateMessage("Retrieve images...finished");
        return true;
    }

}
