package org.photoslide.datamodel;

/**
 * A filter that needs the whole image at once (multi pass blurs, bilateral
 * grids, blending with a filtered copy). FilterChain16 calls process()
 * instead of apply(); the filter may parallelise internally.
 */
public interface WholeImageFilter16 extends Filter16 {

    /**
     * @return the result, a new image of the same size (src must not be changed)
     */
    Image16 process(Image16 src);

    @Override
    default void apply(Image16 src, Image16 dst, int yStart, int yEnd) {
        throw new UnsupportedOperationException(name() + " works on the whole image, use process()");
    }
}
