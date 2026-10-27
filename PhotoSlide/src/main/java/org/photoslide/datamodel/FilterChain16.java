package org.photoslide.datamodel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Ordered list of filters. render() is non-destructive: the source stays
 * untouched, the filters run in 16 bit (two scratch buffers are alternated).
 * Rows are processed in parallel.
 */
public final class FilterChain16 {

    private static final int ROWS_PER_TASK = 16;

    private final List<Filter16> filters = new ArrayList<>();

    public FilterChain16 add(Filter16 filter) {
        filters.add(filter);
        return this;
    }

    public FilterChain16 clear() {
        filters.clear();
        return this;
    }

    public List<Filter16> filters() {
        return Collections.unmodifiableList(filters);
    }

    /** @return the result; this is the source itself if the chain is empty */
    public Image16 render(Image16 source) {
        return render(source, new Image16[2]);
    }

    /**
     * Like render(source), but keeps the two scratch buffers in the given
     * array (length 2) for the next call, so repeated renders of the same
     * size do not allocate. The result may be one of these buffers: it is
     * only valid until the next call with the same array.
     */
    public Image16 render(Image16 source, Image16[] scratch) {
        for (int i = 0; i < 2; i++) {
            Image16 s = scratch[i];
            if (s != null && (s.width() != source.width() || s.height() != source.height()
                    || s.isLinear() != source.isLinear())) {
                scratch[i] = null;
            }
        }
        Image16 current = source;
        for (Filter16 f : filters) {
            int slot = current == scratch[0] ? 1 : 0;
            if (scratch[slot] == null) {
                scratch[slot] = Image16.blank(source.width(), source.height(), source.isLinear());
            }
            Image16 dst = scratch[slot];
            run(f, current, dst);
            current = dst;
        }
        return current;
    }

    private static void run(Filter16 f, Image16 src, Image16 dst) {
        final int h = src.height();
        final int tasks = (h + ROWS_PER_TASK - 1) / ROWS_PER_TASK;
        IntStream.range(0, tasks).parallel().forEach(t -> {
            int y0 = t * ROWS_PER_TASK;
            f.apply(src, dst, y0, Math.min(h, y0 + ROWS_PER_TASK));
        });
    }
}