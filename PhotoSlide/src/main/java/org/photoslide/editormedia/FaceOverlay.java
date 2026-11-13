package org.photoslide.editormedia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.controlsfx.control.textfield.TextFields;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.I18n;
import org.photoslide.datamodel.FaceRegion;
import org.photoslide.editpipeline.ImagePreview16;

/**
 * The faces of the shown photo: a frame around each face and a name field
 * below it. Orange = unknown, yellow dashed = suggestion of the recognition
 * (Enter or the check button confirms it), green = confirmed. A name typed
 * and confirmed with Enter or by leaving the field is confirmed; the cross
 * removes a wrong face. In the add mode a frame drawn on the photo becomes a
 * new face.
 *
 * Faces are stored relative to the source image, so the frames follow zoom,
 * crop, rotation and flip of the view (see ViewMapping). Clicks outside the
 * frames and fields go through to the photo.
 */
public final class FaceOverlay extends Pane {

    private static final Color CONFIRMED = Color.web("#4caf50");
    private static final Color SUGGESTED = Color.web("#ffc107");
    private static final Color UNKNOWN = Color.web("#ff7043");
    private static final double MIN_DRAW = 12;

    private final ImageView view;
    private final ImagePreview16 preview;
    private final List<FaceBox> boxes = new ArrayList<>();
    private volatile List<String> names = List.of();
    private boolean onlyUnknown;
    private boolean adding;
    private Consumer<List<FaceRegion>> onChange = f -> {
    };
    private Consumer<FaceRegion> onAdd = f -> {
    };
    private Runnable onAddingEnded = () -> {
    };

    // frame being drawn in the add mode (layer coordinates)
    private Point2D drawStart;
    private final Rectangle drawn = new Rectangle();

    public FaceOverlay(ImageView view, ImagePreview16 preview) {
        this.view = view;
        this.preview = preview;
        setPickOnBounds(false);
        setVisible(false);
        drawn.setFill(Color.TRANSPARENT);
        drawn.setStroke(UNKNOWN);
        drawn.setStrokeWidth(2);
        drawn.getStrokeDashArray().setAll(6.0, 4.0);
        drawn.setManaged(false);
        drawn.setMouseTransparent(true);
        addEventHandler(MouseEvent.MOUSE_PRESSED, this::pressed);
        addEventHandler(MouseEvent.MOUSE_DRAGGED, this::dragged);
        addEventHandler(MouseEvent.MOUSE_RELEASED, this::released);
        view.boundsInParentProperty().addListener(o -> layoutBoxes());
        view.viewportProperty().addListener(o -> layoutBoxes());
        view.imageProperty().addListener(o -> layoutBoxes());
        widthProperty().addListener(o -> layoutBoxes());
        heightProperty().addListener(o -> layoutBoxes());
    }

    /** Called with all faces after the user changed one (name, removed). */
    public void setOnChange(Consumer<List<FaceRegion>> onChange) {
        this.onChange = onChange;
    }

    /** Called with a face the user drew (unknown; the caller may ask for a suggestion and call setFaces). */
    public void setOnAdd(Consumer<FaceRegion> onAdd) {
        this.onAdd = onAdd;
    }

    /** Called when the add mode ends by itself (after a frame was drawn). */
    public void setOnAddingEnded(Runnable onAddingEnded) {
        this.onAddingEnded = onAddingEnded;
    }

    /** Names offered while typing. */
    public void setNames(Collection<String> names) {
        this.names = List.copyOf(names);
    }

    /** Shows these faces (replaces the current ones). JavaFX thread. */
    public void setFaces(List<FaceRegion> faces) {
        boxes.forEach(FaceBox::remove);
        boxes.clear();
        for (FaceRegion f : faces) {
            boxes.add(new FaceBox(f));
        }
        layoutBoxes();
    }

    public List<FaceRegion> getFaces() {
        List<FaceRegion> faces = new ArrayList<>();
        for (FaceBox b : boxes) {
            faces.add(b.face);
        }
        return faces;
    }

    /** true = only unknown faces and suggestions are shown. */
    public void setOnlyUnknown(boolean onlyUnknown) {
        this.onlyUnknown = onlyUnknown;
        layoutBoxes();
    }

    /** Add mode: a frame drawn on the photo becomes a new face. */
    public void setAdding(boolean adding) {
        this.adding = adding;
        setPickOnBounds(adding);
        setCursor(adding ? Cursor.CROSSHAIR : Cursor.DEFAULT);
        if (!adding) {
            drawStart = null;
            getChildren().remove(drawn);
        }
    }

