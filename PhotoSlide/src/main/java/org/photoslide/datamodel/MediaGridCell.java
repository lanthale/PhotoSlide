/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaView;
import javafx.scene.text.Font;
import javafx.util.Duration;
import org.controlsfx.control.GridCell;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.datamodel.MediaFile.MediaTypes;

/**
 *
 * @author selfemp
 */
public class MediaGridCell extends GridCell<MediaFile> {

    private final StackPane rootPane;
    private final MediaView mediaview;
    private final ImageView imageView;
    private final FontIcon layerIcon;
    private final FontIcon restoreIcon;
    private final FontIcon errorIcon;
    private final SimpleDoubleProperty rotationAngle;
    private final FontIcon dummyIcon;
    private final ProgressIndicator prgInd;
    private final FontIcon filmIcon;
    private final Label errorLabel;
    private final VBox deleteBox;

    public MediaGridCell() {
        this.setId("MediaGridCell");
        rootPane = new StackPane();
        deleteBox = new VBox();
        deleteBox.setStyle("-fx-background-color: rgba(80, 80, 80, .7);");
        rotationAngle = new SimpleDoubleProperty(0.0);
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.fitHeightProperty().bind(heightProperty().subtract(15));
        imageView.fitWidthProperty().bind(widthProperty().subtract(15));
        imageView.rotateProperty().bind(rotationAngle);
        mediaview = new MediaView();
        mediaview.setPreserveRatio(true);
        mediaview.fitHeightProperty().bind(heightProperty());
        mediaview.fitWidthProperty().bind(widthProperty());
        mediaview.rotateProperty().bind(rotationAngle);
        prgInd = new ProgressIndicator();
        prgInd.setId("MediaLoadingProgressIndicator");
        prgInd.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        prgInd.setMaxSize(50 * 0.6, 50 * 0.6);
        layerIcon = new FontIcon("ti-view-grid");
        restoreIcon = new FontIcon("ti-back-right");
        filmIcon = new FontIcon("fa-file-movie-o");
        filmIcon.setOpacity(0.3);
        dummyIcon = new FontIcon("fa-file-movie-o");
        errorIcon = new FontIcon("ti-bolt");
        rootPane.getChildren().add(prgInd);
        errorLabel = new Label("Error loading");
        Tooltip tp = new Tooltip("Cannot load mediafile, because format is not supported!");
        tp.setFont(new Font(13));
        errorLabel.setPadding(new Insets(-5));
        errorLabel.setTooltip(tp);
        errorLabel.setStyle("-fx-font-size:6");
        errorLabel.setGraphic(errorIcon);
        errorLabel.setContentDisplay(ContentDisplay.TOP);
        setupBinding();
    }

    private void setupBinding() {
        Platform.runLater(() -> {
            if (layerIcon.iconSizeProperty().isBound() == false) {
                layerIcon.iconSizeProperty().bind(rootPane.heightProperty().subtract(42));
            }
            if (restoreIcon.iconSizeProperty().isBound() == false) {
                restoreIcon.iconSizeProperty().bind(rootPane.heightProperty().subtract(28));
            }
            if (dummyIcon.iconSizeProperty().isBound() == false) {
                dummyIcon.iconSizeProperty().bind(rootPane.heightProperty().subtract(10));
            }
            if (filmIcon.iconSizeProperty().isBound() == false) {
                filmIcon.iconSizeProperty().bind(rootPane.heightProperty().subtract(10));
            }
            if (errorIcon.iconSizeProperty().isBound() == false) {
                errorIcon.iconSizeProperty().bind(rootPane.heightProperty().subtract(10));
            }
        });
    }

