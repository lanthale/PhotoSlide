/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.imageops;

import java.util.Arrays;
import java.util.Objects;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 *
 * @author selfemp
 */
public class ExposureFilter implements ImageFilter {

    private Image image;
    private WritableImage filteredImage;
    private final String name;
    private int pos;
    private float[] values;    
    private int height;
    private int width;
    private float exposure;
    protected int[] rTable, gTable, bTable;
    private byte[] buffer;    

    public ExposureFilter() {
        name = "ExposureFilter";
        exposure = 1.0f;
        rTable = gTable = bTable = makeTable();
    }

    @Override
    public Image loadMediaData(Image img) {
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
        this.exposure = 1.0f;
        rTable = gTable = bTable = makeTable();
        return image;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public float[] getValues() {
        return new float[]{exposure};
    }

    @Override
    public void filterMediaData(float[] values) {
        this.values = values;
        this.exposure = values[0];
        rTable = gTable = bTable = makeTable();
        PixelWriter pixelWriter = filteredImage.getPixelWriter();
        Thread.ofVirtual().start(() -> {
            byte[] targetBuffer = new byte[width * height * 4];
            for (int i = 0; i < buffer.length; i++) {
                int rgba = buffer[i];
                int res = filterRGB(rgba);
                targetBuffer[i] = (byte) (res);
            }
            pixelWriter.setPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), targetBuffer, 0, width * 4);
        });
    }            

    @Override
    public void setValues(float[] values) {
        this.values = values;
        this.exposure = values[0];
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
        return 1 - (float) Math.exp(-f * exposure);
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
        ExposureFilter tmp = null;
        try {
            tmp = (ExposureFilter) super.clone();
            tmp.values = values;
            tmp.pos = pos;
            tmp.exposure = exposure;
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
        return "ExposureFilter{" + "name=" + name + ", pos=" + pos + ", values=" + values + ", exposure=" + exposure + '}';
    }    

}
