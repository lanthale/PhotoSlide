/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;

import java.io.IOException;
import java.net.URL;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.PauseTransition;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.controlsfx.control.GridView;
import org.controlsfx.control.textfield.CustomTextField;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.MainViewController;
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.Utility;
import org.photoslide.browsercollections.CollectionsController;
import org.photoslide.browsermetadata.MetadataController;
import org.photoslide.datamodel.MediaFile;

/**
 *
 * @author selfemp
 */
public class SearchToolsController implements Initializable {

    private ObservableList<MediaFile> fullMediaList;
    private FilteredList<MediaFile> filteredMediaList;
    private SortedList<MediaFile> sortedMediaList;
    private GridView<MediaFile> imageGrid;
    private ScheduledExecutorService executorParallel;
    // waits until the user stops typing
    private PauseTransition searchDelay;
    private SRMediaLoadingTask task;
    @FXML
    private CustomTextField searchTextField;
    private ProgressIndicator progressInd;
    @FXML
    private Button closeAction;
    @FXML
    private FontIcon sIcon;
    @FXML
    private VBox searchResultVBox;
    private MediaGridCellSearchFactory factory;
    @FXML
    private AnchorPane mainSearchPane;
    private DialogPane dialogPane;
    private double diaglogHeight;
    private Button clearButton;
    @FXML
    private HBox toolbar;
    private CollectionsController collectionsController;
    @FXML
    private Label mediaFileInfoLabel;
    @FXML
    private HBox infoBox;
    private Utility util;
    @FXML
    private ProgressIndicator searchProgress;
    @FXML
    private Label searchLabel;

