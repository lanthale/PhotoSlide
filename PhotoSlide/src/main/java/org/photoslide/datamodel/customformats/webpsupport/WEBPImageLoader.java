package org.photoslide.datamodel.customformats.webpsupport;

import com.sun.javafx.iio.ImageFrame;
import com.sun.javafx.iio.ImageMetadata;
import com.sun.javafx.iio.ImageStorage;
import com.sun.javafx.iio.common.ImageLoaderImpl;
import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.photoslide.datamodel.customformats.dimension.Dimension;
import org.photoslide.datamodel.customformats.dimension.DimensionProvider;

/**
 * Loads a WebP image for JavaFX.
 * <p>
 * The descriptor's signature can only match "RIFF" at offset 0, therefore this
 * loader verifies the full "RIFF....WEBP" header itself and rejects other RIFF
 * containers (WAV, AVI).
 * <p>
 * Decoding does NOT use the ImageIO registry. The TwelveMonkeys reader is
 * created directly from its SPI, so the plugin can be deregistered globally
 * (to avoid the JavaFX ImageIO ClassCastException) without breaking this
 * loader. The SPI is stateless; every call creates its own reader instance,
 * which makes this thread-safe.
 *
 * @author selfemp
 */
public class WEBPImageLoader extends ImageLoaderImpl {

    private static final int BYTES_PER_PIXEL = 4; // RGBA
    private static final int HEADER_SIZE = 12;     // "RIFF" + size + "WEBP"

    private static final Logger LOG = Logger.getLogger(WEBPImageLoader.class.getName());

    /** Stateless, shared. Independent of the IIORegistry. */
    private static final WebPImageReaderSpi WEBP_SPI = new WebPImageReaderSpi();

    private final InputStream input;
    private final DimensionProvider dimensionProvider;

    protected WEBPImageLoader(InputStream input, DimensionProvider dimensionProvider) {
        super(WEBPDescriptor.getInstance());

        if (input == null) {
            throw new IllegalArgumentException("input == null!");
        }
        // mark/reset is required by verifyWebpHeader(); wrap if unsupported
        this.input = input.markSupported() ? input : new BufferedInputStream(input);
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
            LOG.log(Level.WARNING, "Error reading WEBP file format!", ex);
            throw ex;
        }
    }

    /**
     * Reads the first 12 bytes, checks "RIFF" + "WEBP" and resets the stream.
     * The stream is guaranteed to support mark/reset (see constructor).
     */
    private void verifyWebpHeader() throws IOException {
        input.mark(HEADER_SIZE);
        try {
            byte[] header = input.readNBytes(HEADER_SIZE);
            if (!WEBPDescriptor.isWEBPStream(header)) {
                throw new IOException(
                        "RIFF stream is not WEBP (RIFF header: " + toHex(header) + ")");
            }
        } finally {
            input.reset();
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
        int targetW = Math.max(1, (int) width);
        int targetH = Math.max(1, (int) height);
        BufferedImage source = readFile(targetW, targetH);
        return resize(source, targetW, targetH);
    }

    /**
     * Decodes the WebP stream directly via the TwelveMonkeys SPI (no
     * IIORegistry lookup). Uses source subsampling to reduce the amount of
     * pixel data for large images; the exact scaling is done afterwards in
     * {@link #resize}.
     */
    private BufferedImage readFile(int targetW, int targetH) throws IOException {
        // closing this stream does NOT close the underlying InputStream
        try (ImageInputStream iis = new MemoryCacheImageInputStream(input)) {
            ImageReader reader = WEBP_SPI.createReaderInstance();
            try {
                // seekForwardOnly=false: the reader needs to seek back after
                // reading the header (getWidth/getHeight) before decoding
                reader.setInput(iis, false, true);

                ImageReadParam param = reader.getDefaultReadParam();
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                int sub = Math.max(1, Math.min(w / targetW, h / targetH));
                if (sub > 1) {
                    param.setSourceSubsampling(sub, sub, 0, 0);
                }

                BufferedImage image = reader.read(0, param);
                if (image == null) {
                    throw new IOException("WebP reader returned no image");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
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
     * Uses bilinear interpolation; the final smooth scaling to screen size is
     * done by JavaFX (smooth=true).
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