package org.photoslide.datamodel;

import java.util.stream.IntStream;

/**
 * Converts a 16 bit image to 8 bit ARGB pixels for display, with optional
 * ordered dithering against banding. No JavaFX dependency.
 *
 * Linear images are in the working space (linear ProPhoto, see WorkingSpace):
 * they are converted to linear sRGB by matrix (colours outside sRGB are
 * clipped), then encoded with the sRGB curve. Gamma encoded images are only
 * scaled (they are expected to have sRGB primaries already).
 */
public final class Display16 {

    /** linear 16 bit value -> sRGB encoded 0..255 (float, not yet rounded) */
    private static final float[] FROM_LINEAR = new float[65536];
    /** already encoded 16 bit value -> 0..255 */
    private static final float[] FROM_ENCODED = new float[65536];

    /** Standard 8x8 Bayer matrix. */
    private static final int[][] BAYER = {
        {0, 32, 8, 40, 2, 34, 10, 42},
        {48, 16, 56, 24, 50, 18, 58, 26},
        {12, 44, 4, 36, 14, 46, 6, 38},
        {60, 28, 52, 20, 62, 30, 54, 22},
        {3, 35, 11, 43, 1, 33, 9, 41},
        {51, 19, 59, 27, 49, 17, 57, 25},
        {15, 47, 7, 39, 13, 45, 5, 37},
        {63, 31, 55, 23, 61, 29, 53, 21}
    };
    private static final float[][] THRESHOLD = new float[8][8];

    static {
        for (int i = 0; i < 65536; i++) {
            FROM_LINEAR[i] = (float) (srgbEncode(i / 65535.0) * 255.0);
            FROM_ENCODED[i] = (float) (i * 255.0 / 65535.0);
        }
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                THRESHOLD[y][x] = (BAYER[y][x] + 0.5f) / 64f;
            }
        }
    }

    private Display16() {
    }

    public static double srgbEncode(double linear) {
        if (linear <= 0.0) {
            return 0.0;
        }
        if (linear >= 1.0) {
            return 1.0;
        }
        return linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1.0 / 2.4) - 0.055;
    }

    public static double srgbDecode(double encoded) {
        if (encoded <= 0.0) {
            return 0.0;
        }
        if (encoded >= 1.0) {
            return 1.0;
        }
        return encoded <= 0.04045 ? encoded / 12.92 : Math.pow((encoded + 0.055) / 1.055, 2.4);
    }

    /**
     * @param dither true = ordered dither (average error 0, no visible banding
     * in smooth gradients), false = plain rounding
     * @return width * height ARGB pixels (opaque), usable as INT_ARGB_PRE
     */
    public static int[] toArgb(Image16 img, boolean dither) {
        final int w = img.width();
        final int h = img.height();
        final short[] s = img.data();
        final int[] out = new int[w * h];
        if (!img.isLinear()) {
            IntStream.range(0, h).parallel().forEach(y -> {
                final float[] thr = THRESHOLD[y & 7];
                int i = y * w * Image16.CHANNELS;
                int o = y * w;
                for (int x = 0; x < w; x++, i += 3, o++) {
                    float t = dither ? thr[x & 7] : 0.5f;
                    out[o] = 0xFF000000 | (quantize(FROM_ENCODED[s[i] & 0xFFFF], t) << 16)
                            | (quantize(FROM_ENCODED[s[i + 1] & 0xFFFF], t) << 8)
                            | quantize(FROM_ENCODED[s[i + 2] & 0xFFFF], t);
                }
            });
            return out;
        }
        final double[][] m = WorkingSpace.WORKING_TO_SRGB;
        final float m00 = (float) m[0][0], m01 = (float) m[0][1], m02 = (float) m[0][2];
        final float m10 = (float) m[1][0], m11 = (float) m[1][1], m12 = (float) m[1][2];
        final float m20 = (float) m[2][0], m21 = (float) m[2][1], m22 = (float) m[2][2];
        IntStream.range(0, h).parallel().forEach(y -> {
            final float[] thr = THRESHOLD[y & 7];
            int i = y * w * Image16.CHANNELS;
            int o = y * w;
            for (int x = 0; x < w; x++, i += 3, o++) {
                float t = dither ? thr[x & 7] : 0.5f;
                float r = s[i] & 0xFFFF;
                float g = s[i + 1] & 0xFFFF;
                float b = s[i + 2] & 0xFFFF;
                int sr = toIndex(m00 * r + m01 * g + m02 * b);
                int sg = toIndex(m10 * r + m11 * g + m12 * b);
                int sb = toIndex(m20 * r + m21 * g + m22 * b);
                out[o] = 0xFF000000 | (quantize(FROM_LINEAR[sr], t) << 16)
                        | (quantize(FROM_LINEAR[sg], t) << 8) | quantize(FROM_LINEAR[sb], t);
            }
        });
        return out;
    }

    private static int toIndex(float v) {
        int q = (int) (v + 0.5f);
        return q < 0 ? 0 : (q > 65535 ? 65535 : q);
    }

    private static int quantize(float v, float threshold) {
        int q = (int) (v + threshold);
        return q > 255 ? 255 : q;
    }
}