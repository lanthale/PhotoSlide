package org.photoslide.imageops.mask;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * One closed outline of a region, in pixels of the original image.
 *
 * @param points outline points (at least 3)
 * @param feather width of the soft edge in pixels of the original
 * @param smooth true = smooth curve through the points, false = polygon
 * @param sourceX for clone and spot: centre of the area copied into this
 * outline, NaN if not used
 * @param sourceY see sourceX
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Contour(List<Point> points, float feather, boolean smooth, double sourceX, double sourceY) {

    public record Point(double x, double y) {
    }

    public Contour {
        points = List.copyOf(points);
    }

    public boolean hasSource() {
        return !Double.isNaN(sourceX) && !Double.isNaN(sourceY);
    }

    public Contour withFeather(float f) {
        return new Contour(points, f, smooth, sourceX, sourceY);
    }

    public Contour withSource(double x, double y) {
        return new Contour(points, feather, smooth, x, y);
    }

    /** Centre of the bounding box (target of a clone). */
    public Point center() {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Point p : points) {
            minX = Math.min(minX, p.x());
            minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        return new Point((minX + maxX) / 2, (minY + maxY) / 2);
    }

    /** A circle (for spots), approximated by 24 points on a smooth curve. */
    public static Contour circle(double cx, double cy, double radius, float feather) {
        Point[] pts = new Point[24];
        for (int i = 0; i < pts.length; i++) {
            double a = 2 * Math.PI * i / pts.length;
            pts[i] = new Point(cx + radius * Math.cos(a), cy + radius * Math.sin(a));
        }
        return new Contour(List.of(pts), feather, true, Double.NaN, Double.NaN);
    }

    /**
     * Outline as a Java2D shape, transformed with x' = x * scale - offsetX
     * (smooth = closed Catmull-Rom spline through the points).
     */
    public Path2D.Double shape(double scale, double offsetX, double offsetY) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_NON_ZERO);
        int n = points.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = points.get(i).x() * scale - offsetX;
            ys[i] = points.get(i).y() * scale - offsetY;
        }
        path.moveTo(xs[0], ys[0]);
        for (int i = 0; i < n; i++) {
            int i1 = (i + 1) % n;
            if (!smooth || n < 3) {
                path.lineTo(xs[i1], ys[i1]);
                continue;
            }
            int i0 = (i - 1 + n) % n;
            int i2 = (i + 2) % n;
            path.curveTo(
                    xs[i] + (xs[i1] - xs[i0]) / 6, ys[i] + (ys[i1] - ys[i0]) / 6,
                    xs[i1] - (xs[i2] - xs[i]) / 6, ys[i1] - (ys[i2] - ys[i]) / 6,
                    xs[i1], ys[i1]);
        }
        path.closePath();
        return path;
    }
}
