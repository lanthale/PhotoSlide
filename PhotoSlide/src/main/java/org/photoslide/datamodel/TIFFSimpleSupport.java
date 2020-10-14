/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import com.icafe4j.image.ImageIO;
import com.icafe4j.image.reader.ImageReader;
import com.icafe4j.image.tiff.TIFFImage;
import com.icafe4j.io.FileCacheRandomAccessInputStream;
import com.icafe4j.io.PeekHeadInputStream;
import com.icafe4j.io.RandomAccessInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

/**
 *
 * @author selfemp
 */
public class TIFFSimpleSupport {

    public Image readImage(URI uri) {
        FileInputStream fin = null;
        Image image = null;
        try {
            File f = new File(uri);
            fin = new FileInputStream(f);

            BufferedImage bufferedImage;
            try (PeekHeadInputStream peekHeadInputStream = new PeekHeadInputStream(fin, ImageIO.IMAGE_MAGIC_NUMBER_LEN)) {
                ImageReader reader = ImageIO.getReader(peekHeadInputStream);
                bufferedImage = reader.read(peekHeadInputStream);
            }
            if (bufferedImage == null) {
                Logger.getLogger(TIFFSimpleSupport.class.getName()).log(Level.SEVERE, "Failed to load image");
                return null;
            }
            image = SwingFXUtils.toFXImage(bufferedImage, null);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(TIFFSimpleSupport.class.getName()).log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            Logger.getLogger(TIFFSimpleSupport.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                fin.close();
            } catch (IOException ex) {
                Logger.getLogger(TIFFSimpleSupport.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return image;
    }
}
