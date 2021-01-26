/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.print;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Pagination;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import org.photoslide.ThreadFactoryPS;

/**
 *
 * @author selfemp
 */
public class PrintController implements Initializable {
    
    private ExecutorService executor;
    private ExecutorService executorParallel;    
    private DialogPane dialogPane;
    @FXML
    private ComboBox<?> printerCombo;
    @FXML
    private ComboBox<?> paperSizeCombo;
    @FXML
    private TextField copyQTY;
    @FXML
    private CheckBox greyscaleChoice;
    @FXML
    private RadioButton allPagesRadio;
    @FXML
    private RadioButton rangeRadio;
    @FXML
    private TextField pageFromText;
    @FXML
    private TextField pageToText;
    @FXML
    private ComboBox<?> copyiesPerPageCombo;
    @FXML
    private ToggleButton portraitButton;
    @FXML
    private ToggleButton landscapeButton;
    @FXML
    private Pagination paganinationControl;
    @FXML
    private ImageView previewPane;
    private ToggleGroup tGroup;

    @Override
    public void initialize(URL url, ResourceBundle rb) {        
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("SearchToolExecutor"));
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryPS("SearchToolExecutor"));        
        tGroup=new ToggleGroup();
        portraitButton.setToggleGroup(tGroup);
        landscapeButton.setToggleGroup(tGroup);
    }

    public void shutdown() {
        executor.shutdown();
        executorParallel.shutdown();
    }

    public void setDialogPane(DialogPane pane) {
        this.dialogPane = pane;        
    }

    @FXML
    private void allPagesRadioAction(ActionEvent event) {
    }

    @FXML
    private void rangeRadioAction(ActionEvent event) {
    }

    @FXML
    private void pageFormTextAction(ActionEvent event) {
    }

    @FXML
    private void pageToTextAction(ActionEvent event) {
    }

    

}
