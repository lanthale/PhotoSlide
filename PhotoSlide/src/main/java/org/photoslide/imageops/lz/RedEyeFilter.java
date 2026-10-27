package org.photoslide.imageops.lz;

import java.util.List;
import java.util.stream.IntStream;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WholeImageFilter16;
import org.photoslide.datamodel.WorkingSpace;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;
import org.photoslide.imageops.mask.Contour;

/**
 * Red eye removal inside the region: red (and orange) pixels are found in
 * Lab, skin tones are excluded, and the found pixels are darkened towards a
 * neutral based on their green channel.
 *
 * Algorithm ported from LightZone (RedEyesOperation, RedMaskOpImage,
 * RedMaskBlackener), Copyright (c) 2005-2011 Fabio Riccardi, BSD-3-Clause,
 * see THIRD-PARTY-NOTICES.md.
 */
public class RedEyeFilter extends AbstractImageFilter {

    public static final String NAME = "RedEyeFilter";

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("tolerance", "Tolerance", 0, 0.5f, 1.5f, 1f, 0.05f));

    private static final short[] POW = new short[65536];

    static {
        for (int i = 0; i < 65536; i++) {
            POW[i] = (short) (int) (0xffff * Math.pow(i / 65535.0, 1.4));
        }
    }

    public RedEyeFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Red Eyes";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        if (getRegion().isEmpty()) {
            return null;                    // needs a region around the eyes
        }
        final double t = value(0);
        final double[][] toXyz = WorkingSpace.WORKING_TO_XYZ;
        final double xn = toXyz[0][0] + toXyz[0][1] + toXyz[0][2];
        final double zn = toXyz[2][0] + toXyz[2][1] + toXyz[2][2];
        // bounding box of the region in rendered pixels: only there is work to do
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Contour c : getRegion().contours()) {
            for (Contour.Point p : c.points()) {
                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
                maxX = Math.max(maxX, p.x());
                maxY = Math.max(maxY, p.y());
            }
        }
        final double bx0 = minX, by0 = minY, bx1 = maxX, by1 = maxY;
        return (WholeImageFilter16) src -> {
            final int w = src.width();
            final int h = src.height();
            final int pad = (int) Math.ceil(ctx.scaled(16)) + 2;
            final int x0 = Math.max(0, (int) (bx0 * ctx.scale() - ctx.offsetX()) - pad);
            final int y0 = Math.max(0, (int) (by0 * ctx.scale() - ctx.offsetY()) - pad);
            final int x1 = Math.min(w, (int) (bx1 * ctx.scale() - ctx.offsetX()) + pad);
            final int y1 = Math.min(h, (int) (by1 * ctx.scale() - ctx.offsetY()) + pad);
            final short[] s = src.data();
            final Image16 out = new Image16(w, h, s.clone(), src.isLinear());
            if (x1 <= x0 || y1 <= y0) {
                return out;
            }
            final int bw = x1 - x0;
            final int bh = y1 - y0;
            final float[] red = new float[bw * bh];
            IntStream.range(0, bh).parallel().forEach(yy -> {
                for (int xx = 0; xx < bw; xx++) {
                    int i = ((yy + y0) * w + xx + x0) * 3;
                    double r = (s[i] & 0xFFFF) / 65535.0, g = (s[i + 1] & 0xFFFF) / 65535.0, b = (s[i + 2] & 0xFFFF) / 65535.0;
                    double X = (toXyz[0][0] * r + toXyz[0][1] * g + toXyz[0][2] * b) / xn;
                    double Y = toXyz[1][0] * r + toXyz[1][1] * g + toXyz[1][2] * b;
                    double Z = (toXyz[2][0] * r + toXyz[2][1] * g + toXyz[2][2] * b) / zn;
                    double fx = f(X), fy = f(Y), fz = f(Z);
                    double L = 116 * fy - 16, A = 500 * (fx - fy), B = 200 * (fy - fz);
                    boolean isRed = (A > 71.5 - 48.5 * t && A < 71.5 + 48.5 * t && B > 8 - 35 * t && B < 8 + 35 * t)
                            || (A > 1 && A < 61 && B > 46 && B < 66)
                            || (A > 62 - 13.6 && A < 62 + 13.6 && B > 35 - 13 && B < 35 + 13);
                    boolean skin = L > 43.3593 && L < 99.6093 && A > 1.5 && A < 43.125 && B > -4.6875 && B < 34.6875;
                    red[yy * bw + xx] = isRed && !skin ? 1f : 0f;
                }
            });
            // grow by one pixel (3 x 3 dilate) and soften
            float[] grown = new float[red.length];
            for (int yy = 0; yy < bh; yy++) {
                for (int xx = 0; xx < bw; xx++) {
                    float m = 0;
                    for (int j = -1; j <= 1 && m == 0; j++) {
                        for (int k = -1; k <= 1; k++) {
                            int y2 = yy + j, x2 = xx + k;
                            if (y2 >= 0 && y2 < bh && x2 >= 0 && x2 < bw && red[y2 * bw + x2] > 0) {
                                m = 1;
                                break;
                            }
                        }
                    }
                    grown[yy * bw + xx] = m;
                }
            }
            final float[] mask = Gauss.blur(grown, bw, bh, ctx.scaled(4));
            final short[] d = out.data();
            for (int yy = 0; yy < bh; yy++) {
                for (int xx = 0; xx < bw; xx++) {
                    float m = mask[yy * bw + xx];
                    if (m <= 0) {
                        continue;
                    }
                    int i = ((yy + y0) * w + xx + x0) * 3;
                    int gg = POW[s[i + 1] & 0xFFFF] & 0xFFFF;
                    for (int c = 0; c < 3; c++) {
                        float v = s[i + c] & 0xFFFF;
                        d[i + c] = (short) (int) ((1 - m) * v + m * gg + 0.5f);
                    }
                }
            }
            return out;
        };
    }

    private static double f(double v) {
        return v > 216.0 / 24389 ? Math.cbrt(v) : (24389.0 / 27 * v + 16) / 116;
    }
}
