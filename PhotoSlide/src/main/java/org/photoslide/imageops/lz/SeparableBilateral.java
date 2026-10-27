package org.photoslide.imageops.lz;

import java.util.stream.IntStream;

/**
 * Separable (row then column) bilateral filter on float planes, for one
 * plane (luminance) or two planes filtered jointly (the chroma planes S and
 * T). Edge pixels use the nearest pixel inside the image.
 *
 * Ported from LightZone (jnisrc/fast_jai/bilateralFilterRGB.cpp,
 * BilateralFilterRGBOpImage), Copyright (c) 2005-2011 Fabio Riccardi,
 * BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public final class SeparableBilateral {

    private static final int RANGE_TABLE = 4096;
    /** exp(-16) is treated as 0, like LightZone's fast_exp */
    private static final float RANGE_LIMIT = 16f;

    private SeparableBilateral() {
    }

    /**
     * Filters one or two planes in place.
     *
     * @param a first plane
     * @param b second plane filtered together with a (range distance over
     * both), or null
     * @param sigmaD spatial sigma in pixels
     * @param sigmaR range sigma (values are 0..1)
     */
    public static void filter(float[] a, float[] b, int w, int h, double sigmaD, double sigmaR) {
        if (sigmaD < 0.01 || sigmaR <= 0) {
            return;
        }
        final int wr = (int) Math.ceil(sigmaD);
        final float[] spatial = new float[2 * wr + 1];
        for (int i = -wr; i <= wr; i++) {
            spatial[i + wr] = (float) Math.exp(-(i * i) / (2 * sigmaD * sigmaD));
        }
        // range weight exp(-d2 / (2 sr^2)) tabulated over d2 in [0, limit]
        final double ar = 1 / (2 * sigmaR * sigmaR);
        final float d2Max = (float) (RANGE_LIMIT / ar);
        final float[] range = new float[RANGE_TABLE + 1];
        for (int i = 0; i <= RANGE_TABLE; i++) {
            range[i] = (float) Math.exp(-ar * d2Max * i / RANGE_TABLE);
        }
        final float toIndex = RANGE_TABLE / d2Max;
        pass(a, b, w, h, wr, spatial, range, toIndex, true);
        pass(a, b, w, h, wr, spatial, range, toIndex, false);
    }

    private static void pass(float[] a, float[] b, int w, int h, int wr, float[] spatial, float[] range,
            float toIndex, boolean rows) {
        final int lines = rows ? h : w;
        final int len = rows ? w : h;
        final int step = rows ? 1 : w;
        IntStream.range(0, lines).parallel().forEach(line -> {
            final int start = rows ? line * w : line;
            final float[] la = new float[len];
            final float[] lb = b == null ? null : new float[len];
            for (int k = 0; k < len; k++) {
                la[k] = a[start + k * step];
                if (lb != null) {
                    lb[k] = b[start + k * step];
                }
            }
            for (int k = 0; k < len; k++) {
                float a0 = la[k];
                float b0 = lb == null ? 0 : lb[k];
                float numA = 0, numB = 0, den = 0;
                for (int j = -wr; j <= wr; j++) {
                    int kk = k + j;
                    kk = kk < 0 ? 0 : (kk >= len ? len - 1 : kk);
                    float da = la[kk] - a0;
                    float d2 = da * da;
                    if (lb != null) {
                        float db = lb[kk] - b0;
                        d2 += db * db;
                    }
                    int ri = (int) (d2 * toIndex);
                    if (ri >= range.length) {
                        continue;
                    }
                    float f = spatial[j + wr] * range[ri];
                    numA += f * la[kk];
                    if (lb != null) {
                        numB += f * lb[kk];
                    }
                    den += f;
                }
                if (den > 0) {
                    a[start + k * step] = numA / den;
                    if (lb != null) {
                        b[start + k * step] = numB / den;
                    }
                }
            }
        });
    }
}
