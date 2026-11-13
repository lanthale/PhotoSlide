package org.photoslide.datamodel.customformats.tiffsupport;

import com.sun.javafx.iio.ImageFrame;
import com.sun.javafx.iio.ImageStorage;
import com.sun.javafx.iio.common.ImageLoaderImpl;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Lädt TIFF-Bilder über einen ImageIO-Reader (JDK ab 9 oder TwelveMonkeys
 * imageio-tiff) und verkleinert sie speicherschonend auf die gewünschte Größe.
 *
 * @author selfemp
 */
public class TIFFImageLoader extends ImageLoaderImpl {

    private static final int BYTES_PER_PIXEL = 4; // RGBA

    private final InputStream input;

    protected TIFFImageLoader(InputStream input) {
        super(TIFFDescriptor.getInstance());
        this.input = Objects.requireNonNull(input, "input == null!");
    }

    @Override
    public void dispose() {
        try {
            input.close();
        } catch (IOException ignored) {
            // nichts zu tun
        }
    }

    @Override
    public ImageFrame load(int imageIndex, double width, double height,
            boolean preserveAspectRatio, boolean smooth,
            float screenPixelScale, float imagePixelScale) throws IOException {
        if (imageIndex != 0) {
            return null;
        }

        BufferedImage image;
        // kleine Dateien im Speicher, große über einen Platten-Cache: der Reader springt
        // zu den IFDs am Dateiende, ein Memory-Cache hielte dann die ganze Datei im Heap
        // (eine 472 MB TIFF führt bei 1 GB Heap zum OutOfMemoryError)
        try (ImageInputStream iis = openCached(input)) {
            image = readScaled(iis, width, height, preserveAspectRatio, smooth);
        } catch (IOException | RuntimeException ex) {
            throw new IOException("Fehler beim Lesen von TIFF: " + ex, ex);
        } finally {
            dispose();
        }

        return new ImageFrame(ImageStorage.ImageType.RGBA, toRgbaBuffer(image),
                image.getWidth(), image.getHeight(),
                image.getWidth() * BYTES_PER_PIXEL, 1.0f, null);
    }

    /**
     * Lädt ein verkleinertes TIFF direkt aus der Datei (wahlfreier Zugriff).
     * Für Thumbnails statt {@code new Image(url, w, h, ...)}: JavaFX liest über
     * einen markierten BufferedInputStream, der jedes gelesene Byte puffert,
     * weil die IFDs oft am Dateiende liegen, also die ganze Datei (eine
     * 472 MB TIFF ergibt bei 1 GB Heap einen OutOfMemoryError).
     *
     * @return das Bild, höchstens maxW x maxH, Seitenverhältnis bleibt
     */
    public static javafx.scene.image.Image loadScaled(java.nio.file.Path file, double maxW, double maxH) throws IOException {
        BufferedImage image;
        try (ImageInputStream iis = ImageIO.createImageInputStream(file.toFile())) {
            if (iis == null) {
                throw new IOException("Cannot open " + file);
            }
            image = readScaled(iis, maxW, maxH, true, true);
        }
        int w = image.getWidth();
        int h = image.getHeight();
        int[] argb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        javafx.scene.image.WritableImage fx = new javafx.scene.image.WritableImage(w, h);
        fx.getPixelWriter().setPixels(0, 0, w, h, javafx.scene.image.PixelFormat.getIntArgbInstance(), argb, 0, w);
        return fx;
    }

    /** Bis zu dieser Größe wird die Datei im Speicher gehalten. */
    private static final int MEMORY_CACHE_LIMIT = 32 * 1024 * 1024;

    /**
     * Liest höchstens MEMORY_CACHE_LIMIT Bytes in den Speicher. Ist die Datei
     * damit vollständig, wird sie aus dem Speicher gelesen, sonst über eine
     * temporäre Datei (FileCacheImageInputStream), die beim Schließen gelöscht wird.
     */
    private static ImageInputStream openCached(InputStream in) throws IOException {
        byte[] head = in.readNBytes(MEMORY_CACHE_LIMIT);
        if (head.length < MEMORY_CACHE_LIMIT) {
            return new MemoryCacheImageInputStream(new ByteArrayInputStream(head));
        }
        InputStream rest = new SequenceInputStream(new ByteArrayInputStream(head), in);
        return new FileCacheImageInputStream(rest, null);
    }

