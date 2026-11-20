package org.photoslide.browsercollections.library;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.photoslide.I18n;
import org.photoslide.Utility;
import org.photoslide.datamodel.FileTypes;

/**
 * Reads the macOS Photos library ({@code *.photoslibrary}) from its database
 * {@code database/Photos.sqlite} (Photos 5 and later, macOS 10.15+).
 * <p>
 * The library is never changed: the database is copied to the application
 * data (with its write ahead log, so recent changes are included) and only
 * the copy is opened; it is copied again when the library changed.
 * <p>
 * The database is a Core Data store whose table and column names change
 * between macOS versions: the asset table and the table joining albums and
 * assets are looked up when the database is opened.
 * <p>
 * Reading the library needs "Full Disk Access" for PhotoSlide (System
 * Settings, Privacy &amp; Security), the library is protected by macOS.
 */
final class ApplePhotosProvider implements LibraryProvider {

    private static final Logger LOG = Logger.getLogger(ApplePhotosProvider.class.getName());
    /** seconds between 1970-01-01 and 2001-01-01, the epoch of Core Data */
    private static final long CORE_DATA_EPOCH = 978307200L;
    private static final int KIND_ALBUM = 2;
    private static final int KIND_FOLDER = 4000;
    private static final int KIND_ROOT_FOLDER = 3999;
    private static final Pattern JOIN_TABLE = Pattern.compile("Z_\\d+ASSETS");

    static final String NODE_ALBUMS = "albums";
    static final String NODE_YEARS = "years";
    private static final String PREFIX_FOLDER = "folder-";
    private static final String PREFIX_ALBUM = "album-";
    private static final String PREFIX_YEAR = "year-";
    private static final String PREFIX_MONTH = "month-";

    private final Path root;
    /** original file names of the files returned by assets() */
    private final java.util.Map<Path, String> originalNames = new java.util.concurrent.ConcurrentHashMap<>();
    private String originalNameJoin = "";
    private String originalNameColumn = "NULL";
    private Connection connection;
    private long databaseStamp;
    // schema of the opened database
    private String assetTable;
    private String joinTable;
    private String joinAlbumColumn;
    private String joinAssetColumn;
    private String visibleCondition;
    private int rootFolder;

