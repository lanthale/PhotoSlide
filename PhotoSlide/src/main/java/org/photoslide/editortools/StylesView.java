package org.photoslide.editortools;

import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.scene.control.Accordion;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.Styles;

/**
 * The styles of LightZone, grouped by category: a click adds the tools of
 * the style on top of the tools of the shown photo. Collapsed until opened.
 */
public final class StylesView {

    private final TitledPane pane = new TitledPane();
    private MediaFile file;

    public StylesView() {
        Accordion categories = new Accordion();
        for (Map.Entry<String, List<Styles.Style>> e : Styles.byCategory().entrySet()) {
            VBox list = new VBox(0);
            list.setPadding(new Insets(2, 4, 2, 4));
            for (Styles.Style style : e.getValue()) {
                Hyperlink link = new Hyperlink(style.name());
                link.setTooltip(new Tooltip(describe(style)));
                link.setOnAction(a -> {
                    link.setVisited(false);
                    if (file != null) {
                        Styles.apply(file, style);
                    }
                });
                list.getChildren().add(link);
            }
            TitledPane category = new TitledPane(e.getKey(), list);
            category.setAnimated(false);
            categories.getPanes().add(category);
        }
        ScrollPane scroll = new ScrollPane(categories);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(260);
        pane.setText("Styles");
        pane.setGraphic(new FontIcon("fa-magic"));
        pane.setContent(scroll);
        pane.setAnimated(false);
        pane.setExpanded(false);
        pane.setDisable(true);
    }

    public TitledPane node() {
        return pane;
    }

    /** The photo the styles are applied to (null = none). */
    public void setFile(MediaFile f) {
        file = f;
        pane.setDisable(f == null);
    }

    private static String describe(Styles.Style style) {
        StringBuilder b = new StringBuilder("Adds: ");
        for (int i = 0; i < style.tools().size(); i++) {
            AbstractImageFilter f = style.tools().get(i).get();
            b.append(i == 0 ? "" : ", ").append(f.displayName());
        }
        return b.toString();
    }
}
