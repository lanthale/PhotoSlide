/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.imageops;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import org.photoslide.datamodel.Filter16;
import org.photoslide.datamodel.LutFilter16;

/**
 * Exposure in stops (+1 = twice as much light), applied in linear light.
 *
 * Version 1 (the old 8 bit filter) stored a factor e of the curve
 * 1 - exp(-x * e). Such values are converted when they are read.
 *
 * @author selfemp
 */
public class ExposureFilter implements ImageFilter {

    public static final String NAME = "ExposureFilter";

    public static final int VERSION = 2;
    public static final float MIN_STOPS = -5f;
    public static final float MAX_STOPS = 5f;

    private final String name;
    private int pos;
    private float exposure;

    public ExposureFilter() {
        name = NAME;
        exposure = 0f;
    }

    @JsonCreator
    ExposureFilter(@JsonProperty("values") float[] values, @JsonProperty("position") int position,
            @JsonProperty("version") Integer version) {
        this();
        this.pos = position;
        if (values != null && values.length > 0) {
            exposure = (version == null || version < VERSION) ? fromLegacy(values[0]) : values[0];
        }
    }

    /**
     * Same as reading the JSON form: values of version 1 (or without version)
     * are converted from the old 8 bit curve.
     */
    public static ExposureFilter of(float[] values, int position, Integer version) {
        return new ExposureFilter(values, position, version);
    }

    /**
     * For small values the old curve is a gain of e on the encoded values,
     * which is about e^2.2 in linear light.
     */
    static float fromLegacy(float e) {
        if (e <= 0f) {
            return MIN_STOPS;
        }
        float stops = (float) (2.2 * Math.log(e) / Math.log(2.0));
        return Math.max(MIN_STOPS, Math.min(MAX_STOPS, stops));
    }

    @Override
    public String getName() {
        return name;
    }

    public int getVersion() {
        return VERSION;
    }

    @Override
    public float[] getValues() {
        return new float[]{exposure};
    }

    @Override
    public void setValues(float[] values) {
        this.exposure = values[0];
    }

    @Override
    public Filter16 toFilter16() {
        if (exposure == 0f) {
            return null;
        }
        return LutFilter16.exposure(exposure);
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
        hash = 79 * hash + Float.floatToIntBits(this.exposure);
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
        final ExposureFilter other = (ExposureFilter) obj;
        if (this.pos != other.pos) {
            return false;
        }
        if (Float.floatToIntBits(this.exposure) != Float.floatToIntBits(other.exposure)) {
            return false;
        }
        return Objects.equals(this.name, other.name);
    }

    @Override
    public String toString() {
        return "ExposureFilter{" + "name=" + name + ", pos=" + pos + ", exposure=" + exposure + '}';
    }

}
