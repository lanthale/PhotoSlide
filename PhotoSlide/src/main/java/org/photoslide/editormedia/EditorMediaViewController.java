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
import javafx.collections.ObservableList;
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
import org.photoslide.imageops.ImageFilter;

/**
 *
 * @author selfemp
 */
public class EditorMediaViewController implements Initializable {

    private ExecutorService executor;
    private MediaFile selectedMediaFile;

    @FXML
    private Button zoomButton;
    @FXML
    private Rating editorRatingControl;
    @FXML
    private ImageView editorImageView;
    @FXML
    private StackPane stackPane;
    @FXML
    private HBox imageHbox;
    @FXML
    private ProgressIndicator imageProgress;
    @FXML
    private VBox infoPane;
    @FXML
    private Label titleLabel;
    @FXML
    private Label cameraLabel;
    @FXML
    private Label filenameLabel;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newCachedThreadPool(new ThreadFactoryPS("editorMediaViewController"));
        editorImageView.fitWidthProperty().bind(stackPane.widthProperty());
        editorImageView.fitHeightProperty().bind(stackPane.heightProperty());
    }

    public void setMediaFileForEdit(MediaFile f) {
        if (f == null) {
            return;
        }
        selectedMediaFile = f;
        editorImageView.setImage(null);
        Task<Boolean> task = new Task<>() {
            private Image imageWithFilters;
            private ObservableList<ImageFilter> filterList;
            private Image img;

            @Override
            protected Boolean call() throws Exception {
                switch (selectedMediaFile.getMediaType()) {
                    case VIDEO:
                        break;
                    case IMAGE:
                        Platform.runLater(() -> {
                            //editorImageView.fitWidthProperty().unbind();
                            //editorImageView.fitHeightProperty().unbind();
                            imageProgress.progressProperty().unbind();
                            editorImageView.setImage(null);
                            editorImageView.setVisible(true);
                            imageProgress.setVisible(true);
                        });
                        String url = selectedMediaFile.getImageUrl().toString();
                        img = new Image(url, true);
                        Platform.runLater(() -> {
                            imageProgress.progressProperty().bind(img.progressProperty());
                            img.progressProperty().addListener((ov, t, t1) -> {
                                if ((Double) t1 == 1.0 && !img.isError()) {
                                    imageProgress.setVisible(false);
                                    imageWithFilters = img;
                                    filterList = selectedMediaFile.getFilterListWithoutImageData();
                                    for (ImageFilter imageFilter : filterList) {
                                        imageWithFilters = imageFilter.load(imageWithFilters);
                                        imageFilter.filter(imageFilter.getValues());
                                    }
                                    img = imageWithFilters;
                                    editorImageView.setImage(img);
                                } else {
                                    imageProgress.setVisible(true);
                                }
                            });
                            editorImageView.setImage(img);
                        });
                        break;
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

    public void resetImageView() {
        this.editorImageView.setImage(null);
    }
}
