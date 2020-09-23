/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.itarchitects.lightzonefx.browser;

import java.nio.file.Path;

/**
 *
 * @author selfemp
 */
public class PathItem {
    private final Path filePath;

    public PathItem(Path filePath) {
        this.filePath = filePath;
    }

    public Path getFilePath() {
        return filePath;
    }

    @Override
    public String toString() {
        return filePath.getFileName().toString();
    }
    
    
}
