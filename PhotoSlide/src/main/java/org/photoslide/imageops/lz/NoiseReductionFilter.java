package org.photoslide.imageops.lz;

import java.util.List;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WholeImageFilter16;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;

/**
 * Noise reduction: edge preserving (bilateral) smoothing of the colour
 * (chroma) and, separately, of the luminance grain.
 *
 * Algorithm ported from LightZone (AdvancedNoiseReductionOperationV4,
 * BilateralFilterRGBOpImage, bilateralFilterRGB.cpp), Copyright (c)
 * 2005-2011 Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class NoiseReductionFilter extends AbstractImageFilter {

    public static final String NAME = "NoiseReductionFilter";

    /** Range sigmas: LightZone passes 0.02 / 0.04, effective half of it. */
    private static final double LUMA_RANGE = 0.01;
    private static final double CHROMA_RANGE = 0.02;

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("color", "Color noise", 0, 0f, 20f, 2f, 0.1f),
            ParamSpec.slider("grain", "Grain noise", 1, 0f, 20f, 0f, 0.1f));

    public NoiseReductionFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Noise Reduction";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        final double color = ctx.scaled(value(0));
        final double grain = ctx.scaled(value(1));
        if (color < 0.01 && grain < 0.01) {
            return null;
        }
        return (WholeImageFilter16) src -> {
            YstPlanes p = YstPlanes.of(src);
            SeparableBilateral.filter(p.y, null, p.w, p.h, grain, LUMA_RANGE);
            SeparableBilateral.filter(p.s, p.t, p.w, p.h, color, CHROMA_RANGE);
            return p.toImage();
        };
    }
}
