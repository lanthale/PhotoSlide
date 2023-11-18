/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.media.Media;
import javafx.util.Callback;
import org.photoslide.browsermetadata.Coordinate;
import org.photoslide.browsermetadata.DMSCoordinate;
import org.photoslide.browsermetadata.DegreeCoordinate;
import org.photoslide.browsermetadata.Point;
import org.photoslide.imageops.ImageFilter;

/**
 *
 * @author selfemp
 */
public class MediaFile_Serializable implements Serializable {

    private static final long serialVersionUID = 1L;
    private String name;
    private String pathStorage;
    private boolean loading;

    private transient Image image;
    private transient Image unModifiyAbleImage;
    private transient Media media;

    private String title;
    private String keywords;
    private String camera;
    private String comments;
    private SimpleDoubleProperty rotationAngle;
    private SimpleIntegerProperty rating;
    private String place;
    private String faces;
    private Rectangle2D cropView;
    private Point2D orignalImageSize;
    private LocalDateTime recordTime;
    private boolean deleted;
    private boolean selected;
    private String stackName;
    private SimpleIntegerProperty stackPos;
    private boolean stacked;
    private FileTime creationTime;
    private boolean subViewSelected;
    private List<ImageFilter> filterList;
    private Point gpsPosition;
    private LocalDateTime gpsDateTime;
    private double gpsHeight;
    private boolean bookmarked;

    public enum MediaTypes {
        IMAGE,
        VIDEO,
        NONE
    }

    public enum VideoTypes {
        SUPPORTED,
        UNSUPPORTED
    }
    private VideoTypes videoSupported;
    private MediaTypes mediaType;

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.name);
        hash = 23 * hash + Objects.hashCode(this.pathStorage);
        hash = 23 * hash + Objects.hashCode(this.videoSupported);
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
        final MediaFile_Serializable other = (MediaFile_Serializable) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.pathStorage, other.pathStorage)) {
            return false;
        }
        if (this.videoSupported != other.videoSupported) {
            return false;
        }
        return true;
    }
    
    public static MediaFile_Serializable convertToSerializable(MediaFile m){
        MediaFile_Serializable mserial=new MediaFile_Serializable();
        return mserial;
    }
    
    public static MediaFile convertToSerializable(MediaFile_Serializable mserial){
        MediaFile m=new MediaFile();
        return m;
    }

}
