package org.photoslide.datamodel;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.function.DoubleConsumer;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.stream.ImageInputStream;

/**
 * Reads images with 16 bit per channel (TIFF, PNG) through ImageIO in full
 * depth. JavaFX can only deliver 8 bit, so these files would lose precision.
 * Values are taken as sRGB encoded unless the file declares a linear RGB
 * colour space, and are converted to the working space.
 */
public final class AwtImage16 {

    /** sRGB encoded 16 bit value -> linear 0..65535 */
    private static final float[] DECODE = new float[65536];

    static {
        for (int i = 0; i < 65536; i++) {
            DECODE[i] = (float) (Display16.srgbDecode(i / 65535.0) * 65535.0);
        }
    }

    private AwtImage16() {
    }

    /**
     * @return the image, or null if the file is not a 16 bit image that
     * ImageIO can read (the caller then uses the 8 bit way)
     */
    public static Image16 read16(Path path, DoubleConsumer progress) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(path.toFile())) {
            if (in == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                ImageTypeSpecifier type = reader.getRawImageType(0);
                if (type == null) {
                    Iterator<ImageTypeSpecifier> types = reader.getImageTypes(0);
                    type = types.hasNext() ? types.next() : null;
                }
                if (type == null || type.getSampleModel().getDataType() != DataBuffer.TYPE_USHORT) {
                    return null;
                }
                reader.addIIOReadProgressListener(new ProgressAdapter(progress));
                BufferedImage img = reader.read(0);
                return convert(img);
            } finally {
                reader.dispose();
            }
        }
    }

    private static Image16 convert(BufferedImage img) {
        final Raster raster = img.getRaster();
        final int w = raster.getWidth();
        final int h = raster.getHeight();
        final int bands = raster.getNumBands();
        final boolean gray = bands < 3;
        final boolean linear = img.getColorModel().getColorSpace() == ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB);
        final Image16 out = Image16.blank(w, h, true);
        final short[] d = out.data();
        IntStream.range(0, h).parallel().forEach(y -> {
            int[] px = raster.getPixels(0, y, w, 1, (int[]) null);
            int o = y * w * Image16.CHANNELS;
            for (int x = 0, i = 0; x < w; x++, i += bands, o += 3) {
                float r = value(px[i], linear);
                float g = gray ? r : value(px[i + 1], linear);
                float b = gray ? r : value(px[i + 2], linear);
                FxImage16.toWorking(r, g, b, d, o);
            }
        });
        return out;
    }

    private static float value(int v, boolean linear) {
        int q = v < 0 ? 0 : (v > 65535 ? 65535 : v);
        return linear ? q : DECODE[q];
    }

    private record ProgressAdapter(DoubleConsumer progress) implements IIOReadProgressListener {

        @Override
        public void imageProgress(ImageReader source, float percentageDone) {
            progress.accept(percentageDone / 100.0);
        }

        @Override
        public void sequenceStarted(ImageReader source, int minIndex) {
        }

        @Override
        public void sequenceComplete(ImageReader source) {
        }

        @Override
        public void imageStarted(ImageReader source, int imageIndex) {
        }

        @Override
        public void imageComplete(ImageReader source) {
        }

        @Override
        public void thumbnailStarted(ImageReader source, int imageIndex, int thumbnailIndex) {
        }

        @Override
        public void thumbnailProgress(ImageReader source, float percentageDone) {
        }

        @Override
        public void thumbnailComplete(ImageReader source) {
        }

        @Override
        public void readAborted(ImageReader source) {
        }
    }
}
