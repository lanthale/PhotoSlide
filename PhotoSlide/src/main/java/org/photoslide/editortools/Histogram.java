package org.photoslide.editortools;

import org.photoslide.datamodel.Display16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.WorkingSpace;

/**
 * Histogram of the edited photo as it is shown: the 16 bit result of the
 * pipeline (linear working space) is converted like Display16 does (matrix to
 * linear sRGB, sRGB curve) into 256 levels per channel, plus the luminance
 * of the displayed values. Only a part of the image (the crop) is counted,
 * and only every n-th pixel, so a full size result costs a few milliseconds.
 */
public final class Histogram {

    public static final int LEVELS = 256;
    /** At most this many pixels are counted. */
    private static final int MAX_SAMPLES = 250_000;

    /** linear sRGB 0..65535 -> displayed level 0..255 */
    private static final byte[] TO_LEVEL = new byte[65536];

    static {
        for (int i = 0; i < 65536; i++) {
            TO_LEVEL[i] = (byte) Math.round(Display16.srgbEncode(i / 65535.0) * 255);
        }
    }

    private final int[] red = new int[LEVELS];
    private final int[] green = new int[LEVELS];
    private final int[] blue = new int[LEVELS];
    private final int[] luminance = new int[LEVELS];
    private int samples;
    private int shadowsClipped;
    private int highlightsClipped;

    private Histogram() {
    }

    /** Histogram of the whole image. */
    public static Histogram of(Image16 img) {
        return of(img, 0, 0, img.width(), img.height());
    }

    /**
     * Histogram of a rectangle of the image (clipped to it), e.g. the crop.
     */
    public static Histogram of(Image16 img, int x, int y, int w, int h) {
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(img.width(), x + w);
        int y1 = Math.min(img.height(), y + h);
        Histogram hist = new Histogram();
        if (x1 <= x0 || y1 <= y0) {
            return hist;
        }
        long area = (long) (x1 - x0) * (y1 - y0);
        int step = (int) Math.max(1, Math.ceil(Math.sqrt((double) area / MAX_SAMPLES)));
        final double[][] m = WorkingSpace.WORKING_TO_SRGB;
        final float m00 = (float) m[0][0], m01 = (float) m[0][1], m02 = (float) m[0][2];
        final float m10 = (float) m[1][0], m11 = (float) m[1][1], m12 = (float) m[1][2];
        final float m20 = (float) m[2][0], m21 = (float) m[2][1], m22 = (float) m[2][2];
        final short[] d = img.data();
        final boolean linear = img.isLinear();
        for (int yy = y0; yy < y1; yy += step) {
            for (int xx = x0, i = (yy * img.width() + x0) * 3; xx < x1; xx += step, i += step * 3) {
                float r = d[i] & 0xFFFF;
                float g = d[i + 1] & 0xFFFF;
                float b = d[i + 2] & 0xFFFF;
                int lr, lg, lb;
                if (linear) {
                    lr = level(m00 * r + m01 * g + m02 * b);
                    lg = level(m10 * r + m11 * g + m12 * b);
                    lb = level(m20 * r + m21 * g + m22 * b);
                } else {
                    lr = (int) r >> 8;
                    lg = (int) g >> 8;
                    lb = (int) b >> 8;
                }
                hist.red[lr]++;
                hist.green[lg]++;
                hist.blue[lb]++;
                hist.luminance[Math.min(LEVELS - 1, Math.round(0.2126f * lr + 0.7152f * lg + 0.0722f * lb))]++;
                if (lr == 0 && lg == 0 && lb == 0) {
                    hist.shadowsClipped++;
                }
                if (lr == LEVELS - 1 || lg == LEVELS - 1 || lb == LEVELS - 1) {
                    hist.highlightsClipped++;
                }
                hist.samples++;
            }
        }
        return hist;
    }

    private static int level(float linearSrgb) {
        int q = (int) (linearSrgb + 0.5f);
        return TO_LEVEL[q < 0 ? 0 : (q > 65535 ? 65535 : q)] & 0xFF;
    }

    public int[] red() {
        return red.clone();
    }

    public int[] green() {
        return green.clone();
    }

    public int[] blue() {
        return blue.clone();
    }

    public int[] luminance() {
        return luminance.clone();
    }

    /** Number of counted pixels. */
    public int samples() {
        return samples;
    }

    /** Share of pixels that are black in all channels (0..1). */
    public double shadowsClipped() {
        return samples == 0 ? 0 : (double) shadowsClipped / samples;
    }

    /** Share of pixels with at least one channel at white (0..1). */
    public double highlightsClipped() {
        return samples == 0 ? 0 : (double) highlightsClipped / samples;
    }

    /**
     * Count that fills the height of the diagram: the largest bin without the
     * two end levels (black and white often hold a huge spike that would
     * flatten everything else).
     */
    public int scale() {
        int max = 0;
        for (int i = 1; i < LEVELS - 1; i++) {
            max = Math.max(max, Math.max(Math.max(red[i], green[i]), Math.max(blue[i], luminance[i])));
        }
        return max > 0 ? max : Math.max(1, Math.max(Math.max(red[0], red[LEVELS - 1]), luminance[0]));
    }
}
