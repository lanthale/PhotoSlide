/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import org.photoslide.browsermetadata.Point;
import org.photoslide.imageops.ImageFilter;

/**
 *
 * @author selfemp
 */
public class MediaFile_Serializable implements Serializable {

    private static final long serialVersionUID = 2L;
    private String name;
    private String pathStorage;
    private boolean loading;
    private boolean loadingError;

    private String title;
    private String keywords;
    private String camera;
    private String comments;
    private double rotationAngle;
    private int rating;
    private String place;
    private String faces;
    private double cropViewX;
    private double cropViewY;
    private double cropViewWidth;
    private double cropViewHeigth;
    private double orignalImageSizeX;
    private double orignalImageSizeY;
    private LocalDateTime recordTime;
    private boolean deleted;
    private boolean selected;
    private String stackName;
    private int stackPos;
    private boolean stacked;
    private boolean subViewSelected;
    private Point gpsPosition;
    private LocalDateTime gpsDateTime;
    private double gpsHeight;
    private boolean bookmarked;
    private String videoSupported;
    private String mediaType;
    private List<String> filterList;
    private String creationTime;
    private String lastModifyTime;

    public MediaFile_Serializable() {
        filterList = new ArrayList<>();
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
        final MediaFile_Serializable other = (MediaFile_Serializable) obj;
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.pathStorage, other.pathStorage)) {
            return false;
        }
        if (!Objects.equals(this.videoSupported, other.videoSupported)) {
            return false;
        }
        return true;
    }

    public static MediaFile_Serializable convertToSerializable(MediaFile m) {
        MediaFile_Serializable mserial = new MediaFile_Serializable();
        mserial.setName(m.getName());
        mserial.setPathStorage(m.getPathStorage().toString());
        mserial.setBookmarked(m.isBookmarked());
        mserial.setLoading(true);
        mserial.setLoadingError(false);
        mserial.setSubViewSelected(false);
        mserial.setDeleted(m.isDeleted());
        mserial.setSelected(m.isSelected());
        mserial.setStackName(m.getStackName());
        mserial.setStackPos(m.getStackPos());
        mserial.setStacked(m.isStacked());
        mserial.setPlace(m.getPlace().get());
        mserial.setFaces(m.getFaces().get());
        mserial.setFilterList(mserial.convertImageFilterToStringList(m.getFilterList()));
        mserial.setMediaType(convertFromMediaType(m.getMediaType()));
        mserial.setVideoSupported(convertFromVideoSupportedType(m.getVideoSupported()));
        mserial.setTitle(m.getTitle().get());
        mserial.setKeywords(m.getKeywords());
        mserial.setCamera(m.getCamera().get());
        mserial.setComments(m.getCamera().get());
        mserial.setRotationAngle(m.getRotationAngleProperty().get());
        mserial.setRating(m.getRatingProperty().get());
        mserial.setGpsHeight(-1);
        if (m.getOrignalImageSize() != null) {
            mserial.setOrignalImageSizeX(m.getOrignalImageSize().getX());
            mserial.setOrignalImageSizeY(m.getOrignalImageSize().getY());
        }
        if (m.getCropView() != null) {
            mserial.setCropViewX(m.getCropView().getMinX());
            mserial.setCropViewY(m.getCropView().getMinY());
            mserial.setCropViewWidth(m.getCropView().getWidth());
            mserial.setCropViewHeigth(m.getCropView().getHeight());
        }
        try {
            mserial.setCreationTime(formatDateTime(m.getCreationTime()));
            mserial.setLastModifyTime(formatDateTime(m.getLastModifyTime()));
        } catch (IOException ex) {
        }
        return mserial;
    }

    public static void convertToMediaFile(MediaFile_Serializable mserial, MediaFile m) {
        m.setName(mserial.getName());
        m.setPathStorage(Path.of(mserial.getPathStorage()));
        m.setBookmarked(mserial.isBookmarked());
        m.setLoading(true);
        m.setLoadingError(false);
        m.setSubViewSelected(false);
        m.setDeleted(mserial.isDeleted());
        m.setSelected(mserial.isSelected());
        m.setStackName(mserial.getStackName());
        m.setStackPos(mserial.getStackPos());
        m.setStacked(mserial.isStacked());
        m.setPlace(mserial.getPlace());
        m.setFaces(mserial.getFaces());
        m.setFilterList(mserial.convertToImageFilterList(mserial.getFilterList()));
        m.setMediaType(convertToMediaType(mserial.getMediaType()));
        m.setVideoSupported(convertToVideoSupportedType(mserial.getVideoSupported()));
        m.setTitle(mserial.getTitle());
        m.setKeywords(mserial.getKeywords());
        m.setCamera(mserial.getCamera());
        m.setComments(mserial.getCamera());
        m.setRotationAngle(mserial.getRotationAngle());
        m.setRating(mserial.getRating());
        m.setGpsHeight(-1);
        m.setOrignalImageSize(new Point2D(mserial.getOrignalImageSizeX(), mserial.getOrignalImageSizeY()));
        if (mserial.getCropViewWidth() > 0) {
            m.setCropView(new Rectangle2D(mserial.getCropViewX(), mserial.getCropViewY(), mserial.getCropViewWidth(), mserial.getCropViewHeigth()));
        } else {
            m.setCropView(null);
        }
        m.setCreationTime(formatDateTime(mserial.getCreationTime()));
        m.setLastModifyTime(formatDateTime(mserial.getLastModifyTime()));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPathStorage() {
        return pathStorage;
    }

    public void setPathStorage(String pathStorage) {
        this.pathStorage = pathStorage;
    }

    public boolean isLoading() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public boolean isLoadingError() {
        return loadingError;
    }

    public void setLoadingError(boolean loadingError) {
        this.loadingError = loadingError;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getCamera() {
        return camera;
    }

    public void setCamera(String camera) {
        this.camera = camera;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public double getRotationAngle() {
        return rotationAngle;
    }

    public void setRotationAngle(double rotationAngle) {
        this.rotationAngle = rotationAngle;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public String getFaces() {
        return faces;
    }

    public void setFaces(String faces) {
        this.faces = faces;
    }

    public double getCropViewX() {
        return cropViewX;
    }

    public void setCropViewX(double cropViewX) {
        this.cropViewX = cropViewX;
    }

    public double getCropViewY() {
        return cropViewY;
    }

    public void setCropViewY(double cropViewY) {
        this.cropViewY = cropViewY;
    }

    public double getCropViewWidth() {
        return cropViewWidth;
    }

    public void setCropViewWidth(double cropViewWidth) {
        this.cropViewWidth = cropViewWidth;
    }

    public double getCropViewHeigth() {
        return cropViewHeigth;
    }

    public void setCropViewHeigth(double cropViewHeigth) {
        this.cropViewHeigth = cropViewHeigth;
    }

    public double getOrignalImageSizeX() {
        return orignalImageSizeX;
    }

    public void setOrignalImageSizeX(double orignalImageSizeX) {
        this.orignalImageSizeX = orignalImageSizeX;
    }

    public double getOrignalImageSizeY() {
        return orignalImageSizeY;
    }

    public void setOrignalImageSizeY(double orignalImageSizeY) {
        this.orignalImageSizeY = orignalImageSizeY;
    }

    public LocalDateTime getRecordTime() {
        return recordTime;
    }

    public void setRecordTime(LocalDateTime recordTime) {
        this.recordTime = recordTime;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getStackName() {
        return stackName;
    }

    public void setStackName(String stackName) {
        this.stackName = stackName;
    }

    public int getStackPos() {
        return stackPos;
    }

    public void setStackPos(int stackPos) {
        this.stackPos = stackPos;
    }

    public boolean isStacked() {
        return stacked;
    }

    public void setStacked(boolean stacked) {
        this.stacked = stacked;
    }

    public String getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(String creationTime) {
        this.creationTime = creationTime;
    }

    public String getLastModifyTime() {
        return lastModifyTime;
    }

    public void setLastModifyTime(String lastModifyTime) {
        this.lastModifyTime = lastModifyTime;
    }

    public boolean isSubViewSelected() {
        return subViewSelected;
    }

    public void setSubViewSelected(boolean subViewSelected) {
        this.subViewSelected = subViewSelected;
    }

    public List<String> getFilterList() {
        return filterList;
    }

    public void setFilterList(List<String> filterList) {
        this.filterList = filterList;
    }

    public Point getGpsPosition() {
        return gpsPosition;
    }

    public void setGpsPosition(Point gpsPosition) {
        this.gpsPosition = gpsPosition;
    }

    public LocalDateTime getGpsDateTime() {
        return gpsDateTime;
    }

    public void setGpsDateTime(LocalDateTime gpsDateTime) {
        this.gpsDateTime = gpsDateTime;
    }

    public double getGpsHeight() {
        return gpsHeight;
    }

    public void setGpsHeight(double gpsHeight) {
        this.gpsHeight = gpsHeight;
    }

    public boolean isBookmarked() {
        return bookmarked;
    }

    public void setBookmarked(boolean bookmarked) {
        this.bookmarked = bookmarked;
    }

    public String getVideoSupported() {
        return videoSupported;
    }

    public void setVideoSupported(String videoSupported) {
        this.videoSupported = videoSupported;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public ObservableList<ImageFilter> convertToImageFilterList(List<String> sourceList) {
        ObservableList<ImageFilter> destinationList = FXCollections.observableArrayList();
        ObjectMapper mapper = new ObjectMapper();
        for (String filter : sourceList) {
            StringTokenizer st = new StringTokenizer(filter, ";");
            String fname = (String) st.nextElement();
            String value = (String) st.nextElement();
            ImageFilter ifm = null;
            try {
                String cname = fname.substring(12);
                ifm = (ImageFilter) mapper.readValue(value, Class.forName("org.photoslide.imageops." + cname));
            } catch (JsonProcessingException | ClassNotFoundException ex) {
                Logger.getLogger(MediaFile_Serializable.class.getName()).log(Level.SEVERE, "Cannot find class name in config file", ex);
            }
            if (ifm != null) {
                if (destinationList.contains(ifm) == false) {
                    destinationList.add(ifm);
                }
            }
        }
        return destinationList;
    }

    public static MediaFile.MediaTypes convertToMediaType(String source) {
        switch (source) {
            case "IMAGE" -> {
                return MediaFile.MediaTypes.IMAGE;
            }
            case "VIDEO" -> {
                return MediaFile.MediaTypes.VIDEO;
            }
        }
        return MediaFile.MediaTypes.NONE;
    }

    public static String convertFromMediaType(MediaFile.MediaTypes source) {
        switch (source) {
            case MediaFile.MediaTypes.IMAGE -> {
                return "IMAGE";
            }
            case MediaFile.MediaTypes.VIDEO -> {
                return "VIDEO";
            }
        }
        return "NONE";
    }

    public static MediaFile.VideoTypes convertToVideoSupportedType(String source) {
        if (source == null) {
            return MediaFile.VideoTypes.UNSUPPORTED;
        }
        switch (source) {
            case "SUPPORTED" -> {
                return MediaFile.VideoTypes.SUPPORTED;
            }
            case "UNSUPPORTED" -> {
                return MediaFile.VideoTypes.UNSUPPORTED;
            }
        }
        return MediaFile.VideoTypes.UNSUPPORTED;
    }

    public static String convertFromVideoSupportedType(MediaFile.VideoTypes source) {
        if (source == null) {
            return null;
        }
        switch (source) {
            case MediaFile.VideoTypes.SUPPORTED -> {
                return "SUPPORTED";
            }
            case MediaFile.VideoTypes.UNSUPPORTED -> {
                return "UNSUPPORTED";
            }
        }
        return "UNSUPPORTED";
    }

    public List<String> convertImageFilterToStringList(List<ImageFilter> sourceList) {
        ArrayList<String> destinationList = new ArrayList<>();
        if (sourceList.isEmpty() == false) {
            ObjectMapper mapper = new ObjectMapper();
            for (ImageFilter imgFilter : sourceList) {
                try {
                    String value = mapper.writeValueAsString(imgFilter);
                    destinationList.add("ImageFilter:" + imgFilter.getName() + ";" + value);
                } catch (JsonProcessingException ex) {
                    Logger.getLogger(MediaFile.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return destinationList;
    }

    private static String formatDateTime(FileTime fileTime) {

        LocalDateTime localDateTime = fileTime
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        return localDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    private static FileTime formatDateTime(String fileTime) {
        LocalDateTime parse = LocalDateTime.parse(fileTime, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        return FileTime.from(parse.atZone(ZoneId.systemDefault()).toInstant());
    }

}
