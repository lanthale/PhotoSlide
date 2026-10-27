package org.photoslide.imageops.lz;

import java.util.List;
import java.util.stream.IntStream;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;

/**
 * Hi pass sharpening: a small Laplacian of Gaussian kernel (5 x 5) that
 * boosts fine detail.
 *
 * Algorithm ported from LightZone (HiPassFilterOperation,
 * Functions.LoGSharpenKernel), Copyright (c) 2005-2011 Fabio Riccardi,
 * BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class HiPassFilter extends AbstractImageFilter {

    public static final String NAME = "HiPassFilter";
    private static final int SIZE = 5;

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("gain", "Gain", 0, 0.01f, 1f, 0.02f, 0.001f),
            ParamSpec.slider("radius", "Radius", 1, 0.2f, 5f, 0.8f, 0.001f));

    public HiPassFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Hi Pass Sharpen";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    static float[] kernel(double radius, double gain) {
        radius = Math.max(radius, 0.00001);
        float[] k = new float[SIZE * SIZE];
        double sum = 0;
        for (int j = 0, n = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++, n++) {
                int x = i - SIZE / 2;
                int y = j - SIZE / 2;
                double e = (x * x + y * y) / (2 * radius * radius);
                double v = -Math.exp(-e) * (1 - e);
                sum += v;
                k[n] = (float) v;
            }
        }
        for (int n = 0; n < k.length; n++) {
            k[n] = (float) (k[n] / sum * gain);
        }
        k[SIZE * SIZE / 2] += (float) (1 - gain);
        return k;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        final float[] k = kernel(ctx.scaled(value(1)), value(0));
        return new Filter16() {
            @Override
            public void apply(Image16 src, Image16 dst, int yStart, int yEnd) {
                final int w = src.width();
                final int h = src.height();
                final short[] s = src.data();
                final short[] d = dst.data();
                final int r = SIZE / 2;
                for (int y = yStart; y < yEnd; y++) {
                    for (int x = 0; x < w; x++) {
                        float a = 0, b = 0, c = 0;
                        for (int j = -r, n = 0; j <= r; j++) {
                            int yy = Math.max(0, Math.min(h - 1, y + j));
                            for (int i = -r; i <= r; i++, n++) {
                                int xx = Math.max(0, Math.min(w - 1, x + i));
                                int p = (yy * w + xx) * 3;
                                float kv = k[n];
                                a += kv * (s[p] & 0xFFFF);
                                b += kv * (s[p + 1] & 0xFFFF);
                                c += kv * (s[p + 2] & 0xFFFF);
                            }
                        }
                        int o = (y * w + x) * 3;
                        d[o] = Planes.to16(a / 65535f);
                        d[o + 1] = Planes.to16(b / 65535f);
                        d[o + 2] = Planes.to16(c / 65535f);
                    }
                }
            }

            @Override
            public String name() {
                return "Hi Pass";
            }
        };
    }
}
