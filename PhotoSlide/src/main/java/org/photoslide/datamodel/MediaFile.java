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
import java.io.ObjectInputStream;
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
public class MediaFile implements Serializable {

    private static final long serialVersionUID = 1L;
    private String name;
    private String pathStorage;
    private SimpleBooleanProperty loading;
    private SimpleBooleanProperty loadingError;

    private Image image;
    private Image unModifiyAbleImage;
    private Media media;

    private SimpleStringProperty title;
    private SimpleStringProperty keywords;
    private SimpleStringProperty camera;
    private SimpleStringProperty comments;
    private SimpleDoubleProperty rotationAngle;
    private SimpleIntegerProperty rating;
    private SimpleStringProperty place;
    private SimpleStringProperty faces;
    private Rectangle2D cropView;
    private Point2D orignalImageSize;
    private LocalDateTime recordTime;
    private SimpleBooleanProperty deleted;
    private SimpleBooleanProperty selected;
    private SimpleStringProperty stackName;
    private SimpleIntegerProperty stackPos;
    private SimpleBooleanProperty stacked;
    private FileTime creationTime;
    private FileTime lastModifyTime;
    private boolean subViewSelected;
    private ObservableList<ImageFilter> filterList;
    private Point gpsPosition;
    private LocalDateTime gpsDateTime;
    private double gpsHeight;
    private SimpleBooleanProperty bookmarked;

    public static enum MediaTypes {
        IMAGE,
        VIDEO,
        NONE
    }

    public static enum VideoTypes {
        SUPPORTED,
        UNSUPPORTED
    }
    private VideoTypes videoSupported;
    private MediaTypes mediaType;

    private void writeObject(ObjectOutputStream oos)
            throws IOException {
        //oos.defaultWriteObject();
        MediaFile_Serializable convertToSerializable = MediaFile_Serializable.convertToSerializable(this);
        oos.writeObject(convertToSerializable);
    }

    private void readObject(ObjectInputStream ois)
            throws ClassNotFoundException, IOException {
        //ois.defaultReadObject();
        initData();
        MediaFile_Serializable mserial = (MediaFile_Serializable) ois.readObject();
        MediaFile_Serializable.convertToMediaFile(mserial, this);
    }

    public MediaFile() {
        bookmarked = new SimpleBooleanProperty(false);
        loading = new SimpleBooleanProperty(true);
        loadingError = new SimpleBooleanProperty(false);
        subViewSelected = false;
        deleted = new SimpleBooleanProperty(false);
        selected = new SimpleBooleanProperty(false);
        stackName = new SimpleStringProperty();
        stackPos = new SimpleIntegerProperty(-1);
        stacked = new SimpleBooleanProperty(false);
        place = new SimpleStringProperty();
        faces = new SimpleStringProperty();
        filterList = FXCollections.observableArrayList();

        mediaType = MediaTypes.NONE;
        title = new SimpleStringProperty();
        keywords = new SimpleStringProperty();
        camera = new SimpleStringProperty();
        comments = new SimpleStringProperty();
        rotationAngle = new SimpleDoubleProperty(0.0);
        rating = new SimpleIntegerProperty(0);
        gpsHeight = -1;
        orignalImageSize = null;
        cropView = null;
    }

    public void initData() {
        bookmarked = new SimpleBooleanProperty(false);
        loading = new SimpleBooleanProperty(true);
        loadingError = new SimpleBooleanProperty(false);
        subViewSelected = false;
        deleted = new SimpleBooleanProperty(false);
        selected = new SimpleBooleanProperty(false);
        stackName = new SimpleStringProperty();
        stackPos = new SimpleIntegerProperty(-1);
        stacked = new SimpleBooleanProperty(false);
        place = new SimpleStringProperty();
        faces = new SimpleStringProperty();
        filterList = FXCollections.observableArrayList();
        title = new SimpleStringProperty();
        keywords = new SimpleStringProperty();
        camera = new SimpleStringProperty();
        comments = new SimpleStringProperty();
        rotationAngle = new SimpleDoubleProperty(0.0);
        rating = new SimpleIntegerProperty(0);
        gpsHeight = -1;
        orignalImageSize = null;
        cropView = null;
    }

