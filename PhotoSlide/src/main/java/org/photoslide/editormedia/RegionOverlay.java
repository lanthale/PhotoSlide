package org.photoslide.editormedia;

import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
    private Consumer<double[]> boxRequest;
    private boolean boxEllipse;
    private BiConsumer<Contour.Point, Double> circleRequest;
    private double circleDefault;
    /** clicked points (outline) or dragged points (freehand) */
    private final List<Contour.Point> drawing = new ArrayList<>();
    private Contour.Point dragStart;
    private Contour.Point dragNow;
    private Contour.Point hover;
    private boolean freehand;
    private double lastDragX;
    private double lastDragY;

    /** Editing the selection with the mouse: which outline, which handle. */
    private record Hit(int contour, int handle) {

        static final int MOVE = 4;
    }
    private Consumer<Region> selectionEdited;
    private Hit edit;
    private Contour editOriginal;
    private Contour.Point editStart;
    private javafx.scene.Node editPreview;

    public RegionOverlay(ImageView view, ImagePreview16 preview) {
        this.view = view;
        this.preview = preview;
        // idle: only the selection outlines and their handles take the mouse,
        // all other clicks go to the image
        setPickOnBounds(false);
        setFocusTraversable(true);
        addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragged);
        addEventHandler(MouseEvent.MOUSE_RELEASED, this::released);
        addEventHandler(MouseEvent.MOUSE_CLICKED, this::clicked);
        addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            if (outlineRequest != null && !drawing.isEmpty()) {
                hover = toOriginal(e.getX(), e.getY(), true);
                redraw();
            }
        });
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

    /**
     * Called when the user moved or resized an outline of the selection
     * (with the new selection).
     */
    public void setOnSelectionEdited(Consumer<Region> listener) {
        this.selectionEdited = listener;
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
     * Draw an outline: drag with the mouse button held (freehand, closes on
     * release), or click points and close with a double click or Enter.
     * Backspace removes the last point, Escape cancels.
     */
    public void requestOutline(Consumer<List<Contour.Point>> onOutline) {
        cancel();
        outlineRequest = onOutline;
        arm();
    }

    /**
     * Drag a rectangle or ellipse (press, drag, release).
     *
     * @param onBox receives {x0, y0, x1, y1} with x0 < x1, y0 < y1
     */
    public void requestBox(boolean ellipse, Consumer<double[]> onBox) {
        cancel();
        boxRequest = onBox;
        boxEllipse = ellipse;
        arm();
    }

    /**
     * Click for a circle of the default radius, or press on the centre and
     * drag to set the radius.
     */
    public void requestCircle(double defaultRadius, BiConsumer<Contour.Point, Double> onCircle) {
        cancel();
        circleRequest = onCircle;
        circleDefault = defaultRadius;
        arm();
    }

    public boolean isBusy() {
        return pointRequest != null || outlineRequest != null || boxRequest != null || circleRequest != null;
    }

    /** Ends any request without result. */
    public void cancel() {
        pointRequest = null;
        outlineRequest = null;
        boxRequest = null;
        circleRequest = null;
        drawing.clear();
        dragStart = null;
        dragNow = null;
        hover = null;
        freehand = false;
        setPickOnBounds(false);
        setCursor(Cursor.DEFAULT);
        redraw();
    }

    private void arm() {
        setPickOnBounds(true);
        setCursor(Cursor.CROSSHAIR);
        requestFocus();
        redraw();
    }

    private void pressed(MouseEvent e) {
        if (!isBusy() && e.getButton() == MouseButton.PRIMARY && e.getTarget() instanceof javafx.scene.Node n
                && n.getUserData() instanceof Hit hit && hit.contour() < selection.contours().size()) {
            e.consume();
            edit = hit;
            editOriginal = selection.contours().get(hit.contour());
            editStart = toOriginal(e.getX(), e.getY(), true);
            return;
        }
        if (!isBusy() || e.getButton() != MouseButton.PRIMARY) {
            return;
        }
        e.consume();
        Contour.Point p = toOriginal(e.getX(), e.getY(), false);
        if (p == null) {
            return;
        }
        dragStart = p;
        dragNow = p;
        lastDragX = e.getX();
        lastDragY = e.getY();
        freehand = false;
    }

    private void dragged(MouseEvent e) {
        if (edit != null) {
            e.consume();
            Contour c = edited(toOriginal(e.getX(), e.getY(), true));
            ViewMapping m = mapping();
            if (c != null && m != null) {
                // only the preview node changes: the pressed node must stay in the scene
                getChildren().remove(editPreview);
                editPreview = path(c, m, Color.WHITE, true);
                getChildren().add(editPreview);
            }
            return;
        }
        if (!isBusy() || dragStart == null) {
            return;
        }
        e.consume();
        dragNow = toOriginal(e.getX(), e.getY(), true);
        if (outlineRequest != null) {
            if (!freehand && drawing.isEmpty()) {
                freehand = true;                // dragging starts a freehand outline
                drawing.add(dragStart);
            }
            if (freehand && Math.hypot(e.getX() - lastDragX, e.getY() - lastDragY) >= 3) {
                drawing.add(dragNow);
                lastDragX = e.getX();
                lastDragY = e.getY();
            }
        }
        redraw();
    }

    private void released(MouseEvent e) {
        if (edit != null) {
            e.consume();
            Contour c = edited(toOriginal(e.getX(), e.getY(), true));
            int index = edit.contour();
            edit = null;
            editPreview = null;
            if (c != null && !c.equals(editOriginal)) {
                List<Contour> l = new ArrayList<>(selection.contours());
                l.set(index, c);
                selection = selection.withContours(l);
                if (selectionEdited != null) {
                    selectionEdited.accept(selection);
                }
            }
            redraw();
            return;
        }
        if (!isBusy() || dragStart == null) {
            return;
        }
        e.consume();
        Contour.Point a = dragStart;
        Contour.Point b = toOriginal(e.getX(), e.getY(), true);
        dragStart = null;
        dragNow = null;
        if (b == null) {
            redraw();
            return;
        }
        if (boxRequest != null) {
            double[] box = {Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.max(a.x(), b.x()), Math.max(a.y(), b.y())};
            if (box[2] - box[0] < 2 || box[3] - box[1] < 2) {
                redraw();                       // a click, not a box: keep waiting
                return;
            }
            Consumer<double[]> r = boxRequest;
            cancel();
            r.accept(box);
        } else if (circleRequest != null) {
            double radius = Math.hypot(b.x() - a.x(), b.y() - a.y());
            BiConsumer<Contour.Point, Double> r = circleRequest;
            cancel();
            r.accept(a, radius < 2 ? circleDefault : radius);
        } else if (outlineRequest != null && freehand) {
            drawing.add(b);
            finishOutline();
        } else {
            redraw();
        }
    }

    private void clicked(MouseEvent e) {
        if (!isBusy()) {
            return;
        }
        e.consume();
        if (!e.isStillSincePress() || boxRequest != null || circleRequest != null || freehand) {
            return;                             // handled by the drag
        }
        Contour.Point p = toOriginal(e.getX(), e.getY(), false);
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

    /** The outline being edited, moved or resized to the mouse position p. */
    private Contour edited(Contour.Point p) {
        if (edit == null || p == null || editStart == null) {
            return null;
        }
        List<Contour.Point> out = new ArrayList<>();
        if (edit.handle() == Hit.MOVE) {
            double dx = p.x() - editStart.x(), dy = p.y() - editStart.y();
            for (Contour.Point q : editOriginal.points()) {
                out.add(new Contour.Point(q.x() + dx, q.y() + dy));
            }
        } else {
            double[] b = bounds(editOriginal);
            double[][] corners = {{b[0], b[1]}, {b[2], b[1]}, {b[2], b[3]}, {b[0], b[3]}};
            double[] corner = corners[edit.handle()];
            double[] anchor = corners[(edit.handle() + 2) % 4];
            double sx = scale(p.x() - anchor[0], corner[0] - anchor[0]);
            double sy = scale(p.y() - anchor[1], corner[1] - anchor[1]);
            for (Contour.Point q : editOriginal.points()) {
                out.add(new Contour.Point(anchor[0] + (q.x() - anchor[0]) * sx, anchor[1] + (q.y() - anchor[1]) * sy));
            }
        }
        return new Contour(out, editOriginal.feather(), editOriginal.smooth(), editOriginal.sourceX(), editOriginal.sourceY());
    }

    private static double scale(double now, double was) {
        if (Math.abs(was) < 1e-9) {
            return 1;
        }
        double s = now / was;
        return Math.abs(s) < 0.01 ? Math.copySign(0.01, s == 0 ? 1 : s) : s;
    }

    /** {minX, minY, maxX, maxY} of an outline. */
    private static double[] bounds(Contour c) {
        double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Contour.Point q : c.points()) {
            b[0] = Math.min(b[0], q.x());
            b[1] = Math.min(b[1], q.y());
            b[2] = Math.max(b[2], q.x());
            b[3] = Math.max(b[3], q.y());
        }
        return b;
    }

    private void finishOutline() {
        if (outlineRequest == null) {
            return;
        }
        List<Contour.Point> pts = thin(drawing);
        Consumer<List<Contour.Point>> r = outlineRequest;
        cancel();
        if (pts.size() >= 3) {
            r.accept(pts);
        }
    }

    /** At most 200 points (freehand outlines can have many). */
    private static List<Contour.Point> thin(List<Contour.Point> pts) {
        if (pts.size() <= 200) {
            return List.copyOf(pts);
        }
        List<Contour.Point> out = new ArrayList<>();
        double step = pts.size() / 200.0;
        for (double i = 0; i < pts.size(); i += step) {
            out.add(pts.get((int) i));
        }
        return out;
    }

    // ------------------------------------------------------------------
    // coordinates
    // ------------------------------------------------------------------
    private ViewMapping mapping() {
        return ViewMapping.of(view, preview, this);
    }

    /**
     * @param clamp true = points outside the image are moved to its edge
     * (while dragging), false = they give null
     */
    private Contour.Point toOriginal(double lx, double ly, boolean clamp) {
        ViewMapping m = mapping();
        if (m == null) {
            return null;
        }
        double[] p = m.fromLayer(lx, ly, clamp);
        return p == null ? null : new Contour.Point(p[0], p[1]);
    }

    /** Pixel of the original under the scene position, or null outside the shown photo. */
    public Contour.Point originalAt(double sceneX, double sceneY) {
        javafx.geometry.Point2D l = sceneToLocal(sceneX, sceneY);
        return l == null ? null : toOriginal(l.getX(), l.getY(), false);
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------
    public void redraw() {
        if (edit != null) {
            return;                             // keep the pressed node while dragging
        }
        getChildren().clear();
        ViewMapping m = mapping();
        if (m == null) {
            return;
        }
        boolean editable = !isBusy() && selectionEdited != null;
        for (int i = 0; i < selection.contours().size(); i++) {
            Contour c = selection.contours().get(i);
            Path outline = path(c, m, OUTLINE, false);
            if (editable) {
                outline.setMouseTransparent(false);
                outline.setCursor(Cursor.MOVE);
                outline.setUserData(new Hit(i, Hit.MOVE));
            }
            getChildren().add(outline);
            if (editable) {
                double[] b = bounds(c);
                double[][] corners = {{b[0], b[1]}, {b[2], b[1]}, {b[2], b[3]}, {b[0], b[3]}};
                Cursor[] cursors = {Cursor.NW_RESIZE, Cursor.NE_RESIZE, Cursor.SE_RESIZE, Cursor.SW_RESIZE};
                for (int h = 0; h < 4; h++) {
                    javafx.geometry.Point2D q = m.toLayer(corners[h][0], corners[h][1]);
                    javafx.scene.shape.Rectangle handle = new javafx.scene.shape.Rectangle(
                            q.getX() - 5, q.getY() - 5, 10, 10);
                    handle.setFill(Color.WHITE);
                    handle.setStroke(Color.web("#333333"));
                    handle.setCursor(cursors[h]);
                    handle.setUserData(new Hit(i, h));
                    getChildren().add(handle);
                }
            }
        }
        if (selection.inverted() && !selection.isEmpty()) {
            Text t = new Text(labelX(m), labelY(m) + 20, "selection inverted");
            t.setFill(OUTLINE);
            t.setMouseTransparent(true);
            getChildren().add(t);
        }
        if (shown != null) {
            Region r = shown.getRegion();
            for (Contour c : r.contours()) {
                getChildren().add(path(c, m, c.hasSource() ? OUTLINE : TOOL_AREA, !c.hasSource()));
                if (c.hasSource()) {
                    Contour.Point t = c.center();
                    javafx.geometry.Point2D from = m.toLayer(c.sourceX(), c.sourceY());
                    javafx.geometry.Point2D to = m.toLayer(t.x(), t.y());
                    Line l = new Line(from.getX(), from.getY(), to.getX(), to.getY());
                    l.setStroke(SOURCE);
                    l.getStrokeDashArray().addAll(4.0, 4.0);
                    Circle s = new Circle(from.getX(), from.getY(), 5, Color.TRANSPARENT);
                    s.setStroke(SOURCE);
                    s.setStrokeWidth(2);
                    l.setMouseTransparent(true);
                    s.setMouseTransparent(true);
                    getChildren().addAll(l, s);
                }
            }
            if (r.inverted() && !r.isEmpty()) {
                Text t = new Text(labelX(m), labelY(m) + 38, shown.displayName() + ": outside of the outlines");
                t.setFill(TOOL_AREA);
                t.setMouseTransparent(true);
                getChildren().add(t);
            }
        }
        if (!drawing.isEmpty()) {
            Polyline line = new Polyline();
            for (Contour.Point p : drawing) {
                javafx.geometry.Point2D q = m.toLayer(p.x(), p.y());
                line.getPoints().addAll(q.getX(), q.getY());
            }
            if (!freehand && hover != null) {
                javafx.geometry.Point2D q = m.toLayer(hover.x(), hover.y());
                line.getPoints().addAll(q.getX(), q.getY());   // rubber band
            }
            line.setStroke(Color.WHITE);
            line.setStrokeWidth(1.5);
            line.setMouseTransparent(true);
            getChildren().add(line);
            if (!freehand) {
                for (Contour.Point p : drawing) {
                    javafx.geometry.Point2D q = m.toLayer(p.x(), p.y());
                    Circle c = new Circle(q.getX(), q.getY(), 3, Color.WHITE);
                    c.setMouseTransparent(true);
                    getChildren().add(c);
                }
            }
        }
        if (dragStart != null && dragNow != null && (boxRequest != null || circleRequest != null)) {
            javafx.geometry.Point2D q0 = m.toLayer(dragStart.x(), dragStart.y());
            javafx.geometry.Point2D q1 = m.toLayer(dragNow.x(), dragNow.y());
            double x0 = q0.getX(), y0 = q0.getY();
            double x1 = q1.getX(), y1 = q1.getY();
            javafx.scene.shape.Shape live;
            if (circleRequest != null) {
                live = new Circle(x0, y0, Math.hypot(x1 - x0, y1 - y0));
            } else if (boxEllipse) {
                live = new javafx.scene.shape.Ellipse((x0 + x1) / 2, (y0 + y1) / 2, Math.abs(x1 - x0) / 2, Math.abs(y1 - y0) / 2);
            } else {
                live = new javafx.scene.shape.Rectangle(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0));
            }
            live.setFill(Color.color(1, 1, 1, 0.08));
            live.setStroke(Color.WHITE);
            live.setStrokeWidth(1.5);
            live.getStrokeDashArray().addAll(5.0, 4.0);
            live.setMouseTransparent(true);
            getChildren().add(live);
        }
    }

    private static double labelX(ViewMapping m) {
        return Math.max(0, m.viewInLayer().getMinX()) + 8;
    }

    private static double labelY(ViewMapping m) {
        return Math.max(0, m.viewInLayer().getMinY());
    }

    private static Path path(Contour c, ViewMapping m, Color color, boolean dashed) {
        java.awt.Shape shape = m.toLayer(c.shape(1, 0, 0));
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
