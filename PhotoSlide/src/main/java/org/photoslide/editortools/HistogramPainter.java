package org.photoslide.editortools;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.Color;

/**
 * Draws a Histogram like Lightroom and LightZone: red, green and blue as
 * filled areas added on top of each other (where they overlap the colours
 * mix, all three give a light grey), the luminance as a light line, and two
 * corner marks that light up when shadows (left, blue) or highlights (right,
 * red) are clipped.
 */
final class HistogramPainter {

    private static final Color BACKGROUND = Color.web("#2b2b2b");
    private static final Color GRID = Color.web("#3d3d3d");
    // each column sums to 140: all three added on the background give a neutral grey,
    // two give the mixed colour
    private static final Color RED = Color.rgb(100, 20, 20);
    private static final Color GREEN = Color.rgb(20, 90, 20);
    private static final Color BLUE = Color.rgb(20, 30, 100);
    private static final Color LUMINANCE = Color.web("#d8d8d8");
    /** Clipping above this share of the pixels is shown. */
    private static final double CLIP_WARNING = 0.001;
    private static final double MARK = 7;

    private HistogramPainter() {
    }

    static void draw(GraphicsContext gc, Histogram h, double width, double height) {
        gc.setGlobalAlpha(1);
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, width, height);
        gc.setStroke(GRID);
        gc.setLineWidth(1);
        for (int i = 1; i < 4; i++) {
            double x = Math.round(width * i / 4) + 0.5;
            gc.strokeLine(x, 0, x, height);
        }
        if (h == null || h.samples() == 0) {
            return;
        }
        double top = MARK + 2;
        double scale = (height - top) / h.scale();
        gc.setGlobalBlendMode(BlendMode.ADD);
        area(gc, h.red(), RED, width, height, scale);
        area(gc, h.green(), GREEN, width, height, scale);
        area(gc, h.blue(), BLUE, width, height, scale);
        gc.setGlobalBlendMode(BlendMode.SRC_OVER);
        // luminance: light line with a dark edge, visible on dark and light areas
        int[] lum = h.luminance();
        for (int pass = 0; pass < 2; pass++) {
            gc.setStroke(pass == 0 ? Color.color(0, 0, 0, 0.6) : LUMINANCE);
            gc.setLineWidth(pass == 0 ? 3 : 1);
            gc.beginPath();
            for (int i = 0; i < Histogram.LEVELS; i++) {
                double x = x(i, width);
                double y = height - Math.min(height - top, lum[i] * scale);
                if (i == 0) {
                    gc.moveTo(x, y);
                } else {
                    gc.lineTo(x, y);
                }
            }
            gc.stroke();
        }
        gc.setLineWidth(1);
        mark(gc, 1, 1, true, h.shadowsClipped() > CLIP_WARNING ? Color.web("#4d8dff") : null);
        mark(gc, width - 1, 1, false, h.highlightsClipped() > CLIP_WARNING ? Color.web("#ff5a5a") : null);
    }

    private static double x(int level, double width) {
        return level * (width - 1) / (Histogram.LEVELS - 1);
    }

    private static void area(GraphicsContext gc, int[] counts, Color color, double width, double height, double scale) {
        double top = MARK + 2;
        gc.setFill(color);
        gc.beginPath();
        gc.moveTo(0, height);
        for (int i = 0; i < counts.length; i++) {
            gc.lineTo(x(i, width), height - Math.min(height - top, counts[i] * scale));
        }
        gc.lineTo(width - 1, height);
        gc.closePath();
        gc.fill();
    }

    /** Small triangle in a top corner: filled = clipped, outlined = fine. */
    private static void mark(GraphicsContext gc, double x, double y, boolean left, Color clipped) {
        double dx = left ? MARK : -MARK;
        double[] xs = {x, x + dx, x};
        double[] ys = {y, y, y + MARK};
        if (clipped != null) {
            gc.setFill(clipped);
            gc.fillPolygon(xs, ys, 3);
        } else {
            gc.setStroke(Color.web("#6a6a6a"));
            gc.strokePolygon(xs, ys, 3);
        }
    }
}
