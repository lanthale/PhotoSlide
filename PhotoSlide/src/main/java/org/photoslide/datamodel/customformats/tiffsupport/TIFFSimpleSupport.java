/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel.customformats.tiffsupport;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javax.imageio.ImageIO;

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
            //fin = new FileInputStream(f);

            BufferedImage bufferedImage;
            
            
            bufferedImage = ImageIO.read(f);
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
            /*try {
                fin.close();
            } catch (IOException ex) {
                Logger.getLogger(TIFFSimpleSupport.class.getName()).log(Level.SEVERE, null, ex);
            }*/
        }
        return image;
    }
}
