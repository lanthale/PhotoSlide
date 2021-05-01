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
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.javafx.StackedFontIcon;

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

    public void showError(Node centerNode, String text, Throwable e) {
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

        StackedFontIcon st = new StackedFontIcon();
        FontIcon f1 = new FontIcon("ti-layout-width-full");
        f1.setIconSize(50);
        FontIcon f2 = new FontIcon("ti-close");
        f2.setIconSize(30);
        st.getChildren().add(f1);
        st.getChildren().add(f2);
        alert.setGraphic(st);

        // Set content for Dialog Pane
        alert.getDialogPane().setExpandableContent(dialogPaneContent);
        alert.getDialogPane().setExpanded(false);
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) centerNode.getScene().getWindow());
        Image dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(dialogIcon);
        alert.show();
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

    public static void centerTopChildWindowOnStage(Stage stage, Stage primaryStage) {
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
            y = primaryStage.getY() + titleBarHeight / 3.0 + (ownerScene.getHeight() / 3.0) - (dialogHeight / 3.0);
        } else {
            y = primaryStage.getY();
        }
        stage.setX(x);
        stage.setY(y);
    }

    public static Node pick(Node node, double sceneX, double sceneY) {
        Point2D p = node.sceneToLocal(sceneX, sceneY, true /* rootScene */);

        // check if the given node has the point inside it, or else we drop out
        if (!node.contains(p)) {
            return null;
        }

        // at this point we know that _at least_ the given node is a valid
        // answer to the given point, so we will return that if we don't find
        // a better child option
        if (node instanceof Parent) {
            // we iterate through all children in reverse order, and stop when we find a match.
            // We do this as we know the elements at the end of the list have a higher
            // z-order, and are therefore the better match, compared to children that
            // might also intersect (but that would be underneath the element).
            Node bestMatchingChild = null;
            List<Node> children = ((Parent) node).getChildrenUnmodifiable();
            for (int i = children.size() - 1; i >= 0; i--) {
                Node child = children.get(i);
                p = child.sceneToLocal(sceneX, sceneY, true /* rootScene */);
                if (child.isVisible() && !child.isMouseTransparent() && child.contains(p)) {
                    bestMatchingChild = child;
                    break;
                }
            }

            if (bestMatchingChild != null) {
                return pick(bestMatchingChild, sceneX, sceneY);
            }
        }

        return node;
    }

    public static Alert setDefaultButton(Alert alert, ButtonType defBtn) {
        DialogPane pane = alert.getDialogPane();
        for (ButtonType t : alert.getButtonTypes()) {
            ((Button) pane.lookupButton(t)).setDefaultButton(t == defBtn);
        }
        return alert;
    }

}
