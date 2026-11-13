package org.photoslide.faces;

import org.photoslide.datamodel.Display16;
import org.photoslide.datamodel.Image16;

/**
 * An 8 bit sRGB image (packed 0xRRGGBB) as the face models expect it: the
 * working image (16 bit linear ProPhoto) converted like for the screen.
 */
record Rgb8(int width, int height, int[] pixels) {

    /** Scaled so that the long side is at most maxSide (never enlarged). */
    static Rgb8 of(Image16 img, int maxSide) {
        double s = Math.min(1.0, (double) maxSide / Math.max(img.width(), img.height()));
        Image16 small = s < 1 ? img.scaledTo(Math.max(1, (int) Math.round(img.width() * s)),
                Math.max(1, (int) Math.round(img.height() * s))) : img;
        return new Rgb8(small.width(), small.height(), Display16.toArgb(small, false));
    }

    /** Turned clockwise by quarters (0..3). */
    Rgb8 rotated(int quarters) {
        int q = Math.floorMod(quarters, 4);
        if (q == 0) {
            return this;
        }
        int w = width;
        int h = height;
        int ow = q % 2 == 1 ? h : w;
        int oh = q % 2 == 1 ? w : h;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int nx;
                int ny;
                switch (q) {
                    case 1 -> {
                        nx = h - 1 - y;
                        ny = x;
                    }
                    case 2 -> {
                        nx = w - 1 - x;
                        ny = h - 1 - y;
                    }
                    default -> {
                        nx = y;
                        ny = w - 1 - x;
                    }
                }
                out[ny * ow + nx] = pixels[y * w + x];
            }
        }
        return new Rgb8(ow, oh, out);
    }

    /** Bilinear sample of one channel (0 = red, 1 = green, 2 = blue), 0 outside. */
    float sample(double x, double y, int channel) {
        int shift = 16 - channel * 8;
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = x - x0;
        double fy = y - y0;
        double v = (1 - fx) * (1 - fy) * at(x0, y0, shift) + fx * (1 - fy) * at(x0 + 1, y0, shift)
                + (1 - fx) * fy * at(x0, y0 + 1, shift) + fx * fy * at(x0 + 1, y0 + 1, shift);
        return (float) v;
    }

    private int at(int x, int y, int shift) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return 0;
        }
        return (pixels[y * width + x] >> shift) & 0xFF;
    }
}
