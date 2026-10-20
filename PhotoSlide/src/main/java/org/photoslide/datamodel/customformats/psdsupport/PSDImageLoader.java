package org.photoslide.datamodel.customformats.psdsupport;

import com.sun.javafx.iio.ImageFrame;
import com.sun.javafx.iio.ImageStorage;
import com.sun.javafx.iio.common.ImageLoaderImpl;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.photoslide.datamodel.customformats.dimension.DimensionProvider;

/**
 * Lädt ein PSD-Bild über ImageIO (benötigt einen PSD-Reader, z. B.
 * TwelveMonkeys) und liefert es als JavaFX-ImageFrame.
 *
 * @author selfemp
 */
public class PSDImageLoader extends ImageLoaderImpl {

    private static final int BYTES_PER_PIXEL = 4; // RGBA

    private final InputStream input;

    // dimensionProvider wird nicht mehr benötigt, Parameter bleibt für die Factory erhalten
    protected PSDImageLoader(InputStream input, DimensionProvider dimensionProvider) {
        super(PSDDescriptor.getInstance());
        this.input = Objects.requireNonNull(input, "input == null!");
    }

    protected PSDImageLoader(InputStream input) {
        super(PSDDescriptor.getInstance());
        this.input = Objects.requireNonNull(input, "input == null!");
    }

    @Override
    public void dispose() {
        try {
            input.close();
        } catch (IOException ignored) {
            // nichts zu tun
        }
    }

    @Override
    public ImageFrame load(int imageIndex, double width, double height,
            boolean preserveAspectRatio, boolean smooth,
            float screenPixelScale, float imagePixelScale) throws IOException {
        if (imageIndex != 0) {
            return null;
        }

        BufferedImage source;
        try {
            source = ImageIO.read(input);
        } finally {
            dispose(); // Stream ist nach dem Lesen verbraucht
        }
        if (source == null) {
            throw new IOException("Kein ImageIO-Reader für PSD gefunden "
                    + "(imageio-psd / TwelveMonkeys im Classpath?)");
        }

        int[] size = computeTargetSize(source.getWidth(), source.getHeight(),
                width, height, preserveAspectRatio);
        BufferedImage argb = toArgb(source, size[0], size[1], smooth);

        return new ImageFrame(ImageStorage.ImageType.RGBA, toRgbaBuffer(argb),
                argb.getWidth(), argb.getHeight(),
                argb.getWidth() * BYTES_PER_PIXEL, 1.0f, null);
    }

    private static int[] computeTargetSize(int srcW, int srcH,
            double reqW, double reqH, boolean keepRatio) {
        if (reqW <= 0 && reqH <= 0) {
            return new int[]{srcW, srcH};
        }
        double w;
        double h;
        if (reqW > 0 && reqH > 0) {
            if (keepRatio) {
                double scale = Math.min(reqW / srcW, reqH / srcH);
                w = srcW * scale;
                h = srcH * scale;
            } else {
                w = reqW;
                h = reqH;
            }
        } else if (reqW > 0) {
            w = reqW;
            h = srcH * (reqW / srcW);
        } else {
            h = reqH;
            w = srcW * (reqH / srcH);
        }
        return new int[]{Math.max(1, (int) Math.round(w)), Math.max(1, (int) Math.round(h))};
    }

    /**
     * Zeichnet (ggf. skaliert) in ein frisches TYPE_INT_ARGB-Bild.
     */
    private static BufferedImage toArgb(BufferedImage src, int w, int h, boolean smooth) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    smooth ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                            : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    /**
     * ARGB (int) -> RGBA (byte), ohne Zwischenkopie des Pixel-Arrays.
     */
    private static ByteBuffer toRgbaBuffer(BufferedImage argbImage) {
        int[] argb = ((DataBufferInt) argbImage.getRaster().getDataBuffer()).getData();
        byte[] out = new byte[argb.length * BYTES_PER_PIXEL];
        for (int i = 0, o = 0; i < argb.length; i++, o += BYTES_PER_PIXEL) {
            int p = argb[i];
            out[o] = (byte) (p >> 16); // R
            out[o + 1] = (byte) (p >> 8);  // G
            out[o + 2] = (byte) p;         // B
            out[o + 3] = (byte) (p >>> 24); // A
        }
        return ByteBuffer.wrap(out);
    }
}
