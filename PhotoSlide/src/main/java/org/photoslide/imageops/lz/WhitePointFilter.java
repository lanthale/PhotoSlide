package org.photoslide.imageops.lz;


import org.photoslide.I18n;
import java.util.List;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WorkingSpace;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;
import org.photoslide.imageops.Pickable;

/**
 * Removes a colour cast: pick something that should be neutral; its colour
 * is subtracted, most at its own brightness.
 *
 * Algorithm ported from LightZone (WhitePointOperationImpl), Copyright (c)
 * 2005-2011 Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class WhitePointFilter extends AbstractImageFilter implements Pickable {

    public static final String NAME = "WhitePointFilter";

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.pick("neutral", I18n.t("tool.param.neutralColor"), 0));

    public WhitePointFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return I18n.t("tool.colorCast");
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        if (value(0) < 0) {
            return null;
        }
        double[] p = {value(0) * 65535.0, value(1) * 65535.0, value(2) * 65535.0};
        double[] t = BumpCurves.towards(p);
        double lum = WorkingSpace.luminance(value(0), value(1), value(2));
        return BumpCurves.filter("Color Cast", Math.max(0.001, Math.min(0.999, lum)),
                new double[]{-t[0], -t[1], -t[2]});
    }

    @Override
    public String pickLabel() {
        return I18n.t("tool.pickNeutralGrey");
    }

    @Override
    public void pick(double[] rgb) {
        for (int c = 0; c < 3; c++) {
            setValue(c, (float) Math.max(0, Math.min(1, rgb[c])));
        }
    }
}
