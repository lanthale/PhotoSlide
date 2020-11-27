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
import javafx.scene.paint.Color;

/**
 *
 * @author selfemp
 */
public class SampleFilter {

    private final Image image;
    private WritableImage filteredImage;
    private int[] buffer;

    public SampleFilter(Image img) {
        this.image = img;
    }

    public Image filter(double redLimit, double greenLimit, double blueLimit) {
        if (redLimit == 0) {
            redLimit = 0.2162;
            greenLimit = 0.7152;
            blueLimit = 0.0722;
        }
        PixelReader pixelReader = image.getPixelReader();
        int height = (int) image.getHeight();
        int width = (int) image.getWidth();
        if (buffer == null) {
            buffer = new int[width * height * 4];
            pixelReader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), buffer, 0, width * 4);
            filteredImage = new WritableImage(width, height);
        }        
        PixelWriter pixelWriter = filteredImage.getPixelWriter();
        int[] targetBuffer = new int[width * height * 4];
        for (int i = 0; i < buffer.length; i++) {
            int pixel = buffer[i];
            int alpha = ((pixel >> 24) & 0xff);
            int red = ((pixel >> 16) & 0xff);
            int green = ((pixel >> 8) & 0xff);
            int blue = (pixel & 0xff);

            int grayLevel = (int) (redLimit * red + greenLimit * green + blueLimit * blue);
            int gray = (alpha << 24) + (grayLevel << 16) + (grayLevel << 8) + grayLevel;
            targetBuffer[i]=gray;
        }
        pixelWriter.setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), targetBuffer, 0, width*4);
        return filteredImage;
    }       
}
