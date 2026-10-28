package org.photoslide.imageops;

import java.util.stream.IntStream;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.FilterChain16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.WholeImageFilter16;

/**
 * Applies a tool as a layer: front = tool(back), then
 * out = back + opacity * mask * (mode.blend(front, back) - back).
 *
 * Algorithm ported from LightZone (BlendOpImage / blendLoop), Copyright (c)
 * 2005-2011 Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public final class BlendedFilter16 implements WholeImageFilter16 {

    /** Per pixel weight of a tool, computed from the image below it. */
    @FunctionalInterface
    public interface MaskSource {

        /** @return weights 0..1 (width * height of back) */
        float[] mask(Image16 back);
    }

    private final Filter16 inner;
    private final BlendMode mode;
    private final float opacity;
    private final MaskSource maskSource;

    private BlendedFilter16(Filter16 inner, BlendMode mode, float opacity, MaskSource mask) {
        this.inner = inner;
        this.mode = mode;
        this.opacity = opacity;
        this.maskSource = mask;
    }

    /**
     * @param mask region / colour selection weights, or null for the whole
     * image
     * @return the filter itself if nothing has to be blended
     */
    public static Filter16 wrap(Filter16 inner, BlendMode mode, float opacity, MaskSource mask) {
        if (inner == null) {
            return null;
        }
        if (mode == BlendMode.NORMAL && opacity >= 1f && mask == null) {
            return inner;
        }
        return new BlendedFilter16(inner, mode, Math.max(0f, Math.min(1f, opacity)), mask);
    }

    @Override
    public Image16 process(Image16 back) {
        final Image16 front;
        if (inner instanceof WholeImageFilter16 whole) {
            front = whole.process(back);
        } else {
            front = Image16.blank(back.width(), back.height(), back.isLinear());
            FilterChain16.run(inner, back, front);
        }
        final float[] mask = maskSource == null ? null : maskSource.mask(back);
        // the result replaces the front values in place: front belongs to this call
        final short[] f = front.data();
        final short[] b = back.data();
        final int w = back.width();
        final float op = opacity;
        IntStream.range(0, back.height()).parallel().forEach(y -> {
            // the constant mode lets the JIT inline blend() into a loop per mode
            switch (mode) {
                case NORMAL -> row(BlendMode.NORMAL, f, b, mask, op, y, w);
                case AVERAGE -> row(BlendMode.AVERAGE, f, b, mask, op, y, w);
                case MULTIPLY -> row(BlendMode.MULTIPLY, f, b, mask, op, y, w);
                case SCREEN -> row(BlendMode.SCREEN, f, b, mask, op, y, w);
                case DARKEN -> row(BlendMode.DARKEN, f, b, mask, op, y, w);
                case LIGHTEN -> row(BlendMode.LIGHTEN, f, b, mask, op, y, w);
                case DIFFERENCE -> row(BlendMode.DIFFERENCE, f, b, mask, op, y, w);
                case NEGATION -> row(BlendMode.NEGATION, f, b, mask, op, y, w);
                case EXCLUSION -> row(BlendMode.EXCLUSION, f, b, mask, op, y, w);
                case OVERLAY -> row(BlendMode.OVERLAY, f, b, mask, op, y, w);
                case HARD_LIGHT -> row(BlendMode.HARD_LIGHT, f, b, mask, op, y, w);
                case SOFT_LIGHT -> row(BlendMode.SOFT_LIGHT, f, b, mask, op, y, w);
                case COLOR_DODGE -> row(BlendMode.COLOR_DODGE, f, b, mask, op, y, w);
                case COLOR_BURN -> row(BlendMode.COLOR_BURN, f, b, mask, op, y, w);
                case SOFT_DODGE -> row(BlendMode.SOFT_DODGE, f, b, mask, op, y, w);
                case SOFT_BURN -> row(BlendMode.SOFT_BURN, f, b, mask, op, y, w);
                case SHADOWS -> row(BlendMode.SHADOWS, f, b, mask, op, y, w);
                case MID_HILIGHTS -> row(BlendMode.MID_HILIGHTS, f, b, mask, op, y, w);
                case MIDTONES -> row(BlendMode.MIDTONES, f, b, mask, op, y, w);
            }
        });
        return front;
    }

    /** Blends row y: f = back + weight * (mode(f, back) - back). */
    private static void row(BlendMode mode, short[] f, short[] b, float[] mask, float opacity, int y, int w) {
        final float scale = 1f / 65535f;
        int i = y * w * Image16.CHANNELS;
        for (int x = 0; x < w; x++) {
            float op = mask == null ? opacity : opacity * mask[y * w + x];
            for (int c = 0; c < 3; c++, i++) {
                if (op <= 0f) {
                    f[i] = b[i];
                    continue;
                }
                float bv = (b[i] & 0xFFFF) * scale;
                float fv = (f[i] & 0xFFFF) * scale;
                float v = bv + op * (mode.blend(fv, bv) - bv);
                int q = Math.round(v * 65535f);
                f[i] = (short) (q < 0 ? 0 : (q > 65535 ? 65535 : q));
            }
        }
    }

    @Override
    public String name() {
        return inner.name() + " (" + mode.label() + ", " + Math.round(opacity * 100) + " %)";
    }
}
