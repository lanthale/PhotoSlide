package org.photoslide.bench;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.BlendMode;
import org.photoslide.imageops.EditPipeline;
import org.photoslide.imageops.Geometry;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.imageops.PrefixCachedRenderer;
import org.photoslide.imageops.Styles;
import org.photoslide.imageops.ToolRegistry;
import org.photoslide.imageops.lz.RelightFilter;
import org.photoslide.imageops.mask.ColorSelection;

/**
 * Renders all styles, every tool in every blend mode and a dragged slider
 * through the prefix cache, and compares the pixels with a reference made
 * before an optimisation:
 *
 * <pre>
 * java ... org.photoslide.bench.PipelineVerify write &lt;dir&gt;   (before)
 * java ... org.photoslide.bench.PipelineVerify check &lt;dir&gt; [tolerance]   (after)
 * </pre>
 *
 * Exits with 1 if any pixel differs by more than the tolerance in 16 bit
 * steps (default 1). The Vector API (float pow/exp) needs about 8: blend
 * modes like Color Dodge enlarge its rounding differences.
 */
public final class PipelineVerify {

    private static final int W = 640, H = 427;

    private PipelineVerify() {
    }

    public static void main(String[] args) throws IOException {
        boolean write = args[0].equals("write");
        Path dir = Path.of(args[1]);
        int tolerance = args.length > 2 ? Integer.parseInt(args[2]) : 1;
        Files.createDirectories(dir);
        Image16 src = PipelineBench.synthetic(W, H);
        RenderContext ctx = new RenderContext((double) W / 6000);
        int worst = 0;
        int cases = 0;
        for (Map.Entry<String, List<ImageFilter>> e : cases().entrySet()) {
            Image16 out = EditPipeline.chain(e.getValue(), Geometry.NONE, ctx).render(src);
            Path file = dir.resolve(e.getKey().replaceAll("[^A-Za-z0-9]+", "_") + ".bin");
            cases++;
            if (write) {
                save(file, out);
            } else {
                int d = maxDiff(load(file), out);
                worst = Math.max(worst, d);
                if (d > tolerance) {
                    System.out.println("DIFF " + d + "  " + e.getKey());
                }
            }
        }
        if (!write) {
            worst = Math.max(worst, checkPrefixCache(src, ctx));
            cases++;
        }
        System.out.println((write ? "wrote " : "checked ") + cases + " cases" + (write ? "" : ", largest difference " + worst));
        System.exit(worst > tolerance ? 1 : 0);
    }

    /** Dragging a slider of the third tool: cached and uncached renders must be equal. */
    private static int checkPrefixCache(Image16 src, RenderContext ctx) {
        List<ImageFilter> stack = new ArrayList<>(tools("Soft Wow!"));
        stack.addAll(tools("Golden Sepia"));
        for (int i = 0; i < stack.size(); i++) {
            stack.get(i).setPosition(i);
        }
        PrefixCachedRenderer cached = new PrefixCachedRenderer();
        Image16[] scratch = new Image16[2];
        int worst = 0;
        for (int step = 0; step < 4; step++) {
            List<ImageFilter> copy = copies(stack);
            ((AbstractImageFilter) copy.get(2)).setValue(3, 0.1f + step * 0.2f);
            if (step == 3) {                              // last step: the first tool changes
                ((AbstractImageFilter) copy.get(0)).setOpacity(0.5f);
            }
            Image16 a = cached.render(src, copy, Geometry.NONE, ctx, scratch).copy();
            Image16 b = EditPipeline.chain(copy, Geometry.NONE, ctx).render(src);
            int d = maxDiff(a, b);
            if (d > 0) {
                System.out.println("DIFF " + d + "  prefix cache step " + step);
            }
            worst = Math.max(worst, d);
        }
        return worst;
    }

    private static Map<String, List<ImageFilter>> cases() {
        Map<String, List<ImageFilter>> map = new LinkedHashMap<>();
        for (List<Styles.Style> l : Styles.byCategory().values()) {
            for (Styles.Style s : l) {
                map.put("style " + s.category() + " " + s.name(), tools(s.name()));
            }
        }
        for (BlendMode mode : BlendMode.values()) {
            for (float opacity : new float[]{1f, 0.6f}) {
                RelightFilter f = new RelightFilter();
                f.setBlendMode(mode);
                f.setOpacity(opacity);
                map.put("relight " + mode + " " + opacity, List.of(f));
            }
        }
        for (ToolRegistry.Tool t : ToolRegistry.tools()) {
            AbstractImageFilter f = t.create();
            for (org.photoslide.imageops.ParamSpec p : f.params()) {
                if (p.kind() == org.photoslide.imageops.ParamSpec.Kind.SLIDER) {
                    f.setValue(p.index(), p.min() + 0.6f * (p.max() - p.min()));
                    break;
                }
            }
            f.setOpacity(0.8f);
            f.setColorSelection(new ColorSelection(true, 0.08f, 0.1f, 0.05f, true, 0.2f, 0.8f, 0.1f, false));
            map.put("tool " + t.name(), List.of(f));
        }
        return map;
    }

    private static List<ImageFilter> tools(String style) {
        for (List<Styles.Style> l : Styles.byCategory().values()) {
            for (Styles.Style s : l) {
                if (s.name().equals(style)) {
                    List<ImageFilter> out = new ArrayList<>();
                    int pos = 0;
                    for (Supplier<AbstractImageFilter> t : s.tools()) {
                        AbstractImageFilter f = t.get();
                        f.setPosition(pos++);
                        out.add(f);
                    }
                    return out;
                }
            }
        }
        throw new IllegalArgumentException(style);
    }

    private static List<ImageFilter> copies(List<ImageFilter> l) {
        List<ImageFilter> out = new ArrayList<>();
        for (ImageFilter f : l) {
            try {
                out.add((ImageFilter) f.clone());
            } catch (CloneNotSupportedException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return out;
    }

    private static int maxDiff(Image16 a, Image16 b) {
        short[] x = a.data();
        short[] y = b.data();
        int m = 0;
        for (int i = 0; i < x.length; i++) {
            m = Math.max(m, Math.abs((x[i] & 0xFFFF) - (y[i] & 0xFFFF)));
        }
        return m;
    }

    private static void save(Path file, Image16 img) throws IOException {
        try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeInt(img.width());
            out.writeInt(img.height());
            for (short v : img.data()) {
                out.writeShort(v);
            }
        }
    }

    private static Image16 load(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            int w = in.readInt();
            int h = in.readInt();
            short[] d = new short[w * h * 3];
            for (int i = 0; i < d.length; i++) {
                d[i] = in.readShort();
            }
            return new Image16(w, h, d, true);
        }
    }
}
