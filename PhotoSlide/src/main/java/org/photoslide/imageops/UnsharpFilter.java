/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.imageops;

import java.util.Arrays;
import java.util.Objects;
import org.photoslide.datamodel.Filter16;

/**
 * Settings for unsharp masking. The 16 bit operation is not implemented yet,
 * so the filter has no effect for now.
 *
 * @author selfemp
 */
public class UnsharpFilter implements ImageFilter {

    private final String name;
    private int pos;
    private float[] values;

    private float amount;
    private int threshold;
    private final int radius = 2;

    public UnsharpFilter() {
        name = "UnsharpFilter";
        amount = 0.5f;
        threshold = 1;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float[] getValues() {
        return new float[]{amount, threshold};
    }

    @Override
    public void setValues(float[] values) {
        this.values = values;
        this.amount = values[0];
        this.threshold = (int) values[1];
    }

    @Override
    public Filter16 toFilter16() {
        return null;
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
        UnsharpFilter tmp = null;
        try {
            tmp = (UnsharpFilter) super.clone();
            tmp.values = values;
            tmp.pos = pos;
            tmp.amount = amount;
            tmp.threshold = threshold;
        } catch (CloneNotSupportedException e) {
        }
        return tmp;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.name);
        hash = 79 * hash + this.pos;
        hash = 79 * hash + Arrays.hashCode(this.values);
        hash = 79 * hash + Float.floatToIntBits(this.amount);
        hash = 79 * hash + Float.floatToIntBits(this.threshold);
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
        final UnsharpFilter other = (UnsharpFilter) obj;
        if (this.pos != other.pos) {
            return false;
        }
        if (Float.floatToIntBits(this.amount) != Float.floatToIntBits(other.amount)) {
            return false;
        }
        if (Float.floatToIntBits(this.threshold) != Float.floatToIntBits(other.threshold)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Arrays.equals(this.values, other.values)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "UnsharpFilter{" + "name=" + name + ", pos=" + pos + ", values=" + values + ", amount=" + amount + ", threshold=" + threshold + ", radius=" + radius + '}';
    }    

}
