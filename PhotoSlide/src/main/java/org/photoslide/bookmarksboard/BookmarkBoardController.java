/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.bookmarksboard;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.controlsfx.control.GridView;
import org.photoslide.MainViewController;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.Utility;
import org.photoslide.datamodel.MediaFile;

/**
 *
 * @author selfemp
 */
public class BookmarkBoardController implements Initializable {

    private ObservableList<MediaFile> fullMediaList;
    private FilteredList<MediaFile> filteredMediaList;
    private SortedList<MediaFile> sortedMediaList;
    private GridView<MediaFile> imageGrid;
    private ExecutorService executor;
    private ExecutorService executorParallel;
    private List<String> mediaFileList;
    private BMBMediaLoadingTask task;

    @FXML
    private VBox boardcontentBox;
    @FXML
    private HBox messageBox;
    private MediaGridCellBMBFactory factory;
    @FXML
    private Label statusLabel;
    private Utility util;
    private MainViewController mainViewController;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private HBox menuBox;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        mediaFileList = new ArrayList<>();
        fullMediaList = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
        filteredMediaList = new FilteredList<>(fullMediaList, null);
        sortedMediaList = new SortedList<>(filteredMediaList);
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("SearchToolExecutor"));
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryPS("SearchToolExecutorParallel"));
        imageGrid = new GridView<>(sortedMediaList);
        factory = new MediaGridCellBMBFactory(executor, this, sortedMediaList);
        imageGrid.setCellFactory(factory);
        double defaultCellWidth = imageGrid.getCellWidth();
        double defaultCellHight = imageGrid.getCellHeight();
        imageGrid.setCellWidth(defaultCellWidth + 20);
        imageGrid.setCellHeight(defaultCellHight + 20);
        boardcontentBox.getChildren().add(imageGrid);
        util = new Utility();
        statusLabel.setText("");
    }

    public void injectMainController(MainViewController mainC) {
        this.mainViewController = mainC;
    }

    public void setMediaFileList(List<String> mediaFileList) {
        this.mediaFileList = mediaFileList;
    }

    public void shutdown() {
        if (task != null) {
            task.shutdown();
        }
        executor.shutdown();
        executorParallel.shutdown();
    }

    @FXML
    private void copyClipboardAction(ActionEvent event) {
        copyAction();
    }

    @FXML
    private void removeMediaFileAction(ActionEvent event) {
        MediaFile selectedMediaFile = factory.getSelectedMediaFile();
        mainViewController.removeBookmarkMediaFile(selectedMediaFile);
        fullMediaList.remove(selectedMediaFile);
    }

    @FXML
    private void clearBoardAction(ActionEvent event) {
        fullMediaList.forEach((m) -> {
            m.setBookmarked(false);
        });
        fullMediaList.clear();
        mainViewController.clearBookmars();
    }

    public void readBookmarks() {
        if (mediaFileList.isEmpty()) {
            menuBox.setDisable(true);
        } else {
            menuBox.setDisable(false);
        }
        progressIndicator.setVisible(true);
        statusLabel.setVisible(true);
        statusLabel.setText("Retrieving Mediafiles...");
        if (task != null) {
            task.shutdown();
        }
        task = new BMBMediaLoadingTask(mediaFileList, this, fullMediaList, imageGrid);
        task.setOnSucceeded((t) -> {
            progressIndicator.setVisible(false);
            statusLabel.setVisible(false);
        });
        task.setOnFailed((t) -> {
            progressIndicator.setVisible(false);
            statusLabel.setVisible(false);
        });
        executor.submit(task);

    }

    public GridView<MediaFile> getImageGrid() {
        return imageGrid;
    }

    public MediaGridCellBMBFactory getFactory() {
        return factory;
    }

    public void copyAction() {
        statusLabel.setText("Copy " + fullMediaList.size() + " files to clipboard...");
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        List<File> filesForClipboard = new ArrayList<>();

        fullMediaList.stream().forEach((mfile) -> {
            filesForClipboard.add(mfile.getPathStorage().toFile());
        });
        content.putFiles(filesForClipboard);
        clipboard.setContent(content);
        statusLabel.setText("Copy " + fullMediaList.size() + " files to clipboard...finished");
        util.hideNodeAfterTime(statusLabel, 3, true);
    }

    @FXML
    private void exportAction(ActionEvent event) {
        statusLabel.setText("Start export " + fullMediaList.size() + " files to filesystem...");
        String initDir = System.getProperty("user.dir").toUpperCase();
        mainViewController.exportData("Export bookmarks", initDir, fullMediaList);
        statusLabel.setText("Export " + fullMediaList.size() + " files...finished");
        util.hideNodeAfterTime(statusLabel, 3, true);
    }

    public ObservableList<MediaFile> getFullMediaList() {
        return fullMediaList;
    }

}
