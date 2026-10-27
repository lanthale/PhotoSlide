package org.photoslide.datamodel;

import java.util.stream.IntStream;
import org.librawjava.RawPixels16;

/**
 * Working copy of an image with 16 bit per channel: interleaved R, G, B,
 * unsigned values 0..65535 stored in a short[] (read with {@code & 0xFFFF}).
 *
 * "linear" says whether the values are linear light (right for exposure and
 * white balance) or gamma encoded. The class has no JavaFX dependency.
 */
public final class Image16 {

    public static final int CHANNELS = 3;

    private final int width;
    private final int height;
    private final short[] data;
    private final boolean linear;

    public Image16(int width, int height, short[] data, boolean linear) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid size " + width + "x" + height);
        }
        if (data == null || data.length != (long) width * height * CHANNELS) {
            throw new IllegalArgumentException("data length does not match width * height * 3");
        }
        this.width = width;
        this.height = height;
        this.data = data;
        this.linear = linear;
    }

    /** Takes over the array of the decoder result without copying it. */
    public static Image16 fromRaw(RawPixels16 raw) {
        if (raw.channels() != CHANNELS) {
            throw new IllegalArgumentException("Only 3 channel images are supported");
        }
        return new Image16(raw.width(), raw.height(), raw.data(), raw.linear());
    }

    public static Image16 blank(int width, int height, boolean linear) {
        return new Image16(width, height, new short[Math.multiplyExact(Math.multiplyExact(width, height), CHANNELS)], linear);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean isLinear() {
        return linear;
    }

    /** The backing array (not a copy). Filters write into it row by row. */
    public short[] data() {
        return data;
    }

    public Image16 copy() {
        return new Image16(width, height, data.clone(), linear);
    }

    /** Copy of a rectangle (clipped to the image). */
    public Image16 crop(int x, int y, int w, int h) {
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(width, x + w);
        int y1 = Math.min(height, y + h);
        if (x1 <= x0 || y1 <= y0) {
            throw new IllegalArgumentException("Rectangle outside the image");
        }
        Image16 out = blank(x1 - x0, y1 - y0, linear);
        for (int row = y0; row < y1; row++) {
            System.arraycopy(data, (row * width + x0) * CHANNELS, out.data, (row - y0) * out.width * CHANNELS,
                    (x1 - x0) * CHANNELS);
        }
        return out;
    }

    /**
     * Area-averaging scale, meant for preview proxies. Upscaling is nearest
     * neighbour. Returns this if the size is unchanged.
     */
    public Image16 scaledTo(int dstW, int dstH) {
        if (dstW <= 0 || dstH <= 0) {
            throw new IllegalArgumentException("Invalid target size");
        }
        if (dstW == width && dstH == height) {
            return this;
        }
        final short[] out = new short[Math.multiplyExact(Math.multiplyExact(dstW, dstH), CHANNELS)];
        final int[] x0 = new int[dstW];
        final int[] x1 = new int[dstW];
        for (int x = 0; x < dstW; x++) {
            x0[x] = (int) ((long) x * width / dstW);
            x1[x] = Math.max(x0[x] + 1, (int) ((long) (x + 1) * width / dstW));
        }
        IntStream.range(0, dstH).parallel().forEach(y -> {
            int ya = (int) ((long) y * height / dstH);
            int yb = Math.max(ya + 1, (int) ((long) (y + 1) * height / dstH));
            for (int x = 0; x < dstW; x++) {
                long r = 0;
                long g = 0;
                long b = 0;
                int xa = x0[x];
                int xb = x1[x];
                for (int sy = ya; sy < yb; sy++) {
                    int i = (sy * width + xa) * CHANNELS;
                    for (int sx = xa; sx < xb; sx++, i += CHANNELS) {
                        r += data[i] & 0xFFFF;
                        g += data[i + 1] & 0xFFFF;
                        b += data[i + 2] & 0xFFFF;
                    }
                }
                long n = (long) (yb - ya) * (xb - xa);
                int o = (y * dstW + x) * CHANNELS;
                out[o] = (short) (r / n);
                out[o + 1] = (short) (g / n);
                out[o + 2] = (short) (b / n);
            }
        });
        return new Image16(dstW, dstH, out, linear);
    }
}