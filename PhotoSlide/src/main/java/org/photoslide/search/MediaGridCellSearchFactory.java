/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;

import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
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
public class MediaGridCellSearchFactory implements Callback<GridView<MediaFile>, GridCell<MediaFile>> {

    private final SortedList<MediaFile> sortedMediaList;
    private MediaGridCellSR selectedCell;
    private MediaFile selectedMediaFile;
    private final Comparator<MediaFile> stackNameComparator;
    private final ExecutorService executor;
    private boolean changed;
    private final SearchToolsController searchTools;
    private final GridCellSelectionModel selectionModel;

    public MediaGridCellSearchFactory(ExecutorService executor, SearchToolsController controller, SortedList<MediaFile> sortedMediaList) {
        this.sortedMediaList = sortedMediaList;
        this.searchTools = controller;
        stackNameComparator = Comparator.comparing(MediaFile::getStackPos);
        this.executor = executor;
        changed = false;
        selectionModel = new GridCellSelectionModel();
    }

    @Override
    public GridCell<MediaFile> call(GridView<MediaFile> p) {
        MediaGridCellSR cell = new MediaGridCellSR();
        cell.setAlignment(Pos.CENTER);
        cell.setEditable(false);
        cell.setOnMouseClicked((t) -> {
            manageGUISelection(t, cell);
            handleGridCellSelection(t);
            t.consume();
        });
        return cell;
    }

    private void manageGUISelection(MouseEvent t, MediaGridCellSR cell) {
        searchTools.getFullMediaList().stream().filter(c -> c != null && c.isSelected() == true).forEach((mfile) -> {
            mfile.setSelected(false);
        });
        selectedMediaFile = ((MediaGridCellSR) t.getSource()).getItem();
        searchTools.getInfoBox().setVisible(true);
        searchTools.getMediaFileInfoLabel().setText(selectedMediaFile.getName());
        selectionModel.clear();
        selectionModel.add(((MediaGridCellSR) t.getSource()).getItem());
        selectedCell = cell;
        cell.requestLayout();
    }

    private void handleGridCellSelection(MouseEvent t) {

    }

    public MediaFile getSelectedMediaFile() {
        return selectedMediaFile;
    }
    
    

}
