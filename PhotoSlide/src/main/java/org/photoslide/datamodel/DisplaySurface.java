package org.photoslide.datamodel;

import java.nio.IntBuffer;
import javafx.application.Platform;
import javafx.scene.image.PixelBuffer;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/**
 * A reusable 8 bit surface for an ImageView. Create it once for a given
 * display size, give its image() to the ImageView and call update(...) after
 * every render. The pixels are converted on the calling thread, only the copy
 * into the shared buffer happens on the JavaFX thread (no new Image per
 * update, no flicker).
 */
public final class DisplaySurface {

    private final int width;
    private final int height;
    private final PixelBuffer<IntBuffer> pixelBuffer;
    private final WritableImage image;

    public DisplaySurface(int width, int height) {
        this.width = width;
        this.height = height;
        IntBuffer buffer = IntBuffer.allocate(width * height);
        this.pixelBuffer = new PixelBuffer<>(width, height, buffer, PixelFormat.getIntArgbPreInstance());
        this.image = new WritableImage(pixelBuffer);
    }

    public WritableImage image() {
        return image;
    }

    /**
     * Converts the 16 bit image and shows it. Any thread. The image must have
     * exactly the size of the surface.
     */
    public void update(Image16 img, boolean dither) {
        if (img.width() != width || img.height() != height) {
            throw new IllegalArgumentException("Image size " + img.width() + "x" + img.height()
                    + " does not match the surface " + width + "x" + height);
        }
        final int[] argb = Display16.toArgb(img, dither);
        Runnable copy = () -> pixelBuffer.updateBuffer(pb -> {
            IntBuffer b = pb.getBuffer();
            b.clear();
            b.put(argb);
            b.clear();
            return null;                    // whole buffer is dirty
        });
        if (Platform.isFxApplicationThread()) {
            copy.run();
        } else {
            Platform.runLater(copy);
        }
    }
}