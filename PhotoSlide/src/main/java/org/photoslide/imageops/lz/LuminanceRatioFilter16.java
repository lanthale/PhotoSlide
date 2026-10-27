package org.photoslide.imageops.lz;

import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.WorkingSpace;

/**
 * Tone change that keeps the hue: the luminance Y of each pixel selects a
 * factor from a table, all three channels are multiplied by it. Channels that
 * end up above white are scaled down together (hue preserving clip).
 *
 * Same idea as LightZone's LightnessLookupTable, Copyright (c) 2005-2011
 * Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public final class LuminanceRatioFilter16 implements Filter16 {

    private static final float WR = (float) WorkingSpace.WR;
    private static final float WG = (float) WorkingSpace.WG;
    private static final float WB = (float) WorkingSpace.WB;

    private final String name;
    private final float[] ratio;

    /**
     * @param ratio 65536 factors, index = Y * 65535 (linear luminance)
     */
    public LuminanceRatioFilter16(String name, float[] ratio) {
        if (ratio.length != 65536) {
            throw new IllegalArgumentException("ratio table needs 65536 entries");
        }
        this.name = name;
        this.ratio = ratio;
    }

    /**
     * Builds the table from a tone curve on linear luminance.
     *
     * @param curve maps Y (0..1) to the new Y (0..1, may exceed 1)
     */
    public static float[] ratiosFor(java.util.function.DoubleUnaryOperator curve) {
        float[] r = new float[65536];
        for (int i = 1; i < 65536; i++) {
            double y = i / 65535.0;
            r[i] = (float) (curve.applyAsDouble(y) / y);
        }
        r[0] = r[1];
        return r;
    }

    @Override
    public void apply(Image16 src, Image16 dst, int yStart, int yEnd) {
        final short[] s = src.data();
        final short[] d = dst.data();
        final int end = yEnd * src.width() * Image16.CHANNELS;
        for (int i = yStart * src.width() * Image16.CHANNELS; i < end; i += 3) {
            float r = s[i] & 0xFFFF;
            float g = s[i + 1] & 0xFFFF;
            float b = s[i + 2] & 0xFFFF;
            int yi = (int) (WR * r + WG * g + WB * b + 0.5f);
            float k = ratio[yi < 0 ? 0 : (yi > 65535 ? 65535 : yi)];
            r *= k;
            g *= k;
            b *= k;
            float max = Math.max(r, Math.max(g, b));
            if (max > 65535f) {
                float c = 65535f / max;
                r *= c;
                g *= c;
                b *= c;
            }
            d[i] = (short) (int) (r + 0.5f);
            d[i + 1] = (short) (int) (g + 0.5f);
            d[i + 2] = (short) (int) (b + 0.5f);
        }
    }

    @Override
    public String name() {
        return name;
    }
}
