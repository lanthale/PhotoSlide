package org.photoslide.editortools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.datamodel.Display16;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.datamodel.WorkingSpace;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.BlendMode;
import org.photoslide.editpipeline.EditPipeline;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.imageops.ParamSpec;
import org.photoslide.imageops.Pickable;
import org.photoslide.imageops.ToolRegistry;
import org.photoslide.imageops.lz.CloneFilter;
import org.photoslide.imageops.lz.SpotFilter;
import org.photoslide.imageops.mask.ColorSelection;
import org.photoslide.imageops.mask.Contour;
import org.photoslide.imageops.mask.Region;

/**
 * The stack of editing tools of one media file, as in LightZone: tools are
 * applied from top to bottom (first added = applied first), each with its
 * own settings, on/off switch, opacity and blend mode. The controls are built
 * from the ParamSpec list of each tool.
 */
public final class ToolStackView {

    /** What the editor image offers to the tools (all positions in pixels of the original). */
    public interface DropperHost {

        /** The next click on the image is passed on. */
        void requestPoint(Consumer<Contour.Point> onPoint);

        /** The user draws an outline on the image (at least 3 points). */
        void requestOutline(Consumer<List<Contour.Point>> onOutline);

        /** Click (default radius) or drag from the centre to set a circle. */
        void requestCircle(double defaultRadius, java.util.function.BiConsumer<Contour.Point, Double> onCircle);

        /** Ends a running request. */
        void cancelPick();

        /**
         * @return colour (linear working space) at the point as it enters the
         * tool, or null if the image is not loaded
         */
        double[] sampleBelow(AbstractImageFilter tool, Contour.Point p);

        /** @return width and height of the original, or null */
        double[] imageSize();

        /** Shows the region of the tool on the image (null = none). */
        void showRegion(AbstractImageFilter tool);

        /** Current selection of the editor (empty = whole image). */
        Region selectedRegion();

        /** Current colour selection of the editor (inactive = all colours). */
        ColorSelection selectedColors();
    }

    private final VBox box = new VBox(4);
    private final MenuButton addButton = new MenuButton("Add tool");
    private final DropperHost dropperHost;
    private MediaFile file;

    public ToolStackView(DropperHost dropperHost) {
        this.dropperHost = dropperHost;
        box.setFillWidth(true);
        addButton.setGraphic(new FontIcon("fa-plus"));
        for (ToolRegistry.Tool tool : ToolRegistry.tools()) {
            MenuItem item = new MenuItem(tool.create().displayName());
            item.setOnAction(e -> addTool(tool));
            addButton.getItems().add(item);
        }
        addButton.setDisable(true);
    }

    /** The "add tool" menu button (placed by the controller). */
    public MenuButton addButton() {
        return addButton;
    }

    /** The node holding the tool panes. */
    public VBox node() {
        return box;
    }

    /** Shows the tools of the file (null = none). */
    public void setFile(MediaFile f) {
        dropperHost.cancelPick();
        dropperHost.showRegion(null);
        file = f;
        addButton.setDisable(f == null);
        rebuild();
    }

    private List<AbstractImageFilter> tools() {
        List<AbstractImageFilter> list = new ArrayList<>();
        if (file != null) {
            for (ImageFilter f : file.getFilterList()) {
                if (f instanceof AbstractImageFilter a) {
                    list.add(a);
                }
            }
        }
        list.sort(Comparator.comparingInt(ImageFilter::getPosition));
        return list;
    }

