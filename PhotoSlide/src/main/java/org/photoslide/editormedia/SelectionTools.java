package org.photoslide.editormedia;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import org.controlsfx.control.PopOver;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.imageops.mask.ColorSelection;
import org.photoslide.imageops.mask.Contour;
import org.photoslide.imageops.mask.Region;

/**
 * The selection tools of the editor toolbar: lasso, rectangle, ellipse,
 * colour pick, invert, soft edge and clear. The selection belongs to the
 * shown image; tools added while something is selected act only there,
 * without a selection they act on the whole image.
 */
final class SelectionTools {

    private final RegionOverlay overlay;
    private final Function<Contour.Point, double[]> colorAt;
    private Region region = Region.EMPTY;
    private ColorSelection colors = ColorSelection.NONE;
    private float feather = 40;

    private final ToggleButton invert = new ToggleButton("Invert");
    private final Button clear;
    private final Rectangle swatch = new Rectangle(12, 12);
    private final Label info = new Label();
    private final PopOver menu = new PopOver();
    private static final int TOOL_ICON = 18;

    /**
     * @param colorAt colour of the edited image at a point (pixels of the
     * original, linear working space), null if not available
     */
    SelectionTools(ToolBar bar, RegionOverlay overlay, Function<Contour.Point, double[]> colorAt) {
        this.overlay = overlay;
        this.colorAt = colorAt;
        // outlines moved or resized on the image
        overlay.setOnSelectionEdited(r -> {
            region = r;
            changed();
        });

        Button lasso = tool("Lasso", "fa-pencil",
                "Hold the mouse button and draw around the area (or click points and double click to close); Esc cancels");
        lasso.setOnAction(e -> overlay.requestOutline(pts -> add(new Contour(pts, feather, true, Double.NaN, Double.NaN))));
        Button rect = tool("Rectangle", "fa-square-o", "Hold the mouse button and drag the rectangle");
        rect.setOnAction(e -> overlay.requestBox(false, box -> addBox(box, false)));
        Button ellipse = tool("Ellipse", "fa-circle-thin", "Hold the mouse button and drag the ellipse");
        ellipse.setOnAction(e -> overlay.requestBox(true, box -> addBox(box, true)));
        Button pick = tool("Color", "fa-eyedropper", "Select by colour: click a colour in the image");
        pick.setOnAction(e -> overlay.requestPoint(p -> {
            double[] rgb = colorAt.apply(p);
            if (rgb != null) {
                colors = ColorSelection.around(rgb);
                changed();
            }
        }));
        invert.setId("toolbutton");
        invert.setContentDisplay(javafx.scene.control.ContentDisplay.TOP);
        invert.setGraphic(new FontIcon("fa-adjust"));
        invert.setFont(new Font(11));
        invert.setTooltip(new Tooltip("Apply to everything outside the selection"));
        invert.setOnAction(e -> {
            region = region.withInverted(invert.isSelected());
            colors = new ColorSelection(colors.hueEnabled(), colors.hueCenter(), colors.hueRange(), colors.hueFeather(),
                    colors.lumEnabled(), colors.lumLower(), colors.lumUpper(), colors.lumFeather(), invert.isSelected());
            changed();
        });
        clear = tool("Clear", "fa-times", "Clear the selection: new tools act on the whole image");
        clear.setOnAction(e -> clear());

        Slider soft = new Slider(0, 300, feather);
        soft.setPrefWidth(90);
        Label softLabel = new Label(featherText());
        softLabel.setFont(new Font(11));
        soft.valueProperty().addListener(o -> {
            feather = (float) soft.getValue();
            softLabel.setText(featherText());
            List<Contour> l = new ArrayList<>();
            for (Contour c : region.contours()) {
                l.add(c.withFeather(feather));
            }
            if (!l.isEmpty()) {
                region = region.withContours(l);
                changed();
            }
        });
        soft.setTooltip(new Tooltip("Soft edge of the selection"));
        VBox softBox = new VBox(0, soft, softLabel);
        softBox.setAlignment(Pos.CENTER);

        info.setFont(new Font(11));
        info.setTextFill(Color.web("#9f9f9f"));
        swatch.setVisible(false);
        // all selection tools in one button that opens a vertical tool bar
        for (Button b : new Button[]{lasso, rect, ellipse, pick, clear}) {
            b.addEventHandler(ActionEvent.ACTION, e -> menu.hide());
        }
        invert.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        invert.setMaxWidth(Double.MAX_VALUE);
        invert.setAlignment(Pos.CENTER_LEFT);
        ((FontIcon) invert.getGraphic()).setIconSize(TOOL_ICON);
        invert.setFont(new Font(13));
        VBox tools = new VBox(2, lasso, rect, ellipse, pick, new Separator(), invert, softBox, new Separator(), clear);
        tools.setPadding(new Insets(6));
        tools.setFillWidth(true);
        tools.setStyle("-fx-background-color: #3a3a3a;");
        menu.setContentNode(tools);
        menu.setArrowLocation(PopOver.ArrowLocation.TOP_LEFT);
        menu.setDetachable(false);
        menu.setAutoHide(true);
        menu.setHideOnEscape(true);
        menu.setAnimated(false);
        FontIcon icon = new FontIcon("fa-object-group");
        icon.setIconSize(ViewTools.ICON_SIZE);
        Button select = new Button(null, icon);
        select.setId("toolbutton");
        select.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        select.setTooltip(new Tooltip("Selection tools: lasso, rectangle, ellipse, colour, invert, soft edge"));
        select.setFocusTraversable(false);
        select.setOnAction(e -> {
            if (menu.isShowing()) {
                menu.hide();
            } else {
                menu.show(select);
            }
        });
        bar.getItems().addAll(new Separator(), select, swatch, info);
        changed();
    }

