package org.photoslide.imageops;

import java.util.List;
import java.util.function.Supplier;
import org.photoslide.imageops.lz.BlackWhiteFilter;
import org.photoslide.imageops.lz.LensCorrectionFilter;
import org.photoslide.imageops.lz.RedEyeFilter;
import org.photoslide.imageops.lz.SpotFilter;
import org.photoslide.imageops.lz.CloneFilter;
import org.photoslide.imageops.lz.HiPassFilter;
import org.photoslide.imageops.lz.ContrastMaskFilter;
import org.photoslide.imageops.lz.NoiseReductionFilter;
import org.photoslide.imageops.lz.SharpenFilter;
import org.photoslide.imageops.lz.RelightFilter;
import org.photoslide.imageops.lz.ColorBalanceFilter;
import org.photoslide.imageops.lz.FilmGrainFilter;
import org.photoslide.imageops.lz.GaussianBlurFilter;
import org.photoslide.imageops.lz.HueSaturationFilter;
import org.photoslide.imageops.lz.WhiteBalanceFilter;
import org.photoslide.imageops.lz.WhitePointFilter;
import org.photoslide.imageops.lz.ZonesFilter;

/**
 * All editing tools, in the order of the editor's "add tool" menu (LightZone
 * order). The name is what XMP sidecars and the cache store.
 */
public final class ToolRegistry {

    public record Tool(String name, Class<? extends AbstractImageFilter> type,
            Supplier<? extends AbstractImageFilter> factory) {

        public AbstractImageFilter create() {
            return factory.get();
        }
    }

    private static final List<Tool> TOOLS = List.of(
            new Tool(ExposureFilter.NAME, ExposureFilter.class, ExposureFilter::new),
            new Tool(GainFilter.NAME, GainFilter.class, GainFilter::new),
            new Tool(ZonesFilter.NAME, ZonesFilter.class, ZonesFilter::new),
            new Tool(RelightFilter.NAME, RelightFilter.class, RelightFilter::new),
            new Tool(SharpenFilter.NAME, SharpenFilter.class, SharpenFilter::new),
            new Tool(GaussianBlurFilter.NAME, GaussianBlurFilter.class, GaussianBlurFilter::new),
            new Tool(HueSaturationFilter.NAME, HueSaturationFilter.class, HueSaturationFilter::new),
            new Tool(ColorBalanceFilter.NAME, ColorBalanceFilter.class, ColorBalanceFilter::new),
            new Tool(WhiteBalanceFilter.NAME, WhiteBalanceFilter.class, WhiteBalanceFilter::new),
            new Tool(WhitePointFilter.NAME, WhitePointFilter.class, WhitePointFilter::new),
            new Tool(BlackWhiteFilter.NAME, BlackWhiteFilter.class, BlackWhiteFilter::new),
            new Tool(NoiseReductionFilter.NAME, NoiseReductionFilter.class, NoiseReductionFilter::new),
            new Tool(CloneFilter.NAME, CloneFilter.class, CloneFilter::new),
            new Tool(SpotFilter.NAME, SpotFilter.class, SpotFilter::new),
            new Tool(RedEyeFilter.NAME, RedEyeFilter.class, RedEyeFilter::new),
            new Tool(FilmGrainFilter.NAME, FilmGrainFilter.class, FilmGrainFilter::new),
            new Tool(LensCorrectionFilter.NAME, LensCorrectionFilter.class, LensCorrectionFilter::new),
            new Tool(ContrastMaskFilter.NAME, ContrastMaskFilter.class, ContrastMaskFilter::new),
            new Tool(HiPassFilter.NAME, HiPassFilter.class, HiPassFilter::new));

    private ToolRegistry() {
    }

    public static List<Tool> tools() {
        return TOOLS;
    }

    /** @return the tool with this name, or null */
    public static Tool find(String name) {
        for (Tool t : TOOLS) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        return null;
    }

    /** @return a new instance with default values, or null for an unknown name */
    public static AbstractImageFilter create(String name) {
        Tool t = find(name);
        return t == null ? null : t.create();
    }
}
