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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.stage.Screen;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.stream.FileCacheImageInputStream;
import org.photoslide.Utility;
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
    public ImageFrame load(int imageIndex, int width, int height, boolean preserveAspectRatio, boolean smooth) throws IOException {
        if (0 != imageIndex) {
            return null;
        }

        Dimension fallbackDimension = (width <= 0 || height <= 0) ? dimensionProvider.getDimension() : null;

        float imageWidth = width > 0 ? width : (float) fallbackDimension.getWidth();
        float imageHeight = height > 0 ? height : (float) fallbackDimension.getHeight();

        ImageMetadata md = new ImageMetadata(null, true,
                null, null, null, null, null,
                width, height, null, null, null);

        updateImageMetadata(md);
        
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
        BufferedImage read;
        try {
            FileCacheImageInputStream fileCache = new FileCacheImageInputStream​(input, new File(Utility.getAppData()));
            if (width <= 300) {
                BufferedImage rBufImg = readFile(fileCache);
                read = resize(rBufImg, (int) width, (int) height);
            } else {
                BufferedImage rBufImg = readFile(fileCache);
                read = resize(rBufImg, (int) width * 4, (int) height * 4);
            }
        } catch (IOException e) {
            Logger.getLogger(WEBPImageLoader.class.getName()).log(Level.FINE, "Error reading WEBP file format!");
            throw new IOException(e);
        }
        return read;
    }

    private BufferedImage readFile(FileCacheImageInputStream fileCache) throws IOException {
        BufferedImage image = null;
        int w;
        int h;

        // Get the reader
        Iterator<ImageReader> readers = ImageIO.getImageReaders(fileCache);

        if (!readers.hasNext()) {
            throw new IllegalArgumentException("No reader found!");
        }

        ImageReader reader = readers.next();

        try {
            reader.setInput(fileCache);
            Iterator<ImageTypeSpecifier> types = reader.getImageTypes(0);
            ImageTypeSpecifier type = types.next();

            int sub = 4;
            int srcWidth = reader.getWidth(0);
            int srcHeight = reader.getHeight(0);
            if (srcWidth > 8000) {
                sub = 4;
            } else {
                sub = 1;
            }
            w = srcWidth / sub;
            h = srcHeight / sub;

            //image = MappedImageFactory.createCompatibleMappedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            ImageReadParam param = reader.getDefaultReadParam();

            //param.setDestination(image);
            param.setSourceSubsampling(sub, sub, 0, 0);

            reader.addIIOReadProgressListener(new IIOReadProgressListener() {
                @Override
                public void imageComplete(ImageReader source) {
                    //updateImageProgress(1.0f);
                }

                @Override
                public void imageProgress(ImageReader source, float percentageDone) {                    
                    updateImageProgress(percentageDone/100);
                }

                @Override
                public void imageStarted(ImageReader source, int imageIndex) {
                    //updateImageProgress(0f);
                }

                @Override
                public void readAborted(ImageReader source) {                    
                }

                @Override
                public void sequenceComplete(ImageReader source) {                    
                }

                @Override
                public void sequenceStarted(ImageReader source, int minIndex) {                
                }

                @Override
                public void thumbnailComplete(ImageReader source) {                    
                }

                @Override
                public void thumbnailProgress(ImageReader source, float percentageDone) {                    
                }

                @Override
                public void thumbnailStarted(ImageReader source, int imageIndex, int thumbnailIndex) {                    
                }
            });

            image = reader.read(0, param);
        } finally {
            // Dispose reader in finally block to avoid memory leaks
            reader.dispose();
        }
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

        /*Image tmp = image.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_FAST);
        BufferedImage resized = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();*/
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
