/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.bookmarksboard;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.controlsfx.control.GridView;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.search.MediaGridCellSearchFactory;

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

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        mediaFileList = new ArrayList<>();
        fullMediaList = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
        filteredMediaList = new FilteredList<>(fullMediaList, null);
        sortedMediaList = new SortedList<>(filteredMediaList);
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("SearchToolExecutor"));
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryPS("SearchToolExecutor"));
        imageGrid = new GridView<>(sortedMediaList);
        factory = new MediaGridCellBMBFactory(executor, this, sortedMediaList);
        imageGrid.setCellFactory(factory);
        double defaultCellWidth = imageGrid.getCellWidth();
        double defaultCellHight = imageGrid.getCellHeight();
        imageGrid.setCellWidth(defaultCellWidth + 3 * 20);
        imageGrid.setCellHeight(defaultCellHight + 3 * 20);
        boardcontentBox.getChildren().add(imageGrid);
    }

    public void setMediaFileList(List<String> mediaFileList) {
        this.mediaFileList = mediaFileList;
    }

    public void shutdown() {
        executor.shutdown();
        executorParallel.shutdown();
    }

    @FXML
    private void copyClipboardAction(ActionEvent event) {
    }

    @FXML
    private void removeMediaFileAction(ActionEvent event) {
    }

    @FXML
    private void clearBoardAction(ActionEvent event) {
    }

    public void readBookmarks() {
        task = new BMBMediaLoadingTask(mediaFileList, this, fullMediaList, imageGrid);
        executor.submit(task);

    }

    public GridView<MediaFile> getImageGrid() {
        return imageGrid;
    }

    public MediaGridCellBMBFactory getFactory() {
        return factory;
    }
    
    
    
    

}