    /**
     * Wählt einen echten TIFF-Reader. Der RAW-Reader (librawimageio) meldet sich
     * ebenfalls für TIFF (viele RAW-Formate sind TIFF-basiert) und steht oft an
     * erster Stelle, ignoriert aber das Subsampling: für ein Thumbnail würde das
     * volle Bild decodiert (bei 1 GB Heap und mehreren Thumbnails OutOfMemoryError).
     */
    public static ImageReader selectReader(ImageInputStream iis) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        ImageReader first = null;
        ImageReader tiff = null;
        while (readers.hasNext()) {
            ImageReader r = readers.next();
            if (tiff == null && r.getClass().getName().toLowerCase().contains("tiff")) {
                tiff = r;
            } else if (first == null) {
                first = r;
            } else {
                r.dispose();
            }
        }
        if (tiff != null) {
            if (first != null) {
                first.dispose();
            }
            return tiff;
        }
        if (first == null) {
            throw new IOException("Kein ImageIO-Reader für TIFF gefunden");
        }
        return first;
    }

    private static BufferedImage readScaled(ImageInputStream iis, double reqW, double reqH,
            boolean keepRatio, boolean smooth) throws IOException {
        ImageReader reader = selectReader(iis);
        try {
            // wahlfreier Zugriff nötig (TIFF-IFDs liegen oft am Dateiende)
            reader.setInput(iis, false, false);
            int srcW = reader.getWidth(0);
            int srcH = reader.getHeight(0);
            int[] target = computeTargetSize(srcW, srcH, reqW, reqH, keepRatio);

            // 1. Grobes Verkleinern schon beim Lesen (spart RAM und Zeit)
            ImageReadParam param = reader.getDefaultReadParam();
            int sub = computeSubsampling(srcW, srcH, target[0], target[1], smooth);
            if (sub > 1) {
                param.setSourceSubsampling(sub, sub, 0, 0);
            }
            BufferedImage read = reader.read(0, param);

            // 2. Feines Verkleinern in Halbierungsschritten
            return scaleProgressive(read, target[0], target[1], smooth);
        } finally {
            reader.dispose();
        }
    }

    /**
     * Subsampling überspringt Pixel ohne Filter und aliast daher selbst.
     * Deshalb nur so weit verkleinern, dass mindestens das 2-fache der
     * Zielgröße übrig bleibt. Den Rest erledigt die gefilterte Skalierung.
     */
    private static int computeSubsampling(int srcW, int srcH, int tw, int th, boolean smooth) {
        double ratio = Math.min((double) srcW / tw, (double) srcH / th);
        if (ratio < 2) {
            return 1;
        }
        return Math.max(1, smooth ? (int) (ratio / 2) : (int) ratio);
    }

    private static int[] computeTargetSize(int srcW, int srcH,
            double reqW, double reqH, boolean keepRatio) {
        if (reqW <= 0 && reqH <= 0) {
            return new int[]{srcW, srcH};
        }
        double w;
        double h;
        if (reqW > 0 && reqH > 0) {
            if (keepRatio) {
                double scale = Math.min(reqW / srcW, reqH / srcH);
                w = srcW * scale;
                h = srcH * scale;
            } else {
                w = reqW;
                h = reqH;
            }
        } else if (reqW > 0) {
            w = reqW;
            h = srcH * (reqW / srcW);
        } else {
            h = reqH;
            w = srcW * (reqH / srcH);
        }
        return new int[]{Math.max(1, (int) Math.round(w)), Math.max(1, (int) Math.round(h))};
    }

    /** Halbiert schrittweise, bis das Ziel weniger als 2x entfernt ist, dann Endskalierung. */
    private static BufferedImage scaleProgressive(BufferedImage src, int tw, int th, boolean smooth) {
        BufferedImage cur = src;
        int cw = src.getWidth();
        int ch = src.getHeight();
        if (smooth) {
            while (cw / 2 >= tw && ch / 2 >= th) {
                cw /= 2;
                ch /= 2;
                cur = drawScaled(cur, cw, ch, true);
            }
        }
        if (cw != tw || ch != th || cur.getType() != BufferedImage.TYPE_INT_ARGB) {
            cur = drawScaled(cur, tw, th, smooth);
        }
        return cur;
    }

    private static BufferedImage drawScaled(BufferedImage src, int w, int h, boolean smooth) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    smooth ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                           : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /** ARGB (int) -> RGBA (byte). */
    private static ByteBuffer toRgbaBuffer(BufferedImage argbImage) {
        int[] argb = ((DataBufferInt) argbImage.getRaster().getDataBuffer()).getData();
        byte[] out = new byte[argb.length * BYTES_PER_PIXEL];
        for (int i = 0, o = 0; i < argb.length; i++, o += BYTES_PER_PIXEL) {
            int p = argb[i];
            out[o]     = (byte) (p >> 16);  // R
            out[o + 1] = (byte) (p >> 8);   // G
            out[o + 2] = (byte) p;          // B
            out[o + 3] = (byte) (p >>> 24); // A
        }
        return ByteBuffer.wrap(out);
    }
}