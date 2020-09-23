/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.itarchitects.lightzonefx.lighttableat.itarchitects.lightzonefx.lighttable;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

/**
 * FXML Controller class
 *
 * @author selfemp
 */
public class ExportDialogControllerExportDialogController implements Initializable {

    @FXML
    private ComboBox<?> fileSequenceCombo;
    @FXML
    private TextField outputDirText;
    @FXML
    private TextField filenamePrefixText;
    @FXML
    private ComboBox<?> fileFormatCombo;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // TODO
    }    

    @FXML
    private void outputSelectionButtonAction(ActionEvent event) {
    }
    
}
