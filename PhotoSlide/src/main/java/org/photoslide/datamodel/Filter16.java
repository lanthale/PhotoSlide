package org.photoslide.datamodel;

import org.photoslide.datamodel.Image16;

/**
 * One step of the edit pipeline. A filter reads from src and writes the rows
 * [yStart, yEnd) into dst (same size as src). FilterChain16 calls apply() from
 * several threads with disjoint row ranges, so an implementation must not
 * keep mutable state and must only write rows inside its range.
 *
 * Filters that need neighbouring pixels (blur, sharpen) may READ any row of
 * src, but must still only WRITE their own rows.
 */
@FunctionalInterface
public interface Filter16 {

    void apply(Image16 src, Image16 dst, int yStart, int yEnd);

    default String name() {
        return getClass().getSimpleName();
    }
}