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
import java.util.Iterator;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Lädt PSD-Bilder über einen ImageIO-Reader (z. B. TwelveMonkeys imageio-psd)
 * und verkleinert sie speicherschonend auf die gewünschte Größe.
 *
 * @author selfemp
 */
public class PSDImageLoader extends ImageLoaderImpl {

    private static final int BYTES_PER_PIXEL = 4; // RGBA

    private final InputStream input;

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

        BufferedImage image;
        // Memory-Cache statt Platten-Cache: lokal, ohne globales ImageIO.setUseCache(false)
        try (ImageInputStream iis = new MemoryCacheImageInputStream(input)) {
            image = readScaled(iis, width, height, preserveAspectRatio, smooth);
        } finally {
            dispose();
        }

        return new ImageFrame(ImageStorage.ImageType.RGBA, toRgbaBuffer(image),
                image.getWidth(), image.getHeight(),
                image.getWidth() * BYTES_PER_PIXEL, 1.0f, null);
    }

    private static BufferedImage readScaled(ImageInputStream iis, double reqW, double reqH,
            boolean keepRatio, boolean smooth) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        if (!readers.hasNext()) {
            throw new IOException("Kein ImageIO-Reader für PSD gefunden "
                    + "(imageio-psd / TwelveMonkeys im Classpath?)");
        }
        ImageReader reader = readers.next();
        try {
            reader.setInput(iis, true, true);
            int srcW = reader.getWidth(0);
            int srcH = reader.getHeight(0);
            int[] target = computeTargetSize(srcW, srcH, reqW, reqH, keepRatio);

            // 1. Grobes Verkleinern schon beim Lesen (spart RAM und Zeit)
            ImageReadParam param = reader.getDefaultReadParam();
            int sub = computeSubsampling(srcW, srcH, target[0], target[1], smooth);
            if (sub > 1) {
                param.setSourceSubsampling(sub, sub, 0, 0);
            }
            BufferedImage read = reader.read(0, param);

            // 2. Feines Verkleinern in Halbierungsschritten
            return scaleProgressive(read, target[0], target[1], smooth);
        } finally {
            reader.dispose();
        }
    }

    /**
     * Subsampling überspringt Pixel ohne Filter und aliast daher selbst.
     * Deshalb nur so weit verkleinern, dass noch mindestens das 2-fache der
     * Zielgröße übrig bleibt. Den Rest erledigt die gefilterte Skalierung.
     */
    private static int computeSubsampling(int srcW, int srcH, int tw, int th, boolean smooth) {
        double ratio = Math.min((double) srcW / tw, (double) srcH / th);
        if (ratio < 2) {
            return 1;
        }
        return Math.max(1, smooth ? (int) (ratio / 2) : (int) ratio);
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

    /** Halbiert schrittweise, bis die Zielgröße weniger als 2x entfernt ist, dann Endskalierung. */
    private static BufferedImage scaleProgressive(BufferedImage src, int tw, int th, boolean smooth) {
        BufferedImage cur = src;
        int cw = src.getWidth();
        int ch = src.getHeight();
        if (smooth) {
            while (cw / 2 >= tw && ch / 2 >= th) {
                cw /= 2;
                ch /= 2;
                cur = drawScaled(cur, cw, ch, true);
            }
        }
        if (cw != tw || ch != th || cur.getType() != BufferedImage.TYPE_INT_ARGB) {
            cur = drawScaled(cur, tw, th, smooth);
        }
        return cur;
    }

    private static BufferedImage drawScaled(BufferedImage src, int w, int h, boolean smooth) {
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

    /** ARGB (int) -> RGBA (byte). */
    private static ByteBuffer toRgbaBuffer(BufferedImage argbImage) {
        int[] argb = ((DataBufferInt) argbImage.getRaster().getDataBuffer()).getData();
        byte[] out = new byte[argb.length * BYTES_PER_PIXEL];
        for (int i = 0, o = 0; i < argb.length; i++, o += BYTES_PER_PIXEL) {
            int p = argb[i];
            out[o]     = (byte) (p >> 16);  // R
            out[o + 1] = (byte) (p >> 8);   // G
            out[o + 2] = (byte) p;          // B
            out[o + 3] = (byte) (p >>> 24); // A
        }
        return ByteBuffer.wrap(out);
    }
}