    /** Renumbers the positions in list order and redraws. */
    private void structureChanged(List<AbstractImageFilter> ordered) {
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition(i);
        }
        rebuild();
        changed();
    }

    private void changed() {
        if (file != null) {
            EditPipeline.filtersChanged(file);
        }
    }

    private void addTool(ToolRegistry.Tool tool) {
        if (file == null) {
            return;
        }
        List<AbstractImageFilter> list = tools();
        AbstractImageFilter f = tool.create();
        if (!(f instanceof CloneFilter)) {
            // the new tool acts on the current selection (nothing selected = whole image)
            f.setRegion(dropperHost.selectedRegion());
            f.setColorSelection(dropperHost.selectedColors());
        }
        file.getFilterList().add(f);
        list.add(f);
        structureChanged(list);
    }

    private void rebuild() {
        box.getChildren().clear();
        List<AbstractImageFilter> list = tools();
        for (AbstractImageFilter f : list) {
            box.getChildren().add(pane(f, list));
        }
        if (list.isEmpty()) {
            Label hint = new Label(file == null ? "" : "No tools yet. Add one with \"Add tool\".");
            hint.setWrapText(true);
            hint.setTextFill(Color.web("#9f9f9f"));
            box.getChildren().add(hint);
        }
    }

    private TitledPane pane(AbstractImageFilter f, List<AbstractImageFilter> list) {
        VBox content = new VBox(6);
        content.setPadding(new Insets(4));
        for (ParamSpec p : f.params()) {
            content.getChildren().add(control(f, p));
        }
        if (f instanceof Pickable pickable) {
            Button dropper = new Button("Pick", new FontIcon("fa-eyedropper"));
            dropper.setTooltip(new Tooltip(pickable.pickLabel()));
            dropper.setOnAction(e -> dropperHost.requestPoint(p -> {
                double[] rgb = dropperHost.sampleBelow(f, p);
                if (rgb != null) {
                    pickable.pick(rgb);
                    rebuild();
                    changed();
                }
            }));
            content.getChildren().add(dropper);
        }
        if (f instanceof CloneFilter) {
            content.getChildren().add(cloneControls(f));
        } else {
            content.getChildren().add(areaControls(f));
        }
        content.getChildren().add(layerControls(f));

        CheckBox active = new CheckBox();
        active.setSelected(f.isActive());
        active.setTooltip(new Tooltip("On / off"));
        active.setOnAction(e -> {
            f.setActive(active.isSelected());
            changed();
        });
        Label title = new Label(f.displayName());
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        int index = list.indexOf(f);
        Button up = iconButton("fa-arrow-up", "Apply earlier");
        up.setDisable(index == 0);
        up.setOnAction(e -> {
            list.remove(f);
            list.add(index - 1, f);
            structureChanged(list);
        });
        Button down = iconButton("fa-arrow-down", "Apply later");
        down.setDisable(index == list.size() - 1);
        down.setOnAction(e -> {
            list.remove(f);
            list.add(index + 1, f);
            structureChanged(list);
        });
        Button reset = iconButton("fa-undo", "Reset settings");
        reset.setOnAction(e -> {
            f.reset();
            rebuild();
            changed();
        });
        Button delete = iconButton("fa-trash", "Remove tool");
        delete.setOnAction(e -> {
            file.getFilterList().remove(f);
            list.remove(f);
            structureChanged(list);
        });
        HBox header = new HBox(4, active, title, spacer, up, down, reset, delete);
        header.setAlignment(Pos.CENTER_LEFT);
        header.minWidthProperty().bind(box.widthProperty().subtract(40));

        TitledPane pane = new TitledPane();
        pane.setGraphic(header);
        pane.setOnMouseClicked(e -> dropperHost.showRegion(f));
        pane.setContent(content);
        pane.setAnimated(false);
        pane.setExpanded(true);
        return pane;
    }

    // ------------------------------------------------------------------
    // area of a tool, clone areas
    // ------------------------------------------------------------------
    /** Feather and curve style for new clone outlines. */
    private float newFeather = 40;
    private boolean newSmooth = true;

    /** Where the tool acts: whole image or the selection it got; can be changed. */
    private Node areaControls(AbstractImageFilter f) {
        Region region = f.getRegion();
        ColorSelection cs = f.getColorSelection();
        StringBuilder text = new StringBuilder("Acts on: ");
        if (region.isEmpty() && !cs.isActive()) {
            text.append("whole image");
        } else {
            if (!region.isEmpty()) {
                text.append(region.contours().size()).append(" area(s)");
            }
            if (cs.isActive()) {
                text.append(region.isEmpty() ? "" : " + ").append("color");
            }
            if (region.inverted() || cs.inverted()) {
                text.append(", inverted");
            }
        }
        Label label = new Label(text.toString());
        label.setTextFill(Color.web("#9f9f9f"));
        label.setOnMouseClicked(e -> dropperHost.showRegion(f));
        Button use = new Button("Use selection", new FontIcon("fa-crop"));
        use.setTooltip(new Tooltip("Apply this tool to the current selection of the toolbar"));
        use.setOnAction(e -> {
            f.setRegion(dropperHost.selectedRegion());
            f.setColorSelection(dropperHost.selectedColors());
            regionChanged(f);
        });
        Button whole = new Button("Whole image", new FontIcon("fa-expand"));
        whole.setDisable(region.isEmpty() && !cs.isActive());
        whole.setOnAction(e -> {
            f.setRegion(Region.EMPTY);
            f.setColorSelection(ColorSelection.NONE);
            regionChanged(f);
        });
        VBox box = new VBox(4, label, new HBox(4, use, whole));
        box.setPadding(new Insets(4, 0, 0, 0));
        return box;
    }

    private Node cloneControls(AbstractImageFilter f) {
        Region region = f.getRegion();
        VBox box = new VBox(6);
        boolean spot = f instanceof SpotFilter;
        Button add = new Button(spot ? "Add spot" : "Add clone area", new FontIcon("fa-plus-circle"));
        add.setTooltip(new Tooltip(spot ? "Click on the spot, or drag from its centre to set the size"
                : "Hold the mouse button and draw around the area to cover, then click the source"));
        add.setOnAction(e -> {
            dropperHost.showRegion(f);
            if (spot) {
                dropperHost.requestCircle(((SpotFilter) f).spotRadius(), (p, r) -> {
                    Contour c = Contour.circle(p.x(), p.y(), r, (float) (r * 0.6));
                    double[] size = dropperHost.imageSize();
                    double sx = p.x() + 2.5 * r;
                    if (size != null && sx > size[0] - r) {
                        sx = p.x() - 2.5 * r;
                    }
                    f.setRegion(f.getRegion().with(c.withSource(sx, p.y())));
                    regionChanged(f);
                });
            } else {
                dropperHost.requestOutline(pts -> {
                    Contour c = new Contour(pts, newFeather, newSmooth, Double.NaN, Double.NaN);
                    f.setRegion(f.getRegion().with(c));
                    regionChanged(f);
                    dropperHost.requestPoint(src -> setLastSource(f, src));
                });
            }
        });
        Button source = new Button("Move source", new FontIcon("fa-crosshairs"));
        source.setTooltip(new Tooltip("Click where the last area should be copied from"));
        source.setDisable(region.isEmpty());
        source.setOnAction(e -> {
            dropperHost.showRegion(f);
            dropperHost.requestPoint(src -> setLastSource(f, src));
        });
        Button undo = new Button("Remove last", new FontIcon("fa-undo"));
        undo.setDisable(region.isEmpty());
        undo.setOnAction(e -> {
            List<Contour> l = new ArrayList<>(f.getRegion().contours());
            l.remove(l.size() - 1);
            f.setRegion(f.getRegion().withContours(l));
            regionChanged(f);
        });
        Label count = new Label(region.contours().size() + (spot ? " spot(s)" : " area(s)"));
        count.setTextFill(Color.web("#9f9f9f"));
        box.getChildren().addAll(new HBox(4, add, source), new HBox(4, undo, count));
        if (!spot) {
            box.getChildren().add(featherSlider(f, region.isEmpty() ? newFeather : region.contours().get(0).feather()));
        }
        return box;
    }

    private void setLastSource(AbstractImageFilter f, Contour.Point src) {
        List<Contour> l = new ArrayList<>(f.getRegion().contours());
        if (l.isEmpty()) {
            return;
        }
        l.set(l.size() - 1, l.get(l.size() - 1).withSource(src.x(), src.y()));
        f.setRegion(f.getRegion().withContours(l));
        regionChanged(f);
    }

    /** Soft edge of all outlines of the tool (and of new ones). */
    private Node featherSlider(AbstractImageFilter f, float value) {
        Slider feather = new Slider(0, 500, value);
        Label v = new Label(format(value, 0) + " px");
        v.setMinWidth(46);
        feather.valueProperty().addListener(o -> {
            newFeather = (float) feather.getValue();
            v.setText(format(newFeather, 0) + " px");
            List<Contour> l = new ArrayList<>();
            for (Contour c : f.getRegion().contours()) {
                l.add(c.withFeather(newFeather));
            }
            if (!l.isEmpty()) {
                f.setRegion(f.getRegion().withContours(l));
                changed();
            }
        });
        HBox.setHgrow(feather, Priority.ALWAYS);
        return new VBox(0, new Label("Soft edge"), new HBox(4, feather, v));
    }

    private void regionChanged(AbstractImageFilter f) {
        rebuild();
        changed();
        dropperHost.showRegion(f);
    }

    private static Button iconButton(String icon, String tip) {
        Button b = new Button(null, new FontIcon(icon));
        b.setId("toolbutton");
        b.setTooltip(new Tooltip(tip));
        b.setFocusTraversable(false);
        return b;
    }

    private Node layerControls(AbstractImageFilter f) {
        Slider opacity = new Slider(0, 100, f.getOpacity() * 100);
        Label value = new Label(format(f.getOpacity() * 100, 1) + " %");
        value.setMinWidth(46);
        opacity.valueProperty().addListener(o -> {
            value.setText(format(opacity.getValue(), 1) + " %");
            f.setOpacity((float) (opacity.getValue() / 100));
            changed();
        });
        HBox.setHgrow(opacity, Priority.ALWAYS);
        ComboBox<BlendMode> mode = new ComboBox<>();
        mode.getItems().addAll(BlendMode.values());
        mode.setValue(f.getBlendMode());
        mode.setMaxWidth(Double.MAX_VALUE);
        mode.setOnAction(e -> {
            f.setBlendMode(mode.getValue());
            changed();
        });
        VBox layer = new VBox(2, new Label("Opacity / Mode"), new HBox(4, opacity, value), mode);
        layer.setPadding(new Insets(4, 0, 0, 0));
        return layer;
    }

    private Node control(AbstractImageFilter f, ParamSpec p) {
        float[] v = f.getValues();
        switch (p.kind()) {
            case SLIDER -> {
                Slider slider = new Slider(p.min(), p.max(), v[p.index()]);
                slider.setBlockIncrement(p.step());
                int decimals = p.step() >= 1 ? 0 : (p.step() >= 0.1 ? 1 : (p.step() >= 0.01 ? 2 : 3));
                Label value = new Label(format(v[p.index()], decimals));
                value.setMinWidth(46);
                slider.valueProperty().addListener(o -> {
                    double x = Math.round(slider.getValue() / p.step()) * (double) p.step();
                    value.setText(format(x, decimals));
                    f.setValue(p.index(), (float) x);
                    changed();
                });
                // double click on the label resets the value
                Label label = new Label(p.label());
                label.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) {
                        slider.setValue(p.defaults()[0]);
                    }
                });
                HBox.setHgrow(slider, Priority.ALWAYS);
                return new VBox(0, label, new HBox(4, slider, value));
            }
            case CHECK -> {
                CheckBox check = new CheckBox(p.label());
                check.setSelected(v[p.index()] >= 0.5f);
                check.setOnAction(e -> {
                    f.setValue(p.index(), check.isSelected() ? 1 : 0);
                    changed();
                });
                return check;
            }
            case CHOICE -> {
                ComboBox<String> combo = new ComboBox<>();
                combo.getItems().addAll(p.choices());
                combo.getSelectionModel().select(Math.max(0, Math.min(p.choices().size() - 1, (int) v[p.index()])));
                combo.setOnAction(e -> {
                    f.setValue(p.index(), combo.getSelectionModel().getSelectedIndex());
                    changed();
                });
                return new HBox(6, new Label(p.label()), combo);
            }
            case COLOR -> {
                ColorPicker picker = new ColorPicker(Color.color(clamp(v[p.index()]),
                        clamp(v[p.index() + 1]), clamp(v[p.index() + 2])));
                picker.setOnAction(e -> {
                    Color c = picker.getValue();
                    f.setValue(p.index(), (float) c.getRed());
                    f.setValue(p.index() + 1, (float) c.getGreen());
                    f.setValue(p.index() + 2, (float) c.getBlue());
                    changed();
                });
                return new VBox(0, new Label(p.label()), picker);
            }
            case PICK -> {
                Rectangle swatch = new Rectangle(28, 16);
                Label text = new Label();
                if (v[p.index()] < 0) {
                    swatch.setFill(Color.TRANSPARENT);
                    swatch.setStroke(Color.GRAY);
                    text.setText("not picked yet");
                } else {
                    double[] s = WorkingSpace.multiply(WorkingSpace.WORKING_TO_SRGB,
                            new double[]{v[p.index()], v[p.index() + 1], v[p.index() + 2]});
                    swatch.setFill(Color.color(enc(s[0]), enc(s[1]), enc(s[2])));
                    text.setText("");
                }
                HBox row = new HBox(6, swatch, text);
                row.setAlignment(Pos.CENTER_LEFT);
                return new VBox(2, new Label(p.label()), row);
            }
            case ZONES -> {
                float[] zones = new float[p.count()];
                System.arraycopy(v, p.index(), zones, 0, p.count());
                ZoneMapperControl zm = new ZoneMapperControl(zones, values -> {
                    for (int i = 0; i < values.length; i++) {
                        f.setValue(p.index() + i, values[i]);
                    }
                    changed();
                });
                Label hint = new Label("Drag a zone up or down, double click to release it");
                hint.setWrapText(true);
                hint.setTextFill(Color.web("#9f9f9f"));
                return new VBox(2, zm, hint);
            }
            default -> {
                return new Label(p.label());
            }
        }
    }

    private static double enc(double linear) {
        return Display16.srgbEncode(Math.max(0, Math.min(1, linear)));
    }

    private static double clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static String format(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}
