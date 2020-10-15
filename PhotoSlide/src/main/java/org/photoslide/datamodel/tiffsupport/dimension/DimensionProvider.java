/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel.tiffsupport.dimension;

import org.w3c.dom.Document;

/**
 *
 * @author selfemp
 */
public interface DimensionProvider {
    Dimension getDimension(Document document);
}
