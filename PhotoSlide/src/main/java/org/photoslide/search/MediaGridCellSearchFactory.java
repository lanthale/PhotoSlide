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
import org.photoslide.datamodel.MediaFile;

/**
 *
 * @author selfemp
 */
public class MediaGridCellSearchFactory implements Callback<GridView<MediaFile>, GridCell<MediaFile>> {

    private final SortedList<MediaFile> sortedMediaList;
    private MediaGridCellSR selectedCell;    
    private final Comparator<MediaFile> stackNameComparator;    
    private final ExecutorService executor;
    private boolean changed;
    private final SearchTools searchTools;

    public MediaGridCellSearchFactory(ExecutorService executor, SearchTools controller, SortedList<MediaFile> sortedMediaList) {
        this.sortedMediaList = sortedMediaList;
        this.searchTools = controller;
        stackNameComparator = Comparator.comparing(MediaFile::getStackPos);
        this.executor = executor;        
        changed = false;
    }

    @Override
    public GridCell<MediaFile> call(GridView<MediaFile> p) {
        MediaGridCellSR cell = new MediaGridCellSR();
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

    private void manageGUISelection(MouseEvent t, MediaGridCellSR cell) {
        //set UI via ID and CSS
        cell.requestLayout();
        selectedCell = cell;
    }

    private void handleGridCellSelection(MouseEvent t) {

    }

    
}