    /**
     * Here all properties of the mediaFile must be set. If not no graphic
     * update is happening
     *
     * @param item to be updated
     * @param empty only on creation otherwise should be always false
     */
    @Override
    protected void updateItem(MediaFile item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
        } else {
            if (item.isSelected() == true) {
                if (item.isStacked()) {
                    this.setId("MediaGridCellSelectedStacked");
                } else {
                    this.setId("MediaGridCellSelected");
                }
            } else {
                if (item.isStacked()) {
                    this.setId("MediaGridCellStacked");
                } else {
                    this.setId("MediaGridCell");
                }
            }
            item.loadingProperty().addListener((ov, t, t1) -> {
                if (t1 == false) {
                    this.requestLayout();
                }
            });
            item.bookmarkedProperty().addListener((o) -> {
                this.requestLayout();
            });
            switch (item.getMediaType()) {
                case VIDEO:
                    setMedia(item);
                    setGraphic(rootPane);
                    break;
                case IMAGE:
                    setImage(item);
                    setGraphic(rootPane);
                    break;
                case NONE:
                    setError(item);
                    setGraphic(rootPane);
                    break;
                default:
                    break;
            }
        }
    }

    public final void setError(MediaFile item) {
        PauseTransition pause = new PauseTransition(Duration.millis(100));
        pause.setOnFinished((t) -> {
            rootPane.getChildren().clear();
            HBox hb = new HBox();
            hb.setAlignment(Pos.BOTTOM_CENTER);
            hb.getChildren().add(errorLabel);
            rootPane.getChildren().add(hb);
            if (item.getDeletedProperty().getValue() == true) {
                setDeletedNode();
            }
        });
        pause.play();
    }

    public final void setImage(MediaFile item) {
        if (item.isLoading() == true) {
            setLoadingNode(item.getMediaType());
        } else {
            if (item.getUnModifiyAbleImage() == null) {
                item.setUnModifiyAbleImage(item.getClonedImage(item.getImage()));
            }
            item.setImage(item.setFilters());
            //calc cropview based on small imageview
            //imageView.setViewport(item.getCropView());
            rootPane.getChildren().clear();
            rootPane.getChildren().add(imageView);            
            imageView.setImage(item.getImage());
            rotationAngle.set(item.getRotationAngleProperty().get());
            setRatingNode(item.getRatingProperty().get());
            setBookmarked(item.isBookmarked());
            setStacked(item.isStacked(), item.getStackPos());
            if (item.getDeletedProperty().getValue() == true) {
                setDeletedNode();
            }
        }
    }

    public final void setMedia(MediaFile item) {
        if (item.isLoading() == true && item.getVideoSupported() == null) {
            setLoadingNode(item.getMediaType());
        } else {
            setRatingNode(item.getRatingProperty().get());
            if (item.getVideoSupported() == MediaFile.VideoTypes.SUPPORTED) {
                dummyIcon.setIconLiteral("fa-file-movie-o");
                rootPane.getChildren().clear();
                rootPane.getChildren().add(dummyIcon);
            } else {
                dummyIcon.setIconLiteral("fa-minus-circle");
                rootPane.getChildren().clear();
                rootPane.getChildren().add(filmIcon);
                rootPane.getChildren().add(dummyIcon);
            }
            setBookmarked(item.isBookmarked());
        }
    }

    public void setLoadingNode(MediaTypes mediaType) {
        prgInd.maxHeightProperty().unbind();
        prgInd.maxWidthProperty().unbind();
        if (mediaType == MediaFile.MediaTypes.IMAGE) {
            prgInd.maxHeightProperty().bind(imageView.fitHeightProperty().multiply(0.6));
            prgInd.maxWidthProperty().bind(imageView.fitWidthProperty().multiply(0.6));
        } else {
            prgInd.maxHeightProperty().bind(mediaview.fitHeightProperty().multiply(0.5));
            prgInd.maxWidthProperty().bind(mediaview.fitWidthProperty().multiply(0.5));
        }
        rootPane.getChildren().clear();
        rootPane.getChildren().add(prgInd);
    }

    private void setDeletedNode() {
        rootPane.getChildren().add(deleteBox);
        rootPane.getChildren().add(restoreIcon);
    }

    private void setRatingNode(int rating) {
        if (rating > 0) {
            VBox vb = new VBox();
            vb.setAlignment(Pos.BOTTOM_RIGHT);
            HBox hb = new HBox();
            hb.setPadding(new Insets(40, 0, 0, 0));
            for (int i = 0; i < rating; i++) {
                FontIcon starIcon = new FontIcon("fa-star");
                starIcon.setId("star-ikonli-font-icon");
                hb.getChildren().add(starIcon);
            }
            vb.getChildren().add(hb);
            rootPane.getChildren().add(vb);
        }
    }

    private void setStacked(boolean stacked, int stackPos) {
        VBox vb = new VBox();
        if (stacked == true && stackPos == 1) {
            vb.setAlignment(Pos.BOTTOM_RIGHT);
            vb.setPadding(new Insets(0, -3, -5, 0));
            vb.getChildren().add(layerIcon);
            rootPane.getChildren().add(vb);
        } else {
            rootPane.getChildren().remove(vb);
        }
    }

    private void setBookmarked(boolean bookmarked) {
        if (bookmarked) {
            VBox vb = new VBox();
            vb.setAlignment(Pos.TOP_LEFT);
            HBox hb = new HBox();
            hb.setPadding(new Insets(0, 0, 0, 3));
            FontIcon bookmarkIcon = new FontIcon("fa-bookmark");
            bookmarkIcon.setId("bookmark-icon");
            hb.getChildren().add(bookmarkIcon);
            vb.getChildren().add(hb);
            rootPane.getChildren().add(vb);
        }
    }

}
