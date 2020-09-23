/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.itarchitects.lightzonefx;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

/**
 *
 * @author selfemp
 */
public class ExportDialogController implements Initializable {

    @FXML
    private ComboBox<String> fileSequenceCombo;
    @FXML
    private TextField outputDirText;
    @FXML
    private TextField filenamePrefixText;
    @FXML
    private ComboBox<String> fileFormatCombo;
    @FXML
    private Label exampleLabel;

    private Stage root;
    @FXML
    private CheckBox exportSelectedBox;
    @FXML
    private CheckBox exportDeletedFileBox;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        fileFormatCombo.getItems().add("JPG");
        fileFormatCombo.getItems().add("PNG");
        fileFormatCombo.getItems().add("TIFF");
        fileFormatCombo.getSelectionModel().select("JPG");
        fileSequenceCombo.getItems().add("Title/Caption based");
        fileSequenceCombo.getItems().add("Orginial filename");
        fileSequenceCombo.getItems().add("Custom filname");
        fileSequenceCombo.getSelectionModel().select("Title/Caption based");
        fileSequenceCombo.valueProperty().addListener((o) -> {
            String selectedItem = fileSequenceCombo.getSelectionModel().getSelectedItem();
            if (selectedItem.equalsIgnoreCase("Custom filname")) {
                filenamePrefixText.setDisable(false);
                filenamePrefixText.clear();
            }
            if (selectedItem.equalsIgnoreCase("Orginial filename")) {
                filenamePrefixText.setDisable(true);
            }
        });
        fileSequenceCombo.getSelectionModel().select("Title/Caption based");
        filenamePrefixText.setText("Probe_01");
        filenamePrefixText.setDisable(true);
    }

    @FXML
    private void outputSelectionButtonAction(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setInitialDirectory(new File(outputDirText.getText()));
        File selectedDirectory = directoryChooser.showDialog((Stage) fileSequenceCombo.getScene().getWindow());
        outputDirText.setText(selectedDirectory.getAbsolutePath());
    }

    public String getFileFormat() {
        return fileFormatCombo.getSelectionModel().getSelectedItem();
    }

    public String getFilename() {
        return filenamePrefixText.getText()+"_";
    }

    public String getOutputDir() {
        return outputDirText.getText();
    }

    public void setInitOutDir(String dir) {
        outputDirText.setText(dir);
    }

    public void setTitel(String titel) {
        if (titel != null) {
            filenamePrefixText.setText(titel);
            exampleLabel.setText(filenamePrefixText.getText() + "_1" + "." + fileFormatCombo.getSelectionModel().getSelectedItem());
        } else {
            fileSequenceCombo.getSelectionModel().select("Custom filname");
        }
    }

    public CheckBox getExportSelectedBox() {
        return exportSelectedBox;
    }

    public CheckBox getExportDeletedFileBox() {
        return exportDeletedFileBox;
    }
    
    
    

}
