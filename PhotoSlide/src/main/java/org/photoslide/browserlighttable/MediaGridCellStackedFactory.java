/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browserlighttable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.IndexedCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import org.photoslide.GridCells;
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
    /** The photo chosen in the stack view (cells are reused by position, so the file is kept). */
    private MediaFile selectedItem;
    /** The photo being dragged to another place in the stack, null if none. */
    private MediaFile dragged;
    private static final javafx.util.Duration REORDER = javafx.util.Duration.millis(260);

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
        installDragAndDrop(cell);
        cell.itemProperty().addListener((ov, oldMediaItem, newMediaItem) -> {
            if (oldMediaItem != null && oldMediaItem != newMediaItem && oldMediaItem.isLoading()) {
                // the cell was reused for another file (scrolling): drop the request
                // of the file it does not show any more, if it has not started yet
                fileLoader.cancel(oldMediaItem);
            }
            // on every new item, not only the first: a reused cell may now show a
            // file whose request was dropped above
            if (newMediaItem != null) {
                // checked after the running layout: the first row gets its items
                // before the flow knows any visible cell (see MediaGridCellFactory)
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

    private void manageGUISelection(MouseEvent t, MediaGridCellStackedDetailView cell) {
        sortedMediaList.forEach((mFile) -> {
            mFile.setSubViewSelected(false);
        });
        cell.getItem().setSubViewSelected(true);
        cell.requestLayout();
        selectedCell = cell;
        selectedItem = cell.getItem();
    }

    private void handleGridCellSelection(MouseEvent t) {

    }

    void setOnFrontImageButtonAction(ActionEvent t) {
        moveSelected(1);
    }

    void orderOneDownButtonAction(ActionEvent t) {
        if (selectedItem != null) {
            moveSelected(sortedMediaList.indexOf(selectedItem) + 2);
        }
    }

    void orderOneUpButtonAction(ActionEvent t) {
        if (selectedItem != null) {
            moveSelected(sortedMediaList.indexOf(selectedItem));
        }
    }

    void orderToTheButtomButtonAction(ActionEvent t) {
        moveSelected(sortedMediaList.size());
    }

    /** Swaps the chosen photo with the one at the position (1 = top), animated. */
    private void moveSelected(int newPos) {
        if (selectedItem == null || !sortedMediaList.contains(selectedItem)
                || newPos < 1 || newPos > sortedMediaList.size()) {
            return;
        }
        lighttableController.getImageView().setImage(null);
        MediaFile item = selectedItem;
        animateReorder(() -> changeStackPosition(newPos, item));
        changed = true;
    }

    // ------------------------------------------------------------------
    // drag and drop: a photo is dropped onto another and takes its place,
    // the photos in between move up or down by one
    // ------------------------------------------------------------------
    private void installDragAndDrop(MediaGridCellStackedDetailView cell) {
        cell.setOnDragDetected(e -> {
            if (cell.getItem() == null || sortedMediaList.size() < 2) {
                return;
            }
            dragged = cell.getItem();
            Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(dragged.getName());
            db.setContent(content);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            db.setDragView(cell.snapshot(params, null), e.getX(), e.getY());
            cell.setOpacity(0.35);
            e.consume();
        });
        cell.setOnDragOver(e -> {
            if (dragged != null && cell.getItem() != null && cell.getItem() != dragged) {
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });
        cell.setOnDragEntered(e -> {
            if (dragged != null && cell.getItem() != null && cell.getItem() != dragged) {
                cell.setStyle("-fx-border-color: -fx-accent; -fx-border-width: 2;");
            }
            e.consume();
        });
        cell.setOnDragExited(e -> {
            cell.setStyle(null);
            e.consume();
        });
        cell.setOnDragDropped(e -> {
            boolean done = false;
            MediaFile source = dragged;
            MediaFile target = cell.getItem();
            if (source != null && target != null && source != target) {
                int to = sortedMediaList.indexOf(target);
                lighttableController.getImageView().setImage(null);
                animateReorder(() -> moveTo(source, to));
                changed = true;
                done = true;
            }
            cell.setStyle(null);
            e.setDropCompleted(done);
            e.consume();
        });
        cell.setOnDragDone(e -> {
            dragged = null;
            for (MediaGridCellStackedDetailView c : visibleCells()) {
                c.setOpacity(1);
                c.setStyle(null);
            }
            e.consume();
        });
    }

    /** Moves the file to the index of the stack; the photos in between shift by one. */
    private void moveTo(MediaFile file, int index) {
        List<MediaFile> order = new ArrayList<>(sortedMediaList);
        if (!order.remove(file)) {
            return;
        }
        order.add(Math.max(0, Math.min(index, order.size())), file);
        List<MediaFile> changedFiles = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            MediaFile m = order.get(i);
            if (m.getStackPos() != i + 1) {
                m.setStackPos(i + 1);
                changedFiles.add(m);
            }
            m.setSelected(i == 0);
        }
        executor.submit(() -> changedFiles.forEach(MediaFile::saveEdits));
        resort();
    }

    /** Sorts the stack view again and lets the lighttable show the new top photo. */
    private void resort() {
        // the stack position is not in the list's extractor: force a full sort
        sortedMediaList.setComparator(null);
        sortedMediaList.setComparator(stackNameComparator);
        lighttableController.updateSortFiltering();
    }

    /**
     * Runs the change of the order, then lets every visible photo slide from
     * its old place to its new one.
     */
    private void animateReorder(Runnable change) {
        Map<MediaFile, Point2D> before = new HashMap<>();
        for (MediaGridCellStackedDetailView c : visibleCells()) {
            if (c.getItem() != null) {
                before.put(c.getItem(), scenePos(c));
            }
        }
        change.run();
        mediaGrid.applyCss();
        mediaGrid.layout();
        for (MediaGridCellStackedDetailView c : visibleCells()) {
            Point2D old = c.getItem() == null ? null : before.get(c.getItem());
            c.setTranslateX(0);
            c.setTranslateY(0);
            c.setOpacity(1);
            if (old == null) {
                continue;
            }
            Point2D now = scenePos(c);
            double dx = old.getX() - now.getX();
            double dy = old.getY() - now.getY();
            if (Math.abs(dx) < 1 && Math.abs(dy) < 1) {
                continue;
            }
            c.setTranslateX(dx);
            c.setTranslateY(dy);
            TranslateTransition move = new TranslateTransition(REORDER, c);
            move.setToX(0);
            move.setToY(0);
            move.setInterpolator(Interpolator.EASE_BOTH);
            move.play();
        }
    }

    private static Point2D scenePos(Node n) {
        Bounds b = n.localToScene(n.getBoundsInLocal());
        return new Point2D(b.getMinX(), b.getMinY());
    }

    /** The cells of the stack view that are shown now. */
    private List<MediaGridCellStackedDetailView> visibleCells() {
        List<MediaGridCellStackedDetailView> cells = new ArrayList<>();
        if (mediaGrid.getChildrenUnmodifiable().isEmpty()
                || !(mediaGrid.getChildrenUnmodifiable().get(0) instanceof VirtualFlow<?> vf)
                || vf.getFirstVisibleCell() == null) {
            return cells;
        }
        for (int i = vf.getFirstVisibleCell().getIndex(); i <= vf.getLastVisibleCell().getIndex(); i++) {
            IndexedCell<?> row = vf.getVisibleCell(i);
            if (row == null) {
                continue;
            }
            for (Node n : row.getChildrenUnmodifiable()) {
                if (n instanceof MediaGridCellStackedDetailView c) {
                    cells.add(c);
                }
            }
        }
        return cells;
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
            javafx.scene.control.IndexedCell<?> row = vf.getVisibleCell(i);
            if (row != null && row.getChildrenUnmodifiable().contains(input)) {
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
        resort();
    }

    public boolean isChanged() {
        return changed;
    }

    public MediaGridCell getMediaCellForMediaFile(MediaFile input) {
        return GridCells.find(lighttableController.getImageGrid(), input, MediaGridCell.class);
    }

}
