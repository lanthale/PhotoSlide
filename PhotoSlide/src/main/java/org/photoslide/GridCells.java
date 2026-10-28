package org.photoslide;

import javafx.scene.Node;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.skin.VirtualFlow;
import org.controlsfx.control.GridView;
import org.photoslide.datamodel.MediaFile;

/**
 * Finds the cell of a media file in a GridView without touching rows that
 * are not shown. VirtualFlow.getCell(i) creates or updates a row for any
 * index, so looping over all rows of a large list (10000+ files) renders
 * thousands of rows with their thumbnails.
 */
public final class GridCells {

    private GridCells() {
    }

    private static VirtualFlow<?> flow(GridView<?> grid) {
        if (grid.getChildrenUnmodifiable().isEmpty()
                || !(grid.getChildrenUnmodifiable().get(0) instanceof VirtualFlow<?> vf)) {
            return null;
        }
        return vf;
    }

    /**
     * @return the cell showing the file; the grid is scrolled to it if it is
     * not visible. null if it cannot be found.
     */
    public static <T extends Node> T find(GridView<MediaFile> grid, MediaFile input, Class<T> type) {
        VirtualFlow<?> vf = flow(grid);
        if (vf == null || input == null) {
            return null;
        }
        T cell = findVisible(vf, input, type);
        if (cell != null) {
            return cell;
        }
        int perRow = itemsPerRow(vf);
        int index = grid.getItems().indexOf(input);
        if (index < 0 || perRow <= 0) {
            return null;
        }
        vf.scrollTo(index / perRow);
        grid.layout();
        return findVisible(vf, input, type);
    }

    /** @return the shown row that contains the node, or null */
    public static IndexedCell<?> visibleRowOf(GridView<?> grid, Node cell) {
        VirtualFlow<?> vf = flow(grid);
        if (vf == null || vf.getFirstVisibleCell() == null) {
            return null;
        }
        for (int i = vf.getFirstVisibleCell().getIndex(); i <= vf.getLastVisibleCell().getIndex(); i++) {
            IndexedCell<?> row = vf.getVisibleCell(i);
            if (row != null && row.getChildrenUnmodifiable().contains(cell)) {
                return row;
            }
        }
        return null;
    }

    private static <T extends Node> T findVisible(VirtualFlow<?> vf, MediaFile input, Class<T> type) {
        if (vf.getFirstVisibleCell() == null) {
            return null;
        }
        for (int i = vf.getFirstVisibleCell().getIndex(); i <= vf.getLastVisibleCell().getIndex(); i++) {
            IndexedCell<?> row = vf.getVisibleCell(i);
            if (row == null) {
                continue;
            }
            for (Node n : row.getChildrenUnmodifiable()) {
                if (type.isInstance(n) && n instanceof javafx.scene.control.Cell<?> c && c.getItem() instanceof MediaFile m
                        && m.getName().equalsIgnoreCase(input.getName())) {
                    return type.cast(n);
                }
            }
        }
        return null;
    }

    private static int itemsPerRow(VirtualFlow<?> vf) {
        IndexedCell<?> first = vf.getFirstVisibleCell();
        return first == null ? 0 : first.getChildrenUnmodifiable().size();
    }
}
