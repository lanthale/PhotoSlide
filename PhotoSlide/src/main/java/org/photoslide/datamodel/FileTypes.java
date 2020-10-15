/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

/**
 *
 * @author selfemp
 */
public class FileTypes {

    private static final String imageTypes = "jpg,tif,tiff,jpeg,png";
    private static final String videoTypes = "mp4,mov";

    /**
     *
     * @param fileName Name of the file to check as String
     * @return true if the name ends with a valid type else false
     */
    public static boolean isValidType(String fileName) {
        int lastIndexOf = fileName.lastIndexOf('.');
        String fileEndingStr = "";
        if (lastIndexOf != -1) {
            fileEndingStr = fileName.substring(lastIndexOf + 1);
        } else {
            return false;
        }
        return (imageTypes + "," + videoTypes).toUpperCase().contains(fileEndingStr.toUpperCase());
    }

    public static boolean isValidVideo(String fileName) {        
        int lastIndexOf = fileName.lastIndexOf('.');
        String fileEndingStr = "";
        if (lastIndexOf != -1) {
            fileEndingStr = fileName.substring(lastIndexOf + 1);
        } else {            
            return false;
        }        
        return videoTypes.toUpperCase().contains(fileEndingStr.toUpperCase());
    }
    
    public static boolean isValidImge(String fileName) {        
        int lastIndexOf = fileName.lastIndexOf('.');
        String fileEndingStr = "";
        if (lastIndexOf != -1) {
            fileEndingStr = fileName.substring(lastIndexOf + 1);
        } else {            
            return false;
        }        
        return imageTypes.toUpperCase().contains(fileEndingStr.toUpperCase());
    }

}
