/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;

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
public class SearchToolsDialog extends Alert {

    private SearchToolsController controller;
    
    public SearchToolsDialog(AlertType at) {
        super(at);
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/org/photoslide/fxml/SearchToolsDialog.fxml"));
            Parent root = (Parent) fxmlLoader.load();
            controller = fxmlLoader.<SearchToolsController>getController();            
            getDialogPane().getStylesheets().add(
                    getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
            getDialogPane().setContent(root);             
            Image dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
            Stage stage = (Stage) this.getDialogPane().getScene().getWindow();
            stage.getIcons().add(dialogIcon);            
        } catch (IOException e) {
            Logger.getLogger(SearchToolsDialog.class.getName()).log(Level.SEVERE, null, e);
        }
    }
    
    
    public SearchToolsDialog(AlertType at, String string, ButtonType... bts) {
        super(at, string, bts);
    }

    public SearchToolsController getController() {
        return controller;
    }    
    
}
