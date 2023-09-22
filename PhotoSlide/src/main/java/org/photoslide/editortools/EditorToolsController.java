/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.editortools;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.FadeTransition;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.imageops.ImageFilter;

/**
 *
 * @author selfemp
 */
public class EditorToolsController implements Initializable {

    private static final double OPACITY = 0.8;
    private ExecutorService executor;
    private MediaFile selectedMediaFile;
    private Histogram histogram;
    private GraphicsContext gc;

    @FXML
    private VBox titlePaneBox;
    @FXML
    private Canvas drawingCanvas;
    @FXML
    private AnchorPane histoAnchorPane;
    @FXML
    private ProgressIndicator progressHistogramm;
    private Task<Boolean> task;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNamePrefix("editorToolsController").build());
        drawingCanvas.widthProperty().bind(histoAnchorPane.widthProperty());
        drawingCanvas.heightProperty().bind(histoAnchorPane.heightProperty());
        gc = drawingCanvas.getGraphicsContext2D();
        gc.setGlobalAlpha(OPACITY);
        gc.setLineWidth(1);
    }

    public void cancleTask() {
        if (task != null) {
            task.cancel();
            resetUI();
        }
    }

    public void setMediaFileForEdit(MediaFile f) {
        if (f == null) {
            return;
        }
        resetUI();
        selectedMediaFile = f;
        task = new Task<>() {
            private Image imageWithFilters;
            private ObservableList<ImageFilter> filterList;

            @Override
            protected Boolean call() throws Exception {
                String url = selectedMediaFile.getImageUrl().toString();
                Image img = new Image(url, false);
                imageWithFilters = img;
                filterList = selectedMediaFile.getFilterListWithoutImageData();
                for (ImageFilter imageFilter : filterList) {
                    imageWithFilters = imageFilter.load(imageWithFilters);
                    imageFilter.filter(imageFilter.getValues());
                }
                img = imageWithFilters;
                histogram = new Histogram(img);
                return true;
            }

        };
        task.setOnSucceeded((t) -> {
            drawHistogram();
            drawingCanvas.widthProperty().addListener((o) -> {
                drawHistogram();
            });
            drawingCanvas.heightProperty().addListener((o) -> {
                drawHistogram();
            });
            progressHistogramm.setVisible(false);
            FadeTransition ft1 = new FadeTransition(Duration.millis(500), drawingCanvas);
            ft1.setAutoReverse(false);
            ft1.setFromValue(0.0);
            ft1.setToValue(1.0);
            ft1.play();
        });
        executor.submit(task);
    }

    private void drawHistogram() {
        resetUI();
        double height = drawingCanvas.getHeight();
        double width = drawingCanvas.getWidth();
        drawCurve(histogram.getRed(), Color.RED, height, width);
        drawCurve(histogram.getGreen(), Color.GREEN, height, width);
        drawCurve(histogram.getBlue(), Color.BLUE, height, width);
    }

    /**
     * Draw lines from the bottom of the histogram to the height of each given
     * point with the given color.
     */
    public void drawCurve(List<Integer> points, Color color, double hight, double width) {
        gc.setStroke(color);
        double w = (double) width / points.size();
        for (int i = 0; i < points.size(); i++) {
            //gc.strokeLine(i + 0.5, 100.5, i + 0.5, 100.5 - points.get(i));
            gc.strokeLine(w * (i), hight, w * (i), hight - points.get(i));
        }
    }

    public void resetUI() {
        progressHistogramm.setVisible(true);
        drawingCanvas.setOpacity(0);
        gc.setGlobalAlpha(1);
        gc.clearRect(0, 0, drawingCanvas.getWidth(),
                drawingCanvas.getHeight());
        gc.setGlobalAlpha(OPACITY);
        selectedMediaFile = null;
    }

    public void shutdown() {
        executor.shutdownNow();
    }

}
