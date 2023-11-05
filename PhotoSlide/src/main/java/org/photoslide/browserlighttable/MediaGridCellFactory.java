/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browserlighttable;

import org.photoslide.datamodel.GridCellSelectionModel;
import org.photoslide.datamodel.MediaGridCell;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.Utility;
import org.photoslide.browsermetadata.MetadataController;
import java.io.File;
import java.net.MalformedURLException;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.image.Image;
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
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.datamodel.MediaFile.MediaTypes;
import org.photoslide.datamodel.MediaFileLoader;
import org.photoslide.imageops.ImageFilter;

/**
 *
 * @author selfemp
 */
public class MediaGridCellFactory implements Callback<GridView<MediaFile>, GridCell<MediaFile>> {

    private Image img;

    private final Utility util;
    private final MetadataController metadataController;
    private final GridCellSelectionModel selectionModel;
    private GridView<MediaFile> grid;
    private final ExecutorService executor;
    private MediaGridCell selectedCell;
    private final LighttableController lightController;
    private MediaFile selectedMediaItem;
    private final AtomicInteger xMouse;
    private final AtomicInteger yMouse;
    private final Image dialogIcon;
    private ObservableList<ImageFilter> filterList;
    private Image imageWithFilters;
    private final Button resetCrop;
    private final MediaFileLoader fileLoader;
    private boolean listFilesActive;

