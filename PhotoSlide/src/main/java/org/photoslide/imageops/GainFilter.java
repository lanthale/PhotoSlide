package org.photoslide.imageops;

import java.util.List;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.imageops.lz.LuminanceRatioFilter16;

/**
 * Contrast (gain) and brightness of the midtones (bias) on the luminance.
 * The curve works on a perceptual scale (log, like LightZone's zones) with
 * middle grey (18 %) as pivot; colours keep their hue. 0.5 / 0.5 has no
 * effect.
 *
 * @author selfemp
 */
public class GainFilter extends AbstractImageFilter {

    public static final String NAME = "GainFilter";

    private static final double PIVOT = perceptual(0.18);

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.slider("gain", "Contrast", 0, 0f, 1f, 0.5f, 0.01f),
            ParamSpec.slider("bias", "Brightness", 1, 0f, 1f, 0.5f, 0.01f));

    public GainFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Contrast / Brightness";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        final float gain = clamp(value(0));
        final float bias = clamp(value(1));
        if (gain == 0.5f && bias == 0.5f) {
            return null;
        }
        return new LuminanceRatioFilter16("Contrast " + gain + "/" + bias,
                LuminanceRatioFilter16.ratiosFor(y -> linear(tone(perceptual(y), gain, bias))));
    }

    /** Pivot to 0.5, Perlin gain (contrast) and bias (midtones), back. */
    static double tone(double u, float gain, float bias) {
        float v = (float) (u <= PIVOT ? 0.5 * u / PIVOT : 0.5 + 0.5 * (u - PIVOT) / (1 - PIVOT));
        v = ImageMath.bias(ImageMath.gain(v, gain), bias);
        return v <= 0.5f ? 2 * v * PIVOT : PIVOT + (v - 0.5) * 2 * (1 - PIVOT);
    }

    /** linear luminance 0..1 -> perceptual 0..1 (8 stops, LightZone zones) */
    static double perceptual(double y) {
        return Math.log(255 * y + 1) / Math.log(2) / 8;
    }

    static double linear(double u) {
        return (Math.pow(2, 8 * u) - 1) / 255;
    }

    private static float clamp(float v) {
        return Math.max(0.01f, Math.min(0.99f, v));
    }
}
