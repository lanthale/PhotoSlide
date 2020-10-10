/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.lighttable;

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
            //setGraphic(item);            
        } else {
            mediaFile.setName(item.getName());
            mediaFile.setDeleted(item.isDeleted());
            mediaFile.setSeleted(item.isSelected());
            mediaFile.setRating(item.getRatingProperty().get());
            mediaFile.setRotationAngle(item.getRotationAngleProperty().get());
            mediaFile.setCropView(item.getCropView());
            mediaFile.setRecordTime(item.getRecordTime());
            mediaFile.setVideoSupported(item.getVideoSupported());
            mediaFile.setCreationTime(item.getCreationTime());
            if (mediaFile.isSelected() == true) {
                this.setId("MediaGridCellSelected");
            } else {
                this.setId("MediaGridCell");
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
