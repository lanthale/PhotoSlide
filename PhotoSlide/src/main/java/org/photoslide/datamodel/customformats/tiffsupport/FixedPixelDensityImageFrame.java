/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel.customformats.tiffsupport;

import com.sun.javafx.iio.ImageFrame;
import com.sun.javafx.iio.ImageMetadata;
import com.sun.javafx.iio.ImageStorage.ImageType;
import java.nio.ByteBuffer;

/**
 *
 * @author selfemp
 */
public class FixedPixelDensityImageFrame extends ImageFrame {

    public FixedPixelDensityImageFrame(ImageType imageType, ByteBuffer imageData, int width, int height, int stride,
            byte[][] palette, float pixelScale, ImageMetadata metadata) {
        super(imageType, imageData, width, height, stride, palette, pixelScale, metadata);
    }

    @Override
    public void setPixelScale(float pixelScale) {
        // Prevent ImageStorage class from overwriting the pixel density
    }
}
