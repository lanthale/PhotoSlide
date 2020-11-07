/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import org.photoslide.datamodel.MediaFile;
import org.controlsfx.control.GridCell;

/**
 *
 * @author selfemp
 */
public class MediaGridCell extends GridCell<MediaFile> {

    private final MediaFile mediaFile;

    public MediaGridCell() {
        this.setId("MediaGridCell");
        mediaFile = new MediaFile();
        managedProperty().bind(mediaFile.managedProperty());
        visibleProperty().bind(mediaFile.visibleProperty());
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
            mediaFile.setSize(item.getHeight(), item.getWidth());
            mediaFile.setVisible(item.isVisible());
            mediaFile.setManaged(item.isManaged());
            mediaFile.setName(item.getName());
            mediaFile.setDeleted(item.isDeleted());
            mediaFile.setSelected(item.isSelected());
            mediaFile.setRating(item.getRatingProperty().get());
            mediaFile.setRotationAngle(item.getRotationAngleProperty().get());
            mediaFile.setCropView(item.getCropView());
            mediaFile.setRecordTime(item.getRecordTime());
            mediaFile.setVideoSupported(item.getVideoSupported());
            mediaFile.setCreationTime(item.getCreationTime());
            mediaFile.setStackName(item.getStackName());
            mediaFile.setStackPos(item.getStackPos());
            mediaFile.setStacked(item.isStacked());
            if (mediaFile.isSelected() == true) {
                if (mediaFile.isStacked()) {
                    this.setId("MediaGridCellSelectedStacked");
                } else {
                    this.setId("MediaGridCellSelected");
                }
            } else {
                if (mediaFile.isStacked()) {
                    this.setId("MediaGridCellStacked");
                } else {
                    this.setId("MediaGridCell");
                }
            }
            switch (item.getMediaType()) {
                case VIDEO:
                    mediaFile.setMedia(item.getMedia(), item.getVideoSupported());
                    setGraphic(mediaFile);
                    break;
                case IMAGE:
                    mediaFile.setImage(item.getImage());
                    setGraphic(mediaFile);
                    break;
                default:
                    break;
            }
        }
    }

    public MediaFile getMediaFile() {
        return mediaFile;
    }

}
