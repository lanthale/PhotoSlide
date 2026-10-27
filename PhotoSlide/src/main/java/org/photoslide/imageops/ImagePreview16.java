package org.photoslide.imageops;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.scene.image.ImageView;
import org.photoslide.datamodel.DisplaySurface;
import org.photoslide.datamodel.FilterChain16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.MediaFile;

/**
 * Shows one media file in an ImageView through the EditPipeline: the full
 * size 16 bit source is filtered and written into a DisplaySurface. Renders
 * on its own thread; if settings change faster than it can render, only the
 * latest state is rendered.
 *
 * The displayed image has the full size of the source, so viewport and crop
 * coordinates are the same as with the former JavaFX image.
 */
public final class ImagePreview16 {

    private static final Logger LOG = Logger.getLogger(ImagePreview16.class.getName());

    /** Share of loading in the progress; the rest is the first render. */
    private static final double LOAD_SHARE = 0.9;

    private final ImageView view;
    private final ExecutorService executor;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean renderQueued = new AtomicBoolean();
    private final AtomicReference<List<ImageFilter>> pendingFilters = new AtomicReference<>();
    private final Image16[] scratch = new Image16[2];

    private volatile MediaFile file;
    private volatile Image16 source;
    private DisplaySurface surface;
    private Runnable onShown;
    private Consumer<Throwable> onFailed;
    private Consumer<Image16> onRendered;
    private DoubleConsumer onProgress;

    public ImagePreview16(ImageView view, String threadName) {
        this.view = view;
        this.executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name(threadName).daemon().factory());
        EditPipeline.addFilterListener(changed -> {
            MediaFile f = file;
            if (f != null && changed != null && f.getName().equals(changed.getName())) {
                refresh();
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
     * Render thread, after every render. The image is only valid during the
     * call (the buffer is reused).
     */
    public void setOnRendered(Consumer<Image16> onRendered) {
        this.onRendered = onRendered;
    }

    public MediaFile getFile() {
        return file;
    }

    /**
     * Loads and shows the file. The ImageView keeps its current image (e.g.
     * a thumbnail) until the first render is done. JavaFX thread.
     */
    public void show(MediaFile f) {
        final long gen = generation.incrementAndGet();
        file = f;
        source = null;
        pendingFilters.set(f.getFilterListWithoutImageData());
        executor.submit(() -> {
            if (gen != generation.get()) {
                return;
            }
            try {
                Image16 src = EditPipeline.loadSource(f, p -> progress(gen, p * LOAD_SHARE));
                if (gen != generation.get()) {
                    return;
                }
                source = src;
                render(gen, true);
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

    /** Renders again with the current filters of the file. Any thread. */
    public void refresh() {
        MediaFile f = file;
        if (f == null) {
            return;
        }
        pendingFilters.set(f.getFilterListWithoutImageData());
        if (renderQueued.compareAndSet(false, true)) {
            final long gen = generation.get();
            executor.submit(() -> {
                renderQueued.set(false);
                if (gen == generation.get() && source != null) {
                    render(gen, false);
                }
            });
        }
    }

    /** Stops showing the file and frees the buffers. JavaFX thread. */
    public void clear() {
        generation.incrementAndGet();
        file = null;
        source = null;
        executor.submit(() -> {
            scratch[0] = null;
            scratch[1] = null;
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void render(long gen, boolean first) {
        Image16 src = source;
        List<ImageFilter> filters = pendingFilters.get();
        if (src == null || filters == null) {
            return;
        }
        FilterChain16 chain = EditPipeline.chain(filters);
        Image16 result = chain.render(src, scratch);
        if (gen != generation.get()) {
            return;
        }
        final boolean newSurface = surface == null
                || (int) surface.image().getWidth() != result.width()
                || (int) surface.image().getHeight() != result.height();
        if (newSurface) {
            surface = new DisplaySurface(result.width(), result.height());
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
                if (newSurface || view.getImage() != s.image()) {
                    view.setImage(s.image());
                }
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
}
