package org.photoslide.imageops.lz;


import org.photoslide.I18n;
import java.util.List;
import org.photoslide.imageops.ParamSpec;

/**
 * Spot removal: round clone areas set with one click; the source is placed
 * next to the spot and can be moved.
 *
 * Ported from LightZone (SpotOperationImpl), Copyright (c) 2005-2011 Fabio
 * Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class SpotFilter extends CloneFilter {

    public static final String NAME = "SpotFilter";

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("radius", I18n.t("tool.param.spotSize"), 0, 3f, 300f, 20f, 1f));

    public SpotFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return I18n.t("tool.spot");
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    /** Radius in pixels of the original for the next spot. */
    public float spotRadius() {
        return value(0);
    }
}
