package org.photoslide.datamodel;

import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.XMPPathFactory;
import com.adobe.internal.xmp.options.PropertyOptions;
import com.adobe.internal.xmp.properties.XMPProperty;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.geometry.Point2D;

/**
 * Faces in XMP, in the standard properties other programs understand:
 * <ul>
 * <li>MWG regions ({@code mwg-rs:Regions}, type "Face", name and normalized
 * area) as written and read by Lightroom, digiKam, Picasa, ACDSee and Windows
 * Photos,</li>
 * <li>{@code Iptc4xmpExt:PersonInImage}, the plain list of the names.</li>
 * </ul>
 * MWG areas refer to the image as it is shown (centre, width, height). PhotoSlide
 * keeps faces relative to the decoded source, so they are turned by the
 * PhotoSlide rotation when written; the rotation used is stored in
 * {@code photoslide:FaceRotation} to read them back exactly. Regions of other
 * types (pets, focus areas, ...) are kept untouched.
 *
 * Only confirmed names are written: a suggestion of the recognition is stored
 * as a face without name.
 */
final class XmpFaceRegions {

    private static final Logger LOG = Logger.getLogger(XmpFaceRegions.class.getName());

    static final String NS_RS = "http://www.metadataworkinggroup.com/schemas/regions/";
    static final String NS_AREA = "http://ns.adobe.com/xmp/sType/Area#";
    static final String NS_DIM = "http://ns.adobe.com/xap/1.0/sType/Dimensions#";
    static final String NS_IPTC_EXT = "http://iptc.org/std/Iptc4xmpExt/2008-02-29/";

    private static final String TYPE_FACE = "Face";
    /** Paths below the property "Regions", with the prefix the registry knows. */
    private static final String LIST;
    private static final String DIMENSIONS;

    static {
        var registry = XMPMetaFactory.getSchemaRegistry();
        try {
            registerIfMissing(registry, NS_RS, "mwg-rs");
            registerIfMissing(registry, NS_AREA, "stArea");
            registerIfMissing(registry, NS_DIM, "stDim");
            registerIfMissing(registry, NS_IPTC_EXT, "Iptc4xmpExt");
        } catch (XMPException ex) {
            LOG.log(Level.SEVERE, "Cannot register the XMP namespaces for faces", ex);
        }
        String prefix = registry.getNamespacePrefix(NS_RS);    // "mwg-rs:"
        if (prefix == null) {
            prefix = "mwg-rs:";
        }
        LIST = "Regions/" + prefix + "RegionList";
        DIMENSIONS = "Regions/" + prefix + "AppliedToDimensions";
    }

    private static void registerIfMissing(com.adobe.internal.xmp.XMPSchemaRegistry registry, String ns,
            String prefix) throws XMPException {
        if (registry.getNamespacePrefix(ns) == null) {
            registry.registerNamespace(ns, prefix);
        }
    }

    private XmpFaceRegions() {
    }

    // ------------------------------------------------------------------
    // MediaFile -> XMP
    // ------------------------------------------------------------------
    static void put(XMPMeta m, MediaFile f, String nsPhotoslide) throws XMPException {
        List<FaceRegion> faces = f.getFaceRegions();
        removeFaceItems(m);
        int q = FaceRegion.quarters(f.getRotationAngleProperty().get());
        if (!faces.isEmpty()) {
            if (!m.doesPropertyExist(NS_RS, "Regions")) {
                m.setProperty(NS_RS, "Regions", null, new PropertyOptions().setStruct(true));
            }
            Point2D size = f.getOrignalImageSize();
            if (size != null && size.getX() > 0 && size.getY() > 0) {
                boolean swap = q % 2 == 1;
                m.setStructField(NS_RS, "Regions", NS_RS, "AppliedToDimensions", null,
                        new PropertyOptions().setStruct(true));
                m.setStructField(NS_RS, DIMENSIONS, NS_DIM, "w", fmt(swap ? size.getY() : size.getX()));
                m.setStructField(NS_RS, DIMENSIONS, NS_DIM, "h", fmt(swap ? size.getX() : size.getY()));
                m.setStructField(NS_RS, DIMENSIONS, NS_DIM, "unit", "pixel");
            }
            for (FaceRegion face : faces) {
                FaceRegion r = face.rotated(q);
                m.appendArrayItem(NS_RS, LIST, new PropertyOptions().setArray(true), null,
                        new PropertyOptions().setStruct(true));
                String item = XMPPathFactory.composeArrayItemPath(LIST, m.countArrayItems(NS_RS, LIST));
                m.setStructField(NS_RS, item, NS_RS, "Area", null, new PropertyOptions().setStruct(true));
                String area = item + XMPPathFactory.composeStructFieldPath(NS_RS, "Area");
                m.setStructField(NS_RS, area, NS_AREA, "x", fmt(r.x() + r.w() / 2));
                m.setStructField(NS_RS, area, NS_AREA, "y", fmt(r.y() + r.h() / 2));
                m.setStructField(NS_RS, area, NS_AREA, "w", fmt(r.w()));
                m.setStructField(NS_RS, area, NS_AREA, "h", fmt(r.h()));
                m.setStructField(NS_RS, area, NS_AREA, "unit", "normalized");
                m.setStructField(NS_RS, item, NS_RS, "Type", TYPE_FACE);
                if (face.confirmed() && !face.isUnknown()) {
                    m.setStructField(NS_RS, item, NS_RS, "Name", face.name());
                }
            }
            m.setPropertyInteger(nsPhotoslide, "FaceRotation", q);
        } else {
            m.deleteProperty(nsPhotoslide, "FaceRotation");
        }
        if (m.doesPropertyExist(NS_RS, "Regions") && m.countArrayItems(NS_RS, LIST) == 0) {
            m.deleteProperty(NS_RS, "Regions");
        }

        // the plain name list is only replaced when PhotoSlide knows the faces,
        // so names another program wrote without regions stay
        if (!faces.isEmpty() || f.isFacesScanned()) {
            m.deleteProperty(NS_IPTC_EXT, "PersonInImage");
            for (String name : confirmedNames(faces)) {
                m.appendArrayItem(NS_IPTC_EXT, "PersonInImage", new PropertyOptions().setArray(true), name, null);
            }
        }
        if (f.isFacesScanned()) {
            m.setPropertyBoolean(nsPhotoslide, "FacesScanned", true);
        } else {
            m.deleteProperty(nsPhotoslide, "FacesScanned");
        }
    }

