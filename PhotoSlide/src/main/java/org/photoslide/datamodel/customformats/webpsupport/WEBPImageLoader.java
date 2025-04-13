/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel.customformats.webpsupport;

import com.sun.javafx.iio.ImageFrame;
import com.sun.javafx.iio.ImageMetadata;
import com.sun.javafx.iio.ImageStorage;
import com.sun.javafx.iio.common.ImageLoaderImpl;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.stage.Screen;
import javax.imageio.ImageIO;
import org.photoslide.datamodel.customformats.dimension.Dimension;
import org.photoslide.datamodel.customformats.dimension.DimensionProvider;

/**
 * Class to load the image requested by the file
 *
 * @author selfemp
 */
public class WEBPImageLoader extends ImageLoaderImpl {

    private static final int BYTES_PER_PIXEL = 4; // RGBA

    private final InputStream input;
    private float maxPixelScale = 0;
    private final DimensionProvider dimensionProvider;

    protected WEBPImageLoader(InputStream input, DimensionProvider dimensionProvider) {
        super(WEBPDescriptor.getInstance());

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
    protected void updateImageMetadata(ImageMetadata im) {
        super.updateImageMetadata(im);
    }

    @Override
    protected void updateImageProgress(float f) {
        super.updateImageProgress(f);
    }

    @Override
    protected void emitWarning(String string) {
        super.emitWarning(string); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public ImageFrame load(int imageIndex, double width, double height, boolean preserveAspectRatio, boolean smooth, float screenPixelScale, float imagePixelScale) throws IOException {
        if (0 != imageIndex) {
            return null;
        }        

        Dimension fallbackDimension = (width <= 0 || height <= 0) ? dimensionProvider.getDimension() : null;

        float imageWidth = (int) width > 0 ? (int) width : (float) fallbackDimension.getWidth();
        float imageHeight = (int) height > 0 ? (int) height : (float) fallbackDimension.getHeight();

        ImageMetadata md = new ImageMetadata(null, true,
                null, null, null, null, null,
                (int) width, (int) height, null, null, null);

        updateImageMetadata(md);

        try {
            return createImageFrame(imageWidth, imageHeight, screenPixelScale);
        } catch (IOException ex) {
            throw new IOException(ex);
        }
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

        return new ImageFrame(ImageStorage.ImageType.RGBA, imageData, bufferedImage.getWidth(),
                bufferedImage.getHeight(), getStride(bufferedImage), pixelScale, null);
    }

    private BufferedImage getTranscodedImage(float width, float height)
            throws IOException {
        BufferedImage read;
        try {            
            if (width <= 300) {
                BufferedImage rBufImg = readFile();
                read = resize(rBufImg, (int) width, (int) height);
            } else {
                BufferedImage rBufImg = readFile();
                read = resize(rBufImg, (int) width * 4, (int) height * 4);
            }            
        } catch (IOException e) {
            Logger.getLogger(WEBPImageLoader.class.getName()).log(Level.FINE, "Error reading WEBP file format!");
            throw new IOException(e);
        }
        return read;
    }

    private BufferedImage readFile() throws IOException {
        BufferedImage image = null;
        image = ImageIO.read(input);
        return image;
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

    private BufferedImage resize(BufferedImage image, int scaledWidth, int scaledHeight) {
        double imageHeight = image.getHeight();
        double imageWidth = image.getWidth();

        if (imageHeight / scaledHeight > imageWidth / scaledWidth) {
            scaledWidth = (int) (scaledHeight * imageWidth / imageHeight);
        } else {
            scaledHeight = (int) (scaledWidth * imageHeight / imageWidth);
        }

        BufferedImage resized = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        g.drawImage(image, 0, 0, scaledWidth, scaledHeight, null);
        g.dispose();

        return resized;
    }

    private static class Lock {

        private boolean locked;

        public Lock() {
            locked = false;
        }

        public synchronized boolean isLocked() {
            return locked;
        }

        public synchronized void lock() {
            if (locked) {
                throw new IllegalStateException("Recursive loading is not allowed.");
            }
            locked = true;
        }

        public synchronized void unlock() {
            if (!locked) {
                throw new IllegalStateException("Invalid loader state.");
            }
            locked = false;
        }
    }

}
