package org.photoslide.editpipeline;

import java.util.ArrayList;
import java.util.List;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.WorkingSpace;

/**
 * Colour calibration of a camera from a photo of an X-Rite / Calibrite
 * ColorChecker (24 patches): a 3 x 3 matrix in the working space that maps
 * the decoded colours of the patches as close as possible to their
 * reference values, like the colour matrix of a camera profile. The matrix
 * keeps white white and does not change the brightness, so white balance
 * and exposure stay separate settings: the white balance of the chart photo
 * is only used to fit the matrix, it is not part of it (it depends on the
 * light of each photo).
 *
 * The user marks the centres of the four corner patches in any order; the
 * orientation of the chart (portrait, landscape, upside down) is found by
 * trying all of them and keeping the best fit.
 */
public final class ColorCalibration {

    /**
     * Reference colours, CIE Lab (D50), X-Rite values from November 2014:
     * 4 rows of 6 patches as printed (dark skin ... black).
     */
    private static final double[][] REFERENCE_LAB = {
        {37.54, 14.37, 14.92}, {64.66, 19.27, 17.50}, {49.32, -3.82, -22.54},
        {43.46, -12.74, 22.72}, {54.94, 9.61, -24.79}, {70.48, -32.26, -0.37},
        {62.73, 35.83, 56.50}, {39.43, 10.75, -45.17}, {50.57, 48.64, 16.67},
        {30.10, 22.54, -20.87}, {71.77, -24.13, 58.19}, {71.51, 18.24, 67.37},
        {28.37, 15.42, -49.80}, {54.38, -39.72, 32.27}, {42.43, 51.05, 28.62},
        {81.80, 2.67, 80.41}, {50.63, 51.28, -14.12}, {49.57, -29.71, -28.32},
        {95.19, -1.03, 2.93}, {81.29, -0.57, 0.44}, {66.89, -0.75, -0.06},
        {50.76, -0.13, 0.14}, {35.63, -0.46, -0.48}, {20.64, 0.07, -0.46}};
    private static final int WHITE = 18;
    /** Grey patches used for the white balance (white may clip, black is noisy). */
    private static final int[] GREYS = {19, 20, 21, 22};
    private static final double[] D65 = {0.95047, 1.0, 1.08883};

    /**
     * @param matrix 3 x 3, row major, working space -> working space
     * @param errorBefore mean colour error (Lab a/b distance) of the 18
     * colour patches without the matrix (white balance matched)
     * @param errorAfter the same with the matrix
     * @param patches centres of the 24 patches in pixels (reference order)
     */
    public record Result(double[] matrix, double errorBefore, double errorAfter, List<double[]> patches) {
    }

    private ColorCalibration() {
    }

    /**
     * @param image decoded photo of the chart: linear working space, without
     * base look or colour matrix
     * @param corners centres of the four corner patches in pixels, any order
     */
    public static Result fit(Image16 image, double[][] corners) {
        double[][] reference = new double[24][];
        for (int i = 0; i < 24; i++) {
            reference[i] = labToWorking(REFERENCE_LAB[i]);
        }
        double[][] c = clockwise(corners);
        Result best = null;
        for (int start = 0; start < 4; start++) {
            for (int dir : new int[]{1, -1}) {
                double[][] q = new double[4][];
                for (int k = 0; k < 4; k++) {
                    q[k] = c[Math.floorMod(start + dir * k, 4)];
                }
                for (boolean wide : new boolean[]{true, false}) {
                    Result r = fit(image, q, wide, reference);
                    if (r != null && (best == null || r.errorAfter() < best.errorAfter())) {
                        best = r;
                    }
                }
            }
        }
        return best;
    }

    /**
     * One orientation: q0 -> q1 is the first axis with 6 (wide) or 4 patches,
     * q0 -> q3 the other one.
     */
    private static Result fit(Image16 image, double[][] q, boolean wide, double[][] reference) {
        int ni = wide ? 6 : 4;
        int nj = wide ? 4 : 6;
        double spacing = Math.min(dist(q[0], q[1]) / (ni - 1), dist(q[0], q[3]) / (nj - 1));
        int half = (int) Math.max(1, spacing * 0.18);
        double[][] measured = new double[24][];
        List<double[]> centres = new ArrayList<>(java.util.Collections.nCopies(24, (double[]) null));
        for (int i = 0; i < ni; i++) {
            for (int j = 0; j < nj; j++) {
                double u = (double) i / (ni - 1);
                double v = (double) j / (nj - 1);
                double x = (1 - u) * (1 - v) * q[0][0] + u * (1 - v) * q[1][0] + u * v * q[2][0] + (1 - u) * v * q[3][0];
                double y = (1 - u) * (1 - v) * q[0][1] + u * (1 - v) * q[1][1] + u * v * q[2][1] + (1 - u) * v * q[3][1];
                int row = wide ? j : i;
                int col = wide ? i : j;
                int index = row * 6 + col;
                measured[index] = average(image, x, y, half);
                if (measured[index] == null) {
                    return null;                        // outside the photo
                }
                centres.set(index, new double[]{x, y});
            }
        }
        // white balance and exposure of this photo, from the greys
        double[] gain = new double[3];
        for (int ch = 0; ch < 3; ch++) {
            double m = 0;
            double r = 0;
            for (int g : GREYS) {
                m += measured[g][ch];
                r += reference[g][ch];
            }
            if (m <= 0) {
                return null;
            }
            gain[ch] = r / m;
        }
        double[][] balanced = new double[24][3];
        for (int k = 0; k < 24; k++) {
            for (int ch = 0; ch < 3; ch++) {
                balanced[k][ch] = measured[k][ch] * gain[ch];
            }
        }
        double[] m = fitWhitePreserving(balanced, reference);
        double before = error(balanced, null);
        double after = error(balanced, m);
        return new Result(m, before, after, centres);
    }

