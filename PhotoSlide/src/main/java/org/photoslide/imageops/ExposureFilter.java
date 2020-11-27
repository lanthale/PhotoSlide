/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.imageops;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 *
 * @author selfemp
 */
public class ExposureFilter {

    private Image image;
    private WritableImage filteredImage;
    private byte[] buffer;
    private int height;
    private int width;
    private float exposure;
    protected int[] rTable, gTable, bTable;

    public ExposureFilter() {
        exposure = 1.0f;        
        rTable = gTable = bTable = makeTable();
    }

    public void setExposure(float exposure) {
        this.exposure = exposure;        
    }

    public float getExposure() {
        return exposure;
    }

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

    public int filterRGB(int rgba) {
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

    public void filter(float exp) {
        this.exposure = exp;
        rTable = gTable = bTable = makeTable();
        PixelWriter pixelWriter = filteredImage.getPixelWriter();
        byte[] targetBuffer = new byte[width * height * 4];
        for (int i = 0; i < buffer.length; i++) {
            int rgba = buffer[i];            
            int res = filterRGB(rgba);
            targetBuffer[i] = (byte) (res);                        
        }
        pixelWriter.setPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), targetBuffer, 0, width * 4);
    }

    public Image reset() {
        this.exposure = 1.0f;
        rTable = gTable = bTable = makeTable();
        return image;
    }
}
