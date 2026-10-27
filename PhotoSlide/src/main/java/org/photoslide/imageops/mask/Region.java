package org.photoslide.imageops.mask;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;
import java.awt.Rectangle;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.imageops.lz.Gauss;

/**
 * The area a tool is applied to: one or more outlines with soft edges.
 * Several outlines add up (alpha over); inverted = everything outside.
 *
 * Feathering follows LightZone (ShapedMask, LCROIShape): the filled outline
 * is blurred with a Gaussian of half the feather width; outlines combine as
 * a = a1 * (1 - a2) + a2. Copyright (c) 2005-2011 Fabio Riccardi,
 * BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Region(List<Contour> contours, boolean inverted) {

    public static final Region EMPTY = new Region(List.of(), false);

    public Region {
        contours = List.copyOf(contours);
    }

    public boolean isEmpty() {
        return contours.isEmpty();
    }

    public Region with(Contour c) {
        List<Contour> l = new ArrayList<>(contours);
        l.add(c);
        return new Region(l, inverted);
    }

    public Region withContours(List<Contour> l) {
        return new Region(l, inverted);
    }

    public Region withInverted(boolean inv) {
        return new Region(contours, inv);
    }

    /**
     * Mask (0..1, width * height) of all outlines for an image rendered with
     * the given context.
     */
    public float[] rasterize(int width, int height, RenderContext ctx) {
        float[] mask = new float[width * height];
        for (Contour c : contours) {
            float[] m = rasterize(c, width, height, ctx);
            for (int i = 0; i < mask.length; i++) {
                mask[i] = mask[i] * (1 - m[i]) + m[i];
            }
        }
        if (inverted) {
            for (int i = 0; i < mask.length; i++) {
                mask[i] = 1 - mask[i];
            }
        }
        return mask;
    }

    /** Mask (0..1, width * height) of one outline with its soft edge. */
    public static float[] rasterize(Contour c, int width, int height, RenderContext ctx) {
        float[] mask = new float[width * height];
        if (c.points().size() < 3) {
            return mask;
        }
        double sigma = ctx.scaled(c.feather()) / 2;
        int margin = (int) Math.ceil(3 * sigma) + 2;
        java.awt.geom.Path2D shape = c.shape(ctx.scale(), ctx.offsetX(), ctx.offsetY());
        Rectangle b = shape.getBounds();
        int x0 = Math.max(0, b.x - margin);
        int y0 = Math.max(0, b.y - margin);
        int x1 = Math.min(width, b.x + b.width + margin + 1);
        int y1 = Math.min(height, b.y + b.height + margin + 1);
        if (x1 <= x0 || y1 <= y0) {
            return mask;
        }
        int w = x1 - x0;
        int h = y1 - y0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(-x0, -y0);
        g.setColor(java.awt.Color.WHITE);
        g.fill(shape);
        g.dispose();
        byte[] px = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        float[] local = new float[w * h];
        for (int i = 0; i < local.length; i++) {
            local[i] = (px[i] & 0xFF) / 255f;
        }
        if (sigma >= 0.3) {
            local = Gauss.blur(local, w, h, sigma);
        }
        for (int y = 0; y < h; y++) {
            System.arraycopy(local, y * w, mask, (y + y0) * width + x0, w);
        }
        return mask;
    }
}
