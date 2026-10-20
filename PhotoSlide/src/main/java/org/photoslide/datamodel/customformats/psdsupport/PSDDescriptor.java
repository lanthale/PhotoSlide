package org.photoslide.datamodel.customformats.psdsupport;

import com.sun.javafx.iio.common.ImageDescriptor;

/**
 * Descriptor für das Adobe-Photoshop-Format (PSD).
 *
 * @author selfemp
 */
public class PSDDescriptor extends ImageDescriptor {

    private static final String FORMAT_NAME = "PSD";

    private static final String[] EXTENSIONS = {"psd"};

    // "8BPS" = Photoshop-Dateiheader
    private static final Signature[] SIGNATURES = {
        new Signature(new byte[]{'8', 'B', 'P', 'S'})
    };

    private static final String[] MIME_SUBTYPES = {"vnd.adobe.photoshop", "x-photoshop", "psd"};

    private static final PSDDescriptor INSTANCE = new PSDDescriptor();

    private PSDDescriptor() {
        super(FORMAT_NAME, EXTENSIONS, SIGNATURES, MIME_SUBTYPES);
    }

    public static PSDDescriptor getInstance() {
        return INSTANCE;
    }
}