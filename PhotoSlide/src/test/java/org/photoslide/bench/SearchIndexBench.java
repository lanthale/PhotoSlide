package org.photoslide.bench;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.h2.fulltext.FullText;
import org.photoslide.App;
import org.photoslide.search.SRMediaLoadingTask;

/**
 * Times the search database: the former indexing (one query and one commit
 * per file, all columns in the full text index) against the current one (one
 * query per collection, batched commits, text columns only), for the first
 * scan and for a scan without changes, and the search with and without an
 * incomplete last word. Synthetic rows, no files are read. Not a unit test:
 * run it with
 *
 * <pre>
 * mvn test-compile
 * java -cp target/test-classes:target/classes:&lt;dependencies&gt; org.photoslide.bench.SearchIndexBench [files]
 * </pre>
 */
public final class SearchIndexBench {

    private static final String[] PLACES = {"Wien", "Wienerwald", "Graz", "Salzburg", "Innsbruck", "Linz", "Bregenz", "Venedig"};
    private static final String[] CAMERAS = {"Canon EOS R5", "Nikon Z6", "Sony A7 IV", "iPhone 15 Pro"};

    private SearchIndexBench() {
    }

    public static void main(String[] args) throws Exception {
        int files = args.length > 0 ? Integer.parseInt(args[0]) : 3000;
        Path dir = Files.createTempDirectory("searchbench");
        try {
            try (Connection con = DriverManager.getConnection("jdbc:h2:" + dir.resolve("old"))) {
                createOldSchema(con);
                long t0 = System.nanoTime();
                oldScan(con, files);
                long t1 = System.nanoTime();
                oldScan(con, files);
                long t2 = System.nanoTime();
                System.out.printf("former:  first scan %6d ms, unchanged scan %6d ms%n", (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000);
            }
            try (Connection con = DriverManager.getConnection("jdbc:h2:" + dir.resolve("new"))) {
                App.prepareSearchDB(con);
                long t0 = System.nanoTime();
                newScan(con, files);
                long t1 = System.nanoTime();
                newScan(con, files);
                long t2 = System.nanoTime();
                System.out.printf("current: first scan %6d ms, unchanged scan %6d ms%n", (t1 - t0) / 1_000_000, (t2 - t1) / 1_000_000);
                search(con, "Wien");
                search(con, "Wie");
                search(con, "canon wie");
                search(con, "O'Brien");
                search(con, "Urlaub");
                App.prepareSearchDB(con);
                try (Statement stm = con.createStatement(); ResultSet rs = stm.executeQuery("SELECT COUNT(*) FROM MEDIAFILES")) {
                    rs.next();
                    System.out.println("rows after prepareSearchDB of the current version: " + rs.getInt(1));
                }
            }
        } finally {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    private static void search(Connection con, String text) throws Exception {
        long t0 = System.nanoTime();
        Set<String> found = SRMediaLoadingTask.find(con, text, 500);
        System.out.printf("search %-12s %4d files in %4d ms%n", "\"" + text + "\"", found.size(), (System.nanoTime() - t0) / 1_000_000);
    }

    private static String path(int i) {
        return "/Volumes/Photos/" + (i % 7 == 0 ? "Urlaub " : "Album ") + (2000 + i % 25) + "/" + (i % 50 == 0 ? "O'Brien " : "IMG_") + i + ".jpg";
    }

    /** About the former search string: key:value; of all EXIF and IPTC entries. */
    private static String metadata(Random rnd) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("Tag Name ").append(i).append(':').append(rnd.nextInt(10000)).append(" value").append(rnd.nextInt(3000)).append(';');
        }
        return sb.substring(0, Math.min(sb.length(), 3900));
    }

    private static void createOldSchema(Connection con) throws Exception {
        try (Statement stat = con.createStatement()) {
            FullText.init(con);
            FullText.setWhitespaceChars(con, " ;-:/.\\");
            stat.execute("CREATE TABLE MEDIAFILES(collectionname VARCHAR(255) NOT NULL, name VARCHAR(255) NOT NULL, pathStorage VARCHAR(1000) NOT NULL, title VARCHAR(255), keywords VARCHAR(255), camera VARCHAR(255), rating INTEGER, recordTime TIMESTAMP, creationTime TIMESTAMP, places VARCHAR(255), gpsposition VARCHAR(255), faces VARCHAR(255), metadata VARCHAR(4000), PRIMARY KEY (collectionname, name, pathStorage))");
            FullText.createIndex(con, "PUBLIC", "MEDIAFILES", null);
        }
    }

    /** Like the former SearchIndex: a query per file, an insert with autocommit per new file. */
    private static void oldScan(Connection con, int files) throws Exception {
        Random rnd = new Random(1);
        for (int i = 0; i < files; i++) {
            String path = path(i).replace("'", "''");
            String name = path.substring(path.lastIndexOf('/') + 1);
            String meta = metadata(rnd);
            try (Statement stm = con.createStatement(); ResultSet rs = stm.executeQuery("SELECT name, places from MediaFiles where NAME='" + name + "' and PATHSTORAGE='" + path + "'")) {
                if (rs.next()) {
                    continue;
                }
            }
            try (Statement stm = con.createStatement()) {
                stm.execute("INSERT INTO MediaFiles VALUES('/Volumes/Photos','" + name + "','" + path + "',NULL,'urlaub;familie','" + CAMERAS[i % CAMERAS.length] + "'," + (i % 6) + ",ts '2020-01-01',ts '2020-01-01','" + PLACES[i % PLACES.length] + "',NULL,NULL,'" + meta + "')");
            }
        }
    }

    /** Like the current SearchIndex: one query, batched merges of new files. */
    private static void newScan(Connection con, int files) throws Exception {
        Map<String, Long> indexed = new HashMap<>();
        try (PreparedStatement stm = con.prepareStatement("SELECT PATHSTORAGE, LASTMODIFIED FROM MEDIAFILES WHERE COLLECTIONNAME=?")) {
            stm.setString(1, "/Volumes/Photos");
            try (ResultSet rs = stm.executeQuery()) {
                while (rs.next()) {
                    indexed.put(rs.getString(1), rs.getLong(2));
                }
            }
        }
        Random rnd = new Random(1);
        con.setAutoCommit(false);
        try (PreparedStatement merge = con.prepareStatement("MERGE INTO MEDIAFILES (PATHSTORAGE, COLLECTIONNAME, NAME, KEYWORDS, CAMERA, RATING, PLACES, METADATA, LASTMODIFIED) KEY(PATHSTORAGE) VALUES(?,?,?,?,?,?,?,?,?)")) {
            int pending = 0;
            for (int i = 0; i < files; i++) {
                String path = path(i);
                String meta = metadata(rnd);
                Long known = indexed.remove(path);
                if (known != null && known == 1L) {
                    continue;
                }
                merge.setString(1, path);
                merge.setString(2, "/Volumes/Photos");
                merge.setString(3, path.substring(path.lastIndexOf('/') + 1));
                merge.setString(4, "urlaub;familie");
                merge.setString(5, CAMERAS[i % CAMERAS.length]);
                merge.setInt(6, i % 6);
                merge.setString(7, PLACES[i % PLACES.length]);
                // the current search string has values only, no keys
                merge.setString(8, meta.replaceAll("Tag Name \\d+:", ""));
                merge.setLong(9, 1L);
                merge.addBatch();
                if (++pending >= 200) {
                    merge.executeBatch();
                    con.commit();
                    pending = 0;
                }
            }
            merge.executeBatch();
            con.commit();
        } finally {
            con.setAutoCommit(true);
        }
    }
}