    public boolean isAdding() {
        return adding;
    }

    /** Commits the name being typed (e.g. before another photo is shown). */
    public void commitEditing() {
        for (FaceBox b : boxes) {
            b.commitIfChanged();
        }
    }

    public void clear() {
        setAdding(false);
        boxes.forEach(FaceBox::remove);
        boxes.clear();
    }

    private ViewMapping mapping() {
        return ViewMapping.of(view, preview, this);
    }

    private void layoutBoxes() {
        ViewMapping m = mapping();
        for (FaceBox b : boxes) {
            b.layout(m);
        }
    }

    private void changed() {
        onChange.accept(getFaces());
    }

    // ------------------------------------------------------------------
    // add mode
    // ------------------------------------------------------------------
    private void pressed(MouseEvent e) {
        if (!adding || e.getTarget() != this) {
            return;
        }
        drawStart = new Point2D(e.getX(), e.getY());
        drawn.setX(e.getX());
        drawn.setY(e.getY());
        drawn.setWidth(0);
        drawn.setHeight(0);
        if (!getChildren().contains(drawn)) {
            getChildren().add(drawn);
        }
        e.consume();
    }

    private void dragged(MouseEvent e) {
        if (drawStart == null) {
            return;
        }
        drawn.setX(Math.min(drawStart.getX(), e.getX()));
        drawn.setY(Math.min(drawStart.getY(), e.getY()));
        drawn.setWidth(Math.abs(e.getX() - drawStart.getX()));
        drawn.setHeight(Math.abs(e.getY() - drawStart.getY()));
        e.consume();
    }

    private void released(MouseEvent e) {
        if (drawStart == null) {
            return;
        }
        Point2D start = drawStart;
        drawStart = null;
        getChildren().remove(drawn);
        e.consume();
        ViewMapping m = mapping();
        if (m == null || Math.abs(e.getX() - start.getX()) < MIN_DRAW || Math.abs(e.getY() - start.getY()) < MIN_DRAW) {
            return;
        }
        double[] a = m.fromLayer(start.getX(), start.getY(), true);
        double[] b = m.fromLayer(e.getX(), e.getY(), true);
        double[] c = m.fromLayer(start.getX(), e.getY(), true);
        double[] d = m.fromLayer(e.getX(), start.getY(), true);
        double x0 = Math.min(Math.min(a[0], b[0]), Math.min(c[0], d[0]));
        double x1 = Math.max(Math.max(a[0], b[0]), Math.max(c[0], d[0]));
        double y0 = Math.min(Math.min(a[1], b[1]), Math.min(c[1], d[1]));
        double y1 = Math.max(Math.max(a[1], b[1]), Math.max(c[1], d[1]));
        FaceRegion face = new FaceRegion(x0 / m.width(), y0 / m.height(), (x1 - x0) / m.width(),
                (y1 - y0) / m.height(), null, false);
        boxes.add(new FaceBox(face));
        layoutBoxes();
        setAdding(false);
        onAddingEnded.run();
        changed();
        onAdd.accept(face);
    }

    // ------------------------------------------------------------------
    // one face
    // ------------------------------------------------------------------
    private final class FaceBox {

        private FaceRegion face;
        private final Rectangle frame = new Rectangle();
        private final TextField field = new TextField();
        private final Button accept = new Button(null, new FontIcon("fa-check"));
        private final Button remove = new Button(null, new FontIcon("fa-times"));
        private final HBox editor = new HBox(2, field, accept, remove);

