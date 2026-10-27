package org.photoslide.imageops;

import java.util.List;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.imageops.lz.LuminanceRatioFilter16;

/**
 * Exposure in stops (+1 = twice as much light) on the linear 16 bit data.
 * All channels are scaled by the same factor, so the hue stays. With
 * "Protect highlights" the brightest tones roll off smoothly into white
 * instead of being clipped.
 *
 * @author selfemp
 */
public class ExposureFilter extends AbstractImageFilter {

    public static final String NAME = "ExposureFilter";

    /** Highlights above this luminance are compressed when protection is on. */
    private static final double SHOULDER = 0.8;

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("ev", "Exposure (EV)", 0, -5f, 5f, 0f, 0.05f),
            ParamSpec.check("protect", "Protect highlights", 1, true));

    public ExposureFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Exposure";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        final double ev = value(0);
        if (ev == 0) {
            return null;
        }
        final double k = Math.pow(2.0, ev);
        final boolean protect = value(1) >= 0.5f;
        return new LuminanceRatioFilter16("Exposure " + ev,
                LuminanceRatioFilter16.ratiosFor(y -> protect ? shoulder(k * y, k) : k * y));
    }

    /**
     * Identity up to SHOULDER; above, [SHOULDER, k] is mapped onto
     * [SHOULDER, 1] with an extended Reinhard curve: slope 1 at the start,
     * exactly 1 for the brightest input, and bright tones stay apart. For
     * k <= 1 nothing is compressed.
     */
    static double shoulder(double x, double k) {
        if (k <= 1 || x <= SHOULDER) {
            return x;
        }
        double l = (k - SHOULDER) / (1 - SHOULDER);
        double u = Math.min(l, (x - SHOULDER) / (1 - SHOULDER));
        return SHOULDER + (1 - SHOULDER) * u * (1 + u / (l * l)) / (1 + u);
    }
}
