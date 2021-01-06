/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;

import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.GridView;
import org.controlsfx.control.textfield.CustomTextField;
import org.h2.fulltext.FullText;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.App;
import org.photoslide.ThreadFactoryPS;
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

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        progressInd = new ProgressIndicator();
        progressInd.setVisible(false);
        progressInd.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        searchTextField.setRight(progressInd);
        clearButton=new Button();
        clearButton.setId("toolbutton");
        FontIcon clearIcon=new FontIcon("ti-close");
        clearButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        clearButton.setGraphic(clearIcon);
        clearButton.setOnAction((t) -> {
            searchTextField.clear();
            dialogPane.getScene().getWindow().setHeight(diaglogHeight);
        });
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

    public void shutdown() {
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
        //if (event.getCode() != KeyCode.BACK_SPACE) {
        if (searchTextField.getText().length() > 2) {
            searchTextField.setRight(progressInd);
            progressInd.setVisible(true);
            searchResultVBox.getChildren().clear();
            String keyword = searchTextField.getText() + event.getText();
            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    Thread.sleep(500);
                    Platform.runLater(() -> {
                        searchTextField.setRight(clearButton);
                    });                    
                    dialogPane.getScene().getWindow().setHeight(300);
                    performSearch(keyword);
                    return null;
                }
            };
            task.setOnFailed((t) -> {                
                progressInd.setVisible(false);
            });
            task.setOnSucceeded((t) -> {
                progressInd.setVisible(false);
            });
            executor.submit(task);
        }
        //}
        if (event.getCode() == KeyCode.BACK_SPACE) {
            dialogPane.getScene().getWindow().setHeight(diaglogHeight);
            if (searchTextField.getText().length() < 1) {
                progressInd.setVisible(false);
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
            if (queryList.isEmpty() == false) {
                fullMediaList = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
                filteredMediaList = new FilteredList<>(fullMediaList, null);
                sortedMediaList = new SortedList<>(filteredMediaList);
                imageGrid = new GridView<>(sortedMediaList);
                double defaultCellWidth = imageGrid.getCellWidth();
                double defaultCellHight = imageGrid.getCellHeight();
                factory = new MediaGridCellSearchFactory(executorParallel, this, sortedMediaList);
                imageGrid.setCellFactory(factory);
                Platform.runLater(() -> {
                    searchResultVBox.getChildren().add(imageGrid);
                });
                for (String query : queryList) {                    
                    try (Statement stm = App.getSearchDBConnection().createStatement(); ResultSet rs = stm.executeQuery(query)) {
                        rs.next();
                        String pathStorage = rs.getString("pathStorage");
                        //loading mediafiles
                        SRMediaLoadingTask task = new SRMediaLoadingTask(pathStorage, this, fullMediaList, imageGrid);
                        task.setOnFailed((t) -> {
                            t.getSource().getException().printStackTrace();
                            Logger.getLogger(SearchToolsController.class.getName()).log(Level.SEVERE, null, t.getSource().getException());
                        });
                        executorParallel.submit(task);
                    }
                }
            }
        } catch (SQLException ex) {
            Logger.getLogger(SearchToolsController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void setDialogPane(DialogPane pane) {
        this.dialogPane = pane;
        this.diaglogHeight = dialogPane.getHeight();
    }

}
