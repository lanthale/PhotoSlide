package org.photoslide.imageops.lz;

import java.util.List;
import org.photoslide.datamodel.Display16;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WorkingSpace;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;

/**
 * Black and white conversion with a colour filter, like a filter in front of
 * the lens: a red filter darkens blue sky, green lightens foliage. White =
 * plain luminance.
 *
 * Algorithm ported from LightZone (ChannelMixerV2, V4), Copyright (c)
 * 2005-2011 Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class BlackWhiteFilter extends AbstractImageFilter {

    public static final String NAME = "BlackWhiteFilter";

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.color("filter", "Filter colour", 0, 1f, 1f, 1f),
            ParamSpec.slider("strength", "Strength", 3, 0f, 10f, 1f, 0.5f));

    public BlackWhiteFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Black and White";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        double[] f = WorkingSpace.multiply(WorkingSpace.SRGB_TO_WORKING, new double[]{
            Display16.srgbDecode(value(0)), Display16.srgbDecode(value(1)), Display16.srgbDecode(value(2))});
        double strength = value(3);
        double red = 1 - f[0], green = 1 - f[1], blue = 1 - f[2];
        double tr = ColorScience.WR, tg = ColorScience.WG, tb = ColorScience.WB;
        if (red != 0) {
            tr -= strength * red / 2;
            tg += strength * red / 4;
            tb += strength * red / 4;
        }
        if (blue != 0) {
            tb -= strength * blue / 2;
            tg += strength * blue / 4;
            tr += strength * blue / 4;
        }
        if (green != 0) {
            tg -= strength * green / 2;
            tb += strength * green / 4;
            tr += strength * green / 4;
        }
        final float wr = (float) tr, wg = (float) tg, wb = (float) tb;
        return new Filter16() {
            @Override
            public void apply(Image16 src, Image16 dst, int yStart, int yEnd) {
                final short[] s = src.data();
                final short[] d = dst.data();
                final int end = yEnd * src.width() * 3;
                for (int i = yStart * src.width() * 3; i < end; i += 3) {
                    float v = wr * (s[i] & 0xFFFF) + wg * (s[i + 1] & 0xFFFF) + wb * (s[i + 2] & 0xFFFF);
                    int q = (int) (v + 0.5f);
                    short gray = (short) (q < 0 ? 0 : (q > 65535 ? 65535 : q));
                    d[i] = gray;
                    d[i + 1] = gray;
                    d[i + 2] = gray;
                }
            }

            @Override
            public String name() {
                return "Black and White";
            }
        };
    }
}
