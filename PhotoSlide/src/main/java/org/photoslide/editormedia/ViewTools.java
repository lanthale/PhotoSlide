package org.photoslide.editormedia;

import java.util.Locale;
import java.util.Objects;
import javafx.beans.InvalidationListener;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.imageops.EditHistory;
import org.photoslide.imageops.EditPipeline;
import org.photoslide.imageops.Geometry;
import org.photoslide.imageops.ImagePreview16;

/**
 * The view and geometry tools of the editor toolbar, as in LightZone: undo
 * and redo (history of the photo), zoom (fit, 1:1, larger, smaller), the
 * crop and rotate modes, quarter turns and flips. While a mode is active, a
 * second bar below the toolbar holds its settings.
 *
 * Crop and quarter turn are kept as before in the media file (crop =
 * viewport of the rendered image, quarter turn = rotation of the view), flip
 * and straighten are rendered (Geometry).
 */
final class ViewTools {

    /** Size of the toolbar icons. */
    static final int ICON_SIZE = 22;
    private static final double ZOOM_STEP = Math.sqrt(2);
    /** Largest zoom in multiples of 1:1. */
    private static final double MAX_ZOOM = 8;
    private static final KeyCombination UNDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination REDO = new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN,
            KeyCombination.SHIFT_DOWN);
    private static final KeyCombination REDO_Y = new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN);

    private final ImageView view;
    private final StackPane stack;
    private final ImagePreview16 preview;
    private final CropRotateOverlay overlay;
    private final RegionOverlay regions;

    private final Button undo;
    private final Button redo;
    private final Button fit;
    private final Button oneToOne;
    private final Button zoomIn;
    private final Button zoomOut;
    private final ToggleButton crop;
    private final ToggleButton rotate;
    private final Button rotateLeft;
    private final Button rotateRight;
    private final Button flipH;
    private final Button flipV;

    private final ToolBar modeBar = new ToolBar();
    private final HBox cropControls;
    private final HBox rotateControls;
    private final ComboBox<String> aspect = CropAspect.comboBox();
    private final Slider straighten = new Slider(-Geometry.MAX_STRAIGHTEN, Geometry.MAX_STRAIGHTEN, 0);
    private final Label straightenValue = new Label();
    private boolean updatingSlider;

    private MediaFile file;
    private EditHistory history;
    private final InvalidationListener historyListener = o -> updateButtons();

    /** 0 = fit, otherwise screen pixels per pixel of the original. */
    private double zoom;
    /** Centre of the zoomed view, pixels of the rendered image; NaN = centre of the photo. */
    private double centerX = Double.NaN;
    private double centerY = Double.NaN;
    private double[] panStart;

    ViewTools(ToolBar bar, ImageView view, StackPane stack, ImagePreview16 preview,
            CropRotateOverlay overlay, RegionOverlay regions) {
        this.view = view;
        this.stack = stack;
        this.preview = preview;
        this.overlay = overlay;
        this.regions = regions;
        // a quarter turned photo is laid out unturned: its box must not widen the editor
        if (view.getParent() instanceof javafx.scene.layout.Region box) {
            box.setMinSize(0, 0);
        }

        undo =button("ti-back-left", "Undo (" + UNDO.getDisplayText() + ")");
        undo.setOnAction(e -> undo());
        redo = button("ti-back-right", "Redo (" + REDO.getDisplayText() + ")");
        redo.setOnAction(e -> redo());
        fit = textButton("Fit", "Show the whole photo");
        fit.setOnAction(e -> zoomTo(0));
        oneToOne = textButton("1:1", "Actual pixels (100 %)");
        oneToOne.setOnAction(e -> zoomTo(oneToOneZoom()));
        zoomIn = button("fa-search-plus", "Larger");
        zoomIn.setOnAction(e -> zoomBy(ZOOM_STEP));
        zoomOut = button("fa-search-minus", "Smaller");
        zoomOut.setOnAction(e -> zoomBy(1 / ZOOM_STEP));
        crop = toggle("fa-crop", "Crop mode: drag the frame or its handles, Enter applies, Esc cancels");
        crop.setOnAction(e -> {
            if (crop.isSelected()) {
                startCrop();
            } else {
                overlay.commit();
            }
        });
        rotate = toggle("ti-reload", "Rotate mode: draw a line along the horizon to straighten the photo");
        rotate.setOnAction(e -> {
            if (rotate.isSelected()) {
                startRotate();
            } else {
                endMode();
            }
        });
        rotateLeft = button("fa-rotate-left", "Rotate left");
        rotateLeft.setOnAction(e -> turn(-90));
        rotateRight = button("fa-rotate-right", "Rotate right");
        rotateRight.setOnAction(e -> turn(90));
        flipH = button("fa-arrows-h", "Flip horizontal");
        flipH.setOnAction(e -> flip(true));
        flipV = button("fa-arrows-v", "Flip vertical");
        flipV.setOnAction(e -> flip(false));

        bar.getItems().addAll(0, java.util.List.of(undo, redo, new Separator(), fit, oneToOne, zoomIn, zoomOut,
                new Separator(), crop, rotate, new Separator(), rotateLeft, rotateRight, flipH, flipV));

        // settings of the active mode
        aspect.setOnAction(e -> overlay.setAspect(aspectValue()));
        Button resetCrop = new Button("Reset", new FontIcon("fa-expand"));
        resetCrop.setTooltip(new Tooltip("Frame the whole photo"));
        resetCrop.setOnAction(e -> overlay.resetCrop());
        Button cancelCrop = new Button("Cancel");
        cancelCrop.setOnAction(e -> endMode());
        Button applyCrop = new Button("Apply", new FontIcon("fa-check"));
        applyCrop.setDefaultButton(false);
        applyCrop.setOnAction(e -> overlay.commit());
        cropControls = new HBox(6, new Label("Aspect ratio"), aspect, resetCrop, cancelCrop, applyCrop);
        cropControls.setAlignment(Pos.CENTER_LEFT);

        straighten.setPrefWidth(220);
        straighten.setMajorTickUnit(15);
        straighten.setShowTickMarks(true);
        straighten.setBlockIncrement(0.1);
        straightenValue.setMinWidth(52);
        straighten.valueProperty().addListener(o -> {
            straightenValue.setText(String.format(Locale.ROOT, "%.1f°", straighten.getValue()));
            if (!updatingSlider) {
                setStraighten(Math.round(straighten.getValue() * 10) / 10.0);
            }
        });
        Button resetAngle = new Button("Reset", new FontIcon("fa-undo"));
        resetAngle.setOnAction(e -> setStraighten(0));
        Button done = new Button("Done", new FontIcon("fa-check"));
        done.setOnAction(e -> endMode());
        Label hint = new Label("or draw a line along the horizon");
        hint.setFont(new Font(11));
        rotateControls = new HBox(6, new Label("Angle"), straighten, straightenValue, resetAngle, hint, done);
        rotateControls.setAlignment(Pos.CENTER_LEFT);
        modeBar.setVisible(false);
        modeBar.setManaged(false);
        if (bar.getParent() instanceof Pane parent) {
            parent.getChildren().add(parent.getChildren().indexOf(bar) + 1, modeBar);
        }

        // pan the zoomed photo by scrolling or dragging
        stack.addEventHandler(ScrollEvent.SCROLL, e -> {
            if (zoom > 0 && file != null) {
                pan(-e.getDeltaX(), -e.getDeltaY());
                e.consume();
            }
        });
        stack.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            panStart = zoom > 0 && e.getButton() == MouseButton.PRIMARY && overlay.mode() == CropRotateOverlay.Mode.NONE
                    && !regions.isBusy() ? new double[]{e.getSceneX(), e.getSceneY()} : null;
        });
        stack.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (panStart != null) {
                pan(panStart[0] - e.getSceneX(), panStart[1] - e.getSceneY());
                panStart = new double[]{e.getSceneX(), e.getSceneY()};
            }
        });
        stack.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> panStart = null);
        stack.widthProperty().addListener(o -> {
            if (zoom > 0) {
                applyView();
            }
        });
        stack.heightProperty().addListener(o -> {
            if (zoom > 0) {
                applyView();
            }
        });
        // undo / redo wherever the focus is, while the editor is shown
        stack.sceneProperty().addListener((o, oldScene, newScene) -> {
            if (oldScene != null) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
            }
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, this::keyPressed);
            }
        });
        EditPipeline.addReloadListener(f -> {
            if (f == file) {
                if (overlay.mode() == CropRotateOverlay.Mode.CROP) {
                    endMode();
                }
                updateSlider();
                applyView();
                updateButtons();
            }
        });
        setFile(null);
    }

    private static FontIcon icon(String literal) {
        FontIcon i = new FontIcon(literal);
        i.setIconSize(ICON_SIZE);
        return i;
    }

    private static Button button(String icon, String tip) {
        Button b = new Button(null, icon(icon));
        style(b, tip);
        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        return b;
    }

    private static Button textButton(String text, String tip) {
        Button b = new Button(text);
        style(b, tip);
        b.setFont(Font.font(null, javafx.scene.text.FontWeight.BOLD, 14));
        return b;
    }

    private static ToggleButton toggle(String icon, String tip) {
        ToggleButton b = new ToggleButton(null, icon(icon));
        style(b, tip);
        b.setId("tooltoggle-button");               // shows the active mode
        b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        return b;
    }

    private static void style(ButtonBase b, String tip) {
        b.setId("toolbutton");
        b.setFont(new Font(11));
        b.setTooltip(new Tooltip(tip));
        b.setFocusTraversable(false);
    }

    /** The photo shown in the editor (null = none). JavaFX thread. */
    void setFile(MediaFile f) {
        if (overlay.mode() != CropRotateOverlay.Mode.NONE) {
            endMode();
        }
        if (history != null) {
            history.currentProperty().removeListener(historyListener);
            history.entries().removeListener(historyListener);
        }
        file = f;
        history = f == null ? null : EditHistory.of(f);
        if (history != null) {
            history.currentProperty().addListener(historyListener);
            history.entries().addListener(historyListener);
        }
        zoom = 0;
        centerX = centerY = Double.NaN;
        for (ButtonBase b : new ButtonBase[]{fit, oneToOne, zoomIn, zoomOut, crop, rotate,
            rotateLeft, rotateRight, flipH, flipV}) {
            b.setDisable(f == null);
        }
        updateSlider();
        updateButtons();
        applyView();
    }

    private void updateButtons() {
        undo.setDisable(history == null || !history.canUndo());
        redo.setDisable(history == null || !history.canRedo());
        boolean cropping = overlay.mode() == CropRotateOverlay.Mode.CROP;
        flipH.setDisable(file == null || cropping);
        flipV.setDisable(file == null || cropping);
    }

    private void updateSlider() {
        updatingSlider = true;
        try {
            straighten.setValue(file == null ? 0 : file.getGeometry().straighten());
        } finally {
            updatingSlider = false;
        }
    }

    // ------------------------------------------------------------------
    // undo / redo
    // ------------------------------------------------------------------
    private void undo() {
        if (history != null && history.canUndo()) {
            endMode();
            history.undo(file);
        }
    }

    private void redo() {
        if (history != null && history.canRedo()) {
            endMode();
            history.redo(file);
        }
    }

    private void keyPressed(KeyEvent e) {
        if (file == null || !isShowing() || e.getTarget() instanceof TextInputControl) {
            return;
        }
        if (REDO.match(e) || REDO_Y.match(e)) {
            redo();
            e.consume();
        } else if (UNDO.match(e)) {
            undo();
            e.consume();
        }
    }

    private boolean isShowing() {
        for (Node n = stack; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        Scene s = stack.getScene();
        return s != null && s.getWindow() != null && s.getWindow().isFocused();
    }

    // ------------------------------------------------------------------
    // view: zoom, crop, quarter turn
    // ------------------------------------------------------------------
    /** Size of the original, null while unknown. */
    private double[] size() {
        Image16 source = preview.getSource();
        if (source != null && preview.getFile() == file) {
            return new double[]{source.width(), source.height()};
        }
        javafx.geometry.Point2D s = file == null ? null : file.getOrignalImageSize();
        return s == null ? null : new double[]{s.getX(), s.getY()};
    }

    /** Area the view may show: the crop, or the whole photo (always in crop mode). */
    private Rectangle2D bounds(double[] size) {
        Rectangle2D c = file.getCropView();
        if (c == null || c.getWidth() <= 0 || overlay.mode() == CropRotateOverlay.Mode.CROP) {
            return new Rectangle2D(0, 0, size[0], size[1]);
        }
        return c;
    }

    private static double normalized(double degrees) {
        return ((degrees % 360) + 360) % 360;
    }

    private boolean quarter() {
        double r = normalized(file == null ? 0 : file.getRotationAngleProperty().get());
        return Math.abs(r - 90) < 1 || Math.abs(r - 270) < 1;
    }

    /** Space for the photo on screen, in the orientation of the unturned photo. */
    private double[] available() {
        return quarter() ? new double[]{stack.getHeight(), stack.getWidth()}
                : new double[]{stack.getWidth(), stack.getHeight()};
    }

    private double fitZoom(double[] size) {
        Rectangle2D b = bounds(size);
        double[] a = available();
        return Math.min(a[0] / b.getWidth(), a[1] / b.getHeight());
    }

    private double oneToOneZoom() {
        double outputScale = 1;
        Window w = stack.getScene() == null ? null : stack.getScene().getWindow();
        if (w != null) {
            outputScale = Math.max(1, w.getOutputScaleX());
        }
        return 1 / outputScale;
    }

    /** Applies quarter turn, crop and zoom of the file to the image view. JavaFX thread. */
    void applyView() {
        double rotation = file == null ? 0 : normalized(file.getRotationAngleProperty().get());
        view.setRotate(rotation);
        boolean q = file != null && quarter();
        view.fitWidthProperty().unbind();
        view.fitHeightProperty().unbind();
        view.fitWidthProperty().bind(q ? stack.heightProperty() : stack.widthProperty());
        view.fitHeightProperty().bind(q ? stack.widthProperty() : stack.heightProperty());
        double[] size = file == null ? null : size();
        if (size == null) {
            preview.setViewport(null);
            return;
        }
        Rectangle2D b = bounds(size);
        Rectangle2D vp;
        if (zoom <= 0) {
            boolean whole = b.getMinX() <= 0 && b.getMinY() <= 0 && b.getWidth() >= size[0] && b.getHeight() >= size[1];
            vp = whole ? null : b;
        } else {
            double[] a = available();
            double w = Math.min(b.getWidth(), a[0] / zoom);
            double h = Math.min(b.getHeight(), a[1] / zoom);
            if (Double.isNaN(centerX)) {
                centerX = (b.getMinX() + b.getMaxX()) / 2;
                centerY = (b.getMinY() + b.getMaxY()) / 2;
            }
            centerX = Math.max(b.getMinX() + w / 2, Math.min(b.getMaxX() - w / 2, centerX));
            centerY = Math.max(b.getMinY() + h / 2, Math.min(b.getMaxY() - h / 2, centerY));
            vp = new Rectangle2D(centerX - w / 2, centerY - h / 2, w, h);
        }
        preview.setViewport(vp);
        overlay.redraw();
        regions.redraw();
    }

    /** @param z screen pixels per pixel of the original, 0 = fit */
    private void zoomTo(double z) {
        double[] size = file == null ? null : size();
        if (size == null) {
            return;
        }
        if (overlay.mode() == CropRotateOverlay.Mode.CROP) {
            z = 0;                      // the frame needs the whole photo
        }
        zoom = z <= 0 ? 0 : Math.min(z, MAX_ZOOM * oneToOneZoom());
        if (zoom > 0 && zoom <= fitZoom(size) * 1.0001) {
            zoom = 0;
        }
        if (zoom == 0) {
            centerX = centerY = Double.NaN;
        }
        applyView();
    }

    private void zoomBy(double factor) {
        double[] size = file == null ? null : size();
        if (size == null) {
            return;
        }
        double current = zoom > 0 ? zoom : fitZoom(size);
        zoomTo(current * factor);
    }

    /** Moves the zoomed view by screen pixels. */
    private void pan(double dx, double dy) {
        if (zoom <= 0 || Double.isNaN(centerX)) {
            return;
        }
        double r = Math.toRadians(normalized(file.getRotationAngleProperty().get()));
        // screen direction -> direction in the unturned photo
        double ix = dx * Math.cos(r) + dy * Math.sin(r);
        double iy = -dx * Math.sin(r) + dy * Math.cos(r);
        centerX += ix / zoom;
        centerY += iy / zoom;
        applyView();
    }

    // ------------------------------------------------------------------
    // modes
    // ------------------------------------------------------------------
    private void startCrop() {
        if (file == null) {
            crop.setSelected(false);
            return;
        }
        if (overlay.mode() == CropRotateOverlay.Mode.ROTATE) {
            endMode();
        }
        crop.setSelected(true);
        regions.cancel();
        zoom = 0;
        centerX = centerY = Double.NaN;
        overlay.startCrop(file.getCropView(), this::finishCrop, this::endMode);
        double[] size = size();
        if (size != null) {
            aspect.getSelectionModel().select(CropAspect.initial(file.getCropView(), size[0], size[1], quarter()));
        }
        overlay.setAspect(aspectValue());
        showModeBar(cropControls);
        applyView();
        updateButtons();
    }

    private double aspectValue() {
        double[] size = file == null ? null : size();
        return size == null ? 0 : CropAspect.frameRatio(aspect.getSelectionModel().getSelectedIndex(),
                size[0], size[1], quarter());
    }

    private void finishCrop(Rectangle2D r) {
        MediaFile f = file;
        endMode();
        if (f != null && !Objects.equals(r, f.getCropView())) {
            f.setCropView(r);
            EditPipeline.filtersChanged(f, r == null ? "Reset crop" : "Crop");
        }
        applyView();
    }

    private void startRotate() {
        if (file == null) {
            rotate.setSelected(false);
            return;
        }
        if (overlay.mode() == CropRotateOverlay.Mode.CROP) {
            endMode();
        }
        rotate.setSelected(true);
        regions.cancel();
        updateSlider();
        overlay.startRotate(delta -> setStraighten(file.getGeometry().straighten() + delta), this::endMode);
        showModeBar(rotateControls);
        updateButtons();
    }

    /** Leaves crop or rotate mode (an unapplied crop frame is dropped). */
    private void endMode() {
        boolean wasCrop = overlay.mode() == CropRotateOverlay.Mode.CROP;
        overlay.stop();
        crop.setSelected(false);
        rotate.setSelected(false);
        modeBar.setVisible(false);
        modeBar.setManaged(false);
        updateButtons();
        if (wasCrop) {
            applyView();
        }
        stack.requestFocus();
    }

    private void showModeBar(HBox controls) {
        modeBar.getItems().setAll(controls);
        modeBar.setVisible(true);
        modeBar.setManaged(true);
    }

    private void setStraighten(double degrees) {
        if (file == null) {
            return;
        }
        Geometry g = file.getGeometry();
        Geometry n = g.withStraighten(degrees);
        if (n.equals(g)) {
            return;
        }
        file.setGeometry(n);
        updateSlider();
        EditPipeline.filtersChanged(file, "Straighten");
    }

    // ------------------------------------------------------------------
    // quarter turns and flips
    // ------------------------------------------------------------------
    private void turn(double degrees) {
        if (file == null) {
            return;
        }
        file.setRotationAngle(normalized(file.getRotationAngleProperty().get() + degrees));
        EditPipeline.filtersChanged(file, degrees < 0 ? "Rotate left" : "Rotate right");
        if (zoom > 0) {
            zoom = 0;
            centerX = centerY = Double.NaN;
        }
        applyView();
    }

    /** @param horizontal mirrored left/right on screen (top/bottom of the turned photo) */
    private void flip(boolean horizontal) {
        double[] size = file == null ? null : size();
        if (size == null) {
            return;
        }
        boolean h = horizontal != quarter();
        Geometry g = file.getGeometry();
        Geometry n = h ? g.withFlipH(!g.flipH()) : g.withFlipV(!g.flipV());
        // a mirrored photo stays straight with the opposite angle
        file.setGeometry(n.withStraighten(-n.straighten()));
        Rectangle2D c = file.getCropView();
        if (c != null) {
            file.setCropView(h
                    ? new Rectangle2D(size[0] - c.getMaxX(), c.getMinY(), c.getWidth(), c.getHeight())
                    : new Rectangle2D(c.getMinX(), size[1] - c.getMaxY(), c.getWidth(), c.getHeight()));
        }
        if (!Double.isNaN(centerX)) {
            if (h) {
                centerX = size[0] - centerX;
            } else {
                centerY = size[1] - centerY;
            }
        }
        updateSlider();
        EditPipeline.filtersChanged(file, horizontal ? "Flip horizontal" : "Flip vertical");
        applyView();
    }
}
