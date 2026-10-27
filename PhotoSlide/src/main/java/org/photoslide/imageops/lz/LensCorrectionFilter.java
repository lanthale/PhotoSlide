package org.photoslide.imageops.lz;

import java.util.List;
import java.util.stream.IntStream;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WholeImageFilter16;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;

/**
 * Manual lens correction: barrel / pincushion distortion (radial polynomial
 * r' = r (1 + k1 r^2 + k2 r^4), radius 1 = half the image diagonal) and
 * lateral chromatic aberration (red and blue scaled slightly against
 * green).
 *
 * Model and slider scaling as LightZone's manual mode (LensCorrectionsOperation,
 * lensfun POLY5 and linear TCA), Copyright (c) 2005-2011 Fabio Riccardi,
 * BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class LensCorrectionFilter extends AbstractImageFilter {

    public static final String NAME = "LensCorrectionFilter";

    private static final float K_SCALE = 1e-3f;
    private static final float TCA_SCALE = 1e-3f;

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("k1", "Distortion (main)", 0, -50f, 50f, 0f, 1f),
            ParamSpec.slider("k2", "Distortion (edge)", 1, -50f, 50f, 0f, 1f),
            ParamSpec.slider("tcaRed", "Color fringe red", 2, -5f, 5f, 0f, 0.1f),
            ParamSpec.slider("tcaBlue", "Color fringe blue", 3, -5f, 5f, 0f, 0.1f));

    public LensCorrectionFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Lens Correction";
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
        final double k1 = K_SCALE * value(0);
        final double k2 = K_SCALE * value(1);
        final double[] channelScale = {1 + TCA_SCALE * value(2), 1, 1 + TCA_SCALE * value(3)};
        return (WholeImageFilter16) src -> {
            final int w = src.width();
            final int h = src.height();
            final short[] s = src.data();
            final Image16 out = Image16.blank(w, h, src.isLinear());
            final short[] d = out.data();
            final double cx = (w - 1) / 2.0;
            final double cy = (h - 1) / 2.0;
            final double norm = 1 / Math.hypot(w / 2.0, h / 2.0);
            IntStream.range(0, h).parallel().forEach(y -> {
                for (int x = 0; x < w; x++) {
                    double u = (x - cx) * norm;
                    double v = (y - cy) * norm;
                    double r2 = u * u + v * v;
                    double f = 1 + k1 * r2 + k2 * r2 * r2;
                    int o = (y * w + x) * 3;
                    for (int c = 0; c < 3; c++) {
                        double sx = u * f * channelScale[c] / norm + cx;
                        double sy = v * f * channelScale[c] / norm + cy;
                        d[o + c] = sample(s, w, h, sx, sy, c);
                    }
                }
            });
            return out;
        };
    }

    /** Bilinear sample of one channel, edges repeated. */
    private static short sample(short[] s, int w, int h, double x, double y, int c) {
        x = Math.max(0, Math.min(w - 1, x));
        y = Math.max(0, Math.min(h - 1, y));
        int x0 = (int) x, y0 = (int) y;
        int x1 = Math.min(w - 1, x0 + 1), y1 = Math.min(h - 1, y0 + 1);
        double tx = x - x0, ty = y - y0;
        double a = (s[(y0 * w + x0) * 3 + c] & 0xFFFF) * (1 - tx) + (s[(y0 * w + x1) * 3 + c] & 0xFFFF) * tx;
        double b = (s[(y1 * w + x0) * 3 + c] & 0xFFFF) * (1 - tx) + (s[(y1 * w + x1) * 3 + c] & 0xFFFF) * tx;
        return (short) (int) (a + (b - a) * ty + 0.5);
    }
}
