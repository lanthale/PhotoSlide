/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.bookmarksboard;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.Tooltip;
import org.controlsfx.control.GridView;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.datamodel.MediaFileLoader;

/**
 *
 * @author selfemp
 */
public class BMBMediaLoadingTask extends Task<Void> {

    private final BookmarkBoardController bmbController;
    private final ObservableList<MediaFile> fullMediaList;
    private final GridView<MediaFile> imageGrid;
    private final MediaFileLoader fileLoader;
    private final List<String> mediaList;
    private final ExecutorService executor;

    public BMBMediaLoadingTask(List<String> queryList, BookmarkBoardController control, ObservableList<MediaFile> fullMediaList, GridView<MediaFile> imageGrid) {
        this.bmbController = control;
        executor = Executors.newFixedThreadPool(20, new ThreadFactoryPS("BMBMediaLoadingTask"));
        this.fullMediaList = fullMediaList;
        this.imageGrid = imageGrid;
        fileLoader = new MediaFileLoader();
        this.mediaList = queryList;
    }

    @Override
    protected Void call() throws Exception {
        for (String mFile : mediaList) {
            if (this.isCancelled()) {
                return null;
            }
            String mediaURL = mFile;
            MediaFile mediaItem = new MediaFile();
            mediaItem.setName(Path.of(mediaURL).getFileName().toString());
            mediaItem.setPathStorage(Path.of(mediaURL));
            mediaItem.setMediaType(MediaFile.MediaTypes.IMAGE);
            if (this.isCancelled() == true) {
                return null;
            }
            Task<Void> loadTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    loadItem(this, mediaItem, mediaURL);
                    return null;
                }
            };
            executor.submit(loadTask);
            Platform.runLater(() -> {
                fullMediaList.add(mediaItem);
            });
        }
        return null;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void loadItem(Task task, MediaFile mediaItem, String mediaURL) {
        mediaItem.readEdits();
        if (this.isCancelled() == true) {
            task.cancel();
            return;
        }
        mediaItem.getCreationTime();
        if (this.isCancelled() == true) {
            task.cancel();
            return;
        }
        Tooltip t = new Tooltip(mediaItem.getName());
        if (FileTypes.isValidVideo(mediaURL)) {
            if (this.isCancelled() == true) {
                task.cancel();
                return;
            }
            mediaItem.setMediaType(MediaFile.MediaTypes.VIDEO);
            mediaItem.setMedia(fileLoader.loadVideo(mediaItem), mediaItem.getVideoSupported());
        } else if (FileTypes.isValidImage(mediaURL)) {
            mediaItem.setMediaType(MediaFile.MediaTypes.IMAGE);
            if (this.isCancelled() == true) {
                task.cancel();
                return;
            }
            fileLoader.loadImage(mediaItem);
            if (this.isCancelled() == true) {
                task.cancel();
                return;
            }
        } else {
            mediaItem.setMediaType(MediaFile.MediaTypes.NONE);
        }
    }

}
