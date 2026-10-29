package org.photoslide.editortools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.photoslide.datamodel.Display16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.editpipeline.EditPipeline;
import org.photoslide.imageops.Geometry;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.editpipeline.ImagePreview16;
import org.photoslide.imageops.lz.CloneFilter;
import org.photoslide.imageops.lz.SpotFilter;
import org.photoslide.imageops.mask.ColorSelection;
import org.photoslide.imageops.mask.Region;

/**
 * LightZone's colour selection viewer ("Color Mask"): where the selected
 * tool acts, white = fully, black = not at all. A tool without colour
 * selection or area shows the photo in colour (it acts everywhere).
 */
public final class ColorMaskView {

    /** Long side of the image the mask is computed from. */
    private static final int SIZE = 360;

    private final StackPane pane = new StackPane();
    private final ImageView view = new ImageView();
    private final Label hint = new Label();
    private final ExecutorService executor;
    private final AtomicBoolean queued = new AtomicBoolean();
    private ImagePreview16 preview;
    private MediaFile file;
    private AbstractImageFilter tool;
    private boolean shown;
    /** Render thread only: scaled source and the source it belongs to. */
    private Image16 small;
    private Image16 smallOf;

    /**
     * Everything the render thread needs, taken on the JavaFX thread.
     *
     * @param filters the tools below the selected tool, or all tools when the
     * edited photo is shown
     * @param tool the tool whose mask is shown, null = the edited photo
     */
    private record Job(Image16 source, List<ImageFilter> filters, AbstractImageFilter tool, Geometry geometry) {
    }

    public ColorMaskView(ExecutorService executor) {
        this.executor = executor;
        view.setPreserveRatio(true);
        view.fitWidthProperty().bind(pane.widthProperty());
        view.fitHeightProperty().bind(pane.heightProperty());
        hint.setWrapText(true);
        hint.setTextFill(Color.web("#bfbfbf"));
        hint.setStyle("-fx-background-color: rgba(0,0,0,0.55); -fx-padding: 2 4 2 4; -fx-font-size: 9pt;");
        StackPane.setAlignment(hint, Pos.BOTTOM_LEFT);
        pane.setMinSize(0, 0);
        pane.getChildren().addAll(view, hint);
        setTool(null);
    }

    public StackPane node() {
        return pane;
    }

    public void setPreview(ImagePreview16 preview) {
        this.preview = preview;
    }

    /** JavaFX thread. */
    public void setFile(MediaFile f) {
        file = f;
        setTool(null);
    }

    /** The tool whose mask is shown (null = none). JavaFX thread. */
    public void setTool(AbstractImageFilter t) {
        tool = t;
        update();
    }

    /** Only computed while the tab is visible. JavaFX thread. */
    public void setShown(boolean shown) {
        this.shown = shown;
        update();
    }

    /** Computes the mask again (after the edits or the tool changed). JavaFX thread. */
    public void update() {
        if (!shown || file == null || preview == null || preview.getSource() == null) {
            return;
        }
        if (tool != null && !file.getFilterList().contains(tool)) {
            tool = null;                    // removed, or replaced by a step of the history
        }
        // a tool without colour selection or area acts everywhere: show the edited photo
        AbstractImageFilter t = null;
        if (tool != null && acts(tool)) {
            try {
                t = (AbstractImageFilter) tool.clone();
            } catch (CloneNotSupportedException ex) {
                return;
            }
        }
        List<ImageFilter> filters = new ArrayList<>();
        for (ImageFilter f : file.getFilterListWithoutImageData()) {
            if (t == null || f.getPosition() < t.getPosition()) {
                filters.add(f);
            }
        }
        Job job = new Job(preview.getSource(), filters, t, file.getGeometry());
        if (t != null) {
            hint.setText(t.displayName() + ": white = acts fully, black = not at all");
        } else if (tool != null) {
            hint.setText(tool.displayName() + " acts on the whole image");
        } else {
            hint.setText("Edited photo. Click on a tool to see where it acts");
        }
        if (queued.compareAndSet(false, true)) {
            executor.submit(() -> {
                queued.set(false);
                render(job);
            });
        }
    }

    private static boolean useRegion(AbstractImageFilter t) {
        return !t.getRegion().isEmpty() && !(t instanceof CloneFilter) && !(t instanceof SpotFilter);
    }

    private static boolean acts(AbstractImageFilter t) {
        return useRegion(t) || t.getColorSelection().isActive();
    }

    /** Render thread. */
    private void render(Job job) {
        Image16 src = job.source();
        if (smallOf != src) {
            double s = Math.min(1.0, (double) SIZE / Math.max(src.width(), src.height()));
            small = src.scaledTo(Math.max(1, (int) Math.round(src.width() * s)),
                    Math.max(1, (int) Math.round(src.height() * s)));
            smallOf = src;
        }
        final int w = small.width();
        final int h = small.height();
        RenderContext ctx = new RenderContext((double) w / src.width());
        Image16 back = EditPipeline.chain(job.filters(), ctx).render(small);
        Image16 shownImage;
        AbstractImageFilter t = job.tool();
        if (t == null) {
            shownImage = back;
        } else {
            Region region = t.getRegion();
            float[] m = useRegion(t) ? region.rasterize(w, h, ctx) : null;
            ColorSelection cs = t.getColorSelection();
            final short[] d = back.data();
            final short[] out = new short[w * h * 3];
            IntStream.range(0, h).parallel().forEach(y -> {
                for (int x = 0, o = y * w, i = o * 3; x < w; x++, o++, i += 3) {
                    float v = m == null ? 1 : m[o];
                    if (v > 0 && cs.isActive()) {
                        v *= cs.weight((d[i] & 0xFFFF) / 65535f, (d[i + 1] & 0xFFFF) / 65535f, (d[i + 2] & 0xFFFF) / 65535f);
                    }
                    short g = (short) Math.round(Math.max(0, Math.min(1, v)) * 65535);
                    out[i] = g;
                    out[i + 1] = g;
                    out[i + 2] = g;
                }
            });
            shownImage = new Image16(w, h, out, false);
        }
        Image16 result = EditPipeline.chain(List.of(), job.geometry(), ctx).render(shownImage);
        int[] argb = Display16.toArgb(result, false);
        Platform.runLater(() -> {
            WritableImage img = new WritableImage(w, h);
            img.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbPreInstance(), argb, 0, w);
            view.setImage(img);
        });
    }
}
