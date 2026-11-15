package org.photoslide.faces;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import org.photoslide.App;
import org.photoslide.datamodel.FaceRegion;

/**
 * Checks the face tables of the search database in an in-memory H2:
 * migration from version 3, faces, embeddings matched by position,
 * removal and relocation.
 *
 * <pre>
 * java ... org.photoslide.faces.FaceStoreVerify
 * </pre>
 */
public final class FaceStoreVerify {

    private static final String AURA = EmbedderType.AURAFACE.id();
    private static final String SFACE = EmbedderType.SFACE.id();

    private FaceStoreVerify() {
    }

    public static void main(String[] args) throws Exception {
        try (Connection con = DriverManager.getConnection("jdbc:h2:mem:faces", "", "")) {
            App.prepareSearchDB(con);
            // as an old database of version 3: without face tables
            try (Statement s = con.createStatement()) {
                s.execute("DROP TABLE FACES");
                s.execute("DROP TABLE FACE_EMBEDDINGS");
                s.execute("UPDATE SCHEMAINFO SET version=3");
                s.execute("INSERT INTO MEDIAFILES (PATHSTORAGE, COLLECTIONNAME, NAME) VALUES ('/old/a.jpg', '/old', 'a.jpg')");
            }
            App.prepareSearchDB(con);
            // as a database of version 4 (face tables without model column)
            try (Statement s = con.createStatement()) {
                s.execute("ALTER TABLE FACE_EMBEDDINGS DROP COLUMN MODEL");
                s.execute("UPDATE SCHEMAINFO SET version=4");
            }
            App.prepareSearchDB(con);
            try (Statement s = con.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM MEDIAFILES")) {
                rs.next();
                check(rs.getInt(1) == 1, "migration keeps the indexed files");
            }

            String path = "/old/a.jpg";
            FaceRegion anna = new FaceRegion(0.1, 0.2, 0.05, 0.08, "Anna", true);
            FaceRegion unknown = new FaceRegion(0.5, 0.5, 0.1, 0.1, null, false);
            FaceStore.writeFaces(con, path, List.of(anna, unknown));
            check(FaceStore.confirmedWithoutEmbedding(con, AURA, 10).size() == 1, "Anna needs an embedding");
            float[] e = FaceEmbedder.normalize(new float[]{1, 2, 3});
            // position after an XMP round trip differs in the last digits
            FaceStore.putEmbedding(con, path, anna.withBounds(0.1000004, 0.2, 0.05, 0.0799996), AURA, e);
            FaceStore.putEmbedding(con, path, unknown, AURA, e);
            check(FaceStore.confirmedWithoutEmbedding(con, AURA, 10).isEmpty(), "embedding matched by position");
            // the other model has its own vectors
            check(FaceStore.confirmedWithoutEmbedding(con, SFACE, 10).size() == 1, "SFace vector still missing");
            check(FaceStore.knownFaces(con, SFACE).isEmpty(), "no SFace known faces");
            float[] small = FaceEmbedder.normalize(new float[]{3, 1});
            FaceStore.putEmbedding(con, path, anna, SFACE, small);
            check(FaceStore.embedding(con, path, anna, AURA).length == 3, "AuraFace vector kept");
            check(FaceStore.embedding(con, path, anna, SFACE).length == 2, "SFace vector stored");
            List<FaceStore.KnownFace> known = FaceStore.knownFaces(con, AURA);
            check(known.size() == 1 && known.get(0).name().equals("Anna"), "known faces " + known);
            check(FaceStore.embedding(con, path, unknown, AURA) != null, "embedding of unknown face");
            check(FaceStore.names(con).equals(List.of("Anna")), "names");
            check("Anna".equals(FaceService.match(known, e, 0.3)), "match");
            check(FaceService.match(known, FaceEmbedder.normalize(new float[]{3, -2, 0}), 0.3) == null, "no match");

            // renamed: the embedding stays; removed face: its embedding goes
            FaceStore.writeFaces(con, path, List.of(anna.withName("Anna Maria", true)));
            check(FaceStore.knownFaces(con, AURA).get(0).name().equals("Anna Maria"), "renamed keeps embedding");
            check(FaceStore.embedding(con, path, unknown, AURA) == null, "embedding of removed face deleted");

            FaceStore.relocate(con, "/old", "/new");
            check(FaceStore.embedding(con, "/new/a.jpg", anna, AURA) != null, "relocated");
            try (Statement s = con.createStatement()) {
                s.execute("UPDATE MEDIAFILES SET PATHSTORAGE='/new/a.jpg', COLLECTIONNAME='/new'");
            }
            FaceStore.deleteCollection(con, "/new");
            check(FaceStore.knownFaces(con, AURA).isEmpty() && FaceStore.names(con).isEmpty(), "collection removed");
        }
        System.out.println("FaceStoreVerify OK");
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAILED: " + what);
            System.exit(1);
        }
    }
}
