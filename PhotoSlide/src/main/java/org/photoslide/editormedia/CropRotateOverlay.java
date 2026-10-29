package org.photoslide.editormedia;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import org.photoslide.editpipeline.ImagePreview16;

/**
 * LightZone's crop and rotate modes on the editor image.
 *
 * Crop: a frame with eight handles over the whole photo; drag inside to move
 * it, drag a handle to resize, drag outside to draw a new frame. Enter
 * applies, Esc cancels. Optionally keeps an aspect ratio.
 *
 * Rotate: a grid over the photo; draw a line along something that should be
 * level or upright and the photo is straightened by it.
 *
 * Coordinates are pixels of the rendered image (after flip and straighten)
 * in the scale of the original, as MediaFile stores the crop.
 */
public final class CropRotateOverlay extends Pane {

    public enum Mode {
        NONE, CROP, ROTATE
    }

    private static final double HANDLE = 10;
    private static final double MIN_SIZE = 16;
    private static final Color FRAME = Color.WHITE;
    private static final Color SHADE = Color.color(0, 0, 0, 0.55);

    private final ImageView view;
    private final ImagePreview16 preview;
    private Mode mode = Mode.NONE;

    // crop
    private Rectangle2D crop;
    /** width / height, 0 = free */
    private double aspect;
    /** -1 = move, 0..7 = handle (clockwise from top left), -2 = new frame */
    private int dragging = -3;
    private double[] dragStart;
    private Rectangle2D dragOriginal;
    private Consumer<Rectangle2D> onCrop;
    private Runnable onCancel;

    // rotate
    private double[] lineStart;
    private double[] lineEnd;
    private DoubleConsumer onStraighten;

