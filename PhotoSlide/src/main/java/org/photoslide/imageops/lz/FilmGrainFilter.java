package org.photoslide.imageops.lz;

import java.util.List;
import java.util.stream.IntStream;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WholeImageFilter16;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.BlendMode;
import org.photoslide.imageops.ParamSpec;

/**
 * Film grain: OpenSimplex noise, softened by a blur, combined with the image
 * in hard light mode (grey noise does nothing, brighter or darker grain
 * lightens or darkens).
 *
 * Algorithm ported from LightZone (FilmGrainOperation, FilmGrainOpImage),
 * Copyright (c) 2005-2011 Fabio Riccardi, BSD-3-Clause, see
 * THIRD-PARTY-NOTICES.md.
 */
public class FilmGrainFilter extends AbstractImageFilter {

    public static final String NAME = "FilmGrainFilter";

    private static final OpenSimplexNoise NOISE = new OpenSimplexNoise();

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("size", "Grain size", 0, 1f, 3f, 1f, 0.1f),
            ParamSpec.slider("sharpness", "Sharpness", 1, 0f, 1f, 0.5f, 0.1f),
            ParamSpec.slider("color", "Color", 2, 0f, 1f, 0f, 0.1f),
            ParamSpec.slider("intensity", "Intensity", 3, 0.1f, 1f, 0.5f, 0.1f));

    public FilmGrainFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Film Grain";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        final double featureSize = ctx.scaled(value(0) + 0.01);
        final double blur = ctx.scaled(1.0 - value(1));
        final double color = value(2);
        final double intensity = value(3);
        return (WholeImageFilter16) src -> {
            final int w = src.width();
            final int h = src.height();
            final int planes = color != 0 ? 3 : 1;
            final float[][] grain = new float[planes][w * h];
            IntStream.range(0, h).parallel().forEach(y -> {
                for (int x = 0; x < w; x++) {
                    for (int c = 0; c < planes; c++) {
                        double v = NOISE.eval(x / featureSize, y / featureSize, (c - 1) * color);
                        grain[c][y * w + x] = (float) ((v * intensity + 1) * 0.5);
                    }
                }
            });
            for (int c = 0; c < planes; c++) {
                grain[c] = Gauss.blur(grain[c], w, h, blur);
            }
            Image16 out = Image16.blank(w, h, true);
            final short[] s = src.data();
            final short[] d = out.data();
            IntStream.range(0, h).parallel().forEach(y -> {
                int i = y * w * 3;
                for (int x = 0; x < w; x++) {
                    int p = y * w + x;
                    for (int c = 0; c < 3; c++, i++) {
                        float g = grain[planes == 3 ? c : 0][p];
                        d[i] = Planes.to16(BlendMode.HARD_LIGHT.blend(g, (s[i] & 0xFFFF) / 65535f));
                    }
                }
            });
            return out;
        };
    }
}
