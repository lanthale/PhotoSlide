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
import org.photoslide.datamodel.MediaFileLoader;
import org.photoslide.datamodel.MediaGridCell;

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
    private final MediaFileLoader fileLoader;

    public MediaGridCellBMBFactory(ExecutorService executor, BookmarkBoardController controller, SortedList<MediaFile> sortedMediaList) {
        this.sortedMediaList = sortedMediaList;
        this.bmbTools = controller;
        stackNameComparator = Comparator.comparing(MediaFile::getStackPos);
        this.executor = executor;
        selectionModel = new GridCellSelectionModel();
        fileLoader = new MediaFileLoader();
    }

    @Override
    public GridCell<MediaFile> call(GridView<MediaFile> p) {
        MediaGridCellBMB cell = new MediaGridCellBMB();
        cell.setAlignment(Pos.CENTER);
        cell.setEditable(false);
        cell.setOnMouseClicked((t) -> {
            manageGUISelection(t, cell);
            handleGridCellSelection(t);
            t.consume();
        });
        cell.itemProperty().addListener((ov, oldMediaItem, newMediaItem) -> {
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
        });
        return cell;
    }

    private void manageGUISelection(MouseEvent t, MediaGridCellBMB cell) {
        bmbTools.getFullMediaList().stream().filter(c -> c != null && c.isSelected() == true).forEach((mfile) -> {
            mfile.setSelected(false);
        });
        selectedMediaFile = ((MediaGridCellBMB) t.getSource()).getItem();
        selectionModel.clear();
        selectionModel.add(((MediaGridCellBMB) t.getSource()).getItem());
        selectedCell = cell;
        cell.requestLayout();
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

    public boolean isCellVisible(MediaGridCellBMB input) {
        VirtualFlow vf = (VirtualFlow) bmbTools.getImageGrid().getChildrenUnmodifiable().get(0);
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
    
    public void shutdown(){
        fileLoader.shutdown();
    }

}
