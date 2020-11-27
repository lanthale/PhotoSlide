/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.lighttable;

import org.photoslide.datamodel.GridCellSelectionModel;
import org.photoslide.datamodel.MediaGridCell;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.Utility;
import org.photoslide.metadata.MetadataController;
import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;
import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;
import org.controlsfx.control.PopOver;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 *
 * @author selfemp
 */
public class MediaGridCellFactory implements Callback<GridView<MediaFile>, GridCell<MediaFile>> {

    private Image img;

    private final Utility util;
    private final MetadataController metadataController;
    private final GridCellSelectionModel selectionModel;
    private final GridView<MediaFile> grid;
    private final Set<MediaGridCell> mediaCells;
    private Task<Boolean> task;
    private final ExecutorService executor;
    private MediaGridCell selectedCell;
    private final LighttableController lightController;
    private MediaFile selectedMediaItem;
    private final List<Task> taskList;
    private final AtomicInteger xMouse;
    private final AtomicInteger yMouse;
    private final Image dialogIcon;

    public MediaGridCellFactory(ExecutorService executor, LighttableController lightController, GridView<MediaFile> grid, Utility util, MetadataController metadataController) {
        this.util = util;
        taskList = new ArrayList<>();
        this.metadataController = metadataController;
        this.grid = grid;
        this.lightController = lightController;
        this.mediaCells = new HashSet<>();
        selectionModel = new GridCellSelectionModel();
        //new RubberBandSelection(grid, selectionModel);
        this.executor = executor;
        lightController.getOptionPane().setOnSwipeLeft((t) -> {
            lightController.selectNextImageInGrid();
        });
        lightController.getOptionPane().setOnSwipeRight((t) -> {
            lightController.selectPreviousImageInGrid();
        });
        lightController.getOptionPane().setOnZoom((z) -> {
            if (lightController.getImageView().getViewport() == null) {
                lightController.getImageView().setViewport(new Rectangle2D(0, 0, lightController.getImageView().getImage().getWidth(), lightController.getImageView().getImage().getHeight()));
            }
            double ratio = (double) lightController.getImageView().getImage().getHeight() / lightController.getImageView().getImage().getWidth();
            if (z.getZoomFactor() > 1) {
                handleZoomIn(ratio);
            } else {
                handleZoomOut(ratio);
            }
            z.consume();
        });
        xMouse = new AtomicInteger(0);
        yMouse = new AtomicInteger(0);
        lightController.getOptionPane().setOnMouseDragged((t) -> {
            if (lightController.getImageView().getViewport() == null) {
                lightController.getImageView().setViewport(new Rectangle2D(0, 0, lightController.getImageView().getImage().getWidth(), lightController.getImageView().getImage().getHeight()));
            }
            Rectangle2D viewport = lightController.getImageView().getViewport();
            double x = viewport.getMinX();
            double y = viewport.getMinY();
            double width = viewport.getWidth();
            double height = viewport.getHeight();
            if (t.getX() < xMouse.get()) {
                x = viewport.getMinX() + 10;
            } else if (t.getX() > xMouse.get()) {
                x = viewport.getMinX() - 10;
            } else if (t.getY() < yMouse.get()) {
                y = viewport.getMinY() + 10;
            } else if (t.getY() > yMouse.get()) {
                y = viewport.getMinY() - 10;
            }
            if (x > 0 || y > 0) {
                Rectangle2D cropView = new Rectangle2D(x, y, width, height);
                lightController.getImageView().setViewport(cropView);
                xMouse.set((int) t.getX());
                yMouse.set((int) t.getY());
            }
        });
        dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
    }

    @Override
    public GridCell<MediaFile> call(GridView<MediaFile> p) {
        MediaGridCell cell = new MediaGridCell();
        mediaCells.add(cell);
        cell.setAlignment(Pos.CENTER);
        cell.setEditable(false);
        cell.setOnMouseClicked((t) -> {
            manageGUISelection(t, cell);
            handleGridCellSelection(t);
            //cell.requestFocus();
            t.consume();
        });
        return cell;
    }

