package org.photoslide.imageops.lz;

/**
 * RGB <-> HSB (hue 0..1, saturation 0..1, brightness = max channel).
 *
 * Ported from LightZone (com.lightcrafts.image.color.HSB), Copyright (c)
 * 2005-2011 Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public final class Hsb {

    private Hsb() {
    }

    public static void fromRGB(float[] rgb, float[] hsb) {
        float cmax = Math.max(rgb[0], Math.max(rgb[1], rgb[2]));
        float cmin = Math.min(rgb[0], Math.min(rgb[1], rgb[2]));
        float saturation = cmax != 0 ? (cmax - cmin) / cmax : 0;
        float hue = 0;
        if (saturation != 0) {
            float redc = (cmax - rgb[0]) / (cmax - cmin);
            float greenc = (cmax - rgb[1]) / (cmax - cmin);
            float bluec = (cmax - rgb[2]) / (cmax - cmin);
            if (rgb[0] == cmax) {
                hue = bluec - greenc;
            } else if (rgb[1] == cmax) {
                hue = 2.0f + redc - bluec;
            } else {
                hue = 4.0f + greenc - redc;
            }
            hue /= 6.0f;
            if (hue < 0) {
                hue += 1.0f;
            }
        }
        hsb[0] = hue;
        hsb[1] = saturation;
        hsb[2] = cmax;
    }

    public static void toRGB(float[] hsb, float[] rgb) {
        float h = hsb[0], s = hsb[1], v = hsb[2];
        if (v == 0) {
            rgb[0] = rgb[1] = rgb[2] = 0;
            return;
        }
        if (s == 0) {
            rgb[0] = rgb[1] = rgb[2] = v;
            return;
        }
        float low = v * (1 - s);
        if (h < 1.0 / 6) {
            rgb[0] = v;
            rgb[2] = low;
            rgb[1] = low + (v - low) * h * 6;
        } else if (h < 2.0 / 6) {
            rgb[1] = v;
            rgb[2] = low;
            rgb[0] = v - (v - low) * (h - 1f / 6) * 6;
        } else if (h < 3.0 / 6) {
            rgb[1] = v;
            rgb[0] = low;
            rgb[2] = low + (v - low) * (h - 2f / 6) * 6;
        } else if (h < 4.0 / 6) {
            rgb[2] = v;
            rgb[0] = low;
            rgb[1] = v - (v - low) * (h - 3f / 6) * 6;
        } else if (h < 5.0 / 6) {
            rgb[2] = v;
            rgb[1] = low;
            rgb[0] = low + (v - low) * (h - 4f / 6) * 6;
        } else {
            rgb[0] = v;
            rgb[1] = low;
            rgb[2] = v - (v - low) * (h - 5f / 6) * 6;
        }
    }
}
