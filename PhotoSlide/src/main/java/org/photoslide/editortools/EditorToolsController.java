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
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.datamodel.MediaFile;

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

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newCachedThreadPool(new ThreadFactoryPS("editorToolsController"));
        drawingCanvas.widthProperty().bind(histoAnchorPane.widthProperty());
        drawingCanvas.heightProperty().bind(histoAnchorPane.heightProperty());
        gc = drawingCanvas.getGraphicsContext2D();
        gc.setGlobalAlpha(OPACITY);
        gc.setLineWidth(1);
    }

    public void setMediaFileForEdit(MediaFile f) {
        if (f == null) {
            return;
        }
        clearCanvas();
        selectedMediaFile = f;
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                String url = selectedMediaFile.getImage().getUrl();
                Image img = new Image(url, false);
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
        });
        executor.submit(task);
    }

    private void drawHistogram() {
        clearCanvas();
        double height = drawingCanvas.getHeight();
        double width=drawingCanvas.getWidth();
        drawCurve(histogram.getRed(), Color.RED, height,width);
        drawCurve(histogram.getGreen(), Color.GREEN, height,width);
        drawCurve(histogram.getBlue(), Color.BLUE, height,width);
    }

    /**
     * Draw lines from the bottom of the histogram to the height of each given
     * point with the given color.
     */
    public void drawCurve(List<Integer> points, Color color, double hight, double width) {
        gc.setStroke(color);
        double w=(double)width/points.size();
        for (int i = 0; i < points.size(); i++) {
            //gc.strokeLine(i + 0.5, 100.5, i + 0.5, 100.5 - points.get(i));
            gc.strokeLine(w*(i), hight, w*(i), hight - points.get(i));
        }
    }

    private void clearCanvas() {
        gc.setGlobalAlpha(1);
        gc.clearRect(0, 0, drawingCanvas.getWidth(),
                drawingCanvas.getHeight());
        gc.setGlobalAlpha(OPACITY);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

}
