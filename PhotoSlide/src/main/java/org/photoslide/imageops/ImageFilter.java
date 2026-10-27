/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.imageops;

import org.photoslide.datamodel.Filter16;

/**
 * Settings of one edit step. The filter is stored at the @MediaFile class and
 * will be saved in the property's file. It holds no image data: the pixels are
 * processed by the Filter16 returned from toFilter16(), see EditPipeline.
 *
 * @author selfemp
 */
public interface ImageFilter extends Cloneable {

    /**
     *
     * @return the name of the filter. Must always return the same name because this is used to
     * save the values and it must match the class name
     * to the property's file of the mediafile
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
     *
     * @return the 16 bit operation for the actual values, or null if the
     * filter has no effect with these values (it is skipped then)
     */
    public Filter16 toFilter16();

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

    public Object clone() throws CloneNotSupportedException;

}
