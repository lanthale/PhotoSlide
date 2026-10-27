package org.photoslide.imageops.lz;

/**
 * B-spline and rational B-spline curves (uniform open knot vector) and
 * linear interpolation of the resulting point lists. Used for tone curves
 * (zones, colour balance, tint).
 *
 * Ported from LightZone (com.lightcrafts.utils.splines), Copyright (c)
 * 2005-2011 Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public final class Splines {

    private Splines() {
    }

    private static void knot(int vertices, int order, int[] knots) {
        knots[0] = 0;
        for (int i = 1; i < vertices + order; i++) {
            knots[i] = (i > order - 1 && i < vertices + 1) ? knots[i - 1] + 1 : knots[i - 1];
        }
    }

    private static void basis(int order, double t, int vertices, int[] knots, double[] weights, double[] nbasis) {
        double[] temp = new double[knots.length - 1];
        for (int i = 0; i < knots.length - 1; i++) {
            temp[i] = (t >= knots[i] && t < knots[i + 1]) ? 1 : 0;
        }
        for (int k = 2; k <= order; k++) {
            for (int i = 0; i < knots.length - k; i++) {
                double d = temp[i] != 0 ? ((t - knots[i]) * temp[i]) / (knots[i + k - 1] - knots[i]) : 0;
                double e = temp[i + 1] != 0 ? ((knots[i + k] - t) * temp[i + 1]) / (knots[i + k] - knots[i + 1]) : 0;
                temp[i] = d + e;
            }
        }
        if (t == (double) knots[knots.length - 1]) {
            temp[vertices - 1] = 1;
        }
        if (weights == null) {
            System.arraycopy(temp, 0, nbasis, 0, vertices);
            return;
        }
        double sum = 0;
        for (int i = 0; i < vertices; i++) {
            sum += temp[i] * weights[i];
        }
        for (int i = 0; i < vertices; i++) {
            nbasis[i] = sum != 0 ? (temp[i] * weights[i]) / sum : 0;
        }
    }

    /** B-spline through the control polygon; curve.length points are computed. */
    public static void bspline(int order, double[][] polygon, double[][] curve) {
        rbspline(order, polygon, null, curve);
    }

    /**
     * Rational B-spline (weights per control point, null = all 1).
     */
    public static void rbspline(int order, double[][] polygon, double[] weights, double[][] curve) {
        int[] knots = new int[polygon.length + order];
        double[] nbasis = new double[polygon.length];
        int dimensions = polygon[0].length;
        knot(polygon.length, order, knots);
        double end = knots[knots.length - 1];
        double step = end / (curve.length - 1);
        double t = 0;
        for (int n = 0; n < curve.length; n++) {
            if (end - t < 5e-6) {
                t = end;
            }
            basis(order, t, polygon.length, knots, weights, nbasis);
            for (int j = 0; j < dimensions; j++) {
                double x = 0;
                for (int i = 0; i < polygon.length; i++) {
                    x += nbasis[i] * polygon[i][j];
                }
                curve[n][j] = x;
            }
            t += step;
        }
    }

    /**
     * Linear interpolation in a curve of (x, y) points with ascending x.
     * Below the first point the line through the origin is used, above the
     * last the line to (1, 1). Fast for ascending x (remembers the position).
     */
    public static final class Interpolator {

        private int lastIdx;

        public void reset() {
            lastIdx = 0;
        }

        public double interpolate(double x, double[][] curve) {
            if (x < curve[0][0]) {
                return x * curve[0][1] / curve[0][0];
            }
            int last = curve.length - 1;
            if (x > curve[last][0]) {
                return curve[last][1] + (x - curve[last][0]) * (1 - curve[last][1]) / (1 - curve[last][0]);
            }
            int i = lastIdx;
            if (i > 0 && curve[i][0] > x) {
                i = 0;
            }
            while (i < last && curve[i + 1][0] < x) {
                i++;
            }
            lastIdx = i;
            if (curve[i][0] == x) {
                return curve[i][1];
            }
            if (curve[i][0] < x) {
                return curve[i][1] + (x - curve[i][0]) * (curve[i + 1][1] - curve[i][1]) / (curve[i + 1][0] - curve[i][0]);
            }
            return curve[i - 1][1] + (x - curve[i - 1][0]) * (curve[i][1] - curve[i - 1][1]) / (curve[i][0] - curve[i - 1][0]);
        }
    }

    /**
     * Table of 65536 offsets for a "bump" curve: 0 at both ends, value at
     * position center (quadratic B-spline through (0,0), (center, value),
     * (1,0)). Used by colour balance, white point and tint.
     */
    public static double[] bump(double center, double value) {
        double[][] polygon = {{0, 0}, {center, value}, {1, 0}};
        double[][] curve = new double[256][2];
        bspline(2, polygon, curve);
        double[] out = new double[65536];
        Interpolator in = new Interpolator();
        for (int i = 0; i < 65536; i++) {
            out[i] = in.interpolate(i / 65535.0, curve);
        }
        return out;
    }
}
