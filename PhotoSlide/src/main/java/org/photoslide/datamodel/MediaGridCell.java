/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.MediaView;
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
    private final SimpleDoubleProperty rotationAngle;
    private FontIcon dummyIcon;
    private ProgressIndicator prgInd;
    private boolean loading;
    private FontIcon filmIcon;

    public MediaGridCell() {
        this.setId("MediaGridCell");
        loading = true;
        rootPane = new StackPane();
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
        layerIcon = new FontIcon("ti-view-grid");
        restoreIcon = new FontIcon("ti-back-right");
        filmIcon = new FontIcon("fa-file-movie-o");
        filmIcon.setOpacity(0.3);
        dummyIcon = new FontIcon("fa-file-movie-o");        
        layerIcon.iconSizeProperty().bind(rootPane.heightProperty().subtract(42));
        restoreIcon.iconSizeProperty().bind(rootPane.heightProperty().subtract(28));
        dummyIcon.iconSizeProperty().bind(rootPane.heightProperty().subtract(10));        
        filmIcon.iconSizeProperty().bind(rootPane.heightProperty().subtract(10));
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
            loading = item.isLoading();
            switch (item.getMediaType()) {
                case VIDEO -> {
                    setMedia(item);
                    setGraphic(rootPane);
                }
                case IMAGE -> {
                    setImage(item);
                    setGraphic(rootPane);
                }
                default -> {
                }
            }
        }
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
            //imageView.setViewport(cropView);
            rootPane.getChildren().clear();
            rootPane.getChildren().add(imageView);
            imageView.setImage(item.getImage());
            setRatingNode(item.getRatingProperty().get());
            setStacked(item.isStacked(), item.getStackPos());
            if (item.getDeletedProperty().getValue() == true) {
                setDeletedNode();
            }
            item.setLoading(false);
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
            item.setLoading(false);
        }
    }

    public void setLoadingNode(MediaTypes mediaType) {
        prgInd = new ProgressIndicator();
        prgInd.setId("MediaLoadingProgressIndicator");
        prgInd.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        if (mediaType == MediaFile.MediaTypes.IMAGE) {
            prgInd.maxHeightProperty().bind(imageView.fitHeightProperty().multiply(0.6));
            prgInd.maxWidthProperty().bind(imageView.fitWidthProperty().multiply(0.6));
        } else {
            prgInd.maxHeightProperty().bind(mediaview.fitHeightProperty().multiply(0.6));
            prgInd.maxWidthProperty().bind(mediaview.fitWidthProperty().multiply(0.6));
        }
        rootPane.getChildren().clear();
        rootPane.getChildren().add(prgInd);
    }

    private void setDeletedNode() {
        VBox v = new VBox();
        v.setStyle("-fx-background-color: rgba(80, 80, 80, .7);");
        rootPane.getChildren().add(v);
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

}
