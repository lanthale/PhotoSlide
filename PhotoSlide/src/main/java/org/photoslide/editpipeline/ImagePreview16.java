package org.photoslide.editpipeline;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Window;
import org.photoslide.imageops.Geometry;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.datamodel.DisplaySurface;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.datamodel.RenderContext;

/**
 * Shows one media file in an ImageView through the EditPipeline. Renders on
 * its own thread; if settings change faster than it can render, only the
 * latest state is rendered.
 *
 * While settings change, a proxy in the size of the view is rendered (fast,
 * radii scaled with RenderContext); 250 ms after the last change the full
 * resolution follows. The viewport of the ImageView is kept in pixels of the
 * original and converted whenever the shown image changes size, so zoom and
 * crop stay in place. Code that stores viewport coordinates must divide by
 * getDisplayScale().
 */
public final class ImagePreview16 {

    private static final Logger LOG = Logger.getLogger(ImagePreview16.class.getName());

    /** Share of loading in the progress; the rest is the first render. */
    private static final double LOAD_SHARE = 0.9;
    /** Delay after the last change before the full resolution is rendered. */
    private static final long FULL_RENDER_DELAY_MS = 250;
    /** A proxy is only used if it is at most this fraction of the original. */
    private static final double MAX_PROXY_SCALE = 0.7;

    private final ImageView view;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicLong changes = new AtomicLong();
    private final AtomicBoolean proxyQueued = new AtomicBoolean();
    private final AtomicReference<Pending> pending = new AtomicReference<>();
    private final Image16[] scratchFull = new Image16[2];
    private final Image16[] scratchProxy = new Image16[2];
    /** Proxy renders while a slider is dragged: only the changed tool and the ones after it. */
    private final PrefixCachedRenderer proxyRenderer = new PrefixCachedRenderer();

    private volatile MediaFile file;
    private volatile Image16 source;
    private volatile double proxyLongSide = 1600;
    private Image16 proxySource;
    private DisplaySurface fullSurface;
    private DisplaySurface proxySurface;

    /** JavaFX thread only: the image this preview put into the view and its scale. */
    private Image shownImage;
    private double shownScale = 1;
    /** Viewport in pixels of the original, null = whole image. JavaFX thread only. */
    private Rectangle2D fullViewport;
    private boolean settingViewport;

    /** Settings the next render uses. */
    private record Pending(List<ImageFilter> filters, Geometry geometry) {

        static Pending of(MediaFile f) {
            return new Pending(f.getFilterListWithoutImageData(), f.getGeometry());
        }
    }

    private Runnable onShown;
    private Consumer<Throwable> onFailed;
    private Consumer<Image16> onRendered;
    private DoubleConsumer onProgress;

