package org.photoslide.imageops;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.editpipeline.EditPipeline;
import org.photoslide.imageops.lz.BlackWhiteFilter;
import org.photoslide.imageops.lz.ColorBalanceFilter;
import org.photoslide.imageops.lz.ContrastMaskFilter;
import org.photoslide.imageops.lz.GaussianBlurFilter;
import org.photoslide.imageops.lz.HiPassFilter;
import org.photoslide.imageops.lz.HueSaturationFilter;
import org.photoslide.imageops.lz.RelightFilter;
import org.photoslide.imageops.lz.SharpenFilter;
import org.photoslide.imageops.lz.ZonesFilter;
import org.photoslide.imageops.mask.ColorSelection;

/**
 * The styles of LightZone: ready made tool stacks that are added on top of
 * the tools of a photo. The values are taken from LightZone's templates
 * (resources/com/lightcrafts/templates/resources/*.lzt), Copyright (c)
 * 2005-2011 Fabio Riccardi, BSD-3-Clause, see THIRD-PARTY-NOTICES.md.
 *
 * Mapping of the LightZone operations: Tone Mapper V4/V5 = Relight (V4 values
 * converted to the V5 scale), UnSharp Mask V3 = Sharpen, Channel Mixer =
 * Black and White, Color Balance V2 = Color Balance, Tone Mapper (V1) =
 * Contrast Mask, Hi Pass Filter, Gaussian Blur, Hue/Saturation V3 and Zones
 * map directly. LightZone's RGB colour selections become hue/brightness
 * selections.
 */
public final class Styles {

    /** One style: the tools it adds, in the order they are applied. */
    public record Style(String category, String name, List<Supplier<AbstractImageFilter>> tools) {
    }

    private static final List<Style> STYLES = new ArrayList<>();

