package org.photoslide.imageops.lz;

import java.util.stream.IntStream;
import org.photoslide.datamodel.Image16;

/**
 * Conversion between Image16 (interleaved 16 bit) and planar float data
 * (one array per channel, values 0..1), which neighbourhood filters use.
 */
public final class Planes {

    private Planes() {
    }

    /** @return [3][width * height] R, G, B as 0..1 */
    public static float[][] split(Image16 img) {
        final int n = img.width() * img.height();
        final float[][] p = new float[3][n];
        final short[] s = img.data();
        final float k = 1f / 65535f;
        IntStream.range(0, img.height()).parallel().forEach(y -> {
            int o = y * img.width();
            int i = o * 3;
            for (int x = 0; x < img.width(); x++, o++, i += 3) {
                p[0][o] = (s[i] & 0xFFFF) * k;
                p[1][o] = (s[i + 1] & 0xFFFF) * k;
                p[2][o] = (s[i + 2] & 0xFFFF) * k;
            }
        });
        return p;
    }

    /** @return a new image from three planes 0..1 (values are clamped) */
    public static Image16 merge(float[][] p, int width, int height) {
        Image16 out = Image16.blank(width, height, true);
        final short[] d = out.data();
        IntStream.range(0, height).parallel().forEach(y -> {
            int o = y * width;
            int i = o * 3;
            for (int x = 0; x < width; x++, o++, i += 3) {
                d[i] = to16(p[0][o]);
                d[i + 1] = to16(p[1][o]);
                d[i + 2] = to16(p[2][o]);
            }
        });
        return out;
    }

    public static short to16(float v) {
        int q = (int) (v * 65535f + 0.5f);
        return (short) (q < 0 ? 0 : (q > 65535 ? 65535 : q));
    }
}
