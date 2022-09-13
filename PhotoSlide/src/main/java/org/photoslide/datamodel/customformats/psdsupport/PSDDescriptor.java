/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel.customformats.psdsupport;

import org.photoslide.datamodel.customformats.tiffsupport.*;
import com.sun.javafx.iio.common.ImageDescriptor;

/**
 *
 * @author selfemp
 */
public class PSDDescriptor extends ImageDescriptor {

    private static final String formatName = "PSD";

    private static final String[] extensions = {"psd"};

    private static final Signature[] signatures = {
        new Signature(hexStringToByteArray("38425053"))};
    
    private static final String[] mimesubtype = {"psd"};

    private static ImageDescriptor theInstance = null;

    private PSDDescriptor() {
        super(formatName, extensions, signatures, mimesubtype);
    }

    public static synchronized ImageDescriptor getInstance() {
        if (theInstance == null) {
            theInstance = new PSDDescriptor();
        }
        return theInstance;
    }

    public static byte[] hexStringToByteArray(String input) {
        int len = input.length();

        if (len == 0) {
            return new byte[]{};
        }

        byte[] data;
        int startIdx;
        if (len % 2 != 0) {
            data = new byte[(len / 2) + 1];
            data[0] = (byte) Character.digit(input.charAt(0), 16);
            startIdx = 1;
        } else {
            data = new byte[len / 2];
            startIdx = 0;
        }

        for (int i = startIdx; i < len; i += 2) {
            data[(i + 1) / 2] = (byte) ((Character.digit(input.charAt(i), 16) << 4)
                    + Character.digit(input.charAt(i + 1), 16));
        }
        return data;
    }
}
