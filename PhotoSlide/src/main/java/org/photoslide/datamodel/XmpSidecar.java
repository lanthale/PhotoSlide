package org.photoslide.datamodel;

import com.adobe.internal.xmp.XMPConst;
import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.XMPPathFactory;
import com.adobe.internal.xmp.options.PropertyOptions;
import com.adobe.internal.xmp.options.SerializeOptions;
import com.adobe.internal.xmp.properties.XMPProperty;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.photoslide.imageops.ExposureFilter;
import org.photoslide.imageops.ImageFilter;

/**
 * Reads and writes the edits of a media file as XMP sidecar
 * ({@code IMG_0001.xmp} next to {@code IMG_0001.NEF}, like Lightroom, Bridge
 * and Capture One). Metadata goes into the standard properties so other
 * programs see it (rating, title, description, keywords, location, GPS).
 * Everything that only PhotoSlide understands (filters, crop, stacks,
 * rotation, deleted flag) goes into the namespace {@link #NS_PHOTOSLIDE}.
 *
 * An existing sidecar is read, changed and written back: properties of other
 * programs (e.g. Lightroom develop settings) are kept.
 *
 * Reading sets the properties of the MediaFile directly (not via
 * Platform.runLater), like the former properties file, because files are
 * read on loader threads before they are shown.
 */
public final class XmpSidecar {

    private static final Logger LOG = Logger.getLogger(XmpSidecar.class.getName());

    public static final String NS_PHOTOSLIDE = "http://ns.photoslide.org/1.0/";
    private static final String PREFIX = "photoslide";
    private static final String FILTER_PACKAGE = "org.photoslide.imageops.";

    /** All PhotoSlide properties, used to remove them in one go. */
    private static final String[][] OWN_PROPERTIES = {
        {XMPConst.NS_XMP, "Rating"},
        {XMPConst.NS_DC, "title"},
        {XMPConst.NS_DC, "description"},
        {XMPConst.NS_DC, "subject"},
        {XMPConst.NS_IPTCCORE, "Location"},
        {XMPConst.NS_EXIF, "GPSLatitude"},
        {XMPConst.NS_EXIF, "GPSLongitude"},
        {XMPConst.NS_EXIF, "GPSAltitude"},
        {XMPConst.NS_EXIF, "GPSAltitudeRef"},
        {XMPConst.NS_EXIF, "GPSTimeStamp"},
        {NS_PHOTOSLIDE, "Rotation"},
        {NS_PHOTOSLIDE, "Deleted"},
        {NS_PHOTOSLIDE, "Crop"},
        {NS_PHOTOSLIDE, "OriginalSize"},
        {NS_PHOTOSLIDE, "Stacked"},
        {NS_PHOTOSLIDE, "StackName"},
        {NS_PHOTOSLIDE, "StackPos"},
        {NS_PHOTOSLIDE, "Filters"}
    };

    static {
        try {
            XMPMetaFactory.getSchemaRegistry().registerNamespace(NS_PHOTOSLIDE, PREFIX);
        } catch (XMPException ex) {
            LOG.log(Level.SEVERE, "Cannot register the PhotoSlide XMP namespace", ex);
        }
    }

    /** Readable layout, no padding (padding is only useful for XMP embedded in images). */
    private static SerializeOptions serializeOptions() {
        return new SerializeOptions().setUseCompactFormat(false).setIndent("  ").setPadding(0);
    }

    private XmpSidecar() {
    }

    /** {@code <folder>/<name without extension>.xmp} */
    public static Path sidecarPath(MediaFile file) {
        Path image = file.getPathStorage();
        String name = image.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return image.resolveSibling(base + ".xmp");
    }

    /**
     * Writes the edits of the file into its sidecar, keeping all properties
     * of other programs.
     */
    public static synchronized void write(MediaFile file) throws IOException {
        Path path = sidecarPath(file);
        try {
            XMPMeta meta = readMeta(path);
            put(meta, file);
            byte[] bytes = XMPMetaFactory.serializeToBuffer(meta,
                    serializeOptions());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (XMPException ex) {
            throw new IOException("Cannot write " + path, ex);
        }
    }

    /**
     * Reads the sidecar into the file (only properties that exist are set).
     *
     * @return false if there is no sidecar
     */
    public static boolean read(MediaFile file) throws IOException {
        Path path = sidecarPath(file);
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            get(readMeta(path), file);
            return true;
        } catch (XMPException ex) {
            throw new IOException("Cannot read " + path, ex);
        }
    }

