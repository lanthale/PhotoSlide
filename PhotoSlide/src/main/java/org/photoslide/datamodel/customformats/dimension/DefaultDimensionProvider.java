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
public class DefaultDimensionProvider implements DimensionProvider {

    private static final int DEFAULT_SIZE = 400;
    private static final Dimension BOUNDS = new Dimension(DEFAULT_SIZE, DEFAULT_SIZE);

    @Override
    public Dimension getDimension() {
        return BOUNDS;
    }

}
