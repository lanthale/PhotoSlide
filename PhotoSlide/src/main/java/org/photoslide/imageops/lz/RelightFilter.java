package org.photoslide.imageops.lz;

import java.util.List;
import java.util.stream.IntStream;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WholeImageFilter16;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;
import org.photoslide.imageops.simd.Simd;

/**
 * Relight (tone mapper): opens up shadows and holds back highlights
 * locally, keeping local contrast (detail). A bilateral filter of the
 * luminance gives the local brightness, which is compressed while the
 * difference to it (the detail) is kept or boosted.
 *
 * Algorithm ported from LightZone (HDROperationV3 "Tone Mapper V5",
 * FastBilateralFilterOpImage, HDROpImage2), Copyright (c) 2005-2011 Fabio
 * Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class RelightFilter extends AbstractImageFilter {

    public static final String NAME = "RelightFilter";

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("shadows", "Shadows", 0, 0f, 10f, 3f, 0.05f),
            ParamSpec.slider("highlights", "Highlights", 1, 0f, 1f, 0.2f, 0.05f),
            ParamSpec.slider("detail", "Detail", 2, 0f, 10f, 1.5f, 0.05f),
            ParamSpec.slider("depth", "Depth", 3, 8f, 64f, 64f, 0.5f),
            ParamSpec.slider("fuzz", "Fuzz", 4, 0.1f, 1f, 0.1f, 0.05f));

    /** Edge profile of the bilateral filter (log like, with a toe for the shadows). */
    private static final float[] PROFILE = new float[65536];

    static {
        for (int i = 0; i < 65536; i++) {
            double x = i / 65535.0;
            PROFILE[i] = (float) (Math.log1p(x) / Math.log(2) + 1.5 * Math.exp(-10 * x) * Math.pow(x, 0.7));
        }
    }

    public RelightFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Relight";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        final double shadows = Math.max(0.05, value(0));
        final double highlights = value(1);
        final double detail = value(2);
        final double sigmaS = Math.max(1, ctx.scaled(value(3)));
        final double fuzz = ctx.scaled(10 * value(4));
        final float wr = ColorScience.WR, wg = ColorScience.WG, wb = ColorScience.WB;
        return (WholeImageFilter16) src -> {
            final int w = src.width();
            final int h = src.height();
            final short[] s = src.data();
            final float[] y = new float[w * h];
            final float[] profile = new float[w * h];
            IntStream.range(0, h).parallel().forEach(row -> {
                for (int x = 0, o = row * w, i = o * 3; x < w; x++, o++, i += 3) {
                    float v = (wr * (s[i] & 0xFFFF) + wg * (s[i + 1] & 0xFFFF) + wb * (s[i + 2] & 0xFFFF)) / 65535f;
                    y[o] = v;
                    int q = (int) (v * 65535f + 0.5f);
                    profile[o] = PROFILE[q < 0 ? 0 : (q > 65535 ? 65535 : q)];
                }
            });
            final float[] bf = new float[w * h];
            final float[] weight = new float[w * h];
            BilateralGrid.filter(y, profile, w, h, sigmaS, 0.1, bf, weight);
            final float wNorm = (float) (1 / (sigmaS * sigmaS));
            final float[] blurred = Gauss.blur(bf, w, h, fuzz);
            Image16 out = Image16.blank(w, h, true);
            final short[] d = out.data();
            if (Simd.ENABLED) {
                // the two powers per pixel are most of the time: computed per row in float vectors
                final float invShadows = (float) (1 / shadows);
                final float fDetail = (float) detail;
                IntStream.range(0, h).parallel().forEach(row -> {
                    final double[] mRow = new double[w];
                    final float[] mf = new float[w];
                    final float[] yf = new float[w];
                    final float[] mm = new float[w];
                    for (int x = 0, o = row * w; x < w; x++, o++) {
                        double m = localMean(bf, weight, blurred, wNorm, o);
                        mRow[x] = m;
                        boolean skip = y[o] <= 0 || m <= 0;
                        mf[x] = skip ? 1 : (float) m;
                        yf[x] = skip ? -1 : y[o];
                    }
                    Simd.powRatio(mf, yf, invShadows, fDetail, mm, w);
                    for (int x = 0, i = row * w * 3; x < w; x++, i += 3) {
                        if (yf[x] < 0) {
                            d[i] = s[i];
                            d[i + 1] = s[i + 1];
                            d[i + 2] = s[i + 2];
                            continue;
                        }
                        relight(s, d, i, mRow[x], yf[x], mm[x], highlights);
                    }
                });
                return out;
            }
            IntStream.range(0, h).parallel().forEach(row -> {
                for (int x = 0, o = row * w, i = o * 3; x < w; x++, o++, i += 3) {
                    double m = localMean(bf, weight, blurred, wNorm, o);
                    double yy = y[o];
                    if (yy <= 0 || m <= 0) {
                        d[i] = s[i];
                        d[i + 1] = s[i + 1];
                        d[i + 2] = s[i + 2];
                        continue;
                    }
                    double mm = Math.pow(m, 1 / shadows) * Math.pow(yy / m, detail);
                    relight(s, d, i, m, yy, mm, highlights);
                }
            });
            return out;
        };
    }

    /** Local brightness: the bilateral result where it is reliable, else its blur. */
    private static double localMean(float[] bf, float[] weight, float[] blurred, float wNorm, int o) {
        double m = Math.min(1, Math.max(0, bf[o]));
        double um = Math.min(1, weight[o] * wNorm);
        um = Math.min(um * um, 1);
        return um * m + (1 - um) * Math.min(1, Math.max(0, blurred[o]));
    }

    /** Scales the pixel at i by the tone mapped luminance mm (of yy, local brightness m). */
    private static void relight(short[] s, short[] d, int i, double m, double yy, double mm, double highlights) {
        double compressed = softLight(1 - m, Math.min(1, yy));
        double ratio = (compressed * highlights + (1 - highlights)) * mm / yy;
        d[i] = clamp((s[i] & 0xFFFF) * ratio);
        d[i + 1] = clamp((s[i + 1] & 0xFFFF) * ratio);
        d[i + 2] = clamp((s[i + 2] & 0xFFFF) * ratio);
    }

    private static double softLight(double f, double b) {
        double m = f * b;
        double s = 1 - (1 - f) * (1 - b);
        return (1 - b) * m + b * s;
    }

    private static short clamp(double v) {
        long q = Math.round(v);
        return (short) (q < 0 ? 0 : (q > 65535 ? 65535 : q));
    }
}
