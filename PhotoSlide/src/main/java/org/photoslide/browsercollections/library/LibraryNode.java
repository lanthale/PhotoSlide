package org.photoslide.browsercollections.library;

/**
 * A node of a photo library shown in the collection tree: a folder, an album
 * or a group by date. Only albums and date groups without sub groups have
 * media files.
 *
 * @param id unique within the library, used in the virtual path of the node
 * @param name shown in the tree
 * @param kind what the node is
 * @param hasChildren true if the node has sub nodes (shown expandable)
 */
public record LibraryNode(String id, String name, Kind kind, boolean hasChildren) {

    public enum Kind {
        /** a folder of albums, has no media files itself */
        FOLDER,
        /** an album of the library */
        ALBUM,
        /** a group by date (year, month) */
        EVENT
    }
}
