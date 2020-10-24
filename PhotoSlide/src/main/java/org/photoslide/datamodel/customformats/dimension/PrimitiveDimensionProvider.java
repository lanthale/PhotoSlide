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
public class PrimitiveDimensionProvider implements DimensionProvider {

    @Override
    public Dimension getDimension() {
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
