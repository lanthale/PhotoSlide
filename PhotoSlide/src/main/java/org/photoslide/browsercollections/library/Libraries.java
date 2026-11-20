package org.photoslide.browsercollections.library;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.photoslide.Utility;

/**
 * Photo libraries of other programs used as a collection, and the paths that
 * stand for their nodes.
 * <p>
 * The tree and the light table work with paths. A node of a library gets a
 * virtual path below the library, {@code <library>/§node/<node id>}, that
 * does not exist on disk; {@link #nodeOf(Path)} gives the node back.
 * <p>
 * Files in a library are never written: their sidecars are kept in the
 * application folder ({@link #sidecarFolder(Path)}). All methods here only
 * look at the path, they do not access the disk.
 */
public final class Libraries {

    /** Name of the virtual folder holding the nodes of a library. */
    public static final String NODE_DIR = "§node";
    private static final String PHOTOS_SUFFIX = ".photoslibrary";
    private static final Map<Path, LibraryProvider> PROVIDERS = new ConcurrentHashMap<>();

    private Libraries() {
    }

    /** A node of a library: the provider and the id of the node. */
    public record NodeRef(LibraryProvider provider, String nodeId) {
    }

    /** True if the collection root is a photo library and not a folder. */
    public static boolean isLibrary(Path root) {
        return root != null && root.getFileName() != null
                && root.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(PHOTOS_SUFFIX);
    }

    /** The reader of the library (one per library, it caches its database). */
    public static LibraryProvider provider(Path root) {
        return PROVIDERS.computeIfAbsent(root.toAbsolutePath().normalize(), ApplePhotosProvider::new);
    }

    /** The virtual path of a node of the library. */
    public static Path virtualPath(Path root, String nodeId) {
        return root.resolve(NODE_DIR).resolve(nodeId);
    }

    /** True for the virtual path of a node ({@link #virtualPath}). */
    public static boolean isVirtual(Path path) {
        return path != null && path.getParent() != null && path.getParent().getFileName() != null
                && NODE_DIR.equals(path.getParent().getFileName().toString())
                && isLibrary(path.getParent().getParent());
    }

    /** The node of a virtual path, null for other paths. */
    public static NodeRef nodeOf(Path path) {
        if (!isVirtual(path)) {
            return null;
        }
        return new NodeRef(provider(path.getParent().getParent()), path.getFileName().toString());
    }

    /** The library containing the path, null if the path is not in a library. */
    public static Path libraryOf(Path path) {
        if (path == null) {
            return null;
        }
        Path abs = path.toAbsolutePath();
        for (Path p = abs; p != null; p = p.getParent()) {
            if (isLibrary(p)) {
                return p;
            }
        }
        return null;
    }

    /** True if the path is in a library: nothing may be written there. */
    public static boolean isReadOnly(Path path) {
        return libraryOf(path) != null;
    }

    /**
     * The folder of the sidecars of an image: next to the image, or for an
     * image in a library a folder in the application data with the same
     * structure as the library (the library is not changed).
     */
    public static Path sidecarFolder(Path image) {
        Path parent = image.toAbsolutePath().getParent();
        Path library = libraryOf(image);
        if (library == null) {
            return parent;
        }
        String name = library.getFileName().toString();
        String key = name.substring(0, name.length() - PHOTOS_SUFFIX.length()).replaceAll("[^\\p{Alnum}._-]", "_")
                + "-" + Integer.toHexString(library.toString().hashCode());
        Path base = Path.of(Utility.getAppData(), "library-sidecars", key);
        return parent == null ? base : base.resolve(library.relativize(parent).toString());
    }
}
