package org.photoslide.editortools;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TitledPane;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.editpipeline.EditHistory;

/**
 * The edit history of the shown photo: every step from the original to the
 * last change. A click on a step shows the photo in that state (later steps
 * stay until a new change is made). Collapsed until opened.
 */
public final class HistoryView {

    private final TitledPane pane = new TitledPane();
    private final ListView<EditHistory.Entry> list = new ListView<>();
    private final ChangeListener<Number> currentListener = (o, oldValue, newValue) -> select();
    /** A merged step replaces the selected entry without moving the current step. */
    private final ListChangeListener<EditHistory.Entry> entriesListener = c -> Platform.runLater(this::select);
    private MediaFile file;
    private EditHistory history;

    public HistoryView() {
        list.setPrefHeight(220);
        list.getStyleClass().add("history-list");
        list.setCellFactory(l -> new ListCell<>() {
            @Override
            protected void updateItem(EditHistory.Entry item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("undone");
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(item.label());
                if (history != null && getIndex() > history.current()) {
                    getStyleClass().add("undone");          // steps after the current one (redo)
                }
                setGraphic(getIndex() == 0 ? new FontIcon("fa-picture-o") : null);
            }
        });
        // only the user picks a step: the selection also moves when steps are added or dropped
        list.setOnMouseClicked(e -> goToSelected());
        list.setOnKeyReleased(e -> {
            switch (e.getCode()) {
                case UP, DOWN, HOME, END, ENTER -> goToSelected();
                default -> {
                }
            }
        });
        pane.setText("History");
        pane.setGraphic(new FontIcon("fa-history"));
        pane.setContent(list);
        pane.setAnimated(false);
        pane.setExpanded(false);
        pane.setDisable(true);
    }

    public TitledPane node() {
        return pane;
    }

    /** Shows the history of the photo (null = none). */
    public void setFile(MediaFile f) {
        if (history != null) {
            history.currentProperty().removeListener(currentListener);
            history.entries().removeListener(entriesListener);
        }
        file = f;
        history = f == null ? null : EditHistory.of(f);
        pane.setDisable(f == null);
        if (history == null) {
            list.setItems(null);
            return;
        }
        history.currentProperty().addListener(currentListener);
        history.entries().addListener(entriesListener);
        list.setItems(history.entries());
        select();
    }

    private void goToSelected() {
        int index = list.getSelectionModel().getSelectedIndex();
        if (history != null && file != null && index >= 0) {
            history.goTo(file, index);
        }
    }

    private void select() {
        if (history == null) {
            return;
        }
        list.getSelectionModel().select(history.current());
        list.scrollTo(history.current());
        list.refresh();
    }
}
