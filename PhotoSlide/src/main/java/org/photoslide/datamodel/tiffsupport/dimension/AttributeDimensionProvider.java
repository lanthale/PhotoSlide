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
public class AttributeDimensionProvider implements DimensionProvider {

    private static final String HEIGHT = "height";
    private static final String WIDTH = "width";

    @Override
    public Dimension getDimension(Document document) {
        return new Dimension(getFloatAttribute(document, WIDTH), getFloatAttribute(document, HEIGHT));
    }

    private float getFloatAttribute(Document document, String name) {
        try {
            return Float.parseFloat(document.getDocumentElement().getAttribute(name));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
