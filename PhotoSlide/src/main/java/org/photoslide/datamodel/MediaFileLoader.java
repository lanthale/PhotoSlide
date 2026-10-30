/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import org.photoslide.editpipeline.EditPipeline;

import org.photoslide.editpipeline.LoadPriority;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private final Map<String, Task> taskList;

    public MediaFileLoader() {
        // few threads: each thumbnail reads the whole file, on a network share all
        // reads share the bandwidth with the preview the user waits for
        int threads = Utility.nativeMemorySize > 4194500 ? 4 : 3;
        // newest request first: after scrolling, the cells now visible load before
        // the ones scrolled past (those are cancelled when their cell is reused)
        BlockingQueue<Runnable> newestFirst = new LinkedBlockingDeque<>() {
            @Override
            public boolean offer(Runnable r) {
                return super.offerFirst(r);
            }
        };
        executorParallel = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, newestFirst,
                new ThreadFactoryBuilder().setNamePrefix("mediaFileLoaderThread").setDaemon(true).build());
        taskList = new ConcurrentHashMap<>();
    }

    /** Longest side of the thumbnails in the grids. */
    private static final int THUMBNAIL_SIZE = 200;

    public void loadImage(MediaFile newMediaItem) {
        Task<Image> task = new Task<Image>() {
            @Override
            public Image call() throws Exception {
                LoadPriority.awaitBackground();
                if (this.isCancelled()) {
                    taskList.remove(newMediaItem.getName());
                    return null;
                }
                // RAW: the same decoder, profile and base look as the editor
                Image raw = EditPipeline.loadRawThumbnail(newMediaItem, THUMBNAIL_SIZE);
                if (raw != null) {
                    taskList.remove(newMediaItem.getName());
                    // the grid cells observe the loading flag: change it on the JavaFX thread
                    javafx.application.Platform.runLater(() -> {
                        newMediaItem.setImage(raw);
                        newMediaItem.setLoading(false);
                    });
                    return raw;
                }
                // loaded in this thread, not in the background: JavaFX would load the
                // thumbnails of all visible cells at once and this pool would not limit them
                Image image = new Image(newMediaItem.getPathStorage().toUri().toURL().toString(), 200, 200, true, false, false);
                taskList.remove(newMediaItem.getName());
                final boolean error = image.isError();
                javafx.application.Platform.runLater(() -> {
                    newMediaItem.setImage(image);
                    newMediaItem.setLoadingError(error);
                    newMediaItem.setLoading(false);
                });
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
                } catch (MediaException e) {
                    if (e.getType() == MediaException.Type.MEDIA_UNSUPPORTED) {
                        fileItem.setVideoSupported(MediaFile.VideoTypes.UNSUPPORTED);
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

    /**
     * Drops the thumbnail request of a media file a cell does not show any more,
     * if it has not started yet. A started one finishes. JavaFX thread.
     */
    public void cancel(MediaFile m) {
        Task t = taskList.remove(m.getName());
        if (t != null) {
            t.cancel(false);
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
