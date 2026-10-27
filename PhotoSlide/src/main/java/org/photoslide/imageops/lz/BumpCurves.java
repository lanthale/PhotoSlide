package org.photoslide.imageops.lz;

import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.LutFilter16;

/**
 * Colour offsets that are strongest at one brightness and fade to zero at
 * black and white (quadratic B-spline "bump"), per channel. Shared by colour
 * balance, colour cast and the tint of the white balance.
 *
 * Algorithm ported from LightZone (ColorBalanceOperationV2,
 * WhitePointOperationImpl, WhiteBalanceV2.tintCast), Copyright (c)
 * 2005-2011 Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
final class BumpCurves {

    private BumpCurves() {
    }

    /**
     * @param center brightness (0..1, linear) where the offsets are applied fully
     * @param offsets per channel offset in LightZone units (1 = 255 of 65535)
     */
    static short[][] tables(double center, double[] offsets) {
        short[][] t = new short[3][];
        for (int c = 0; c < 3; c++) {
            double[] bump = Splines.bump(center, offsets[c]);
            short[] lut = new short[65536];
            for (int i = 0; i < 65536; i++) {
                double v = i + 0xff * bump[i];
                lut[i] = (short) (int) Math.min(Math.max(v, 0), 0xffff);
            }
            t[c] = lut;
        }
        return t;
    }

    static Filter16 filter(String name, double center, double[] offsets) {
        short[][] t = tables(center, offsets);
        return LutFilter16.of(name, t[0], t[1], t[2]);
    }

    /**
     * Offsets that push towards a colour: each channel half of itself minus a
     * quarter of each other channel (LightZone colour balance).
     *
     * @param p colour, 0..65535 per channel
     */
    static double[] towards(double[] p) {
        return new double[]{
            (p[0] / 2 - p[2] / 4 - p[1] / 4) / 256.0,
            (p[1] / 2 - p[0] / 4 - p[2] / 4) / 256.0,
            (p[2] / 2 - p[0] / 4 - p[1] / 4) / 256.0
        };
    }
}