    static {
        String bw = "Black & White";
        add(bw, "Alien Infrared", blackWhite(255, 0, 2, 7));
        add(bw, "Blue Filter", blackWhite(0, 2, 255, 1));
        add(bw, "Channel Mixer", blackWhite(255, 255, 255, 1));
        add(bw, "High Key Portrait", blackWhite(255, 0, 2, 1),
                zones(false, new int[]{2, 6, 10, 12, 14}, new float[]{0.0171f, 0.5043f, 0.8248f, 0.9017f, 0.9573f}));
        add(bw, "Infrared", blackWhite(255, 0, 2, 3));
        add(bw, "Normal", blackWhite(255, 255, 255, 1));
        add(bw, "Red Filter", blackWhite(255, 0, 2, 1));
        add(bw, "Soft Skin Tones", blackWhite(255, 144, 0, 2.1f));
        add(bw, "Yellow Filter", blackWhite(255, 244, 0, 1));

        String detail = "Detail Enhancement";
        add(detail, "High-Pass Filter", tool(HiPassFilter::new, 0.05f, 0.6f).mode(BlendMode.MIDTONES));
        add(detail, "Local Contrast", sharpen(15, 50, 20));
        add(detail, "SLR Sharpen", sharpen(45, 2.3f, 20).mode(BlendMode.DARKEN), sharpen(100, 1, 20));

        String effects = "Effects";
        add(effects, "Clarity", relightV5(64, 6.5f, 0.1f, 0.1f, 2).opacity(52)
                .colors(lzSelection(-1, -1, -1, -1, 0.365f, 0.189f, 0.765f, 0.229f)));
        add(effects, "Clarity II", relightV5(64, 6.5f, 0.1f, 0.1f, 4).opacity(51)
                .colors(lzSelection(-1, -1, -1, -1, 0.306f, 0.188f, 0.765f, 0.228f)));
        add(effects, "MT Green Sharpen", sharpen(185, 2.3f, 20)
                .colors(lzSelection(0.1294f, 0.2667f, 0.0824f, 0.71f, 0.453f, 0.271f, 0.859f, 0.135f)));
        add(effects, "Midtones Sharpening", sharpen(185, 2.3f, 20)
                .colors(lzSelection(-1, -1, -1, -1, 0.447f, 0.176f, 0.8f, 0.194f)));
        add(effects, "Negative", zones(true, new int[]{0}, new float[]{1}).mode(BlendMode.DIFFERENCE));
        add(effects, "Skin Glow", tool(HueSaturationFilter::new, 0, 20, 0, 5)
                .colors(lzSelection(0.6353f, 0.4824f, 0.4f, 0.28f, 0.741f, 0.1f, 0.985f, 0.015f)));
        add(effects, "Skin Glow II", sharpen(100, 500, 20).mode(BlendMode.SOFT_LIGHT).opacity(70)
                .colors(lzSelection(0.5373f, 0.4353f, 0.3412f, 0.2f, 0.753f, 0.141f, 0.962f, 0.038f)));
        add(effects, "Skin Glow III", relightV5(64, 1.5f, 0.1f, 0.2f, 3),
                tool(GaussianBlurFilter::new, 151.7f).mode(BlendMode.SOFT_LIGHT).opacity(60)
                        .colors(lzSelection(0.5333f, 0.4314f, 0.3412f, 0.16f, -1, 0, 0, 0)));
        add(effects, "Soften Skin", tool(GaussianBlurFilter::new, 5)
                .colors(lzSelection(0.3451f, 0.2549f, 0.1647f, 0.26f, 0.673f, 0.1f, 0.873f, 0.1f)));
        add(effects, "Soften Skin II", tool(GaussianBlurFilter::new, 5)
                .colors(lzSelection(0.4863f, 0.2667f, 0.1725f, 0.4f, 0.7023f, 0.1f, 0.9023f, 0.0392f)));

        String contrast = "High Contrast";
        add(contrast, "Polarizer", relightV4(32, 2.12f, 0.2f, 0.63f, 1));
        add(contrast, "Soft Wow!", relightV4(32, 1.32f, 0.2f, 0.63f, 2),
                sharpen(100, 500, 20).mode(BlendMode.SOFT_LIGHT).opacity(70));
        add(contrast, "Soft Wow! 2", sharpen(100, 500, 20).mode(BlendMode.SOFT_LIGHT).opacity(70),
                relightV4(16, 0.92f, 0.2f, 1, 1));
        add(contrast, "Tone Mapper", tool(ContrastMaskFilter::new, 250, 2.2f).mode(BlendMode.SOFT_LIGHT).opacity(75));
        add(contrast, "Wow!", sharpen(100, 500, 20).mode(BlendMode.SOFT_LIGHT).opacity(70));

        String hdr = "High Dynamic Range";
        add(hdr, "Bright Scene", colorBalance(3, 3, 2, 0.01f), relightV4(32, 1.32f, 0.2f, 1, 3));
        add(hdr, "Dark Scene", relightV4(32, 0.92f, 0.2f, 2.51f, 2));
        add(hdr, "Fill Flash", relightV4(32, 0.5f, 0.2f, 1, 3));
        add(hdr, "Sunset Darker", relightV4(64, 0.92f, 0.2f, 3.98f, 6));
        add(hdr, "Sunset Scene", relightV4(64, 0.92f, 0.2f, 1.58f, 6));

        String looks = "Looks";
        add(looks, "Crisp", relightV4(32.08f, 1.32f, 0.2f, 1.58f, 1));
        add(looks, "Hard", relightV4(16, 1.72f, 0.2f, 2.51f, 0));
        add(looks, "High Key", zones(false, new int[]{3, 5, 9, 12, 13, 14},
                new float[]{0.1282f, 0.4017f, 0.7051f, 0.8248f, 0.8761f, 0.9316f}));

        String toning = "Toning";
        add(toning, "Brown Yellow", colorBalance(128, 124, 112, 0.18f).opacity(82),
                colorBalance(126, 128, 46, 1).opacity(80));
        add(toning, "Golden Brick", colorBalance(128, 120, 107, 0.03f).opacity(38),
                colorBalance(128, 119, 63, 0.46f).opacity(55));
        add(toning, "Golden Sepia", colorBalance(128, 122, 112, 0.07f).opacity(49),
                colorBalance(128, 127, 104, 0.5f));
        add(toning, "Palladium", colorBalance(128, 121, 108, 0.04f).opacity(23),
                colorBalance(126, 128, 109, 0.5f).opacity(85));
        add(toning, "Palladium Gold", colorBalance(128, 125, 51, 0.91f).opacity(71),
                colorBalance(128, 120, 112, 0.04f).opacity(28));
        add(toning, "Platinum Silver", colorBalance(128, 125, 84, 0.87f),
                colorBalance(128, 122, 118, 0.1f).opacity(45));
        add(toning, "Soft Toning", colorBalance(128, 125, 121, 0.21f).opacity(48),
                colorBalance(127, 128, 114, 0.63f));
    }

    private Styles() {
    }

    /** All styles, grouped by category in LightZone's order. */
    public static Map<String, List<Style>> byCategory() {
        Map<String, List<Style>> map = new LinkedHashMap<>();
        for (Style s : STYLES) {
            map.computeIfAbsent(s.category(), k -> new ArrayList<>()).add(s);
        }
        return map;
    }

