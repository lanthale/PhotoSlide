/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel.customformats.webpsupport;

import com.sun.javafx.iio.ImageFormatDescription;
import com.sun.javafx.iio.common.ImageDescriptor;

public class WEBPDescriptor extends ImageDescriptor {

    private static final String formatName = "WEBP";
    private static final String[] extensions = {"webp"};
    private static final String[] mimeSubtypes = {"webp"};

    // WebP ist ein RIFF-Container: "RIFF" (0-3) + Dateigroesse (4-7, variabel)
    // + "WEBP" (8-11). ImageFormatDescription.Signature kann nur ab Offset 0
    // matchen, daher ist "RIFF" die maximal moegliche Signatur. Der Loader muss
    // zusaetzlich Bytes 8-11 auf "WEBP" pruefen (siehe isWEBPStream()),
    // sonst wuerden auch WAV/AVI-Dateien erkannt.
    private static final Signature[] signatures = {
        new ImageFormatDescription.Signature(
        hexStringToByteArray("52494646")) // "RIFF"
    };

    private static volatile WEBPDescriptor theInstance;    

    private WEBPDescriptor() {
        super(formatName, extensions, signatures, mimeSubtypes);
    }

    public static WEBPDescriptor getInstance() {
        WEBPDescriptor result = theInstance;
        if (result == null) {
            synchronized (WEBPDescriptor.class) {
                result = theInstance;
                if (result == null) {
                    theInstance = result = new WEBPDescriptor();
                }
            }
        }
        return result;
    }

    /**
     * Vollstaendige Pruefung des RIFF-Headers, muss im Loader erfolgen, da die
     * Signatur nur "RIFF" ab Offset 0 abdecken kann.
     */
    public static boolean isWEBPStream(byte[] header) {
        if (header == null || header.length < 12) {
            return false;
        }
        return "RIFF".charAt(0) == (char) (header[0] & 0xFF)
                && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46 // "RIFF"
                && header[8] == 0x57 && header[9] == 0x45 // "WE"
                && header[10] == 0x42 && header[11] == 0x50; // "BP"
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