    private MainViewController mainViewController;
    private MetadataController metadataController;
    @FXML
    private Slider mediaZoomSlider;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        util = new Utility();
        progressInd = new ProgressIndicator();
        progressInd.setVisible(false);
        searchProgress.setVisible(false);
        searchLabel.setVisible(false);
        progressInd.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        searchTextField.setRight(progressInd);
        clearButton = new Button();
        clearButton.setId("toolbutton");
        FontIcon clearIcon = new FontIcon("ti-close");
        clearButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        clearButton.setGraphic(clearIcon);
        clearButton.setOnAction((t) -> {
            searchTextField.clear();
            toolbar.setVisible(false);
            toolbar.setManaged(false);
            searchProgress.setVisible(false);
            infoBox.setVisible(false);
            searchResultVBox.getChildren().clear();
            dialogPane.getScene().getWindow().setHeight(diaglogHeight);
        });
        toolbar.setVisible(false);
        toolbar.setManaged(false);
        fullMediaList = FXCollections.observableArrayList(MediaFile.extractor());
        filteredMediaList = new FilteredList<>(fullMediaList, null);
        sortedMediaList = new SortedList<>(filteredMediaList);
        sortedMediaList.setComparator(Comparator.comparing(SearchToolsController::sortTime, Comparator.nullsLast(Comparator.reverseOrder())));
        executorParallel = Executors.newScheduledThreadPool(20, new ThreadFactoryBuilder().setNamePrefix("SearchToolExecutorScheduledParallel").build());
        imageGrid = new GridView<>(sortedMediaList);
        factory = new MediaGridCellSearchFactory(executorParallel, this, sortedMediaList);
        imageGrid.setCellFactory(factory);
        double defaultCellWidth = imageGrid.getCellWidth();
        double defaultCellHight = imageGrid.getCellHeight();
        imageGrid.setCellWidth(defaultCellWidth + 3 * 20);
        imageGrid.setCellHeight(defaultCellHight + 3 * 20);
        mediaZoomSlider.valueProperty().addListener((ObservableValue<? extends Number> ov, Number t, Number t1) -> {
            imageGrid.setCellWidth(defaultCellWidth + 3 * mediaZoomSlider.getValue());
            imageGrid.setCellHeight(defaultCellHight + 3 * mediaZoomSlider.getValue());
        });
        searchDelay = new PauseTransition(Duration.millis(300));
        searchDelay.setOnFinished((t) -> {
            startSearch(searchTextField.getText().trim());
        });
        searchTextField.textProperty().addListener((ov, oldText, newText) -> {
            searchTextChanged(newText);
        });
    }

    /** Record time, or creation time of the file if it has none. */
    private static LocalDateTime sortTime(MediaFile m) {
        if (m.getRecordTime() != null) {
            return m.getRecordTime();
        }
        try {
            FileTime creationTime = m.getCreationTime();
            return creationTime == null ? null : LocalDateTime.ofInstant(creationTime.toInstant(), ZoneId.systemDefault());
        } catch (IOException ex) {
            return null;
        }
    }

    public void injectMainController(MainViewController mainC) {
        this.mainViewController = mainC;
    }

    public void injectCollectionsController(CollectionsController controller) {
        this.collectionsController = controller;
    }

    public void injectMetaDataController(MetadataController controller) {
        this.metadataController = controller;
    }

    public void shutdown() {
        cancelSearch();
        if (factory != null) {
            factory.shutdown();
        }
        searchDelay.stop();
        executorParallel.shutdown();
    }

    public Button getCloseAction() {
        return closeAction;
    }

    public void setCloseAction(Button closeAction) {
        this.closeAction = closeAction;
    }

    public CustomTextField getSearchTextField() {
        return searchTextField;
    }

    private void searchTextChanged(String text) {
        searchDelay.stop();
        cancelSearch();
        if (text == null || text.trim().length() < 3) {
            if (dialogPane != null) {
                dialogPane.getScene().getWindow().setHeight(diaglogHeight);
            }
            progressInd.setVisible(false);
            searchProgress.setVisible(false);
            searchLabel.setVisible(false);
            infoBox.setVisible(false);
            toolbar.setVisible(false);
            toolbar.setManaged(false);
            searchResultVBox.getChildren().clear();
            fullMediaList.clear();
            return;
        }
        searchTextField.setRight(progressInd);
        progressInd.setVisible(true);
        searchDelay.playFromStart();
    }

    private void cancelSearch() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void startSearch(String keyword) {
        cancelSearch();
        searchLabel.setText("Populating results...");
        searchProgress.setVisible(true);
        searchProgress.setManaged(true);
        searchLabel.setVisible(true);
        fullMediaList.clear();
        if (searchResultVBox.getChildren().contains(imageGrid) == false) {
            searchResultVBox.getChildren().setAll(imageGrid);
        }
        dialogPane.getScene().getWindow().setHeight(400);
        mediaFileInfoLabel.setText("");
        SRMediaLoadingTask searchTask = new SRMediaLoadingTask(keyword, fullMediaList, mainViewController);
        searchTask.setOnSucceeded((t) -> {
            searchTextField.setRight(clearButton);
            progressInd.setVisible(false);
            toolbar.setVisible(true);
            toolbar.setManaged(true);
            searchProgress.setVisible(false);
            searchLabel.setVisible(false);
        });
        searchTask.setOnFailed((t) -> {
            searchTextField.setRight(clearButton);
            progressInd.setVisible(false);
            if (t.getSource().getException() instanceof IOException) {
                searchLabel.setText("Nothing found!");
                searchProgress.setVisible(false);
                searchProgress.setManaged(false);
            } else {
                Logger.getLogger(SearchToolsController.class.getName()).log(Level.SEVERE, "Search failed", t.getSource().getException());
                searchProgress.setVisible(false);
                searchLabel.setVisible(false);
            }
        });
        task = searchTask;
        mainViewController.getTaskProgressView().getTasks().add(searchTask);
        executorParallel.submit(searchTask);
    }

    public void setDialogPane(DialogPane pane) {
        this.dialogPane = pane;
        this.diaglogHeight = dialogPane.getHeight();
    }

    public ObservableList<MediaFile> getFullMediaList() {
        return fullMediaList;
    }

    @FXML
    private void linkToCollectionAction(ActionEvent event) {
        if (factory.getSelectedMediaFile() != null) {
            collectionsController.highlightCollection(factory.getSelectedMediaFile().getPathStorage());
        }
    }

    public Label getMediaFileInfoLabel() {
        return mediaFileInfoLabel;
    }

    @FXML
    private void copyFilePathToClipboardAction(ActionEvent event) {
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        content.putString(factory.getSelectedMediaFile().getEditFilePath().getParent().toString());
        clipboard.setContent(content);
        String text = mediaFileInfoLabel.getText();
        mediaFileInfoLabel.setText("Copied path to clipboard successfully!");
        PauseTransition pause = new PauseTransition(Duration.millis(5000));
        pause.setOnFinished((t) -> {
            mediaFileInfoLabel.setVisible(true);
            mediaFileInfoLabel.setText(text);
        });
        pause.play();
    }

    public HBox getInfoBox() {
        return infoBox;
    }

    public GridView<MediaFile> getImageGrid() {
        return imageGrid;
    }

    public MediaGridCellSearchFactory getFactory() {
        return factory;
    }

    @FXML
    private void bookmarkMediaFileAction(ActionEvent event) {
        if (factory.getSelectedMediaFile() != null) {
            mainViewController.bookmarkMediaFile(factory.getSelectedMediaFile());
            mainViewController.saveBookmarksFile();
        }
    }

}
