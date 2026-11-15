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
 * XMP sidecars (rebuilt by every scan) plus the faces the background scan
 * found in files without faces in their XMP (AUTO, only here), FACE_SCANS
 * records the files the background scan has done, FACE_EMBEDDINGS keeps the feature
 * vectors, separately for each recognizer (column MODEL). Embeddings are matched to faces by position, so renaming a face
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

    /** A face of the background scan with its feature vector (name = current suggestion or null). */
    public record AutoFace(String path, int idx, String name, float[] embedding) {
    }

    /** A file the background scan still has to do. */
    public record ScanItem(String path, long lastModified) {
    }

    private FaceStore() {
    }

    /**
     * Replaces the faces of the file by those of its XMP; embeddings of
     * removed faces are deleted. Faces in the XMP replace those of the
     * background scan; without faces in the XMP the ones of the scan stay.
     */
    public static void writeFaces(Connection con, String path, List<FaceRegion> faces) throws SQLException {
        try (PreparedStatement delete = con.prepareStatement(faces.isEmpty()
                ? "DELETE FROM FACES WHERE PATHSTORAGE=? AND NOT AUTO" : "DELETE FROM FACES WHERE PATHSTORAGE=?")) {
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

    /**
     * Replaces the faces the background scan found in the file (suggested
     * names unconfirmed). Only for files without faces in their XMP.
     */
    public static void writeAutoFaces(Connection con, String path, List<FaceRegion> faces) throws SQLException {
        try (PreparedStatement delete = con.prepareStatement("DELETE FROM FACES WHERE PATHSTORAGE=? AND AUTO")) {
            delete.setString(1, path);
            delete.executeUpdate();
        }
        if (faces.isEmpty()) {
            return;
        }
        try (PreparedStatement insert = con.prepareStatement(
                "INSERT INTO FACES (PATHSTORAGE, IDX, NAME, CONFIRMED, X, Y, W, H, AUTO) VALUES (?,?,?,FALSE,?,?,?,?,TRUE)")) {
            int i = 0;
            for (FaceRegion r : faces) {
                insert.setString(1, path);
                insert.setInt(2, i++);
                if (r.isUnknown()) {
                    insert.setNull(3, Types.VARCHAR);
                } else {
                    insert.setString(3, r.name());
                }
                insert.setDouble(4, r.x());
                insert.setDouble(5, r.y());
                insert.setDouble(6, r.w());
                insert.setDouble(7, r.h());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /** true if the file has faces from its XMP (not only ones of the background scan). */
    public static boolean hasXmpFaces(Connection con, String path) throws SQLException {
        try (PreparedStatement stm = con.prepareStatement("SELECT 1 FROM FACES WHERE PATHSTORAGE=? AND NOT AUTO LIMIT 1")) {
            stm.setString(1, path);
            try (ResultSet rs = stm.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * The searchable names of the file: the confirmed ones and the
     * suggestions of the background scan, each once ("; " separated), null
     * if none.
     */
    public static String searchText(Connection con, String path) throws SQLException {
        StringBuilder text = new StringBuilder();
        try (PreparedStatement stm = con.prepareStatement("SELECT DISTINCT NAME FROM FACES WHERE PATHSTORAGE=? "
                + "AND NAME IS NOT NULL AND (CONFIRMED OR AUTO) ORDER BY NAME")) {
            stm.setString(1, path);
            try (ResultSet rs = stm.executeQuery()) {
                while (rs.next()) {
                    if (text.length() > 0) {
                        text.append("; ");
                    }
                    text.append(rs.getString(1));
                }
            }
        }
        return text.length() == 0 ? null : text.toString();
    }

    /** Writes {@link #searchText} into the full text indexed column MEDIAFILES.FACES. */
    public static void refreshSearchText(Connection con, String path) throws SQLException {
        try (PreparedStatement stm = con.prepareStatement("UPDATE MEDIAFILES SET FACES=? WHERE PATHSTORAGE=?")) {
            String text = searchText(con, path);
            if (text == null) {
                stm.setNull(1, Types.VARCHAR);
            } else {
                stm.setString(1, text);
            }
            stm.setString(2, path);
            stm.executeUpdate();
        }
    }

    /** All faces of the background scan that have a feature vector of the model. */
    public static List<AutoFace> autoFaces(Connection con, String model) throws SQLException {
        List<AutoFace> result = new ArrayList<>();
        try (PreparedStatement stm = con.prepareStatement("SELECT f.PATHSTORAGE, f.IDX, f.NAME, e.EMBEDDING FROM FACES f "
                + "JOIN FACE_EMBEDDINGS e ON f.PATHSTORAGE=e.PATHSTORAGE AND e.MODEL=? AND " + SAME_FACE
                + " WHERE f.AUTO")) {
            stm.setString(1, model);
            try (ResultSet rs = stm.executeQuery()) {
                while (rs.next()) {
                    result.add(new AutoFace(rs.getString(1), rs.getInt(2), rs.getString(3), fromBytes(rs.getBytes(4))));
                }
            }
        }
        return result;
    }

    /** Sets the suggested name of a face of the background scan (null = unknown). */
    public static void setAutoName(Connection con, String path, int idx, String name) throws SQLException {
        try (PreparedStatement stm = con.prepareStatement("UPDATE FACES SET NAME=? WHERE PATHSTORAGE=? AND IDX=? AND AUTO")) {
            if (name == null) {
                stm.setNull(1, Types.VARCHAR);
            } else {
                stm.setString(1, name);
            }
            stm.setString(2, path);
            stm.setInt(3, idx);
            stm.executeUpdate();
        }
    }

    /**
     * The files of the collection the background scan has not done yet with
     * the model in their current state (new, changed or done with another
     * model), sorted by path.
     */
    public static List<ScanItem> unscannedFiles(Connection con, String collectionName, String model) throws SQLException {
        List<ScanItem> result = new ArrayList<>();
        try (PreparedStatement stm = con.prepareStatement("SELECT m.PATHSTORAGE, m.LASTMODIFIED FROM MEDIAFILES m "
                + "LEFT JOIN FACE_SCANS s ON s.PATHSTORAGE=m.PATHSTORAGE WHERE m.COLLECTIONNAME=? AND "
                + "(s.PATHSTORAGE IS NULL OR s.MODEL<>? OR s.LASTMODIFIED<>m.LASTMODIFIED) ORDER BY m.PATHSTORAGE")) {
            stm.setString(1, collectionName);
            stm.setString(2, model);
            try (ResultSet rs = stm.executeQuery()) {
                while (rs.next()) {
                    result.add(new ScanItem(rs.getString(1), rs.getLong(2)));
                }
            }
        }
        return result;
    }

    /** Forgets the background scans of the files of the collection: the next scan does them all again. */
    public static void forgetScans(Connection con, String collectionName) throws SQLException {
        try (PreparedStatement stm = con.prepareStatement("DELETE FROM FACE_SCANS "
                + "WHERE PATHSTORAGE IN (SELECT PATHSTORAGE FROM MEDIAFILES WHERE COLLECTIONNAME=?)")) {
            stm.setString(1, collectionName);
            stm.executeUpdate();
        }
    }

    /** Records that the background scan has done the file in this state. */
    public static void markScanned(Connection con, String path, String model, long lastModified) throws SQLException {
        try (PreparedStatement stm = con.prepareStatement(
                "MERGE INTO FACE_SCANS (PATHSTORAGE, MODEL, LASTMODIFIED) KEY(PATHSTORAGE) VALUES (?,?,?)")) {
            stm.setString(1, path);
            stm.setString(2, model);
            stm.setLong(3, lastModified);
            stm.executeUpdate();
        }
    }

    /** Removes faces and embeddings of the file. */
    public static void deleteFile(Connection con, String path) throws SQLException {
        for (String table : new String[]{"FACES", "FACE_EMBEDDINGS", "FACE_SCANS"}) {
            try (PreparedStatement stm = con.prepareStatement("DELETE FROM " + table + " WHERE PATHSTORAGE=?")) {
                stm.setString(1, path);
                stm.executeUpdate();
            }
        }
    }

    /** Removes faces and embeddings of all files of the collection (before the files are removed). */
    public static void deleteCollection(Connection con, String collectionName) throws SQLException {
        for (String table : new String[]{"FACES", "FACE_EMBEDDINGS", "FACE_SCANS"}) {
            try (PreparedStatement stm = con.prepareStatement("DELETE FROM " + table
                    + " WHERE PATHSTORAGE IN (SELECT PATHSTORAGE FROM MEDIAFILES WHERE COLLECTIONNAME=?)")) {
                stm.setString(1, collectionName);
                stm.executeUpdate();
            }
        }
    }

    /** Rewrites the paths below oldRoot to newRoot. */
    public static void relocate(Connection con, String oldRoot, String newRoot) throws SQLException {
        for (String table : new String[]{"FACES", "FACE_EMBEDDINGS", "FACE_SCANS"}) {
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

    /** Stores the feature vector of a face (replaces one of the model at the same position). */
    public static void putEmbedding(Connection con, String path, FaceRegion r, String model, float[] embedding)
            throws SQLException {
        try (PreparedStatement delete = con.prepareStatement("DELETE FROM FACE_EMBEDDINGS f WHERE f.PATHSTORAGE=? AND "
                + "f.MODEL=? AND ABS(f.X-?)<" + SAME + " AND ABS(f.Y-?)<" + SAME + " AND ABS(f.W-?)<" + SAME
                + " AND ABS(f.H-?)<" + SAME)) {
            delete.setString(1, path);
            delete.setString(2, model);
            delete.setDouble(3, r.x());
            delete.setDouble(4, r.y());
            delete.setDouble(5, r.w());
            delete.setDouble(6, r.h());
            delete.executeUpdate();
        }
        try (PreparedStatement insert = con.prepareStatement(
                "INSERT INTO FACE_EMBEDDINGS (PATHSTORAGE, MODEL, X, Y, W, H, EMBEDDING) VALUES (?,?,?,?,?,?,?)")) {
            insert.setString(1, path);
            insert.setString(2, model);
            insert.setDouble(3, r.x());
            insert.setDouble(4, r.y());
            insert.setDouble(5, r.w());
            insert.setDouble(6, r.h());
            insert.setBytes(7, toBytes(embedding));
            insert.executeUpdate();
        }
    }

    /** @return the stored feature vector of the face by the model, null if none */
    public static float[] embedding(Connection con, String path, FaceRegion r, String model) throws SQLException {
        try (PreparedStatement stm = con.prepareStatement("SELECT EMBEDDING FROM FACE_EMBEDDINGS f WHERE f.PATHSTORAGE=? AND "
                + "f.MODEL=? AND ABS(f.X-?)<" + SAME + " AND ABS(f.Y-?)<" + SAME + " AND ABS(f.W-?)<" + SAME
                + " AND ABS(f.H-?)<" + SAME)) {
            stm.setString(1, path);
            stm.setString(2, model);
            stm.setDouble(3, r.x());
            stm.setDouble(4, r.y());
            stm.setDouble(5, r.w());
            stm.setDouble(6, r.h());
            try (ResultSet rs = stm.executeQuery()) {
                return rs.next() ? fromBytes(rs.getBytes(1)) : null;
            }
        }
    }

    /** All confirmed faces that have a feature vector of the model. */
    public static List<KnownFace> knownFaces(Connection con, String model) throws SQLException {
        List<KnownFace> result = new ArrayList<>();
        try (PreparedStatement stm = con.prepareStatement("SELECT f.NAME, e.EMBEDDING FROM FACES f JOIN FACE_EMBEDDINGS e "
                + "ON f.PATHSTORAGE=e.PATHSTORAGE AND e.MODEL=? AND " + SAME_FACE
                + " WHERE f.CONFIRMED AND f.NAME IS NOT NULL")) {
            stm.setString(1, model);
            try (ResultSet rs = stm.executeQuery()) {
                while (rs.next()) {
                    result.add(new KnownFace(rs.getString(1), fromBytes(rs.getBytes(2))));
                }
            }
        }
        return result;
    }

    /**
     * Confirmed faces without feature vector of the model (e.g. after the
     * database was built again from the XMP files, or the model was changed).
     */
    public static List<PendingFace> confirmedWithoutEmbedding(Connection con, String model, int limit)
            throws SQLException {
        List<PendingFace> result = new ArrayList<>();
        try (PreparedStatement stm = con.prepareStatement("SELECT f.PATHSTORAGE, f.X, f.Y, f.W, f.H, f.NAME FROM FACES f "
                + "WHERE f.CONFIRMED AND NOT EXISTS (SELECT 1 FROM FACE_EMBEDDINGS e WHERE e.PATHSTORAGE=f.PATHSTORAGE AND "
                + "e.MODEL=? AND " + SAME_FACE + ") LIMIT ?")) {
            stm.setString(1, model);
            stm.setInt(2, limit);
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
