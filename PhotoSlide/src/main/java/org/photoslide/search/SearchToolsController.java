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
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
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
    @FXML
    private CustomTextField searchTextField;
    private ProgressIndicator progressInd;
    @FXML
    private TableView<?> tableView;
    @FXML
    private Button closeAction;
    @FXML
    private FontIcon sIcon;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        progressInd = new ProgressIndicator();
        progressInd.setVisible(false);
        progressInd.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        searchTextField.setRight(progressInd);
        fullMediaList = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());
        filteredMediaList = new FilteredList<>(fullMediaList, null);
        sortedMediaList = new SortedList<>(filteredMediaList);
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("SearchToolExecutor"));
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
                progressInd.setVisible(true);                
                String keyword = searchTextField.getText()+event.getText();
                Task<Void> task=new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        Thread.sleep(500);
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
            if (searchTextField.getText().length() < 1) {
                progressInd.setVisible(false);
            }
        }
    }

    private void performSearch(String keyword) {        
        System.out.println("keyword "+keyword);
        try {
            ArrayList<String> queryList=new ArrayList<>();
            ResultSet searchRS = FullText.search(App.getSearchDBConnection(), keyword, 0, 0);
            while (searchRS.next()) {
                queryList.add("SELECT * FROM "+searchRS.getString("QUERY"));
            }
            searchRS.close();
            for (String query : queryList) {
                System.out.println("query "+query);
                try (Statement stm = App.getSearchDBConnection().createStatement(); ResultSet rs = stm.executeQuery(query)) {
                    rs.next();
                    String pathStorage = rs.getString("pathStorage");
                    System.out.println("Search found: "+pathStorage);
                }
            }
        } catch (SQLException ex) {
            Logger.getLogger(SearchToolsController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