    /**
     * Removes all PhotoSlide properties from the sidecar. The file is deleted
     * if nothing of other programs is left in it.
     */
    public static synchronized void removeAll(MediaFile file) throws IOException {
        Path path = sidecarPath(file);
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            XMPMeta meta = readMeta(path);
            for (String[] p : OWN_PROPERTIES) {
                meta.deleteProperty(p[0], p[1]);
            }
            if (!meta.iterator().hasNext()) {
                Files.delete(path);
                return;
            }
            Files.write(path, XMPMetaFactory.serializeToBuffer(meta,
                    serializeOptions()));
        } catch (XMPException ex) {
            throw new IOException("Cannot change " + path, ex);
        }
    }

    private static XMPMeta readMeta(Path path) throws IOException, XMPException {
        if (Files.isRegularFile(path)) {
            return XMPMetaFactory.parseFromBuffer(Files.readAllBytes(path));
        }
        return XMPMetaFactory.create();
    }

    // ------------------------------------------------------------------
    // MediaFile -> XMP
    // ------------------------------------------------------------------
    private static void put(XMPMeta m, MediaFile f) throws XMPException {
        m.setPropertyInteger(XMPConst.NS_XMP, "Rating", f.getRatingProperty().get());
        putText(m, XMPConst.NS_DC, "title", f.getTitle().get());
        putText(m, XMPConst.NS_DC, "description", f.getComments().get());

        m.deleteProperty(XMPConst.NS_DC, "subject");
        String keywords = f.getKeywords();
        if (keywords != null) {
            StringTokenizer t = new StringTokenizer(keywords, ";");
            while (t.hasMoreTokens()) {
                String k = t.nextToken().trim();
                if (!k.isEmpty()) {
                    m.appendArrayItem(XMPConst.NS_DC, "subject", new PropertyOptions().setArray(true), k, null);
                }
            }
        }
        putString(m, XMPConst.NS_IPTCCORE, "Location", f.getPlace().get());

        if (f.hasGpsPosition()) {
            m.setProperty(XMPConst.NS_EXIF, "GPSLatitude", toXmpCoordinate(f.getGpsLatPosAsDouble(), 'N', 'S'));
            m.setProperty(XMPConst.NS_EXIF, "GPSLongitude", toXmpCoordinate(f.getGpsLonPosAsDouble(), 'E', 'W'));
        } else {
            m.deleteProperty(XMPConst.NS_EXIF, "GPSLatitude");
            m.deleteProperty(XMPConst.NS_EXIF, "GPSLongitude");
        }
        if (f.getGpsHeight() != -1) {
            double h = f.getGpsHeight();
            m.setProperty(XMPConst.NS_EXIF, "GPSAltitude", Math.round(Math.abs(h) * 100) + "/100");
            m.setProperty(XMPConst.NS_EXIF, "GPSAltitudeRef", h < 0 ? "1" : "0");
        } else {
            m.deleteProperty(XMPConst.NS_EXIF, "GPSAltitude");
            m.deleteProperty(XMPConst.NS_EXIF, "GPSAltitudeRef");
        }
        if (f.getGpsDateTime() != null) {
            m.setProperty(XMPConst.NS_EXIF, "GPSTimeStamp",
                    f.getGpsDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } else {
            m.deleteProperty(XMPConst.NS_EXIF, "GPSTimeStamp");
        }

        m.setPropertyDouble(NS_PHOTOSLIDE, "Rotation", f.getRotationAngleProperty().get());
        m.setPropertyBoolean(NS_PHOTOSLIDE, "Deleted", f.isDeleted());
        Rectangle2D crop = f.getCropView();
        putString(m, NS_PHOTOSLIDE, "Crop", crop == null ? null
                : join(crop.getMinX(), crop.getMinY(), crop.getWidth(), crop.getHeight()));
        Point2D size = f.getOrignalImageSize();
        putString(m, NS_PHOTOSLIDE, "OriginalSize", size == null ? null : join(size.getX(), size.getY()));
        m.setPropertyBoolean(NS_PHOTOSLIDE, "Stacked", f.isStacked());
        putString(m, NS_PHOTOSLIDE, "StackName", f.getStackName());
        m.setPropertyInteger(NS_PHOTOSLIDE, "StackPos", f.getStackPos());

        m.deleteProperty(NS_PHOTOSLIDE, "Filters");
        List<ImageFilter> filters = new ArrayList<>(f.getFilterList());
        filters.sort(Comparator.comparingInt(ImageFilter::getPosition));
        int i = 0;
        for (ImageFilter filter : filters) {
            i++;
            m.appendArrayItem(NS_PHOTOSLIDE, "Filters", new PropertyOptions().setArrayOrdered(true), null,
                    new PropertyOptions().setStruct(true));
            String item = XMPPathFactory.composeArrayItemPath("Filters", i);
            m.setStructField(NS_PHOTOSLIDE, item, NS_PHOTOSLIDE, "Name", filter.getName());
            m.setStructField(NS_PHOTOSLIDE, item, NS_PHOTOSLIDE, "Position", Integer.toString(filter.getPosition()));
            m.setStructField(NS_PHOTOSLIDE, item, NS_PHOTOSLIDE, "Version",
                    Integer.toString(filter instanceof ExposureFilter e ? e.getVersion() : 1));
            StringJoiner values = new StringJoiner(";");
            for (float v : filter.getValues()) {
                values.add(Float.toString(v));
            }
            m.setStructField(NS_PHOTOSLIDE, item, NS_PHOTOSLIDE, "Values", values.toString());
        }
    }

    private static void putText(XMPMeta m, String ns, String name, String value) throws XMPException {
        if (value == null || value.isEmpty()) {
            m.deleteProperty(ns, name);
        } else {
            m.setLocalizedText(ns, name, "", XMPConst.X_DEFAULT, value);
        }
    }

    private static void putString(XMPMeta m, String ns, String name, String value) throws XMPException {
        if (value == null || value.isEmpty()) {
            m.deleteProperty(ns, name);
        } else {
            m.setProperty(ns, name, value);
        }
    }

    /** XMP GPS format "DDD,MM.mmmmmmR", e.g. "48,12.345678N". */
    static String toXmpCoordinate(double degrees, char positive, char negative) {
        double a = Math.abs(degrees);
        int d = (int) a;
        double minutes = (a - d) * 60.0;
        return String.format(Locale.ROOT, "%d,%.6f%c", d, minutes, degrees < 0 ? negative : positive);
    }

    // ------------------------------------------------------------------
    // XMP -> MediaFile
    // ------------------------------------------------------------------
    private static void get(XMPMeta m, MediaFile f) throws XMPException {
        Integer rating = m.getPropertyInteger(XMPConst.NS_XMP, "Rating");
        if (rating != null) {
            f.setRating(Math.max(0, rating));     // -1 = rejected in Lightroom, no stars here
        }
        String title = getText(m, XMPConst.NS_DC, "title");
        if (title != null) {
            f.titleProperty().set(title);
        }
        String description = getText(m, XMPConst.NS_DC, "description");
        if (description != null) {
            f.commentsProperty().set(description);
        }
        int n = m.countArrayItems(XMPConst.NS_DC, "subject");
        if (n > 0) {
            StringJoiner keywords = new StringJoiner(";");
            for (int i = 1; i <= n; i++) {
                keywords.add(m.getArrayItem(XMPConst.NS_DC, "subject", i).getValue());
            }
            f.keywordsProperty().set(keywords.toString());
        }
        String place = m.getPropertyString(XMPConst.NS_IPTCCORE, "Location");
        if (place != null) {
            f.placeProperty().set(place);
        }

        String lat = m.getPropertyString(XMPConst.NS_EXIF, "GPSLatitude");
        String lon = m.getPropertyString(XMPConst.NS_EXIF, "GPSLongitude");
        if (lat != null && lon != null) {
            try {
                f.setGpsPositionFromDegree(fromXmpCoordinate(lat) + ";" + fromXmpCoordinate(lon));
            } catch (NumberFormatException ex) {
                LOG.log(Level.WARNING, "Invalid GPS position in XMP: {0} {1}", new Object[]{lat, lon});
            }
        }
        String alt = m.getPropertyString(XMPConst.NS_EXIF, "GPSAltitude");
        if (alt != null) {
            try {
                double h = parseRational(alt);
                f.setGpsHeight("1".equals(m.getPropertyString(XMPConst.NS_EXIF, "GPSAltitudeRef")) ? -h : h);
            } catch (NumberFormatException ex) {
                LOG.log(Level.WARNING, "Invalid GPS altitude in XMP: {0}", alt);
            }
        }
        String time = m.getPropertyString(XMPConst.NS_EXIF, "GPSTimeStamp");
        if (time != null) {
            f.setGpsDateTime(parseDateTime(time));
        }

        Double rotation = m.getPropertyDouble(NS_PHOTOSLIDE, "Rotation");
        if (rotation != null) {
            f.setRotationAngle(rotation);
        }
        Boolean deleted = m.getPropertyBoolean(NS_PHOTOSLIDE, "Deleted");
        if (deleted != null) {
            f.deletedProperty().set(deleted);
        }
        double[] crop = split(m.getPropertyString(NS_PHOTOSLIDE, "Crop"), 4);
        if (crop != null) {
            f.setCropView(new Rectangle2D(crop[0], crop[1], crop[2], crop[3]));
        }
        double[] size = split(m.getPropertyString(NS_PHOTOSLIDE, "OriginalSize"), 2);
        if (size != null) {
            f.setOrignalImageSize(new Point2D(size[0], size[1]));
        }
        Boolean stacked = m.getPropertyBoolean(NS_PHOTOSLIDE, "Stacked");
        if (stacked != null) {
            f.setStacked(stacked);
        }
        String stackName = m.getPropertyString(NS_PHOTOSLIDE, "StackName");
        if (stackName != null) {
            f.setStackName(stackName);
        }
        Integer stackPos = m.getPropertyInteger(NS_PHOTOSLIDE, "StackPos");
        if (stackPos != null) {
            f.setStackPos(stackPos);
        }

        int filters = m.countArrayItems(NS_PHOTOSLIDE, "Filters");
        List<ImageFilter> list = new ArrayList<>();
        for (int i = 1; i <= filters; i++) {
            String item = XMPPathFactory.composeArrayItemPath("Filters", i);
            ImageFilter filter = createFilter(
                    field(m, item, "Name"),
                    field(m, item, "Values"),
                    field(m, item, "Position"),
                    field(m, item, "Version"));
            if (filter != null && !f.getFilterList().contains(filter)) {
                list.add(filter);
            }
        }
        list.sort(Comparator.comparingInt(ImageFilter::getPosition));
        f.getFilterList().addAll(list);
    }

    private static String field(XMPMeta m, String item, String name) throws XMPException {
        XMPProperty p = m.getStructField(NS_PHOTOSLIDE, item, NS_PHOTOSLIDE, name);
        return p == null ? null : p.getValue();
    }

    private static ImageFilter createFilter(String name, String values, String position, String version) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            double[] dv = split(values, -1);
            float[] fv = new float[dv == null ? 0 : dv.length];
            for (int i = 0; i < fv.length; i++) {
                fv[i] = (float) dv[i];
            }
            int pos = position == null ? 0 : Integer.parseInt(position.trim());
            Integer ver = version == null ? null : Integer.valueOf(version.trim());
            if (ExposureFilter.NAME.equals(name)) {
                return ExposureFilter.of(fv, pos, ver);
            }
            ImageFilter filter = (ImageFilter) Class.forName(FILTER_PACKAGE + name)
                    .getDeclaredConstructor().newInstance();
            if (fv.length > 0) {
                filter.setValues(fv);
            }
            filter.setPosition(pos);
            return filter;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOG.log(Level.WARNING, "Unknown or invalid filter in XMP: " + name, ex);
            return null;
        }
    }

    private static String getText(XMPMeta m, String ns, String name) throws XMPException {
        XMPProperty p = m.getLocalizedText(ns, name, "", XMPConst.X_DEFAULT);
        return p == null ? null : p.getValue();
    }

    /** "48,12.345678N" or "48,12,20.7N" -> signed decimal degrees */
    static double fromXmpCoordinate(String s) {
        String v = s.trim();
        char ref = Character.toUpperCase(v.charAt(v.length() - 1));
        String[] parts = v.substring(0, v.length() - 1).split(",");
        double deg = Double.parseDouble(parts[0]);
        if (parts.length > 1) {
            deg += Double.parseDouble(parts[1]) / 60.0;
        }
        if (parts.length > 2) {
            deg += Double.parseDouble(parts[2]) / 3600.0;
        }
        return (ref == 'S' || ref == 'W') ? -deg : deg;
    }

    private static double parseRational(String s) {
        int slash = s.indexOf('/');
        if (slash < 0) {
            return Double.parseDouble(s.trim());
        }
        return Double.parseDouble(s.substring(0, slash).trim()) / Double.parseDouble(s.substring(slash + 1).trim());
    }

    private static LocalDateTime parseDateTime(String s) {
        try {
            return OffsetDateTime.parse(s).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(s);
            } catch (DateTimeParseException ex2) {
                LOG.log(Level.WARNING, "Invalid GPS time in XMP: {0}", s);
                return null;
            }
        }
    }

    private static String join(double... values) {
        StringJoiner j = new StringJoiner(";");
        for (double v : values) {
            j.add(Double.toString(v));
        }
        return j.toString();
    }

    /** @param expected number of values, -1 = any */
    private static double[] split(String s, int expected) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String[] parts = s.split(";");
        if (expected >= 0 && parts.length != expected) {
            return null;
        }
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Double.parseDouble(parts[i].trim());
        }
        return out;
    }
}
