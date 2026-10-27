package org.photoslide.imageops.lz;

import java.util.List;
import org.photoslide.datamodel.Display16;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WorkingSpace;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;
import org.photoslide.imageops.Pickable;

/**
 * Pushes the colours towards a chosen colour, strongest at the midpoint
 * brightness (e.g. warm shadows, cool highlights, toning a black and white
 * image). Grey has no effect. The dropper picks the complementary colour of
 * a pixel, which removes its cast.
 *
 * Algorithm ported from LightZone (ColorBalanceOperationV2, V3), Copyright
 * (c) 2005-2011 Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
public class ColorBalanceFilter extends AbstractImageFilter implements Pickable {

    public static final String NAME = "ColorBalanceFilter";

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.color("color", "Color", 0, 0.5f, 0.5f, 0.5f),
            ParamSpec.slider("midpoint", "Midpoint", 3, 0.01f, 1f, 0.18f, 0.01f));

    public ColorBalanceFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Color Balance";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        double[] p = {value(0) * 65535.0, value(1) * 65535.0, value(2) * 65535.0};
        double[] t = BumpCurves.towards(p);
        if (Math.abs(t[0]) < 1e-6 && Math.abs(t[1]) < 1e-6 && Math.abs(t[2]) < 1e-6) {
            return null;
        }
        return BumpCurves.filter("Color Balance", value(3), t);
    }

    @Override
    public String pickLabel() {
        return "Pick a colour to neutralise";
    }

    @Override
    public void pick(double[] rgb) {
        double[] s = WorkingSpace.multiply(WorkingSpace.WORKING_TO_SRGB, rgb);
        float[] enc = new float[3];
        for (int c = 0; c < 3; c++) {
            enc[c] = (float) Display16.srgbEncode(Math.max(0, s[c]));
        }
        float[] hsb = new float[3];
        Hsb.fromRGB(enc, hsb);
        hsb[0] += 0.5f;
        if (hsb[0] >= 1) {
            hsb[0] -= 1;
        }
        Hsb.toRGB(hsb, enc);
        setValue(0, enc[0]);
        setValue(1, enc[1]);
        setValue(2, enc[2]);
        double lum = WorkingSpace.luminance(rgb[0], rgb[1], rgb[2]);
        setValue(3, (float) Math.max(0.01, Math.min(1, lum)));
    }
}