        FaceBox(FaceRegion face) {
            this.face = face;
            frame.setFill(Color.TRANSPARENT);
            frame.setStrokeWidth(2);
            frame.setManaged(false);
            frame.setMouseTransparent(true);
            field.setPrefColumnCount(10);
            field.setPromptText(I18n.t("faces.namePrompt"));
            field.setStyle("-fx-background-color: rgba(30,30,30,0.85); -fx-text-fill: white; "
                    + "-fx-prompt-text-fill: #bbbbbb; -fx-font-size: 11px; -fx-padding: 2 4 2 4;");
            accept.setTooltip(new Tooltip(I18n.t("faces.confirm")));
            remove.setTooltip(new Tooltip(I18n.t("faces.remove")));
            for (Button b : new Button[]{accept, remove}) {
                b.setFocusTraversable(false);
                b.setStyle("-fx-background-color: rgba(30,30,30,0.85); -fx-text-fill: white; -fx-padding: 2 5 2 5;");
                ((FontIcon) b.getGraphic()).setIconColor(Color.WHITE);
                ((FontIcon) b.getGraphic()).setIconSize(11);
            }
            editor.setManaged(false);
            TextFields.bindAutoCompletion(field, request -> {
                String typed = request.getUserText() == null ? "" : request.getUserText().trim().toLowerCase(Locale.ROOT);
                if (typed.isEmpty()) {
                    return List.of();
                }
                return names.stream().filter(n -> n.toLowerCase(Locale.ROOT).contains(typed)).limit(10).toList();
            });
            field.setOnAction(e -> confirm(field.getText()));
            field.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    field.setText(face.name() == null ? "" : face.name());
                    getParent().requestFocus();
                    e.consume();
                }
            });
            field.focusedProperty().addListener((o, was, is) -> {
                if (!is) {
                    commitIfChanged();
                }
            });
            accept.setOnAction(e -> confirm(field.getText()));
            remove.setOnAction(e -> {
                FaceBox.this.remove();
                boxes.remove(FaceBox.this);
                changed();
            });
            update();
            getChildren().addAll(frame, editor);
        }

        void commitIfChanged() {
            String text = field.getText() == null ? "" : field.getText().trim();
            String current = face.name() == null ? "" : face.name();
            if (!text.equals(current)) {
                confirm(text);
            }
        }

        private void confirm(String name) {
            String n = name == null ? "" : name.trim();
            FaceRegion next = face.withName(n, !n.isEmpty());
            if (next.equals(face)) {
                return;
            }
            face = next;
            update();
            changed();
        }

        private void update() {
            Color c = face.confirmed() ? CONFIRMED : face.isUnknown() ? UNKNOWN : SUGGESTED;
            frame.setStroke(c);
            if (!face.confirmed() && !face.isUnknown()) {
                frame.getStrokeDashArray().setAll(6.0, 4.0);
            } else {
                frame.getStrokeDashArray().clear();
            }
            String text = face.name() == null ? "" : face.name();
            if (!text.equals(field.getText())) {
                field.setText(text);
            }
            accept.setVisible(!face.confirmed() && !face.isUnknown());
            accept.setManaged(accept.isVisible());
        }

        void layout(ViewMapping m) {
            boolean show = m != null && !(onlyUnknown && face.confirmed());
            Bounds r = null;
            if (show) {
                double w = m.width();
                double h = m.height();
                Point2D[] corners = {
                    m.toLayer(face.x() * w, face.y() * h),
                    m.toLayer((face.x() + face.w()) * w, face.y() * h),
                    m.toLayer(face.x() * w, (face.y() + face.h()) * h),
                    m.toLayer((face.x() + face.w()) * w, (face.y() + face.h()) * h)};
                double x0 = Double.MAX_VALUE;
                double y0 = Double.MAX_VALUE;
                double x1 = -Double.MAX_VALUE;
                double y1 = -Double.MAX_VALUE;
                for (Point2D p : corners) {
                    x0 = Math.min(x0, p.getX());
                    y0 = Math.min(y0, p.getY());
                    x1 = Math.max(x1, p.getX());
                    y1 = Math.max(y1, p.getY());
                }
                r = new javafx.geometry.BoundingBox(x0, y0, x1 - x0, y1 - y0);
                // hidden when its centre is outside the shown part (zoom, crop)
                show = m.viewInLayer().contains(r.getCenterX(), r.getCenterY());
            }
            frame.setVisible(show);
            editor.setVisible(show);
            if (!show) {
                return;
            }
            frame.setX(r.getMinX());
            frame.setY(r.getMinY());
            frame.setWidth(r.getWidth());
            frame.setHeight(r.getHeight());
            editor.autosize();
            double ew = editor.prefWidth(-1);
            double eh = editor.prefHeight(-1);
            double ex = Math.max(0, Math.min(getWidth() - ew, r.getCenterX() - ew / 2));
            double ey = r.getMaxY() + 4;
            if (ey + eh > getHeight()) {
                ey = Math.max(0, r.getMinY() - eh - 4);
            }
            editor.resizeRelocate(ex, ey, ew, eh);
        }

        void remove() {
            getChildren().removeAll(frame, editor);
        }
    }
}