    ApplePhotosProvider(Path root) {
        this.root = root;
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public String displayName(Path file) {
        return originalNames.get(file);
    }

    @Override
    public String accessProblem() {
        return I18n.t("library.photosNoAccess", root.toString());
    }

    @Override
    public synchronized List<LibraryNode> topLevel() throws IOException {
        connection();
        return List.of(
                new LibraryNode(NODE_ALBUMS, I18n.t("library.albums"), LibraryNode.Kind.FOLDER, true),
                new LibraryNode(NODE_YEARS, I18n.t("library.byDate"), LibraryNode.Kind.EVENT, true));
    }

    @Override
    public synchronized List<LibraryNode> children(String nodeId) throws IOException {
        Connection c = connection();
        try {
            if (nodeId.equals(NODE_ALBUMS)) {
                return albums(c, rootFolder);
            }
            if (nodeId.startsWith(PREFIX_FOLDER)) {
                return albums(c, Integer.parseInt(nodeId.substring(PREFIX_FOLDER.length())));
            }
            if (nodeId.equals(NODE_YEARS)) {
                return years(c);
            }
            if (nodeId.startsWith(PREFIX_YEAR)) {
                return months(c, nodeId.substring(PREFIX_YEAR.length()));
            }
            return List.of();
        } catch (SQLException | NumberFormatException ex) {
            throw new IOException("Cannot read " + nodeId + " of " + root, ex);
        }
    }

    @Override
    public synchronized List<Path> assets(String nodeId) throws IOException {
        Connection c = connection();
        String sql;
        String param;
        if (nodeId.startsWith(PREFIX_ALBUM)) {
            sql = "SELECT a.ZDIRECTORY, a.ZFILENAME, a.ZUUID, " + originalNameColumn + " FROM " + assetTable + " a" + originalNameJoin + " JOIN " + joinTable
                    + " j ON j." + joinAssetColumn + " = a.Z_PK WHERE j." + joinAlbumColumn + " = ? AND "
                    + visibleCondition + " ORDER BY a.ZDATECREATED";
            param = nodeId.substring(PREFIX_ALBUM.length());
        } else if (nodeId.startsWith(PREFIX_MONTH)) {
            sql = "SELECT a.ZDIRECTORY, a.ZFILENAME, a.ZUUID, " + originalNameColumn + " FROM " + assetTable + " a" + originalNameJoin + " WHERE "
                    + dateExpr("%Y-%m") + " = ? AND " + visibleCondition + " ORDER BY a.ZDATECREATED";
            param = nodeId.substring(PREFIX_MONTH.length());
        } else if (nodeId.startsWith(PREFIX_YEAR)) {
            sql = "SELECT a.ZDIRECTORY, a.ZFILENAME, a.ZUUID, " + originalNameColumn + " FROM " + assetTable + " a" + originalNameJoin + " WHERE "
                    + dateExpr("%Y") + " = ? AND " + visibleCondition + " ORDER BY a.ZDATECREATED";
            param = nodeId.substring(PREFIX_YEAR.length());
        } else {
            return List.of();
        }
        List<Path> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Path file = localFile(rs.getString(1), rs.getString(2), rs.getString(3));
                    if (file != null) {
                        result.add(file);
                        String original = rs.getString(4);
                        if (original != null && !original.isBlank()) {
                            originalNames.put(file, original);
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            throw new IOException("Cannot read the media files of " + nodeId + " in " + root, ex);
        }
        return result;
    }

    private List<LibraryNode> albums(Connection c, int parent) throws SQLException {
        List<LibraryNode> result = new ArrayList<>();
        String sql = "SELECT Z_PK, ZTITLE, ZKIND FROM ZGENERICALBUM WHERE ZPARENTFOLDER = ? AND ZKIND IN ("
                + KIND_ALBUM + "," + KIND_FOLDER + ") AND (ZTRASHEDSTATE IS NULL OR ZTRASHEDSTATE = 0)"
                + " ORDER BY ZKIND DESC, ZTITLE COLLATE NOCASE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, parent);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int pk = rs.getInt(1);
                    String title = rs.getString(2);
                    if (title == null || title.isBlank()) {
                        title = "(" + pk + ")";
                    }
                    if (rs.getInt(3) == KIND_FOLDER) {
                        result.add(new LibraryNode(PREFIX_FOLDER + pk, title, LibraryNode.Kind.FOLDER, true));
                    } else {
                        result.add(new LibraryNode(PREFIX_ALBUM + pk, title, LibraryNode.Kind.ALBUM, false));
                    }
                }
            }
        }
        return result;
    }

    private List<LibraryNode> years(Connection c) throws SQLException {
        List<LibraryNode> result = new ArrayList<>();
        String sql = "SELECT " + dateExpr("%Y") + " y, COUNT(*) FROM " + assetTable + " a WHERE "
                + visibleCondition + " AND a.ZDATECREATED IS NOT NULL GROUP BY y ORDER BY y DESC";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String year = rs.getString(1);
                if (year != null) {
                    result.add(new LibraryNode(PREFIX_YEAR + year, year + " (" + rs.getInt(2) + ")",
                            LibraryNode.Kind.EVENT, true));
                }
            }
        }
        return result;
    }

    private List<LibraryNode> months(Connection c, String year) throws SQLException {
        List<LibraryNode> result = new ArrayList<>();
        String sql = "SELECT " + dateExpr("%m") + " m, COUNT(*) FROM " + assetTable + " a WHERE "
                + dateExpr("%Y") + " = ? AND " + visibleCondition + " GROUP BY m ORDER BY m";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String month = rs.getString(1);
                    if (month == null) {
                        continue;
                    }
                    String name = Month.of(Integer.parseInt(month)).getDisplayName(TextStyle.FULL, I18n.locale())
                            + " " + year + " (" + rs.getInt(2) + ")";
                    result.add(new LibraryNode(PREFIX_MONTH + year + "-" + month, name, LibraryNode.Kind.EVENT, false));
                }
            }
        }
        return result;
    }

    /** strftime of the creation date of the asset (alias a), in local time. */
    private static String dateExpr(String format) {
        return "strftime('" + format + "', a.ZDATECREATED + " + CORE_DATA_EPOCH + ", 'unixepoch', 'localtime')";
    }

    /**
     * The original, or if it is only in iCloud ("Optimize Mac Storage") the
     * largest preview Photos keeps on this computer; null if neither exists.
     */
    private Path localFile(String directory, String fileName, String uuid) {
        if (directory == null || fileName == null) {
            return null;
        }
        Path original = root.resolve("originals").resolve(directory).resolve(fileName);
        if (Files.exists(original)) {
            return original;
        }
        if (uuid == null) {
            return null;
        }
        Path derivatives = root.resolve("resources").resolve("derivatives").resolve(directory);
        Path best = null;
        long bestSize = -1;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(derivatives, uuid + "*")) {
            for (Path p : ds) {
                if (!FileTypes.isValidType(p.getFileName().toString())) {
                    continue;
                }
                long size = Files.size(p);
                if (size > bestSize) {
                    best = p;
                    bestSize = size;
                }
            }
        } catch (IOException ex) {
            return null;
        }
        return best;
    }

    /**
     * The connection to a current copy of the database: copied again if the
     * database or its log changed since.
     */
    private Connection connection() throws IOException {
        Path db = root.resolve("database").resolve("Photos.sqlite");
        Path wal = db.resolveSibling("Photos.sqlite-wal");
        // macOS refuses opening the file without Full Disk Access (a test of
        // the permissions alone says readable)
        try (InputStream in = Files.newInputStream(db)) {
            in.read();
        } catch (IOException ex) {
            closeConnection();
            throw new IOException(accessProblem(), ex);
        }
        long stamp = Files.getLastModifiedTime(db).toMillis() * 31
                + (Files.exists(wal) ? Files.getLastModifiedTime(wal).toMillis() : 0);
        if (connection != null && stamp == databaseStamp) {
            return connection;
        }
        closeConnection();
        Path copyDir = Path.of(Utility.getAppData(), "library-cache",
                Integer.toHexString(root.toString().hashCode()));
        Files.createDirectories(copyDir);
        Path copy = copyDir.resolve("Photos.sqlite");
        Files.deleteIfExists(copyDir.resolve("Photos.sqlite-shm"));
        Files.deleteIfExists(copyDir.resolve("Photos.sqlite-wal"));
        Files.copy(db, copy, StandardCopyOption.REPLACE_EXISTING);
        if (Files.exists(wal)) {
            Files.copy(wal, copyDir.resolve("Photos.sqlite-wal"), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + copy.toAbsolutePath());
            readSchema(connection);
        } catch (SQLException ex) {
            closeConnection();
            throw new IOException(I18n.t("library.photosUnknownFormat", root.toString()), ex);
        }
        databaseStamp = stamp;
        LOG.log(Level.INFO, "Opened Photos library {0}: assets {1}, albums joined by {2}",
                new Object[]{root, assetTable, joinTable});
        return connection;
    }

    private void readSchema(Connection c) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        if (tables.contains("ZASSET")) {
            assetTable = "ZASSET";
        } else if (tables.contains("ZGENERICASSET")) {
            assetTable = "ZGENERICASSET";
        } else {
            throw new SQLException("No asset table");
        }
        if (!tables.contains("ZGENERICALBUM")) {
            throw new SQLException("No album table");
        }
        Set<String> assetColumns = columns(c, assetTable);
        for (String needed : List.of("Z_PK", "ZDIRECTORY", "ZFILENAME", "ZUUID", "ZDATECREATED")) {
            if (!assetColumns.contains(needed)) {
                throw new SQLException("Column " + needed + " missing in " + assetTable);
            }
        }
        StringBuilder visible = new StringBuilder("(a.ZTRASHEDSTATE IS NULL OR a.ZTRASHEDSTATE = 0)");
        if (assetColumns.contains("ZHIDDEN")) {
            visible.append(" AND (a.ZHIDDEN IS NULL OR a.ZHIDDEN = 0)");
        }
        if (assetColumns.contains("ZVISIBILITYSTATE")) {
            // the not chosen photos of a burst are not visible
            visible.append(" AND (a.ZVISIBILITYSTATE IS NULL OR a.ZVISIBILITYSTATE = 0)");
        }
        visibleCondition = visible.toString();
        joinTable = null;
        for (String table : tables) {
            if (!JOIN_TABLE.matcher(table).matches()) {
                continue;
            }
            String albumCol = null;
            String assetCol = null;
            for (String col : columns(c, table)) {
                if (col.startsWith("Z_FOK")) {
                    continue;
                }
                if (col.endsWith("ALBUMS")) {
                    albumCol = col;
                } else if (col.endsWith("ASSETS")) {
                    assetCol = col;
                }
            }
            if (albumCol != null && assetCol != null) {
                joinTable = table;
                joinAlbumColumn = albumCol;
                joinAssetColumn = assetCol;
                break;
            }
        }
        if (joinTable == null) {
            throw new SQLException("No table joining albums and assets");
        }
        // the original file name is optional: without it the name in the library is shown
        originalNameJoin = "";
        originalNameColumn = "NULL";
        if (tables.contains("ZADDITIONALASSETATTRIBUTES")) {
            Set<String> cols = columns(c, "ZADDITIONALASSETATTRIBUTES");
            if (cols.contains("ZASSET") && cols.contains("ZORIGINALFILENAME")) {
                originalNameJoin = " LEFT JOIN ZADDITIONALASSETATTRIBUTES aa ON aa.ZASSET = a.Z_PK";
                originalNameColumn = "aa.ZORIGINALFILENAME";
            }
        }
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT MIN(Z_PK) FROM ZGENERICALBUM WHERE ZKIND = " + KIND_ROOT_FOLDER)) {
            rootFolder = rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static Set<String> columns(Connection c, String table) throws SQLException {
        Set<String> result = new HashSet<>();
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                result.add(rs.getString("name").toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ex) {
                LOG.log(Level.FINE, null, ex);
            }
            connection = null;
        }
    }
}
