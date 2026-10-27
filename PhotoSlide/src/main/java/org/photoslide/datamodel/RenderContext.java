package org.photoslide.datamodel;

/**
 * Information a filter needs about the image it is rendered for.
 *
 * @param scale size of the rendered image relative to the original (1 = full
 * size, 0.25 = preview at a quarter). Radii and other sizes in pixels are
 * given for the original and must be multiplied with it, so previews and the
 * export look the same.
 * @param offsetX position of the rendered image's left edge, in rendered
 * pixels (not 0 when only a part is rendered, e.g. for a dropper)
 * @param offsetY see offsetX
 */
public record RenderContext(double scale, double offsetX, double offsetY) {

    public static final RenderContext FULL = new RenderContext(1.0);

    public RenderContext(double scale) {
        this(scale, 0, 0);
    }

    public RenderContext {
        if (!(scale > 0)) {
            throw new IllegalArgumentException("scale must be > 0: " + scale);
        }
    }

    /** A size in pixels of the original, scaled to the rendered image. */
    public double scaled(double pixels) {
        return pixels * scale;
    }
}
