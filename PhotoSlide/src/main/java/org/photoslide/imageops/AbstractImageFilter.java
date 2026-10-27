package org.photoslide.imageops;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.RenderContext;
import org.photoslide.imageops.mask.ColorSelection;
import org.photoslide.imageops.mask.Region;

/**
 * Base of all editing tools: holds the values described by params(), the
 * position in the stack and the layer settings (active, opacity, blend
 * mode). Subclasses only describe their parameters and create the 16 bit
 * operation for the current values.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AbstractImageFilter implements ImageFilter {

    private final String name;
    private int position;
    private float[] values;
    private boolean active = true;
    private float opacity;
    private BlendMode blendMode;
    private Region region = Region.EMPTY;
    private ColorSelection colorSelection = ColorSelection.NONE;

    /**
     * @param name stable name, equal to the simple class name (used to find
     * the class when the settings are read)
     */
    protected AbstractImageFilter(String name) {
        this.name = name;
        this.values = defaults();
        this.opacity = defaultOpacity();
        this.blendMode = defaultBlendMode();
    }

    /** Name shown in the editor. */
    public abstract String displayName();

    /**
     * The settings of the tool. Must not depend on instance fields (it is
     * called from the constructor); return a constant list.
     */
    public abstract List<ParamSpec> params();

    /**
     * @return the operation for the current values, or null if they have no
     * effect
     */
    protected abstract Filter16 createFilter16(RenderContext ctx);

    /** Layer mode a new tool starts with (LightZone default: Normal). */
    protected BlendMode defaultBlendMode() {
        return BlendMode.NORMAL;
    }

    /** Opacity a new tool starts with. */
    protected float defaultOpacity() {
        return 1f;
    }

    /**
     * true (default): the region limits where the tool acts. Tools that use
     * the region themselves (clone, spot) return false.
     */
    protected boolean regionIsMask() {
        return true;
    }

    @Override
    public final Filter16 toFilter16(RenderContext ctx) {
        if (!active || opacity <= 0f) {
            return null;
        }
        final boolean useRegion = regionIsMask() && !region.isEmpty();
        final ColorSelection cs = colorSelection;
        BlendedFilter16.MaskSource mask = null;
        if (useRegion || cs.isActive()) {
            final Region r = region;
            mask = back -> {
                float[] m = useRegion ? r.rasterize(back.width(), back.height(), ctx) : null;
                if (!cs.isActive()) {
                    return m;
                }
                final float[] out = m != null ? m : new float[back.width() * back.height()];
                if (m == null) {
                    java.util.Arrays.fill(out, 1f);
                }
                final short[] d = back.data();
                java.util.stream.IntStream.range(0, back.height()).parallel().forEach(y -> {
                    for (int x = 0, o = y * back.width(), i = o * 3; x < back.width(); x++, o++, i += 3) {
                        if (out[o] > 0) {
                            out[o] *= cs.weight((d[i] & 0xFFFF) / 65535f, (d[i + 1] & 0xFFFF) / 65535f,
                                    (d[i + 2] & 0xFFFF) / 65535f);
                        }
                    }
                });
                return out;
            };
        }
        return BlendedFilter16.wrap(createFilter16(ctx), blendMode, opacity, mask);
    }

    /** Area the tool acts on (empty = whole image). */
    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region == null ? Region.EMPTY : region;
    }

    /** Colours the tool acts on (inactive = all). */
    public ColorSelection getColorSelection() {
        return colorSelection;
    }

    public void setColorSelection(ColorSelection colorSelection) {
        this.colorSelection = colorSelection == null ? ColorSelection.NONE : colorSelection;
    }

    /** Default values of all parameters. */
    public final float[] defaults() {
        int size = 0;
        for (ParamSpec p : params()) {
            size = Math.max(size, p.index() + p.count());
        }
        float[] d = new float[size];
        for (ParamSpec p : params()) {
            System.arraycopy(p.defaults(), 0, d, p.index(), p.count());
        }
        return d;
    }

    /** Value of the first entry of a parameter. */
    protected final float value(int index) {
        return values[index];
    }

    /** true if all values are the defaults (the tool has no effect then for most tools). */
    protected final boolean isDefault() {
        return Arrays.equals(values, defaults());
    }

    /** Resets all values to the defaults. */
    public void reset() {
        values = defaults();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float[] getValues() {
        return values.clone();
    }

    /**
     * Takes over the values; missing ones (older settings) keep their
     * defaults, extra ones are ignored.
     */
    @Override
    public void setValues(float[] newValues) {
        float[] v = defaults();
        if (newValues != null) {
            System.arraycopy(newValues, 0, v, 0, Math.min(v.length, newValues.length));
        }
        values = v;
    }

    /** Sets one value (e.g. from a slider). */
    public void setValue(int index, float value) {
        float[] v = values.clone();
        v[index] = value;
        values = v;
    }

    @Override
    public void setPosition(int val) {
        this.position = val;
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public float getOpacity() {
        return opacity;
    }

    @Override
    public void setOpacity(float opacity) {
        this.opacity = Math.max(0f, Math.min(1f, opacity));
    }

    @Override
    public BlendMode getBlendMode() {
        return blendMode;
    }

    @Override
    public void setBlendMode(BlendMode blendMode) {
        this.blendMode = blendMode == null ? defaultBlendMode() : blendMode;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        AbstractImageFilter c = (AbstractImageFilter) super.clone();
        c.values = values.clone();
        return c;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, position, Arrays.hashCode(values), active, opacity, blendMode, region, colorSelection);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        AbstractImageFilter o = (AbstractImageFilter) obj;
        return position == o.position && active == o.active
                && Float.floatToIntBits(opacity) == Float.floatToIntBits(o.opacity)
                && blendMode == o.blendMode && name.equals(o.name) && Arrays.equals(values, o.values)
                && region.equals(o.region) && colorSelection.equals(o.colorSelection);
    }

    @Override
    public String toString() {
        return name + "{pos=" + position + ", values=" + Arrays.toString(values) + ", active=" + active
                + ", opacity=" + opacity + ", mode=" + blendMode
                + (region.isEmpty() ? "" : ", region=" + region.contours().size() + (region.inverted() ? " inverted" : ""))
                + (colorSelection.isActive() ? ", colorSelection" : "") + '}';
    }
}
