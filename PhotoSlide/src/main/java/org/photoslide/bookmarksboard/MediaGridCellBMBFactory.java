/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.bookmarksboard;

import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;
import org.photoslide.datamodel.GridCellSelectionModel;
import org.photoslide.datamodel.MediaFile;

/**
 *
 * @author selfemp
 */
public class MediaGridCellBMBFactory implements Callback<GridView<MediaFile>, GridCell<MediaFile>> {

    private final SortedList<MediaFile> sortedMediaList;
    private MediaGridCellBMB selectedCell;
    private MediaFile selectedMediaFile;
    private final Comparator<MediaFile> stackNameComparator;
    private final ExecutorService executor;
    private final BookmarkBoardController bmbTools;
    private final GridCellSelectionModel selectionModel;

    public MediaGridCellBMBFactory(ExecutorService executor, BookmarkBoardController controller, SortedList<MediaFile> sortedMediaList) {
        this.sortedMediaList = sortedMediaList;
        this.bmbTools = controller;
        stackNameComparator = Comparator.comparing(MediaFile::getStackPos);
        this.executor = executor;        
        selectionModel = new GridCellSelectionModel();
    }

    @Override
    public GridCell<MediaFile> call(GridView<MediaFile> p) {
        MediaGridCellBMB cell = new MediaGridCellBMB();
        cell.setAlignment(Pos.CENTER);
        cell.setEditable(false);
        /*cell.setOnMouseClicked((t) -> {
            manageGUISelection(t, cell);
            handleGridCellSelection(t);
            t.consume();
        });*/
        return cell;
    }

    private void manageGUISelection(MouseEvent t, MediaGridCellBMB cell) {
        /*bmbTools.getFullMediaList().stream().filter(c -> c != null && c.isSelected() == true).forEach((mfile) -> {
            mfile.setSelected(false);
        });
        selectedMediaFile = ((MediaGridCellBMB) t.getSource()).getItem();
        bmbTools.getInfoBox().setVisible(true);
        bmbTools.getMediaFileInfoLabel().setText(selectedMediaFile.getName());        
        selectionModel.clear();
        selectionModel.add(((MediaGridCellBMB) t.getSource()).getItem());
        selectedCell = cell;
        cell.requestLayout();*/
    }

    private void handleGridCellSelection(MouseEvent t) {

    }

    public MediaFile getSelectedMediaFile() {
        return selectedMediaFile;
    }
    
    
    public MediaGridCellBMB getMediaCellForMediaFile(MediaFile input) {
        VirtualFlow vf = (VirtualFlow) bmbTools.getImageGrid().getChildrenUnmodifiable().get(0);
        for (int i = 0; i < vf.getCellCount(); i++) {
            for (Node mediaCell : vf.getCell(i).getChildrenUnmodifiable()) {
                if (((MediaGridCellBMB) mediaCell).getItem().getName().equalsIgnoreCase(input.getName())) {
                    return (MediaGridCellBMB) mediaCell;
                }
            }
        }
        return null;
    }

}
