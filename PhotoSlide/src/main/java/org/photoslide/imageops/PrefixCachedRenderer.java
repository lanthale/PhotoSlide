package org.photoslide.imageops;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;

/**
 * Renders a tool stack and keeps the input of the tool that changed last.
 * While a slider of one tool is dragged, only that tool and the ones after it
 * are rendered again; the (often expensive) tools before it come from the
 * cache. The result is the same as rendering the whole stack.
 *
 * One instance per render thread (not thread safe). Holds one extra image of
 * the size of the source.
 */
public final class PrefixCachedRenderer {

    private Image16 source;
    private RenderContext context;
    private List<ImageFilter> filters = List.of();
    /** Result of the first level tools of filters (the source itself for level 0). */
    private Image16 prefix;
    private int level;

    /**
     * @param filters the tools (any order; copies that are not changed later)
     * @param scratch two buffers reused between calls, see FilterChain16.render
     * @return the result, valid until the next call with the same scratch
     */
    public Image16 render(Image16 src, List<ImageFilter> filters, Geometry geometry, RenderContext ctx,
            Image16[] scratch) {
        List<ImageFilter> sorted = new ArrayList<>(filters);
        sorted.sort(Comparator.comparingInt(ImageFilter::getPosition));
        if (src != source || !ctx.equals(context)) {
            source = src;
            context = ctx;
            prefix = null;
            this.filters = List.of();
        }
        int common = commonPrefix(sorted, this.filters);
        Image16 start = src;
        int from = 0;
        if (prefix != null && level <= common) {
            start = prefix;
            from = level;
        }
        Image16 current = from < common
                ? EditPipeline.chain(sorted.subList(from, common), ctx).render(start, scratch)
                : start;
        if (prefix == null || level != common) {
            // keep the input of the changed tool; scratch buffers are overwritten by the next render
            prefix = current == src || current == prefix ? current : current.copy();
            level = common;
            current = prefix;
        }
        this.filters = sorted;
        return EditPipeline.chain(sorted.subList(common, sorted.size()), geometry, ctx).render(current, scratch);
    }

    /** Frees the cached image. */
    public void clear() {
        source = null;
        context = null;
        prefix = null;
        filters = List.of();
    }

    private static int commonPrefix(List<ImageFilter> a, List<ImageFilter> b) {
        int n = Math.min(a.size(), b.size());
        int i = 0;
        while (i < n && a.get(i).equals(b.get(i))) {
            i++;
        }
        return i;
    }
}
