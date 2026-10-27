package org.photoslide.imageops.lz;

import java.util.stream.IntStream;

/**
 * Separable Gaussian blur on float planes, as in LightZone: the kernel has
 * 2 * ceil(sigma) + 1 taps (cut off at one sigma, which gives LightZone's
 * look), and for sigma above 32 the plane is halved until the remaining
 * sigma is small, blurred, and scaled back up.
 *
 * Ported from LightZone (Functions.getGaussKernel / gaussianBlur),
 * Copyright (c) 2005-2011 Fabio Riccardi, BSD-3-Clause, see
 * THIRD-PARTY-NOTICES.md.
 */
public final class Gauss {

    private static final double MAX_DIRECT_SIGMA = 32;

    private Gauss() {
    }

    /** @return a new blurred plane (the input is not changed) */
    public static float[] blur(float[] plane, int w, int h, double sigma) {
        if (sigma < 0.001) {
            return plane.clone();
        }
        if (sigma > MAX_DIRECT_SIGMA && w > 4 && h > 4) {
            int hw = (w + 1) / 2;
            int hh = (h + 1) / 2;
            float[] small = blur(halve(plane, w, h, hw, hh), hw, hh, sigma / 2);
            return upscale(small, hw, hh, w, h);
        }
        float[] kernel = kernel(sigma);
        float[] tmp = new float[w * h];
        float[] out = new float[w * h];
        horizontal(plane, tmp, w, h, kernel);
        vertical(tmp, out, w, h, kernel);
        return out;
    }

    static float[] kernel(double sigma) {
        int size = 2 * (int) Math.ceil(sigma) + 1;
        float[] k = new float[size];
        float sum = 0;
        for (int i = 0, x = -size / 2; x <= size / 2; x++, i++) {
            k[i] = (float) Math.exp(-(x * x) / (2 * sigma * sigma));
            sum += k[i];
        }
        for (int i = 0; i < size; i++) {
            k[i] /= sum;
        }
        return k;
    }

    private static void horizontal(float[] in, float[] out, int w, int h, float[] k) {
        final int r = k.length / 2;
        IntStream.range(0, h).parallel().forEach(y -> {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float s = 0;
                for (int i = -r; i <= r; i++) {
                    int xx = x + i;
                    xx = xx < 0 ? 0 : (xx >= w ? w - 1 : xx);
                    s += k[i + r] * in[row + xx];
                }
                out[row + x] = s;
            }
        });
    }

    private static void vertical(float[] in, float[] out, int w, int h, float[] k) {
        final int r = k.length / 2;
        IntStream.range(0, h).parallel().forEach(y -> {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                out[row + x] = 0;
            }
            for (int i = -r; i <= r; i++) {
                int yy = y + i;
                yy = yy < 0 ? 0 : (yy >= h ? h - 1 : yy);
                float kv = k[i + r];
                int src = yy * w;
                for (int x = 0; x < w; x++) {
                    out[row + x] += kv * in[src + x];
                }
            }
        });
    }

    /** 2x2 box average to (hw, hh). */
    static float[] halve(float[] in, int w, int h, int hw, int hh) {
        float[] out = new float[hw * hh];
        IntStream.range(0, hh).parallel().forEach(y -> {
            int y0 = Math.min(2 * y, h - 1);
            int y1 = Math.min(2 * y + 1, h - 1);
            for (int x = 0; x < hw; x++) {
                int x0 = Math.min(2 * x, w - 1);
                int x1 = Math.min(2 * x + 1, w - 1);
                out[y * hw + x] = 0.25f * (in[y0 * w + x0] + in[y0 * w + x1] + in[y1 * w + x0] + in[y1 * w + x1]);
            }
        });
        return out;
    }

    /** Bilinear scale from (sw, sh) to (w, h), pixel centres aligned. */
    static float[] upscale(float[] in, int sw, int sh, int w, int h) {
        float[] out = new float[w * h];
        final float fx = (float) sw / w;
        final float fy = (float) sh / h;
        IntStream.range(0, h).parallel().forEach(y -> {
            float sy = Math.max(0, (y + 0.5f) * fy - 0.5f);
            int y0 = Math.min((int) sy, sh - 1);
            int y1 = Math.min(y0 + 1, sh - 1);
            float ty = sy - y0;
            for (int x = 0; x < w; x++) {
                float sx = Math.max(0, (x + 0.5f) * fx - 0.5f);
                int x0 = Math.min((int) sx, sw - 1);
                int x1 = Math.min(x0 + 1, sw - 1);
                float tx = sx - x0;
                float a = in[y0 * sw + x0] + (in[y0 * sw + x1] - in[y0 * sw + x0]) * tx;
                float b = in[y1 * sw + x0] + (in[y1 * sw + x1] - in[y1 * sw + x0]) * tx;
                out[y * w + x] = a + (b - a) * ty;
            }
        });
        return out;
    }
}
