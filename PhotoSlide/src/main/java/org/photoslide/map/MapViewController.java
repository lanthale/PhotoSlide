package org.photoslide.map;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.AnchorPane;
import org.photoslide.MainViewController;
import org.photoslide.ViewModule;

public class MapViewController implements Initializable, ViewModule {

    private MainViewController mainController;
    @FXML
    private AnchorPane rootPane;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
    }

    @Override
    public void injectMainController(MainViewController mainController) {
        this.mainController = mainController;
    }

    @Override
    public void shutdown() {
    }
}
