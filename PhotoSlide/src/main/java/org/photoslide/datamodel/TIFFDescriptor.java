/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import com.sun.javafx.iio.common.ImageDescriptor;

/**
 *
 * @author selfemp
 */
public class TIFFDescriptor extends ImageDescriptor {

    private static final String formatName = "TIFF";

    private static final String[] extensions = {"tif", "tiff"};

    private static final Signature[] signatures = {
        new Signature("<svg".getBytes()), new Signature("<?xml".getBytes())};

    private static ImageDescriptor theInstance = null;

    private TIFFDescriptor() {
        super(formatName, extensions, signatures);
    }

    public static synchronized ImageDescriptor getInstance() {
        if (theInstance == null) {
            theInstance = new TIFFDescriptor();
        }
        return theInstance;
    }
}
