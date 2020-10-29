/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.pspreloader;

import javafx.animation.FadeTransition;
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
import org.photoslide.Utility;

/**
 * Simple Preloader Using the ProgressBar Control
 *
 * @author Clemens Lanthaler clemens.lanthaler@itarchitects.at
 */
public class PSPreloader extends Preloader {

    ProgressBar bar;
    Label text;
    Stage stage;
    private Label text2;
    private StackPane stack;
    private static double width = 640;
    private static double hight = 500;
    private Scene createPreloaderScene;

    private Scene createPreloaderScene() {
        bar = new ProgressBar();
        bar.setProgress(0.0);
        bar.setMaxHeight(10);
        bar.setMaxWidth(230);
        text = new Label("PhotoSlide");
        text.setId("titelLabel");
        text2 = new Label(new Utility().getAppVersion());
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
        ImageView img = new ImageView(new Image(getClass().getResource("/org/photoslide/img/Splashscreen.png").toString()));
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
        createPreloaderScene = createPreloaderScene();
        createPreloaderScene.getStylesheets().add(getClass().getResource("/org/photoslide/fxml/PreLoader.css").toExternalForm());
        createPreloaderScene.setFill(Color.TRANSPARENT);
        Image iconImage = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
        stage.getIcons().add(iconImage);
        stage.setScene(createPreloaderScene);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.show();
    }

    @Override
    public void handleStateChangeNotification(StateChangeNotification scn) {
        if (scn.getType() == StateChangeNotification.Type.BEFORE_START) {
            FadeTransition fadeOut = new FadeTransition(Duration.seconds(2), createPreloaderScene.getRoot());
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);
            fadeOut.setCycleCount(1);
            
            fadeOut.setOnFinished((t) -> {
                stage.hide();
            });            
            fadeOut.play();

        }
    }

    @Override
    public void handleApplicationNotification(PreloaderNotification arg0) {
        if (arg0 instanceof ProgressNotification) {
            ProgressNotification pn = (ProgressNotification) arg0;
            Platform.runLater(() -> {
                bar.setProgress(pn.getProgress());
            });
        }
    }

}
