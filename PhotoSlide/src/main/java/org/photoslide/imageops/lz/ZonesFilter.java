package org.photoslide.imageops.lz;

import java.util.List;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.LutFilter16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;

/**
 * Zone mapper: 16 zones on a logarithmic axis (8 stops). Each zone can be
 * moved to a new brightness; a smooth curve (rational B-spline) through the
 * moved zones becomes the tone curve. Luminosity mode keeps the colours,
 * RGB mode applies the curve to each channel.
 *
 * Algorithm ported from LightZone (ZoneOperationImpl, LightnessLookupTable),
 * Copyright (c) 2005-2011 Fabio Riccardi, BSD-3-Clause, see
 * THIRD-PARTY-NOTICES.md.
 */
public class ZonesFilter extends AbstractImageFilter {

    public static final String NAME = "ZonesFilter";
    public static final int ZONES = 16;

    private static final double WEIGHT = 10.0;
    private static final int ORDER = 3;

    private static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.zones("zones", "Zones", 0, ZONES),
            ParamSpec.choice("mode", "Apply to", ZONES, 0, List.of("Luminosity", "RGB")));

    public ZonesFilter() {
        super(NAME);
    }

    @Override
    public String displayName() {
        return "Zones";
    }

    @Override
    public List<ParamSpec> params() {
        return PARAMS;
    }

    /** Position of zone i on the linear axis (0..1). */
    public static double zoneX(int i) {
        return (Math.pow(2, i * 8.0 / (ZONES - 1)) - 1) / 255.0;
    }

    /** Linear value of a zone value u (0..1 on the 8 stop axis). */
    public static double zoneY(double u) {
        return (Math.pow(2, u * 8.0) - 1) / 255.0;
    }

    /** Zone value (0..1) of linear luminance y. */
    public static double zoneOf(double y) {
        return Math.log(255 * y + 1) / Math.log(2) / 8;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        float[] v = getValues();
        boolean any = false;
        for (int i = 0; i < ZONES; i++) {
            any |= v[i] >= 0;
        }
        if (!any) {
            return null;
        }
        double[] curveTable = curve(v);
        if (value(ZONES) >= 0.5f) {
            short[] lut = new short[65536];
            for (int i = 0; i < 65536; i++) {
                lut[i] = Planes.to16((float) curveTable[i]);
            }
            return LutFilter16.of("Zones RGB", lut, lut, lut);
        }
        float[] ratio = new float[65536];
        for (int i = 1; i < 65536; i++) {
            ratio[i] = (float) (curveTable[i] / (i / 65535.0));
        }
        ratio[0] = ratio[1];
        return new LuminanceRatioFilter16("Zones", ratio);
    }

    /** Tone curve (65536 entries, linear in, linear out) of the zone values. */
    public static double[] curve(float[] v) {
        double[] points = new double[ZONES];
        for (int i = 0; i < ZONES; i++) {
            points[i] = v[i];
        }
        if (points[0] < 0) {
            points[0] = 0;
        }
        if (points[ZONES - 1] < 0) {
            points[ZONES - 1] = 1;
        }
        int n = 0;
        for (double p : points) {
            if (p >= 0) {
                n++;
            }
        }
        double[][] control = new double[n][2];
        n = 0;
        for (int i = 0; i < ZONES; i++) {
            if (points[i] >= 0) {
                control[n][0] = zoneX(i);
                control[n][1] = zoneY(points[i]);
                n++;
            }
        }
        double[] table = new double[65536];
        if (control.length > 2) {
            double[] weights = new double[control.length];
            java.util.Arrays.fill(weights, WEIGHT);
            weights[0] = weights[control.length - 1] = 1.0;
            double[][] curve = new double[256][2];
            Splines.rbspline(ORDER, control, weights, curve);
            Splines.Interpolator in = new Splines.Interpolator();
            for (int i = 0; i < 65536; i++) {
                table[i] = in.interpolate(i / 65535.0, curve);
            }
        } else {
            for (int i = 0; i < 65536; i++) {
                double x = i / 65535.0;
                table[i] = control[0][1] + x * (control[1][1] - control[0][1]);
            }
        }
        return table;
    }
}
