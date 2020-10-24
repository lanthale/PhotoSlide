/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel.customformats.psdsupport;

import com.sun.javafx.iio.ImageFrame;
import com.sun.javafx.iio.ImageStorage;
import com.sun.javafx.iio.common.ImageLoaderImpl;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javafx.stage.Screen;
import javax.imageio.ImageIO;
import org.photoslide.datamodel.customformats.dimension.Dimension;
import org.photoslide.datamodel.customformats.dimension.DimensionProvider;

/**
 * Class to load the image requested by the file
 * @author selfemp
 */
public class PSDImageLoader extends ImageLoaderImpl {

    private static final int BYTES_PER_PIXEL = 4; // RGBA

    private final InputStream input;
    private float maxPixelScale = 0;
    private final DimensionProvider dimensionProvider;

    protected PSDImageLoader(InputStream input, DimensionProvider dimensionProvider) {
        super(PSDDescriptor.getInstance());

        if (input == null) {
            throw new IllegalArgumentException("input == null!");
        }

        this.input = input;
        this.dimensionProvider = dimensionProvider;
    }

    @Override
    public void dispose() {
    }

    @Override
    public ImageFrame load(int imageIndex, int width, int height, boolean preserveAspectRatio, boolean smooth) throws IOException {
        if (0 != imageIndex) {
            return null;
        }

        Dimension fallbackDimension = (width <= 0 || height <= 0) ? dimensionProvider.getDimension() : null;

        float imageWidth = width > 0 ? width : (float) fallbackDimension.getWidth();
        float imageHeight = height > 0 ? height : (float) fallbackDimension.getHeight();

        try {
            return createImageFrame(imageWidth, imageHeight, getPixelScale());
        } catch (IOException ex) {
            throw new IOException(ex);
        }
    }

    
    public float getPixelScale() {
        if (maxPixelScale == 0) {
            maxPixelScale = calculateMaxRenderScale();
        }
        return maxPixelScale;
    }

    public float calculateMaxRenderScale() {
        float maxRenderScale = 0;
        ScreenHelper.ScreenAccessor accessor = ScreenHelper.getScreenAccessor();
        for (Screen screen : Screen.getScreens()) {
            maxRenderScale = Math.max(maxRenderScale, accessor.getRenderScale(screen));
        }
        return maxRenderScale;
    }

    private ImageFrame createImageFrame(float width, float height, float pixelScale)
            throws IOException {
        BufferedImage bufferedImage = getTranscodedImage(width * pixelScale, height * pixelScale);
        ByteBuffer imageData = getImageData(bufferedImage);

        return new FixedPixelDensityImageFrame(ImageStorage.ImageType.RGBA, imageData, bufferedImage.getWidth(),
                bufferedImage.getHeight(), getStride(bufferedImage), null, pixelScale, null);
    }

    private BufferedImage getTranscodedImage(float width, float height)
            throws IOException {

        return ImageIO.read(input);
    }

    private int getStride(BufferedImage bufferedImage) {
        return bufferedImage.getWidth() * BYTES_PER_PIXEL;
    }

    private ByteBuffer getImageData(BufferedImage bufferedImage) {
        int[] rgb = bufferedImage.getRGB(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), null, 0,
                bufferedImage.getWidth());

        byte[] imageData = new byte[getStride(bufferedImage) * bufferedImage.getHeight()];

        copyColorToBytes(rgb, imageData);
        return ByteBuffer.wrap(imageData);
    }

    private void copyColorToBytes(int[] rgb, byte[] imageData) {
        if (rgb.length * BYTES_PER_PIXEL != imageData.length) {
            throw new ArrayIndexOutOfBoundsException();
        }

        ByteBuffer byteBuffer = ByteBuffer.allocate(Integer.BYTES);

        for (int i = 0; i < rgb.length; i++) {
            byte[] bytes = byteBuffer.putInt(rgb[i]).array();

            int dataOffset = BYTES_PER_PIXEL * i;
            imageData[dataOffset] = bytes[1];
            imageData[dataOffset + 1] = bytes[2];
            imageData[dataOffset + 2] = bytes[3];
            imageData[dataOffset + 3] = bytes[0];

            byteBuffer.clear();
        }
    }

}
