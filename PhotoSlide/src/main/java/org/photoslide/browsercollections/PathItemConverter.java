/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browsercollections;

import java.nio.file.Path;
import javafx.util.StringConverter;

/**
 *
 * @author selfemp
 */
public class PathItemConverter extends StringConverter<PathItem> {

    @Override
    public String toString(PathItem t) {
        return t.toString();
    }

    @Override
    public PathItem fromString(String string) {
        return new PathItem(Path.of(string));
    }
    
}
