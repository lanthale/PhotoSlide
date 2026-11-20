package org.photoslide.browsercollections.library;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The folders where the system keeps the pictures of the user, added as
 * normal folder collections:
 * <ul>
 * <li>Windows: the folders of the "Pictures" library
 * ({@code %APPDATA%\Microsoft\Windows\Libraries\Pictures.library-ms}), else
 * {@code %USERPROFILE%\Pictures}</li>
 * <li>Linux: {@code XDG_PICTURES_DIR} of {@code ~/.config/user-dirs.dirs},
 * else {@code ~/Pictures}</li>
 * <li>macOS: {@code ~/Pictures} (the Photos library is its own type)</li>
 * </ul>
 */
public final class SystemPicturesFolders {

    private static final Logger LOG = Logger.getLogger(SystemPicturesFolders.class.getName());
    private static final Pattern LIBRARY_URL = Pattern.compile("<url>([^<]+)</url>");
    private static final Pattern XDG_PICTURES = Pattern.compile("^\\s*XDG_PICTURES_DIR\\s*=\\s*\"?([^\"]*)\"?\\s*$", Pattern.MULTILINE);
    /** known folder id of "My Pictures" */
    private static final String KNOWN_FOLDER_PICTURES = "knownfolder:{33e28130-4e1e-4676-835a-98395c3bc3bb}";

    private SystemPicturesFolders() {
    }

    /** The existing picture folders, without duplicates. */
    public static List<Path> list() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        Set<Path> result = new LinkedHashSet<>();
        if (os.contains("win")) {
            result.addAll(windowsLibrary(home));
        } else if (os.contains("nux") || os.contains("nix")) {
            Path xdg = xdgPictures(Path.of(home));
            if (xdg != null) {
                result.add(xdg);
            }
        }
        if (result.isEmpty()) {
            result.add(Path.of(home, "Pictures"));
        }
        List<Path> existing = new ArrayList<>();
        for (Path p : result) {
            if (Files.isDirectory(p)) {
                existing.add(p.toAbsolutePath().normalize());
            }
        }
        return existing;
    }

    private static List<Path> windowsLibrary(String home) {
        List<Path> result = new ArrayList<>();
        String appData = System.getenv("APPDATA");
        if (appData == null) {
            return result;
        }
        Path library = Path.of(appData, "Microsoft", "Windows", "Libraries", "Pictures.library-ms");
        if (!Files.isRegularFile(library)) {
            return result;
        }
        try {
            String xml = Files.readString(library, StandardCharsets.UTF_8);
            result.addAll(parseLibrary(xml, Path.of(home, "Pictures")));
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Cannot read " + library, ex);
        }
        return result;
    }

    /** The folders of a Windows library file; package visible for tests. */
    static List<Path> parseLibrary(String xml, Path myPictures) {
        List<Path> result = new ArrayList<>();
        Matcher m = LIBRARY_URL.matcher(xml);
        while (m.find()) {
            String url = m.group(1).trim();
            if (url.equalsIgnoreCase(KNOWN_FOLDER_PICTURES)) {
                result.add(myPictures);
            } else if (!url.toLowerCase(Locale.ROOT).startsWith("knownfolder:")) {
                // other known folders (e.g. public pictures) are skipped: the
                // id cannot be resolved without the Windows API
                result.add(Path.of(url));
            }
        }
        return result;
    }

    private static Path xdgPictures(Path home) {
        Path config = System.getenv("XDG_CONFIG_HOME") != null
                ? Path.of(System.getenv("XDG_CONFIG_HOME")) : home.resolve(".config");
        Path dirs = config.resolve("user-dirs.dirs");
        if (!Files.isRegularFile(dirs)) {
            return null;
        }
        try {
            return parseXdg(Files.readString(dirs, StandardCharsets.UTF_8), home);
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Cannot read " + dirs, ex);
            return null;
        }
    }

    /** XDG_PICTURES_DIR of a user-dirs.dirs file; package visible for tests. */
    static Path parseXdg(String content, Path home) {
        Matcher m = XDG_PICTURES.matcher(content);
        if (!m.find()) {
            return null;
        }
        String value = m.group(1).replace("$HOME", home.toString());
        if (value.isBlank() || Path.of(value).equals(home)) {
            // "$HOME" means the folder is disabled
            return null;
        }
        return Path.of(value);
    }
}
