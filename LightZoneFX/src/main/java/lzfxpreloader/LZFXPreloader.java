/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lzfxpreloader;

import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.application.Preloader;
import javafx.application.Preloader.ProgressNotification;
import javafx.application.Preloader.StateChangeNotification;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Simple Preloader Using the ProgressBar Control
 *
 * @author Clemens Lanthaler clemens.lanthaler@itarchitects.at
 */
public class LZFXPreloader extends Preloader {

    ProgressBar bar;
    Label text;
    Stage stage;
    private Label text2;
    private StackPane stack;
    private static double width = 640;
    private static double hight = 500;

    private Scene createPreloaderScene() {
        bar = new ProgressBar();
        bar.setProgress(0.0);
        bar.setMaxHeight(10);
        bar.setMaxWidth(230);
        text = new Label("LightZoneFX");
        text.setId("titelLabel");
        text2 = new Label("1.0");
        text2.setId("versionLabel");

        VBox v1 = new VBox();
        v1.setAlignment(Pos.TOP_RIGHT);
        v1.setSpacing(10);
        v1.setPadding(new Insets(50, 20, 10, 10));
        v1.getChildren().add(text);
        v1.getChildren().add(text2);
        VBox v2 = new VBox();
        v2.setAlignment(Pos.BOTTOM_RIGHT);
        stack = new StackPane();
        BorderPane p = new BorderPane();
        p.setPrefWidth(width);
        p.setPadding(new Insets(15));
        p.setBackground(Background.EMPTY);
        ImageView img = new ImageView(new Image(getClass().getResource("/at/itarchitects/lightzonefx/img/Splash.png").toString()));
        //img.setFitHeight(hight);
        img.setFitWidth(width);
        img.setPreserveRatio(true);
        p.setBottom(bar);
        stack.getChildren().add(img);
        stack.getChildren().add(p);
        stack.getChildren().add(v1);
        return new Scene(stack, width, hight);
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        stage.setAlwaysOnTop(true);
        Scene createPreloaderScene = createPreloaderScene();
        createPreloaderScene.getStylesheets().add(getClass().getResource("/at/itarchitects/lightzonefx/fxml/PreLoader.css").toExternalForm());
        createPreloaderScene.setFill(Color.TRANSPARENT);
        stage.setScene(createPreloaderScene);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.show();
    }

    @Override
    public void handleStateChangeNotification(StateChangeNotification scn) {
        if (scn.getType() == StateChangeNotification.Type.BEFORE_START) {            
            stage.hide();
        }
    }

    @Override
    public void handleApplicationNotification(PreloaderNotification arg0) {
        if (arg0 instanceof ProgressNotification) {
            ProgressNotification pn = (ProgressNotification) arg0;
            bar.setProgress(pn.getProgress());            
        }
    }

}
