package org.photoslide.editpipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import org.librawfx.RAWImageLoader;
import org.librawjava.LibrawException;
import org.librawjava.LibrawImage;
import org.librawjava.OutputColor;
import org.librawjava.RawFormatDetector;
import org.librawdecodersettings.RawDecoderSettings;
import org.photoslide.imageops.Geometry;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.datamodel.AwtImage16;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.FilterChain16;
import org.photoslide.datamodel.FxImage16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.datamodel.RenderContext;

/**
 * The one way from a media file to a displayed or exported image: load the
 * file as linear 16 bit (Image16), run the filters of the media file as a
 * FilterChain16 and convert to 8 bit only at the end. Lighttable, editor,
 * thumbnails and export all use it, so they show the same result.
 */
public final class EditPipeline {

    private static final Logger LOG = Logger.getLogger(EditPipeline.class.getName());

    private static final List<Consumer<MediaFile>> FILTER_LISTENERS = new CopyOnWriteArrayList<>();

    private static final List<Consumer<MediaFile>> RELOAD_LISTENERS = new CopyOnWriteArrayList<>();

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
        return SourceCache.get(file.getPathStorage(), () -> loadSourceUncached(file, progress));
    }

    /**
     * Decodes the file without the cache (for exports of many files) into the
     * working space (16 bit linear ProPhoto). RAW files are decoded by LibRaw
     * directly in ProPhoto, 16 bit TIFF/PNG by ImageIO, all others by JavaFX in
     * 8 bit; non-RAW data is converted from sRGB.
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
                    Image16 linear = Image16.fromRaw(libraw.readPixelData16(bytes, true, OutputColor.PROPHOTO));
                    // base look of the RAW profile of the camera (like the camera JPEG)
                    RawLook look = RawLook.forCamera(cameraKey(libraw));
                    return look.apply(linear, look.fileStops(path, libraw));
                } catch (LibrawException ex) {
                    if (!ex.isUnsupportedFormat()) {
                        throw ex;
                    }
                    LOG.log(Level.FINE, "{0} is not a RAW file, using JavaFX", path);
                }
            }
        }
        String lower = path.getFileName().toString().toLowerCase();
        if (lower.endsWith(".tif") || lower.endsWith(".tiff") || lower.endsWith(".png")) {
            Image16 deep = AwtImage16.read16(path, progress);     // null = only 8 bit per channel
            if (deep != null) {
                return deep;
            }
        }
        Image img = loadFxImage(file.getImageUrl().toString(), progress);
        if (img.isError()) {
            throw new IOException("Cannot load " + path, img.getException());
        }
        return FxImage16.fromImage(img);
    }

    /**
     * Thumbnail of a RAW file made like the editor shows it: decoded at half
     * size by LibRaw in the working space with the RAW profile of the camera,
     * then the base look (exposure, colour matrix, tone curve). Without this
     * the thumbnails would come from LibRaw's 8 bit output (auto brightness,
     * no look) and look different from the editor.
     *
     * @param maxSize longest side of the thumbnail
     * @return the thumbnail, or null if the file is not a RAW LibRaw can
     * read (the caller loads it the usual way)
     */
    public static Image loadRawThumbnail(MediaFile file, int maxSize) throws IOException {
        if (!file.isRawImage() || file.isHEIFImage()) {
            return null;
        }
        Path path = file.getPathStorage();
        byte[] bytes = Files.readAllBytes(path);
        if (!RawFormatDetector.isRaw(bytes)) {
            return null;
        }
        try {
            LibrawImage libraw = new LibrawImage(path.toString(), RAWImageLoader.getSettings());
            String key = cameraKey(libraw);
            RawDecoderSettings settings = RAWImageLoader.getSettings().get(key == null ? "Default" : key);
            if (settings == null) {
                settings = RAWImageLoader.getSettings().get("Default");
            }
            settings = settings == null ? new RawDecoderSettings() : settings.copy();
            settings.setHalfSizeOutput(true);                 // a quarter of the pixels, plenty for a thumbnail
            libraw.setSettingsOverride(settings);
            Image16 linear = Image16.fromRaw(libraw.readPixelData16(bytes, true, OutputColor.PROPHOTO));
            double s = Math.min(1.0, (double) maxSize / Math.max(linear.width(), linear.height()));
            Image16 small = linear.scaledTo(Math.max(1, (int) Math.round(linear.width() * s)),
                    Math.max(1, (int) Math.round(linear.height() * s)));
            RawLook look = RawLook.forCamera(key);
            look.apply(small, look.fileStops(path, libraw));
            return FxImage16.toImage(small);
        } catch (LibrawException ex) {
            if (!ex.isUnsupportedFormat()) {
                throw ex;
            }
            return null;
        }
    }

    /** "Maker Model" as the RAW profiles are named, or null. */
    private static String cameraKey(LibrawImage libraw) {
        try {
            return libraw.getCameraMaker();
        } catch (IOException | RuntimeException ex) {
            LOG.log(Level.FINE, "Camera of the RAW file unknown", ex);
            return null;
        }
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
        SourceCache.invalidate(file.getPathStorage());
    }

    /** Empties the source cache, e.g. after the RAW decoder settings changed. */
    public static void clearCache() {
        SourceCache.clear();
    }

    /**
     * Colour of the image as it enters the tool at the given position (all
     * tools below applied), averaged over 3 x 3 pixels. For droppers.
     *
     * @param source full size source of the file
     * @param x pixel of the original
     * @param y pixel of the original
     * @return linear working space colour 0..1
     */
    public static double[] sampleBelow(MediaFile file, Image16 source, int position, int x, int y) {
        return sampleBelow(file.getFilterListWithoutImageData(), source, position, x, y);
    }

    /**
     * Like sampleBelow(file, ...) with a copy of the filters (any thread).
     */
    public static double[] sampleBelow(List<ImageFilter> filters, Image16 source, int position, int x, int y) {
        final int r = 48;
        Image16 crop = source.crop(x - r, y - r, 2 * r + 1, 2 * r + 1);
        final int x0 = Math.max(0, x - r);
        final int y0 = Math.max(0, y - r);
        List<ImageFilter> below = new ArrayList<>();
        for (ImageFilter f : filters) {
            if (f.getPosition() < position) {
                below.add(f);
            }
        }
        Image16 img = chain(below, new RenderContext(1.0, x0, y0)).render(crop);
        int cx = Math.min(img.width() - 1, Math.max(0, x - x0));
        int cy = Math.min(img.height() - 1, Math.max(0, y - y0));
        double[] sum = new double[3];
        int n = 0;
        for (int yy = Math.max(0, cy - 1); yy <= Math.min(img.height() - 1, cy + 1); yy++) {
            for (int xx = Math.max(0, cx - 1); xx <= Math.min(img.width() - 1, cx + 1); xx++) {
                int i = (yy * img.width() + xx) * 3;
                for (int c = 0; c < 3; c++) {
                    sum[c] += (img.data()[i + c] & 0xFFFF) / 65535.0;
                }
                n++;
            }
        }
        return new double[]{sum[0] / n, sum[1] / n, sum[2] / n};
    }

    /**
     * Filter chain for the given settings, ordered by position.
     *
     * @param ctx size of the rendered image relative to the original
     */
    public static FilterChain16 chain(List<ImageFilter> filters, RenderContext ctx) {
        List<ImageFilter> sorted = new ArrayList<>(filters);
        sorted.sort(Comparator.comparingInt(ImageFilter::getPosition));
        FilterChain16 chain = new FilterChain16();
        for (ImageFilter f : sorted) {
            Filter16 op = f.toFilter16(ctx);
            if (op != null) {
                chain.add(op);
            }
        }
        return chain;
    }

    /**
     * Filter chain for the given settings followed by flip and straighten
     * (the result keeps the size of the source).
     */
    public static FilterChain16 chain(List<ImageFilter> filters, Geometry geometry, RenderContext ctx) {
        FilterChain16 chain = chain(filters, ctx);
        Filter16 g = geometry == null ? null : geometry.toFilter16();
        if (g != null) {
            chain.add(g);
        }
        return chain;
    }

    /**
     * Applies the filters, flip and straighten to a small 8 bit image
     * (thumbnails). The image is not changed.
     *
     * @param originalWidth width of the full image, to scale radii; 0 = unknown
     */
    public static Image applyTo(Image img, List<ImageFilter> filters, Geometry geometry, double originalWidth) {
        double scale = originalWidth > 0 ? Math.min(1.0, img.getWidth() / originalWidth) : 1.0;
        FilterChain16 chain = chain(filters, geometry, new RenderContext(scale));
        Image16 result = chain.render(FxImage16.fromImage(img));
        return FxImage16.toImage(result);
    }

    /**
     * The finished photo as the user sees it, at most maxSize pixels on the
     * long side: source (RAW with its base look), all tools, flip and
     * straighten, then crop and quarter turn. For printing and other views
     * outside the editor. Any thread except the JavaFX thread.
     */
    public static Image renderFinal(MediaFile file, int maxSize) throws IOException {
        Image16 source = loadSource(file);
        Rectangle2D crop = file.getCropView();
        // scale so that the cropped part fits maxSize
        double cropLong = crop == null ? Math.max(source.width(), source.height())
                : Math.max(crop.getWidth(), crop.getHeight());
        double s = Math.min(1.0, maxSize / cropLong);
        Image16 small = source.scaledTo(Math.max(1, (int) Math.round(source.width() * s)),
                Math.max(1, (int) Math.round(source.height() * s)));
        Image16 result = chain(file.getFilterListWithoutImageData(), file.getGeometry(),
                new RenderContext((double) small.width() / source.width())).render(small);
        if (crop != null && crop.getWidth() > 0 && crop.getHeight() > 0) {
            double k = (double) result.width() / source.width();
            result = result.crop((int) Math.round(crop.getMinX() * k), (int) Math.round(crop.getMinY() * k),
                    Math.max(1, (int) Math.round(crop.getWidth() * k)), Math.max(1, (int) Math.round(crop.getHeight() * k)));
        } else if (result == small) {
            result = result.copy();                       // the source must not be changed
        }
        double rotation = ((file.getRotationAngleProperty().get() % 360) + 360) % 360;
        return FxImage16.toImage(rotateQuarters(result, (int) Math.round(rotation / 90) % 4));
    }

    /** Turns the image clockwise by quarters (0..3). */
    static Image16 rotateQuarters(Image16 img, int quarters) {
        if (quarters == 0) {
            return img;
        }
        int w = img.width();
        int h = img.height();
        boolean swap = quarters % 2 == 1;
        Image16 out = Image16.blank(swap ? h : w, swap ? w : h, img.isLinear());
        short[] s = img.data();
        short[] d = out.data();
        int ow = out.width();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int nx, ny;
                switch (quarters) {
                    case 1 -> {
                        nx = h - 1 - y;
                        ny = x;
                    }
                    case 2 -> {
                        nx = w - 1 - x;
                        ny = h - 1 - y;
                    }
                    default -> {
                        nx = y;
                        ny = w - 1 - x;
                    }
                }
                System.arraycopy(s, (y * w + x) * 3, d, (ny * ow + nx) * 3, 3);
            }
        }
        return out;
    }

    /** Full size 8 bit result with all filters, flip and straighten (export; crop and quarter turns follow). */
    public static Image renderForExport(MediaFile file) throws IOException {
        Image16 source = loadSourceUncached(file);
        return FxImage16.toImage(chain(file.getFilterListWithoutImageData(), file.getGeometry(), RenderContext.FULL).render(source));
    }

    /**
     * Returns the filter with the name, adds the one from the factory if the
     * file has none, and sets the first values (the rest keep their values).
     */
    public static ImageFilter setFilterValues(MediaFile file, String name,
            java.util.function.Supplier<ImageFilter> factory, float[] values) {
        ImageFilter f = file.getFilterForName(name);
        if (f == null) {
            f = factory.get();
            file.addImageFilter(f);
        }
        // only the given values change, the others (e.g. set in the editor) stay
        float[] merged = f.getValues();
        System.arraycopy(values, 0, merged, 0, Math.min(values.length, merged.length));
        f.setValues(merged);
        return f;
    }

    /**
     * Called by views that must redraw their controls when the edits of a
     * file were reloaded from its sidecar (changed by another program).
     */
    public static void addReloadListener(Consumer<MediaFile> listener) {
        RELOAD_LISTENERS.add(listener);
    }

    /**
     * The edits of the file were reloaded from disk (JavaFX thread): all views
     * redraw, nothing is saved.
     */
    public static void editsReloaded(MediaFile file) {
        EditHistory history = EditHistory.peek(file);
        if (history != null) {
            history.labelNext("Changed by another program");
            history.record(file);
        }
        for (Consumer<MediaFile> l : FILTER_LISTENERS) {
            l.accept(file);
        }
        for (Consumer<MediaFile> l : RELOAD_LISTENERS) {
            l.accept(file);
        }
    }

    /**
     * The edits of the file were set to a step of its history (JavaFX
     * thread): all views redraw like after a reload, and the state is saved.
     */
    static void editsRestored(MediaFile file) {
        for (Consumer<MediaFile> l : FILTER_LISTENERS) {
            l.accept(file);
        }
        for (Consumer<MediaFile> l : RELOAD_LISTENERS) {
            l.accept(file);
        }
        save(file);
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
        filtersChanged(file, null);
    }

    /**
     * Like filtersChanged(file); label names the step in the history of the
     * file (null = derived from the change). Also for crop, rotation, flip and
     * straighten.
     */
    public static void filtersChanged(MediaFile file, String label) {
        EditHistory history = EditHistory.peek(file);
        if (history != null) {
            if (label != null) {
                history.labelNext(label);
            }
            history.record(file);
        }
        for (Consumer<MediaFile> l : FILTER_LISTENERS) {
            l.accept(file);
        }
        save(file);
    }

    /** Saves the edits in the background (several changes in a row are saved once). */
    private static void save(MediaFile file) {
        if (PENDING_SAVES.add(file)) {
            SAVER.submit(() -> {
                PENDING_SAVES.remove(file);
                file.saveEdits();
                // thumbnails follow once per burst of changes, not on every slider step
                javafx.application.Platform.runLater(file::thumbnailChanged);
            });
        }
    }
}
