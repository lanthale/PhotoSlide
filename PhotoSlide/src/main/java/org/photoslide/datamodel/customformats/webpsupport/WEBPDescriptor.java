package org.photoslide.datamodel.customformats.webpsupport;

import com.sun.javafx.iio.ImageFormatDescription;
import com.sun.javafx.iio.common.ImageDescriptor;

/**
 * Format description for WebP, used by the JavaFX ImageStorage to select the
 * {@link WEBPImageLoader}.
 * <p>
 * WebP is a RIFF container: "RIFF" (bytes 0-3) + file size (bytes 4-7,
 * variable) + "WEBP" (bytes 8-11). {@link ImageFormatDescription.Signature}
 * can only match a fixed byte sequence at offset 0 (no wildcards), so "RIFF"
 * is the longest possible signature. Other RIFF containers (WAV, AVI) are
 * therefore also routed to the loader, which must reject them using
 * {@link #isWEBPStream(byte[])}.
 */
public class WEBPDescriptor extends ImageDescriptor {

    private static final String FORMAT_NAME = "WEBP";
    private static final String[] EXTENSIONS = {"webp"};
    private static final String[] MIME_SUBTYPES = {"webp"};

    private static final int HEADER_SIZE = 12;

    // "RIFF"
    private static final byte[] RIFF = {0x52, 0x49, 0x46, 0x46};
    // "WEBP"
    private static final byte[] WEBP = {0x57, 0x45, 0x42, 0x50};

    private static final Signature[] SIGNATURES = {
        new ImageFormatDescription.Signature(RIFF)
    };

    private static volatile WEBPDescriptor theInstance;

    private WEBPDescriptor() {
        super(FORMAT_NAME, EXTENSIONS, SIGNATURES, MIME_SUBTYPES);
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
     * Full check of the RIFF header: "RIFF" at bytes 0-3 and "WEBP" at bytes
     * 8-11. Must be done by the loader because the signature can only cover
     * "RIFF" at offset 0.
     *
     * @param header at least the first 12 bytes of the stream
     * @return true if the header is a WebP header
     */
    public static boolean isWEBPStream(byte[] header) {
        if (header == null || header.length < HEADER_SIZE) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (header[i] != RIFF[i] || header[8 + i] != WEBP[i]) {
                return false;
            }
        }
        return true;
    }
}
