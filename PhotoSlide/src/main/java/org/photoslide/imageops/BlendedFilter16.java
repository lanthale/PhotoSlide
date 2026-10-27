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
        final Image16 out = Image16.blank(back.width(), back.height(), back.isLinear());
        final short[] f = front.data();
        final short[] b = back.data();
        final short[] o = out.data();
        final int w = back.width();
        final float scale = 1f / 65535f;
        IntStream.range(0, back.height()).parallel().forEach(y -> {
            int i = y * w * Image16.CHANNELS;
            for (int x = 0; x < w; x++) {
                float op = mask == null ? opacity : opacity * mask[y * w + x];
                for (int c = 0; c < 3; c++, i++) {
                    float bv = (b[i] & 0xFFFF) * scale;
                    if (op <= 0f) {
                        o[i] = b[i];
                        continue;
                    }
                    float fv = (f[i] & 0xFFFF) * scale;
                    float v = bv + op * (mode.blend(fv, bv) - bv);
                    int q = Math.round(v * 65535f);
                    o[i] = (short) (q < 0 ? 0 : (q > 65535 ? 65535 : q));
                }
            }
        });
        return out;
    }

    @Override
    public String name() {
        return inner.name() + " (" + mode.label() + ", " + Math.round(opacity * 100) + " %)";
    }
}
