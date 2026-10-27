package org.photoslide.imageops.lz;

import java.util.stream.IntStream;

/**
 * Fast cross bilateral filter on a bilateral grid (Paris and Durand): values
 * of input are averaged over neighbours in space (sigmaS pixels) and in the
 * range of base (sigmaR), using a down sampled 3D grid that is blurred and
 * read back with trilinear interpolation.
 *
 * Ported from LightZone (jnisrc/fast_bilateral/include/fast_lbf.h, after
 * Sylvain Paris' reference code), Copyright (c) 2005-2011 Fabio Riccardi,
 * BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public final class BilateralGrid {

    private static final int PAD_XY = 2;
    private static final int PAD_Z = 2;

    private BilateralGrid() {
    }

    /**
     * @param input values to filter (w * h)
     * @param base edge image deciding which pixels are similar, 0..1
     * @param result receives the filtered values
     * @param weight receives the homogeneous weight (number of similar
     * pixels nearby, high in flat areas, low at edges), may be null
     */
    public static void filter(float[] input, float[] base, int w, int h, double sigmaS, double sigmaR,
            float[] result, float[] weight) {
        final int sw = (int) ((w - 1) / sigmaS) + 2 + 2 * PAD_XY;
        final int sh = (int) ((h - 1) / sigmaS) + 2 + 2 * PAD_XY;
        final int sd = (int) (1 / sigmaR) + 1 + 2 * PAD_Z;
        final int strideY = sw;
        final int strideZ = sw * sh;
        float[] val = new float[sw * sh * sd];
        float[] cnt = new float[sw * sh * sd];
        // splat (nearest grid cell)
        for (int y = 0; y < h; y++) {
            int gy = (int) (y / sigmaS + 0.5) + PAD_XY;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int gx = (int) (x / sigmaS + 0.5) + PAD_XY;
                float z = Math.max(0f, Math.min(1.2f, base[i]));
                int gz = Math.min(sd - 1, (int) (z / sigmaR + 0.5) + PAD_Z);
                int g = gx + gy * strideY + gz * strideZ;
                val[g] += input[i];
                cnt[g] += 1;
            }
        }
        // blur: per axis two passes of [1 2 1] / 4
        int[] offsets = {1, strideY, strideZ};
        float[] bufV = new float[val.length];
        float[] bufC = new float[cnt.length];
        for (int dim = 0; dim < 3; dim++) {
            final int off = offsets[dim];
            for (int iter = 0; iter < 2; iter++) {
                System.arraycopy(val, 0, bufV, 0, val.length);
                System.arraycopy(cnt, 0, bufC, 0, cnt.length);
                final float[] bv = bufV, bc = bufC, dv = val, dc = cnt;
                IntStream.range(1, sd - 1).parallel().forEach(z -> {
                    for (int y = 1; y < sh - 1; y++) {
                        int g = 1 + y * strideY + z * strideZ;
                        for (int x = 1; x < sw - 1; x++, g++) {
                            dv[g] = (bv[g - off] + bv[g + off] + 2 * bv[g]) * 0.25f;
                            dc[g] = (bc[g - off] + bc[g + off] + 2 * bc[g]) * 0.25f;
                        }
                    }
                });
            }
        }
        // slice (trilinear)
        final float[] gv = val, gc = cnt;
        IntStream.range(0, h).parallel().forEach(y -> {
            double fy = y / sigmaS + PAD_XY;
            int y0 = Math.min((int) fy, sh - 2);
            double ty = fy - y0;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                double fx = x / sigmaS + PAD_XY;
                int x0 = Math.min((int) fx, sw - 2);
                double tx = fx - x0;
                double fz = Math.max(0f, Math.min(1.2f, base[i])) / sigmaR + PAD_Z;
                int z0 = Math.min((int) fz, sd - 2);
                double tz = Math.min(1, fz - z0);
                double v = 0, c = 0;
                for (int dz = 0; dz < 2; dz++) {
                    double wz = dz == 0 ? 1 - tz : tz;
                    for (int dy = 0; dy < 2; dy++) {
                        double wy = dy == 0 ? 1 - ty : ty;
                        int row = (y0 + dy) * strideY + (z0 + dz) * strideZ;
                        for (int dx = 0; dx < 2; dx++) {
                            double wgt = wz * wy * (dx == 0 ? 1 - tx : tx);
                            v += wgt * gv[row + x0 + dx];
                            c += wgt * gc[row + x0 + dx];
                        }
                    }
                }
                result[i] = c > 0 ? (float) (v / c) : input[i];
                if (weight != null) {
                    weight[i] = (float) c;
                }
            }
        });
    }
}
