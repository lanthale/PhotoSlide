package org.photoslide.datamodel.customformats.tiffsupport;

import com.sun.javafx.iio.ImageFormatDescription;
import com.sun.javafx.iio.ImageLoader;
import com.sun.javafx.iio.ImageLoaderFactory;
import com.sun.javafx.iio.ImageStorage;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registriert den TIFF-Loader im JavaFX-Imagesystem.
 *
 * @author selfemp
 */
public class TIFFImageLoaderFactory implements ImageLoaderFactory {

    private static final TIFFImageLoaderFactory INSTANCE = new TIFFImageLoaderFactory();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private TIFFImageLoaderFactory() {
    }

    /** Einmalig beim Start aufrufen, bevor das erste TIFF geladen wird. */
    public static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            ImageStorage.getInstance().addImageLoaderFactory(INSTANCE);
        }
    }

    public static TIFFImageLoaderFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public ImageFormatDescription getFormatDescription() {
        return TIFFDescriptor.getInstance();
    }

    @Override
    public ImageLoader createImageLoader(InputStream input) throws IOException {
        return new TIFFImageLoader(input);
    }
}