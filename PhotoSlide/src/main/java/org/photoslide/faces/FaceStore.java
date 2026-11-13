package org.photoslide.faces;

import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import org.photoslide.datamodel.FaceRegion;

/**
 * Faces in the search database: the table FACES mirrors the regions of the
 * XMP sidecars (rebuilt by every scan), FACE_EMBEDDINGS keeps the feature
 * vectors. Embeddings are matched to faces by position, so renaming a face
 * keeps its embedding. All methods use the given connection and do not
 * commit.
 */
public final class FaceStore {

    /** Two rectangles closer than this (normalized) are the same face. */
    private static final double SAME = 0.002;
    private static final String SAME_FACE = "ABS(f.X-e.X)<" + SAME + " AND ABS(f.Y-e.Y)<" + SAME
            + " AND ABS(f.W-e.W)<" + SAME + " AND ABS(f.H-e.H)<" + SAME;

    /** A known (confirmed) face with its feature vector. */
    public record KnownFace(String name, float[] embedding) {
    }

    /** A face whose embedding still has to be computed. */
    public record PendingFace(String path, FaceRegion region) {
    }

    private FaceStore() {
    }

    /** Replaces the faces of the file; embeddings of removed faces are deleted. */
    public static void writeFaces(Connection con, String path, List<FaceRegion> faces) throws SQLException {
        try (PreparedStatement delete = con.prepareStatement("DELETE FROM FACES WHERE PATHSTORAGE=?")) {
            delete.setString(1, path);
            delete.executeUpdate();
        }
        if (!faces.isEmpty()) {
            try (PreparedStatement insert = con.prepareStatement(
                    "INSERT INTO FACES (PATHSTORAGE, IDX, NAME, CONFIRMED, X, Y, W, H) VALUES (?,?,?,?,?,?,?,?)")) {
                int i = 0;
                for (FaceRegion r : faces) {
                    insert.setString(1, path);
                    insert.setInt(2, i++);
                    if (r.isUnknown()) {
                        insert.setNull(3, Types.VARCHAR);
                    } else {
                        insert.setString(3, r.name());
                    }
                    insert.setBoolean(4, r.confirmed() && !r.isUnknown());
                    insert.setDouble(5, r.x());
                    insert.setDouble(6, r.y());
                    insert.setDouble(7, r.w());
                    insert.setDouble(8, r.h());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        }
        try (PreparedStatement orphans = con.prepareStatement("DELETE FROM FACE_EMBEDDINGS e WHERE e.PATHSTORAGE=? "
                + "AND NOT EXISTS (SELECT 1 FROM FACES f WHERE f.PATHSTORAGE=e.PATHSTORAGE AND " + SAME_FACE + ")")) {
            orphans.setString(1, path);
            orphans.executeUpdate();
        }
    }

    /** Removes faces and embeddings of the file. */
    public static void deleteFile(Connection con, String path) throws SQLException {
        for (String table : new String[]{"FACES", "FACE_EMBEDDINGS"}) {
            try (PreparedStatement stm = con.prepareStatement("DELETE FROM " + table + " WHERE PATHSTORAGE=?")) {
                stm.setString(1, path);
                stm.executeUpdate();
            }
        }
    }

    /** Removes faces and embeddings of all files of the collection (before the files are removed). */
    public static void deleteCollection(Connection con, String collectionName) throws SQLException {
        for (String table : new String[]{"FACES", "FACE_EMBEDDINGS"}) {
            try (PreparedStatement stm = con.prepareStatement("DELETE FROM " + table
                    + " WHERE PATHSTORAGE IN (SELECT PATHSTORAGE FROM MEDIAFILES WHERE COLLECTIONNAME=?)")) {
                stm.setString(1, collectionName);
                stm.executeUpdate();
            }
        }
    }

    /** Rewrites the paths below oldRoot to newRoot. */
    public static void relocate(Connection con, String oldRoot, String newRoot) throws SQLException {
        for (String table : new String[]{"FACES", "FACE_EMBEDDINGS"}) {
            try (PreparedStatement stm = con.prepareStatement("UPDATE " + table
                    + " SET PATHSTORAGE = ? || SUBSTRING(PATHSTORAGE, ?) WHERE LEFT(PATHSTORAGE, ?) = ?")) {
                stm.setString(1, newRoot);
                stm.setInt(2, oldRoot.length() + 1);
                stm.setInt(3, oldRoot.length());
                stm.setString(4, oldRoot);
                stm.executeUpdate();
            }
        }
    }

    /** Stores the feature vector of a face (replaces one at the same position). */
    public static void putEmbedding(Connection con, String path, FaceRegion r, float[] embedding) throws SQLException {
        try (PreparedStatement delete = con.prepareStatement("DELETE FROM FACE_EMBEDDINGS f WHERE f.PATHSTORAGE=? AND "
                + "ABS(f.X-?)<" + SAME + " AND ABS(f.Y-?)<" + SAME + " AND ABS(f.W-?)<" + SAME + " AND ABS(f.H-?)<" + SAME)) {
            delete.setString(1, path);
            delete.setDouble(2, r.x());
            delete.setDouble(3, r.y());
            delete.setDouble(4, r.w());
            delete.setDouble(5, r.h());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = con.prepareStatement(
                "INSERT INTO FACE_EMBEDDINGS (PATHSTORAGE, X, Y, W, H, EMBEDDING) VALUES (?,?,?,?,?,?)")) {
            insert.setString(1, path);
            insert.setDouble(2, r.x());
            insert.setDouble(3, r.y());
            insert.setDouble(4, r.w());
            insert.setDouble(5, r.h());
            insert.setBytes(6, toBytes(embedding));
            insert.executeUpdate();
        }
    }

    /** @return the stored feature vector of the face, null if none */
    public static float[] embedding(Connection con, String path, FaceRegion r) throws SQLException {
        try (PreparedStatement stm = con.prepareStatement("SELECT EMBEDDING FROM FACE_EMBEDDINGS f WHERE f.PATHSTORAGE=? AND "
                + "ABS(f.X-?)<" + SAME + " AND ABS(f.Y-?)<" + SAME + " AND ABS(f.W-?)<" + SAME + " AND ABS(f.H-?)<" + SAME)) {
            stm.setString(1, path);
            stm.setDouble(2, r.x());
            stm.setDouble(3, r.y());
            stm.setDouble(4, r.w());
            stm.setDouble(5, r.h());
            try (ResultSet rs = stm.executeQuery()) {
                return rs.next() ? fromBytes(rs.getBytes(1)) : null;
            }
        }
    }

    /** All confirmed faces that have a feature vector. */
    public static List<KnownFace> knownFaces(Connection con) throws SQLException {
        List<KnownFace> result = new ArrayList<>();
        try (PreparedStatement stm = con.prepareStatement("SELECT f.NAME, e.EMBEDDING FROM FACES f JOIN FACE_EMBEDDINGS e "
                + "ON f.PATHSTORAGE=e.PATHSTORAGE AND " + SAME_FACE + " WHERE f.CONFIRMED AND f.NAME IS NOT NULL");
                ResultSet rs = stm.executeQuery()) {
            while (rs.next()) {
                result.add(new KnownFace(rs.getString(1), fromBytes(rs.getBytes(2))));
            }
        }
        return result;
    }

    /** Confirmed faces without feature vector (e.g. after the database was built again from the XMP files). */
    public static List<PendingFace> confirmedWithoutEmbedding(Connection con, int limit) throws SQLException {
        List<PendingFace> result = new ArrayList<>();
        try (PreparedStatement stm = con.prepareStatement("SELECT f.PATHSTORAGE, f.X, f.Y, f.W, f.H, f.NAME FROM FACES f "
                + "WHERE f.CONFIRMED AND NOT EXISTS (SELECT 1 FROM FACE_EMBEDDINGS e WHERE e.PATHSTORAGE=f.PATHSTORAGE AND "
                + SAME_FACE + ") LIMIT ?")) {
            stm.setInt(1, limit);
            try (ResultSet rs = stm.executeQuery()) {
                while (rs.next()) {
                    result.add(new PendingFace(rs.getString(1), new FaceRegion(rs.getDouble(2), rs.getDouble(3),
                            rs.getDouble(4), rs.getDouble(5), rs.getString(6), true)));
                }
            }
        }
        return result;
    }

    /** All names of confirmed faces, sorted (for the input suggestions). */
    public static List<String> names(Connection con) throws SQLException {
        List<String> result = new ArrayList<>();
        try (PreparedStatement stm = con.prepareStatement(
                "SELECT DISTINCT NAME FROM FACES WHERE CONFIRMED AND NAME IS NOT NULL ORDER BY NAME");
                ResultSet rs = stm.executeQuery()) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }

    static byte[] toBytes(float[] v) {
        ByteBuffer b = ByteBuffer.allocate(v.length * 4);
        for (float f : v) {
            b.putFloat(f);
        }
        return b.array();
    }

    static float[] fromBytes(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes);
        float[] v = new float[bytes.length / 4];
        for (int i = 0; i < v.length; i++) {
            v[i] = b.getFloat();
        }
        return v;
    }
}
