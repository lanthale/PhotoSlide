/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.print;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 *
 * @author selfemp
 */
public class PrintDialog extends Alert {

    private PrintController controller;

    public PrintDialog(AlertType at) {
        super(at);
        initDialog();
    }
    
    public PrintDialog(AlertType at, String string, ButtonType... bts) {
        super(at, string, bts);
        initDialog();
    }

    private void initDialog() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/org/photoslide/fxml/PrintDialog.fxml"));
            Parent root = (Parent) fxmlLoader.load();
            controller = fxmlLoader.<PrintController>getController();            
            getDialogPane().setContent(root);
            Image dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
            Stage stage = (Stage) this.getDialogPane().getScene().getWindow();
            stage.getIcons().add(dialogIcon);
        } catch (IOException e) {
            Logger.getLogger(PrintDialog.class.getName()).log(Level.SEVERE, null, e);
        }
    }    

    public PrintController getController() {
        return controller;
    }

}
