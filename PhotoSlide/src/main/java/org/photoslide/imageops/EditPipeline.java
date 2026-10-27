package org.photoslide.imageops;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.image.Image;
import org.librawfx.RAWImageLoader;
import org.librawjava.LibrawException;
import org.librawjava.LibrawImage;
import org.librawjava.RawFormatDetector;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.FilterChain16;
import org.photoslide.datamodel.FxImage16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.MediaFile;

/**
 * The one way from a media file to a displayed or exported image: load the
 * file as linear 16 bit (Image16), run the filters of the media file as a
 * FilterChain16 and convert to 8 bit only at the end. Lighttable, editor,
 * thumbnails and export all use it, so they show the same result.
 */
public final class EditPipeline {

    private static final Logger LOG = Logger.getLogger(EditPipeline.class.getName());

    /** Full size sources kept in memory (one 24 MP image is about 144 MB). */
    private static final int CACHE_SIZE = 2;

    private static final Map<String, CompletableFuture<Image16>> CACHE = new LinkedHashMap<>(4, 0.75f, true);

    private static final List<Consumer<MediaFile>> FILTER_LISTENERS = new CopyOnWriteArrayList<>();

    private static final Set<MediaFile> PENDING_SAVES = ConcurrentHashMap.newKeySet();

    private static final ExecutorService SAVER = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name("editPipelineSave").daemon().factory());

    private EditPipeline() {
    }

    /**
     * Full size linear source of the file, shared between all views. Blocks
     * until the file is decoded. Any thread except the JavaFX thread.
     */
    public static Image16 loadSource(MediaFile file) throws IOException {
        return loadSource(file, p -> {
        });
    }

    /**
     * Like loadSource(file), and reports the loading progress (0..1, on the
     * loading thread). If another view is already loading the same file, no
     * progress is reported and the call waits for that result.
     */
    public static Image16 loadSource(MediaFile file, DoubleConsumer progress) throws IOException {
        Path path = file.getPathStorage();
        FileTime modified = Files.getLastModifiedTime(path);
        String key = path.toAbsolutePath() + "|" + modified.toMillis();
        CompletableFuture<Image16> future;
        boolean owner = false;
        synchronized (CACHE) {
            future = CACHE.get(key);
            if (future == null) {
                future = new CompletableFuture<>();
                CACHE.put(key, future);
                owner = true;
                Iterator<String> it = CACHE.keySet().iterator();
                while (CACHE.size() > CACHE_SIZE && it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
        }
        if (owner) {
            try {
                future.complete(loadSourceUncached(file, progress));
            } catch (IOException | RuntimeException ex) {
                future.completeExceptionally(ex);
                synchronized (CACHE) {
                    CACHE.remove(key);
                }
            }
        }
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Loading interrupted: " + path, ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof IOException io) {
                throw io;
            }
            throw new IOException("Cannot load " + path, ex.getCause());
        }
    }

    /**
     * Decodes the file without the cache (for exports of many files). RAW
     * files are decoded by LibRaw in 16 bit linear, all others by JavaFX in 8
     * bit and converted to linear 16 bit.
     */
    public static Image16 loadSourceUncached(MediaFile file) throws IOException {
        return loadSourceUncached(file, p -> {
        });
    }

    /**
     * Like loadSourceUncached(file), and reports the loading progress (0..1,
     * on the loading thread).
     */
    public static Image16 loadSourceUncached(MediaFile file, DoubleConsumer progress) throws IOException {
        Path path = file.getPathStorage();
        progress.accept(0);
        if (file.isRawImage() && !file.isHEIFImage()) {
            byte[] bytes = Files.readAllBytes(path);
            if (RawFormatDetector.isRaw(bytes)) {
                try {
                    LibrawImage libraw = new LibrawImage(path.toString(), RAWImageLoader.getSettings());
                    libraw.setProgressListener((done, total) -> progress.accept((double) done / total));
                    return Image16.fromRaw(libraw.readPixelData16(bytes, true));
                } catch (LibrawException ex) {
                    if (!ex.isUnsupportedFormat()) {
                        throw ex;
                    }
                    LOG.log(Level.FINE, "{0} is not a RAW file, using JavaFX", path);
                }
            }
        }
        Image img = loadFxImage(file.getImageUrl().toString(), progress);
        if (img.isError()) {
            throw new IOException("Cannot load " + path, img.getException());
        }
        return FxImage16.fromImage(img);
    }

    /**
     * Loads with JavaFX in the background (which reports progress) and waits
     * for the result. The progress events of JavaFX arrive on the JavaFX
     * thread and are passed on from there.
     */
    private static Image loadFxImage(String url, DoubleConsumer progress) throws IOException {
        Image img = new Image(url, true);
        CountDownLatch done = new CountDownLatch(1);
        Runnable check = () -> {
            if (img.getProgress() >= 1.0 || img.isError()) {
                done.countDown();
            }
        };
        img.progressProperty().addListener((o, oldValue, newValue) -> {
            progress.accept(newValue.doubleValue());
            check.run();
        });
        img.errorProperty().addListener((o) -> check.run());
        check.run();                       // already finished before the listeners were added
        try {
            done.await();
        } catch (InterruptedException ex) {
            img.cancel();
            Thread.currentThread().interrupt();
            throw new IOException("Loading interrupted: " + url, ex);
        }
        return img;
    }

    /**
     * Removes the file from the source cache, so the next show() decodes it
     * again (e.g. after the RAW settings were changed).
     */
    public static void invalidate(MediaFile file) {
        String prefix = file.getPathStorage().toAbsolutePath() + "|";
        synchronized (CACHE) {
            CACHE.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    /** Empties the source cache, e.g. after the RAW decoder settings changed. */
    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    /** Filter chain for the given settings, ordered by position. */
    public static FilterChain16 chain(List<ImageFilter> filters) {
        List<ImageFilter> sorted = new ArrayList<>(filters);
        sorted.sort(Comparator.comparingInt(ImageFilter::getPosition));
        FilterChain16 chain = new FilterChain16();
        for (ImageFilter f : sorted) {
            Filter16 op = f.toFilter16();
            if (op != null) {
                chain.add(op);
            }
        }
        return chain;
    }

    /**
     * Applies the filters to a small 8 bit image (thumbnails). The image is
     * not changed.
     */
    public static Image applyTo(Image img, List<ImageFilter> filters) {
        FilterChain16 chain = chain(filters);
        Image16 result = chain.render(FxImage16.fromImage(img));
        return FxImage16.toImage(result);
    }

    /** Full size 8 bit result with all filters (export). */
    public static Image renderForExport(MediaFile file) throws IOException {
        Image16 source = loadSourceUncached(file);
        return FxImage16.toImage(chain(file.getFilterListWithoutImageData()).render(source));
    }

    /**
     * Returns the filter with the name, adds the one from the factory if the
     * file has none, and sets the values.
     */
    public static ImageFilter setFilterValues(MediaFile file, String name,
            java.util.function.Supplier<ImageFilter> factory, float[] values) {
        ImageFilter f = file.getFilterForName(name);
        if (f == null) {
            f = factory.get();
            file.addImageFilter(f);
        }
        f.setValues(values);
        return f;
    }

    /** Called by every view that shows filtered images of a file. */
    public static void addFilterListener(Consumer<MediaFile> listener) {
        FILTER_LISTENERS.add(listener);
    }

    /**
     * Must be called after the filter list or values of the file changed
     * (JavaFX thread). All views showing the file render again and the edits
     * are saved in the background (several changes in a row are saved once).
     */
    public static void filtersChanged(MediaFile file) {
        for (Consumer<MediaFile> l : FILTER_LISTENERS) {
            l.accept(file);
        }
        if (PENDING_SAVES.add(file)) {
            SAVER.submit(() -> {
                PENDING_SAVES.remove(file);
                file.saveEdits();
            });
        }
    }
}
