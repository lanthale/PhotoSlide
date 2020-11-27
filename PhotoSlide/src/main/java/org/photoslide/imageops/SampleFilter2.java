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
public class SampleFilter2 {

    private Image image;
    private WritableImage filteredImage;
    private byte[] buffer;
    private int height;
    private int width;

    public SampleFilter2() {        
    }

    public void filter(double redLimit, double greenLimit, double blueLimit) {
        if (redLimit == 0) {
            redLimit = 0.2162;
            greenLimit = 0.7152;
            blueLimit = 0.0722;
        }        
        PixelWriter pixelWriter = filteredImage.getPixelWriter();
        byte[] targetBuffer = new byte[width * height * 4];
        for (int i = 0; i < buffer.length; i++) {
            int b = buffer[i];
            int g = buffer[i + 1];
            int r = buffer[i + 2];
            int a = buffer[i + 3];

            int grayLevel = (int) (redLimit * r + greenLimit * g + blueLimit * b);
            targetBuffer[i] = (byte) (grayLevel);
            targetBuffer[i + 1] = (byte) (grayLevel);
            targetBuffer[i + 2] = (byte) (grayLevel);
            targetBuffer[i + 3] = (byte) (a);
            i = i + 3;
        }
        pixelWriter.setPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), targetBuffer, 0, width * 4);
        //return filteredImage;
    }

    public Image load(Image img) {
        image=img;
        PixelReader pixelReader = image.getPixelReader();
        height = (int) image.getHeight();
        width = (int) image.getWidth(); 
        buffer = new byte[width * height * 4];
        pixelReader.getPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), buffer, 0, width * 4);
        filteredImage = new WritableImage(pixelReader,width, height);
        return filteredImage;
    }
}
