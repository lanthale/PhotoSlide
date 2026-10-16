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
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.photoslide.datamodel.customformats.dimension.Dimension;
import org.photoslide.datamodel.customformats.dimension.DimensionProvider;

/**
 * Loads a WebP image for JavaFX. The descriptor's signature can only match
 * "RIFF" at offset 0, therefore this loader must verify the full
 * "RIFF....WEBP" header itself and reject other RIFF containers (WAV, AVI)
 * with an {@link ImageFormatException}.
 *
 * @author selfemp
 */
public class WEBPImageLoader extends ImageLoaderImpl {

    private static final int BYTES_PER_PIXEL = 4; // RGBA
    private static final int HEADER_SIZE = 12;     // "RIFF" + size + "WEBP"

    private static final Logger LOG = Logger.getLogger(WEBPImageLoader.class.getName());

    private final InputStream input;
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
        // nothing to release; stream is owned by the caller/ImageStorage
    }

    @Override
    public ImageFrame load(int imageIndex, double width, double height,
            boolean preserveAspectRatio, boolean smooth,
            float screenPixelScale, float imagePixelScale) throws IOException {

        if (imageIndex != 0) {
            return null;
        }

        // The signature in WEBPDescriptor only covers "RIFF" (offset 0),
        // because bytes 4-7 hold the variable file size. JavaFX may therefore
        // select this loader for any RIFF container. Verify "WEBP" at bytes
        // 8-11 here, without consuming the stream for the decoder.
        verifyWebpHeader();

        Dimension fallbackDimension
                = (width <= 0 || height <= 0) ? dimensionProvider.getDimension() : null;

        float imageWidth = width > 0 ? (float) width : (float) fallbackDimension.getWidth();
        float imageHeight = height > 0 ? (float) height : (float) fallbackDimension.getHeight();

        ImageMetadata md = new ImageMetadata(null, true,
                null, null, null, null, null,
                (int) imageWidth, (int) imageHeight, null, null, null);
        updateImageMetadata(md);

        try {
            return createImageFrame(imageWidth, imageHeight, screenPixelScale);
        } catch (IOException ex) {
            LOG.log(Level.FINE, "Error reading WEBP file format!", ex);
            throw ex;
        }
    }

    /**
     * Reads the first 12 bytes, checks "RIFF" + "WEBP" and resets the stream.
     * Throws {@link ImageFormatException} for non-WebP RIFF files (WAV, AVI)
     * so JavaFX can report/handle the unsupported format cleanly.
     */
    private void verifyWebpHeader() throws IOException {
        boolean marked = false;
        if (input.markSupported()) {
            input.mark(HEADER_SIZE);
            marked = true;
        }
        try {
            byte[] header = input.readNBytes(HEADER_SIZE);
            if (!WEBPDescriptor.isWEBPStream(header)) {
                throw new IOException(
                        "RIFF stream is not WEBP (RIFF header: " + toHex(header) + ")");
            }
        } finally {
            if (marked) {
                input.reset();
            }
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private ImageFrame createImageFrame(float width, float height, float pixelScale)
            throws IOException {
        BufferedImage bufferedImage = getTranscodedImage(width * pixelScale, height * pixelScale);
        ByteBuffer imageData = getImageData(bufferedImage);

        return new ImageFrame(ImageStorage.ImageType.RGBA, imageData,
                bufferedImage.getWidth(), bufferedImage.getHeight(),
                getStride(bufferedImage), pixelScale, null);
    }

    private BufferedImage getTranscodedImage(float width, float height) throws IOException {
        BufferedImage source = readFile();
        return resize(source, Math.max(1, (int) width), Math.max(1, (int) height));
    }

    private BufferedImage readFile() throws IOException {
        BufferedImage image = ImageIO.read(input);
        if (image == null) {
            // ImageIO found no reader -> either not a decodable image or the
            // TwelveMonkeys imageio-webp codec is missing on the classpath.
            throw new IOException(
                    "ImageIO cannot decode WEBP stream (is imageio-webp 3.15.x on the classpath?)");
        }
        return image;
    }

    private int getStride(BufferedImage bufferedImage) {
        return bufferedImage.getWidth() * BYTES_PER_PIXEL;
    }

    private ByteBuffer getImageData(BufferedImage bufferedImage) {
        int[] rgb = bufferedImage.getRGB(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(),
                null, 0, bufferedImage.getWidth());

        byte[] imageData = new byte[getStride(bufferedImage) * bufferedImage.getHeight()];
        copyColorToBytes(rgb, imageData);
        return ByteBuffer.wrap(imageData);
    }

    private void copyColorToBytes(int[] rgb, byte[] imageData) {
        if (rgb.length * BYTES_PER_PIXEL != imageData.length) {
            throw new ArrayIndexOutOfBoundsException();
        }

        for (int i = 0; i < rgb.length; i++) {
            int pixel = rgb[i];
            int dataOffset = BYTES_PER_PIXEL * i;
            imageData[dataOffset] = (byte) (pixel >> 16);      // R
            imageData[dataOffset + 1] = (byte) (pixel >> 8);   // G
            imageData[dataOffset + 2] = (byte) pixel;          // B
            imageData[dataOffset + 3] = (byte) (pixel >>> 24); // A
        }
    }

    /**
     * Scales the image to the given bounding box preserving the aspect ratio.
     * Uses bilinear interpolation for acceptable downsampling quality; the
     * final smooth scaling to screen size is done by JavaFX (smooth=true).
     */
    private BufferedImage resize(BufferedImage image, int scaledWidth, int scaledHeight) {
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();

        if (imageHeight / scaledHeight > imageWidth / scaledWidth) {
            scaledWidth = (int) Math.max(1, Math.round(scaledHeight * imageWidth / imageHeight));
        } else {
            scaledHeight = (int) Math.max(1, Math.round(scaledWidth * imageHeight / imageWidth));
        }

        BufferedImage resized = new BufferedImage(scaledWidth, scaledHeight,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(image, 0, 0, scaledWidth, scaledHeight, null);
        } finally {
            g.dispose();
        }
        return resized;
    }
}