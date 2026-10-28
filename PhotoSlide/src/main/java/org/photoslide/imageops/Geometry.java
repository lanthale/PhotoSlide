package org.photoslide.imageops;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;

/**
 * Flip and straighten of a media file: applied after all tools and keeps the
 * size of the image, so the crop (in pixels of this result) and the quarter
 * turns (rotation of the view) work as before. Straighten rotates around the
 * centre and enlarges just enough that no empty corners are shown (like
 * LightZone's rotate mode).
 *
 * @param flipH mirrored left/right
 * @param flipV mirrored top/bottom
 * @param straighten degrees, positive = clockwise, -45..45
 */
public record Geometry(boolean flipH, boolean flipV, double straighten) {

    public static final Geometry NONE = new Geometry(false, false, 0);
    public static final double MAX_STRAIGHTEN = 45;

    public Geometry {
        straighten = Math.max(-MAX_STRAIGHTEN, Math.min(MAX_STRAIGHTEN, straighten));
    }

    public boolean isIdentity() {
        return !flipH && !flipV && straighten == 0;
    }

    public Geometry withFlipH(boolean v) {
        return new Geometry(v, flipV, straighten);
    }

    public Geometry withFlipV(boolean v) {
        return new Geometry(flipH, v, straighten);
    }

    public Geometry withStraighten(double degrees) {
        return new Geometry(flipH, flipV, degrees);
    }

    /** Enlargement that hides the empty corners of a w x h image turned by the angle. */
    public static double fillScale(double degrees, double w, double h) {
        double a = Math.toRadians(Math.abs(degrees));
        double ratio = Math.max(w / h, h / w);
        return Math.cos(a) + ratio * Math.sin(a);
    }

    /**
     * Maps a pixel of the original (w x h) to the pixel of the result (same
     * size).
     */
    public AffineTransform transform(double w, double h) {
        AffineTransform t = new AffineTransform();
        if (isIdentity()) {
            return t;
        }
        double s = fillScale(straighten, w, h);
        t.translate(w / 2, h / 2);
        t.rotate(Math.toRadians(straighten));
        t.scale(s * (flipH ? -1 : 1), s * (flipV ? -1 : 1));
        t.translate(-w / 2, -h / 2);
        return t;
    }

    /** The 16 bit operation (same size, bilinear), or null for no change. */
    public Filter16 toFilter16() {
        if (isIdentity()) {
            return null;
        }
        return new Filter16() {
            @Override
            public void apply(Image16 src, Image16 dst, int yStart, int yEnd) {
                final int w = src.width();
                final int h = src.height();
                AffineTransform inv;
                try {
                    inv = transform(w, h).createInverse();
                } catch (NoninvertibleTransformException ex) {
                    throw new IllegalStateException(ex);
                }
                final double m00 = inv.getScaleX(), m01 = inv.getShearX(), m02 = inv.getTranslateX();
                final double m10 = inv.getShearY(), m11 = inv.getScaleY(), m12 = inv.getTranslateY();
                final short[] s = src.data();
                final short[] d = dst.data();
                for (int y = yStart; y < yEnd; y++) {
                    double cy = y + 0.5;
                    for (int x = 0, o = y * w * 3; x < w; x++, o += 3) {
                        double cx = x + 0.5;
                        double sx = m00 * cx + m01 * cy + m02 - 0.5;
                        double sy = m10 * cx + m11 * cy + m12 - 0.5;
                        sample(s, w, h, sx, sy, d, o);
                    }
                }
            }

            @Override
            public String name() {
                return "Geometry";
            }
        };
    }

    /** Bilinear sample at (x, y), edges repeated. */
    private static void sample(short[] s, int w, int h, double x, double y, short[] d, int o) {
        x = Math.max(0, Math.min(w - 1, x));
        y = Math.max(0, Math.min(h - 1, y));
        int x0 = (int) x;
        int y0 = (int) y;
        int x1 = Math.min(w - 1, x0 + 1);
        int y1 = Math.min(h - 1, y0 + 1);
        double fx = x - x0;
        double fy = y - y0;
        int i00 = (y0 * w + x0) * 3, i10 = (y0 * w + x1) * 3, i01 = (y1 * w + x0) * 3, i11 = (y1 * w + x1) * 3;
        for (int c = 0; c < 3; c++) {
            double top = (s[i00 + c] & 0xFFFF) * (1 - fx) + (s[i10 + c] & 0xFFFF) * fx;
            double bottom = (s[i01 + c] & 0xFFFF) * (1 - fx) + (s[i11 + c] & 0xFFFF) * fx;
            d[o + c] = (short) (int) (top * (1 - fy) + bottom * fy + 0.5);
        }
    }
}
