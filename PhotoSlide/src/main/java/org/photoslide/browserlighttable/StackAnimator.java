package org.photoslide.browserlighttable;

import java.util.ArrayList;
import java.util.List;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.util.Duration;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.datamodel.MediaGridCell;

/**
 * Animates stacking in the lighttable grid: the photos of a new stack slide
 * into its first photo, and when a stack is dissolved they slide out of it
 * to their own places again. Pictures of the cells fly in a layer above the
 * grid (unmanaged children of the pane holding the grid) while the real
 * cells are hidden. Photos that are not visible in the grid are not
 * animated. JavaFX thread.
 */
final class StackAnimator {

    private static final Duration MOVE = Duration.millis(380);
    private static final Duration STAGGER = Duration.millis(35);

    private final Pane layer;
    private final MediaGridCellFactory factory;
    private final Node grid;
    private boolean running;

    StackAnimator(Pane layer, Node grid, MediaGridCellFactory factory) {
        this.layer = layer;
        this.grid = grid;
        this.factory = factory;
    }

    boolean isRunning() {
        return running;
    }

    /**
     * Moves the cells of the files into the cell of the target, then runs
     * change (which hides the stacked photos).
     */
    void collapse(MediaFile target, List<MediaFile> files, Runnable change) {
        MediaGridCell targetCell = factory.getMediaCellForMediaFile(target);
        if (targetCell == null) {
            change.run();
            return;
        }
        Point2D to = topLeft(targetCell);
        List<Node> ghosts = new ArrayList<>();
        List<MediaGridCell> hidden = new ArrayList<>();
        ParallelTransition all = new ParallelTransition();
        int n = 0;
        for (MediaFile f : files) {
            MediaGridCell cell = f == target ? null : factory.getMediaCellForMediaFile(f);
            if (cell == null) {
                continue;
            }
            Point2D from = topLeft(cell);
            ImageView ghost = ghostOf(cell, from);
            ghosts.add(ghost);
            cell.setOpacity(0);
            hidden.add(cell);

            TranslateTransition move = new TranslateTransition(MOVE, ghost);
            move.setToX(to.getX() - from.getX());
            move.setToY(to.getY() - from.getY());
            move.setInterpolator(Interpolator.EASE_BOTH);
            ScaleTransition shrink = new ScaleTransition(MOVE, ghost);
            shrink.setToX(0.85);
            shrink.setToY(0.85);
            FadeTransition fade = new FadeTransition(MOVE, ghost);
            fade.setFromValue(1);
            fade.setToValue(0.15);
            fade.setInterpolator(Interpolator.EASE_IN);
            ParallelTransition one = new ParallelTransition(move, shrink, fade);
            one.setDelay(STAGGER.multiply(n++));
            all.getChildren().add(one);
        }
        if (ghosts.isEmpty()) {
            change.run();
            return;
        }
        SequentialTransition total = new SequentialTransition(all, bump(targetCell));
        running = true;
        all.setOnFinished(e -> {
            layer.getChildren().removeAll(ghosts);
            hidden.forEach(c -> c.setOpacity(1));
            change.run();
        });
        total.setOnFinished(e -> running = false);
        total.play();
    }

    /**
     * Runs change (which shows the photos of the stack again), then moves
     * the photos from the place of the stack out to their own cells.
     */
    void expand(MediaFile top, List<MediaFile> files, Runnable change) {
        MediaGridCell topCell = factory.getMediaCellForMediaFile(top);
        if (topCell == null) {
            change.run();
            return;
        }
        Point2D from = topLeft(topCell);
        change.run();
        // the grid creates the cells of the new items on the next layout: do it now
        grid.applyCss();
        if (grid instanceof javafx.scene.Parent p) {
            p.layout();
        }
        List<Node> ghosts = new ArrayList<>();
        List<MediaGridCell> hidden = new ArrayList<>();
        ParallelTransition all = new ParallelTransition();
        int n = 0;
        for (MediaFile f : files) {
            MediaGridCell cell = factory.getMediaCellForMediaFile(f);
            if (cell == null) {
                continue;
            }
            Point2D to = topLeft(cell);
            if (to.distance(from) < 1) {
                continue;                       // stays where the stack was
            }
            ImageView ghost = ghostOf(cell, from);
            ghost.setOpacity(0.15);
            ghost.setScaleX(0.85);
            ghost.setScaleY(0.85);
            ghosts.add(ghost);
            cell.setOpacity(0);
            hidden.add(cell);

            TranslateTransition move = new TranslateTransition(MOVE, ghost);
            move.setToX(to.getX() - from.getX());
            move.setToY(to.getY() - from.getY());
            move.setInterpolator(Interpolator.EASE_BOTH);
            ScaleTransition grow = new ScaleTransition(MOVE, ghost);
            grow.setToX(1);
            grow.setToY(1);
            FadeTransition fade = new FadeTransition(MOVE, ghost);
            fade.setToValue(1);
            fade.setInterpolator(Interpolator.EASE_OUT);
            ParallelTransition one = new ParallelTransition(move, grow, fade);
            one.setDelay(STAGGER.multiply(n++));
            all.getChildren().add(one);
        }
        if (ghosts.isEmpty()) {
            return;
        }
        running = true;
        all.setOnFinished(e -> {
            layer.getChildren().removeAll(ghosts);
            hidden.forEach(c -> c.setOpacity(1));
            running = false;
        });
        all.play();
    }

    /** Short pulse of the cell that took the photos. */
    private static ScaleTransition bump(Node cell) {
        ScaleTransition s = new ScaleTransition(Duration.millis(110), cell);
        s.setFromX(1);
        s.setFromY(1);
        s.setToX(1.06);
        s.setToY(1.06);
        s.setAutoReverse(true);
        s.setCycleCount(2);
        return s;
    }

    /** Picture of the cell, placed at the point in the layer. */
    private ImageView ghostOf(MediaGridCell cell, Point2D at) {
        double scale = 1;
        if (layer.getScene() != null && layer.getScene().getWindow() != null) {
            scale = Math.max(1, layer.getScene().getWindow().getOutputScaleX());
        }
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        params.setTransform(Transform.scale(scale, scale));
        WritableImage img = cell.snapshot(params, null);
        ImageView ghost = new ImageView(img);
        ghost.setFitWidth(img.getWidth() / scale);
        ghost.setFitHeight(img.getHeight() / scale);
        ghost.setManaged(false);
        ghost.setMouseTransparent(true);
        layer.getChildren().add(ghost);
        ghost.relocate(at.getX(), at.getY());
        return ghost;
    }

    /** Top left corner of the cell in the coordinates of the layer. */
    private Point2D topLeft(Node cell) {
        Bounds b = cell.localToScene(cell.getBoundsInLocal());
        return layer.sceneToLocal(b.getMinX(), b.getMinY());
    }
}
