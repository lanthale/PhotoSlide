package org.photoslide.datamodel.customformats.psdsupport;

import com.sun.javafx.iio.ImageFormatDescription;
import com.sun.javafx.iio.ImageLoader;
import com.sun.javafx.iio.ImageLoaderFactory;
import com.sun.javafx.iio.ImageStorage;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registriert den PSD-Loader im JavaFX-Imagesystem.
 *
 * @author selfemp
 */
public class PSDImageLoaderFactory implements ImageLoaderFactory {

    private static final PSDImageLoaderFactory INSTANCE = new PSDImageLoaderFactory();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private PSDImageLoaderFactory() {
    }

    public static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            ImageStorage.getInstance().addImageLoaderFactory(INSTANCE);
        }
    }

    public static PSDImageLoaderFactory getInstance() {
        return INSTANCE;
    }

    @Override
    public ImageFormatDescription getFormatDescription() {
        return PSDDescriptor.getInstance();
    }

    @Override
    public ImageLoader createImageLoader(InputStream input) throws IOException {
        return new PSDImageLoader(input);
    }
}
