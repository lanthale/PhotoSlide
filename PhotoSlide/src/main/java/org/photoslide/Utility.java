/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 *
 * @author selfemp
 */
public class Utility {

    public void hideNodeAfterTime(Node node, int timeInSec) {
        Platform.runLater(() -> {
            PauseTransition wait = new PauseTransition(Duration.seconds(timeInSec));
            wait.setOnFinished((e) -> {
                node.setVisible(false);
            });
            wait.play();
        });
    }

    public Point2D imageViewToImage(ImageView imageView, Point2D imageViewCoordinates) {
        double xProportion = imageViewCoordinates.getX() / imageView.getBoundsInLocal().getWidth();
        double yProportion = imageViewCoordinates.getY() / imageView.getBoundsInLocal().getHeight();

        Rectangle2D viewport = imageView.getViewport();
        return new Point2D(
                viewport.getMinX() + xProportion * viewport.getWidth(),
                viewport.getMinY() + yProportion * viewport.getHeight());
    }

    public Point2D imageToimageView(ImageView imageView, Point2D imageViewCoordinates) {
        double xProportion = imageViewCoordinates.getX() / imageView.getBoundsInLocal().getWidth();
        double yProportion = imageViewCoordinates.getY() / imageView.getBoundsInLocal().getHeight();

        Rectangle2D viewport = imageView.getViewport();
        return new Point2D(
                viewport.getMinX() + xProportion * viewport.getWidth(),
                viewport.getMinY() + yProportion * viewport.getHeight());
    }

    public void showError(String text, Throwable e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error dialog");
        alert.setHeaderText(text);
        //alert.setContentText(text);

        VBox dialogPaneContent = new VBox();

        Label label = new Label("Stack Trace:");

        String stackTrace = Arrays.toString(e.getStackTrace());
        TextArea textArea = new TextArea();
        textArea.setText(stackTrace);

        dialogPaneContent.getChildren().addAll(label, textArea);

        // Set content for Dialog Pane
        alert.getDialogPane().setExpandableContent(dialogPaneContent);
        alert.showAndWait();
        //sendEmail(text+"\n\nStacktrace:\n"+stackTrace);
    }

    /**
     * Returns the application data path, path is returned with ending /
     *
     * @return
     */
    public static String getAppData() {
        String path = "";
        String OS = System.getProperty("os.name").toUpperCase();
        if (OS.contains("WIN")) {
            path = System.getenv("APPDATA");
        } else if (OS.contains("MAC")) {
            path = System.getProperty("user.home") + "/Library/Application Support";
        } else if (OS.contains("NUX")) {
            path = System.getProperty("user.home");
        } else {
            path = System.getProperty("user.dir");
        }

        path = path + File.separator + "PhotoSlide";
        if (new File(path).exists() == false) {
            new File(path).mkdirs();
        }

        return path;
    }

    public String getAppVersion() {
        String version = "";
        InputStream resourceAsStream
                = this.getClass().getResourceAsStream(
                        "/META-INF/maven/org.photoslide/PhotoSlide/pom.properties"
                );
        Properties prop = new Properties();
        try {
            prop.load(resourceAsStream);
            version = (String) prop.get("version");
        } catch (IOException ex) {
            Logger.getLogger(Utility.class.getName()).log(Level.SEVERE, null, ex);
        }
        return version;
    }

    public static void centerChildWindowOnStage(Stage stage, Stage primaryStage) {
        if (primaryStage == null) {
            return;
        }
        double x = stage.getX();
        double y = stage.getY();

        // Firstly we need to force CSS and layout to happen, as the dialogPane
        // may not have been shown yet (so it has no dimensions)
        stage.getScene().getRoot().applyCss();
        stage.getScene().getRoot().layout();
        final Scene ownerScene = primaryStage.getScene();
        final double titleBarHeight = ownerScene.getY();

        // because Stage does not seem to centre itself over its owner, we
        // do it here.
        // then we can get the dimensions and position the dialog appropriately.
        final double dialogWidth = stage.getScene().getRoot().prefWidth(-1);
        final double dialogHeight = stage.getScene().getRoot().prefHeight(dialogWidth);
        final double ownerWidth = primaryStage.getScene().getRoot().prefWidth(-1);
        final double ownerHeight = primaryStage.getScene().getRoot().prefHeight(ownerWidth);
        if (dialogWidth < ownerWidth) {
            x = primaryStage.getX() + (ownerScene.getWidth() / 2.0) - (dialogWidth / 2.0);
        } else {
            x = primaryStage.getX();
            stage.setWidth(dialogWidth);
        }
        if (dialogHeight < ownerHeight) {
            y = primaryStage.getY() + titleBarHeight / 2.0 + (ownerScene.getHeight() / 2.0) - (dialogHeight / 2.0);
        } else {
            y = primaryStage.getY();
        }
        stage.setX(x);
        stage.setY(y);
    }

}
