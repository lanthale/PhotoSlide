/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.itarchitects.lightzonefx;

import java.io.IOException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

/**
 *
 * @author selfemp
 */
public class ExportDialog extends Alert {

    private ExportDialogController controller;

    public ExportDialog(AlertType at) {
        super(at);
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/at/itarchitects/lightzonefx/fxml/ExportDialog.fxml"));
            Parent root = (Parent) fxmlLoader.load();
            controller = fxmlLoader.<ExportDialogController>getController();            
            //controller.setModel(new LoginModel(data));
            getDialogPane().getStylesheets().add(
                getClass().getResource("/at/itarchitects/lightzonefx/fxml/Dialogs.css").toExternalForm());
            getDialogPane().setContent(root);               
            
            /*setResultConverter(buttonType -> {
                //SomeDataType someData = ... ;
                return null;
            });*/

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    public ExportDialog(AlertType at, String string, ButtonType... bts) {
        super(at, string, bts);
    }

    public ExportDialogController getController() {
        return controller;
    }
    
    

}
