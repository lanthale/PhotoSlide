/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel.tiffsupport.dimension;

import javafx.geometry.Rectangle2D;
import org.w3c.dom.Document;

/**
 *
 * @author selfemp
 */
public class PrimitiveDimensionProvider implements DimensionProvider {

    @Override
    public Dimension getDimension(Document document) {
        /*UserAgent agent = new UserAgentAdapter();
        DocumentLoader loader = new DocumentLoader(agent);

        BridgeContext context = new BridgeContext(agent, loader);
        context.setDynamic(true);

        GVTBuilder builder = new GVTBuilder();
        Rectangle2D primitiveBounds = builder.build(context, document).getPrimitiveBounds();
        return new Dimension((float) primitiveBounds.getWidth(), (float) primitiveBounds.getHeight());*/
        return null;
    }
}
