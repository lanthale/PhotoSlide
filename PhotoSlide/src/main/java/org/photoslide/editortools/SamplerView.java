package org.photoslide.editortools;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.photoslide.datamodel.Display16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.WorkingSpace;
import org.photoslide.imageops.EditPipeline;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.imageops.lz.ZonesFilter;

/**
 * LightZone's sampler: colour of the edited photo under the mouse (all
 * tools applied), as sRGB values, luminosity and zone.
 */
public final class SamplerView {

    private final HBox box = new HBox(10);
    private final Rectangle swatch = new Rectangle(44, 44);
    private final Label rgb = new Label();
    private final Label luminosity = new Label();
    private final Label zone = new Label();
    private final Label position = new Label();
    private final ExecutorService executor;
    private final AtomicReference<Job> pending = new AtomicReference<>();

    private record Job(List<ImageFilter> filters, Image16 source, int x, int y) {
    }

    public SamplerView(ExecutorService executor) {
        this.executor = executor;
        swatch.setStroke(Color.web("#5a5a5a"));
        GridPane values = new GridPane();
        values.setHgap(6);
        values.setVgap(2);
        values.addRow(0, label("RGB"), rgb);
        values.addRow(1, label("Luminosity"), luminosity);
        values.addRow(2, label("Zone"), zone);
        values.addRow(3, label("Position"), position);
        box.getChildren().addAll(swatch, values);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(8));
        clear();
    }

    public HBox node() {
        return box;
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.setTextFill(Color.web("#9f9f9f"));
        return l;
    }

    public void clear() {
        swatch.setFill(Color.TRANSPARENT);
        rgb.setText("–");
        luminosity.setText("–");
        zone.setText("–");
        position.setText("move the mouse over the photo");
    }

    /**
     * Samples the photo at the pixel of the original (JavaFX thread). Only the
     * latest position is computed when the mouse moves faster.
     */
    public void sample(List<ImageFilter> filters, Image16 source, double x, double y) {
        int px = (int) Math.max(0, Math.min(source.width() - 1, x));
        int py = (int) Math.max(0, Math.min(source.height() - 1, y));
        if (pending.getAndSet(new Job(filters, source, px, py)) == null) {
            executor.submit(() -> {
                Job job = pending.getAndSet(null);
                if (job == null) {
                    return;
                }
                double[] lin = EditPipeline.sampleBelow(job.filters(), job.source(), Integer.MAX_VALUE, job.x(), job.y());
                double[] s = WorkingSpace.multiply(WorkingSpace.WORKING_TO_SRGB, lin);
                double[] enc = new double[3];
                for (int c = 0; c < 3; c++) {
                    enc[c] = Display16.srgbEncode(Math.max(0, Math.min(1, s[c])));
                }
                double lum = Math.max(0, WorkingSpace.luminance(lin[0], lin[1], lin[2]));
                double z = ZonesFilter.zoneOf(Math.min(1, lum)) * (ZonesFilter.ZONES - 1);
                Platform.runLater(() -> {
                    swatch.setFill(Color.color(enc[0], enc[1], enc[2]));
                    rgb.setText(String.format(Locale.ROOT, "%d  %d  %d",
                            Math.round(enc[0] * 255), Math.round(enc[1] * 255), Math.round(enc[2] * 255)));
                    luminosity.setText(String.format(Locale.ROOT, "%.1f %%", Display16.srgbEncode(Math.min(1, lum)) * 100));
                    zone.setText(String.format(Locale.ROOT, "%.1f", z));
                    position.setText(job.x() + ", " + job.y());
                });
            });
        }
    }
}
