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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.datamodel.MediaFile;
import javafx.application.Platform;
import org.photoslide.datamodel.Image16;
import java.util.function.Consumer;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.mask.ColorSelection;
import org.photoslide.imageops.mask.Contour;
import org.photoslide.imageops.mask.Region;
import org.photoslide.editpipeline.EditPipeline;
import org.photoslide.editpipeline.ImagePreview16;

/**
 *
 * @author selfemp
 */
public class EditorToolsController implements Initializable {

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
    private HBox toolHeader;
    @FXML
    private ScrollPane toolScroll;
    @FXML
    private TabPane diagramTabs;
    @FXML
    private Tab maskTab;
    @FXML
    private Tab samplerTab;
    private ToolStackView toolStack;
    private ToolStackView.DropperHost dropperHost;
    private ColorMaskView colorMask;
    private SamplerView sampler;
    private ImagePreview16 preview;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNamePrefix("editorToolsController").build());
        drawingCanvas.widthProperty().bind(histoAnchorPane.widthProperty());
        drawingCanvas.heightProperty().bind(histoAnchorPane.heightProperty());
        gc = drawingCanvas.getGraphicsContext2D();
        drawingCanvas.widthProperty().addListener((o) -> drawHistogram());
        drawingCanvas.heightProperty().addListener((o) -> drawHistogram());
        colorMask = new ColorMaskView(executor);
        sampler = new SamplerView(executor);
        maskTab.setContent(colorMask.node());
        samplerTab.setContent(sampler.node());
        diagramTabs.getSelectionModel().selectedItemProperty().addListener(
                (o, oldValue, newValue) -> colorMask.setShown(newValue == maskTab));
        // forwards to the editor view once it is connected (setDropperHost)
        toolStack = new ToolStackView(new ToolStackView.DropperHost() {
            @Override
            public void requestPoint(Consumer<Contour.Point> onPoint) {
                if (dropperHost != null) {
                    dropperHost.requestPoint(onPoint);
                }
            }

            @Override
            public void requestOutline(Consumer<List<Contour.Point>> onOutline) {
                if (dropperHost != null) {
                    dropperHost.requestOutline(onOutline);
                }
            }

            @Override
            public void requestCircle(double defaultRadius, java.util.function.BiConsumer<Contour.Point, Double> onCircle) {
                if (dropperHost != null) {
                    dropperHost.requestCircle(defaultRadius, onCircle);
                }
            }

            @Override
            public void cancelPick() {
                if (dropperHost != null) {
                    dropperHost.cancelPick();
                }
            }

            @Override
            public double[] sampleBelow(AbstractImageFilter tool, Contour.Point p) {
                return dropperHost != null ? dropperHost.sampleBelow(tool, p) : null;
            }

            @Override
            public double[] imageSize() {
                return dropperHost != null ? dropperHost.imageSize() : null;
            }

            @Override
            public void showRegion(AbstractImageFilter tool) {
                if (tool != null) {
                    colorMask.setTool(tool);
                }
                if (dropperHost != null) {
                    dropperHost.showRegion(tool);
                }
            }

            @Override
            public Region selectedRegion() {
                return dropperHost != null ? dropperHost.selectedRegion() : Region.EMPTY;
            }

            @Override
            public ColorSelection selectedColors() {
                return dropperHost != null ? dropperHost.selectedColors() : ColorSelection.NONE;
            }
        });
        toolHeader.getChildren().add(toolStack.addButton());
        toolScroll.setContent(toolStack.node());
        // sidecar changed by another program: show the reloaded tools
        EditPipeline.addReloadListener(f -> {
            if (f == selectedMediaFile) {
                toolStack.setFile(f);
            }
        });
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
        colorMask.setFile(f);
        sampler.clear();
        toolStack.setFile(f);
    }

    /**
     * The mouse is over the pixel p of the original in the editor (null =
     * outside the photo): shown in the sampler while its tab is open.
     */
    public void sampleAt(Contour.Point p) {
        if (!samplerTab.isSelected() || selectedMediaFile == null || preview == null) {
            return;
        }
        Image16 source = preview.getSource();
        if (p == null || source == null) {
            sampler.clear();
            return;
        }
        sampler.sample(selectedMediaFile.getFilterListWithoutImageData(), source, p.x(), p.y());
    }

    /** The editor view that handles dropper clicks on the image. */
    public void setDropperHost(ToolStackView.DropperHost host) {
        this.dropperHost = host;
    }

    /**
     * Connects the tools to the editor image: the histogram follows every
     * render of the preview.
     */
    public void setPreview(ImagePreview16 preview) {
        this.preview = preview;
        colorMask.setPreview(preview);
        preview.setOnRendered(this::updateHistogram);
    }

    /** Render thread of the preview: histogram of the shown part (the crop). */
    private void updateHistogram(Image16 result) {
        int[] r = cropRect(result);
        Histogram h = r == null ? Histogram.of(result) : Histogram.of(result, r[0], r[1], r[2], r[3]);
        Platform.runLater(() -> {
            histogram = h;
            drawHistogram();
            colorMask.update();
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

    /**
     * The crop of the shown file in pixels of the rendered image {x, y, w, h},
     * or null for the whole image. The crop is stored in pixels of the
     * original; the render may be a smaller proxy.
     */
    private int[] cropRect(Image16 result) {
        MediaFile f = selectedMediaFile;
        Image16 source = preview == null ? null : preview.getSource();
        javafx.geometry.Rectangle2D crop = f == null ? null : f.getCropView();
        if (crop == null || source == null || crop.getWidth() <= 0 || crop.getHeight() <= 0) {
            return null;
        }
        double k = (double) result.width() / source.width();
        return new int[]{(int) Math.round(crop.getMinX() * k), (int) Math.round(crop.getMinY() * k),
            Math.max(1, (int) Math.round(crop.getWidth() * k)), Math.max(1, (int) Math.round(crop.getHeight() * k))};
    }

    private void drawHistogram() {
        HistogramPainter.draw(gc, histogram, drawingCanvas.getWidth(), drawingCanvas.getHeight());
    }

    public void resetUI() {
        histogram = null;
        progressHistogramm.setVisible(true);
        drawingCanvas.setOpacity(0);
        drawHistogram();
    }

    public void shutdown() {
        executor.shutdownNow();
    }

}
