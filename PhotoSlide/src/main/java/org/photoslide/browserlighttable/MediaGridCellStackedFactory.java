/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browserlighttable;

import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.datamodel.MediaFileLoader;
import org.photoslide.datamodel.MediaGridCell;
import org.photoslide.datamodel.MediaGridCellStackedDetailView;

/**
 *
 * @author selfemp
 */
public class MediaGridCellStackedFactory implements Callback<GridView<MediaFile>, GridCell<MediaFile>> {

    private final SortedList<MediaFile> sortedMediaList;
    private MediaGridCellStackedDetailView selectedCell;
    private final LighttableController lighttableController;
    private final Comparator<MediaFile> stackNameComparator;
    private final ExecutorService executor;
    private boolean changed;
    private final MediaFileLoader fileLoader;
    private GridView<MediaFile> mediaGrid;

    public MediaGridCellStackedFactory(ExecutorService executor, LighttableController controller, SortedList<MediaFile> sortedMediaList, GridView<MediaFile> mediaGrid) {
        this.sortedMediaList = sortedMediaList;
        this.lighttableController = controller;
        stackNameComparator = Comparator.comparing(MediaFile::getStackPos);
        this.executor = executor;
        changed = false;
        fileLoader = new MediaFileLoader();
        this.mediaGrid = mediaGrid;
    }

    @Override
    public GridCell<MediaFile> call(GridView<MediaFile> p) {
        MediaGridCellStackedDetailView cell = new MediaGridCellStackedDetailView();
        cell.setAlignment(Pos.CENTER);
        cell.setEditable(false);
        cell.setOnMouseClicked((t) -> {
            manageGUISelection(t, cell);
            handleGridCellSelection(t);
            //cell.requestFocus();
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

    private void manageGUISelection(MouseEvent t, MediaGridCellStackedDetailView cell) {
        sortedMediaList.forEach((mFile) -> {
            mFile.setSubViewSelected(false);
        });
        cell.getItem().setSubViewSelected(true);
        cell.requestLayout();
        selectedCell = cell;
    }

    private void handleGridCellSelection(MouseEvent t) {

    }

    void setOnFrontImageButtonAction(ActionEvent t) {
        if (selectedCell != null) {
            lighttableController.getImageView().setImage(null);
            changeStackPosition(1, selectedCell.getItem());
            changed = true;
        }
    }

    void orderOneDownButtonAction(ActionEvent t) {
        if (selectedCell != null) {
            lighttableController.getImageView().setImage(null);
            changeStackPosition(sortedMediaList.indexOf(selectedCell.getItem()) + 2, selectedCell.getItem());
            changed = true;
        }
    }

    void orderOneUpButtonAction(ActionEvent t) {
        if (selectedCell != null) {
            lighttableController.getImageView().setImage(null);
            changeStackPosition(sortedMediaList.indexOf(selectedCell.getItem()), selectedCell.getItem());
            changed = true;
        }
    }

    void orderToTheButtomButtonAction(ActionEvent t) {
        if (selectedCell != null) {
            lighttableController.getImageView().setImage(null);
            changeStackPosition(sortedMediaList.size(), selectedCell.getItem());
            changed = true;
        }
    }

    /**
     *
     * @param input Mediacell to check
     * @return true if cell is actual visible
     */
    public boolean isCellVisible(MediaGridCellStackedDetailView input) {
        VirtualFlow vf = (VirtualFlow) mediaGrid.getChildrenUnmodifiable().get(0);
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

    /**
     * method to change the positon inside of the fullMediaList
     *
     * @param newPos the new position where the item should be moved
     * @param mediaFileToBeChanged the mediafile which should be moved
     */
    private void changeStackPosition(int newPos, MediaFile mediaFileToBeChanged) {
        if (mediaFileToBeChanged == null) {
            return;
        }
        MediaFile actuaMediaFile = sortedMediaList.get(newPos - 1);
        MediaFile selectedMediaFile = sortedMediaList.get(sortedMediaList.indexOf(mediaFileToBeChanged));
        int actStackPos = selectedMediaFile.getStackPos();
        selectedMediaFile.setStackPos(newPos);
        actuaMediaFile.setStackPos(actStackPos);
        if (selectedMediaFile.getStackPos() == 1) {
            selectedMediaFile.setSelected(true);
        } else {
            selectedMediaFile.setSelected(false);
        }
        if (actuaMediaFile.getStackPos() == 1) {
            actuaMediaFile.setSelected(true);
        } else {
            actuaMediaFile.setSelected(false);
        }
        executor.submit(() -> {
            selectedMediaFile.saveEdits();
            actuaMediaFile.saveEdits();
        });
        sortedMediaList.setComparator(stackNameComparator);
        lighttableController.updateSortFiltering();
        ObservableList<MediaFile> fullMediaList = lighttableController.getFullMediaList();
        fullMediaList.set(fullMediaList.indexOf(selectedMediaFile), selectedMediaFile);
    }

    public boolean isChanged() {
        return changed;
    }

    public MediaGridCell getMediaCellForMediaFile(MediaFile input) {
        VirtualFlow vf = (VirtualFlow) lighttableController.getImageGrid().getChildrenUnmodifiable().get(0);
        for (int i = 0; i < vf.getCellCount(); i++) {
            for (Node mediaCell : vf.getCell(i).getChildrenUnmodifiable()) {
                if (((MediaGridCell) mediaCell).getItem().getName().equalsIgnoreCase(input.getName())) {
                    return (MediaGridCell) mediaCell;
                }
            }
        }
        return null;
    }

}
