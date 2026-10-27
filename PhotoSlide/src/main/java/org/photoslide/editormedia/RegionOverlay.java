package org.photoslide.editormedia;

import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.text.Text;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ImagePreview16;
import org.photoslide.imageops.mask.Contour;
import org.photoslide.imageops.mask.Region;

/**
 * Layer over the editor image: shows the region (outlines, clone sources)
 * of one tool and lets the user click points or draw outlines on the image.
 * All coordinates given out are pixels of the original image. While nothing
 * is requested the layer lets all mouse events through.
 */
public final class RegionOverlay extends Pane {

    private static final Color OUTLINE = Color.web("#ffd24d");
    private static final Color SOURCE = Color.web("#4dd2ff");
    private static final Color TOOL_AREA = Color.web("#ff9f43");

    private final ImageView view;
    private final ImagePreview16 preview;
    private AbstractImageFilter shown;
    private Region selection = Region.EMPTY;
    private Consumer<Contour.Point> pointRequest;
    private Consumer<List<Contour.Point>> outlineRequest;
    private final List<Contour.Point> drawing = new ArrayList<>();

    public RegionOverlay(ImageView view, ImagePreview16 preview) {
        this.view = view;
        this.preview = preview;
        setPickOnBounds(false);
        setMouseTransparent(true);
        setFocusTraversable(true);
        addEventHandler(MouseEvent.MOUSE_CLICKED, this::clicked);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancel();
            } else if (e.getCode() == KeyCode.ENTER) {
                finishOutline();
            } else if (e.getCode() == KeyCode.BACK_SPACE && !drawing.isEmpty()) {
                drawing.remove(drawing.size() - 1);
                redraw();
            }
            e.consume();
        });
        view.boundsInParentProperty().addListener(o -> redraw());
        view.viewportProperty().addListener(o -> redraw());
        view.imageProperty().addListener(o -> redraw());
        widthProperty().addListener(o -> redraw());
        heightProperty().addListener(o -> redraw());
    }

    /** The current selection of the editor (drawn in yellow). */
    public void setSelection(Region selection) {
        this.selection = selection == null ? Region.EMPTY : selection;
        redraw();
    }

    /** Shows the area of this tool in addition to the selection (null = nothing). */
    public void show(AbstractImageFilter tool) {
        shown = tool;
        redraw();
    }

    /** The next click on the image is passed on (pixels of the original). */
    public void requestPoint(Consumer<Contour.Point> onPoint) {
        cancel();
        pointRequest = onPoint;
        arm();
    }

    /**
     * Draw an outline: click points, double click or Enter closes it,
     * Backspace removes the last point, Escape cancels.
     */
    public void requestOutline(Consumer<List<Contour.Point>> onOutline) {
        cancel();
        outlineRequest = onOutline;
        arm();
    }

    public boolean isBusy() {
        return pointRequest != null || outlineRequest != null;
    }

    /** Ends any request without result. */
    public void cancel() {
        pointRequest = null;
        outlineRequest = null;
        drawing.clear();
        setMouseTransparent(true);
        setPickOnBounds(false);
        setCursor(Cursor.DEFAULT);
        redraw();
    }

    private void arm() {
        setMouseTransparent(false);
        setPickOnBounds(true);
        setCursor(Cursor.CROSSHAIR);
        requestFocus();
        redraw();
    }

    private void clicked(MouseEvent e) {
        if (!isBusy()) {
            return;
        }
        e.consume();
        Contour.Point p = toOriginal(e.getX(), e.getY());
        if (p == null) {
            return;
        }
        if (pointRequest != null) {
            Consumer<Contour.Point> r = pointRequest;
            cancel();
            r.accept(p);
            return;
        }
        if (e.getButton() == MouseButton.SECONDARY) {
            if (!drawing.isEmpty()) {
                drawing.remove(drawing.size() - 1);
            }
        } else if (e.getClickCount() >= 2) {
            finishOutline();
            return;
        } else {
            drawing.add(p);
        }
        redraw();
    }

    private void finishOutline() {
        if (outlineRequest == null) {
            return;
        }
        List<Contour.Point> pts = List.copyOf(drawing);
        Consumer<List<Contour.Point>> r = outlineRequest;
        cancel();
        if (pts.size() >= 3) {
            r.accept(pts);
        }
    }

    // ------------------------------------------------------------------
    // coordinates
    // ------------------------------------------------------------------
    /** Shown image area in this layer's coordinates and the viewport. */
    private record Mapping(double x, double y, double k, Rectangle2D vp, double scale) {

        double toX(double ox) {
            return x + (ox * scale - vp.getMinX()) * k;
        }

        double toY(double oy) {
            return y + (oy * scale - vp.getMinY()) * k;
        }
    }

    private Mapping mapping() {
        if (view.getImage() == null || view.getScene() == null) {
            return null;
        }
        Rectangle2D vp = view.getViewport();
        if (vp == null) {
            vp = new Rectangle2D(0, 0, view.getImage().getWidth(), view.getImage().getHeight());
        }
        Bounds b = sceneToLocal(view.localToScene(view.getBoundsInLocal()));
        if (b == null || b.getWidth() <= 0 || vp.getWidth() <= 0) {
            return null;
        }
        return new Mapping(b.getMinX(), b.getMinY(), b.getWidth() / vp.getWidth(), vp, preview.getDisplayScale());
    }

    private Contour.Point toOriginal(double lx, double ly) {
        Mapping m = mapping();
        if (m == null) {
            return null;
        }
        double sx = m.vp().getMinX() + (lx - m.x()) / m.k();
        double sy = m.vp().getMinY() + (ly - m.y()) / m.k();
        if (sx < m.vp().getMinX() || sy < m.vp().getMinY() || sx > m.vp().getMaxX() || sy > m.vp().getMaxY()) {
            return null;
        }
        return new Contour.Point(sx / m.scale(), sy / m.scale());
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------
    public void redraw() {
        getChildren().clear();
        Mapping m = mapping();
        if (m == null) {
            return;
        }
        for (Contour c : selection.contours()) {
            getChildren().add(path(c, m, OUTLINE, false));
        }
        if (selection.inverted() && !selection.isEmpty()) {
            Text t = new Text(m.x() + 8, m.y() + 20, "selection inverted");
            t.setFill(OUTLINE);
            getChildren().add(t);
        }
        if (shown != null) {
            Region r = shown.getRegion();
            for (Contour c : r.contours()) {
                getChildren().add(path(c, m, c.hasSource() ? OUTLINE : TOOL_AREA, !c.hasSource()));
                if (c.hasSource()) {
                    Contour.Point t = c.center();
                    Line l = new Line(m.toX(c.sourceX()), m.toY(c.sourceY()), m.toX(t.x()), m.toY(t.y()));
                    l.setStroke(SOURCE);
                    l.getStrokeDashArray().addAll(4.0, 4.0);
                    Circle s = new Circle(m.toX(c.sourceX()), m.toY(c.sourceY()), 5, Color.TRANSPARENT);
                    s.setStroke(SOURCE);
                    s.setStrokeWidth(2);
                    getChildren().addAll(l, s);
                }
            }
            if (r.inverted() && !r.isEmpty()) {
                Text t = new Text(m.x() + 8, m.y() + 38, shown.displayName() + ": outside of the outlines");
                t.setFill(TOOL_AREA);
                getChildren().add(t);
            }
        }
        if (!drawing.isEmpty()) {
            Polyline line = new Polyline();
            for (Contour.Point p : drawing) {
                line.getPoints().addAll(m.toX(p.x()), m.toY(p.y()));
            }
            line.setStroke(Color.WHITE);
            line.setStrokeWidth(1.5);
            getChildren().add(line);
            for (Contour.Point p : drawing) {
                getChildren().add(new Circle(m.toX(p.x()), m.toY(p.y()), 3, Color.WHITE));
            }
        }
    }

    private static Path path(Contour c, Mapping m, Color color, boolean dashed) {
        double offX = m.vp().getMinX() * m.k() - m.x();
        double offY = m.vp().getMinY() * m.k() - m.y();
        java.awt.geom.Path2D shape = c.shape(m.scale() * m.k(), offX, offY);
        Path path = new Path();
        double[] co = new double[6];
        for (PathIterator it = shape.getPathIterator(null); !it.isDone(); it.next()) {
            switch (it.currentSegment(co)) {
                case PathIterator.SEG_MOVETO -> path.getElements().add(new MoveTo(co[0], co[1]));
                case PathIterator.SEG_LINETO -> path.getElements().add(new LineTo(co[0], co[1]));
                case PathIterator.SEG_QUADTO -> path.getElements().add(new QuadCurveTo(co[0], co[1], co[2], co[3]));
                case PathIterator.SEG_CUBICTO -> path.getElements().add(new CubicCurveTo(co[0], co[1], co[2], co[3], co[4], co[5]));
                case PathIterator.SEG_CLOSE -> path.getElements().add(new ClosePath());
                default -> {
                }
            }
        }
        path.setStroke(color);
        path.setStrokeWidth(1.5);
        if (dashed) {
            path.getStrokeDashArray().addAll(6.0, 4.0);
        }
        path.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.08));
        path.setMouseTransparent(true);
        return path;
    }
}
