package org.photoslide.editortools;


import org.photoslide.I18n;
import java.util.List;
import java.util.Map;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.Styles;

/**
 * The styles of LightZone in a tree grouped by category (like the directory
 * tree of the browser): a click on a style adds its tools on top of the
 * tools of the shown photo. Shown expanded.
 */
public final class StylesView {

    /** A tree row: a category (style == null) or a style. */
    private record Entry(String label, Styles.Style style) {
    }

    private final TitledPane pane = new TitledPane();
    private final TreeView<Entry> tree = new TreeView<>();
    private MediaFile file;

    public StylesView() {
        TreeItem<Entry> root = new TreeItem<>(new Entry(I18n.t("styles.title"), null));
        for (Map.Entry<String, List<Styles.Style>> e : Styles.byCategory().entrySet()) {
            TreeItem<Entry> category = new TreeItem<>(new Entry(e.getKey(), null));
            for (Styles.Style style : e.getValue()) {
                category.getChildren().add(new TreeItem<>(new Entry(style.name(), style)));
            }
            root.getChildren().add(category);
        }
        tree.setRoot(root);
        tree.setShowRoot(false);
        tree.setPrefHeight(260);
        tree.setCellFactory(t -> new TreeCell<>() {
            {
                setOnMouseClicked(m -> {
                    if (m.getButton() == MouseButton.PRIMARY && m.getClickCount() == 1
                            && !isEmpty() && getItem().style() != null) {
                        apply(getItem().style());
                    }
                });
            }

            @Override
            protected void updateItem(Entry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item.label());
                    setTooltip(item.style() == null ? null : new Tooltip(describe(item.style())));
                }
            }
        });
        tree.setOnKeyPressed(k -> {
            TreeItem<Entry> selected = tree.getSelectionModel().getSelectedItem();
            if (k.getCode() == KeyCode.ENTER && selected != null && selected.getValue().style() != null) {
                apply(selected.getValue().style());
            }
        });
        pane.setText(I18n.t("styles.title"));
        pane.setGraphic(new FontIcon("fa-magic"));
        pane.setContent(tree);
        pane.setAnimated(false);
        pane.setExpanded(true);
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

    private void apply(Styles.Style style) {
        if (file != null) {
            Styles.apply(file, style);
        }
    }

    private static String describe(Styles.Style style) {
        StringBuilder b = new StringBuilder(I18n.t("styles.adds"));
        for (int i = 0; i < style.tools().size(); i++) {
            AbstractImageFilter f = style.tools().get(i).get();
            b.append(i == 0 ? "" : ", ").append(f.displayName());
        }
        return b.toString();
    }
}