    /**
     * Adds the tools of the style on top of the tools of the file (they act
     * on the whole image) and records one history step.
     */
    public static void apply(MediaFile file, Style style) {
        int position = 0;
        for (ImageFilter f : file.getFilterList()) {
            position = Math.max(position, f.getPosition() + 1);
        }
        for (Supplier<AbstractImageFilter> tool : style.tools()) {
            AbstractImageFilter f = tool.get();
            f.setPosition(position++);
            file.getFilterList().add(f);
        }
        EditPipeline.filtersChanged(file, "Style: " + style.name());
    }

    // ------------------------------------------------------------------
    // building the tools
    // ------------------------------------------------------------------
    private static void add(String category, String name, Tool... tools) {
        STYLES.add(new Style(category, name, List.copyOf(Arrays.asList(tools))));
    }

    /** A tool with values and layer settings; creates a new instance on every get(). */
    private static final class Tool implements Supplier<AbstractImageFilter> {

        private final Supplier<? extends AbstractImageFilter> factory;
        private final float[] values;
        private float opacity = 1;
        private BlendMode mode = BlendMode.NORMAL;
        private ColorSelection colors = ColorSelection.NONE;

        Tool(Supplier<? extends AbstractImageFilter> factory, float[] values) {
            this.factory = factory;
            this.values = values;
        }

        Tool opacity(float percent) {
            opacity = percent / 100f;
            return this;
        }

        Tool mode(BlendMode m) {
            mode = m;
            return this;
        }

        Tool colors(ColorSelection cs) {
            colors = cs;
            return this;
        }

        @Override
        public AbstractImageFilter get() {
            AbstractImageFilter f = factory.get();
            f.setValues(values);
            f.setOpacity(opacity);
            f.setBlendMode(mode);
            f.setColorSelection(colors);
            return f;
        }
    }

    private static Tool tool(Supplier<? extends AbstractImageFilter> factory, float... values) {
        return new Tool(factory, values);
    }

    /** Tone Mapper V5 (LightZone's slider order: depth, detail, fuzz, highlights, shadows). */
    private static Tool relightV5(float depth, float detail, float fuzz, float highlights, float shadows) {
        return tool(RelightFilter::new, shadows, highlights, detail, depth, fuzz);
    }

    /**
     * Tone Mapper V4: highlights 0.1..10 (1 = neutral) and half the detail of
     * V5; converted to the V5 scale.
     */
    private static Tool relightV4(float depth, float detail, float fuzz, float highlights, float shadows) {
        return relightV5(depth, Math.min(10, 2 * detail), fuzz, Math.min(1, 0.2f * highlights), shadows);
    }

    private static Tool sharpen(float amount, float radius, float threshold) {
        return tool(SharpenFilter::new, amount, radius, threshold);
    }

    /** Channel Mixer V3/V4: filter colour 0..255 and strength. */
    private static Tool blackWhite(int r, int g, int b, float strength) {
        return tool(BlackWhiteFilter::new, r / 255f, g / 255f, b / 255f, strength);
    }

    /** Color Balance V2: colour 0..255 (128 = neutral) and midpoint. */
    private static Tool colorBalance(int r, int g, int b, float midpoint) {
        return tool(ColorBalanceFilter::new, r / 255f, g / 255f, b / 255f, midpoint);
    }

    /**
     * Zone mapper with the given zones moved.
     *
     * @param rgb LightZone scale 0 (RGB) instead of 1 (luminosity)
     */
    private static Tool zones(boolean rgb, int[] zone, float[] value) {
        float[] v = new float[ZonesFilter.ZONES + 1];
        Arrays.fill(v, 0, ZonesFilter.ZONES, -1f);
        for (int i = 0; i < zone.length; i++) {
            v[zone[i]] = value[i];
        }
        v[ZonesFilter.ZONES] = rgb ? 1 : 0;
        return tool(ZonesFilter::new, v);
    }

    /**
     * LightZone's RGB colour selection as hue/brightness selection.
     *
     * @param r,g,b selected colour 0..1, radius its extent (-1 = all colours)
     * @param lower,upper brightness range (zone scale 0..1), lower -1 = any
     * brightness; lowerFeather and upperFeather the soft edges
     */
    private static ColorSelection lzSelection(float r, float g, float b, float radius,
            float lower, float lowerFeather, float upper, float upperFeather) {
        boolean hue = radius >= 0;
        float center = 0;
        if (hue) {
            center = java.awt.Color.RGBtoHSB(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255), null)[0];
        }
        boolean lum = lower >= 0;
        return new ColorSelection(hue, center, hue ? radius * 0.12f : 0.05f, hue ? radius * 0.12f : 0.05f,
                lum, lum ? lower : 0, lum ? upper : 1, lum ? (lowerFeather + upperFeather) / 2 : 0.1f, false);
    }
}
