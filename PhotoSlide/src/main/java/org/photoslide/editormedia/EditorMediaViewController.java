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
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.controlsfx.control.GridView;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.Rating;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.browserlighttable.LighttableController;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.editormetadata.EditorMetadataController;
import org.photoslide.editortools.EditorToolsController;
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
    private LighttableController lightTableController;
    private EditorMetadataController editMetadataController;
    private EditorToolsController editorToolsController;
    private Task<Boolean> task;
    @FXML
    private Button showGridViewButton;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("editorMediaViewController"));
        editorImageView.fitWidthProperty().bind(stackPane.widthProperty());
        editorImageView.fitHeightProperty().bind(stackPane.heightProperty());
        stackPane.setOnKeyPressed((t) -> {
            if (t.getCode().equals(KeyCode.RIGHT)) {
                nextMediaItem();
            }
            if (t.getCode().equals(KeyCode.LEFT)) {
                previousMediaItem();
            }
        });
        stackPane.setOnSwipeRight((t) -> {
            nextMediaItem();
        });
        stackPane.setOnSwipeLeft((t) -> {
            previousMediaItem();
        });
    }

    public void injectLightController(LighttableController c) {
        this.lightTableController = c;
    }

    public void injectEditorMetaDataController(EditorMetadataController c) {
        this.editMetadataController = c;
    }

    public void injectEditorToolsController(EditorToolsController c) {
        this.editorToolsController = c;
    }

    public void setMediaFileForEdit(MediaFile f) {
        if (f == null) {
            return;
        }
        stackPane.requestFocus();
        selectedMediaFile = f;
        editorImageView.setImage(null);
        task = new Task<>() {
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
                                    stackPane.requestFocus();
                                } else {
                                    imageProgress.setVisible(true);
                                    stackPane.requestFocus();
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

    public void resetUI() {
        imageProgress.progressProperty().unbind();
        imageProgress.setProgress(0);
        editorImageView.setImage(null);
    }

    private void cancleTask() {
        if (task != null) {
            task.cancel();
            editorImageView.getImage().cancel();
            resetUI();
            editMetadataController.cancleTask();
            editorToolsController.cancleTask();
        }
    }

    @FXML
    private void selectPreviousButtonAction(ActionEvent event) {
        previousMediaItem();
    }

    private void previousMediaItem() {
        cancleTask();
        lightTableController.selectPreviousImageInGrid();
        MediaFile selectedMediaItem = lightTableController.getFactory().getSelectedMediaItem();
        editMetadataController.setMediaFileForEdit(selectedMediaItem);
        editorToolsController.setMediaFileForEdit(selectedMediaItem);
        setMediaFileForEdit(selectedMediaItem);
    }

    @FXML
    private void selectNextButtonAction(ActionEvent event) {
        nextMediaItem();
    }

    private void nextMediaItem() {
        cancleTask();
        lightTableController.selectNextImageInGrid();
        MediaFile selectedMediaItem = lightTableController.getFactory().getSelectedMediaItem();
        editMetadataController.setMediaFileForEdit(selectedMediaItem);
        editorToolsController.setMediaFileForEdit(selectedMediaItem);
        setMediaFileForEdit(selectedMediaItem);
    }

    @FXML
    private void showGridViewAction(ActionEvent event) {
        PopOver po = new PopOver();
        po.setArrowLocation(PopOver.ArrowLocation.BOTTOM_CENTER);
        po.setAnimated(true);
        po.setCloseButtonEnabled(true);
        po.setFadeInDuration(Duration.millis(50));
        po.setFadeOutDuration(Duration.millis(50));
        po.setAutoHide(true);
        VBox box = new VBox();
        box.setPrefSize(700, 100);
        box.setMaxSize(700, 100);
        box.setAlignment(Pos.CENTER);        
        GridView<MediaFile> imageGrid = new GridView<>(lightTableController.getSortedMediaList());
        imageGrid.setCellFactory(lightTableController.getFactory());
        //subscribe to selectedMediaItem in Factory        
        box.getChildren().add(lightTableController.getImageGrid());
        po.setContentNode(box);
        po.show(showGridViewButton);
    }
}
