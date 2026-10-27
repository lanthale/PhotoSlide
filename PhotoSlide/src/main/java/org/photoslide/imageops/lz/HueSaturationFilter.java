package org.photoslide.imageops.lz;

import java.util.List;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WorkingSpace;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;

/**
 * Hue, saturation, vibrance (saturation mainly of muted colours, protecting
 * skin tones) and luminosity.
 *
 * Algorithm ported from LightZone (HueSaturationOperation V3, ColorMatrix2,
 * IntVibranceOpImage, HueRotateOpImage), Copyright (c) 2005-2011 Fabio
 * Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class HueSaturationFilter extends AbstractImageFilter {

    public static final String NAME = "HueSaturationFilter";

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("hue", "Hue", 0, -180f, 180f, 0f, 1f),
            ParamSpec.slider("saturation", "Saturation", 1, -100f, 100f, 0f, 1f),
            ParamSpec.slider("vibrance", "Vibrance", 2, -100f, 100f, 0f, 1f),
            ParamSpec.slider("luminosity", "Luminosity", 3, -100f, 100f, 0f, 1f));

    public HueSaturationFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Hue / Saturation";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        if (isDefault()) {
            return null;
        }
        final float hue = value(0) / 360f;
        final float sat = value(1) / 100f + 1;
        final float vib = value(2) / 100f + 1;
        final float lum = value(3) / 100f + 1;
        final boolean vibranceIncrease = vib > 1;
        final float wr = ColorScience.WR, wg = ColorScience.WG, wb = ColorScience.WB;
        final float[] toS = flat(WorkingSpace.WORKING_TO_SRGB);
        final float[] fromS = flat(WorkingSpace.SRGB_TO_WORKING);
        return new Filter16() {
            @Override
            public void apply(Image16 src, Image16 dst, int yStart, int yEnd) {
                final short[] s = src.data();
                final short[] d = dst.data();
                final float k = 1f / 65535f;
                final float[] hsb = new float[3];
                final float[] rgb = new float[3];
                final int end = yEnd * src.width() * 3;
                for (int i = yStart * src.width() * 3; i < end; i += 3) {
                    float r = (s[i] & 0xFFFF) * k;
                    float g = (s[i + 1] & 0xFFFF) * k;
                    float b = (s[i + 2] & 0xFFFF) * k;
                    // saturation and luminosity (one matrix in LightZone)
                    if (sat != 1) {
                        float y = wr * r + wg * g + wb * b;
                        r = y + sat * (r - y);
                        g = y + sat * (g - y);
                        b = y + sat * (b - y);
                    }
                    r *= lum;
                    g *= lum;
                    b *= lum;
                    if (vib != 1) {
                        float mask = vibranceMask(r, g, b, toS, vibranceIncrease);
                        float y = wr * r + wg * g + wb * b;
                        r += mask * (y + vib * (r - y) - r);
                        g += mask * (y + vib * (g - y) - g);
                        b += mask * (y + vib * (b - y) - b);
                    }
                    if (hue != 0) {
                        rgb[0] = Math.max(0, toS[0] * r + toS[1] * g + toS[2] * b);
                        rgb[1] = Math.max(0, toS[3] * r + toS[4] * g + toS[5] * b);
                        rgb[2] = Math.max(0, toS[6] * r + toS[7] * g + toS[8] * b);
                        Hsb.fromRGB(rgb, hsb);
                        hsb[0] += hue;
                        if (hsb[0] < 0) {
                            hsb[0] += 1;
                        } else if (hsb[0] >= 1) {
                            hsb[0] -= 1;
                        }
                        Hsb.toRGB(hsb, rgb);
                        r = fromS[0] * rgb[0] + fromS[1] * rgb[1] + fromS[2] * rgb[2];
                        g = fromS[3] * rgb[0] + fromS[4] * rgb[1] + fromS[5] * rgb[2];
                        b = fromS[6] * rgb[0] + fromS[7] * rgb[1] + fromS[8] * rgb[2];
                    }
                    d[i] = Planes.to16(r);
                    d[i + 1] = Planes.to16(g);
                    d[i + 2] = Planes.to16(b);
                }
            }

            @Override
            public String name() {
                return "Hue/Saturation";
            }
        };
    }

    /**
     * Weight of the vibrance change: strongest around the hue sixth centred at
     * pi/6 of each third of the hue circle (in sRGB), and for an increase
     * reduced for already saturated colours.
     */
    static float vibranceMask(float r, float g, float b, float[] toS, boolean increase) {
        float lr = toS[0] * r + toS[1] * g + toS[2] * b;
        float lg = toS[3] * r + toS[4] * g + toS[5] * b;
        float lb = toS[6] * r + toS[7] * g + toS[8] * b;
        double x = lr - (lg + lb) / 2;
        double y = Math.sqrt(3) / 2 * (lg - lb);
        double h = Math.atan2(x, y) + Math.PI;
        if (h < 0) {
            h += 2 * Math.PI;
        }
        if (h > 4 * Math.PI / 3) {
            h -= 4 * Math.PI / 3;
        } else if (h > 2 * Math.PI / 3) {
            h -= 2 * Math.PI / 3;
        }
        float mask = (float) (0.5 + (1 - Math.abs(Math.PI / 6 - h) / (Math.PI / 3)) / 2);
        if (increase) {
            float min = Math.min(r, Math.min(g, b));
            float max = Math.max(r, Math.max(g, b));
            float s = max > 0 ? 1 - min / max : 0;
            mask *= 1 - s * s;
        }
        return mask;
    }

    static float[] flat(double[][] m) {
        return new float[]{(float) m[0][0], (float) m[0][1], (float) m[0][2],
            (float) m[1][0], (float) m[1][1], (float) m[1][2],
            (float) m[2][0], (float) m[2][1], (float) m[2][2]};
    }
}
