/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.imageops;

import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.RenderContext;

/**
 * Settings of one edit step (a tool in the editor stack). The filter is
 * stored at the @MediaFile class and saved in its XMP sidecar. It holds no
 * image data: the pixels are processed by the Filter16 returned from
 * toFilter16(), see org.photoslide.editpipeline.EditPipeline. All tools extend AbstractImageFilter.
 *
 * @author selfemp
 */
public interface ImageFilter extends Cloneable {

    /**
     *
     * @return the name of the filter. Must always return the same name because
     * this is used to save the values and it must match the class name
     */
    public String getName();

    /**
     *
     * @return get actual values for that filter
     */
    public float[] getValues();

    /**
     *
     * @param values set the required values for the filter
     */
    public void setValues(float[] values);

    /**
     * @param ctx size of the rendered image relative to the original
     * @return the 16 bit operation (already blended with opacity and mode),
     * or null if the filter is inactive or has no effect
     */
    public Filter16 toFilter16(RenderContext ctx);

    /**
     *
     * @param val set the position
     */
    public void setPosition(int val);

    /**
     *
     * @return the position for stacking the filter on top of others
     */
    public int getPosition();

    public boolean isActive();

    public void setActive(boolean active);

    /** @return 0..1 */
    public float getOpacity();

    public void setOpacity(float opacity);

    public BlendMode getBlendMode();

    public void setBlendMode(BlendMode mode);

    public Object clone() throws CloneNotSupportedException;

}
