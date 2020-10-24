/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel.customformats.dimension;

/**
 *
 * @author selfemp
 */
public class AttributeDimensionProvider implements DimensionProvider {

    private static final String HEIGHT = "height";
    private static final String WIDTH = "width";

    @Override
    public Dimension getDimension() {
        return new Dimension(getFloatAttribute(WIDTH), getFloatAttribute(HEIGHT));
    }

    private float getFloatAttribute(String name) {
        try {
            return Float.parseFloat(name);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
