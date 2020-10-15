/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel.tiffsupport;

import com.sun.javafx.iio.ImageFormatDescription;
import com.sun.javafx.iio.ImageLoader;
import com.sun.javafx.iio.ImageLoaderFactory;
import com.sun.javafx.iio.ImageStorage;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author selfemp
 */
public class TIFFImageLoaderFactory implements ImageLoaderFactory {

    private static final TIFFImageLoaderFactory instance = new TIFFImageLoaderFactory();
    

    public static final void install() {
        ImageStorage.addImageLoaderFactory(instance);
    }
    
    public static final ImageLoaderFactory getInstance() {
		return instance;
	}

    @Override
    public ImageFormatDescription getFormatDescription() {
        return TIFFDescriptor.getInstance();
    }

    @Override
    public ImageLoader createImageLoader(InputStream in) throws IOException {
        return new TIFFImageLoader(in);
    }

}
