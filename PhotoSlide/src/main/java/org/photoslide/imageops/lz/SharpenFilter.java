package org.photoslide.imageops.lz;

import java.util.List;
import java.util.stream.IntStream;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WholeImageFilter16;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;
import org.photoslide.imageops.simd.Simd;

/**
 * Unsharp mask on the luminance only (no colour fringes), in a perceptual
 * scale, with a soft threshold that protects smooth areas from sharpening
 * noise.
 *
 * Algorithm ported from LightZone (UnSharpMaskOperation V3,
 * UnSharpMaskOpImage), Copyright (c) 2005-2011 Fabio Riccardi,
 * BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class SharpenFilter extends AbstractImageFilter {

    public static final String NAME = "SharpenFilter";

    /** linear -> perceptual (inverse of f) and back (f), 65536 entries */
    private static final float[] TO_PERCEPTUAL = new float[65536];
    private static final float[] TO_LINEAR = new float[65536];

    static {
        int[] f = new int[65536];
        for (int i = 0; i < 65536; i++) {
            f[i] = (int) (0xFFFF * curve(i / 65535.0) + 0.5);
            TO_LINEAR[i] = f[i] / 65535f;
        }
        for (int i = 0; i < 65536; i++) {
            int low = 0, high = 65535, p = 0;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (i < f[mid]) {
                    high = mid - 1;
                } else if (i > f[mid]) {
                    low = mid + 1;
                } else {
                    p = mid;
                    break;
                }
                p = low;
            }
            TO_PERCEPTUAL[i] = Math.min(65535, p) / 65535f;
        }
    }

    private static double curve(double x) {
        x *= 10.0;
        return x * (1 - 1 / Math.exp(x * x / 5.0)) / 10.0;
    }

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("amount", "Amount", 0, 1f, 500f, 100f, 1f),
            ParamSpec.slider("radius", "Radius", 1, 0.1f, 500f, 1f, 0.1f),
            ParamSpec.slider("threshold", "Threshold", 2, 0f, 100f, 20f, 1f));

    public SharpenFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Sharpen";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        final float gain = value(0) / 100f;
        final double sigma = ctx.scaled(value(1));
        final float threshold = 256f * (int) value(2);
        return (WholeImageFilter16) src -> {
            YstPlanes p = YstPlanes.of(src);
            final int n = p.w * p.h;
            final float[] py = new float[n];
            for (int i = 0; i < n; i++) {
                py[i] = TO_PERCEPTUAL[index(p.y[i])];
            }
            final float[] blur = Gauss.blur(py, p.w, p.h, sigma);
            if (Simd.ENABLED) {
                // the threshold curve (exp per pixel) in float vectors, per row
                IntStream.range(0, p.h).parallel().forEach(row -> {
                    final float[] e = new float[p.w];
                    for (int x = 0, i = row * p.w; x < p.w; x++, i++) {
                        float d = (py[i] - blur[i]) * 65535f;
                        e[x] = -0.05f * (20 * Math.abs(d) - threshold + 60);
                    }
                    Simd.exp(e, e, p.w);
                    for (int x = 0, i = row * p.w; x < p.w; x++, i++) {
                        float d = (py[i] - blur[i]) * 65535f;
                        double s = 1 / (1 + (double) e[x]);
                        float v = py[i] + (float) (gain * d * s) / 65535f;
                        p.y[i] = TO_LINEAR[index(v)];
                    }
                });
                return p.toImage();
            }
            IntStream.range(0, p.h).parallel().forEach(row -> {
                for (int i = row * p.w, end = i + p.w; i < end; i++) {
                    float d = (py[i] - blur[i]) * 65535f;
                    double s = 1 / (1 + Math.exp(-0.05 * (20 * Math.abs(d) - threshold + 60)));
                    float v = py[i] + (float) (gain * d * s) / 65535f;
                    p.y[i] = TO_LINEAR[index(v)];
                }
            });
            return p.toImage();
        };
    }

    private static int index(float v) {
        int q = (int) (v * 65535f + 0.5f);
        return q < 0 ? 0 : (q > 65535 ? 65535 : q);
    }
}