    public MediaGridCellFactory(LighttableController lightController, GridView<MediaFile> grid, Utility util, MetadataController metadataController) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNamePrefix("factoryController").build());
        listFilesActive = true;
        this.util = util;
        this.metadataController = metadataController;
        this.grid = grid;
        this.lightController = lightController;
        fileLoader = new MediaFileLoader();
        selectionModel = new GridCellSelectionModel();
        //new RubberBandSelection(grid, selectionModel);        
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
                handleZoomIn(ratio, z.getX(), z.getY());
            } else {
                handleZoomOut(ratio, z.getX(), z.getY());
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
                if (y < 0) {
                    y = 0;
                }
                if (x < 0) {
                    x = 0;
                }
                if (x + width > lightController.getImageView().getImage().getWidth()) {
                    x = x - (x + width - lightController.getImageView().getImage().getWidth());
                }
                if (y + height > lightController.getImageView().getImage().getHeight()) {
                    y = y - (y + height - lightController.getImageView().getImage().getHeight());
                }
                Rectangle2D cropView = new Rectangle2D(x, y, width, height);
                lightController.getImageView().setViewport(cropView);
                xMouse.set((int) t.getX());
                yMouse.set((int) t.getY());
            }
        });
        dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
        resetCrop = new Button();
        FontIcon restoreIcon = new FontIcon("ti-back-right:24");
        resetCrop.setGraphic(restoreIcon);
        resetCrop.setOnAction((h) -> {
            lightController.getOptionPane().setDisable(false);
            lightController.getInfoPane().setDisable(false);
            lightController.getImageView().setViewport(null);
            selectedMediaItem.setCropView(null);
            lightController.getImageStackPane().getChildren().clear();
            lightController.getImageStackPane().getChildren().add(lightController.getImageView());
            lightController.getOptionPane().getChildren().clear();
            selectedCell.requestFocus();
        });
    }

    public void shutdown() {
        fileLoader.shutdown();
        executor.shutdownNow();
    }

    @Override
    public GridCell<MediaFile> call(GridView<MediaFile> p) {
        MediaGridCell cell = new MediaGridCell();
        cell.setAlignment(Pos.CENTER);
        cell.setEditable(false);
        cell.setOnMouseClicked((t) -> {
            manageGUISelection(t, cell);
            try {
                handleGridCellSelection(t);
            } catch (MalformedURLException ex) {
                Logger.getLogger(MediaGridCellFactory.class.getName()).log(Level.SEVERE, null, ex);
            }
            t.consume();
        });

        cell.itemProperty().addListener((ov, oldMediaItem, newMediaItem) -> {
            if (listFilesActive == false) {
                if (newMediaItem != null && oldMediaItem == null) {
                    if (newMediaItem.isLoading() == true) {
                        if (newMediaItem.getMediaType() == MediaFile.MediaTypes.IMAGE) {
                            if (isCellVisible(cell)) {
                                fileLoader.loadImage(newMediaItem);
                            }
                        } else {
                            if (isCellVisible(cell)) {
                                fileLoader.loadVideo(newMediaItem);
                            }
                        }
                    }
                }
            }
        });
        return cell;
    }

    private void manageGUISelection(MouseEvent t, MediaGridCell cell) {
        if (t.isShiftDown()) {
            //select all nodes in between
            int indexOfStart = lightController.getSortedMediaList().indexOf(((MediaFile) selectionModel.getSelection().iterator().next()));
            int indexOfEnd = lightController.getSortedMediaList().indexOf(cell.getItem());
            if (indexOfStart < indexOfEnd) {
                for (int i = indexOfStart; i <= indexOfEnd; i++) {
                    selectionModel.add(lightController.getSortedMediaList().get(i));
                }
            } else {
                for (int i = indexOfEnd; i <= indexOfStart; i++) {
                    selectionModel.add(lightController.getSortedMediaList().get(i));
                }
            }
        } else {
            if (t.isShortcutDown()) {
                if (selectionModel.contains(cell.getItem())) {
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
    }

    public void handleGridCellSelection(Event t) throws MalformedURLException {
        if (((MediaGridCell) t.getSource()).getItem().getMediaType() == MediaTypes.NONE) {
            String name = ((MediaGridCell) t.getSource()).getItem().getName();
            Platform.runLater(() -> {
                setStdGUIState();
                lightController.getFilenameLabel().setVisible(true);
                lightController.getFilenameLabel().setText(name);
            });
            return;
        }
        if (t.getTarget().getClass().equals(FontIcon.class)) {
            String code = ((FontIcon) t.getTarget()).getIconLiteral();
            if (code.equalsIgnoreCase("ti-view-grid")) {
                handleStackButtonAction(((MediaGridCell) t.getSource()).getItem().getStackName(), (MediaGridCell) t.getSource());
            }
            t.consume();
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
        if (img != null) {
            cancleImageTask();
            lightController.getPlayIcon().setVisible(false);
            lightController.getImageView().setImage(null);
            lightController.getImageView().setViewport(null);
            lightController.getMediaView().setMediaPlayer(null);
            lightController.getTitleLabel().setVisible(false);
            lightController.getCameraLabel().setVisible(false);
            lightController.getFilenameLabel().setVisible(false);
            lightController.getRatingControl().setVisible(false);
            lightController.getOptionPane().setDisable(false);
        }

        selectedMediaItem = ((MediaGridCell) t.getSource()).getItem();
        selectedCell = (MediaGridCell) t.getSource();
        if (selectedMediaItem.isDeleted() == true) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Restore MediaFile " + selectedMediaItem.getName() + " ?", ButtonType.CANCEL, ButtonType.YES, ButtonType.NO);

            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().add(
                    getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(dialogIcon);
            Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) grid.getScene().getWindow());
            alert.showAndWait();
            if (alert.getResult() == ButtonType.YES) {
                selectedMediaItem.setDeleted(false);
                executor.submit(() -> {
                    selectedMediaItem.saveEdits();
                });
            } else {
                alert.hide();
                return;
            }
        }
        if (lightController.getShowPreviewPaneToggle().isSelected() == false) {
            lightController.getPlayIcon().setVisible(false);
            lightController.getImageView().setImage(null);
            lightController.getMediaView().setMediaPlayer(null);
            lightController.getTitleLabel().setVisible(false);
            lightController.getCameraLabel().setVisible(false);
            lightController.getFilenameLabel().setVisible(false);
            lightController.getRatingControl().setVisible(false);
            return;
        }

        updateGUIAccordingSelection();

        Platform.runLater(() -> {
            lightController.getPlayIcon().setVisible(false);
            lightController.getImageView().setImage(null);
            lightController.getMediaView().setMediaPlayer(null);
        });
        switch (selectedMediaItem.getMediaType()) {
            case VIDEO:
                metadataController.setSelectedFile(selectedMediaItem);
                try {
                    Platform.runLater(() -> {
                        lightController.getImageProgress().progressProperty().unbind();
                        lightController.getImageProgress().setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                        lightController.getImageProgress().setVisible(true);
                        lightController.getImageView().setVisible(false);
                        lightController.getMediaView().setVisible(true);
                        lightController.getImageProgress().setVisible(false);
                        lightController.getInvalidStackPane().setVisible(false);
                        lightController.getPlayIcon().setVisible(true);
                        lightController.getMediaView().toFront();
                        lightController.getPlayIcon().toFront();
                        lightController.getFilenameLabel().setText(new File(selectedCell.getItem().getName()).getName());
                    });
                    Task mediaTask = loadVideo();
                    executor.submit(mediaTask);
                    this.lightController.getMainController().getTaskProgressView().getTasks().add(mediaTask);
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
                break;

            case IMAGE:
                metadataController.setSelectedFile(selectedMediaItem);
                Platform.runLater(() -> {
                    lightController.getImageProgress().progressProperty().unbind();
                    lightController.getImageView().setImage(null);
                    lightController.getInvalidStackPane().setVisible(false);
                    lightController.getImageView().setVisible(true);
                    lightController.getImageView().setCache(true);
                    lightController.getImageView().setCacheHint(CacheHint.SPEED);
                    lightController.getMediaView().setVisible(false);
                    lightController.getImageProgress().setProgress(0);
                    lightController.getImageProgress().setVisible(true);
                    lightController.getDetailToolbar().setDisable(false);
                    lightController.getTitleLabel().textProperty().bind(selectedCell.getItem().titleProperty());
                    lightController.getCameraLabel().textProperty().bind(selectedCell.getItem().cameraProperty());
                    lightController.getFilenameLabel().setText(new File(selectedCell.getItem().getName()).getName());
                    lightController.getRatingControl().ratingProperty().bind(selectedCell.getItem().getRatingProperty());
                    lightController.getImageView().rotateProperty().bind(selectedCell.getItem().getRotationAngleProperty());
                    lightController.getTitleLabel().setVisible(true);
                    lightController.getCameraLabel().setVisible(true);
                    lightController.getFilenameLabel().setVisible(true);
                    lightController.getRatingControl().setVisible(true);
                    switch (selectedCell.getItem().getRotationAngleProperty().getValue().intValue()) {
                        case 0:
                            lightController.getImageView().fitWidthProperty().bind(lightController.getStackPane().widthProperty());
                            lightController.getImageView().fitHeightProperty().bind(lightController.getStackPane().heightProperty());
                            break;
                        case 180:
                            lightController.getImageView().fitWidthProperty().bind(lightController.getStackPane().widthProperty());
                            lightController.getImageView().fitHeightProperty().bind(lightController.getStackPane().heightProperty());
                            break;
                        case 360:
                            lightController.getImageView().fitWidthProperty().bind(lightController.getStackPane().widthProperty());
                            lightController.getImageView().fitHeightProperty().bind(lightController.getStackPane().heightProperty());
                            break;
                        case -180:
                            lightController.getImageView().fitWidthProperty().bind(lightController.getStackPane().widthProperty());
                            lightController.getImageView().fitHeightProperty().bind(lightController.getStackPane().heightProperty());
                            break;
                        case -360:
                            lightController.getImageView().fitWidthProperty().bind(lightController.getStackPane().widthProperty());
                            lightController.getImageView().fitHeightProperty().bind(lightController.getStackPane().heightProperty());
                            break;
                        case 90:
                            lightController.getImageView().fitWidthProperty().bind(lightController.getStackPane().heightProperty());
                            lightController.getImageView().fitHeightProperty().bind(lightController.getStackPane().widthProperty());
                            break;
                        case 270:
                            lightController.getImageView().fitWidthProperty().bind(lightController.getStackPane().heightProperty());
                            lightController.getImageView().fitHeightProperty().bind(lightController.getStackPane().widthProperty());
                            break;
                        case -90:
                            lightController.getImageView().fitWidthProperty().bind(lightController.getStackPane().heightProperty());
                            lightController.getImageView().fitHeightProperty().bind(lightController.getStackPane().widthProperty());
                            break;
                        case -270:
                            lightController.getImageView().fitWidthProperty().bind(lightController.getStackPane().heightProperty());
                            lightController.getImageView().fitHeightProperty().bind(lightController.getStackPane().widthProperty());
                            break;
                    }
                    lightController.getImageView().setImage(selectedMediaItem.getImage());
                });
                loadImage();
                break;
            case NONE:
                Platform.runLater(() -> {
                    lightController.getImageView().setVisible(false);
                    lightController.getMediaView().setVisible(false);
                    lightController.getImageProgress().setVisible(false);
                    lightController.getInvalidStackPane().setVisible(false);
                    lightController.getPlayIcon().setVisible(false);
                });
                break;
            default:
        }
    }

    public Task loadVideo() {
        Task mediaTask = new Task() {
            @Override
            protected MediaPlayer call() throws Exception {
                Media media = new Media(selectedMediaItem.getPathStorage().toUri().toURL().toExternalForm());
                MediaPlayer mp = new MediaPlayer(media);
                return mp;
            }
        };
        mediaTask.setOnSucceeded((p) -> {
            lightController.getMediaView().setMediaPlayer((MediaPlayer) mediaTask.getValue());
            if (lightController.getMediaView().getMediaPlayer() != null) {
                lightController.getMediaView().getMediaPlayer().stop();
            }
            lightController.getMediaView().setOnMouseMoved((k) -> {
                Platform.runLater(() -> {
                    lightController.getPlayIcon().setVisible(true);
                });
                if (lightController.getMediaView().getMediaPlayer() != null) {
                    if (lightController.getMediaView().getMediaPlayer().getStatus() == MediaPlayer.Status.PLAYING) {
                        Platform.runLater(() -> {
                            lightController.getPlayIcon().setVisible(true);
                        });
                        lightController.getPlayIcon().setIconLiteral("fa-pause");
                    } else {
                        lightController.getPlayIcon().setIconLiteral("fa-play");
                    }
                    util.hideNodeAfterTime(lightController.getPlayIcon(), 2, true);
                }
            });
            lightController.getPlayIcon().setOnMouseClicked((k2) -> {
                if (lightController.getMediaView().getMediaPlayer().getStatus() == MediaPlayer.Status.PLAYING) {
                    lightController.getMediaView().getMediaPlayer().pause();
                } else {
                    lightController.getMediaView().getMediaPlayer().play();
                    lightController.getPlayIcon().setIconLiteral("fa-pause");
                    util.hideNodeAfterTime(lightController.getPlayIcon(), 1, true);
                }
            });
            lightController.getMediaView().setOnKeyPressed((k3) -> {
                if (k3.getCode() == KeyCode.SPACE) {
                    if (lightController.getMediaView().getMediaPlayer().getStatus() == MediaPlayer.Status.PLAYING) {
                        lightController.getMediaView().getMediaPlayer().pause();
                    } else {
                        lightController.getMediaView().getMediaPlayer().play();
                        lightController.getPlayIcon().setIconLiteral("fa-pause");
                        util.hideNodeAfterTime(lightController.getPlayIcon(), 1, true);
                    }
                }
            });
            util.hideNodeAfterTime(lightController.getImageProgress(), 1, true);
        });
        return mediaTask;
    }

    public void loadImage() throws MalformedURLException {
        String url = selectedMediaItem.getImageUrl().toString();
        img = new Image(url, true);
        Platform.runLater(() -> {
            lightController.getImageProgress().progressProperty().bind(img.progressProperty());
        });
        img.progressProperty().addListener((ov, g, g1) -> {
            if ((Double) g1 == 1.0 && !img.isError()) {
                lightController.getImageProgress().setVisible(false);
                lightController.getImageView().setImage(img);
                imageWithFilters = img;
                filterList = selectedMediaItem.getFilterListWithoutImageData();
                for (ImageFilter imageFilter : filterList) {
                    switch (imageFilter.getName()) {
                        case "ExposureFilter" -> {
                            metadataController.setExposerFilter(imageFilter);
                            metadataController.getApertureSlider().setValue(imageFilter.getValues()[0]);
                        }
                        case "GainFilter" -> {
                            metadataController.setGainFilter(imageFilter);
                            metadataController.getGainSlider().setValue(imageFilter.getValues()[0]);
                            metadataController.getBiasSlider().setValue(imageFilter.getValues()[1]);
                        }
                    }
                    imageWithFilters = imageFilter.loadMediaData(img);
                    imageFilter.filterMediaData(imageFilter.getValues());
                }
                img = imageWithFilters;
                lightController.getImageView().setImage(img);
            }
        });

        lightController.getImageView().setViewport(selectedCell.getItem().getCropView());

        if (selectedCell.getItem().getCropView() != null) {
            Platform.runLater(() -> {
                resetCrop.setVisible(true);
                lightController.getOptionPane().getChildren().clear();
                lightController.getOptionPane().getChildren().add(resetCrop);
            });
        } else {
            Platform.runLater(() -> {
                resetCrop.setVisible(false);
            });
        }
    }

    private void updateGUIAccordingSelection() {
        if (selectedMediaItem.isBookmarked()) {
            lightController.getBookmarkButton().setText("Unbookmark");
        } else {
            lightController.getBookmarkButton().setText("Bookmark");
        }
    }

    private void handleZoomIn(double ratio, double mouseX, double mouseY) {
        Rectangle2D viewport = lightController.getImageView().getViewport();
        double x = viewport.getMinX() + 20;
        double y = viewport.getMinY() + 20 * ratio;
        double width = viewport.getWidth() - 40;
        double height = viewport.getHeight() - 40 * ratio;
        if (height > 0) {
            Rectangle2D cropView = new Rectangle2D(x, y, width, height);
            lightController.getImageView().setViewport(cropView);
        }
    }

    private void handleZoomOut(double ratio, double mouseX, double mouseY) {
        Rectangle2D viewport = lightController.getImageView().getViewport();
        double x = viewport.getMinX() - 20;
        double y = viewport.getMinY() - 20 * ratio;
        double width = viewport.getWidth() + 40;
        double height = viewport.getHeight() + 40 * ratio;
        if (x < 0) {
            x = 20;
            width = viewport.getWidth() + 40;
            if (width > lightController.getImageView().getImage().getWidth()) {
                x = 0;
                width = lightController.getImageView().getImage().getWidth();
            }
        }
        if (y < 0) {
            y = 20;
            height = viewport.getHeight() + 40 * ratio;
            if (height > lightController.getImageView().getImage().getHeight()) {
                y = 0;
                height = lightController.getImageView().getImage().getHeight();
            }
        }
        Rectangle2D cropView = new Rectangle2D(x, y, width, height);
        lightController.getImageView().setViewport(cropView);
    }

    public void setStdGUIState() {
        lightController.getMainController().handleMenuDisable(false);
        lightController.getImageView().rotateProperty().unbind();
        lightController.getImageView().setRotate(0);
        lightController.getMediaView().setVisible(false);
        lightController.getMediaView().setMediaPlayer(null);
        lightController.getPlayIcon().setVisible(false);
        lightController.getImageProgress().setVisible(false);
        //lightController.getBookmarkButton().setDisable(false);
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
            lightController.getSnapshotView().setSelectionRatioFixed(false);
            lightController.getSnapshotView().setFixedSelectionRatio(1);
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
        resetCrop.setVisible(false);
    }

    public GridCellSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public void cancleTask() {
        if (img != null) {
            img.cancel();
        }
        metadataController.cancelTasks();
        fileLoader.cancleTasks();
    }

    private void cancleImageTask() {
        if (img != null) {
            img.cancel();
        }
        metadataController.cancelTasks();
    }

    public MediaGridCell getSelectedCell() {
        return selectedCell;
    }

    public MediaFile getSelectedMediaItem() {
        return selectedMediaItem;
    }

    public MediaGridCell getMediaCellForMediaFile(MediaFile input) {
        VirtualFlow vf = (VirtualFlow) grid.getChildrenUnmodifiable().get(0);
        for (int i = 0; i < vf.getCellCount(); i++) {
            for (Node mediaCell : vf.getCell(i).getChildrenUnmodifiable()) {
                if (((MediaGridCell) mediaCell).getItem().getName().equalsIgnoreCase(input.getName())) {
                    return (MediaGridCell) mediaCell;
                }
            }
        }
        return null;
    }

    /**
     * Scrolls the GridView one row up.
     *
     * @param input next visible media cell
     */
    public void oneRowUp(MediaGridCell input, double cellHeight) {
        // get the underlying VirtualFlow object
        VirtualFlow flow = (VirtualFlow) grid.getChildrenUnmodifiable().get(0);
        if (flow.getCellCount() == 0) {
            return; // check that rows exist
        }
        for (int i = 0; i <= flow.cellCountProperty().get(); i++) {
            if (flow.getCell(i).getChildrenUnmodifiable().contains(input)) {
                int selectedRow = i;
                int index = flow.getCell(i).getChildrenUnmodifiable().indexOf(input);
                if (index == flow.getCell(i).getChildrenUnmodifiable().size() - 1) {
                    if (--selectedRow < 0) {
                        selectedRow = 0;
                    }
                    if (selectedRow >= flow.cellCountProperty().get()) {
                        selectedRow = flow.getCellCount() - 1;
                    }
                    //flow.scrollTo(selectedRow);
                    flow.scrollPixels(-cellHeight);
                }
            }
        }
    }

    /**
     * Scrolls the GridView one row down.
     *
     * @param input nextMediaCell to be selected
     * @param gridView
     */
    public void oneRowDown(MediaGridCell input, double cellHeight) {
        // get the underlying VirtualFlow object
        VirtualFlow flow = (VirtualFlow) grid.getChildrenUnmodifiable().get(0);
        if (flow.getCellCount() == 0) {
            return; // check that rows exist
        }
        int cellcount = flow.cellCountProperty().get();
        for (int i = 0; i <= cellcount; i++) {
            if (flow.getCell(i).getChildrenUnmodifiable().contains(input)) {
                int selectedRow = i;
                int index = flow.getCell(i).getChildrenUnmodifiable().indexOf(input);
                if (index == 0) {
                    if (++selectedRow >= cellcount) {
                        selectedRow = flow.getCellCount() - 1;
                    }
                    //flow.scrollTo(selectedRow);
                    flow.scrollPixels(cellHeight);
                    break;
                }
                break;
            }
        }
    }

    /**
     *
     * @param input Mediacell to check
     * @return true if cell is actual visible
     */
    public boolean isCellVisible(MediaGridCell input) {
        VirtualFlow vf = (VirtualFlow) grid.getChildrenUnmodifiable().get(0);
        boolean ret = false;
        if (vf.getFirstVisibleCell() == null) {
            return false;
        }
        int start = vf.getFirstVisibleCell().getIndex();
        int end = vf.getLastVisibleCell().getIndex();
        if (start == end) {
            return true;
        }
        for (int i = start; i <= end; i++) {
            if (vf.getCell(i).getChildrenUnmodifiable().contains(input)) {
                return true;
            }
        }
        return ret;
    }

    public void handleStackButtonAction(String stackName, Node anchore) {
        FilteredList<MediaFile> filteredMediaList = lightController.getFullMediaList().filtered(mFile -> mFile.getStackName().equalsIgnoreCase(stackName));
        SortedList<MediaFile> sortedMediaList = new SortedList<>(filteredMediaList);
        Comparator<MediaFile> stackNameComparator = Comparator.comparing(MediaFile::getStackPos);
        sortedMediaList.setComparator(stackNameComparator);
        GridView<MediaFile> mediaGrid = new GridView<>();
        MediaGridCellStackedFactory factory = new MediaGridCellStackedFactory(executor, lightController, sortedMediaList, mediaGrid);
        mediaGrid.setCellFactory(factory);
        double defaultCellWidth = mediaGrid.getCellWidth();
        double defaultCellHight = mediaGrid.getCellHeight();
        mediaGrid.setCellWidth(defaultCellWidth + 3 * 10);
        mediaGrid.setCellHeight(defaultCellHight + 3 * 10);
        VBox vbox = new VBox();
        vbox.setSpacing(5);
        PopOver popOver = new PopOver();
        popOver.setDetachable(false);
        popOver.setAnimated(true);
        popOver.setContentNode(vbox);
        popOver.setArrowLocation(PopOver.ArrowLocation.BOTTOM_CENTER);
        popOver.setFadeInDuration(new Duration(1000));
        popOver.setCloseButtonEnabled(true);
        popOver.setAutoFix(true);
        popOver.setHeaderAlwaysVisible(true);
        popOver.setTitle("Stack view and ordering via drag and drop");
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
        Slider zoomSlider = new Slider(0, 100, 10);
        zoomSlider.setScaleX(0.7);
        zoomSlider.setScaleY(0.7);
        zoomSlider.setOrientation(Orientation.HORIZONTAL);
        zoomSlider.valueProperty().addListener((ObservableValue<? extends Number> ov, Number t, Number t1) -> {
            mediaGrid.setCellWidth(defaultCellWidth + 3 * zoomSlider.getValue());
            mediaGrid.setCellHeight(defaultCellHight + 3 * zoomSlider.getValue());
        });
        hb.getChildren().add(zoomSlider);
        vbox.getChildren().add(hb);
        vbox.getChildren().add(mediaGrid);
        vbox.setAlignment(Pos.TOP_LEFT);
        int width = sortedMediaList.size() * 95 * 2;
        if (width > 600) {
            width = 600;
        }
        vbox.setPrefSize(width, 130 + 3 * 20);
        popOver.show(anchore);
        if (mediaGrid.getItems().isEmpty()) {
            mediaGrid.setItems(sortedMediaList);
        }
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

    public void setListFilesActive(boolean listFilesActive) {
        this.listFilesActive = listFilesActive;
    }

}
