/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import java.io.File;
import java.util.Locale;
import java.util.Set;

/**
 *
 * @author selfemp
 */
public class FileTypes {

    private static final String imageTypes = "jpg,jfif,tif,tiff,jpeg,png,psd,cr2,nef,raf,dng,x3f,heic,webp";
    private static final String videoTypes = "mp4,mov";
    // exact endings: a substring test of the list would also accept e.g. "if" or "pg"
    private static final Set<String> VALID_TYPES = Set.of((imageTypes + "," + videoTypes).split(","));

    /**
     *
     * @param fileName Name of the file to check as String
     * @return true if the name ends with a valid type else false
     */
    // not synchronized: called for every file by all loading threads
    public static boolean isValidType(String fileName) {
        int lastIndexOf = fileName.lastIndexOf('.');
        String fileEndingStr = "";
        if (lastIndexOf != -1) {
            fileEndingStr = fileName.substring(lastIndexOf + 1);
        } else {
            return false;
        }
        boolean isValid = VALID_TYPES.contains(fileEndingStr.toLowerCase(Locale.ROOT));
        if (isValid == true) {
            if (fileName.startsWith(".")) {
                isValid = false;
            } else if (fileName.startsWith("@")) {
                isValid = false;
            }
        }
        return isValid;
    }
    
    /**
     * @return the XMP sidecar of the media file ({@code <name>.xmp}), see XmpSidecar
     */
    public static String convertEditNameToFilename(String fileNameWithPath){
        int lastIndexOfExt = fileNameWithPath.lastIndexOf('.');
        int lastIndexOfSlash = fileNameWithPath.lastIndexOf(File.separatorChar);
        if (lastIndexOfExt != -1) {
            String fileStartStr = fileNameWithPath.substring(lastIndexOfSlash+1,lastIndexOfExt);
            String res= fileNameWithPath.substring(0, lastIndexOfSlash)+File.separatorChar+fileStartStr+".xmp";
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
