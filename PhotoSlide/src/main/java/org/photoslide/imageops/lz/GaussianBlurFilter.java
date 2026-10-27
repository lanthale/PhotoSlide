package org.photoslide.imageops.lz;

import java.util.List;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WholeImageFilter16;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;

/**
 * Gaussian blur; with a region or a blend mode it is the base for glow and
 * soft focus effects.
 *
 * Algorithm ported from LightZone (GaussianBlurOperation), Copyright (c)
 * 2005-2011 Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class GaussianBlurFilter extends AbstractImageFilter {

    public static final String NAME = "GaussianBlurFilter";

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("radius", "Radius", 0, 0.1f, 500f, 5f, 0.1f));

    public GaussianBlurFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Gaussian Blur";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        final double sigma = ctx.scaled(value(0));
        return (WholeImageFilter16) src -> {
            float[][] p = Planes.split(src);
            for (int c = 0; c < 3; c++) {
                p[c] = Gauss.blur(p[c], src.width(), src.height(), sigma);
            }
            return Planes.merge(p, src.width(), src.height());
        };
    }

    /** Blurred copy of an image (helper for other tools). */
    static Image16 blur(Image16 src, double sigma) {
        float[][] p = Planes.split(src);
        for (int c = 0; c < 3; c++) {
            p[c] = Gauss.blur(p[c], src.width(), src.height(), sigma);
        }
        return Planes.merge(p, src.width(), src.height());
    }
}
