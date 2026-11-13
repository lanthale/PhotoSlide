package org.photoslide.bench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.geometry.Point2D;
import org.photoslide.datamodel.FaceRegion;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.datamodel.XmpSidecar;

/**
 * Writes faces into a sidecar and reads them back: names, positions (also
 * with a PhotoSlide rotation) and foreign regions that must be kept.
 *
 * <pre>
 * java ... org.photoslide.bench.FaceXmpVerify
 * </pre>
 *
 * Exits with 1 on the first difference.
 */
public final class FaceXmpVerify {

    private FaceXmpVerify() {
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("facexmp");
        Path image = dir.resolve("IMG_1.jpg");
        Files.write(image, new byte[0]);
        // a region of another program (a pet) that must survive
        Files.writeString(dir.resolve("IMG_1.xmp"), """
                <x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
                <rdf:Description rdf:about="" xmlns:mwg-rs="http://www.metadataworkinggroup.com/schemas/regions/"
                  xmlns:stArea="http://ns.adobe.com/xmp/sType/Area#">
                 <mwg-rs:Regions rdf:parseType="Resource"><mwg-rs:RegionList><rdf:Bag>
                  <rdf:li rdf:parseType="Resource"><mwg-rs:Type>Pet</mwg-rs:Type><mwg-rs:Name>Bello</mwg-rs:Name>
                   <mwg-rs:Area stArea:x="0.5" stArea:y="0.5" stArea:w="0.1" stArea:h="0.1" stArea:unit="normalized"/></rdf:li>
                  <rdf:li rdf:parseType="Resource"><mwg-rs:Type>Face</mwg-rs:Type><mwg-rs:Name>Old Name</mwg-rs:Name>
                   <mwg-rs:Area stArea:x="0.2" stArea:y="0.3" stArea:w="0.1" stArea:h="0.2" stArea:unit="normalized"/></rdf:li>
                 </rdf:Bag></mwg-rs:RegionList></mwg-rs:Regions>
                </rdf:Description></rdf:RDF></x:xmpmeta>
                """);

        for (double rotation : new double[]{0, 90, 180, 270}) {
            MediaFile f = new MediaFile();
            f.setPathStorage(image);
            f.setName("IMG_1.jpg");
            check(XmpSidecar.read(f), "sidecar read");
            if (rotation == 0) {
                List<FaceRegion> foreign = f.getFaceRegions();
                check(foreign.size() == 1 && "Old Name".equals(foreign.get(0).name()), "foreign face read: " + foreign);
                check(near(foreign.get(0).x(), 0.15) && near(foreign.get(0).y(), 0.2), "foreign area " + foreign);
            }
            f.setRotationAngle(rotation);
            f.setOrignalImageSize(new Point2D(4000, 3000));
            List<FaceRegion> faces = List.of(
                    new FaceRegion(0.1, 0.2, 0.05, 0.08, "Anna Müller", true),
                    new FaceRegion(0.6, 0.4, 0.1, 0.12, "Suggested", false),
                    new FaceRegion(0.3, 0.7, 0.07, 0.07, null, false));
            f.setFaceRegions(faces);
            f.setFacesScanned(true);
            XmpSidecar.write(f);

            String xml = Files.readString(dir.resolve("IMG_1.xmp"));
            check(xml.contains("Bello"), "pet region kept");
            check(!xml.contains("Old Name"), "old face replaced");
            check(!xml.contains("Suggested"), "suggestion not written");
            check(xml.contains("PersonInImage"), "PersonInImage written");

            MediaFile g = new MediaFile();
            g.setPathStorage(image);
            g.setName("IMG_1.jpg");
            check(XmpSidecar.read(g), "sidecar read back");
            List<FaceRegion> back = g.getFaceRegions();
            check(back.size() == 3, "3 faces, got " + back);
            for (int i = 0; i < 3; i++) {
                FaceRegion a = faces.get(i);
                FaceRegion b = back.get(i);
                check(near(a.x(), b.x()) && near(a.y(), b.y()) && near(a.w(), b.w()) && near(a.h(), b.h()),
                        "rotation " + rotation + ": " + a + " != " + b);
            }
            check("Anna Müller".equals(back.get(0).name()) && back.get(0).confirmed(), "name confirmed");
            check(back.get(1).isUnknown() && back.get(2).isUnknown(), "unconfirmed stay unknown");
            check("Anna Müller".equals(g.facesProperty().get()), "search text " + g.facesProperty().get());
            check(g.isFacesScanned(), "scanned flag");
        }
        System.out.println("FaceXmpVerify OK");
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-5;
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAILED: " + what);
            System.exit(1);
        }
    }
}
