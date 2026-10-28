package org.photoslide.bench;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.photoslide.datamodel.Display16;
import org.photoslide.datamodel.Image16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.BlendMode;
import org.photoslide.imageops.EditPipeline;
import org.photoslide.imageops.Geometry;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.imageops.ParamSpec;
import org.photoslide.imageops.PrefixCachedRenderer;
import org.photoslide.imageops.Styles;
import org.photoslide.imageops.ToolRegistry;

/**
 * Times every tool, some styles and the display conversion of the 16 bit
 * pipeline on a synthetic photo, at proxy size and full size. Not a unit
 * test: run it with
 *
 * <pre>
 * mvn test-compile
 * java -cp target/test-classes:target/classes:&lt;dependencies&gt; org.photoslide.bench.PipelineBench [full]
 * </pre>
 *
 * Add -XX:ActiveProcessorCount=2 to see a weak computer.
 */
public final class PipelineBench {

    private static final int PROXY_W = 1600, PROXY_H = 1067;
    private static final int FULL_W = 6000, FULL_H = 4000;

    private PipelineBench() {
    }

    public static void main(String[] args) {
        boolean full = Arrays.asList(args).contains("full");
        System.out.printf(Locale.ROOT, "cores %d, %s%n", Runtime.getRuntime().availableProcessors(),
                System.getProperty("java.vm.version"));
        run("proxy " + PROXY_W + "x" + PROXY_H, synthetic(PROXY_W, PROXY_H), 7);
        if (full) {
            run("full " + FULL_W + "x" + FULL_H, synthetic(FULL_W, FULL_H), 3);
        }
    }

    private static void run(String title, Image16 src, int runs) {
        System.out.println();
        System.out.println("== " + title + " (median ms of " + runs + ")");
        double scale = (double) src.width() / FULL_W;
        RenderContext ctx = new RenderContext(scale);
        for (ToolRegistry.Tool t : ToolRegistry.tools()) {
            AbstractImageFilter f = active(t.create());
            time(t.name(), runs, () -> EditPipeline.chain(List.of(f), ctx).render(src));
        }
        AbstractImageFilter relight = active(ToolRegistry.create("RelightFilter"));
        relight.setBlendMode(BlendMode.SOFT_LIGHT);
        relight.setOpacity(0.7f);
        time("RelightFilter soft light 70%", runs, () -> EditPipeline.chain(List.of(relight), ctx).render(src));
        for (String name : List.of("Crisp", "Soft Wow!", "Clarity", "SLR Sharpen", "Golden Sepia")) {
            List<ImageFilter> tools = style(name);
            time("style " + name, runs, () -> EditPipeline.chain(tools, ctx).render(src));
        }
        // dragging a slider of the top tool of a 6 tool stack, without and with the prefix cache
        List<ImageFilter> stack = new ArrayList<>(style("Soft Wow!"));
        stack.addAll(style("Clarity"));
        stack.addAll(style("Golden Sepia"));
        for (int i = 0; i < stack.size(); i++) {
            stack.get(i).setPosition(i);
        }
        AbstractImageFilter top = (AbstractImageFilter) stack.get(stack.size() - 1);
        float[] step = {0.1f};
        time("drag top of 6 tools, uncached", runs, () -> {
            top.setValue(3, step[0] += 0.01f);
            return EditPipeline.chain(stack, ctx).render(src);
        });
        PrefixCachedRenderer cached = new PrefixCachedRenderer();
        Image16[] scratch = new Image16[2];
        time("drag top of 6 tools, prefix cache", runs, () -> {
            List<ImageFilter> copy = new ArrayList<>();
            for (ImageFilter f : stack) {
                try {
                    copy.add((ImageFilter) f.clone());
                } catch (CloneNotSupportedException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            ((AbstractImageFilter) copy.get(copy.size() - 1)).setValue(3, step[0] += 0.01f);
            return cached.render(src, copy, Geometry.NONE, ctx, scratch);
        });
        Geometry g = new Geometry(true, false, 3);
        time("geometry flip + straighten", runs, () -> EditPipeline.chain(List.of(), g, ctx).render(src));
        time("display 16 -> 8 bit", runs, () -> Display16.toArgb(src, false));
    }

    /** The tool with its first slider moved, so it has an effect. */
    private static AbstractImageFilter active(AbstractImageFilter f) {
        for (ParamSpec p : f.params()) {
            if (p.kind() == ParamSpec.Kind.SLIDER) {
                f.setValue(p.index(), p.min() + 0.6f * (p.max() - p.min()));
                break;
            }
        }
        return f;
    }

    private static List<ImageFilter> style(String name) {
        for (List<Styles.Style> l : Styles.byCategory().values()) {
            for (Styles.Style s : l) {
                if (s.name().equals(name)) {
                    List<ImageFilter> tools = new ArrayList<>();
                    int pos = 0;
                    for (Supplier<AbstractImageFilter> t : s.tools()) {
                        AbstractImageFilter f = t.get();
                        f.setPosition(pos++);
                        tools.add(f);
                    }
                    return tools;
                }
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static void time(String name, int runs, Supplier<Object> work) {
        for (int i = 0; i < 2; i++) {
            work.get();                         // warm up
        }
        long[] t = new long[runs];
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            work.get();
            t[i] = System.nanoTime() - t0;
        }
        Arrays.sort(t);
        System.out.printf(Locale.ROOT, "%-34s %8.1f%n", name, t[runs / 2] / 1e6);
    }

    /** Linear photo-like content: gradients, colour and noise (deterministic). */
    static Image16 synthetic(int w, int h) {
        short[] d = new short[w * h * 3];
        long seed = 42;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                seed = seed * 6364136223846793005L + 1442695040888963407L;
                double noise = ((seed >>> 40) & 0xFFFF) / 65535.0 - 0.5;
                double base = 0.02 + 0.6 * Math.pow((double) x / w, 2.2);
                double[] c = {base * (1 + 0.5 * Math.sin(y * 0.01)), base * (1 + 0.3 * Math.cos(x * 0.007)), base * 0.8};
                for (int k = 0; k < 3; k++) {
                    double v = Math.max(0, Math.min(1, c[k] + noise * 0.02));
                    d[(y * w + x) * 3 + k] = (short) Math.round(v * 65535);
                }
            }
        }
        return new Image16(w, h, d, true);
    }
}