    /**
     * This method steers on which properties a re-draw is occuring on the
     * visible cells. One of the most important functions to show in the UI any
     * changes inside of the mediafiles
     *
     * @return the list of mediafiles with the properties changed which is the
     * base for the re-draw happening
     */
    public static Callback<MediaFile, Observable[]> extractor() {
        return (MediaFile p) -> new Observable[]{p.loading, p.bookmarked, p.deleted, p.title, p.rotationAngle, p.rating, p.selected};
    }

    public Image setFilters() {
        if (this.unModifiyAbleImage != null) {
            Image imageWithFilters = getClonedImage(unModifiyAbleImage);
            for (ImageFilter imageFilter : filterList) {
                imageWithFilters = imageFilter.loadMediaData(imageWithFilters);
                imageFilter.filterMediaData(imageFilter.getValues());
            }
            return imageWithFilters;
        } else {
            return this.image;
        }
    }

    public void setImage(Image image) {
        this.image = image;
    }

    public void setMedia(Media video, VideoTypes videoTypes) {
        this.media = video;
        this.videoSupported = videoTypes;
    }

    public Image getImage() {
        return image;
    }

    public URL getImageUrl() throws MalformedURLException {
        return this.getPathStorage().toUri().toURL();
    }

    public Media getMedia() {
        return media;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void saveEdits() {
        //String fileNameWithOutExt = pathStorage.toString().substring(0, pathStorage.toString().lastIndexOf("."));
        //String fileNameWithExt = "Ω_" + fileNameWithOutExt + ".edit";

        String fileNameWithExt = getEditFilePath().toString();

        try (OutputStream output = new FileOutputStream(fileNameWithExt)) {
            Properties prop = new Properties();
            // set the properties value
            if (title.getValue() != null) {
                prop.setProperty("title", title.getValue());
            }
            if (keywords.getValue() != null) {
                prop.setProperty("keywords", keywords.getValue());
            }
            if (comments.getValue() != null) {
                prop.setProperty("comments", comments.getValue());
            }
            if (camera.getValue() != null) {
                prop.setProperty("camera", camera.getValue());
            }
            if (rotationAngle.getValue() != null) {
                prop.setProperty("rotation", rotationAngle.getValue() + "");
            }
            if (rating.getValue() != null) {
                prop.setProperty("rating", rating.getValue() + "");
            }
            if (deleted.getValue() != null) {
                prop.setProperty("deleted", deleted.getValue() + "");
            }
            if (cropView != null) {
                prop.setProperty("crop", cropView.getMinX() + ";" + cropView.getMinY() + ";" + cropView.getWidth() + ";" + cropView.getHeight());
            }
            if (orignalImageSize != null) {
                prop.setProperty("orgImgSize", orignalImageSize.getX() + ";" + orignalImageSize.getY());
            }
            if (stacked.getValue() != null) {
                prop.setProperty("stacked", stacked.getValue() + "");
            }
            if (stackName.getValue() != null) {
                prop.setProperty("stackName", stackName.getValue());
            }
            if (stackPos.getValue() != null) {
                prop.setProperty("stackPos", stackPos.getValue() + "");
            }
            if (filterList.isEmpty() == false) {
                ObjectMapper mapper = new ObjectMapper();
                filterList.forEach((imgFilter) -> {
                    try {
                        String value = mapper.writeValueAsString(imgFilter);
                        prop.put("ImageFilter:" + imgFilter.getName(), value);
                    } catch (JsonProcessingException ex) {
                        Logger.getLogger(MediaFile.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            } else {
                for (Enumeration<?> names = prop.propertyNames(); names.hasMoreElements();) {
                    String key = (String) names.nextElement();
                    if (key.contains("ImageFilter:") == true) {
                        prop.remove(key);
                    }
                }
            }
            if (place.getValue() != null) {
                prop.setProperty("place", place.getValue());
            }
            if (gpsDateTime != null) {
                prop.setProperty("gpsDateTime", gpsDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            if (gpsHeight != -1) {
                prop.setProperty("gpsHeight", "" + gpsHeight);
            }
            if (gpsPosition != null) {
                prop.setProperty("gpsPosition", gpsPosition.latitude + ";" + gpsPosition.longitude);
            }
            prop.store(output, null);

        } catch (IOException ex) {
            Logger.getLogger(MediaFile.class.getName()).log(Level.SEVERE, "Cannot save edit to file " + fileNameWithExt, ex);
        }
    }

    public void readEdits() {
        String fileNameWithOutExt = pathStorage.toString().substring(0, pathStorage.toString().lastIndexOf("."));
        String fileNameWithExt = fileNameWithOutExt + ".edit";
        Path source = new File(fileNameWithExt).toPath();

        if (new File(fileNameWithExt).exists()) {
            try {
                Files.move(source, getEditFilePath());
            } catch (IOException ex) {
                Logger.getLogger(MediaFile.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        fileNameWithExt = getEditFilePath().toString();

        try (InputStream input = new FileInputStream(fileNameWithExt)) {

            Properties prop = new Properties();

            // load a properties file
            prop.load(input);

            if (prop.getProperty("title") != null) {
                title.setValue(prop.getProperty("title"));
            }
            if (prop.getProperty("keywords") != null) {
                keywords.setValue(prop.getProperty("keywords"));
            }
            if (prop.getProperty("comments") != null) {
                comments.setValue(prop.getProperty("comments"));
            }
            if (prop.getProperty("camera") != null) {
                camera.setValue(prop.getProperty("camera"));
            }
            if (prop.getProperty("rotation") != null) {
                rotationAngle.setValue(Double.parseDouble(prop.getProperty("rotation")));
            }
            if (prop.getProperty("rating") != null) {
                rating.setValue(Integer.parseInt(prop.getProperty("rating")));
            }
            if (prop.getProperty("deleted") != null) {
                deleted.setValue(Boolean.parseBoolean(prop.getProperty("deleted")));
            }
            if (prop.getProperty("stacked") != null) {
                stacked.setValue(Boolean.parseBoolean(prop.getProperty("stacked")));
            }
            if (prop.getProperty("stackPos") != null) {
                stackPos.setValue(Integer.parseInt(prop.getProperty("stackPos")));
            }
            if (prop.getProperty("stackName") != null) {
                stackName.setValue(prop.getProperty("stackName"));
            }
            String cropValue = prop.getProperty("crop");
            if (cropValue != null) {
                StringTokenizer defaultTokenizer = new StringTokenizer(cropValue, ";");
                double[] rectValues = new double[4];
                int i = 0;
                while (defaultTokenizer.hasMoreTokens()) {
                    String nextToken = defaultTokenizer.nextToken();
                    rectValues[i] = Double.parseDouble(nextToken);
                    i++;
                }
                cropView = new Rectangle2D(rectValues[0], rectValues[1], rectValues[2], rectValues[3]);
            }
            String sizeValue = prop.getProperty("orgImgSize");
            if (sizeValue != null) {
                StringTokenizer defaultTokenizer = new StringTokenizer(sizeValue, ";");
                double[] pointValues = new double[2];
                int i = 0;
                while (defaultTokenizer.hasMoreTokens()) {
                    String nextToken = defaultTokenizer.nextToken();
                    pointValues[i] = Double.parseDouble(nextToken);
                    i++;
                }
                orignalImageSize = new Point2D(pointValues[0], pointValues[1]);
            }
            ObjectMapper mapper = new ObjectMapper();
            ArrayList<ImageFilter> rawList = new ArrayList<>();
            for (Enumeration<?> names = prop.propertyNames(); names.hasMoreElements();) {
                String key = (String) names.nextElement();
                if (key.contains("ImageFilter:") == true) {
                    ImageFilter ifm = null;
                    try {
                        String cname = key.substring(12);
                        ifm = (ImageFilter) mapper.readValue(prop.getProperty(key), Class.forName("org.photoslide.imageops." + cname));
                    } catch (JsonProcessingException | ClassNotFoundException ex) {
                        Logger.getLogger(MediaFile.class.getName()).log(Level.SEVERE, "Cannot find class name in config file", ex);
                    }
                    if (ifm != null) {
                        if (rawList.contains(ifm) == false) {
                            rawList.add(ifm);
                        }
                    }
                }
            }
            if (rawList.isEmpty() == false) {
                rawList.sort(Comparator.comparing(imageFilter -> imageFilter.getPosition()));
                for (ImageFilter imageFilter : rawList) {
                    if (filterList.contains(imageFilter) == false) {
                        filterList.add(imageFilter);
                    }
                }
            }
            if (prop.getProperty("gpsDateTime") != null) {
                String propStr = prop.getProperty("gpsDateTime", null);
                if (propStr != null) {
                    try {
                        gpsDateTime = LocalDateTime.parse(propStr);
                    } catch (DateTimeParseException e) {
                        Logger.getLogger(MediaFile.class.getName()).log(Level.SEVERE, "Cannot convert GPS date/time!", e);
                    }
                }
            }
            if (prop.getProperty("place") != null) {
                place.setValue(prop.getProperty("place"));
            }
            if (prop.getProperty("gpsHeight", null) != null) {
                gpsHeight = Double.parseDouble(prop.getProperty("gpsHeight", "-1"));
            }
            if (prop.getProperty("gpsPosition") != null && prop.getProperty("gpsPosition").length() > 2) {
                String property = prop.getProperty("gpsPosition");
                if (property.contains("°")) {
                    setGpsPositionFromDMS(property);
                } else {
                    setGpsPositionFromDegree(property);
                }
            }
        } catch (IOException ex) {
            //Do nothing if file not found
        }
    }

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
        final MediaFile other = (MediaFile) obj;
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

    public MediaTypes getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaTypes mediaType) {
        this.mediaType = mediaType;
    }

    public Path getPathStorage() {
        return Path.of(pathStorage);
    }

    public void setPathStorage(Path pathStorage) {
        this.pathStorage = pathStorage.toString();
    }

    public VideoTypes getVideoSupported() {
        return videoSupported;
    }

    public void setVideoSupported(VideoTypes videoSupported) {
        this.videoSupported = videoSupported;
    }

    public SimpleDoubleProperty getRotationAngleProperty() {
        return rotationAngle;
    }

    public void setRotationAngle(double rotation) {
        this.rotationAngle.set(rotation);
    }

    public SimpleIntegerProperty getRatingProperty() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating.set(rating);
    }

    public Rectangle2D getCropView() {
        return cropView;
    }

    public void setCropView(Rectangle2D cropView) {
        this.cropView = cropView;
    }

    public Point2D getOrignalImageSize() {
        return orignalImageSize;
    }

    public void setOrignalImageSize(Point2D orignalImageSize) {
        this.orignalImageSize = orignalImageSize;
    }

    public SimpleStringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public SimpleStringProperty cameraProperty() {
        return camera;
    }

    public void setCamera(String camera) {
        this.camera.set(camera);
    }

    public SimpleBooleanProperty deletedProperty() {
        return deleted;
    }

    public boolean isDeleted() {
        return deleted.get();
    }

    public void setDeleted(boolean deleted) {
        this.deleted.set(deleted);
    }

    public LocalDateTime getRecordTime() {
        return recordTime;
    }

    public void setRecordTime(LocalDateTime recordTime) {
        this.recordTime = recordTime;
    }

    public SimpleBooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public FileTime getCreationTime() throws IOException {
        if (creationTime == null) {
            BasicFileAttributes attr = Files.readAttributes(Path.of(pathStorage), BasicFileAttributes.class);
            creationTime = attr.creationTime();
        }
        return creationTime;
    }

    public void setCreationTime(FileTime creationTime) {
        this.creationTime = creationTime;
    }

    public FileTime getLastModifyTime() throws IOException {
        if (lastModifyTime == null) {
            BasicFileAttributes attr = Files.readAttributes(Path.of(pathStorage), BasicFileAttributes.class);
            lastModifyTime = attr.lastModifiedTime();
        }
        return lastModifyTime;
    }

    public void setLastModifyTime(FileTime lastModifyTime) {
        this.lastModifyTime = lastModifyTime;
    }

    public Path getEditFilePath() {
        String fileNameWithOutExt = pathStorage.toString().substring(0, pathStorage.toString().lastIndexOf("."));
        String fileNameWithExt = fileNameWithOutExt + ".edit";
        Path source = new File(fileNameWithExt).toPath();
        return source.resolveSibling("Ω_" + source.toFile().getName());
    }

    public String getStackName() {
        if (stackName.get() == null) {
            return "";
        } else {
            return stackName.get();
        }
    }

    public void setStackName(String name) {
        this.stackName.set(name);
    }

    public int getStackPos() {
        return stackPos.get();
    }

    public void setStackPos(int pos) {
        this.stackPos.set(pos);
    }

    public boolean isStacked() {
        return stacked.get();
    }

    public void setStacked(boolean stValue) {
        this.stacked.set(stValue);
    }

    public boolean isSubViewSelected() {
        return subViewSelected;
    }

    public void setSubViewSelected(boolean subViewSelected) {
        this.subViewSelected = subViewSelected;
    }

    public void setKeywords(String keywords) {
        this.keywords.set(keywords);
    }

    public String getKeywords() {
        return this.keywords.get();
    }

    public ObservableList<ImageFilter> getFilterList() {
        return filterList;
    }

    public void addImageFilter(ImageFilter ifm) {
        filterList.add(ifm);
        System.out.println("size filter:" + filterList.size());
        ifm.setPosition(filterList.indexOf(ifm));
    }

    public void removeImageFilter(ImageFilter ifm) {
        filterList.remove(ifm);
    }

    public ObservableList<ImageFilter> getFilterListWithoutImageData() {
        ObservableList<ImageFilter> newFilterList = FXCollections.observableArrayList();
        filterList.forEach(imageFilter -> {
            try {
                Object o = imageFilter.clone();
                newFilterList.add((ImageFilter) o);
            } catch (CloneNotSupportedException ex) {
                Logger.getLogger(MediaFile.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        return newFilterList;
    }

    public ImageFilter getFilterForName(String s) {
        ImageFilter retVal = null;
        for (ImageFilter imageFilter : filterList) {
            if (imageFilter.getName().equalsIgnoreCase(s)) {
                retVal = imageFilter;
                break;
            }
        }
        return retVal;
    }

    public void setFilterList(ObservableList<ImageFilter> filterList) {
        this.filterList = filterList;
    }

    public Image getClonedImage(Image img) {
        if (img == null) {
            return null;
        }
        image = img;
        PixelReader pixelReader = image.getPixelReader();
        int height = (int) image.getHeight();
        int width = (int) image.getWidth();
        byte[] buffer = new byte[width * height * 4];
        try {
            pixelReader.getPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), buffer, 0, width * 4);
            Image filteredImage = new WritableImage(pixelReader, width, height);
            return filteredImage;
        } catch (Exception e) {
            return null;
        }
    }

    public Image getUnModifiyAbleImage() {
        return unModifiyAbleImage;
    }

    public void setUnModifiyAbleImage(Image unModifiyAbleImage) {
        this.unModifiyAbleImage = unModifiyAbleImage;
    }

    public SimpleStringProperty placeProperty() {
        return place;
    }

    public SimpleStringProperty facesProperty() {
        return faces;
    }

    public SimpleStringProperty commentsProperty() {
        return comments;
    }

    public String getGpsPosition() {
        String latRef, lonRef;
        if (gpsPosition == null) {
            return "";
        }
        DegreeCoordinate fromDegreesLat = Coordinate.fromDegrees(gpsPosition.latitude);
        DegreeCoordinate fromDegreesLon = Coordinate.fromDegrees(gpsPosition.longitude);
        if (gpsPosition.latitude < 0) {
            latRef = "S";
        } else {
            latRef = "N";
        }
        if (gpsPosition.longitude < 0) {
            lonRef = "W";
        } else {
            lonRef = "E";
        }
        DMSCoordinate toDMSCoordinateLat = fromDegreesLat.toDMSCoordinate();
        DMSCoordinate toDMSCoordinateLon = fromDegreesLon.toDMSCoordinate();
        String ret = toDMSCoordinateLat.wholeDegrees + "°" + toDMSCoordinateLat.minutes + "'" + toDMSCoordinateLat.seconds + "''" + latRef;
        ret = ret + ";" + toDMSCoordinateLon.wholeDegrees + "°" + toDMSCoordinateLon.minutes + "'" + toDMSCoordinateLon.seconds + "''" + lonRef;
        return ret;
    }

    public double getGpsLatPosAsDouble() {
        return gpsPosition.latitude;

    }

    public double getGpsLonPosAsDouble() {
        return gpsPosition.longitude;

    }

    public double parseGPSString(String source) {
        if (source == null || source.equalsIgnoreCase(";")) {
            return -1;
        }
        String degree = source.substring(0, source.indexOf("°"));
        String minutes = source.substring(source.indexOf("°") + 1, source.indexOf("'"));
        String seconds = source.substring(source.indexOf("'") + 1, source.indexOf("\""));
        if (seconds.contains(",")) {
            seconds = seconds.replace(",", ".");
        }
        if (minutes.contains(",")) {
            minutes = minutes.replace(",", ".");
        }
        double d = Double.parseDouble(degree);
        double m = Double.parseDouble(minutes);
        double s = Double.parseDouble(seconds);
        double dd = Math.signum(d) * (Math.abs(d) + (m / 60.0) + (s / 3600.0));
        DMSCoordinate dms = new DMSCoordinate(d, m, s);
        return dms.toDegreeCoordinate().decimalDegrees;
    }

    public int[] getGpsLatPosAsRational() {
        int[] rat1 = new int[6];
        DegreeCoordinate fromDegreesLat = Coordinate.fromDegrees(gpsPosition.latitude);
        DMSCoordinate toDMSCoordinate = fromDegreesLat.toDMSCoordinate();
        double d = toDMSCoordinate.wholeDegrees;
        double m = toDMSCoordinate.minutes;
        double s = toDMSCoordinate.seconds;
        rat1[0] = (int) d;
        rat1[1] = 1;
        rat1[2] = (int) m;
        rat1[3] = 1;
        rat1[4] = (int) s;
        rat1[5] = 100;
        return rat1;
    }

    public String getGpsLatPosRef() {
        String latRef;
        if (gpsPosition.latitude < 0) {
            latRef = "S";
        } else {
            latRef = "N";
        }
        return latRef;
    }

    public int[] getGpsLonPosAsRational() {
        int[] rat1 = new int[6];
        DegreeCoordinate fromDegrees = Coordinate.fromDegrees(gpsPosition.longitude);
        DMSCoordinate toDMSCoordinate = fromDegrees.toDMSCoordinate();
        double d = toDMSCoordinate.wholeDegrees;
        double m = toDMSCoordinate.minutes;
        double s = toDMSCoordinate.seconds;
        rat1[0] = (int) d;
        rat1[1] = 1;
        rat1[2] = (int) m;
        rat1[3] = 1;
        rat1[4] = (int) s;
        rat1[5] = 100;
        return rat1;
    }

    public int[] getGpsHeightAsRational() {
        int[] rat1 = new int[2];
        rat1[0] = (int) gpsHeight * 10000;
        rat1[1] = 10000;
        return rat1;
    }

    public String getGpsLonPosRef() {
        String lonRef;
        if (gpsPosition.latitude < 0) {
            lonRef = "W";
        } else {
            lonRef = "E";
        }
        return lonRef;
    }

    public void setGpsPositionFromDMS(String gpsPosition) {
        if (gpsPosition.length() < 2) {
            return;
        }
        StringTokenizer token = new StringTokenizer(gpsPosition, ";");
        double lat = parseGPSString(token.nextToken());
        double lon = parseGPSString(token.nextToken());
        this.gpsPosition = new Point(lat, lon);
    }

    public void setGpsPositionFromDegree(String gpsPosition) {
        StringTokenizer token = new StringTokenizer(gpsPosition, ";");
        double lat = Double.parseDouble(token.nextToken());
        double lon = Double.parseDouble(token.nextToken());
        this.gpsPosition = new Point(lat, lon);
    }

    public LocalDateTime getGpsDateTime() {
        return gpsDateTime;
    }

    public void setGpsDateTime(String dateTimeStr) {
        LocalDateTime parse = null;
        if (dateTimeStr.contains(":") == false) {
            return;
        }
        if (dateTimeStr.contains(".") == false) {
            return;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd'T'HH:mm:ss','SS");
        try {
            parse = LocalDateTime.parse(dateTimeStr, formatter);
        } catch (DateTimeParseException e) {
            try {
                formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd'T'HH:mm:ss");
                parse = LocalDateTime.parse(dateTimeStr, formatter);
            } catch (DateTimeParseException e2) {
                Logger.getLogger(MediaFile.class.getName()).log(Level.SEVERE, "Cannot parse date/time: " + dateTimeStr, e);
            }
        }
        this.gpsDateTime = parse;
    }

    public double getGpsHeight() {
        return gpsHeight;
    }

    public void setGpsHeight(double gpsHeight) {
        if (gpsHeight != -1) {
            this.gpsHeight = gpsHeight;
        }
    }

    public boolean isLoading() {
        return loading.get();
    }

    public void setLoading(boolean value) {
        this.loading.set(value);
    }

    public boolean getLoadingError() {
        return loadingError.get();
    }

    public void setLoadingError(boolean loadingError) {
        this.loadingError.set(loadingError);
    }
    
    

    public boolean isBookmarked() {
        return bookmarked.get();
    }

    public void setBookmarked(boolean bookmarked) {
        this.bookmarked.set(bookmarked);
    }

    public SimpleBooleanProperty loadingProperty() {
        return loading;
    }

    public SimpleBooleanProperty bookmarkedProperty() {
        return bookmarked;
    }

    public SimpleStringProperty getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place.set(place);
    }

    public SimpleStringProperty getFaces() {
        return faces;
    }

    public void setFaces(String faces) {
        this.faces.set(faces);
    }

    public SimpleStringProperty getTitle() {
        return title;
    }

    public SimpleStringProperty getCamera() {
        return camera;
    }

    public SimpleStringProperty getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments.set(comments);
    }

    public void removeAllEdits() {
        String fileNameWithExt = getEditFilePath().toString();
        File editFile = new File(fileNameWithExt);
        if (editFile.exists()) {
            Platform.runLater(() -> {
                editFile.delete();
            });
            deleted.set(false);
            place.set("");
            faces.set("");
            rotationAngle.set(0.0);
            rating.set(0);
            gpsHeight = -1;
            cropView = null;
            orignalImageSize = null;
        }
    }

    /**
     * checks if the mediafile is a raw image format e.g. NEF, CR2, X3F, ...
     *
     * @return true if file is a raw file
     */
    public boolean isRawImage() {
        if (mediaType == MediaTypes.IMAGE) {
            String ext = name.substring(name.lastIndexOf("."));
            if (ext.toLowerCase().contains("jpg")) {
                return false;
            }
            if (ext.toLowerCase().contains("jpeg")) {
                return false;
            }
            if (ext.toLowerCase().contains("png")) {
                return false;
            }
            if (ext.toLowerCase().contains("tif")) {
                return false;
            }
            if (ext.toLowerCase().contains("tiff")) {
                return false;
            }
        }
        return true;
    }

    /**
     * checks if the mediafile is a raw image format e.g. NEF, CR2, X3F, ...
     *
     * @return true if file is a raw file
     */
    public boolean isHEIFImage() {
        if (mediaType == MediaTypes.IMAGE) {
            String ext = name.substring(name.lastIndexOf("."));
            if (ext.toLowerCase().contains("heic")) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    public boolean isVideoFile() {
        if (mediaType == MediaTypes.VIDEO) {
            return true;
        } else {
            return false;
        }
    }

}
