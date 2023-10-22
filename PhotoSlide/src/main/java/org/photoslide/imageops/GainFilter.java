/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.imageops;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * Set the gain.
 *
 * @min-value: 0
 * @max-value: 1
 * @author selfemp
 */
public class GainFilter implements ImageFilter {

    private Image image;
    private WritableImage filteredImage;
    private final String name;
    private int pos;
    private float[] values;
    private byte[] buffer;
    private int height;
    private int width;
    private float gain;
    private float bias;
    protected int[] rTable, gTable, bTable;

    public GainFilter() {
        name = "GainFilter";
        gain = 0.5f;
        bias = 0.5f;
        rTable = gTable = bTable = makeTable();
    }

    @Override
    public Image load(Image img) {
        image = img;
        PixelReader pixelReader = image.getPixelReader();
        height = (int) image.getHeight();
        width = (int) image.getWidth();
        buffer = new byte[width * height * 4];
        pixelReader.getPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), buffer, 0, width * 4);
        filteredImage = new WritableImage(pixelReader, width, height);
        return filteredImage;
    }

    @Override
    public Image reset() {
        gain = 0.5f;
        bias = 0.5f;
        rTable = gTable = bTable = makeTable();
        return image;
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
    public synchronized void filter(float[] values) {
        this.values = values;
        this.gain = values[0];
        this.bias = values[1];
        rTable = gTable = bTable = makeTable();
        PixelWriter pixelWriter = filteredImage.getPixelWriter();
        byte[] targetBuffer = new byte[width * height * 4];
        Thread.ofVirtual().start(() -> {
            IntStream map = IntStream.range(0, buffer.length).map(i -> buffer[i] & 0xFF);
            AtomicInteger i = new AtomicInteger();
            map.parallel().forEachOrdered((value) -> {
                int rgba = buffer[i.get()];
                int res = filterRGB(rgba);
                targetBuffer[i.get()] = (byte) (res);
                i.addAndGet(1);
            });
            pixelWriter.setPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), targetBuffer, 0, width * 4);
        });
    }

    @Override
    public void setValues(float[] values) {
        this.values = values;
        this.gain = values[0];
        this.bias = values[1];
    }

    private int filterRGB(int rgba) {
        int a = rgba & 0xff000000;
        int r = (rgba >> 16) & 0xff;
        int g = (rgba >> 8) & 0xff;
        int b = rgba & 0xff;
        r = rTable[r];
        g = gTable[g];
        b = bTable[b];
        return a | (r << 16) | (g << 8) | b;
    }

    private float transferFunction(float f) {
        f = ImageMath.gain(f, gain);
        f = ImageMath.bias(f, bias);
        return f;
    }

    private int[] makeTable() {
        int[] table = new int[256];
        for (int i = 0; i < 256; i++) {
            table[i] = PixelUtils.clamp((int) (255 * transferFunction(i / 255.0f)));
        }
        return table;
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
        GainFilter tmp = null;
        try {
            tmp = (GainFilter) super.clone();
            tmp.values = values;
            tmp.pos = pos;
            tmp.gain = gain;
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
        hash = 79 * hash + Float.floatToIntBits(this.gain);
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
        return "GainFilter{" + "name=" + name + ", pos=" + pos + ", values=" + values + ", gain=" + gain + ", bias=" + bias + '}';
    }

    @Override
    public void filterIcon(float[] values) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public Image loadIcon(Image img) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

}
