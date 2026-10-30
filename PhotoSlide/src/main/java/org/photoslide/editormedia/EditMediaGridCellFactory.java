/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.editormedia;

import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import org.photoslide.GridCells;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;
import org.photoslide.datamodel.GridCellSelectionModel;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.datamodel.MediaFileLoader;
import org.photoslide.datamodel.MediaGridCell;
import org.photoslide.imageops.ImageFilter;

/**
 *
 * @author selfemp
 */
public class EditMediaGridCellFactory implements Callback<GridView<MediaFile>, GridCell<MediaFile>> {

    private EditMediaGridCell selectedCell;
    private MediaFile selectedMediaFile;
    private final Comparator<MediaFile> stackNameComparator;
    private final ExecutorService executor;
    private final EditorMediaViewController mediaViewController;
    private final GridCellSelectionModel selectionModel;
    private final MediaFileLoader fileLoader;
    private Task<Object> task;

    public EditMediaGridCellFactory(ExecutorService executor, EditorMediaViewController controller) {
        this.mediaViewController = controller;
        stackNameComparator = Comparator.comparing(MediaFile::getStackPos);
        this.executor = executor;
        selectionModel = new GridCellSelectionModel();
        fileLoader = new MediaFileLoader();
    }

    @Override
    public GridCell<MediaFile> call(GridView<MediaFile> p) {
        EditMediaGridCell cell = new EditMediaGridCell();
        cell.setAlignment(Pos.CENTER);
        cell.setEditable(false);
        cell.setOnMouseClicked((t) -> {
            manageGUISelection(t, cell);
            handleGridCellSelection(t);
            t.consume();
        });
        cell.itemProperty().addListener((ov, oldMediaItem, newMediaItem) -> {
            if (oldMediaItem != null && oldMediaItem != newMediaItem && oldMediaItem.isLoading()) {
                // the cell was reused for another file (scrolling): drop the request
                // of the file it does not show any more, if it has not started yet
                fileLoader.cancel(oldMediaItem);
            }
            // on every new item, not only the first: a reused cell may now show a
            // file whose request was dropped above
            if (newMediaItem != null) {
                // checked after the running layout: items that arrive before the
                // first layout find no visible cell (see MediaGridCellFactory)
                Platform.runLater(() -> {
                    if (cell.getItem() == newMediaItem && newMediaItem.isLoading() == true) {
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
                });
            }
        });
        return cell;
    }

    private void manageGUISelection(MouseEvent t, EditMediaGridCell cell) {
        mediaViewController.getFullMediaList().stream().filter(c -> c != null && c.isSelected() == true).forEach((mfile) -> {
            mfile.setSelected(false);
        });
        selectedMediaFile = ((EditMediaGridCell) t.getSource()).getItem();
        selectionModel.clear();
        selectionModel.add(((EditMediaGridCell) t.getSource()).getItem());
        selectedCell = cell;
        cell.requestLayout();
    }

    private void handleGridCellSelection(MouseEvent t) {
        selectedMediaFile = ((EditMediaGridCell) t.getSource()).getItem();
        selectedCell = (EditMediaGridCell) t.getSource();
        mediaViewController.setMediaFileForEdit(selectedMediaFile);
    }

    public MediaFile getSelectedMediaFile() {
        return selectedMediaFile;
    }

    public EditMediaGridCell getMediaCellForMediaFile(MediaFile input) {
        return GridCells.find(mediaViewController.getImageGrid(), input, EditMediaGridCell.class);
    }

    public boolean isCellVisible(EditMediaGridCell input) {
        VirtualFlow vf = (VirtualFlow) mediaViewController.getImageGrid().getChildrenUnmodifiable().get(0);
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
            javafx.scene.control.IndexedCell<?> row = vf.getVisibleCell(i);
            if (row != null && row.getChildrenUnmodifiable().contains(input)) {
                return true;
            }
        }
        return ret;
    }

    public void shutdown() {
        fileLoader.shutdown();
    }

    public GridCellSelectionModel getSelectionModel() {
        return selectionModel;
    }

}
