package org.photoslide.editormedia;

import java.util.List;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ComboBox;

/**
 * Aspect ratios of the crop frame, as shown on screen (width : height of the
 * turned photo). The frame itself is in pixels of the unturned photo, so a
 * quarter turn swaps the ratio. A new crop starts with 3:2 for landscape and
 * 2:3 for portrait photos.
 */
public final class CropAspect {

    private static final List<String> NAMES = List.of("Free", "Original", "3:2", "2:3", "4:3", "3:4", "1:1", "16:9", "9:16", "5:4", "4:5");
    /** width / height on screen; 0 = free, -1 = like the photo */
    private static final double[] VALUES = {0, -1, 3.0 / 2, 2.0 / 3, 4.0 / 3, 3.0 / 4, 1, 16.0 / 9, 9.0 / 16, 5.0 / 4, 4.0 / 5};

    private CropAspect() {
    }

    public static ComboBox<String> comboBox() {
        ComboBox<String> box = new ComboBox<>();
        box.getItems().addAll(NAMES);
        box.getSelectionModel().select(0);
        box.setVisibleRowCount(NAMES.size());
        return box;
    }

    /**
     * Ratio for the crop frame (unturned photo, width / height), 0 = free.
     *
     * @param w,h size of the unturned photo
     * @param quarter the photo is shown turned by 90 or 270 degrees
     */
    public static double frameRatio(int index, double w, double h, boolean quarter) {
        double v = VALUES[Math.max(0, Math.min(VALUES.length - 1, index))];
        if (v == 0) {
            return 0;
        }
        if (v < 0) {
            return w / h;
        }
        return quarter ? 1 / v : v;
    }

    /**
     * Entry to start with: the ratio of an existing crop if it is in the list
     * (else free), without crop 3:2 or 2:3 as the photo is shown.
     */
    public static int initial(Rectangle2D crop, double w, double h, boolean quarter) {
        if (crop != null && crop.getWidth() > 0 && crop.getHeight() > 0) {
            double r = crop.getWidth() / crop.getHeight();
            for (int i = 2; i < VALUES.length; i++) {
                if (Math.abs(frameRatio(i, w, h, quarter) - r) < 0.01) {
                    return i;
                }
            }
            return Math.abs(r - w / h) < 0.01 ? 1 : 0;
        }
        boolean landscapeOnScreen = (w >= h) != quarter;
        return NAMES.indexOf(landscapeOnScreen ? "3:2" : "2:3");
    }

    /** true if the photo is shown turned by 90 or 270 degrees. */
    public static boolean isQuarter(double rotation) {
        double r = ((rotation % 360) + 360) % 360;
        return Math.abs(r - 90) < 1 || Math.abs(r - 270) < 1;
    }
}
