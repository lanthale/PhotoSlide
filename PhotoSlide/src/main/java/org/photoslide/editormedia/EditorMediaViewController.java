/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.editormedia;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.Rating;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.datamodel.MediaFile;

/**
 *
 * @author selfemp
 */
public class EditorMediaViewController implements Initializable {

    @FXML
    private Button zoomButton;

    private MediaFile selectedMediaFile;
    @FXML
    private StackPane editorStackPane;
    @FXML
    private HBox editorImageStackPane;
    @FXML
    private ProgressIndicator editorImageProgress;
    @FXML
    private VBox editorInfoPane;
    @FXML
    private Label editorTitleLabel;
    @FXML
    private Label editorCameraLabel;
    @FXML
    private Label editorFilenameLabel;
    @FXML
    private Rating editorRatingControl;
    @FXML
    private ImageView editorImageView;
    private ExecutorService executor;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("editorMediaViewController"));
        editorImageView.fitWidthProperty().bind(editorStackPane.widthProperty());
        editorImageView.fitHeightProperty().bind(editorStackPane.heightProperty());        
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
                            editorImageProgress.progressProperty().unbind();
                            editorImageView.setImage(null);
                            editorImageView.setVisible(true);
                            editorImageProgress.setVisible(true);
                        });
                        String url = selectedMediaFile.getImage().getUrl();
                        Image img = new Image(url, true);
                        Platform.runLater(() -> {
                            editorImageProgress.progressProperty().bind(img.progressProperty());
                            img.progressProperty().addListener((ov, t, t1) -> {
                                if ((Double) t1 == 1.0 && !img.isError()) {
                                    editorImageProgress.setVisible(false);
                                } else {
                                    editorImageProgress.setVisible(true);
                                }
                            });
                            editorImageView.setImage(img);
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

    @FXML
    private void zoomButtonAction(ActionEvent event) {
        Rectangle2D newViewportRect3 = new Rectangle2D(
                0,
                0,
                100,
                100);
        editorImageView.setViewport(newViewportRect3);
    }
}
