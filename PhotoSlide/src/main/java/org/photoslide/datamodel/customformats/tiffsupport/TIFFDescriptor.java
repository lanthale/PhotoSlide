package org.photoslide.datamodel.customformats.tiffsupport;

import com.sun.javafx.iio.common.ImageDescriptor;

/**
 * Descriptor für TIFF und BigTIFF.
 *
 * @author selfemp
 */
public class TIFFDescriptor extends ImageDescriptor {

    private static final String FORMAT_NAME = "TIFF";

    private static final String[] EXTENSIONS = {"tif", "tiff"};

    private static final Signature[] SIGNATURES = {
        // TIFF, Little Endian ("II*\0")
        new Signature(new byte[]{0x49, 0x49, 0x2A, 0x00}),
        // TIFF, Big Endian ("MM\0*")
        new Signature(new byte[]{0x4D, 0x4D, 0x00, 0x2A}),
        // BigTIFF, Little Endian
        new Signature(new byte[]{0x49, 0x49, 0x2B, 0x00}),
        // BigTIFF, Big Endian
        new Signature(new byte[]{0x4D, 0x4D, 0x00, 0x2B}),
        // seltene Variante ("I I"), falls bewusst benötigt
        new Signature(new byte[]{0x49, 0x20, 0x49})
    };

    private static final String[] MIME_SUBTYPES = {"tiff", "x-tiff"};

    private static final TIFFDescriptor INSTANCE = new TIFFDescriptor();

    private TIFFDescriptor() {
        super(FORMAT_NAME, EXTENSIONS, SIGNATURES, MIME_SUBTYPES);
    }

    public static TIFFDescriptor getInstance() {
        return INSTANCE;
    }
}