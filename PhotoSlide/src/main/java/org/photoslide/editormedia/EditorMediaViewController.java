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
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
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
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.browserlighttable.LighttableController;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.editormetadata.EditorMetadataController;
import org.photoslide.editortools.EditorToolsController;
import org.photoslide.editpipeline.ImagePreview16;
import org.photoslide.datamodel.Image16;
import org.photoslide.editortools.ToolStackView;
import java.util.function.Consumer;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.mask.ColorSelection;
import org.photoslide.imageops.mask.Contour;
import org.photoslide.imageops.mask.Region;
import org.photoslide.editpipeline.EditPipeline;

/**
 *
 * @author selfemp
 */
public class EditorMediaViewController implements Initializable {

    private ExecutorService executor;
    private MainViewController mainController;
    private MediaFile selectedMediaFile;
    private VBox box;
    private final KeyCombination keyCombinationMetaC = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);

    @FXML
    private ToolBar editorToolBar;
    private SelectionTools selectionTools;
    private ViewTools viewTools;
    private CropRotateOverlay cropOverlay;
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
    private ImagePreview16 preview;
    private RegionOverlay regionOverlay;
    private GridView<MediaFile> mediaGrid;
    private EditMediaGridCellFactory factory;
    @FXML
    private Button showGridViewButton;
    private PopOver mediaGridView;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNamePrefix("editorMediaViewController").build());
        editorImageView.fitWidthProperty().bind(stackPane.widthProperty());
        editorImageView.fitHeightProperty().bind(stackPane.heightProperty());
        preview = new ImagePreview16(editorImageView, "editorPreview");
        regionOverlay = new RegionOverlay(editorImageView, preview);
        stackPane.getChildren().add(regionOverlay);
        cropOverlay = new CropRotateOverlay(editorImageView, preview);
        stackPane.getChildren().add(cropOverlay);
        // colour of the edited image (all tools applied) for the colour selection
        selectionTools = new SelectionTools(editorToolBar, regionOverlay, p -> {
            Image16 source = preview.getSource();
            if (source == null || selectedMediaFile == null) {
                return null;
            }
            int x = (int) Math.max(0, Math.min(source.width() - 1, p.x()));
            int y = (int) Math.max(0, Math.min(source.height() - 1, p.y()));
            return EditPipeline.sampleBelow(selectedMediaFile, source, Integer.MAX_VALUE, x, y);
        });
        viewTools = new ViewTools(editorToolBar, editorImageView, stackPane, preview, cropOverlay, regionOverlay);
        // the sampler of the tools panel follows the mouse
        stackPane.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (editorToolsController != null) {
                editorToolsController.sampleAt(regionOverlay.originalAt(e.getSceneX(), e.getSceneY()));
            }
        });
        stackPane.addEventFilter(MouseEvent.MOUSE_EXITED, e -> {
            if (editorToolsController != null) {
                editorToolsController.sampleAt(null);
            }
        });
        preview.setOnProgress((p) -> imageProgress.setProgress(p));
        preview.setOnShown(() -> {
            imageProgress.setVisible(false);
            viewTools.applyView();          // crop and zoom need the size of the original
        });
        preview.setOnFailed((ex) -> {
            imageProgress.setVisible(false);
            editorImageView.setImage(null);
        });
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
        c.setPreview(preview);
        c.setDropperHost(new ToolStackView.DropperHost() {
            @Override
            public void requestPoint(Consumer<Contour.Point> onPoint) {
                regionOverlay.requestPoint(onPoint);
            }

            @Override
            public void requestOutline(Consumer<List<Contour.Point>> onOutline) {
                regionOverlay.requestOutline(onOutline);
            }

            @Override
            public void requestCircle(double defaultRadius, java.util.function.BiConsumer<Contour.Point, Double> onCircle) {
                regionOverlay.requestCircle(defaultRadius, onCircle);
            }

            @Override
            public void cancelPick() {
                regionOverlay.cancel();
            }

            @Override
            public double[] sampleBelow(AbstractImageFilter tool, Contour.Point p) {
                Image16 source = preview.getSource();
                if (source == null || selectedMediaFile == null) {
                    return null;
                }
                int x = (int) Math.max(0, Math.min(source.width() - 1, p.x()));
                int y = (int) Math.max(0, Math.min(source.height() - 1, p.y()));
                return EditPipeline.sampleBelow(selectedMediaFile, source, tool.getPosition(), x, y);
            }

            @Override
            public double[] imageSize() {
                Image16 source = preview.getSource();
                return source == null ? null : new double[]{source.width(), source.height()};
            }

            @Override
            public void showRegion(AbstractImageFilter tool) {
                regionOverlay.show(tool);
            }

            @Override
            public Region selectedRegion() {
                return selectionTools.region();
            }

            @Override
            public ColorSelection selectedColors() {
                return selectionTools.colors();
            }
        });
    }

    public void setMediaFileForEdit(MediaFile f) {
        if (f == null) {
            return;
        }
        if (f != selectedMediaFile) {
            selectionTools.clear();
        }
        stackPane.requestFocus();
        selectedMediaFile = f;
        if (f.getMediaType() != MediaFile.MediaTypes.IMAGE || f.getLoadingError()) {
            viewTools.setFile(null);
            preview.clear();
            imageProgress.setVisible(false);
            editorImageView.setImage(null);
            return;
        }
        editorImageView.setImage(f.getImage());
        editorImageView.setVisible(true);
        imageProgress.progressProperty().unbind();
        imageProgress.setProgress(0);
        imageProgress.setVisible(true);
        viewTools.setFile(f);
        preview.show(f);
    }

    /**
     * @return the 16 bit pipeline that fills the editor image view
     */
    public ImagePreview16 getPreview() {
        return preview;
    }

    public void shutdown() {
        if (factory != null) {
            factory.shutdown();
        }
        executor.shutdownNow();
        preview.shutdown();
    }

    public void resetUI() {
        imageProgress.progressProperty().unbind();
        imageProgress.setProgress(0);
        editorImageView.setImage(null);
    }

    private void cancleTask() {
        preview.clear();
        resetUI();
        editMetadataController.cancleTask();
        editorToolsController.cancleTask();
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
        if (mediaGridView.isShowing()) {
            mediaGridView.hide();
        }
        box = new VBox();
        box.setFillWidth(true);
        box.setPrefSize(700, 100);
        box.setMaxSize(700, 100);
        box.setAlignment(Pos.CENTER);
        mediaGrid = new GridView<>();
        factory = new EditMediaGridCellFactory(executor, EditorMediaViewController.this);
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
        executor.submit(() -> {
            factory.getSelectedMediaFile().saveEdits();
        });
    }

}