    private void manageGUISelection(MouseEvent t, MediaGridCell cell) {
        if (t.isShiftDown()) {
            //select all nodes in between
            int indexOfStart = lightController.getFullMediaList().indexOf(((MediaFile) selectionModel.getSelection().iterator().next()));
            int indexOfEnd = lightController.getFullMediaList().indexOf(cell.getItem());
            if (indexOfStart < indexOfEnd) {
                for (int i = indexOfStart; i <= indexOfEnd; i++) {
                    selectionModel.add(lightController.getFullMediaList().get(i));
                }
            } else {
                for (int i = indexOfEnd; i <= indexOfStart; i++) {
                    selectionModel.add(lightController.getFullMediaList().get(i));
                }
            }
        } else {
            if (t.isShortcutDown()) {
                if (selectionModel.contains(cell)) {
                    selectionModel.remove(((MediaGridCell) t.getSource()).getItem());
                } else {
                    selectionModel.add(((MediaGridCell) t.getSource()).getItem());
                }
            } else {
                lightController.getFullMediaList().stream().filter(c -> c != null && c.isSelected() == true).forEach((mfile) -> {
                    mfile.setSelected(false);
                });
                selectionModel.clear();
                selectionModel.add(((MediaGridCell) t.getSource()).getItem());
            }
        }
        cell.requestLayout();
    }

