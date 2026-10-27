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
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.imageops.ImageFilter;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import org.photoslide.datamodel.Display16;
import org.photoslide.datamodel.Image16;
import org.photoslide.imageops.EditPipeline;
import org.photoslide.imageops.ExposureFilter;
import org.photoslide.imageops.GainFilter;
import org.photoslide.imageops.ImagePreview16;

/**
 *
 * @author selfemp
 */
public class EditorToolsController implements Initializable {

    private static final double OPACITY = 0.8;
    /** Longer side of the image the histogram is computed from. */
    private static final int HISTOGRAM_SIZE = 512;
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
    @FXML
    private Slider exposureSlider;
    @FXML
    private Label exposureValueLabel;
    @FXML
    private Slider gainSlider;
    @FXML
    private Slider biasSlider;
    private boolean updatingSliders;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNamePrefix("editorToolsController").build());
        drawingCanvas.widthProperty().bind(histoAnchorPane.widthProperty());
        drawingCanvas.heightProperty().bind(histoAnchorPane.heightProperty());
        gc = drawingCanvas.getGraphicsContext2D();
        gc.setGlobalAlpha(OPACITY);
        gc.setLineWidth(1);
        drawingCanvas.widthProperty().addListener((o) -> drawHistogram());
        drawingCanvas.heightProperty().addListener((o) -> drawHistogram());
        exposureSlider.valueProperty().addListener((o) -> {
            exposureValueLabel.setText(String.format("%.2f", exposureSlider.getValue()));
            if (updatingSliders || selectedMediaFile == null) {
                return;
            }
            EditPipeline.setFilterValues(selectedMediaFile, ExposureFilter.NAME, ExposureFilter::new,
                    new float[]{(float) exposureSlider.getValue()});
            EditPipeline.filtersChanged(selectedMediaFile);
        });
        InvalidationListener gainListener = (o) -> {
            if (updatingSliders || selectedMediaFile == null) {
                return;
            }
            EditPipeline.setFilterValues(selectedMediaFile, GainFilter.NAME, GainFilter::new,
                    new float[]{(float) gainSlider.getValue(), (float) biasSlider.getValue()});
            EditPipeline.filtersChanged(selectedMediaFile);
        };
        gainSlider.valueProperty().addListener(gainListener);
        biasSlider.valueProperty().addListener(gainListener);
    }

    public void cancleTask() {
        resetUI();
    }

    public void setMediaFileForEdit(MediaFile f) {
        if (f == null) {
            return;
        }
        resetUI();
        selectedMediaFile = f;
        showFilterValues(f);
    }

    /**
     * Connects the tools to the editor image: the histogram follows every
     * render of the preview.
     */
    public void setPreview(ImagePreview16 preview) {
        preview.setOnRendered(this::updateHistogram);
    }

    /** Render thread of the preview. */
    private void updateHistogram(Image16 result) {
        int step = Math.max(1, Math.max(result.width(), result.height()) / HISTOGRAM_SIZE);
        Image16 small = result.scaledTo(Math.max(1, result.width() / step), Math.max(1, result.height() / step));
        Histogram h = new Histogram(Display16.toArgb(small, false));
        Platform.runLater(() -> {
            histogram = h;
            drawHistogram();
            progressHistogramm.setVisible(false);
            if (drawingCanvas.getOpacity() == 0.0) {
                FadeTransition ft1 = new FadeTransition(Duration.millis(500), drawingCanvas);
                ft1.setAutoReverse(false);
                ft1.setFromValue(0.0);
                ft1.setToValue(1.0);
                ft1.play();
            }
        });
    }

    private void showFilterValues(MediaFile file) {
        ImageFilter exposure = file.getFilterForName(ExposureFilter.NAME);
        ImageFilter gain = file.getFilterForName(GainFilter.NAME);
        updatingSliders = true;
        try {
            exposureSlider.setValue(exposure != null ? exposure.getValues()[0] : 0);
            gainSlider.setValue(gain != null ? gain.getValues()[0] : 0.5);
            biasSlider.setValue(gain != null ? gain.getValues()[1] : 0.5);
        } finally {
            updatingSliders = false;
        }
    }

    @FXML
    private void resetExposureAction(ActionEvent event) {
        resetFilter(ExposureFilter.NAME);
    }

    @FXML
    private void resetGainFilterAction(ActionEvent event) {
        resetFilter(GainFilter.NAME);
    }

    private void resetFilter(String name) {
        if (selectedMediaFile == null) {
            return;
        }
        ImageFilter filter = selectedMediaFile.getFilterForName(name);
        if (filter != null) {
            selectedMediaFile.removeImageFilter(filter);
        }
        showFilterValues(selectedMediaFile);
        EditPipeline.filtersChanged(selectedMediaFile);
    }

    private void drawHistogram() {
        gc.setGlobalAlpha(1);
        gc.clearRect(0, 0, drawingCanvas.getWidth(), drawingCanvas.getHeight());
        gc.setGlobalAlpha(OPACITY);
        if (histogram == null) {
            return;
        }
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
        histogram = null;
        progressHistogramm.setVisible(true);
        drawingCanvas.setOpacity(0);
        gc.setGlobalAlpha(1);
        gc.clearRect(0, 0, drawingCanvas.getWidth(),
                drawingCanvas.getHeight());
        gc.setGlobalAlpha(OPACITY);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

}
