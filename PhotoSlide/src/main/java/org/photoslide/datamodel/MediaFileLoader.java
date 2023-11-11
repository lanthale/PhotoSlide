/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import java.io.InterruptedIOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.Utility;
import org.photoslide.browserlighttable.MediaLoadingTask;

/**
 *
 * @author selfemp
 */
public class MediaFileLoader {

    private final ExecutorService executorParallel;
    private final HashMap<String, Task> taskList;

    public MediaFileLoader() {
        if (Utility.nativeMemorySize > 4194500) {
            //executorParallel = Executors.newFixedThreadPool(20, new ThreadFactoryPS("mediaFileLoaderThread"));
            executorParallel = Executors.newThreadPerTaskExecutor(new ThreadFactoryBuilder().setNamePrefix("mediaFileLoaderThread").setPriority(10).build());
        } else {
            executorParallel = Executors.newFixedThreadPool(3, new ThreadFactoryBuilder().setNamePrefix("mediaFileLoaderThread").setPriority(10).build());
        }
        taskList = new HashMap<>();
    }

    public void loadImage(MediaFile newMediaItem) {
        Task<Image> task = new Task<Image>() {
            @Override
            public Image call() throws Exception {
                Image image = new Image(newMediaItem.getPathStorage().toUri().toURL().toString(), 200, 200, true, false, true);
                image.progressProperty().addListener((ov, g, g1) -> {
                    if (this.isCancelled()) {
                        image.cancel();
                    }
                    if ((Double) g1 == 1.0 && !image.isError()) {
                        newMediaItem.setLoading(false);
                        taskList.remove(newMediaItem.getName());
                    }
                });
                if (this.isCancelled()) {
                    image.cancel();
                }
                if (image.isError()) {
                    if (image.getException() instanceof InterruptedIOException == false) {
                        throw new Exception("Cannot load image '" + newMediaItem.getPathStorage().toUri().toURL().toString() + "'!");
                    }
                }
                newMediaItem.setImage(image);
                return image;
            }
        };
        if (newMediaItem.getMediaType() == MediaFile.MediaTypes.IMAGE) {
            if (newMediaItem.getImage() == null) {
                newMediaItem.setImage(task.getValue());
                task.setOnFailed((t) -> {
                    newMediaItem.setLoading(false);
                    newMediaItem.setMediaType(MediaFile.MediaTypes.NONE);
                    taskList.remove(newMediaItem.getName());
                });
                if (taskList.get(newMediaItem.getName()) == null) {
                    taskList.put(newMediaItem.getName(), task);
                    executorParallel.submit(task);
                }
            }
        }
    }

    public void loadVideo(MediaFile fileItem) {
        Task<Media> task = new Task<Media>() {
            @Override
            public Media call() throws Exception {
                Media video = null;
                try {
                    video = new Media(fileItem.getPathStorage().toUri().toURL().toExternalForm());
                    fileItem.setVideoSupported(MediaFile.VideoTypes.SUPPORTED);
                    //fileItem.setMedia(video, MediaFile.VideoTypes.SUPPORTED);                    
                } catch (MediaException e) {
                    if (e.getType() == MediaException.Type.MEDIA_UNSUPPORTED) {
                        fileItem.setVideoSupported(MediaFile.VideoTypes.UNSUPPORTED);
                        //fileItem.setMedia(video, MediaFile.VideoTypes.UNSUPPORTED);
                    }
                } catch (MalformedURLException ex) {
                    Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
                    throw new Exception("Cannot load video '" + fileItem.getPathStorage().toUri().toURL().toString() + "'!");
                }
                return video;
            }
        };
        if (fileItem.getMediaType() == MediaFile.MediaTypes.VIDEO) {
            if (fileItem.getMedia() == null) {
                fileItem.setMedia(task.getValue(), fileItem.getVideoSupported());
                task.setOnSucceeded((t) -> {
                    fileItem.setLoading(false);
                    taskList.remove(fileItem.getName());
                });
                task.setOnFailed((t) -> {
                    fileItem.setLoading(false);
                    fileItem.setMediaType(MediaFile.MediaTypes.NONE);
                    taskList.remove(fileItem.getName());
                });
                if (taskList.get(fileItem.getName()) == null) {
                    taskList.put(fileItem.getName(), task);
                    executorParallel.submit(task);
                }
            }
        }
    }

    public void shutdown() {
        taskList.values().parallelStream().forEach(t -> {
            t.cancel();
        });
        executorParallel.shutdownNow();
        taskList.clear();
    }

    public void cancleTasks() {
        Thread.ofVirtual().start(() -> {
            taskList.values().parallelStream().forEach(t -> {
                t.cancel();
            });
            Stream<Task> filter = taskList.values().parallelStream().filter((k) -> k.isCancelled());
            taskList.values().removeAll(filter.toList());
        });
        //executorParallel.shutdownNow();
    }
}
