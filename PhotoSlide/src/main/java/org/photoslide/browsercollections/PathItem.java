/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browsercollections;

import java.nio.file.Path;
import org.photoslide.browsercollections.library.LibraryNode;

/**
 *
 * @author selfemp
 */
public class PathItem {
    private final Path filePath;
    /** set for a node of a photo library: filePath is then its virtual path */
    private final LibraryNode libraryNode;

    public PathItem(Path filePath) {
        this(filePath, null);
    }

    public PathItem(Path filePath, LibraryNode libraryNode) {
        this.filePath = filePath;
        this.libraryNode = libraryNode;
    }

    public Path getFilePath() {
        return filePath;
    }

    /** The node of a photo library, null for a folder. */
    public LibraryNode getLibraryNode() {
        return libraryNode;
    }

    @Override
    public String toString() {
        if (libraryNode != null) {
            return libraryNode.name();
        }
        return filePath.getFileName().toString();
    }


}