    private static Set<String> confirmedNames(List<FaceRegion> faces) {
        Set<String> names = new LinkedHashSet<>();
        for (FaceRegion r : faces) {
            if (r.confirmed() && !r.isUnknown()) {
                names.add(r.name());
            }
        }
        return names;
    }

    /** Removes the face regions, keeps all other regions. */
    private static void removeFaceItems(XMPMeta m) throws XMPException {
        if (!m.doesPropertyExist(NS_RS, LIST)) {
            return;
        }
        for (int i = m.countArrayItems(NS_RS, LIST); i >= 1; i--) {
            String item = XMPPathFactory.composeArrayItemPath(LIST, i);
            XMPProperty type = m.getStructField(NS_RS, item, NS_RS, "Type");
            if (type != null && TYPE_FACE.equalsIgnoreCase(type.getValue())) {
                m.deleteArrayItem(NS_RS, LIST, i);
            }
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.6f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // ------------------------------------------------------------------
    // XMP -> MediaFile
    // ------------------------------------------------------------------
    /** Must run after the rotation was read. The sidecar is the reference: no regions = no faces. */
    static void get(XMPMeta m, MediaFile f, String nsPhotoslide) throws XMPException {
        Integer stored = m.getPropertyInteger(nsPhotoslide, "FaceRotation");
        // regions of other programs refer to the image as shown (EXIF orientation)
        int q = stored != null ? stored : FaceRegion.quarters(f.getRotationAngleProperty().get());
        double dimW = parse(m.getStructField(NS_RS, DIMENSIONS, NS_DIM, "w"));
        double dimH = parse(m.getStructField(NS_RS, DIMENSIONS, NS_DIM, "h"));

        List<FaceRegion> faces = new ArrayList<>();
        int n = m.doesPropertyExist(NS_RS, LIST) ? m.countArrayItems(NS_RS, LIST) : 0;
        for (int i = 1; i <= n; i++) {
            String item = XMPPathFactory.composeArrayItemPath(LIST, i);
            XMPProperty type = m.getStructField(NS_RS, item, NS_RS, "Type");
            if (type == null || !TYPE_FACE.equalsIgnoreCase(type.getValue())) {
                continue;
            }
            String area = item + XMPPathFactory.composeStructFieldPath(NS_RS, "Area");
            double cx = parse(m.getStructField(NS_RS, area, NS_AREA, "x"));
            double cy = parse(m.getStructField(NS_RS, area, NS_AREA, "y"));
            double w = parse(m.getStructField(NS_RS, area, NS_AREA, "w"));
            double h = parse(m.getStructField(NS_RS, area, NS_AREA, "h"));
            if (Double.isNaN(cx) || Double.isNaN(cy) || Double.isNaN(w) || Double.isNaN(h)) {
                continue;
            }
            XMPProperty unit = m.getStructField(NS_RS, area, NS_AREA, "unit");
            if (unit != null && "pixel".equalsIgnoreCase(unit.getValue())) {
                if (!(dimW > 0 && dimH > 0)) {
                    continue;
                }
                cx /= dimW;
                w /= dimW;
                cy /= dimH;
                h /= dimH;
            }
            XMPProperty name = m.getStructField(NS_RS, item, NS_RS, "Name");
            String person = name == null ? null : name.getValue();
            FaceRegion shown = new FaceRegion(cx - w / 2, cy - h / 2, w, h, null, false)
                    .withName(person, person != null && !person.isBlank());
            faces.add(shown.rotated(-q));
        }
        f.setFaceRegions(faces);
        f.setFacesScanned(Boolean.TRUE.equals(m.getPropertyBoolean(nsPhotoslide, "FacesScanned")));
    }

    private static double parse(XMPProperty p) {
        if (p == null || p.getValue() == null) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(p.getValue().trim());
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }
}
