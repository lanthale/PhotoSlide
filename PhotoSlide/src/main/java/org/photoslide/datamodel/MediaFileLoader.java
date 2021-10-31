/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.browserlighttable.MediaLoadingTask;

/**
 *
 * @author selfemp
 */
public class MediaFileLoader {
    
    private ExecutorService executor;
    private List<Task> taskList;
    
    public MediaFileLoader() {
        executor = Executors.newFixedThreadPool(20, new ThreadFactoryPS("mediaFileLoaderThread"));
        taskList = new ArrayList<>();
    }
    
    public void loadImage(MediaFile fileItem) {
        Image retImage = null;
        try {
            Image iImage = new Image(fileItem.getPathStorage().toUri().toURL().toString(), 200, 200, true, false, true);
            iImage.progressProperty().addListener((ov, t, t1) -> {
                if (t1.doubleValue() == 1.0) {
                    fileItem.setLoading(false);
                }
                if (iImage.isError()) {
                    fileItem.setMediaType(MediaFile.MediaTypes.NONE);
                }
            });
            retImage = iImage;
        } catch (MalformedURLException ex) {
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
        }
        fileItem.setImage(retImage);
    }
    
    public Media loadVideo(MediaFile fileItem) {
        Media video = null;
        try {
            video = new Media(fileItem.getPathStorage().toUri().toURL().toExternalForm());
            fileItem.setLoading(false);
            fileItem.setVideoSupported(MediaFile.VideoTypes.SUPPORTED);
            fileItem.setMedia(video, MediaFile.VideoTypes.SUPPORTED);
        } catch (MediaException e) {
            if (e.getType() == MediaException.Type.MEDIA_UNSUPPORTED) {
                fileItem.setVideoSupported(MediaFile.VideoTypes.UNSUPPORTED);
                fileItem.setMedia(video, MediaFile.VideoTypes.UNSUPPORTED);
            }
        } catch (MalformedURLException ex) {
            Logger.getLogger(MediaLoadingTask.class.getName()).log(Level.SEVERE, null, ex);
        }
        return video;
    }
    
    public void shutdown() {
        executor.shutdownNow();
    }
}
