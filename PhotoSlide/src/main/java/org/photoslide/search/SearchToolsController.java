/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;

import java.io.IOException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.controlsfx.control.GridView;
import org.controlsfx.control.textfield.CustomTextField;
import org.h2.fulltext.FullText;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.App;
import org.photoslide.MainViewController;
import org.photoslide.ThreadFactoryPS;
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
    private ExecutorService executor;
    private ExecutorService executorParallel;
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
        fullMediaList = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
        filteredMediaList = new FilteredList<>(fullMediaList, null);
        sortedMediaList = new SortedList<>(filteredMediaList);
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("SearchToolExecutor"));
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryPS("SearchToolExecutor"));
        imageGrid = new GridView<>(sortedMediaList);
        MediaGridCellSearchFactory factory = new MediaGridCellSearchFactory(executor, this, sortedMediaList);
        imageGrid.setCellFactory(factory);
        double defaultCellWidth = imageGrid.getCellWidth();
        double defaultCellHight = imageGrid.getCellHeight();
        imageGrid.setCellWidth(defaultCellWidth + 3 * 20);
        imageGrid.setCellHeight(defaultCellHight + 3 * 20);
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
        if (task != null) {
            task.cancel();
            task.shutdown();
        }
        executor.shutdown();
        executorParallel.shutdown();
    }

    private void searchTextFieldAction(ActionEvent event) {
        progressInd.setVisible(true);

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

    @FXML
    private void searchTextFieldAction(KeyEvent event) {
        if (event.getCode().isModifierKey() == false || event.getCode().isNavigationKey() == false) {
            if ((searchTextField.getText() + event.getText()).length() > 2) {
                searchTextField.setRight(progressInd);
                progressInd.setVisible(true);
                searchLabel.setText("Populating results...");
                searchProgress.setVisible(true);
                searchProgress.setManaged(true);
                searchLabel.setVisible(true);
                searchResultVBox.getChildren().clear();
                String keyword = searchTextField.getText() + event.getText();
                if (event.getCode() == KeyCode.ENTER) {
                    keyword = searchTextField.getText();
                }
                if (event.getCode() == KeyCode.SPACE) {
                    keyword = searchTextField.getText();
                }
                final String searchKeyword = keyword;
                Task<Void> srTask = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        Thread.sleep(500);
                        Platform.runLater(() -> {
                            searchTextField.setRight(clearButton);
                        });
                        performSearch(searchKeyword);
                        return null;
                    }
                };
                srTask.setOnFailed((t) -> {
                    progressInd.setVisible(false);
                    infoBox.setVisible(false);
                });
                srTask.setOnSucceeded((t) -> {
                    toolbar.setVisible(true);
                    toolbar.setManaged(true);
                    progressInd.setVisible(false);
                });
                executor.submit(srTask);
                dialogPane.getScene().getWindow().setHeight(400);
                mediaFileInfoLabel.setText("");
            }
        }
        if (event.getCode() == KeyCode.BACK_SPACE) {
            if (task != null) {
                if (task.isRunning()) {
                    task.cancel();
                }
            }
            dialogPane.getScene().getWindow().setHeight(diaglogHeight);
            infoBox.setVisible(false);
            toolbar.setVisible(false);
            toolbar.setManaged(false);
            searchResultVBox.getChildren().clear();
            if (searchTextField.getText().length() < 1) {
                progressInd.setVisible(false);
                toolbar.setVisible(false);
                toolbar.setManaged(false);
                infoBox.setVisible(false);
            }
        }
    }

    private void performSearch(String keyword) {
        try {
            ArrayList<String> queryList = new ArrayList<>();
            try (ResultSet searchRS = FullText.search(App.getSearchDBConnection(), keyword, 0, 0)) {
                while (searchRS.next()) {
                    queryList.add("SELECT * FROM " + searchRS.getString("QUERY"));
                }
            }
            fullMediaList = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
            filteredMediaList = new FilteredList<>(fullMediaList, null);
            sortedMediaList = new SortedList<>(filteredMediaList);
            sortedMediaList.setComparator(new Comparator<MediaFile>() {
                @Override
                public int compare(MediaFile o1, MediaFile o2) {
                    if (o2.getRecordTime() != null && o1.getRecordTime() != null) {
                        return o2.getRecordTime().compareTo(o1.getRecordTime());
                    } else {
                        return o2.getCreationTime().compareTo(o1.getCreationTime());
                    }
                }
            });
            imageGrid = new GridView<>(sortedMediaList);
            double defaultCellWidth = imageGrid.getCellWidth();
            double defaultCellHight = imageGrid.getCellHeight();
            factory = new MediaGridCellSearchFactory(executorParallel, this, sortedMediaList);
            imageGrid.setCellFactory(factory);
            Platform.runLater(() -> {
                searchResultVBox.getChildren().add(imageGrid);
            });
            if (task != null) {
                task.cancel();
                task.shutdown();
            }
            task = new SRMediaLoadingTask(queryList, this, fullMediaList, imageGrid, metadataController, mainViewController);
            task.setOnSucceeded((t) -> {                
                searchProgress.setVisible(false);
                searchLabel.setVisible(false);
            });
            task.setOnFailed((t) -> {
                if (t.getSource().getException() instanceof IOException) {
                    searchLabel.setText("Nothing found!");
                    searchProgress.setVisible(false);
                    searchProgress.setManaged(false);
                } else {
                    searchProgress.setVisible(false);
                    searchLabel.setVisible(false);
                }
            });
            executorParallel.submit(task);

        } catch (SQLException ex) {
            Logger.getLogger(SearchToolsController.class.getName()).log(Level.SEVERE, null, ex);
        }
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
