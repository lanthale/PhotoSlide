package org.photoslide.imageops.mask;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.photoslide.datamodel.WorkingSpace;
import org.photoslide.imageops.lz.Hsb;

/**
 * Restricts a tool to colours of a hue range and/or a brightness range, each
 * with a soft transition (feather). Hue is measured on sRGB (0..1 around the
 * colour circle), brightness on LightZone's zone scale (0 = black, 1 =
 * white, 8 stops).
 *
 * Algorithm ported from LightZone (ColorSelection, blendLoop in
 * blendFunctions.cpp), Copyright (c) 2005-2011 Fabio Riccardi,
 * BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 *
 * @param hueEnabled restrict by hue
 * @param hueCenter centre of the hue range (0..1)
 * @param hueRange half width of the fully selected hue range
 * @param hueFeather width of the soft transition on each side
 * @param lumEnabled restrict by brightness
 * @param lumLower lower end of the fully selected brightness range
 * @param lumUpper upper end
 * @param lumFeather width of the soft transition on each side
 * @param inverted select everything else
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ColorSelection(boolean hueEnabled, float hueCenter, float hueRange, float hueFeather,
        boolean lumEnabled, float lumLower, float lumUpper, float lumFeather, boolean inverted) {

    public static final ColorSelection NONE = new ColorSelection(false, 0, 0.05f, 0.05f, false, 0, 1, 0.1f, false);

    public boolean isActive() {
        return hueEnabled || lumEnabled;
    }

    /** Selection around a picked colour (linear working space). */
    public static ColorSelection around(double[] rgb) {
        float[] hsb = hsb(rgb);
        float lum = zone(rgb);
        return new ColorSelection(true, hsb[0], 0.04f, 0.04f, false,
                Math.max(0, lum - 0.15f), Math.min(1, lum + 0.15f), 0.1f, false);
    }

    private static final float[] TO_SRGB = flat(WorkingSpace.WORKING_TO_SRGB);
    private static final float WR = (float) WorkingSpace.WR;
    private static final float WG = (float) WorkingSpace.WG;
    private static final float WB = (float) WorkingSpace.WB;
    private static final float LOG2 = (float) Math.log(2);

    /** @return weight 0..1 of a linear working space colour (no allocation, per pixel) */
    public float weight(float r, float g, float b) {
        float w = 1;
        if (hueEnabled) {
            float sr = Math.max(0, TO_SRGB[0] * r + TO_SRGB[1] * g + TO_SRGB[2] * b);
            float sg = Math.max(0, TO_SRGB[3] * r + TO_SRGB[4] * g + TO_SRGB[5] * b);
            float sb = Math.max(0, TO_SRGB[6] * r + TO_SRGB[7] * g + TO_SRGB[8] * b);
            float d = Math.abs(hue(sr, sg, sb) - hueCenter);
            d = Math.min(d, 1 - d);                          // distance around the circle
            w *= ramp(d, hueRange, hueFeather);
        }
        if (lumEnabled && w > 0) {
            float y = Math.max(0, WR * r + WG * g + WB * b);
            float l = (float) Math.log(255 * y + 1) / LOG2 / 8;
            float wl;
            if (l >= lumLower && l <= lumUpper) {
                wl = 1;
            } else if (l < lumLower) {
                wl = lumFeather > 0 ? Math.max(0, 1 - (lumLower - l) / lumFeather) : 0;
            } else {
                wl = lumFeather > 0 ? Math.max(0, 1 - (l - lumUpper) / lumFeather) : 0;
            }
            w *= wl;
        }
        return inverted ? 1 - w : w;
    }

    /** HSB hue (0..1) as in LightZone's HSB.fromRGB. */
    private static float hue(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        if (max <= 0 || max == min) {
            return 0;
        }
        float d = max - min;
        float h;
        if (r == max) {
            h = ((max - b) - (max - g)) / d;
        } else if (g == max) {
            h = 2 + ((max - r) - (max - b)) / d;
        } else {
            h = 4 + ((max - g) - (max - r)) / d;
        }
        h /= 6;
        return h < 0 ? h + 1 : h;
    }

    private static float[] flat(double[][] m) {
        return new float[]{(float) m[0][0], (float) m[0][1], (float) m[0][2],
            (float) m[1][0], (float) m[1][1], (float) m[1][2],
            (float) m[2][0], (float) m[2][1], (float) m[2][2]};
    }

    private static float ramp(float d, float range, float feather) {
        if (d <= range) {
            return 1;
        }
        return feather > 0 ? Math.max(0, 1 - (d - range) / feather) : 0;
    }

    private static float[] hsb(double[] rgb) {
        double[] s = WorkingSpace.multiply(WorkingSpace.WORKING_TO_SRGB, rgb);
        float[] hsb = new float[3];
        Hsb.fromRGB(new float[]{(float) Math.max(0, s[0]), (float) Math.max(0, s[1]), (float) Math.max(0, s[2])}, hsb);
        return hsb;
    }

    private static float zone(double[] rgb) {
        double y = Math.max(0, WorkingSpace.luminance(rgb[0], rgb[1], rgb[2]));
        return (float) (Math.log(255 * y + 1) / Math.log(2) / 8);
    }
}
