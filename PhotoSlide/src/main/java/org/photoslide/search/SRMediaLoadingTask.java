/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;


import org.photoslide.I18n;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import org.h2.fulltext.FullText;
import org.photoslide.App;
import org.photoslide.MainViewController;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFile;

/**
 * Searches the index and adds the found files to the result list. The values
 * needed for the list (times) come from the index; the files are only read
 * for their edits.
 *
 * @author selfemp
 */
public class SRMediaLoadingTask extends Task<MediaFile> {

    private static final int MAX_RESULTS = 500;
    // number of words of the index tried for the last, maybe incomplete word
    private static final int MAX_PREFIX_WORDS = 20;
    private static final int CHUNK_SIZE = 50;

    private final String keyword;
    private final ObservableList<MediaFile> fullMediaList;
    private final MainViewController mainController;

    public SRMediaLoadingTask(String keyword, ObservableList<MediaFile> fullMediaList, MainViewController mv) {
        this.keyword = keyword;
        this.fullMediaList = fullMediaList;
        mainController = mv;
    }

    @Override
    protected MediaFile call() throws Exception {
        updateTitle(I18n.t("search.searching"));
        Connection con = App.getSearchDBConnection();
        if (con == null) {
            throw new IOException("Nothing found!");
        }
        Set<String> paths = find(con, keyword, MAX_RESULTS);
        if (paths.isEmpty()) {
            throw new IOException("Nothing found!");
        }
        if (isCancelled()) {
            return null;
        }
        List<String> vanished = new ArrayList<>();
        List<MediaFile> chunk = new ArrayList<>();
        try (PreparedStatement stm = con.prepareStatement("SELECT PATHSTORAGE, RECORDTIME, CREATIONTIME FROM MEDIAFILES WHERE PATHSTORAGE = ANY(?)")) {
            stm.setArray(1, con.createArrayOf("VARCHAR", paths.toArray()));
            try (ResultSet rs = stm.executeQuery()) {
                while (rs.next()) {
                    if (isCancelled()) {
                        return null;
                    }
                    String mediaURL = rs.getString(1);
                    Path path = Path.of(mediaURL);
                    if (Files.exists(path) == false) {
                        // folder missing: the drive may be unmounted, keep the file in the index
                        if (Files.isDirectory(path.getParent())) {
                            vanished.add(mediaURL);
                        }
                        continue;
                    }
                    MediaFile mediaItem = new MediaFile();
                    mediaItem.setName(path.getFileName().toString());
                    mediaItem.setPathStorage(path);
                    mediaItem.setRecordTime(rs.getObject(2, LocalDateTime.class));
                    LocalDateTime creationTime = rs.getObject(3, LocalDateTime.class);
                    if (creationTime != null) {
                        mediaItem.setCreationTime(FileTime.from(creationTime.atZone(ZoneId.systemDefault()).toInstant()));
                    }
                    loadItem(mediaItem, mediaURL);
                    chunk.add(mediaItem);
                    if (chunk.size() >= CHUNK_SIZE) {
                        publish(chunk);
                        chunk = new ArrayList<>();
                    }
                }
            }
        }
        publish(chunk);
        removeVanished(con, vanished);
        return null;
    }

    /**
     * Finds the files that contain all words. The last word may be
     * incomplete (the user is typing): all words of the index that start with
     * it are tried as well.
     *
     * @return the paths of the found files, at most limit
     */
    public static Set<String> find(Connection con, String text, int limit) throws SQLException {
        Set<String> result = new LinkedHashSet<>();
        List<String> words = words(text);
        if (words.isEmpty()) {
            return result;
        }
        addKeys(con, String.join(" ", words), limit, result);
        String last = words.remove(words.size() - 1).toUpperCase(Locale.ENGLISH);
        String rest = String.join(" ", words);
        try (PreparedStatement stm = con.prepareStatement("SELECT NAME FROM FT.WORDS WHERE NAME LIKE ? ESCAPE '\\' AND NAME <> ? ORDER BY LENGTH(NAME), NAME LIMIT " + MAX_PREFIX_WORDS)) {
            stm.setString(1, last.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            stm.setString(2, last);
            try (ResultSet rs = stm.executeQuery()) {
                while (rs.next() && result.size() < limit) {
                    addKeys(con, rest + " " + rs.getString(1), limit, result);
                }
            }
        }
        return result;
    }

    /** The words of the text, split like the full text index splits them. */
    private static List<String> words(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (char c : (text + " ").toCharArray()) {
            if (App.SEARCH_WHITESPACE_CHARS.indexOf(c) >= 0 || Character.isWhitespace(c)) {
                if (word.length() > 0) {
                    words.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.append(c);
            }
        }
        return words;
    }

    private static void addKeys(Connection con, String text, int limit, Set<String> result) throws SQLException {
        try (ResultSet rs = FullText.searchData(con, text, limit, 0)) {
            while (rs.next() && result.size() < limit) {
                Object[] keys = (Object[]) rs.getObject("KEYS");
                result.add((String) keys[0]);
            }
        }
    }

    private void loadItem(MediaFile mediaItem, String mediaURL) {
        if (FileTypes.isValidVideo(mediaURL)) {
            mediaItem.setMediaType(MediaFile.MediaTypes.VIDEO);
        } else if (FileTypes.isValidImage(mediaURL)) {
            mediaItem.setMediaType(MediaFile.MediaTypes.IMAGE);
        } else {
            mediaItem.setMediaType(MediaFile.MediaTypes.NONE);
        }
        mediaItem.readEdits();
        if (mainController.isMediaFileBookmarked(mediaItem)) {
            mediaItem.setBookmarked(true);
        }
    }

    private void publish(List<MediaFile> chunk) {
        if (chunk.isEmpty()) {
            return;
        }
        updateValue(chunk.get(chunk.size() - 1));
        Platform.runLater(() -> {
            if (isCancelled() == false) {
                fullMediaList.addAll(chunk);
            }
        });
    }

    /** Files deleted outside of the app: the next scan would remove them too. */
    private static void removeVanished(Connection con, List<String> vanished) {
        if (vanished.isEmpty()) {
            return;
        }
        try (PreparedStatement stm = con.prepareStatement("DELETE FROM MEDIAFILES WHERE PATHSTORAGE=?")) {
            for (String path : vanished) {
                stm.setString(1, path);
                stm.addBatch();
            }
            stm.executeBatch();
        } catch (SQLException ex) {
            Logger.getLogger(SRMediaLoadingTask.class.getName()).log(Level.WARNING, "Cannot remove vanished files from search index", ex);
        }
    }

}
