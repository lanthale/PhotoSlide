package org.photoslide.datamodel;

/**
 * The colour space of all 16 bit image data in PhotoSlide: linear light with
 * ProPhoto (ROMM) primaries. Much wider than sRGB, so editing (saturation,
 * white balance) does not clip colours. Data is converted to sRGB only at the
 * end, for display and 8 bit output (see Display16).
 *
 * The sRGB -> ProPhoto matrix is the one LibRaw uses for output_color 4
 * (dcraw prophoto_rgb), so RAW files decoded by LibRaw in ProPhoto and files
 * converted here match exactly. Its rows sum to 1: neutral stays neutral.
 */
public final class WorkingSpace {

    /** linear sRGB -> linear working space */
    public static final double[][] SRGB_TO_WORKING = {
        {0.529317, 0.330092, 0.140588},
        {0.098368, 0.873465, 0.028169},
        {0.016879, 0.117663, 0.865457}
    };

    /** linear working space -> linear sRGB */
    public static final double[][] WORKING_TO_SRGB = invert(SRGB_TO_WORKING);

    /** linear sRGB (D65) -> XYZ */
    private static final double[][] SRGB_TO_XYZ = {
        {0.4124564, 0.3575761, 0.1804375},
        {0.2126729, 0.7151522, 0.0721750},
        {0.0193339, 0.1191920, 0.9503041}
    };

    /** linear working space -> XYZ */
    public static final double[][] WORKING_TO_XYZ = multiply(SRGB_TO_XYZ, WORKING_TO_SRGB);

    /** XYZ -> linear working space */
    public static final double[][] XYZ_TO_WORKING = invert(WORKING_TO_XYZ);

    /** Luminance weights of the working space (Y = WR*R + WG*G + WB*B). */
    public static final double WR = WORKING_TO_XYZ[1][0];
    public static final double WG = WORKING_TO_XYZ[1][1];
    public static final double WB = WORKING_TO_XYZ[1][2];

    private WorkingSpace() {
    }

    /** Luminance of linear working space values (any scale). */
    public static double luminance(double r, double g, double b) {
        return WR * r + WG * g + WB * b;
    }

    public static double[][] multiply(double[][] a, double[][] b) {
        double[][] c = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                c[i][j] = a[i][0] * b[0][j] + a[i][1] * b[1][j] + a[i][2] * b[2][j];
            }
        }
        return c;
    }

    public static double[] multiply(double[][] m, double[] v) {
        return new double[]{
            m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
            m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
            m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]
        };
    }

    public static double[][] invert(double[][] m) {
        double a = m[0][0], b = m[0][1], c = m[0][2];
        double d = m[1][0], e = m[1][1], f = m[1][2];
        double g = m[2][0], h = m[2][1], i = m[2][2];
        double det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        if (Math.abs(det) < 1e-12) {
            throw new IllegalArgumentException("Matrix is not invertible");
        }
        return new double[][]{
            {(e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det},
            {(f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det},
            {(d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det}
        };
    }
}
