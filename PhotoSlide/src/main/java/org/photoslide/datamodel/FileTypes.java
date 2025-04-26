/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import java.io.File;

/**
 *
 * @author selfemp
 */
public class FileTypes {

    private static final String imageTypes = "jpg,jfif,tif,tiff,jpeg,png,psd,cr2,nef,raf,dng,x3f,heic,webp";
    private static final String videoTypes = "mp4,mov";

    /**
     *
     * @param fileName Name of the file to check as String
     * @return true if the name ends with a valid type else false
     */
    public synchronized static boolean isValidType(String fileName) {
        int lastIndexOf = fileName.lastIndexOf('.');
        String fileEndingStr = "";
        if (lastIndexOf != -1) {
            fileEndingStr = fileName.substring(lastIndexOf + 1);
        } else {
            return false;
        }
        boolean isValid = (imageTypes + "," + videoTypes).toUpperCase().contains(fileEndingStr.toUpperCase());
        if (isValid == true) {
            if (fileName.startsWith(".")) {
                isValid = false;
            } else if (fileName.startsWith("@")) {
                isValid = false;
            }
        }
        return isValid;
    }
    
    public static String convertEditNameToFilename(String fileNameWithPath){
        int lastIndexOfExt = fileNameWithPath.lastIndexOf('.');
        int lastIndexOfSlash = fileNameWithPath.lastIndexOf(File.separatorChar);
        if (lastIndexOfExt != -1) {
            String fileEndingStr = ".edit";
            String fileStartStr = fileNameWithPath.substring(lastIndexOfSlash+1,lastIndexOfExt);
            String res= fileNameWithPath.substring(0, lastIndexOfSlash)+File.separatorChar+"Ω_"+fileStartStr+fileEndingStr;
            return res;
        } else {
            return "";
        }
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

    public static boolean isValidImage(String fileName) {
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
