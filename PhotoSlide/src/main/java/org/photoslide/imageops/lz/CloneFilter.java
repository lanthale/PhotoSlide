package org.photoslide.imageops.lz;

import java.util.List;
import java.util.stream.IntStream;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.datamodel.WholeImageFilter16;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;
import org.photoslide.imageops.mask.Contour;
import org.photoslide.imageops.mask.Region;

/**
 * Clone: each outline of the region is filled with a copy of another part of
 * the image (its source point), blended in with the soft edge of the
 * outline.
 *
 * Algorithm ported from LightZone (CloneOperationImpl.buildCloner),
 * Copyright (c) 2005-2011 Fabio Riccardi, BSD-3-Clause, see
 * THIRD-PARTY-NOTICES.md.
 */
public class CloneFilter extends AbstractImageFilter {

    public static final String NAME = "CloneFilter";

    public CloneFilter() {
        this(NAME);
    }

    protected CloneFilter(String name) {
        super(name);
    }

    @Override
    public String displayName() {
        return "Clone";
    }

    @Override
    public List<ParamSpec> params() {
        return List.of();
    }

    /** The region holds the clone areas, it does not limit the tool. */
    @Override
    protected boolean regionIsMask() {
        return false;
    }

    @Override
    protected Filter16 createFilter16(RenderContext ctx) {
        final List<Contour> clones = getRegion().contours().stream().filter(Contour::hasSource).toList();
        if (clones.isEmpty()) {
            return null;
        }
        return (WholeImageFilter16) src -> {
            final int w = src.width();
            final int h = src.height();
            final short[] s = src.data();
            final Image16 out = new Image16(w, h, s.clone(), src.isLinear());
            final short[] d = out.data();
            for (Contour c : clones) {
                Contour.Point target = c.center();
                final int dx = (int) Math.round((target.x() - c.sourceX()) * ctx.scale());
                final int dy = (int) Math.round((target.y() - c.sourceY()) * ctx.scale());
                final float[] mask = Region.rasterize(c, w, h, ctx);
                IntStream.range(0, h).parallel().forEach(y -> {
                    int sy = y - dy;
                    if (sy < 0 || sy >= h) {
                        return;
                    }
                    for (int x = 0; x < w; x++) {
                        float m = mask[y * w + x];
                        int sx = x - dx;
                        if (m <= 0 || sx < 0 || sx >= w) {
                            continue;
                        }
                        int o = (y * w + x) * 3;
                        int i = (sy * w + sx) * 3;
                        for (int k = 0; k < 3; k++) {
                            float a = d[o + k] & 0xFFFF;
                            float b = s[i + k] & 0xFFFF;
                            d[o + k] = (short) (int) (a + m * (b - a) + 0.5f);
                        }
                    }
                });
            }
            return out;
        };
    }
}
