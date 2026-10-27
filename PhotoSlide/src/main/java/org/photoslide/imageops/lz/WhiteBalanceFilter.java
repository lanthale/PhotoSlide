package org.photoslide.imageops.lz;

import java.util.List;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;
import org.photoslide.imageops.Pickable;

/**
 * White balance by colour temperature (chromatic adaptation, 5000 K = no
 * change) and tint (green/magenta). The dropper searches the temperature
 * that makes the picked colour neutral.
 *
 * Algorithm ported from LightZone (WhiteBalanceV2), Copyright (c)
 * 2005-2011 Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class WhiteBalanceFilter extends AbstractImageFilter implements Pickable {

    public static final String NAME = "WhiteBalanceFilter";

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("temperature", "Temperature (K)", 0, 1000f, 40000f, ColorScience.REF_T, 10f),
            ParamSpec.slider("tint", "Tint", 1, -20f, 20f, 0f, 0.1f));

    public WhiteBalanceFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "White Balance";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        final float t = value(0);
        final float tint = value(1);
        final boolean matrix = t != ColorScience.REF_T;
        if (!matrix && tint == 0) {
            return null;
        }
        final float[] m = HueSaturationFilter.flat(matrix ? ColorScience.whiteBalanceMatrix(t)
                : new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}});
        final short[][] tintLut = tint != 0
                ? BumpCurves.tables(0.18, new double[]{-tint / 4, tint / 2, -tint / 4}) : null;
        return new Filter16() {
            @Override
            public void apply(Image16 src, Image16 dst, int yStart, int yEnd) {
                final short[] s = src.data();
                final short[] d = dst.data();
                final int end = yEnd * src.width() * 3;
                for (int i = yStart * src.width() * 3; i < end; i += 3) {
                    float r = s[i] & 0xFFFF;
                    float g = s[i + 1] & 0xFFFF;
                    float b = s[i + 2] & 0xFFFF;
                    int nr = clamp(m[0] * r + m[1] * g + m[2] * b);
                    int ng = clamp(m[3] * r + m[4] * g + m[5] * b);
                    int nb = clamp(m[6] * r + m[7] * g + m[8] * b);
                    if (tintLut != null) {
                        nr = tintLut[0][nr] & 0xFFFF;
                        ng = tintLut[1][ng] & 0xFFFF;
                        nb = tintLut[2][nb] & 0xFFFF;
                    }
                    d[i] = (short) nr;
                    d[i + 1] = (short) ng;
                    d[i + 2] = (short) nb;
                }
            }

            @Override
            public String name() {
                return "White Balance " + t + "K " + tint;
            }
        };
    }

    private static int clamp(float v) {
        int q = (int) (v + 0.5f);
        return q < 0 ? 0 : (q > 65535 ? 65535 : q);
    }

    @Override
    public String pickLabel() {
        return "Pick something that should be neutral grey";
    }

    @Override
    public void pick(double[] rgb) {
        float[] wb = ColorScience.neutralize(rgb);
        setValue(0, Math.round(wb[0] / 10) * 10f);
        setValue(1, Math.round(wb[1] * 10) / 10f);
    }
}
