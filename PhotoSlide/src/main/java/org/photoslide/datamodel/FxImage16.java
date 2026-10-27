package org.photoslide.datamodel;

import java.util.stream.IntStream;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/**
 * Bridge between JavaFX images (8 bit, sRGB encoded) and Image16 (linear).
 * Used for files that are not decoded by LibRaw (JPEG, PNG, TIFF, HEIF) and
 * for one-shot results such as thumbnails and exports. For an ImageView that
 * is updated often use DisplaySurface.
 */
public final class FxImage16 {

    /** sRGB encoded 8 bit value -> linear 16 bit value */
    private static final short[] TO_LINEAR = new short[256];

    static {
        for (int i = 0; i < 256; i++) {
            TO_LINEAR[i] = (short) Math.round(Display16.srgbDecode(i / 255.0) * 65535.0);
        }
    }

    private FxImage16() {
    }

    /** Alpha is ignored. Any thread, the image must be completely loaded. */
    public static Image16 fromImage(Image img) {
        final int w = (int) img.getWidth();
        final int h = (int) img.getHeight();
        final int[] argb = new int[w * h];
        img.getPixelReader().getPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        final Image16 out = Image16.blank(w, h, true);
        final short[] d = out.data();
        IntStream.range(0, h).parallel().forEach(y -> {
            int i = y * w;
            int o = i * Image16.CHANNELS;
            for (int x = 0; x < w; x++, i++, o += 3) {
                int p = argb[i];
                d[o] = TO_LINEAR[(p >> 16) & 0xFF];
                d[o + 1] = TO_LINEAR[(p >> 8) & 0xFF];
                d[o + 2] = TO_LINEAR[p & 0xFF];
            }
        });
        return out;
    }

    /** Converts to a new 8 bit image (dithered). Any thread. */
    public static WritableImage toImage(Image16 img) {
        final int w = img.width();
        final int h = img.height();
        WritableImage out = new WritableImage(w, h);
        out.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbPreInstance(),
                Display16.toArgb(img, true), 0, w);
        return out;
    }
}
