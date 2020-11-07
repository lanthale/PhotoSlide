/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.lighttable;

import java.util.Comparator;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.datamodel.MediaGridCell;

/**
 *
 * @author selfemp
 */
public class MediaGridCellStackedFactory implements Callback<GridView<MediaFile>, GridCell<MediaFile>> {

    private final SortedList<MediaFile> sortedMediaList;
    private MediaGridCell selectedCell;
    private final LighttableController lighttableController;
    private Comparator<MediaFile> stackNameComparator;

    public MediaGridCellStackedFactory(LighttableController controller, SortedList<MediaFile> sortedMediaList) {
        this.sortedMediaList = sortedMediaList;
        this.lighttableController = controller;
        stackNameComparator = Comparator.comparing(MediaFile::getStackPos);
    }

    @Override
    public GridCell<MediaFile> call(GridView<MediaFile> p) {
        MediaGridCell cell = new MediaGridCell();
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
        /*sortedMediaList.forEach((mFile) -> {
            mFile.setId("MediaGridCellStacked");
        });*/
        cell.setId("MediaGridCellSelectedStackedDetails");
        selectedCell = cell;
    }

    private void handleGridCellSelection(MouseEvent t) {

    }

    void setOnFrontImageButtonAction(ActionEvent t) {
        MediaFile actualTopImage = sortedMediaList.filtered(mFile -> mFile.getStackPos() == 1).get(0);
        int actStackPos = selectedCell.getItem().getStackPos();
        selectedCell.getItem().setStackPos(1);
        actualTopImage.setStackPos(actStackPos);
        actualTopImage.setSeleted(false);        
        selectedCell.getItem().setSeleted(true);
        Platform.runLater(() -> {
            actualTopImage.saveEdits();
            ObservableList<MediaFile> fullMediaList = lighttableController.getFullMediaList();
            fullMediaList.set(fullMediaList.indexOf(selectedCell.getItem()), selectedCell.getItem());            
            lighttableController.updateSortFiltering();            
            sortedMediaList.setComparator(stackNameComparator);
            selectedCell.getItem().saveEdits();
        });        
    }

    void orderOneDownButtonAction(ActionEvent t) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    void orderOneUpButtonAction(ActionEvent t) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    void orderToTheButtomButtonAction(ActionEvent t) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