    private void handleGridCellSelection(Event t) {
        if (t.getTarget().getClass().equals(MediaFile.class)) {
            Node target = Utility.pick((MediaFile) t.getTarget(), ((MouseEvent) t).getSceneX(), ((MouseEvent) t).getSceneY());
            if (target.getClass().equals(FontIcon.class)) {
                handleStackButtonAction(t, ((MediaFile) t.getTarget()).getStackName(), (MediaGridCell) t.getSource());
                if (lightController.getImageView().getImage() != null) {
                    if (lightController.getImageView().getImage().getUrl().equalsIgnoreCase(((MediaGridCell) t.getSource()).getItem().getImage().getUrl())) {
                        return;
                    }
                }
            }
        }
        if (((MediaGridCell) t.getSource()).getItem().isStacked()) {
            lightController.getStackButton().setText("Unstack");
        } else {
            lightController.getStackButton().setText("Stack");
        }
        setStdGUIState();

        if (selectionModel.selectionCount() > 1) {
            lightController.getPlayIcon().setVisible(false);
            lightController.getImageView().setImage(null);
            lightController.getMediaView().setMediaPlayer(null);
            lightController.getTitleLabel().setVisible(false);
            lightController.getCameraLabel().setVisible(false);
            lightController.getFilenameLabel().setVisible(false);
            lightController.getRatingControl().setVisible(false);
            return;
        }
        if (task != null) {
            cancleTask();
            lightController.getPlayIcon().setVisible(false);
            lightController.getImageView().setImage(null);
            lightController.getImageView().setViewport(null);
            lightController.getMediaView().setMediaPlayer(null);
            lightController.getTitleLabel().setVisible(false);
            lightController.getCameraLabel().setVisible(false);
            lightController.getFilenameLabel().setVisible(false);
            lightController.getRatingControl().setVisible(false);
            lightController.getOptionPane().setDisable(false);
            metadataController.cancelTasks();
            if (img != null) {
                img.cancel();
            }
        }
        if (selectedMediaItem != null) {
            if (selectedMediaItem.isMediaEdited() == true) {
                executor.submit(() -> {
                    selectedMediaItem.saveEdits();
                });
            }
        }
        selectedMediaItem = ((MediaGridCell) t.getSource()).getItem();
        if (selectedMediaItem.isDeleted() == true) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Restore MediaFile " + selectedMediaItem.getName() + " ?", ButtonType.CANCEL, ButtonType.YES, ButtonType.NO);

            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().add(
                    getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(dialogIcon);
            Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) grid.getScene().getWindow());
            alert.showAndWait();
            if (alert.getResult() == ButtonType.YES) {
                selectedMediaItem.setDeleted(false);
                grid.getItems().set(grid.getItems().indexOf(selectedMediaItem), selectedMediaItem);
            } else {
                alert.hide();
                return;
            }
        }
        selectedCell = (MediaGridCell) t.getSource();

        task = new Task<>() {
            @Override
            protected Boolean call() throws MalformedURLException {

                Platform.runLater(() -> {
                    lightController.getPlayIcon().setVisible(false);
                    lightController.getImageView().setImage(null);
                    lightController.getMediaView().setMediaPlayer(null);
                });
                switch (selectedMediaItem.getMediaType()) {
                    case VIDEO -> {
                        try {
                            Media media = new Media(selectedMediaItem.getPathStorage().toUri().toURL().toExternalForm());
                            Platform.runLater(() -> {
                                lightController.getImageView().setVisible(false);
                                lightController.getMediaView().setVisible(true);
                                lightController.getImageProgress().setVisible(false);
                                lightController.getInvalidStackPane().setVisible(false);
                                lightController.getPlayIcon().setVisible(true);
                                lightController.getMediaView().toFront();
                                lightController.getPlayIcon().toFront();
                                lightController.getMediaView().requestFocus();
                            });
                            MediaPlayer mp = new MediaPlayer(media);
                            if (lightController.getMediaView().getMediaPlayer() != null) {
                                lightController.getMediaView().getMediaPlayer().stop();
                            }
                            lightController.getMediaView().setMediaPlayer(mp);
                            lightController.getMediaView().setOnMouseMoved((t) -> {
                                Platform.runLater(() -> {
                                    lightController.getPlayIcon().setVisible(true);
                                });
                                if (lightController.getMediaView().getMediaPlayer().getStatus() == MediaPlayer.Status.PLAYING) {
                                    Platform.runLater(() -> {
                                        lightController.getPlayIcon().setVisible(true);
                                    });
                                    lightController.getPlayIcon().setIconLiteral("fa-pause");
                                } else {
                                    lightController.getPlayIcon().setIconLiteral("fa-play");
                                }
                                util.hideNodeAfterTime(lightController.getPlayIcon(), 2);
                            });
                            lightController.getPlayIcon().setOnMouseClicked((t) -> {
                                if (lightController.getMediaView().getMediaPlayer().getStatus() == MediaPlayer.Status.PLAYING) {
                                    lightController.getMediaView().getMediaPlayer().pause();
                                } else {
                                    lightController.getMediaView().getMediaPlayer().play();
                                    lightController.getPlayIcon().setIconLiteral("fa-pause");
                                    util.hideNodeAfterTime(lightController.getPlayIcon(), 1);
                                }
                            });
                            lightController.getMediaView().setOnKeyPressed((t) -> {
                                if (t.getCode() == KeyCode.SPACE) {
                                    if (lightController.getMediaView().getMediaPlayer().getStatus() == MediaPlayer.Status.PLAYING) {
                                        lightController.getMediaView().getMediaPlayer().pause();
                                    } else {
                                        lightController.getMediaView().getMediaPlayer().play();
                                        lightController.getPlayIcon().setIconLiteral("fa-pause");
                                        util.hideNodeAfterTime(lightController.getPlayIcon(), 1);
                                    }
                                }
                            });
                        } catch (MediaException e) {
                            if (e.getType() == MediaException.Type.MEDIA_UNSUPPORTED) {
                                VBox vb = new VBox();
                                vb.setAlignment(Pos.CENTER);
                                FontIcon filmIcon = new FontIcon("fa-file-movie-o:300");
                                filmIcon.setOpacity(0.3);
                                FontIcon controlIcon = new FontIcon("fa-minus-circle:150");
                                Label lb = new Label("Filtype not supported!");
                                lb.setStyle("-fx-font-size: 20px;");
                                Platform.runLater(() -> {
                                    lightController.getPlayIcon().setVisible(false);
                                    lightController.getImageView().setVisible(false);
                                    lightController.getMediaView().setVisible(false);
                                    lightController.getImageProgress().setVisible(false);
                                    vb.getChildren().clear();
                                    vb.getChildren().add(filmIcon);
                                    vb.getChildren().add(lb);
                                    lightController.getInvalidStackPane().getChildren().clear();
                                    lightController.getInvalidStackPane().getChildren().add(vb);
                                    lightController.getInvalidStackPane().getChildren().add(controlIcon);
                                    lightController.getInvalidStackPane().setVisible(true);
                                });
                            }
                        }
                    }

                    case IMAGE -> {
                        metadataController.setSelectedFile(selectedMediaItem);
                        Platform.runLater(() -> {
                            lightController.getImageProgress().progressProperty().unbind();
                            lightController.getImageView().setImage(null);
                            lightController.getInvalidStackPane().setVisible(false);
                            lightController.getImageView().setVisible(true);
                            lightController.getMediaView().setVisible(false);
                            lightController.getImageProgress().setVisible(true);
                        });
                        String url = selectedMediaItem.getImage().getUrl();
                        img = new Image(url, true);
                        Platform.runLater(() -> {
                            lightController.getDetailToolbar().setDisable(false);
                            lightController.getTitleLabel().textProperty().bind(selectedCell.getItem().getTitleProperty());
                            lightController.getCameraLabel().textProperty().bind(selectedCell.getItem().getCameraProperty());
                            lightController.getFilenameLabel().setText(new File(selectedCell.getItem().getName()).getName());
                            lightController.getRatingControl().ratingProperty().bind(selectedCell.getItem().getRatingProperty());
                            lightController.getImageView().rotateProperty().bind(selectedCell.getItem().getRotationAngleProperty());
                            switch (selectedCell.getItem().getRotationAngleProperty().getValue().intValue()) {
                                case 0,180,360,-180,-360 -> {
                                    lightController.getImageView().fitWidthProperty().bind(lightController.getStackPane().widthProperty());
                                    lightController.getImageView().fitHeightProperty().bind(lightController.getStackPane().heightProperty());
                                }
                                case 90,270,-90,-270 -> {
                                    lightController.getImageView().fitWidthProperty().bind(lightController.getStackPane().heightProperty());
                                    lightController.getImageView().fitHeightProperty().bind(lightController.getStackPane().widthProperty());
                                }
                            }
                            lightController.getTitleLabel().setVisible(true);
                            lightController.getCameraLabel().setVisible(true);
                            lightController.getFilenameLabel().setVisible(true);
                            lightController.getRatingControl().setVisible(true);
                            lightController.getImageProgress().progressProperty().bind(img.progressProperty());
                            img.progressProperty().addListener((ov, t, t1) -> {
                                if ((Double) t1 == 1.0 && !img.isError()) {
                                    lightController.getImageProgress().setVisible(false);                                    
                                } else {
                                    lightController.getImageProgress().setVisible(true);
                                }
                            });                            
                            lightController.getImageView().setImage(img);
                            lightController.getImageView().setViewport(selectedCell.getItem().getCropView());

                            Button resetCrop = new Button();
                            FontIcon restoreIcon = new FontIcon("ti-back-right:24");
                            resetCrop.setGraphic(restoreIcon);
                            if (selectedCell.getItem().getCropView() != null) {
                                lightController.getOptionPane().getChildren().clear();
                                lightController.getOptionPane().getChildren().add(resetCrop);
                                resetCrop.setOnAction((t) -> {
                                    lightController.getOptionPane().setDisable(false);
                                    lightController.getInfoPane().setDisable(false);
                                    lightController.getImageView().setViewport(null);
                                    selectedMediaItem.setCropView(null);
                                    lightController.getImageStackPane().getChildren().clear();
                                    lightController.getImageStackPane().getChildren().add(lightController.getImageView());
                                    lightController.getOptionPane().getChildren().clear();
                                    selectedCell.requestFocus();
                                });
                            } else {
                                Platform.runLater(() -> {
                                    resetCrop.setVisible(false);
                                });
                            }
                        });
                    }
                    default -> {
                    }
                }
                return null;
            }
        };
        taskList.add(task);
        executor.submit(task);
    }

    private void handleZoomIn(double ratio) {
        Rectangle2D viewport = lightController.getImageView().getViewport();
        double x = viewport.getMinX() + 9;
        double y = viewport.getMinY() + 9 * ratio;
        double width = viewport.getWidth() - 9;
        double height = viewport.getHeight() - 9 * ratio;
        if (height > 0) {
            Rectangle2D cropView = new Rectangle2D(x, y, width, height);
            lightController.getImageView().setViewport(cropView);
        }
    }

    private void handleZoomOut(double ratio) {
        Rectangle2D viewport = lightController.getImageView().getViewport();
        double x = viewport.getMinX() - 9;
        double y = viewport.getMinY() - 9 * ratio;
        double width = viewport.getWidth() + 9;
        double height = viewport.getHeight() + 9 * ratio;
        if (x > 0) {
            Rectangle2D cropView = new Rectangle2D(x, y, width, height);
            lightController.getImageView().setViewport(cropView);
        }
    }

    public void setStdGUIState() {
        lightController.getMainController().handleMenuDisable(false);
        lightController.getImageView().rotateProperty().unbind();
        lightController.getImageView().setRotate(0);
        if (selectionModel.getSelection().size() > 1) {
            lightController.getDetailToolbar().setDisable(false);
            lightController.getRotateLeftButton().setDisable(true);
            lightController.getRotateRightButton().setDisable(true);
            lightController.getCropButton().setDisable(true);
            lightController.getRateButton().setDisable(true);
            lightController.getDeleteButton().setDisable(true);
            lightController.getCopyButton().setDisable(false);
            lightController.getPasteButton().setDisable(true);
            lightController.getStackButton().setDisable(false);
        } else {
            lightController.getDetailToolbar().setDisable(false);
            lightController.getRotateLeftButton().setDisable(false);
            lightController.getRotateRightButton().setDisable(false);
            lightController.getCropButton().setDisable(false);
            lightController.getRateButton().setDisable(false);
            lightController.getDeleteButton().setDisable(false);
            lightController.getCopyButton().setDisable(false);
            lightController.getPasteButton().setDisable(true);
            lightController.getStackButton().setDisable(false);
        }
        lightController.getTitleLabel().textProperty().unbind();
        lightController.getCameraLabel().textProperty().unbind();
        lightController.getFilenameLabel().setText("");
        lightController.getRatingControl().ratingProperty().unbind();
        lightController.getOptionPane().getChildren().clear();

        if (lightController.getSnapshotView() != null) {
            lightController.getSnapshotView().setSelectionActive(false);
            lightController.getSnapshotView().setSelection(Rectangle2D.EMPTY);
            lightController.getSnapshotView().setNode(null);
            lightController.getImageStackPane().getChildren().clear();
            lightController.getImageStackPane().getChildren().add(lightController.getImageView());
            lightController.setSnapshotView(null);
        } else {
            lightController.getImageStackPane().getChildren().clear();
            lightController.getImageStackPane().getChildren().add(lightController.getImageView());
            lightController.setSnapshotView(null);
        }
    }

    public GridCellSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public Set<MediaGridCell> getMediaCells() {
        return mediaCells;
    }

    public void cancleTask() {
        metadataController.cancelTasks();
        taskList.forEach(taskItem -> {
            taskItem.cancel();
        });
        taskList.clear();
    }

    public MediaGridCell getSelectedCell() {
        return selectedCell;
    }

    public MediaFile getSelectedMediaItem() {
        return selectedMediaItem;
    }

    public MediaGridCell getMediaCellForMediaFile(MediaFile input) {
        for (MediaGridCell mediaCell : mediaCells) {
            if (mediaCell.getItem().getName().equalsIgnoreCase(input.getName())) {
                return mediaCell;
            }
        }
        return null;
    }

    public void handleStackButtonAction(Event m, String stackName, Node anchore) {
        FilteredList<MediaFile> filteredMediaList = lightController.getFullMediaList().filtered(mFile -> mFile.getStackName().equalsIgnoreCase(stackName));
        SortedList<MediaFile> sortedMediaList = new SortedList<>(filteredMediaList);
        Comparator<MediaFile> stackNameComparator = Comparator.comparing(MediaFile::getStackPos);
        sortedMediaList.setComparator(stackNameComparator);
        GridView<MediaFile> imageGrid = new GridView<>(sortedMediaList);
        MediaGridCellStackedFactory factory = new MediaGridCellStackedFactory(executor, lightController, sortedMediaList);
        imageGrid.setCellFactory(factory);
        double defaultCellWidth = imageGrid.getCellWidth();
        double defaultCellHight = imageGrid.getCellHeight();
        imageGrid.setCellWidth(defaultCellWidth + 3 * 20);
        imageGrid.setCellHeight(defaultCellHight + 3 * 20);
        PopOver popOver = new PopOver();
        popOver.setDetachable(false);
        popOver.setAnimated(true);
        VBox vbox = new VBox();
        vbox.setSpacing(5);
        HBox hb = new HBox();
        hb.setSpacing(5);
        hb.setAlignment(Pos.CENTER);
        hb.setPadding(new Insets(5, 0, 0, 0));
        Button setFrontImageButton = new Button();
        FontIcon setFrontImageIcon = new FontIcon("ti-check-box:20");
        setFrontImageButton.setId("toolbutton");
        setFrontImageButton.setTooltip(new Tooltip("To the Top"));
        setFrontImageButton.setGraphic(setFrontImageIcon);
        setFrontImageButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setFrontImageButton.setOnAction((t) -> {
            factory.setOnFrontImageButtonAction(t);
        });
        Button orderOneDownButton = new Button();
        FontIcon orderOneDownIcon = new FontIcon("ti-arrow-down:20");
        orderOneDownButton.setId("toolbutton");
        orderOneDownButton.setTooltip(new Tooltip("One down"));
        orderOneDownButton.setGraphic(orderOneDownIcon);
        orderOneDownButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        orderOneDownButton.setOnAction((t) -> {
            factory.orderOneDownButtonAction(t);
        });
        Button orderOneUpButton = new Button();
        FontIcon orderOneUpIcon = new FontIcon("ti-arrow-up:20");
        orderOneUpButton.setId("toolbutton");
        orderOneUpButton.setTooltip(new Tooltip("One up"));
        orderOneUpButton.setGraphic(orderOneUpIcon);
        orderOneUpButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        orderOneUpButton.setOnAction((t) -> {
            factory.orderOneUpButtonAction(t);
        });
        Button orderToTheButtomButton = new Button();
        FontIcon orderToTheButtomIcon = new FontIcon("ti-angle-double-down:20");
        orderToTheButtomButton.setId("toolbutton");
        orderToTheButtomButton.setTooltip(new Tooltip("To the buttom"));
        orderToTheButtomButton.setGraphic(orderToTheButtomIcon);
        orderToTheButtomButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        orderToTheButtomButton.setOnAction((t) -> {
            factory.orderToTheButtomButtonAction(t);
        });
        hb.getChildren().add(setFrontImageButton);
        hb.getChildren().add(orderOneDownButton);
        hb.getChildren().add(orderOneUpButton);
        hb.getChildren().add(orderToTheButtomButton);
        Slider zoomSlider = new Slider(0, 100, 20);
        zoomSlider.setScaleX(0.7);
        zoomSlider.setScaleY(0.7);
        zoomSlider.setOrientation(Orientation.HORIZONTAL);
        zoomSlider.valueProperty().addListener((ObservableValue<? extends Number> ov, Number t, Number t1) -> {
            imageGrid.setCellWidth(defaultCellWidth + 3 * zoomSlider.getValue());
            imageGrid.setCellHeight(defaultCellHight + 3 * zoomSlider.getValue());
        });
        hb.getChildren().add(zoomSlider);
        vbox.getChildren().add(hb);
        vbox.getChildren().add(imageGrid);
        vbox.setAlignment(Pos.TOP_LEFT);
        int width = sortedMediaList.size() * 95 * 2;
        if (width > 600) {
            width = 600;
        }
        vbox.setPrefSize(width, 130 + 3 * 20);
        popOver.setContentNode(vbox);
        popOver.setArrowLocation(PopOver.ArrowLocation.BOTTOM_CENTER);
        popOver.setFadeInDuration(new Duration(1000));
        popOver.setCloseButtonEnabled(true);
        popOver.setAutoFix(true);
        popOver.setHeaderAlwaysVisible(true);
        popOver.setTitle("Stack view and ordering via drag and drop");
        popOver.show(anchore);
        popOver.requestFocus();
        popOver.setOnHidden((t) -> {
            if (factory.isChanged()) {
                //fire mousevent
                selectedCell.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0,
                        0, 0, 0, MouseButton.PRIMARY, 1, false, false, false, false,
                        false, false, false, false, false, true, null));
            }
        });
    }

}
