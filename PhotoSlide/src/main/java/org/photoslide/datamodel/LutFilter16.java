package org.photoslide.datamodel;

/**
 * Per channel lookup table filter (65536 entries): exposure, white balance
 * gains, tone curves. Very fast because every pixel is one table access.
 */
public final class LutFilter16 implements Filter16 {

    private final String name;
    private final short[] lutR;
    private final short[] lutG;
    private final short[] lutB;

    private LutFilter16(String name, short[] r, short[] g, short[] b) {
        this.name = name;
        this.lutR = r;
        this.lutG = g;
        this.lutB = b;
    }

    /** Exposure in stops (+1 = twice as bright). Meant for LINEAR images. */
    public static LutFilter16 exposure(float stops) {
        short[] lut = build(v -> v * Math.pow(2.0, stops));
        return new LutFilter16("Exposure " + stops, lut, lut, lut);
    }

    /** Per channel gains, e.g. for white balance. Meant for LINEAR images. */
    public static LutFilter16 channelGains(float r, float g, float b) {
        return new LutFilter16("Gains " + r + "/" + g + "/" + b,
                build(v -> v * r), build(v -> v * g), build(v -> v * b));
    }

    /**
     * Tone curve through the given control points (x and y in 0..1, x
     * ascending, linear interpolation between the points).
     *
     * @param inSrgbSpace true = the image is linear and the curve is defined on
     * the sRGB encoded values (how users expect a curve to look); false = the
     * curve is applied directly to the stored values
     */
    public static LutFilter16 curve(float[] xs, float[] ys, boolean inSrgbSpace) {
        if (xs.length < 2 || xs.length != ys.length) {
            throw new IllegalArgumentException("Need at least two matching control points");
        }
        final float[] cx = xs.clone();
        final float[] cy = ys.clone();
        short[] lut = build(v -> {
            double in = inSrgbSpace ? Display16.srgbEncode(v) : v;
            double out = interpolate(cx, cy, in);
            return inSrgbSpace ? Display16.srgbDecode(out) : out;
        });
        return new LutFilter16("Curve", lut, lut, lut);
    }

    /**
     * Same mapping on all channels, defined on sRGB encoded values 0..1 (for
     * operations that were designed for 8 bit display values, e.g. gain/bias).
     * The image itself is expected to be LINEAR.
     */
    public static LutFilter16 encodedTone(String name, java.util.function.DoubleUnaryOperator f) {
        short[] lut = build(v -> Display16.srgbDecode(f.applyAsDouble(Display16.srgbEncode(v))));
        return new LutFilter16(name, lut, lut, lut);
    }

    private interface Mapping {

        double map(double v);
    }

    private static short[] build(Mapping m) {
        short[] lut = new short[65536];
        for (int i = 0; i < 65536; i++) {
            double out = m.map(i / 65535.0);
            long q = Math.round(out * 65535.0);
            lut[i] = (short) (q < 0 ? 0 : (q > 65535 ? 65535 : q));
        }
        return lut;
    }

    private static double interpolate(float[] xs, float[] ys, double x) {
        if (x <= xs[0]) {
            return ys[0];
        }
        for (int i = 1; i < xs.length; i++) {
            if (x <= xs[i]) {
                double t = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
                return ys[i - 1] + t * (ys[i] - ys[i - 1]);
            }
        }
        return ys[ys.length - 1];
    }

    @Override
    public void apply(Image16 src, Image16 dst, int yStart, int yEnd) {
        final short[] s = src.data();
        final short[] d = dst.data();
        int i = yStart * src.width() * Image16.CHANNELS;
        final int end = yEnd * src.width() * Image16.CHANNELS;
        for (; i < end; i += 3) {
            d[i] = lutR[s[i] & 0xFFFF];
            d[i + 1] = lutG[s[i + 1] & 0xFFFF];
            d[i + 2] = lutB[s[i + 2] & 0xFFFF];
        }
    }

    @Override
    public String name() {
        return name;
    }
}