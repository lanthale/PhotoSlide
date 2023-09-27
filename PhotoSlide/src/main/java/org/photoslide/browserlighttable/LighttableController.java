/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browserlighttable;

import org.photoslide.datamodel.MediaGridCell;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.MainViewController;
import org.photoslide.Utility;
import org.photoslide.browsermetadata.MetadataController;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
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
import javafx.geometry.Point2D;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import org.controlsfx.control.GridView;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.Rating;
import org.controlsfx.control.SnapshotView;
import org.controlsfx.control.ToggleSwitch;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.browsercollections.DirectoryWatcher;

import java.util.prefs.Preferences;
import javafx.scene.CacheHint;
import javafx.scene.control.CheckMenuItem;
import one.microstream.storage.embedded.types.EmbeddedStorage;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.browsercollections.MediaFilenameComparator;

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
    private ObservableList<MediaFile> fullMediaList;
    private FilteredList<MediaFile> filteredMediaList;
    private SortedList<MediaFile> sortedMediaList;
    private ObservableList<String> sortOptions;
    private GridView<MediaFile> imageGrid;
    private final KeyCombination keyCombinationMetaC = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);
    private final KeyCombination keyCombinationMetaA = new KeyCodeCombination(KeyCode.A, KeyCombination.SHORTCUT_DOWN);
    private Preferences pref;

    @FXML
    private ImageView imageView;
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
    private SnapshotView snapshotView;
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

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        util = new Utility();
        pref = Preferences.userRoot().node(NODE_NAME);
        imageProgress.setVisible(false);
        zoomSlider.setDisable(true);
        imageView.fitWidthProperty().bind(stackPane.widthProperty());
        imageView.fitHeightProperty().bind(stackPane.heightProperty());
        imageView.setVisible(false);
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
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNamePrefix("lightTableController").build());
        executorSchedule = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setPriority(8).setNamePrefix("lightTableControllerScheduled").build());
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNamePrefix("lightTableControllerSelection").build());
        //executorParallel = Executors.newVirtualThreadPerTaskExecutor();
        dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
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
        Platform.runLater(() -> {
            mainController.getStatusLabelLeft().setVisible(true);
            mainController.getStatusLabelLeft().setText("Scanning...");
            mainController.getStatusLabelRight().textProperty().unbind();
            mainController.getStatusLabelRight().setText("");
            mainController.getStatusLabelRight().setVisible(true);
            mainController.getProgressPane().setVisible(true);
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbar().setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressbarLabel().setText("Retrieving files...");
        });

        if (directorywatch != null) {
            directorywatch.stopWatch();
        }

        if (Platform.isFxApplicationThread()) {
            imageGridPane.getChildren().clear();
        } else {
            Platform.runLater(() -> {
                imageGridPane.getChildren().clear();
            });
        }
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
        Platform.runLater(() -> {
            detailToolbar.setDisable(!Clipboard.getSystemClipboard().hasFiles());
            mainController.handleMenuDisable(true);
            pasteButton.setDisable(!Clipboard.getSystemClipboard().hasFiles());
            //sortOrderComboBox.setDisable(true);            
            mainController.getStatusLabelRight().textProperty().unbind();
            mainController.getStatusLabelRight().setText("Retrieve images for " + selectedPath.toString() + "...");
        });
        fullMediaList = FXCollections.synchronizedObservableList(FXCollections.observableArrayList(MediaFile.extractor()));
        filteredMediaList = new FilteredList<>(fullMediaList, null);
        filteredMediaList.setPredicate(standardFilter().and(filterDeleted(showDeletedButton.isSelected())));
        sortedMediaList = new SortedList<>(filteredMediaList);
        sortedMediaList.setComparator(new MediaFilenameComparator());        
        imageGrid = new GridView<>(sortedMediaList);
        imageGrid.setCache(true);
        imageGrid.setCacheShape(true);
        imageGrid.setCacheHint(CacheHint.SPEED);
        double defaultCellWidth = imageGrid.getCellWidth();
        double defaultCellHight = imageGrid.getCellHeight();
        factory = new MediaGridCellFactory(this, imageGrid, util, metadataController);

        Platform.runLater(() -> {
            if (imageGridPane.getChildren().indexOf(imageGrid) == -1) {
                imageGridPane.getChildren().add(imageGrid);
            }
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbarLabel().textProperty().unbind();
        });

        taskMLoading = new MediaLoadingTask(fullMediaList, factory, sPath, mainController, mediaQTYLabel, sortOrderComboBox.getSelectionModel().getSelectedItem(), metadataController);
        taskMLoading.setOnSucceeded((WorkerStateEvent t) -> {
            filteredMediaList.setPredicate(standardFilter().and(filterDeleted(showDeletedButton.isSelected())));
            //sort if needed
            sortedMediaList.setComparator(new MediaFilenameComparator()); 
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getStatusLabelRight().textProperty().unbind();
            util.hideNodeAfterTime(mainController.getStatusLabelRight(), 2, true);
            sortOrderComboBox.setDisable(false);
            mainController.getStatusLabelRight().setText("Finished MediaLoading Task.");
            util.hideNodeAfterTime(mainController.getStatusLabelRight(), 2, true);
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setText("");
            //serialize objects to disks
            System.out.println("save cache...");
            //EmbeddedStorageManager storageManager = EmbeddedStorage.start(Paths.get(Utility.getAppData()));
            //storageManager.store(fullMediaList.get(0));
            System.out.println("save cache...done");
            /*try {
                FileOutputStream fos = new FileOutputStream(Utility.getAppData() + "Objectsavefile.ser");
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(new ArrayList<MediaFile>(fullMediaList));
                oos.close();

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }*/

            // end serialize
        });
        taskMLoading.setOnFailed((t2) -> {
            Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, t2.getSource().getException());
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setVisible(false);
        });
        Platform.runLater(() -> {
            mainController.getStatusLabelRight().textProperty().bind(taskMLoading.messageProperty());
        });
        executorSchedule.schedule(taskMLoading, 50, TimeUnit.MILLISECONDS);
        this.mainController.getTaskProgressView().getTasks().add(taskMLoading);

        imageGrid.setCellFactory(factory);
        Platform.runLater(() -> {
            imageGrid.requestFocus();
        });
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
        Platform.runLater(() -> {
            if (!imageGridPane.getChildren().isEmpty()) {
                imageGridPane.getChildren().remove(0);
            }
        });
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
        imageView.fitWidthProperty().unbind();
        imageView.fitHeightProperty().unbind();
        double rotate = imageView.getRotate();
        int angle = (int) rotate - 90;
        if (angle < -360) {
            angle = -90;
        }
        MediaFile item = fullMediaList.get(fullMediaList.indexOf(factory.getSelectedCell().getItem()));
        //imageView.setRotate(angle);
        item.setRotationAngle(angle);
        switch (angle) {
            case 0:
                imageView.fitWidthProperty().bind(stackPane.widthProperty());
                imageView.fitHeightProperty().bind(stackPane.heightProperty());
                break;
            case 180:
                imageView.fitWidthProperty().bind(stackPane.widthProperty());
                imageView.fitHeightProperty().bind(stackPane.heightProperty());
                break;
            case 360:
                imageView.fitWidthProperty().bind(stackPane.widthProperty());
                imageView.fitHeightProperty().bind(stackPane.heightProperty());
                break;
            case -180:
                imageView.fitWidthProperty().bind(stackPane.widthProperty());
                imageView.fitHeightProperty().bind(stackPane.heightProperty());
                break;
            case -360:
                imageView.fitWidthProperty().bind(stackPane.widthProperty());
                imageView.fitHeightProperty().bind(stackPane.heightProperty());
                break;
            case 90:
                imageView.fitWidthProperty().bind(stackPane.heightProperty());
                imageView.fitHeightProperty().bind(stackPane.widthProperty());
                break;
            case 270:
                imageView.fitWidthProperty().bind(stackPane.heightProperty());
                imageView.fitHeightProperty().bind(stackPane.widthProperty());
                break;
            case -90:
                imageView.fitWidthProperty().bind(stackPane.heightProperty());
                imageView.fitHeightProperty().bind(stackPane.widthProperty());
                break;
            case -270:
                imageView.fitWidthProperty().bind(stackPane.heightProperty());
                imageView.fitHeightProperty().bind(stackPane.widthProperty());
                break;
        }
        savingMediaFileEdits(factory.getSelectedCell().getItem());
    }

    @FXML
    private void rotateRightButtonAction(ActionEvent event) {
        rotateRightAction();
    }

    public void rotateRightAction() {
        imageView.fitWidthProperty().unbind();
        imageView.fitHeightProperty().unbind();
        double rotate = imageView.getRotate();
        int angle = (int) rotate + 90;
        if (angle > 360) {
            angle = 90;
        }
        MediaFile item = fullMediaList.get(fullMediaList.indexOf(factory.getSelectedCell().getItem()));
        //imageView.setRotate(angle);
        item.setRotationAngle(angle);
        switch (angle) {
            case 0:
                imageView.fitWidthProperty().bind(stackPane.widthProperty());
                imageView.fitHeightProperty().bind(stackPane.heightProperty());
                break;
            case 180:
                imageView.fitWidthProperty().bind(stackPane.widthProperty());
                imageView.fitHeightProperty().bind(stackPane.heightProperty());
                break;
            case 360:
                imageView.fitWidthProperty().bind(stackPane.widthProperty());
                imageView.fitHeightProperty().bind(stackPane.heightProperty());
                break;
            case -180:
                imageView.fitWidthProperty().bind(stackPane.widthProperty());
                imageView.fitHeightProperty().bind(stackPane.heightProperty());
                break;
            case -360:
                imageView.fitWidthProperty().bind(stackPane.widthProperty());
                imageView.fitHeightProperty().bind(stackPane.heightProperty());
                break;
            case 90:
                imageView.fitWidthProperty().bind(stackPane.heightProperty());
                imageView.fitHeightProperty().bind(stackPane.widthProperty());
                break;
            case 270:
                imageView.fitWidthProperty().bind(stackPane.heightProperty());
                imageView.fitHeightProperty().bind(stackPane.widthProperty());
                break;
            case -90:
                imageView.fitWidthProperty().bind(stackPane.heightProperty());
                imageView.fitHeightProperty().bind(stackPane.widthProperty());
                break;
            case -270:
                imageView.fitWidthProperty().bind(stackPane.heightProperty());
                imageView.fitHeightProperty().bind(stackPane.widthProperty());
                break;
        }
        savingMediaFileEdits(factory.getSelectedCell().getItem());
    }

    @FXML
    private void rateButtonAction(ActionEvent event) {
        rateAction();
    }

    public void rateAction() {
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
            savingMediaFileEdits(factory.getSelectedCell().getItem());
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
        item.setDeleted(true);
        imageView.setImage(null);
        getTitleLabel().setVisible(false);
        getCameraLabel().setVisible(false);
        getFilenameLabel().setVisible(false);
        getRatingControl().setVisible(false);
        savingMediaFileEdits(item);
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
            selectNextImageInGrid(sIdx);
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

    public void cropAction() {
        if (snapshotView != null) {
            if (snapshotView.isSelectionActive()) {
                snapshotView.setSelectionActive(false);
                snapshotView.setSelection(Rectangle2D.EMPTY);
                snapshotView.setNode(null);
                imageStackPane.getChildren().clear();
                imageStackPane.getChildren().add(imageView);
                imageView.requestFocus();
                snapshotView = null;
            }
        } else {
            infoPane.setDisable(true);
            optionPane.setDisable(true);
            snapshotView = new SnapshotView(imageView);
            //snapshotView.setSelectionAreaBoundary(SnapshotView.Boundary.NODE);

            snapshotView.setSelectionActive(false);
            imageStackPane.getChildren().add(snapshotView);
            snapshotView.setOnKeyPressed((t) -> {
                if (KeyCode.ESCAPE == t.getCode()) {
                    snapshotView.setSelection(Rectangle2D.EMPTY);
                    snapshotView.setNode(null);
                    imageStackPane.getChildren().clear();
                    imageStackPane.getChildren().add(imageView);
                    imageView.requestFocus();
                    infoPane.setDisable(false);
                    optionPane.setDisable(false);
                    snapshotView.setSelectionRatioFixed(false);
                    snapshotView.setFixedSelectionRatio(1);
                    snapshotView.setSelectionActive(false);
                    snapshotView = null;
                }
                if (KeyCode.ENTER == t.getCode()) {
                    Rectangle2D selection = snapshotView.getSelection();
                    Point2D imgSize = new Point2D(imageView.getImage().getWidth(), imageView.getImage().getHeight());
                    factory.getSelectedCell().getItem().setOrignalImageSize(imgSize);
                    imageView.setViewport(new Rectangle2D(0, 0, imageView.getImage().getWidth(), imageView.getImage().getHeight()));
                    Point2D imageViewToImageUp = util.imageViewToImage(imageView, new Point2D(selection.getMinX(), selection.getMinY()));
                    Point2D imageViewToImageLow = util.imageViewToImage(imageView, new Point2D(selection.getMaxX(), selection.getMaxY()));
                    double width = imageViewToImageLow.getX() - imageViewToImageUp.getX();
                    double hight = imageViewToImageLow.getY() - imageViewToImageUp.getY();
                    Rectangle2D cropView = new Rectangle2D(imageViewToImageUp.getX(), imageViewToImageUp.getY(), width, hight);
                    imageView.setViewport(cropView);
                    snapshotView.setSelectionActive(false);
                    snapshotView.setSelection(Rectangle2D.EMPTY);
                    snapshotView.setNode(null);
                    imageStackPane.getChildren().clear();
                    imageStackPane.getChildren().add(imageView);
                    factory.getSelectedCell().getItem().setCropView(cropView);
                    infoPane.setDisable(false);
                    optionPane.setDisable(false);
                    snapshotView.setSelectionRatioFixed(false);
                    snapshotView = null;
                    savingMediaFileEdits(factory.getSelectedCell().getItem());
                }
                t.consume();
            });
        }

        if (snapshotView != null) {
            double ratio = 1;
            snapshotView.setSelectionRatioFixed(false);
            ratio = (double) imageView.getImage().getWidth() / imageView.getImage().getHeight();
            snapshotView.requestFocus();
            final double ratioF = ratio;
            snapshotView.setFixedSelectionRatio(ratioF);
            snapshotView.setSelectionRatioFixed(true);
            PauseTransition pause = new PauseTransition(Duration.millis(100));
            pause.setOnFinished((t) -> {
                if (factory.getSelectedCell().getItem().getCropView() == null) {
                    Rectangle2D rect = new Rectangle2D(20, 20, 100 * ratioF, 100);
                    snapshotView.setSelection(rect);
                } else {
                    Rectangle2D viewport = imageView.getViewport();
                    imageView.setViewport(null);
                }
            });
            pause.play();
        }
    }

    public SnapshotView getSnapshotView() {
        return snapshotView;
    }

    public void setSnapshotView(SnapshotView snapshotView) {
        this.snapshotView = snapshotView;
    }

    public HBox getImageStackPane() {
        return imageStackPane;
    }

    public ImageView getImageView() {
        return imageView;
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
                        int i = 0;
                        for (MediaFile mediaFile : fullMediaList) {
                            if (this.isCancelled() == false) {
                                updateProgress(i + 1, fullMediaList.size());
                                updateMessage("Read record time " + (i + 1) + "/" + fullMediaList.size());
                                try {
                                    if (mediaFile.getRecordTime() == null) {
                                        metadataController.setActualMediaFile(mediaFile);
                                        metadataController.readBasicMetadata(this, mediaFile);
                                    }
                                } catch (IOException e) {
                                    long test_timestamp = mediaFile.getPathStorage().toFile().lastModified();
                                    LocalDateTime triggerTime
                                            = LocalDateTime.ofInstant(Instant.ofEpochMilli(test_timestamp), TimeZone.getDefault().toZoneId());
                                    mediaFile.setRecordTime(triggerTime);
                                }
                            }
                            i++;
                        }
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
                });
                taskMeta.setOnFailed((t) -> {
                    mainController.getProgressbar().progressProperty().unbind();
                    mainController.getProgressbarLabel().textProperty().unbind();
                });
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressbar().progressProperty().bind(taskMeta.progressProperty());
                mainController.getProgressbarLabel().textProperty().unbind();
                mainController.getProgressbarLabel().textProperty().bind(taskMeta.messageProperty());
                executor.submit(taskMeta);
                mainController.getTaskProgressView().getTasks().add(taskMeta);
            }
        }
        if (sortOrderComboBox.getSelectionModel().getSelectedItem().equalsIgnoreCase("Filename")) {
            if (taskMLoading.isRunning() == true) {
                taskMLoading.cancel();
                setSelectedPath(selectedPath);
            } else {
                sortedMediaList.setComparator(new MediaFilenameComparator());
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
        Platform.runLater(() -> {
            mainController.getStatusLabelLeft().setVisible(true);
            mainController.getStatusLabelLeft().setText("Copying to clipboard...");
        });
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
        Platform.runLater(() -> {
            pasteButton.setDisable(false);
            mainController.getStatusLabelLeft().setText("Copying to clipboard...Done!");
            util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 3, true);
        });
    }

    public void pastAction() {
        Platform.runLater(() -> {
            mainController.getProgressPane().setVisible(true);
            mainController.getStatusLabelLeft().setVisible(true);
            mainController.getStatusLabelLeft().setText("Pasting...");
        });
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
            Platform.runLater(() -> {
                mainController.getProgressbarLabel().textProperty().unbind();
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressPane().setVisible(false);
                mainController.getStatusLabelLeft().setVisible(false);
            });
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
        return baseFilter().or(mFile -> mFile.getStackPos() == 1);
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
