package org.photoslide.imageops.lz;

import java.util.List;
import java.util.stream.IntStream;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WholeImageFilter16;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.BlendMode;
import org.photoslide.imageops.ParamSpec;
import org.photoslide.imageops.simd.Simd;

/**
 * Contrast mask: a blurred, inverted black and white copy laid over the
 * image (soft light, 50 %), which lightens shadows and darkens highlights
 * like the classic darkroom mask.
 *
 * Algorithm ported from LightZone (ContrastMaskOperation), Copyright (c)
 * 2005-2011 Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class ContrastMaskFilter extends AbstractImageFilter {

    public static final String NAME = "ContrastMaskFilter";

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("radius", "Radius", 0, 0.1f, 500f, 128f, 0.1f),
            ParamSpec.slider("gamma", "Gamma", 1, 0.2f, 5f, 2.2f, 0.1f));

    public ContrastMaskFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Contrast Mask";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected BlendMode defaultBlendMode() {
        return BlendMode.SOFT_LIGHT;
    }

    @Override
    protected float defaultOpacity() {
        return 0.5f;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        final double sigma = ctx.scaled(value(0));
        final double gamma = value(1);
        final float wr = ColorScience.WR, wg = ColorScience.WG, wb = ColorScience.WB;
        return (WholeImageFilter16) src -> {
            final int w = src.width();
            final int h = src.height();
            final short[] s = src.data();
            final float[] mask = new float[w * h];
            IntStream.range(0, h).parallel().forEach(row -> {
                for (int x = 0, o = row * w, i = o * 3; x < w; x++, o++, i += 3) {
                    float y = (wr * (s[i] & 0xFFFF) + wg * (s[i + 1] & 0xFFFF) + wb * (s[i + 2] & 0xFFFF)) / 65535f;
                    if (Simd.ENABLED) {
                        mask[o] = Math.max(0, 1 - y);       // the power follows per row in float vectors
                    } else {
                        mask[o] = (float) Math.pow(Math.max(0, 1 - y), gamma);
                    }
                }
                if (Simd.ENABLED) {
                    float[] r = new float[w];
                    System.arraycopy(mask, row * w, r, 0, w);
                    Simd.pow(r, (float) gamma, r, w);
                    System.arraycopy(r, 0, mask, row * w, w);
                }
            });
            final float[] blurred = Gauss.blur(mask, w, h, sigma);
            Image16 out = Image16.blank(w, h, true);
            final short[] d = out.data();
            IntStream.range(0, h).parallel().forEach(row -> {
                for (int x = 0, o = row * w, i = o * 3; x < w; x++, o++, i += 3) {
                    short v = Planes.to16(blurred[o]);
                    d[i] = v;
                    d[i + 1] = v;
                    d[i + 2] = v;
                }
            });
            return out;
        };
    }
}
