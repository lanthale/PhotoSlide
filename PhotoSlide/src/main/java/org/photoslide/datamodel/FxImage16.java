package org.photoslide.datamodel;

import java.util.stream.IntStream;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/**
 * Bridge between JavaFX images (8 bit, sRGB encoded) and Image16 (linear
 * working space, see WorkingSpace).
 * Used for files that are not decoded by LibRaw (JPEG, PNG, TIFF, HEIF) and
 * for one-shot results such as thumbnails and exports. For an ImageView that
 * is updated often use DisplaySurface.
 */
public final class FxImage16 {

    /** sRGB encoded 8 bit value -> linear 0..65535 (not rounded) */
    private static final float[] TO_LINEAR = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            TO_LINEAR[i] = (float) (Display16.srgbDecode(i / 255.0) * 65535.0);
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
                toWorking(TO_LINEAR[(p >> 16) & 0xFF], TO_LINEAR[(p >> 8) & 0xFF], TO_LINEAR[p & 0xFF], d, o);
            }
        });
        return out;
    }

    private static final float[] SRGB_TO_WORKING = flatten(WorkingSpace.SRGB_TO_WORKING);

    /**
     * Converts linear sRGB (0..65535) to the working space and stores it at
     * d[o..o+2]. Used for all non-RAW sources.
     */
    static void toWorking(float r, float g, float b, short[] d, int o) {
        final float[] m = SRGB_TO_WORKING;
        d[o] = clamp16(m[0] * r + m[1] * g + m[2] * b);
        d[o + 1] = clamp16(m[3] * r + m[4] * g + m[5] * b);
        d[o + 2] = clamp16(m[6] * r + m[7] * g + m[8] * b);
    }

    private static short clamp16(float v) {
        int q = (int) (v + 0.5f);
        return (short) (q < 0 ? 0 : (q > 65535 ? 65535 : q));
    }

    private static float[] flatten(double[][] m) {
        return new float[]{(float) m[0][0], (float) m[0][1], (float) m[0][2],
            (float) m[1][0], (float) m[1][1], (float) m[1][2],
            (float) m[2][0], (float) m[2][1], (float) m[2][2]};
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
