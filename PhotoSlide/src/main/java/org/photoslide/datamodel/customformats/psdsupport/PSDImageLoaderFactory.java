/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel.customformats.psdsupport;

import com.sun.javafx.iio.ImageFormatDescription;
import com.sun.javafx.iio.ImageLoader;
import com.sun.javafx.iio.ImageLoaderFactory;
import com.sun.javafx.iio.ImageStorage;
import java.io.IOException;
import java.io.InputStream;
import org.photoslide.datamodel.customformats.dimension.DefaultDimensionProvider;
import org.photoslide.datamodel.customformats.dimension.DimensionProvider;

/**
 * Class to install the TIFF renderer to the javafx image systems
 * @author selfemp
 */
public class PSDImageLoaderFactory implements ImageLoaderFactory {

    private static final PSDImageLoaderFactory instance = new PSDImageLoaderFactory();

    private static DimensionProvider dimensionProvider;

    public static final void install() {
        install(new DefaultDimensionProvider());
    }

    public static final void install(DimensionProvider dimensionProvider) {
        PSDImageLoaderFactory.dimensionProvider = dimensionProvider;

        ImageStorage.addImageLoaderFactory(instance);
    }

    public static final ImageLoaderFactory getInstance() {
        return instance;
    }

    @Override
    public ImageFormatDescription getFormatDescription() {
        return PSDDescriptor.getInstance();
    }

    @Override
    public ImageLoader createImageLoader(InputStream input) throws IOException {
        return new PSDImageLoader(input, dimensionProvider);
    }

}
