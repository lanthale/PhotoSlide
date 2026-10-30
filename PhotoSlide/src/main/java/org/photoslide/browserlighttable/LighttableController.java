/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browserlighttable;

import org.photoslide.datamodel.MediaGridCell;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.editpipeline.EditHistory;
import org.photoslide.editpipeline.EditPipeline;
import org.photoslide.datamodel.Image16;
import org.photoslide.editormedia.CropAspect;
import org.photoslide.editormedia.CropRotateOverlay;
import org.photoslide.editpipeline.ImagePreview16;
import org.photoslide.datamodel.SidecarWatcher;
import org.photoslide.MainViewController;
import org.photoslide.Utility;
import org.photoslide.browsermetadata.MetadataController;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import org.controlsfx.control.GridView;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.Rating;
import org.controlsfx.control.ToggleSwitch;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.browsercollections.DirectoryWatcher;

import java.util.prefs.Preferences;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.CacheHint;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.SplitPane;
import org.photoslide.ThreadFactoryBuilder;

/**
 *
 * @author selfemp
 */
public class LighttableController implements Initializable {

    private static final String NODE_NAME = "PhotoSlide";
    private MainViewController mainController;
    private Path selectedPath;
    private ExecutorService executor;
    private ScheduledExecutorService executorSchedule;
    private ExecutorService executorParallel;
    private MediaLoadingTask taskMLoading;
    private Task<Boolean> taskSortRead;
    private ObservableList<MediaFile> fullMediaList;
    private FilteredList<MediaFile> filteredMediaList;
    private SortedList<MediaFile> sortedMediaList;
    // one instance per folder: it caches the split filenames
    private MediaFilenameComparator filenameComparator;
    // the filter set when the folder was opened, to avoid filtering and sorting twice
    private Predicate<MediaFile> loadPredicate;
    private ObservableList<String> sortOptions;
    private GridView<MediaFile> imageGrid;
    private final KeyCombination keyCombinationMetaC = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
    private final KeyCombination keyCombinationMetaA = new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN);
    private Preferences pref;

    @FXML
    private ImageView imageView;
    private ImagePreview16 preview;
    private SidecarWatcher sidecarWatcher;
    @FXML
    private ProgressIndicator imageProgress;
    @FXML
    private VBox imageGridPane;
    @FXML
    private Button rotateLeftButton;
    @FXML
    private Button rotateRightButton;
    @FXML
    private Button rateButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Slider zoomSlider;
    @FXML
    private StackPane stackPane;

    private Image dummyImage;
    private Media dummyMedia;
    private Utility util;
    private MetadataController metadataController;
    @FXML
    private MediaView mediaView;
    @FXML
    private StackPane invalidStackPane;
    @FXML
    private FontIcon playIcon;
    private MediaGridCellFactory factory;
    @FXML
    private Button stackButton;
    @FXML
    private Button cropButton;
    @FXML
    private HBox imageStackPane;
    /** Crop mode on the photo (same as in the editor). */
    private CropRotateOverlay cropOverlay;
    private HBox cropBar;
    private ComboBox<String> cropAspect;
    private MediaFile croppedFile;
    /** File of the last history step made here; undo and redo act on it (null = the selected file). */
    private MediaFile historyFile;
    @FXML
    private Label titleLabel;
    @FXML
    private Label cameraLabel;
    @FXML
    private Label filenameLabel;
    @FXML
    private Rating ratingControl;
    @FXML
    private VBox infoPane;
    @FXML
    private VBox optionPane;
    @FXML
    private ToolBar detailToolbar;
    @FXML
    private Label mediaQTYLabel;
    @FXML
    private ComboBox<String> sortOrderComboBox;
    @FXML
    private Button copyButton;
    @FXML
    private Button pasteButton;
    private Image dialogIcon;
    @FXML
    private ToggleButton showDeletedButton;
    @FXML
    private ToggleButton facesButton;
    @FXML
    private Button bookmarkButton;
    private DirectoryWatcher directorywatch;
    @FXML
    private ToggleSwitch showPreviewPaneToggle;
    @FXML
    private Button reloadButton;
    @FXML
    private CheckMenuItem fiveStarMenu;
    @FXML
    private CheckMenuItem fourStarMenu;
    @FXML
    private CheckMenuItem threeStarMenu;
    @FXML
    private CheckMenuItem twoStarMenu;
    @FXML
    private CheckMenuItem oneStarMenu;
    @FXML
    private CheckMenuItem noStarMenu;
    @FXML
    private SplitPane splitPane;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        util = new Utility();
        pref = Preferences.userRoot().node(NODE_NAME);
        imageProgress.setVisible(false);
        zoomSlider.setDisable(true);
        imageView.fitWidthProperty().bind(stackPane.widthProperty());
        imageView.fitHeightProperty().bind(stackPane.heightProperty());
        imageView.setVisible(false);
        preview = new ImagePreview16(imageView, "lighttablePreview");
        preview.setOnProgress((p) -> imageProgress.setProgress(p));
        preview.setOnShown(() -> imageProgress.setVisible(false));
        preview.setOnFailed((ex) -> imageProgress.setVisible(false));
        cropOverlay = new CropRotateOverlay(imageView, preview);
        cropBar = createCropBar();
        stackPane.getChildren().addAll(cropOverlay, cropBar);
        mediaView.fitWidthProperty().bind(stackPane.widthProperty());
        mediaView.fitHeightProperty().bind(stackPane.heightProperty());
        mediaView.setVisible(false);
        playIcon.setVisible(false);
        titleLabel.setText("");
        cameraLabel.setText("");
        filenameLabel.setText("");
        mediaQTYLabel.setText("");
        ratingControl.setRating(0);
        sortOptions = FXCollections.observableArrayList("Filename", "Capture time", "File creation time");
        sortOrderComboBox.setItems(sortOptions);
        sortOrderComboBox.getSelectionModel().selectFirst();
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setPriority(6).setNamePrefix("lightTableController").build());
        executorSchedule = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setPriority(6).setNamePrefix("lightTableControllerScheduled").build());
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNamePrefix("lightTableControllerSelection").build());
        //executorParallel = Executors.newVirtualThreadPerTaskExecutor();
        dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
        showPreviewPaneToggle.selectedProperty().addListener((o) -> {
            previewPaneAnimation();
        });
        reloadButton.visibleProperty().bind(showPreviewPaneToggle.selectedProperty());
    }

    public void injectMainController(MainViewController mainController) {
        this.mainController = mainController;
    }

    public void injectMetaDataController(MetadataController metaController) {
        this.metadataController = metaController;
    }

    /**
     *
     * @param sPath The root path where all collections via directories exists
     */
    public void setSelectedPath(Path sPath) {
        mainController.getStatusLabelLeft().setVisible(true);
        mainController.getStatusLabelLeft().setText("Scanning...");
        mainController.getStatusLabelRight().textProperty().unbind();
        mainController.getStatusLabelRight().setText("");
        mainController.getStatusLabelRight().setVisible(true);
        mainController.getProgressPane().setVisible(true);
        mainController.getProgressbar().progressProperty().unbind();
        mainController.getProgressbar().setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        mainController.getProgressbarLabel().textProperty().unbind();
        mainController.getProgressbarLabel().setText("Retrieving media files...");

        if (directorywatch != null) {
            directorywatch.stopWatch();
        }

        imageGridPane.getChildren().clear();

        if (taskMLoading != null) {
            taskMLoading.cancel();
            if (fullMediaList != null) {
                for (MediaFile mediaFile : fullMediaList) {
                    if (mediaFile.getImage() != null) {
                        mediaFile.getImage().cancel();
                    }
                }
            }
        }
        if (factory != null) {
            factory.cancleTask();
        }
        titleLabel.setVisible(false);
        filenameLabel.setVisible(false);
        cameraLabel.setVisible(false);
        ratingControl.setVisible(false);
        zoomSlider.setDisable(false);
        imageView.setImage(null);
        mediaView.setMediaPlayer(null);
        metadataController.resetGUI();
        mainController.getStatusLabelRight().setVisible(true);
        selectedPath = sPath;

        sortOrderComboBox.getSelectionModel().selectFirst();
        detailToolbar.setDisable(!Clipboard.getSystemClipboard().hasFiles());
        mainController.handleMenuDisable(true);
        pasteButton.setDisable(!Clipboard.getSystemClipboard().hasFiles());
        //sortOrderComboBox.setDisable(true);            
        mainController.getStatusLabelRight().textProperty().unbind();
        mainController.getStatusLabelRight().setText("Retrieve images for " + selectedPath.toString() + "...");

        fullMediaList = FXCollections.synchronizedObservableList(FXCollections.observableArrayList(MediaFile.extractor()));
        startSidecarWatcher(sPath);
        filteredMediaList = new FilteredList<>(fullMediaList, null);
        loadPredicate = standardFilter().and(filterDeleted(showDeletedButton.isSelected()));
        filteredMediaList.setPredicate(loadPredicate);
        sortedMediaList = new SortedList<>(filteredMediaList);
        filenameComparator = new MediaFilenameComparator();
        sortedMediaList.setComparator(filenameComparator);
        imageGrid = new GridView<>(sortedMediaList);
        imageGrid.setCache(true);
        imageGrid.setCacheShape(true);
        imageGrid.setCacheHint(CacheHint.SPEED);
        double defaultCellWidth = imageGrid.getCellWidth();
        double defaultCellHight = imageGrid.getCellHeight();
        factory = new MediaGridCellFactory(this, imageGrid, util, metadataController);

        if (imageGridPane.getChildren().indexOf(imageGrid) == -1) {
            imageGridPane.getChildren().add(imageGrid);
        }
        mainController.getProgressbar().progressProperty().unbind();
        mainController.getProgressbarLabel().textProperty().unbind();
        zoomSlider.setValue(0);

        taskMLoading = new MediaLoadingTask(fullMediaList, factory, sPath, mainController, mediaQTYLabel, sortOrderComboBox.getSelectionModel().getSelectedItem(), metadataController, this);
        taskMLoading.setOnRunning((t) -> {
            factory.setListFilesActive(true);
            mediaQTYLabel.setText("-- media files.");
            mainController.getStatusLabelLeft().setVisible(true);
            mainController.getProgressPane().setVisible(true);
        });
        taskMLoading.setOnSucceeded((WorkerStateEvent t) -> {
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getStatusLabelRight().textProperty().unbind();
            mainController.getStatusLabelLeft().setVisible(false);
            mainController.getProgressbarLabel().setText("");
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setText("");
            //sort media List
            mainController.getStatusLabelRight().setText("Sorting media list...");
            setDefaultSorting();
            mainController.getStatusLabelRight().setText("Sorting media list...finished.");
            factory.setListFilesActive(false);
            imageGrid.requestLayout();
            taskMLoading.saveCacheToDisk();
        });
        taskMLoading.setOnFailed((t2) -> {
            Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, t2.getSource().getException());
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setVisible(false);
            mainController.getStatusLabelRight().setVisible(false);
        });
        taskMLoading.setOnScheduled((t) -> {
            imageGrid.requestFocus();
            imageGrid.setCellFactory(factory);
            imageGrid.requestFocus();
            mainController.getStatusLabelRight().textProperty().bind(taskMLoading.messageProperty());
            mainController.getProgressbarLabel().textProperty().bind(taskMLoading.titleProperty());
            mainController.getProgressPane().setVisible(true);
            mainController.getStatusLabelLeft().setVisible(true);
        });
        executorParallel.submit(taskMLoading);
        //executorSchedule.schedule(taskMLoading, 50, TimeUnit.MILLISECONDS);
        this.mainController.getTaskProgressView().getTasks().add(taskMLoading);
        imageGrid.setOnKeyPressed((t) -> {
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
            if (keyCombinationMetaA.match(t)) {
                fullMediaList.forEach((mediafile) -> {
                    factory.getSelectionModel().add(mediafile);
                });
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
        imageGrid.setOnDragDetected((t) -> {
            /* drag was detected, start a drag-and-drop gesture*/
            Dragboard db = imageGrid.startDragAndDrop(TransferMode.ANY);
            final ClipboardContent content = new ClipboardContent();
            List<File> fileList = new ArrayList<>();
            Set<MediaFile> selection = factory.getSelectionModel().getSelection();
            for (MediaFile mediaFile : selection) {
                fileList.add(mediaFile.getPathStorage().toFile());
            }
            content.putFiles(fileList);
            db.setContent(content);
            t.consume();
        });
        imageGrid.setOnDragOver((t) -> {
            t.acceptTransferModes(TransferMode.COPY);
            t.consume();
        });
        imageGrid.setOnDragDropped((t) -> {
            Dragboard db = t.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                MediaLoadingTaskFiles taskMLoadingFiles = new MediaLoadingTaskFiles(files, fullMediaList, factory, sPath, mainController, mediaQTYLabel, sortOrderComboBox.getSelectionModel().getSelectedItem(), metadataController);
                executorParallel.submit(taskMLoadingFiles);
                taskMLoadingFiles.setOnFailed((tf) -> {
                    Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, tf.getSource().getException());
                    mainController.getProgressbar().progressProperty().unbind();
                    mainController.getProgressbarLabel().textProperty().unbind();
                    mainController.getProgressPane().setVisible(false);
                    mainController.getStatusLabelLeft().setVisible(false);
                });
                taskMLoadingFiles.setOnSucceeded((tc) -> {
                    mainController.getStatusLabelLeft().setVisible(false);
                    mainController.getProgressPane().setVisible(false);
                    filteredMediaList.setPredicate(standardFilter().and(filterDeleted(showDeletedButton.isSelected())));
                    sortedMediaList.setComparator(filenameComparator);
                    mainController.getProgressbar().progressProperty().unbind();
                    mainController.getProgressbarLabel().textProperty().unbind();
                    mainController.getStatusLabelRight().textProperty().unbind();
                    util.hideNodeAfterTime(mainController.getStatusLabelRight(), 2, true);
                    sortOrderComboBox.setDisable(false);
                    mainController.getStatusLabelRight().setText("Finished MediaLoading Task.");
                    util.hideNodeAfterTime(mainController.getStatusLabelRight(), 2, true);
                    mainController.getProgressPane().setVisible(false);
                    mainController.getStatusLabelLeft().setText("");
                });
                success = true;
            }
            t.setDropCompleted(success);
            t.consume();
        });
        zoomSlider.valueProperty().addListener((ObservableValue<? extends Number> ov, Number t, Number t1) -> {
            imageGrid.setCellWidth(defaultCellWidth + 3 * zoomSlider.getValue());
            imageGrid.setCellHeight(defaultCellHight + 3 * zoomSlider.getValue());
        });
        imageGrid.setOnMouseClicked((t) -> {
            if (factory.getSelectionModel().selectionCount() > 1) {
                factory.getSelectionModel().clear();
            }
        });
        /*imageGrid.setOnScroll(new EventHandler<ScrollEvent>() {
            private SmoothishTransition transition;

            @Override
            public void handle(ScrollEvent event) {
                double deltaY = BASE_MODIFIER * event.getDeltaY();
                double width = imageGrid.getBoundsInLocal().getWidth();
                double vvalue = imageGrid.getVvalue();
                Interpolator interp = Interpolator.LINEAR;
                transition = new SmoothishTransition(transition, deltaY) {
                    @Override
                    protected void interpolate(double frac) {
                        double x = interp.interpolate(vvalue, vvalue + -deltaY * getMod() / width, frac);
                        imageGrid.setVvalue(x);
                    }
                };
                transition.play();
            }
        });*/
    }

    public void setDefaultSorting() {
        sortOrderComboBox.setDisable(false);
        // the lists already have this filter and order unless it was changed
        // while loading: setting them again filters and sorts all items again
        if (filteredMediaList.getPredicate() != loadPredicate) {
            filteredMediaList.setPredicate(standardFilter().and(filterDeleted(showDeletedButton.isSelected())));
        }
        if (sortedMediaList.getComparator() != filenameComparator) {
            sortedMediaList.setComparator(filenameComparator);
        }
    }

    /**
     * selects the next image
     *
     * @param idx option parameter - if provided it it selects an the next item
     * based on the provided index-1
     */
    public void selectPreviousImageInGrid(int... idx) {
        int actIndex = -1;
        if (idx.length == 0) {
            actIndex = sortedMediaList.indexOf(factory.getSelectedMediaItem());
        } else {
            actIndex = idx[0] - 1;
        }
        actIndex = actIndex - 1;
        if (actIndex >= 0) {
            MediaGridCell nextCell = factory.getMediaCellForMediaFile(sortedMediaList.get(actIndex));
            if (nextCell != null) {
                nextCell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0,
                        0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false,
                        false, false, false, false, false, true, null));
                factory.oneRowUp(nextCell, imageGrid.getCellHeight());
                nextCell.requestLayout();
                nextCell.requestFocus();
            }
        }
    }

    /**
     * selects the next image
     *
     * @param idx option parameter - if provided it it selects an the next item
     * based on the provided index-1
     */
    public void selectNextImageInGrid(int... idx) {
        int actIndex = -1;
        if (idx.length == 0) {
            actIndex = sortedMediaList.indexOf(factory.getSelectedMediaItem());
        } else {
            actIndex = idx[0] - 1;
        }
        actIndex = actIndex + 1;
        if (actIndex < sortedMediaList.size()) {
            MediaGridCell nextCell = factory.getMediaCellForMediaFile(sortedMediaList.get(actIndex));
            if (nextCell != null) {
                nextCell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0,
                        0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false,
                        false, false, false, false, false, true, null));
                factory.oneRowDown(nextCell, imageGrid.getCellHeight());
                nextCell.requestLayout();
                nextCell.requestFocus();
            }
        }
    }

    public void Shutdown() {
        if (sidecarWatcher != null) {
            sidecarWatcher.stop();
        }
        if (!imageGridPane.getChildren().isEmpty()) {
            imageGridPane.getChildren().remove(0);
        }
        if (taskMLoading != null) {
            taskMLoading.cancel();
        }
        if (factory != null) {
            factory.shutdown();
        }
        if (executorParallel != null) {
            executorParallel.shutdownNow();
        }
        if (executorSchedule != null) {
            executorSchedule.shutdownNow();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @FXML
    private void rotateLeftButtonAction(ActionEvent event) {
        rotateLeftAction();
    }

    public void rotateLeftAction() {
        turn(-90);
    }

    @FXML
    private void rotateRightButtonAction(ActionEvent event) {
        rotateRightAction();
    }

    public void rotateRightAction() {
        turn(90);
    }

    /** Quarter turn of the selected photo, the same as in the editor (saves, thumbnails, history). */
    private void turn(double degrees) {
        MediaFile item = factory == null ? null : factory.getSelectedMediaItem();
        if (item == null) {
            return;
        }
        EditHistory.of(item);
        EditPipeline.turn(item, degrees);
        historyFile = item;
        bindFitToRotation(item.getRotationAngleProperty().get());
    }

    /** Fits the image view into the pane, swapped for a quarter turn. */
    public void bindFitToRotation(double angle) {
        imageView.fitWidthProperty().unbind();
        imageView.fitHeightProperty().unbind();
        boolean quarter = EditPipeline.normalizedAngle(angle) % 180 != 0;
        imageView.fitWidthProperty().bind(quarter ? stackPane.heightProperty() : stackPane.widthProperty());
        imageView.fitHeightProperty().bind(quarter ? stackPane.widthProperty() : stackPane.heightProperty());
    }

    @FXML
    private void rateButtonAction(ActionEvent event) {
        rateAction();
    }

    public void rateAction() {
        final MediaFile item = factory.getSelectedCell().getItem();
        EditHistory.of(item);
        PopOver popOver = new PopOver();
        popOver.setDetachable(false);
        popOver.setAnimated(true);
        Rating rate = new Rating();
        rate.setMax(5);
        rate.setRating(0);
        VBox box = new VBox(rate);
        box.setAlignment(Pos.CENTER);
        popOver.setContentNode(box);
        popOver.setArrowLocation(PopOver.ArrowLocation.BOTTOM_CENTER);
        popOver.setFadeInDuration(new Duration(100));
        rate.ratingProperty().addListener((o) -> {
            rate.ratingProperty().unbindBidirectional(factory.getSelectedCell().getItem().getRatingProperty());
            popOver.hide();
        });
        popOver.setOnHidden((t) -> {
            recordStep(item, null);
            savingMediaFileEdits(item);
        });
        rate.ratingProperty().bindBidirectional(factory.getSelectedCell().getItem().getRatingProperty());
        popOver.show(rateButton);
    }

    @FXML
    private void deleteButtonAction(ActionEvent event) {
        deleteAction();
    }

    public void deleteAction() {
        final int idx = fullMediaList.indexOf(factory.getSelectedCell().getItem());
        final MediaFile item = fullMediaList.get(idx);
        EditHistory.of(item);
        item.setDeleted(true);
        recordStep(item, "Delete");
        hideDetail();
        savingMediaFileEdits(item);
    }

    private void hideDetail() {
        imageView.setImage(null);
        getTitleLabel().setVisible(false);
        getCameraLabel().setVisible(false);
        getFilenameLabel().setVisible(false);
        getRatingControl().setVisible(false);
    }

    // ------------------------------------------------------------------
    // history: undo / redo of the steps made here
    // ------------------------------------------------------------------
    /**
     * Called when a media file is selected in the grid, before it can be
     * changed: starts its history, so that the following changes are steps.
     */
    public void mediaFileSelected(MediaFile f) {
        historyFile = null;
        if (f != null) {
            EditHistory.of(f);
        }
    }

    /** Records the change of the file as a history step; undo and redo follow it. */
    public void recordStep(MediaFile item, String label) {
        EditPipeline.metadataChanged(item, label);
        historyFile = item;
    }

    /** The file undo and redo act on: the one changed last here, else the selected one. */
    public MediaFile historyTarget() {
        if (historyFile != null) {
            return historyFile;
        }
        return factory == null ? null : factory.getSelectedMediaItem();
    }

    public void undoAction() {
        stepHistory(true);
    }

    public void redoAction() {
        stepHistory(false);
    }

    private void stepHistory(boolean back) {
        MediaFile f = historyTarget();
        EditHistory history = f == null ? null : EditHistory.peek(f);
        if (history == null || !(back ? history.canUndo() : history.canRedo())) {
            return;
        }
        cancelCrop();
        if (back) {
            history.undo(f);
        } else {
            history.redo(f);
        }
        filteredMediaList.setPredicate(getStarFilter());       // delete mark or rating may have changed
        boolean selected = factory.getSelectedMediaItem() == f;
        if (f.isDeleted()) {
            if (selected) {
                hideDetail();
            }
        } else if (selected && imageView.getImage() != null) {
            bindFitToRotation(f.getRotationAngleProperty().get());
            preview.setViewport(f.getCropView());
        } else {
            MediaGridCell cell = factory.getMediaCellForMediaFile(f);
            if (cell != null) {
                cell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0,
                        0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false,
                        false, false, false, false, false, true, null));
            }
        }
        historyFile = f;
    }

    /**
     * Show progressbar during saving
     *
     * @param item media items to be saved
     */
    private void savingMediaFileEdits(final MediaFile item) {
        final int sIdx = sortedMediaList.indexOf(factory.getSelectedCell().getItem());
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                item.saveEdits();
                return true;
            }
        };
        task.setOnScheduled((t) -> {
            mainController.getProgressPane().setVisible(true);
            mainController.getProgressbar().setVisible(true);
            mainController.getProgressbarLabel().setText("Saving edits...");
            mainController.getProgressbar().setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        });
        task.setOnSucceeded((t) -> {
            mainController.getProgressPane().setVisible(false);
            MediaFile changed = historyFile;
            selectNextImageInGrid(sIdx);
            historyFile = changed;          // undo still goes to the changed file
        });
        Thread.ofVirtual().start(task);
    }

    @FXML
    private void stackButtonAction(ActionEvent event) {
        if (stackButton.getText().equalsIgnoreCase("stack")) {
            String fileName = ((MediaFile) factory.getSelectionModel().getSelection().iterator().next()).getPathStorage().toFile().getName();
            String stackName = fileName.substring(0, fileName.lastIndexOf("."));
            AtomicInteger i = new AtomicInteger(0);
            Iterator<MediaFile> iterator = factory.getSelectionModel().getSelection().iterator();
            while (iterator.hasNext()) {
                MediaFile mediaF = (MediaFile) iterator.next();
                ((MediaFile) mediaF).setStacked(true);
                ((MediaFile) mediaF).setStackName(stackName);
                ((MediaFile) mediaF).setStackPos(i.addAndGet(1));
                savingMediaFileEdits(((MediaFile) mediaF));
            }
            filteredMediaList.setPredicate(standardFilter());
        } else {
            String stackname = factory.getSelectedMediaItem().getStackName();
            FilteredList<MediaFile> stackList = fullMediaList.filtered(mFile -> mFile.getStackName().equalsIgnoreCase(stackname));
            stackList.forEach((mediaFile) -> {
                mediaFile.setStackPos(-1);
                mediaFile.setStackName(null);
                mediaFile.setStacked(false);
                savingMediaFileEdits(mediaFile);
            });
            filteredMediaList.setPredicate(standardFilter());
            stackButton.setText("Stack");
        }
    }

    @FXML
    public void cropButtonAction(ActionEvent event) {
        cropAction();

    }

    /** Starts the crop mode on the shown photo, or applies the frame if it is active. */
    public void cropAction() {
        if (cropOverlay.mode() == CropRotateOverlay.Mode.CROP) {
            cropOverlay.commit();
            return;
        }
        MediaFile item = factory == null || factory.getSelectedCell() == null ? null : factory.getSelectedCell().getItem();
        Image16 source = preview.getSource();
        if (item == null || item.getMediaType() != MediaFile.MediaTypes.IMAGE || preview.getFile() != item || source == null) {
            return;
        }
        EditHistory.of(item);
        croppedFile = item;
        infoPane.setDisable(true);
        optionPane.setDisable(true);
        preview.setViewport(null);                  // the frame needs the whole photo
        cropOverlay.startCrop(item.getCropView(), this::finishCrop, this::cancelCrop);
        cropAspect.getSelectionModel().select(CropAspect.initial(item.getCropView(), source.width(), source.height(),
                CropAspect.isQuarter(item.getRotationAngleProperty().get())));
        cropOverlay.setAspect(cropRatio());
        cropBar.setVisible(true);
    }

    /** Frame ratio of the chosen aspect for the photo being cropped (0 = free). */
    private double cropRatio() {
        Image16 source = preview.getSource();
        if (croppedFile == null || source == null) {
            return 0;
        }
        return CropAspect.frameRatio(cropAspect.getSelectionModel().getSelectedIndex(), source.width(), source.height(),
                CropAspect.isQuarter(croppedFile.getRotationAngleProperty().get()));
    }

    private void finishCrop(Rectangle2D crop) {
        MediaFile item = croppedFile;
        endCrop();
        if (item == null) {
            return;
        }
        if (!java.util.Objects.equals(crop, item.getCropView())) {
            item.setCropView(crop);
            EditPipeline.filtersChanged(item, crop == null ? "Reset crop" : "Crop");
            historyFile = item;
        }
        if (preview.getFile() == item) {
            preview.setViewport(crop);
        }
    }

    /** Leaves the crop mode without changing the crop (Esc, or another photo is selected). */
    public void cancelCrop() {
        MediaFile item = croppedFile;
        if (cropOverlay.mode() == CropRotateOverlay.Mode.NONE) {
            return;
        }
        endCrop();
        if (item != null && preview.getFile() == item) {
            preview.setViewport(item.getCropView());
        }
    }

    private void endCrop() {
        cropOverlay.stop();
        cropBar.setVisible(false);
        infoPane.setDisable(false);
        optionPane.setDisable(false);
        croppedFile = null;
        imageView.requestFocus();
    }

    public boolean isCropping() {
        return cropOverlay.mode() != CropRotateOverlay.Mode.NONE;
    }

    /** Aspect ratio, reset and apply over the photo while cropping. */
    private HBox createCropBar() {
        cropAspect = CropAspect.comboBox();
        cropAspect.setOnAction(e -> cropOverlay.setAspect(cropRatio()));
        Label label = new Label("Aspect ratio");
        Button reset = new Button("Whole photo", new FontIcon("fa-expand"));
        reset.setOnAction(e -> cropOverlay.resetCrop());
        Button cancel = new Button("Cancel");
        cancel.setOnAction(e -> cancelCrop());
        Button apply = new Button("Apply", new FontIcon("fa-check"));
        apply.setOnAction(e -> cropOverlay.commit());
        HBox bar = new HBox(8, label, cropAspect, reset, cancel, apply);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 10, 6, 10));
        bar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        bar.setStyle("-fx-background-color: rgba(40,40,40,0.85); -fx-background-radius: 6;");
        StackPane.setAlignment(bar, Pos.TOP_CENTER);
        StackPane.setMargin(bar, new Insets(8));
        bar.setVisible(false);
        return bar;
    }

    public HBox getImageStackPane() {
        return imageStackPane;
    }

    public ImageView getImageView() {
        return imageView;
    }

    /**
     * Decodes the shown image again from disk, e.g. after the RAW settings
     * were changed.
     */
    @FXML
    private void reloadImageAction(ActionEvent event) {
        if (preview.getFile() == null) {
            return;
        }
        imageProgress.progressProperty().unbind();
        imageProgress.setProgress(0);
        imageProgress.setVisible(true);
        preview.reload();
    }

    /**
     * Reloads the edits of the shown media files when their XMP sidecars are
     * changed or deleted by another program while the folder is open.
     */
    private void startSidecarWatcher(Path folder) {
        if (sidecarWatcher != null) {
            sidecarWatcher.stop();
        }
        final ObservableList<MediaFile> list = fullMediaList;
        sidecarWatcher = new SidecarWatcher(folder, sidecar -> {
            Path key = sidecar.toAbsolutePath().normalize();
            List<MediaFile> matches = new ArrayList<>();
            synchronized (list) {
                for (MediaFile m : list) {
                    if (m.getEditFilePath().toAbsolutePath().normalize().equals(key)) {
                        matches.add(m);
                    }
                }
            }
            return matches;
        }, (m, sidecar) -> {
            m.reloadEdits();
            EditPipeline.editsReloaded(m);
            if (factory != null && factory.getSelectedMediaItem() == m && !isCropping()) {
                // the crop is in pixels of the original, the view may show a preview
                Rectangle2D crop = m.getCropView();
                double sc = preview.getDisplayScale();
                imageView.setViewport(crop == null ? null : new Rectangle2D(crop.getMinX() * sc,
                        crop.getMinY() * sc, crop.getWidth() * sc, crop.getHeight() * sc));
            }
        });
        sidecarWatcher.start();
    }

    /**
     * @return the 16 bit pipeline that fills the image view
     */
    public ImagePreview16 getPreview() {
        return preview;
    }

    public MediaView getMediaView() {
        return mediaView;
    }

    public FontIcon getPlayIcon() {
        return playIcon;
    }

    public ProgressIndicator getImageProgress() {
        return imageProgress;
    }

    public StackPane getInvalidStackPane() {
        return invalidStackPane;
    }

    public Label getTitleLabel() {
        return titleLabel;
    }

    public Label getCameraLabel() {
        return cameraLabel;
    }

    public Label getFilenameLabel() {
        return filenameLabel;
    }

    public Rating getRatingControl() {
        return ratingControl;
    }

    public ToolBar getDetailToolbar() {
        return detailToolbar;
    }

    public VBox getOptionPane() {
        return optionPane;
    }

    public MediaGridCellFactory getFactory() {
        return factory;
    }

    public ObservableList<MediaFile> getFullMediaList() {
        return fullMediaList;
    }

    public MediaFilenameComparator getFilenameComparator() {
        return filenameComparator;
    }

    public FilteredList<MediaFile> getFilteredMediaList() {
        return filteredMediaList;
    }

    public SortedList<MediaFile> getSortedMediaList() {
        return sortedMediaList;
    }

    public VBox getInfoPane() {
        return infoPane;
    }

    public ComboBox<String> getSortOrderComboBox() {
        return sortOrderComboBox;
    }

    @FXML
    private void selectSortOrderAction(ActionEvent event) {
        // a capture time read still running would keep the progressbar bound
        if (taskSortRead != null && taskSortRead.isRunning()) {
            taskSortRead.cancel();
        }
        mainController.getProgressbar().progressProperty().unbind();
        mainController.getProgressbarLabel().textProperty().unbind();
        if (sortOrderComboBox.getSelectionModel().getSelectedItem().equalsIgnoreCase("Capture time")) {
            if (taskMLoading.isRunning() == true) {
                taskMLoading.cancel();
                setSelectedPath(selectedPath);
            } else {
                mainController.getStatusLabelLeft().setVisible(true);
                mainController.getStatusLabelLeft().setText("Sorting...");
                mainController.getProgressPane().setVisible(true);
                mainController.getProgressbar().setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);

                Task<Boolean> taskMeta = new Task<>() {
                    @Override
                    protected Boolean call() throws IOException {
                        int total = fullMediaList.size();
                        metadataController.readRecordTimes(this, fullMediaList, (n) -> {
                            updateProgress(n, total);
                            updateMessage("Read record time " + n + "/" + total);
                        });
                        return true;
                    }
                };
                taskMeta.setOnSucceeded((t) -> {
                    try {
                        sortedMediaList.setComparator(Comparator.comparing(MediaFile::getRecordTime));
                    } catch (NullPointerException e) {
                        sortOrderComboBox.getSelectionModel().clearSelection(0);
                    }
                    mainController.getProgressPane().setVisible(false);
                    mainController.getStatusLabelLeft().setText("");
                    mainController.getProgressbar().progressProperty().unbind();
                    mainController.getProgressbarLabel().textProperty().unbind();
                    // the capture times are kept in the cache: the next sort reads no file
                    if (taskMLoading != null) {
                        taskMLoading.saveCacheToDisk();
                    }
                });
                taskMeta.setOnFailed((t) -> {
                    mainController.getProgressPane().setVisible(false);
                    mainController.getStatusLabelLeft().setText("");
                    mainController.getProgressbar().progressProperty().unbind();
                    mainController.getProgressbarLabel().textProperty().unbind();
                });
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressbar().progressProperty().bind(taskMeta.progressProperty());
                mainController.getProgressbarLabel().textProperty().unbind();
                mainController.getProgressbarLabel().textProperty().bind(taskMeta.messageProperty());
                taskMeta.setOnCancelled((t) -> {
                    mainController.getProgressPane().setVisible(false);
                    mainController.getStatusLabelLeft().setText("");
                });
                taskSortRead = taskMeta;
                executor.submit(taskMeta);
                mainController.getTaskProgressView().getTasks().add(taskMeta);
            }
        }
        if (sortOrderComboBox.getSelectionModel().getSelectedItem().equalsIgnoreCase("Filename")) {
            if (taskMLoading.isRunning() == true) {
                taskMLoading.cancel();
                setSelectedPath(selectedPath);
            } else {
                sortedMediaList.setComparator(filenameComparator);
            }
        }
        if (sortOrderComboBox.getSelectionModel().getSelectedItem().equalsIgnoreCase("File creation time")) {
            if (taskMLoading.isRunning() == true) {
                taskMLoading.cancel();
                setSelectedPath(selectedPath);
            } else {
                Comparator<MediaFile> comparing = Comparator.comparing((MediaFile t) -> {
                    try {
                        return t.getCreationTime(); //To change body of generated lambdas, choose Tools | Templates.
                    } catch (IOException ex) {
                        Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    return null;
                });
                sortedMediaList.setComparator(comparing);
            }
        }
    }

    public StackPane getStackPane() {
        return stackPane;
    }

    @FXML
    private void copyButtonAction(ActionEvent event) {
        copyAction();
    }

    @FXML
    private void pasteButtonAction(ActionEvent event) {
        pastAction();
    }

    public void copyAction() {
        mainController.getStatusLabelLeft().setVisible(true);
        mainController.getStatusLabelLeft().setText("Copying to clipboard...");
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        List<File> filesForClipboard = new ArrayList<>();
        filteredMediaList.stream().filter(c -> c.isSelected() == true).forEach((mfile) -> {
            filesForClipboard.add(mfile.getPathStorage().toFile());
            if (mfile.getEditFilePath().toFile().exists()) {
                filesForClipboard.add(mfile.getEditFilePath().toFile());
            }
        });
        content.putFiles(filesForClipboard);
        clipboard.setContent(content);
        pasteButton.setDisable(false);
        mainController.getStatusLabelLeft().setText("Copying to clipboard...Done!");
        util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 3, true);
    }

    public void pastAction() {
        mainController.getProgressPane().setVisible(true);
        mainController.getStatusLabelLeft().setVisible(true);
        mainController.getStatusLabelLeft().setText("Pasting...");
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        List<File> clipboardFileList = new ArrayList<>();
        if (clipboard.hasFiles()) {
            clipboardFileList = clipboard.getFiles();
        }
        final List<File> files = clipboardFileList;
        Task<String> pasteTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                files.forEach((fileItem) -> {
                    if (this.isCancelled() == false) {
                        try {
                            updateProgress(files.indexOf(fileItem) + 1, files.size());
                            updateMessage("" + (files.indexOf(fileItem) + 1) + "/" + files.size());
                            Files.copy(fileItem.toPath(), selectedPath.resolve(fileItem.getName()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException ex) {
                            Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                });
                clipboard.clear();
                return "Successfully!";
            }

        };
        mainController.getProgressbar().progressProperty().unbind();
        mainController.getProgressbar().progressProperty().bind(pasteTask.progressProperty());
        mainController.getProgressbarLabel().textProperty().unbind();
        mainController.getProgressbarLabel().textProperty().bind(pasteTask.messageProperty());
        pasteTask.setOnFailed((t) -> {
            Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, t.getSource().getMessage(), t.getSource().getException());
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setVisible(false);
        });
        pasteTask.setOnSucceeded((t) -> {
            pasteButton.setDisable(true);
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setVisible(false);
            mainController.getStatusLabelLeft().setText("Pasting...Done!");
            util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 3, true);
            setSelectedPath(selectedPath);
        });
        if (clipboard.hasFiles()) {
            Thread.ofVirtual().start(pasteTask);
            mainController.getTaskProgressView().getTasks().add(pasteTask);
        } else {
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setVisible(false);
        }
    }

    public MainViewController getMainController() {
        return mainController;
    }

    public void resetLightTableView() {
        if (fullMediaList != null) {
            fullMediaList.clear();
        }
    }

    private Predicate<MediaFile> baseFilter() {
        return mFile -> mFile.isStacked() == false;// || mFile.getStackPos() == 1;
    }

    public Predicate<MediaFile> standardFilter() {
        Predicate<MediaFile> filterDeleted = filterDeleted(showDeletedButton.isSelected());
        return baseFilter().and(filterDeleted).or(mFile -> mFile.getStackPos() == 1);
    }

    public Button getRotateLeftButton() {
        return rotateLeftButton;
    }

    public Button getRotateRightButton() {
        return rotateRightButton;
    }

    public Button getRateButton() {
        return rateButton;
    }

    public Button getDeleteButton() {
        return deleteButton;
    }

    public Button getStackButton() {
        return stackButton;
    }

    public Button getCropButton() {
        return cropButton;
    }

    public Button getCopyButton() {
        return copyButton;
    }

    public Button getPasteButton() {
        return pasteButton;
    }

    public void updateSortFiltering() {
        filteredMediaList.setPredicate(standardFilter());
    }

    public void saveSettings() {
        pref.putBoolean("showDeletedButton", showDeletedButton.isSelected());
    }

    public void restoreSettings() {
        boolean aBoolean = pref.getBoolean("showDeletedButton", false);
        showDeletedButton.setSelected(aBoolean);
    }

    private Predicate<MediaFile> filterDeleted(boolean showDeleted) {
        if (showDeleted == false) {
            return mFile -> mFile.isDeleted() == false;
        } else {
            return mFile -> true;
        }
    }

    private Predicate<MediaFile> filterStar(int starRating) {
        switch (starRating) {
            case 5 -> {
                return mFile -> mFile.getRatingProperty().get() == 5;
            }
            case 4 -> {
                return mFile -> mFile.getRatingProperty().get() == 4;
            }
            case 3 -> {
                return mFile -> mFile.getRatingProperty().get() == 3;
            }
            case 2 -> {
                return mFile -> mFile.getRatingProperty().get() == 2;
            }
            case 1 -> {
                return mFile -> mFile.getRatingProperty().get() == 1;
            }
            case 0 -> {
                return mFile -> true;
            }
        }
        return mFile -> true;
    }

    private Predicate<MediaFile> getStarFilter() {
        Predicate<MediaFile> baseFilter = standardFilter().and(filterDeleted(showDeletedButton.isSelected()));
        if (oneStarMenu.isSelected()) {
            baseFilter = baseFilter.and(filterStar(1));
        }
        if (twoStarMenu.isSelected()) {
            baseFilter = baseFilter.and(filterStar(2));
        }
        if (threeStarMenu.isSelected()) {
            baseFilter = baseFilter.and(filterStar(3));
        }
        if (fourStarMenu.isSelected()) {
            baseFilter = baseFilter.and(filterStar(4));
        }
        if (fiveStarMenu.isSelected()) {
            baseFilter = baseFilter.and(filterStar(5));
        }
        return baseFilter;
    }

    @FXML
    private void showDeletedButtonAction(ActionEvent event) {
        filteredMediaList.setPredicate(standardFilter().and(filterDeleted(showDeletedButton.isSelected())));
    }

    @FXML
    private void fiveStarMenuAction(ActionEvent event) {
        filteredMediaList.setPredicate(getStarFilter());
    }

    @FXML
    private void fourStarMenuAction(ActionEvent event) {
        filteredMediaList.setPredicate(getStarFilter());
    }

    @FXML
    private void threeStarMenuAction(ActionEvent event) {
        filteredMediaList.setPredicate(getStarFilter());
    }

    @FXML
    private void twoStarMenuAction(ActionEvent event) {
        filteredMediaList.setPredicate(getStarFilter());
    }

    @FXML
    private void oneStarMenuAction(ActionEvent event) {
        filteredMediaList.setPredicate(getStarFilter());
    }

    @FXML
    private void noStarMenuAction(ActionEvent event) {
        oneStarMenu.setSelected(false);
        twoStarMenu.setSelected(false);
        threeStarMenu.setSelected(false);
        fourStarMenu.setSelected(false);
        fiveStarMenu.setSelected(false);
        noStarMenu.setSelected(false);
        filteredMediaList.setPredicate(standardFilter().and(filterDeleted(showDeletedButton.isSelected())));
    }

    public GridView<MediaFile> getImageGrid() {
        return imageGrid;
    }

    public Button getBookmarkButton() {
        return bookmarkButton;
    }

    @FXML
    private void bookmarkButtonAction(ActionEvent event) {
        bookmarkSelection();
    }

    private void bookmarkSelection() {
        Set<MediaFile> selection = factory.getSelectionModel().getSelection();
        for (MediaFile m : selection) {
            mainController.bookmarkMediaFile(m);
        }
        mainController.saveBookmarksFile();
    }

    public ToggleSwitch getShowPreviewPaneToggle() {
        return showPreviewPaneToggle;
    }

    private void previewPaneAnimation() {
        if (showPreviewPaneToggle.isSelected()) {
            final Timeline timeline = new Timeline();
            timeline.setCycleCount(1);
            timeline.setAutoReverse(false);
            final KeyValue kv = new KeyValue(splitPane.getDividers().get(0).positionProperty(), 0.65);
            final KeyFrame kf = new KeyFrame(Duration.millis(500), kv);
            timeline.getKeyFrames().add(kf);
            final KeyValue kv2 = new KeyValue(zoomSlider.valueProperty(), 0);
            final KeyFrame kf2 = new KeyFrame(Duration.millis(500), kv2);
            timeline.getKeyFrames().add(kf2);
            timeline.play();
            zoomSlider.setValue(0);
        } else {
            final Timeline timeline = new Timeline();
            timeline.setCycleCount(1);
            timeline.setAutoReverse(false);
            final KeyValue kv = new KeyValue(splitPane.getDividers().get(0).positionProperty(), 0.1);
            final KeyFrame kf = new KeyFrame(Duration.millis(500), kv);
            timeline.getKeyFrames().add(kf);
            if (zoomSlider.getValue() < 12) {
                final KeyValue kv2 = new KeyValue(zoomSlider.valueProperty(), 12);
                final KeyFrame kf2 = new KeyFrame(Duration.millis(500), kv2);
                timeline.getKeyFrames().add(kf2);
            }
            timeline.play();
        }
    }

    @FXML
    private void faceRecognitationAction(ActionEvent event) {
        /*HaarCascadeDetector detector = new HaarCascadeDetector(100);
        List<DetectedFace> faces = null;
        MediaManager media = DefaultMediaManager.INSTANCE;
        String fileName = "file.jpg";

        BufferedImage img = null;

        faces = detector.detectFaces(ImageUtilities.createFImage(img));
        System.out.println("detect faces size = " + faces.size());
        Quadrilateral_F64 location = new Quadrilateral_F64();
        for (DetectedFace f : faces) {
            System.out.println("bounds: " + f.getBounds());
            location = new Quadrilateral_F64(
                    f.getBounds().getTopLeft().getX(), f.getBounds().getTopLeft().getY(),
                    f.getBounds().getTopLeft().getX(), f.getBounds().getTopLeft().getY() + f.getBounds().getHeight(),
                    f.getBounds().getTopLeft().getX() + f.getBounds().getWidth(), f.getBounds().getTopLeft().getY(),
                    f.getBounds().getTopLeft().getX() + f.getBounds().getWidth(), f.getBounds().getTopLeft().getY() + f.getBounds().getHeight());
            break;
        }*/
    }

}
