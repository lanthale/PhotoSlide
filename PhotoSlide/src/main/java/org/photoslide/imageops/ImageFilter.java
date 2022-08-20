/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.imageops;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

/**
 * This interface declares the basic methods which are used to apply the filter. The filter 
 * itself is stored at the @MediaFile class and will be saved in the property's file
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
     * @param values filter with the given values
     */
    public void filter(float[] values);
    
    /**
     *
     * @param img Image to be loaded into an byte array
     * @return the WriteableImage which should be used in the @ImageView
     */
    public Image load(Image img);
    
    /**
     *
     * @return the original Image to reset the filter as Image object
     */
    public Image reset();
    
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
