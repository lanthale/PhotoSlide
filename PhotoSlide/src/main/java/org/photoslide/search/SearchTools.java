/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import org.controlsfx.control.GridView;
import org.controlsfx.control.PopOver;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.datamodel.MediaFile;

/**
 *
 * @author selfemp
 */
public class SearchTools {

    private final ObservableList<MediaFile> fullMediaList;
    private final FilteredList<MediaFile> filteredMediaList;
    private final SortedList<MediaFile> sortedMediaList;
    private final GridView<MediaFile> imageGrid;
    private final ExecutorService executorParallel;
    private final Window rootWindow;

    public SearchTools(Window w) {
        rootWindow = w;
        fullMediaList = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
        filteredMediaList = new FilteredList<>(fullMediaList, null);
        sortedMediaList = new SortedList<>(filteredMediaList);
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryPS("SearchToolExecutor"));
        imageGrid = new GridView<>(sortedMediaList);
        MediaGridCellSearchFactory factory = new MediaGridCellSearchFactory(executorParallel, this, sortedMediaList);
        imageGrid.setCellFactory(factory);
        double defaultCellWidth = imageGrid.getCellWidth();
        double defaultCellHight = imageGrid.getCellHeight();
        imageGrid.setCellWidth(defaultCellWidth + 3 * 20);
        imageGrid.setCellHeight(defaultCellHight + 3 * 20);
    }

    public void setupSearchDialog() {
        PopOver popOver = new PopOver();
        popOver.setDetachable(false);
        popOver.setAnimated(true);
        VBox vbox = new VBox();
        vbox.setSpacing(5);
        HBox hb = new HBox();
        hb.setSpacing(5);
        hb.setAlignment(Pos.CENTER);
        hb.setPadding(new Insets(5, 0, 0, 0));

        vbox.setPrefSize(600, 400);
        popOver.setContentNode(vbox);
        popOver.setArrowLocation(PopOver.ArrowLocation.BOTTOM_CENTER);
        popOver.setFadeInDuration(new Duration(1000));
        popOver.setCloseButtonEnabled(true);
        popOver.setAutoFix(true);
        popOver.setHeaderAlwaysVisible(true);
        popOver.setTitle("Stack view and ordering via drag and drop");
        popOver.show(rootWindow);
        popOver.requestFocus();
    }
    
    public void shutdown(){
        executorParallel.shutdown();
    }
}
