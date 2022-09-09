/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.editormedia;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.controlsfx.control.GridView;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.Rating;
import org.photoslide.MainViewController;
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
    
    private MainViewController mainController;
    private MediaFile selectedMediaFile;
    private VBox box;
    private final KeyCombination keyCombinationMetaC = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);

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
    private GridView<MediaFile> mediaGrid;
    private EditMediaGridCellFactory factory;
    @FXML
    private Button showGridViewButton;
    private PopOver mediaGridView;

    @Override
    public void initialize(URL url, ResourceBundle rb) {        
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
        mediaGridView = new PopOver();
        mediaGridView.setArrowLocation(PopOver.ArrowLocation.BOTTOM_CENTER);
        mediaGridView.setAnimated(true);
        mediaGridView.setCloseButtonEnabled(true);
        mediaGridView.setFadeInDuration(Duration.millis(50));
        mediaGridView.setFadeOutDuration(Duration.millis(50));
        mediaGridView.setAutoHide(true);
        mediaGridView.setHideOnEscape(true);
    }

    public void injectMainController(MainViewController mainController) {
        this.mainController = mainController;
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
        Thread.ofVirtual().start(task);
    }

    public void shutdown() {
        factory.shutdown();        
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
        if (mediaGridView.isShowing()){
            mediaGridView.hide();
        }
        box = new VBox();
        box.setFillWidth(true);
        box.setPrefSize(700, 100);
        box.setMaxSize(700, 100);
        box.setAlignment(Pos.CENTER);
        mediaGrid = new GridView<>();
        factory = new EditMediaGridCellFactory(EditorMediaViewController.this);
        mediaGrid.setCellFactory(factory);        
        mediaGridView.setContentNode(box);
        if (!box.getChildren().contains(mediaGrid)) {
            box.getChildren().add(mediaGrid);
        }
        mediaGridView.show(showGridViewButton);
        if (mediaGrid.getItems().isEmpty()) {
            mediaGrid.setItems(lightTableController.getSortedMediaList());
            mediaGrid.setOnKeyPressed((t) -> {
                if (keyCombinationMetaC.match(t)) {
                    final Clipboard clipboard = Clipboard.getSystemClipboard();
                    final ClipboardContent content = new ClipboardContent();

                    List<File> fileList = new ArrayList<>();
                    Set<MediaFile> selection = factory.getSelectionModel().getSelection();
                    selection.forEach((k) -> {
                        fileList.add(new File(((MediaFile) k).getName()));
                    });
                    content.putFiles(fileList);
                    clipboard.setContent(content);
                }
                if (KeyCode.RIGHT == t.getCode()) {
                    selectNextImageInGrid();
                }
                if (KeyCode.LEFT == t.getCode()) {
                    selectPreviousImageInGrid();
                }
                if (KeyCode.X == t.getCode()) {
                    bookmarkSelection();
                }
                if (KeyCode.B == t.getCode()) {
                    bookmarkSelection();
                }
                if (KeyCode.BACK_SPACE == t.getCode() || KeyCode.DELETE == t.getCode()) {
                    deleteAction();
                }
                t.consume();
            });            
            factory.getSelectionModel().add(selectedMediaFile);            
        }
    }

    public ObservableList<MediaFile> getFullMediaList() {
        return lightTableController.getSortedMediaList();
    }

    public GridView<MediaFile> getImageGrid() {
        return mediaGrid;
    }

    public void selectMediaFileInGrid(MediaFile item) {
        EditMediaGridCell actCell = factory.getMediaCellForMediaFile(item);
        if (actCell != null) {
            actCell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0,
                    0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false,
                    false, false, false, false, false, true, null));
            actCell.requestLayout();
        }
    }

    public void selectPreviousImageInGrid() {
        int actIndex = lightTableController.getSortedMediaList().indexOf(factory.getSelectedMediaFile());
        actIndex = actIndex - 1;
        if (actIndex >= 0) {
            EditMediaGridCell nextCell = factory.getMediaCellForMediaFile(lightTableController.getSortedMediaList().get(actIndex));
            if (nextCell != null) {
                nextCell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0,
                        0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false,
                        false, false, false, false, false, true, null));
                nextCell.requestLayout();
            }
        }
    }

    public void selectNextImageInGrid() {
        int actIndex = lightTableController.getSortedMediaList().indexOf(factory.getSelectedMediaFile());
        actIndex = actIndex + 1;
        if (actIndex < lightTableController.getSortedMediaList().size()) {
            EditMediaGridCell nextCell = factory.getMediaCellForMediaFile(lightTableController.getSortedMediaList().get(actIndex));
            if (nextCell != null) {
                nextCell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0,
                        0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false,
                        false, false, false, false, false, true, null));
                nextCell.requestLayout();
            }
        }
    }

    public ProgressIndicator getImageProgress() {
        return imageProgress;
    }

    public StackPane getStackPane() {
        return stackPane;
    }

    public ImageView getEditorImageView() {
        return editorImageView;
    }

    private void bookmarkSelection() {
        Set<MediaFile> selection = factory.getSelectionModel().getSelection();
        for (MediaFile m : selection) {
            mainController.bookmarkMediaFile(m);
        }
        mainController.saveBookmarksFile();
    }

    public void deleteAction() {
        MediaFile item = lightTableController.getSortedMediaList().get(lightTableController.getSortedMediaList().indexOf(factory.getSelectedMediaFile()));
        item.setDeleted(true);
        editorImageView.setImage(null);
        Thread.ofVirtual().start(() -> {
            factory.getSelectedMediaFile().saveEdits();
        });        
    }

}
