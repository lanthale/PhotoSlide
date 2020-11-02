/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide;

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
public class ExportDialog extends Alert {

    private ExportDialogController controller;

    public ExportDialog(AlertType at) {
        super(at);
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/org/photoslide/fxml/ExportDialog.fxml"));
            Parent root = (Parent) fxmlLoader.load();
            controller = fxmlLoader.<ExportDialogController>getController();
            //controller.setModel(new LoginModel(data));
            getDialogPane().getStylesheets().add(
                    getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
            getDialogPane().setContent(root);            
            Image dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
            Stage stage = (Stage) this.getDialogPane().getScene().getWindow();
            stage.getIcons().add(dialogIcon);
            /*setResultConverter(buttonType -> {
                //SomeDataType someData = ... ;
                return null;
            });*/

        } catch (IOException e) {
            Logger.getLogger(MainViewController.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    public ExportDialog(AlertType at, String string, ButtonType... bts) {
        super(at, string, bts);
    }

    public ExportDialogController getController() {
        return controller;
    }

}