    /** One entry of the vertical tool bar: icon and text side by side. */
    private static Button tool(String text, String icon, String tip) {
        FontIcon graphic = new FontIcon(icon);
        graphic.setIconSize(TOOL_ICON);
        Button b = new Button(text, graphic);
        b.setId("toolbutton");
        b.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setGraphicTextGap(8);
        b.setFont(new Font(13));
        b.setTooltip(new Tooltip(tip));
        return b;
    }

    private String featherText() {
        return String.format(Locale.ROOT, "Soft edge %.0f px", feather);
    }

    /** Adds a dragged rectangle or ellipse ({x0, y0, x1, y1}, pixels of the original). */
    private void addBox(double[] box, boolean ellipse) {
        double x0 = box[0], y0 = box[1], x1 = box[2], y1 = box[3];
        List<Contour.Point> pts = new ArrayList<>();
        if (ellipse) {
            double cx = (x0 + x1) / 2, cy = (y0 + y1) / 2, rx = (x1 - x0) / 2, ry = (y1 - y0) / 2;
            for (int i = 0; i < 32; i++) {
                double t = 2 * Math.PI * i / 32;
                pts.add(new Contour.Point(cx + rx * Math.cos(t), cy + ry * Math.sin(t)));
            }
        } else {
            pts.add(new Contour.Point(x0, y0));
            pts.add(new Contour.Point(x1, y0));
            pts.add(new Contour.Point(x1, y1));
            pts.add(new Contour.Point(x0, y1));
        }
        add(new Contour(pts, feather, ellipse, Double.NaN, Double.NaN));
    }

    private void add(Contour c) {
        region = region.with(c);
        changed();
    }

    /** No selection (new image). */
    void clear() {
        overlay.cancel();
        region = Region.EMPTY;
        colors = ColorSelection.NONE;
        invert.setSelected(false);
        changed();
    }

    Region region() {
        return region;
    }

    ColorSelection colors() {
        return colors;
    }

    private void changed() {
        overlay.setSelection(region);
        boolean any = !region.isEmpty() || colors.isActive();
        clear.setDisable(!any);
        swatch.setVisible(colors.isActive());
        if (colors.isActive()) {
            swatch.setFill(Color.hsb(colors.hueCenter() * 360, 0.8, 0.9));
        }
        StringBuilder sb = new StringBuilder();
        if (!any) {
            sb.append("No selection: tools act on the whole image");
        } else {
            if (!region.isEmpty()) {
                sb.append(region.contours().size()).append(" area(s)");
            }
            if (colors.isActive()) {
                sb.append(sb.length() > 0 ? " + " : "").append("color");
            }
            if (invert.isSelected()) {
                sb.append(", inverted");
            }
        }
        info.setText(sb.toString());
    }
}
