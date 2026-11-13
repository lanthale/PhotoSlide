package org.photoslide.datamodel;

/**
 * A face in a photo. The rectangle is normalized (0..1, top left corner and
 * size) and relative to the source image as it is decoded, before the
 * PhotoSlide rotation, crop, flip and straighten. So it stays on the face
 * whatever the user does to the photo later.
 *
 * @param name the person, null or empty = unknown
 * @param confirmed true = the name was entered or confirmed by the user,
 * false = a suggestion of the recognition (or no name yet)
 */
public record FaceRegion(double x, double y, double w, double h, String name, boolean confirmed) {

    public boolean isUnknown() {
        return name == null || name.isBlank();
    }

    public FaceRegion withName(String newName, boolean isConfirmed) {
        String n = newName == null ? null : newName.trim();
        return new FaceRegion(x, y, w, h, n == null || n.isEmpty() ? null : n, isConfirmed);
    }

    public FaceRegion withBounds(double nx, double ny, double nw, double nh) {
        return new FaceRegion(nx, ny, nw, nh, name, confirmed);
    }

    /**
     * Turns the rectangle clockwise by quarters with the image (0..3), e.g.
     * from the source into the image as it is shown with the PhotoSlide
     * rotation. Negative values turn counter clockwise.
     */
    public FaceRegion rotated(int quarters) {
        int q = Math.floorMod(quarters, 4);
        return switch (q) {
            case 1 -> withBounds(1 - y - h, x, h, w);
            case 2 -> withBounds(1 - x - w, 1 - y - h, w, h);
            case 3 -> withBounds(y, 1 - x - w, h, w);
            default -> this;
        };
    }

    /** Quarter turns (0..3, clockwise) of a rotation angle in degrees. */
    public static int quarters(double degrees) {
        double r = ((degrees % 360) + 360) % 360;
        return (int) Math.round(r / 90) % 4;
    }
}
