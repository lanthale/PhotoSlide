/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.editormetadata;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Accordion;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.datamodel.MediaFile;

/**
 *
 * @author selfemp
 */
public class EditorMetadataController implements Initializable{

    private ExecutorService executor;
    @FXML
    private Accordion editorAccordion;
    private MediaFile selectedMediaFile;
    @FXML
    private ImageView imageVIew;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private StackPane stackPane;
    
    

    @Override
    public void initialize(URL url, ResourceBundle rb) {        
        executor = Executors.newCachedThreadPool(new ThreadFactoryPS("editorMetadataController"));
        imageVIew.fitWidthProperty().bind(stackPane.widthProperty());
        imageVIew.fitHeightProperty().bind(stackPane.heightProperty());        
    }
    
    public void setMediaFileForEdit(MediaFile f) {
        if (f == null) {
            return;
        }
        selectedMediaFile = f;
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                switch (selectedMediaFile.getMediaType()) {
                    case VIDEO -> {

                    }
                    case IMAGE -> {
                        Platform.runLater(() -> {
                            progressIndicator.progressProperty().unbind();
                            imageVIew.setImage(null);                            
                            progressIndicator.setVisible(true);
                        });
                        String url = selectedMediaFile.getImage().getUrl();
                        Image img = new Image(url, true);
                        Platform.runLater(() -> {
                            progressIndicator.progressProperty().bind(img.progressProperty());
                            img.progressProperty().addListener((ov, t, t1) -> {
                                if ((Double) t1 == 1.0 && !img.isError()) {
                                    progressIndicator.setVisible(false);
                                } else {
                                    progressIndicator.setVisible(true);
                                }
                            });
                            imageVIew.setImage(img);
                        });
                    }
                }
                return true;
            }            
        };
        executor.submit(task);
    }
    
    public void shutdown() {
        executor.shutdownNow();
    }
    
}
