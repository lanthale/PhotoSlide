package org.photoslide.imageops;

/**
 * A tool that takes a colour from the image (dropper), e.g. white balance.
 */
public interface Pickable {

    /** Text of the dropper button / tooltip. */
    String pickLabel();

    /**
     * Sets the tool from a picked colour.
     *
     * @param rgb average of a few pixels around the click, linear working
     * space 0..1, taken from the image BELOW this tool
     */
    void pick(double[] rgb);
}
