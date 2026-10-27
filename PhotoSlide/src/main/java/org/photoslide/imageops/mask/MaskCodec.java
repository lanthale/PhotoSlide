package org.photoslide.imageops.mask;

import java.util.ArrayList;
import java.util.List;

/**
 * Text form of regions and colour selections for the XMP sidecar.
 *
 * Region: "N" or "I" (inverted), then per outline
 * "|feather;smooth;sourceX;sourceY;x,y x,y ...".
 * Colour selection: nine values separated by ';' in record order.
 */
public final class MaskCodec {

    private MaskCodec() {
    }

    public static String encode(Region r) {
        StringBuilder sb = new StringBuilder(r.inverted() ? "I" : "N");
        for (Contour c : r.contours()) {
            sb.append('|').append(c.feather()).append(';').append(c.smooth() ? 1 : 0).append(';')
                    .append(c.sourceX()).append(';').append(c.sourceY()).append(';');
            boolean first = true;
            for (Contour.Point p : c.points()) {
                if (!first) {
                    sb.append(' ');
                }
                sb.append(p.x()).append(',').append(p.y());
                first = false;
            }
        }
        return sb.toString();
    }

    /** @return the region, or an empty one for null / unreadable text */
    public static Region decodeRegion(String s) {
        if (s == null || s.isEmpty()) {
            return Region.EMPTY;
        }
        String[] parts = s.split("\\|");
        boolean inverted = parts[0].startsWith("I");
        List<Contour> contours = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String[] f = parts[i].split(";", 5);
            if (f.length < 5) {
                continue;
            }
            List<Contour.Point> pts = new ArrayList<>();
            for (String xy : f[4].trim().split("\\s+")) {
                String[] c = xy.split(",");
                if (c.length == 2) {
                    pts.add(new Contour.Point(Double.parseDouble(c[0]), Double.parseDouble(c[1])));
                }
            }
            if (pts.size() >= 3) {
                contours.add(new Contour(pts, Float.parseFloat(f[0]), "1".equals(f[1].trim()),
                        Double.parseDouble(f[2]), Double.parseDouble(f[3])));
            }
        }
        return new Region(contours, inverted);
    }

    public static String encode(ColorSelection c) {
        return (c.hueEnabled() ? 1 : 0) + ";" + c.hueCenter() + ";" + c.hueRange() + ";" + c.hueFeather() + ";"
                + (c.lumEnabled() ? 1 : 0) + ";" + c.lumLower() + ";" + c.lumUpper() + ";" + c.lumFeather() + ";"
                + (c.inverted() ? 1 : 0);
    }

    /** @return the selection, or NONE for null / unreadable text */
    public static ColorSelection decodeColorSelection(String s) {
        if (s == null || s.isEmpty()) {
            return ColorSelection.NONE;
        }
        String[] f = s.split(";");
        if (f.length < 9) {
            return ColorSelection.NONE;
        }
        return new ColorSelection("1".equals(f[0].trim()), Float.parseFloat(f[1]), Float.parseFloat(f[2]),
                Float.parseFloat(f[3]), "1".equals(f[4].trim()), Float.parseFloat(f[5]), Float.parseFloat(f[6]),
                Float.parseFloat(f[7]), "1".equals(f[8].trim()));
    }
}