    public ImagePreview16(ImageView view, String threadName) {
        this.view = view;
        this.executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name(threadName).daemon().factory());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name(threadName + "-timer").daemon().factory());
        EditPipeline.addFilterListener(changed -> {
            MediaFile f = file;
            if (f != null && changed != null && f.getName().equals(changed.getName())) {
                refresh();
            }
        });
        // zoom, scroll and crop code set the viewport in pixels of the shown image
        view.viewportProperty().addListener((o, oldValue, newValue) -> {
            if (!settingViewport) {
                fullViewport = newValue == null ? null : scale(newValue, 1 / currentScale());
            }
        });
    }

    /** JavaFX thread, after the first render of a file. */
    public void setOnShown(Runnable onShown) {
        this.onShown = onShown;
    }

    /** JavaFX thread, if the file cannot be loaded. */
    public void setOnFailed(Consumer<Throwable> onFailed) {
        this.onFailed = onFailed;
    }

    /**
     * JavaFX thread, while a file is loaded: 0..1. Reaches 1 when the first
     * render is shown.
     */
    public void setOnProgress(DoubleConsumer onProgress) {
        this.onProgress = onProgress;
    }

    /**
     * Render thread, after every render (proxy and full). The image is only
     * valid during the call (the buffer is reused).
     */
    public void setOnRendered(Consumer<Image16> onRendered) {
        this.onRendered = onRendered;
    }

    public MediaFile getFile() {
        return file;
    }

    /** @return the full size source (linear working space) or null while loading */
    public Image16 getSource() {
        return source;
    }

    /**
     * Size of the image in the view relative to the original (1 = full
     * resolution, smaller while a proxy is shown). JavaFX thread.
     */
    public double getDisplayScale() {
        return currentScale();
    }

    /**
     * Sets the viewport (zoom, crop) in pixels of the original, null = whole
     * image. Applied to every image this preview shows. JavaFX thread.
     */
    public void setViewport(Rectangle2D original) {
        fullViewport = original;
        settingViewport = true;
        try {
            // a thumbnail shown before the first render has another scale: show it whole
            boolean ours = shownImage != null && view.getImage() == shownImage;
            view.setViewport(original == null || !ours ? null : scale(original, shownScale));
        } finally {
            settingViewport = false;
        }
    }

    private double currentScale() {
        return shownImage != null && view.getImage() == shownImage ? shownScale : 1;
    }

    /**
     * Loads and shows the file. The ImageView keeps its current image (e.g.
     * a thumbnail) until the first render is done. JavaFX thread.
     */
    public void show(MediaFile f) {
        final long gen = generation.incrementAndGet();
        file = f;
        source = null;
        updateProxySize();
        pending.set(Pending.of(f));
        executor.submit(() -> {
            if (gen != generation.get()) {
                return;
            }
            try {
                Image16 src = LoadPriority.foreground(() -> EditPipeline.loadSource(f, p -> progress(gen, p * LOAD_SHARE)));
                if (gen != generation.get()) {
                    return;
                }
                source = src;
                proxySource = null;
                // the real size of the original: thumbnails need it to place regions
                Platform.runLater(() -> {
                    Point2D size = f.getOrignalImageSize();
                    if (size == null || size.getX() != src.width() || size.getY() != src.height()) {
                        f.setOrignalImageSize(new Point2D(src.width(), src.height()));
                        f.thumbnailChanged();
                    }
                });
                if (useProxy()) {
                    render(gen, true, true);
                    render(gen, false, false);
                } else {
                    render(gen, true, false);
                }
            } catch (Throwable ex) {
                LOG.log(Level.WARNING, "Cannot show " + f.getName(), ex);
                Platform.runLater(() -> {
                    if (gen == generation.get() && onFailed != null) {
                        onFailed.accept(ex);
                    }
                });
            }
        });
    }

    /**
     * Decodes the shown file again from disk (bypasses the source cache) and
     * shows it. JavaFX thread.
     */
    public void reload() {
        MediaFile f = file;
        if (f == null) {
            return;
        }
        EditPipeline.invalidate(f);
        show(f);
    }

    /** Passes the progress to the JavaFX thread, only for the current file. */
    private void progress(long gen, double value) {
        Platform.runLater(() -> {
            if (gen == generation.get() && onProgress != null) {
                onProgress.accept(value);
            }
        });
    }

    /**
     * Renders again with the current filters of the file: the proxy at once,
     * the full resolution when nothing changed for a moment. Any thread.
     */
    public void refresh() {
        MediaFile f = file;
        if (f == null) {
            return;
        }
        pending.set(Pending.of(f));
        final long gen = generation.get();
        final long change = changes.incrementAndGet();
        if (proxyQueued.compareAndSet(false, true)) {
            executor.submit(() -> {
                proxyQueued.set(false);
                if (gen == generation.get() && source != null) {
                    render(gen, false, useProxy());
                }
            });
        }
        scheduler.schedule(() -> executor.submit(() -> {
            if (gen == generation.get() && change == changes.get() && source != null && useProxy()) {
                render(gen, false, false);
            }
        }), FULL_RENDER_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /** Stops showing the file and frees the buffers. JavaFX thread. */
    public void clear() {
        generation.incrementAndGet();
        file = null;
        source = null;
        fullViewport = null;
        executor.submit(() -> {
            proxySource = null;
            scratchFull[0] = scratchFull[1] = null;
            scratchProxy[0] = scratchProxy[1] = null;
            proxyRenderer.clear();
        });
    }

    public void shutdown() {
        executor.shutdownNow();
        scheduler.shutdownNow();
    }

    /** Long side of the proxy: the size the view shows on screen. JavaFX thread. */
    private void updateProxySize() {
        double outputScale = 1;
        if (view.getScene() != null) {
            Window w = view.getScene().getWindow();
            if (w != null) {
                outputScale = Math.max(w.getOutputScaleX(), w.getOutputScaleY());
            }
        }
        double side = Math.max(view.getFitWidth(), view.getFitHeight()) * outputScale;
        proxyLongSide = side > 64 ? side : 1600;
    }

    private boolean useProxy() {
        Image16 src = source;
        return src != null && proxyLongSide / Math.max(src.width(), src.height()) <= MAX_PROXY_SCALE;
    }

    /** Render thread. */
    private Image16 proxySource(Image16 src) {
        double s = proxyLongSide / Math.max(src.width(), src.height());
        int w = Math.max(1, (int) Math.round(src.width() * s));
        int h = Math.max(1, (int) Math.round(src.height() * s));
        if (proxySource == null || proxySource.width() != w || proxySource.height() != h) {
            proxySource = src.scaledTo(w, h);
        }
        return proxySource;
    }

    /** Render thread. */
    private void render(long gen, boolean first, boolean proxy) {
        Image16 full = source;
        Pending settings = pending.get();
        if (full == null || settings == null) {
            return;
        }
        Image16 src = proxy ? proxySource(full) : full;
        double scale = (double) src.width() / full.width();
        RenderContext ctx = new RenderContext(scale);
        Image16 result = proxy
                ? proxyRenderer.render(src, settings.filters(), settings.geometry(), ctx, scratchProxy)
                : EditPipeline.chain(settings.filters(), settings.geometry(), ctx).render(src, scratchFull);
        if (gen != generation.get()) {
            return;
        }
        DisplaySurface surface = proxy ? proxySurface : fullSurface;
        if (surface == null || (int) surface.image().getWidth() != result.width()
                || (int) surface.image().getHeight() != result.height()) {
            surface = new DisplaySurface(result.width(), result.height());
            if (proxy) {
                proxySurface = surface;
            } else {
                fullSurface = surface;
            }
        }
        final DisplaySurface s = surface;
        s.update(result, true);
        if (onRendered != null) {
            onRendered.accept(result);
        }
        // wait until the JavaFX thread has copied the pixels, so a fast slider
        // cannot queue many full size buffers
        CountDownLatch copied = new CountDownLatch(1);
        Platform.runLater(() -> {
            if (gen == generation.get()) {
                show(s.image(), scale);
                if (first) {
                    if (onProgress != null) {
                        onProgress.accept(1.0);
                    }
                    if (onShown != null) {
                        onShown.run();
                    }
                }
            }
            copied.countDown();
        });
        try {
            copied.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /** Puts the image into the view and converts the viewport. JavaFX thread. */
    private void show(Image img, double scale) {
        if (view.getImage() == img && shownScale == scale) {
            return;
        }
        settingViewport = true;
        try {
            view.setImage(img);
            shownImage = img;
            shownScale = scale;
            view.setViewport(fullViewport == null ? null : scale(fullViewport, scale));
        } finally {
            settingViewport = false;
        }
    }

    private static Rectangle2D scale(Rectangle2D r, double s) {
        return new Rectangle2D(r.getMinX() * s, r.getMinY() * s, r.getWidth() * s, r.getHeight() * s);
    }
}
