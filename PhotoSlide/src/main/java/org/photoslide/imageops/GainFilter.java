/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.imageops;

import java.util.Objects;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.LutFilter16;

/**
 * Set the gain and bias (contrast and brightness curve of Perlin). Works on
 * the sRGB encoded values like the former 8 bit filter, so stored settings
 * keep their look. 0.5 / 0.5 has no effect.
 *
 * @min-value: 0
 * @max-value: 1
 * @author selfemp
 */
public class GainFilter implements ImageFilter {

    public static final String NAME = "GainFilter";

    private final String name;
    private int pos;
    private float gain;
    private float bias;

    public GainFilter() {
        name = NAME;
        gain = 0.5f;
        bias = 0.5f;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float[] getValues() {
        return new float[]{gain, bias};
    }

    @Override
    public void setValues(float[] values) {
        this.gain = values[0];
        this.bias = values[1];
    }

    @Override
    public Filter16 toFilter16() {
        if (gain == 0.5f && bias == 0.5f) {
            return null;
        }
        final float g = gain;
        final float b = bias;
        return LutFilter16.encodedTone("Gain " + g + "/" + b,
                v -> ImageMath.bias(ImageMath.gain((float) v, g), b));
    }

    @Override
    public void setPosition(int val) {
        this.pos = val;
    }

    @Override
    public int getPosition() {
        return pos;
    }

    /**
     *
     * @return clone of the filter
     */
    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.name);
        hash = 79 * hash + this.pos;
        hash = 79 * hash + Float.floatToIntBits(this.gain);
        hash = 79 * hash + Float.floatToIntBits(this.bias);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GainFilter other = (GainFilter) obj;
        if (this.pos != other.pos) {
            return false;
        }
        if (Float.floatToIntBits(this.gain) != Float.floatToIntBits(other.gain)) {
            return false;
        }
        if (Float.floatToIntBits(this.bias) != Float.floatToIntBits(other.bias)) {
            return false;
        }
        return Objects.equals(this.name, other.name);
    }

    @Override
    public String toString() {
        return "GainFilter{" + "name=" + name + ", pos=" + pos + ", gain=" + gain + ", bias=" + bias + '}';
    }

}
