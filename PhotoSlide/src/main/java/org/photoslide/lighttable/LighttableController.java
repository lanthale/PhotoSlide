/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.lighttable;

import org.photoslide.datamodel.MediaFile;
import org.photoslide.MainViewController;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.Utility;
import org.photoslide.metadata.MetadataController;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
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
import org.kordamp.ikonli.javafx.FontIcon;

/**
 *
 * @author selfemp
 */
public class LighttableController implements Initializable {

    private MainViewController mainController;
    private Path selectedPath;
    private ExecutorService executor;
    private ExecutorService executorParallel;
    private Task<Boolean> task;
    private Task<List<MediaFile>> taskEmtpy;
    private ObservableList<MediaFile> list;
    private List<MediaFile> deletedList;
    private final KeyCombination keyCombinationMetaC = new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);

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
    private final KeyCombination keyMetaA = new KeyCodeCombination(KeyCode.A, KeyCombination.META_DOWN);
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
    private ObservableList<String> sortOptions;
    private GridView<MediaFile> imageGrid;
    @FXML
    private Button copyButton;
    @FXML
    private Button pasteButton;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        util = new Utility();
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
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("lightTableController"));
        executorParallel = Executors.newSingleThreadExecutor(new ThreadFactoryPS("lightTableControllerSelection"));
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
        imageGridPane.getChildren().clear();
        if (taskEmtpy != null) {
            taskEmtpy.cancel();
        }
        if (task != null) {
            task.cancel();
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
        metadataController.resetGUI();
        mainController.getStatusLabelRight().setVisible(true);
        selectedPath = sPath;
        Platform.runLater(() -> {
            detailToolbar.setDisable(true);
            mainController.handleMenuDisable(true);
            pasteButton.setDisable(!Clipboard.getSystemClipboard().hasFiles());
            //sortOrderComboBox.setDisable(true);
            mainController.getStatusLabelLeft().setVisible(true);
            mainController.getStatusLabelLeft().setText("Scanning...");
            mainController.getProgressPane().setVisible(true);
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbar().setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressbarLabel().setText("Retrieving files...");
            mainController.getStatusLabelRight().textProperty().unbind();
            mainController.getStatusLabelRight().setText("Retrieve images for " + selectedPath.toString() + "...");
        });
        list = FXCollections.observableArrayList();
        deletedList = new ArrayList<>();
        imageGrid = new GridView<>(list);
        double defaultCellWidth = imageGrid.getCellWidth();
        double defaultCellHight = imageGrid.getCellHeight();
        factory = new MediaGridCellFactory(executorParallel, this, imageGrid, util, metadataController);
        imageGrid.setCellFactory(factory);
        imageGrid.requestFocus();
        imageGrid.setOnKeyPressed((t) -> {
            if (keyCombinationMetaC.match(t)) {
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                final ClipboardContent content = new ClipboardContent();

                List<File> fileList = new ArrayList<>();
                Set<Node> selection = factory.getSelectionModel().getSelection();
                selection.forEach((k) -> {
                    fileList.add(new File(((MediaFile) k).getName()));
                });
                content.putFiles(fileList);
                clipboard.setContent(content);
            }
            if (keyMetaA.match(t)) {
                list.forEach((mediafile) -> {
                    factory.getSelectionModel().add(mediafile);
                    mediafile.requestLayout();
                });
            }
            if (KeyCode.RIGHT == t.getCode()) {
                selectNextImageInGrid();
            }
            if (KeyCode.LEFT == t.getCode()) {
                selectPreviousImageInGrid();
            }
            t.consume();
        });
        imageGrid.setOnDragDetected((t) -> {
            /* drag was detected, start a drag-and-drop gesture*/
            Dragboard db = imageGrid.startDragAndDrop(TransferMode.ANY);
            final ClipboardContent content = new ClipboardContent();
            List<File> fileList = new ArrayList<>();
            Set<Node> selection = factory.getSelectionModel().getSelection();
            /*selection.forEach((k) -> {                
                fileList.add(new File(((MediaGridCell) k).getItem().getName()));                
            });*/
            fileList.add(new File(((MediaFile) selection.iterator().next()).getName()));
            content.putFiles(fileList);
            db.setContent(content);
            t.consume();
        });
        zoomSlider.valueProperty().addListener((ObservableValue<? extends Number> ov, Number t, Number t1) -> {
            imageGrid.setCellWidth(defaultCellWidth + 3 * zoomSlider.getValue());
            imageGrid.setCellHeight(defaultCellHight + 3 * zoomSlider.getValue());
            //factory.getSelectedCell().requestFocus();
        });
        Platform.runLater(() -> {
            imageGridPane.getChildren().add(imageGrid);
            mainController.getProgressbar().setProgress(0);
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbar().progressProperty().bind(taskEmtpy.progressProperty());
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressbarLabel().textProperty().bind(taskEmtpy.messageProperty());
        });
        taskEmtpy = new EmptyMediaLoadingTask(sPath, mainController, mediaQTYLabel, sortOrderComboBox.getSelectionModel().getSelectedItem(), metadataController);
        taskEmtpy.setOnSucceeded((WorkerStateEvent t) -> {
            list.addAll((List<MediaFile>) t.getSource().getValue());
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getStatusLabelRight().textProperty().unbind();
            mainController.getStatusLabelRight().setText("Finished directory - found " + ((List<MediaFile>) t.getSource().getValue()).size());
            util.hideNodeAfterTime(mainController.getStatusLabelRight(), 2);
        });
        taskEmtpy.setOnFailed((t2) -> {
            Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, t2.getSource().getException());
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setVisible(false);
        });
        task = new MediaLoadingTask(list, mainController, imageGrid);
        task.setOnSucceeded((WorkerStateEvent t) -> {
            sortOrderComboBox.setDisable(false);
            mainController.getStatusLabelRight().textProperty().unbind();
            mainController.getStatusLabelRight().setText("Finished Image Task.");
            util.hideNodeAfterTime(mainController.getStatusLabelRight(), 2);
        });
        task.setOnFailed((t2) -> {
            Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, t2.getSource().getException());
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setVisible(false);
        });
        mainController.getStatusLabelRight().textProperty().bind(taskEmtpy.messageProperty());
        executor.submit(taskEmtpy);
        mainController.getStatusLabelRight().textProperty().bind(task.messageProperty());
        executor.submit(task);
    }

    public void selectPreviousImageInGrid() {
        MediaGridCell selectedCell = factory.getSelectedCell();
        int actIndex = list.indexOf(selectedCell.getItem());
        actIndex = actIndex - 1;
        if (actIndex >= 0) {
            MediaGridCell nextCell = factory.getMediaCellForMediaFile(list.get(actIndex));
            if (nextCell != null) {
                nextCell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0,
                        0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false,
                        false, false, false, false, false, true, null));
                nextCell.requestLayout();
            }
        }
    }

    public void selectNextImageInGrid() {
        MediaGridCell selectedCell = factory.getSelectedCell();
        int actIndex = list.indexOf(selectedCell.getItem());
        actIndex = actIndex + 1;
        if (actIndex < list.size()) {
            MediaGridCell nextCell = factory.getMediaCellForMediaFile(list.get(actIndex));
            if (nextCell != null) {
                nextCell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0,
                        0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false,
                        false, false, false, false, false, true, null));
                nextCell.requestLayout();
            }
        }
    }

    public void Shutdown() {
        Platform.runLater(() -> {
            if (!imageGridPane.getChildren().isEmpty()) {
                imageGridPane.getChildren().remove(0);
            }
        });
        if (task != null) {
            task.cancel();
        }
        if (taskEmtpy != null) {
            taskEmtpy.cancel();
        }
        if (executorParallel != null) {
            executorParallel.shutdownNow();
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
        MediaFile item = list.get(list.indexOf(factory.getSelectedCell().getItem()));
        //imageView.setRotate(angle);
        item.setRotationAngle(angle);
        switch (angle) {
            case 0,180,360,-180,-360 -> {
                imageView.fitWidthProperty().bind(stackPane.widthProperty());
                imageView.fitHeightProperty().bind(stackPane.heightProperty());
            }
            case 90,270,-90,-270 -> {
                imageView.fitWidthProperty().bind(stackPane.heightProperty());
                imageView.fitHeightProperty().bind(stackPane.widthProperty());
            }
        }
        factory.getSelectedCell().requestLayout();
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
        MediaFile item = list.get(list.indexOf(factory.getSelectedCell().getItem()));
        //imageView.setRotate(angle);
        item.setRotationAngle(angle);
        switch (angle) {
            case 0,180,360,-180,-360 -> {
                imageView.fitWidthProperty().bind(stackPane.widthProperty());
                imageView.fitHeightProperty().bind(stackPane.heightProperty());
            }
            case 90,270,-90,-270 -> {
                imageView.fitWidthProperty().bind(stackPane.heightProperty());
                imageView.fitHeightProperty().bind(stackPane.widthProperty());
            }
        }
        factory.getSelectedCell().requestLayout();
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
            factory.getSelectedCell().requestLayout();
        });
        popOver.showingProperty().addListener((ov, t, t1) -> {
            if (t1 == false) {
                factory.getSelectedCell().requestLayout();
            }
        });
        rate.ratingProperty().bindBidirectional(factory.getSelectedCell().getItem().getRatingProperty());
        popOver.show(rateButton);
    }

    @FXML
    private void deleteButtonAction(ActionEvent event) {
        deleteAction();
    }

    public void deleteAction() {
        /*Alert alert = new Alert(AlertType.CONFIRMATION, "Mark MediaFile " + factory.getSelectedCell().getItem().getName() + " \n as deleted ?", ButtonType.CANCEL, ButtonType.YES, ButtonType.NO);
        
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(
        getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
        alert.showAndWait();
        if (alert.getResult() == ButtonType.YES) {*/
        //new File(factory.getSelectedCell().getItem().getName()).delete();
        MediaFile item = list.get(list.indexOf(factory.getSelectedCell().getItem()));
        item.setDeleted(true);
        list.set(list.indexOf(item), item);
        deletedList.add(item);
        imageView.setImage(null);
        getTitleLabel().setVisible(false);
        getCameraLabel().setVisible(false);
        getFilenameLabel().setVisible(false);
        getRatingControl().setVisible(false);
        /*} else {
        alert.hide();
        }
        factory.getSelectedCell().requestLayout();*/
    }

    @FXML
    private void stackButtonAction(ActionEvent event) {
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
            if (factory.getSelectedCell().getItem().getCropView() == null) {
                snapshotView.setSelection(Rectangle2D.EMPTY);
            } else {
                //recalc selection view from image pixels
                snapshotView.setSelection(factory.getSelectedCell().getItem().getCropView());
            }
            snapshotView.setSelectionActive(false);
            imageStackPane.getChildren().add(snapshotView);
            snapshotView.setOnKeyPressed((t) -> {
                if (KeyCode.ESCAPE == t.getCode()) {
                    snapshotView.setSelectionActive(false);
                    snapshotView.setSelection(Rectangle2D.EMPTY);
                    snapshotView.setNode(null);
                    imageStackPane.getChildren().clear();
                    imageStackPane.getChildren().add(imageView);
                    imageView.requestFocus();
                    infoPane.setDisable(false);
                    optionPane.setDisable(false);
                    snapshotView = null;
                }
                if (KeyCode.ENTER == t.getCode()) {
                    Rectangle2D selection = snapshotView.getSelection();
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
                    snapshotView = null;
                }
                t.consume();
            });
        }

        if (snapshotView != null) {
            double ratio = 1;
            snapshotView.setSelectionRatioFixed(true);
            ratio = imageView.getImage().getWidth() / imageView.getImage().getHeight();
            final double ratioF = ratio;
            Platform.runLater(() -> {
                snapshotView.setFixedSelectionRatio(ratioF);
                try {
                    snapshotView.setSelection(new Rectangle2D(38.5, 46.5, 100.0 * ratioF, 100.0));
                } catch (IllegalArgumentException e) {
                    Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, e.getMessage(), e);
                    infoPane.setDisable(false);
                    optionPane.setDisable(false);
                }
                snapshotView.requestFocus();
            });
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

    public ObservableList<MediaFile> getList() {
        return list;
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
            if (task.isRunning() == true) {
                task.cancel();
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
                        for (MediaFile mediaFile : list) {
                            if (this.isCancelled() == false) {
                                updateProgress(i + 1, list.size());
                                updateMessage("Sort " + (i + 1) + "/" + list.size());
                                try {
                                    if (mediaFile.getRecordTime() == null) {
                                        metadataController.setActualMediaFile(mediaFile);
                                        metadataController.readBasicMetadata(this);
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
                        list.sort(Comparator.comparing(MediaFile::getRecordTime));
                    } catch (NullPointerException e) {
                        sortOrderComboBox.getSelectionModel().clearSelection(0);
                    }
                    mainController.getProgressPane().setVisible(false);
                    mainController.getStatusLabelLeft().setText("");
                });
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressbar().progressProperty().bind(taskMeta.progressProperty());
                mainController.getProgressbarLabel().textProperty().unbind();
                mainController.getProgressbarLabel().textProperty().bind(taskMeta.messageProperty());
                executor.submit(taskMeta);
            }
        }
        if (sortOrderComboBox.getSelectionModel().getSelectedItem().equalsIgnoreCase("Filename")) {
            if (task.isRunning() == true) {
                task.cancel();
                setSelectedPath(selectedPath);
            } else {
                list.sort(Comparator.comparing(MediaFile::getName));
            }
        }
        if (sortOrderComboBox.getSelectionModel().getSelectedItem().equalsIgnoreCase("File creation time")) {
            if (task.isRunning() == true) {
                task.cancel();
                setSelectedPath(selectedPath);
            } else {
                list.sort(Comparator.comparing(MediaFile::getCreationTime));
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
        Alert confirmDiaglog = new Alert(Alert.AlertType.CONFIRMATION, "", ButtonType.NO ,ButtonType.YES);
        confirmDiaglog.setHeaderText("Do you want to transfer all media edits as well ?");
        
        confirmDiaglog.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
        Optional<ButtonType> result = confirmDiaglog.showAndWait();        
        if (result.get() == ButtonType.YES) {
            list.stream().filter(c -> c.isSelected() == true).forEach((mfile) -> {
                filesForClipboard.add(mfile.getPathStorage().toFile());
                filesForClipboard.add(mfile.getEditFilePath().toFile());
            });
        } else {
            list.stream().filter(c -> c.isSelected() == true).forEach((mfile) -> {
                filesForClipboard.add(mfile.getPathStorage().toFile());                
            });
        }
        content.putFiles(filesForClipboard);
        clipboard.setContent(content);
        Platform.runLater(() -> {
            pasteButton.setDisable(false);
            mainController.getStatusLabelLeft().setText("Copying to clipboard...Done!");
            util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 3);
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
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setVisible(false);
            mainController.getStatusLabelLeft().setText("Pasting...Done!");
            util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 3);
            setSelectedPath(selectedPath);
        });
        if (clipboard.hasFiles()) {
            executorParallel.submit(pasteTask);
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
    

}