    public CropRotateOverlay(ImageView view, ImagePreview16 preview) {
        this.view = view;
        this.preview = preview;
        setPickOnBounds(false);
        setMouseTransparent(true);
        setFocusTraversable(true);
        addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragged);
        addEventHandler(MouseEvent.MOUSE_RELEASED, this::released);
        addEventHandler(MouseEvent.MOUSE_MOVED, this::moved);
        setOnKeyPressed(e -> {
            if (mode == Mode.CROP && e.getCode() == KeyCode.ENTER) {
                commit();
                e.consume();
            } else if (mode != Mode.NONE && e.getCode() == KeyCode.ESCAPE) {
                if (onCancel != null) {
                    onCancel.run();
                }
                e.consume();
            }
        });
        view.boundsInParentProperty().addListener(o -> redraw());
        view.viewportProperty().addListener(o -> redraw());
        view.imageProperty().addListener(o -> redraw());
        widthProperty().addListener(o -> redraw());
        heightProperty().addListener(o -> redraw());
    }

    public Mode mode() {
        return mode;
    }

    /**
     * Starts the crop mode.
     *
     * @param current crop of the photo, null = none
     * @param onCrop gets the new crop (null = whole photo) on Enter or apply()
     * @param onCancel Esc
     */
    public void startCrop(Rectangle2D current, Consumer<Rectangle2D> onCrop, Runnable onCancel) {
        this.onCrop = onCrop;
        this.onCancel = onCancel;
        crop = current;
        start(Mode.CROP, Cursor.CROSSHAIR);
    }

    /** @param onStraighten gets the change of the angle in degrees for each drawn line */
    void startRotate(DoubleConsumer onStraighten, Runnable onCancel) {
        this.onStraighten = onStraighten;
        this.onCancel = onCancel;
        lineStart = lineEnd = null;
        start(Mode.ROTATE, Cursor.CROSSHAIR);
    }

    private void start(Mode m, Cursor cursor) {
        mode = m;
        setMouseTransparent(false);
        setPickOnBounds(true);
        setCursor(cursor);
        requestFocus();
        redraw();
    }

    public void stop() {
        mode = Mode.NONE;
        dragging = -3;
        lineStart = lineEnd = null;
        setMouseTransparent(true);
        setPickOnBounds(false);
        setCursor(Cursor.DEFAULT);
        getChildren().clear();
    }

    /** Aspect ratio of the frame (width / height), 0 = free. Fits the frame at once. */
    public void setAspect(double aspect) {
        this.aspect = aspect;
        if (mode == Mode.CROP && aspect > 0) {
            ViewMapping m = mapping();
            Rectangle2D r = crop != null ? crop : (m == null ? null : new Rectangle2D(0, 0, m.width(), m.height()));
            if (r != null && m != null) {
                double w = r.getWidth();
                double h = w / aspect;
                if (h > r.getHeight()) {
                    h = r.getHeight();
                    w = h * aspect;
                }
                crop = clamp(new Rectangle2D(r.getMinX() + (r.getWidth() - w) / 2,
                        r.getMinY() + (r.getHeight() - h) / 2, w, h), m);
            }
        }
        redraw();
    }

    /** Applies the frame (crop mode). */
    public void commit() {
        if (mode != Mode.CROP || onCrop == null) {
            return;
        }
        ViewMapping m = mapping();
        Rectangle2D r = crop;
        if (r != null && m != null && r.getMinX() <= 0.5 && r.getMinY() <= 0.5
                && r.getMaxX() >= m.width() - 0.5 && r.getMaxY() >= m.height() - 0.5) {
            r = null;                       // the whole photo: no crop
        }
        onCrop.accept(r);
    }

    /** Frame back to the whole photo (crop mode). */
    public void resetCrop() {
        crop = null;
        redraw();
    }

    private ViewMapping mapping() {
        return ViewMapping.of(view, preview, this);
    }

    // ------------------------------------------------------------------
    // mouse
    // ------------------------------------------------------------------
    private void pressed(MouseEvent e) {
        ViewMapping m = mapping();
        if (mode == Mode.NONE || m == null) {
            return;
        }
        requestFocus();
        e.consume();
        double[] p = m.shownFromLayer(e.getX(), e.getY(), true);
        if (mode == Mode.ROTATE) {
            lineStart = p;
            lineEnd = p;
            redraw();
            return;
        }
        Rectangle2D r = frame(m);
        dragStart = p;
        dragOriginal = r;
        dragging = handleAt(m, r, e.getX(), e.getY());
        if (dragging == -3) {
            dragging = r.contains(p[0], p[1]) && crop != null ? -1 : -2;
        }
    }

    private void dragged(MouseEvent e) {
        ViewMapping m = mapping();
        if (mode == Mode.NONE || m == null) {
            return;
        }
        e.consume();
        double[] p = m.shownFromLayer(e.getX(), e.getY(), true);
        if (mode == Mode.ROTATE) {
            if (lineStart != null) {
                lineEnd = p;
                redraw();
            }
            return;
        }
        if (dragStart == null) {
            return;
        }
        double dx = p[0] - dragStart[0];
        double dy = p[1] - dragStart[1];
        Rectangle2D o = dragOriginal;
        switch (dragging) {
            case -1 -> {
                double x = Math.max(0, Math.min(m.width() - o.getWidth(), o.getMinX() + dx));
                double y = Math.max(0, Math.min(m.height() - o.getHeight(), o.getMinY() + dy));
                crop = new Rectangle2D(x, y, o.getWidth(), o.getHeight());
            }
            case -2 -> crop = clamp(fromCorners(dragStart[0], dragStart[1], p[0], p[1]), m);
            default -> crop = clamp(resized(o, dragging, dx, dy), m);
        }
        redraw();
    }

    private void released(MouseEvent e) {
        if (mode == Mode.NONE) {
            return;
        }
        e.consume();
        if (mode == Mode.ROTATE) {
            if (lineStart != null && lineEnd != null
                    && Math.hypot(lineEnd[0] - lineStart[0], lineEnd[1] - lineStart[1]) > 4 && onStraighten != null) {
                double a = Math.toDegrees(Math.atan2(lineEnd[1] - lineStart[1], lineEnd[0] - lineStart[0]));
                double delta = a - Math.round(a / 90) * 90;         // tilt against the nearest axis
                onStraighten.accept(-delta);
            }
            lineStart = lineEnd = null;
            redraw();
            return;
        }
        if (crop != null && (crop.getWidth() < MIN_SIZE || crop.getHeight() < MIN_SIZE)) {
            crop = dragOriginal == null || dragging == -2 ? null : dragOriginal;
        }
        dragging = -3;
        dragStart = null;
        redraw();
    }

    private void moved(MouseEvent e) {
        if (mode != Mode.CROP) {
            return;
        }
        ViewMapping m = mapping();
        if (m == null) {
            return;
        }
        Rectangle2D r = frame(m);
        int h = handleAt(m, r, e.getX(), e.getY());
        double[] p = m.shownFromLayer(e.getX(), e.getY(), false);
        if (h >= 0) {
            setCursor(h % 2 == 0 ? Cursor.CROSSHAIR : Cursor.HAND);
        } else if (p != null && crop != null && r.contains(p[0], p[1])) {
            setCursor(Cursor.MOVE);
        } else {
            setCursor(Cursor.CROSSHAIR);
        }
    }

    /** Frame shown: the crop, or the whole photo. */
    private Rectangle2D frame(ViewMapping m) {
        return crop != null ? crop : new Rectangle2D(0, 0, m.width(), m.height());
    }

    /** Handle positions {x, y} clockwise from top left (corners even, edges odd). */
    private static double[][] handles(Rectangle2D r) {
        double cx = (r.getMinX() + r.getMaxX()) / 2;
        double cy = (r.getMinY() + r.getMaxY()) / 2;
        return new double[][]{
            {r.getMinX(), r.getMinY()}, {cx, r.getMinY()}, {r.getMaxX(), r.getMinY()}, {r.getMaxX(), cy},
            {r.getMaxX(), r.getMaxY()}, {cx, r.getMaxY()}, {r.getMinX(), r.getMaxY()}, {r.getMinX(), cy}};
    }

    private static int handleAt(ViewMapping m, Rectangle2D r, double lx, double ly) {
        double[][] hs = handles(r);
        for (int i = 0; i < hs.length; i++) {
            Point2D q = m.shownToLayer(hs[i][0], hs[i][1]);
            if (Math.abs(q.getX() - lx) <= HANDLE && Math.abs(q.getY() - ly) <= HANDLE) {
                return i;
            }
        }
        return -3;
    }

    /** The frame o with handle h moved by (dx, dy), keeping the aspect ratio if set. */
    private Rectangle2D resized(Rectangle2D o, int h, double dx, double dy) {
        double x0 = o.getMinX(), y0 = o.getMinY(), x1 = o.getMaxX(), y1 = o.getMaxY();
        boolean left = h == 0 || h == 6 || h == 7;
        boolean right = h == 2 || h == 3 || h == 4;
        boolean top = h == 0 || h == 1 || h == 2;
        boolean bottom = h == 4 || h == 5 || h == 6;
        if (left) {
            x0 = Math.min(x1 - MIN_SIZE, x0 + dx);
        }
        if (right) {
            x1 = Math.max(x0 + MIN_SIZE, x1 + dx);
        }
        if (top) {
            y0 = Math.min(y1 - MIN_SIZE, y0 + dy);
        }
        if (bottom) {
            y1 = Math.max(y0 + MIN_SIZE, y1 + dy);
        }
        if (aspect > 0) {
            double w = x1 - x0;
            double hh = y1 - y0;
            if (left || right) {
                hh = w / aspect;
            } else {
                w = hh * aspect;
            }
            if (left && !top && !bottom || right && !top && !bottom) {
                double cy = (o.getMinY() + o.getMaxY()) / 2;
                y0 = cy - hh / 2;
                y1 = cy + hh / 2;
            } else if (top && !left && !right || bottom && !left && !right) {
                double cx = (o.getMinX() + o.getMaxX()) / 2;
                x0 = cx - w / 2;
                x1 = cx + w / 2;
            } else {
                if (top) {
                    y0 = y1 - hh;
                } else {
                    y1 = y0 + hh;
                }
                if (left) {
                    x0 = x1 - w;
                } else {
                    x1 = x0 + w;
                }
            }
        }
        return new Rectangle2D(x0, y0, x1 - x0, y1 - y0);
    }

    /** Frame from the drag start to the mouse, with the aspect ratio if set. */
    private Rectangle2D fromCorners(double ax, double ay, double bx, double by) {
        double w = Math.abs(bx - ax);
        double h = Math.abs(by - ay);
        if (aspect > 0) {
            if (w / aspect > h) {
                h = w / aspect;
            } else {
                w = h * aspect;
            }
        }
        double x = bx >= ax ? ax : ax - w;
        double y = by >= ay ? ay : ay - h;
        return new Rectangle2D(x, y, Math.max(1, w), Math.max(1, h));
    }

    /** Keeps the frame inside the photo (shrinks it, keeping the aspect ratio). */
    private Rectangle2D clamp(Rectangle2D r, ViewMapping m) {
        double x0 = Math.max(0, r.getMinX());
        double y0 = Math.max(0, r.getMinY());
        double x1 = Math.min(m.width(), r.getMaxX());
        double y1 = Math.min(m.height(), r.getMaxY());
        double w = Math.max(1, x1 - x0);
        double h = Math.max(1, y1 - y0);
        if (aspect > 0) {
            if (w / aspect > h) {
                w = h * aspect;
            } else {
                h = w / aspect;
            }
            if (x0 > r.getMinX()) {
                x1 = x0 + w;
            } else {
                x0 = x1 - w;
            }
            if (y0 > r.getMinY()) {
                y1 = y0 + h;
            } else {
                y0 = y1 - h;
            }
        }
        return new Rectangle2D(x0, y0, w, h);
    }

    // ------------------------------------------------------------------
    // drawing
    // ------------------------------------------------------------------
    void redraw() {
        getChildren().clear();
        ViewMapping m = mapping();
        if (mode == Mode.NONE || m == null) {
            return;
        }
        if (mode == Mode.CROP) {
            drawCrop(m);
        } else {
            drawRotate(m);
        }
    }

    private void drawCrop(ViewMapping m) {
        Rectangle2D r = frame(m);
        Rectangle2D all = new Rectangle2D(0, 0, m.width(), m.height());
        Path shade = new Path();
        addQuad(shade, m, all);
        addQuad(shade, m, r);
        shade.setFillRule(FillRule.EVEN_ODD);
        shade.setFill(SHADE);
        shade.setStroke(null);
        shade.setMouseTransparent(true);
        getChildren().add(shade);
        // rule of thirds
        for (int i = 1; i <= 2; i++) {
            double x = r.getMinX() + r.getWidth() * i / 3;
            double y = r.getMinY() + r.getHeight() * i / 3;
            getChildren().add(line(m, x, r.getMinY(), x, r.getMaxY(), Color.color(1, 1, 1, 0.4)));
            getChildren().add(line(m, r.getMinX(), y, r.getMaxX(), y, Color.color(1, 1, 1, 0.4)));
        }
        Path frame = new Path();
        addQuad(frame, m, r);
        frame.setStroke(FRAME);
        frame.setStrokeWidth(1.5);
        frame.setFill(null);
        frame.setMouseTransparent(true);
        getChildren().add(frame);
        for (double[] h : handles(r)) {
            Point2D q = m.shownToLayer(h[0], h[1]);
            Rectangle handle = new Rectangle(q.getX() - HANDLE / 2, q.getY() - HANDLE / 2, HANDLE, HANDLE);
            handle.setFill(Color.WHITE);
            handle.setStroke(Color.web("#333333"));
            handle.setMouseTransparent(true);
            getChildren().add(handle);
        }
    }

    private void drawRotate(ViewMapping m) {
        Rectangle2D v = m.visible();
        int n = 8;
        for (int i = 1; i < n; i++) {
            double x = v.getMinX() + v.getWidth() * i / n;
            double y = v.getMinY() + v.getHeight() * i / n;
            getChildren().add(line(m, x, v.getMinY(), x, v.getMaxY(), Color.color(1, 1, 1, 0.3)));
            getChildren().add(line(m, v.getMinX(), y, v.getMaxX(), y, Color.color(1, 1, 1, 0.3)));
        }
        if (lineStart != null && lineEnd != null) {
            Line l = line(m, lineStart[0], lineStart[1], lineEnd[0], lineEnd[1], Color.web("#ffd24d"));
            l.setStrokeWidth(2);
            getChildren().add(l);
        }
    }

    private static void addQuad(Path p, ViewMapping m, Rectangle2D r) {
        Point2D a = m.shownToLayer(r.getMinX(), r.getMinY());
        Point2D b = m.shownToLayer(r.getMaxX(), r.getMinY());
        Point2D c = m.shownToLayer(r.getMaxX(), r.getMaxY());
        Point2D d = m.shownToLayer(r.getMinX(), r.getMaxY());
        p.getElements().addAll(new MoveTo(a.getX(), a.getY()), new LineTo(b.getX(), b.getY()),
                new LineTo(c.getX(), c.getY()), new LineTo(d.getX(), d.getY()), new ClosePath());
    }

    private static Line line(ViewMapping m, double x0, double y0, double x1, double y1, Color color) {
        Point2D a = m.shownToLayer(x0, y0);
        Point2D b = m.shownToLayer(x1, y1);
        Line l = new Line(a.getX(), a.getY(), b.getX(), b.getY());
        l.setStroke(color);
        l.setMouseTransparent(true);
        return l;
    }
}