    /**
     * Least squares fit of reference = M * measured with every row of M
     * summing to 1 (white stays white). Patches are weighted by 1/brightness,
     * so dark colours count as much as bright ones; the white (may clip) is
     * left out.
     */
    private static double[] fitWhitePreserving(double[][] x, double[][] r) {
        double[] m = new double[9];
        for (int row = 0; row < 3; row++) {
            // r - x2 = a (x0 - x2) + b (x1 - x2), M = (a, b, 1 - a - b)
            double s00 = 0, s01 = 0, s11 = 0, t0 = 0, t1 = 0;
            for (int k = 0; k < 24; k++) {
                if (k == WHITE) {
                    continue;
                }
                double w = 1 / (luminance(r[k]) + 0.02);
                double p = x[k][0] - x[k][2];
                double q = x[k][1] - x[k][2];
                double t = r[k][row] - x[k][2];
                s00 += w * p * p;
                s01 += w * p * q;
                s11 += w * q * q;
                t0 += w * p * t;
                t1 += w * q * t;
            }
            double det = s00 * s11 - s01 * s01;
            double a = (t0 * s11 - t1 * s01) / det;
            double b = (s00 * t1 - s01 * t0) / det;
            m[row * 3] = a;
            m[row * 3 + 1] = b;
            m[row * 3 + 2] = 1 - a - b;
        }
        return m;
    }

    /** Mean Lab a/b distance of the 18 colour patches (matrix null = none). */
    private static double error(double[][] x, double[] m) {
        double sum = 0;
        for (int k = 0; k < WHITE; k++) {
            double[] v = m == null ? x[k] : multiply(m, x[k]);
            double[] lab = workingToLab(v);
            sum += Math.hypot(lab[1] - REFERENCE_LAB[k][1], lab[2] - REFERENCE_LAB[k][2]);
        }
        return sum / WHITE;
    }

    /** Average of the square around (x, y), linear 0..1, or null if it is not inside the photo. */
    private static double[] average(Image16 img, double x, double y, int half) {
        int x0 = (int) Math.round(x) - half, x1 = (int) Math.round(x) + half;
        int y0 = (int) Math.round(y) - half, y1 = (int) Math.round(y) + half;
        if (x0 < 0 || y0 < 0 || x1 >= img.width() || y1 >= img.height()) {
            return null;
        }
        double[] sum = new double[3];
        short[] d = img.data();
        for (int yy = y0; yy <= y1; yy++) {
            for (int xx = x0; xx <= x1; xx++) {
                int i = (yy * img.width() + xx) * 3;
                for (int ch = 0; ch < 3; ch++) {
                    sum[ch] += (d[i + ch] & 0xFFFF) / 65535.0;
                }
            }
        }
        int n = (x1 - x0 + 1) * (y1 - y0 + 1);
        return new double[]{sum[0] / n, sum[1] / n, sum[2] / n};
    }

    /** The four points ordered around their centre. */
    private static double[][] clockwise(double[][] p) {
        double cx = 0, cy = 0;
        for (double[] v : p) {
            cx += v[0] / 4;
            cy += v[1] / 4;
        }
        final double fx = cx, fy = cy;
        double[][] s = p.clone();
        java.util.Arrays.sort(s, java.util.Comparator.comparingDouble(v -> Math.atan2(v[1] - fy, v[0] - fx)));
        return s;
    }

    private static double dist(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    static double[] multiply(double[] m, double[] v) {
        return new double[]{
            m[0] * v[0] + m[1] * v[1] + m[2] * v[2],
            m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
            m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    private static double luminance(double[] v) {
        return WorkingSpace.luminance(v[0], v[1], v[2]);
    }

    /** Lab relative to the white of the working space (the chart's white point maps to it). */
    private static double[] labToWorking(double[] lab) {
        double fy = (lab[0] + 16) / 116;
        double fx = fy + lab[1] / 500;
        double fz = fy - lab[2] / 200;
        double[] xyz = {D65[0] * finv(fx), D65[1] * finv(fy), D65[2] * finv(fz)};
        return WorkingSpace.multiply(WorkingSpace.XYZ_TO_WORKING, xyz);
    }

    private static double[] workingToLab(double[] v) {
        double[] xyz = WorkingSpace.multiply(WorkingSpace.WORKING_TO_XYZ, v);
        double fx = f(xyz[0] / D65[0]);
        double fy = f(xyz[1] / D65[1]);
        double fz = f(xyz[2] / D65[2]);
        return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double f(double t) {
        t = Math.max(0, t);
        return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
    }

    private static double finv(double t) {
        return t > 0.206893 ? t * t * t : (t - 16.0 / 116) / 7.787;
    }
}
