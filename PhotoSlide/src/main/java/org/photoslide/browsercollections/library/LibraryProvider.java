package org.photoslide.browsercollections.library;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads the structure of a photo library of another program (e.g. the macOS
 * Photos library). The library is never changed: PhotoSlide keeps its edits
 * of these files in its own sidecars (see {@link Libraries#sidecarFolder}).
 * All methods may access the disk: not on the FX thread.
 */
public interface LibraryProvider {

    /** The folder or file of the library, as stored for the collection. */
    Path root();

    /** The top level nodes. */
    List<LibraryNode> topLevel() throws IOException;

    /** The sub nodes of the node (empty for an album). */
    List<LibraryNode> children(String nodeId) throws IOException;

    /**
     * The media files of the node that are on this computer, sorted by date.
     * Files only in the cloud are replaced by the largest local preview.
     */
    List<Path> assets(String nodeId) throws IOException;

    /**
     * The original file name of a file returned by {@link #assets}, shown
     * instead of the name in the library; null if not known.
     */
    default String displayName(Path file) {
        return null;
    }

    /** Shown when the library cannot be read, e.g. a missing permission. */
    String accessProblem();
}
