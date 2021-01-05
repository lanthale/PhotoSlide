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
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
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
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.imageops.ImageFilter;

/**
 *
 * @author selfemp
 */
public class MediaFile extends StackPane {

    private Image image;
    private Image unModifiyAbleImage;
    private Media media;
    private MediaPlayer mediaPlayer;
    private final MediaView mediaview;
    private final ImageView imageView;
    private FontIcon dummyIcon;

    private String name;
    private Path pathStorage;
    private final SimpleStringProperty title;
    private final SimpleStringProperty keywords;
    private final SimpleStringProperty camera;
    private final SimpleStringProperty comments;
    private final SimpleDoubleProperty rotationAngle;
    private final SimpleIntegerProperty rating;
    private final SimpleStringProperty places;
    private final SimpleStringProperty faces;
    private Rectangle2D cropView;
    private LocalDateTime recordTime;
    private final SimpleBooleanProperty deleted;
    private final SimpleBooleanProperty selected;
    private final SimpleStringProperty stackName;
    private final SimpleIntegerProperty stackPos;
    private final SimpleBooleanProperty stacked;
    private FileTime creationTime;
    private final FontIcon layerIcon;
    private FontIcon restoreIcon;
    private boolean subViewSelected;
    private ObservableList<ImageFilter> filterList;
    private String gpsPosition;
    private LocalDateTime gpsDateTime;
    private double gpsHeight;

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

    public MediaFile() {
        subViewSelected = false;
        deleted = new SimpleBooleanProperty(false);
        selected = new SimpleBooleanProperty(false);
        stackName = new SimpleStringProperty();
        stackPos = new SimpleIntegerProperty(-1);
        stacked = new SimpleBooleanProperty(false);
        places = new SimpleStringProperty();
        faces = new SimpleStringProperty();
        filterList = FXCollections.observableArrayList();
        layerIcon = new FontIcon("ti-view-grid");
        restoreIcon = new FontIcon("ti-back-right:22");
        mediaType = MediaTypes.NONE;
        title = new SimpleStringProperty();
        keywords = new SimpleStringProperty();
        camera = new SimpleStringProperty();
        comments = new SimpleStringProperty();
        rotationAngle = new SimpleDoubleProperty(0.0);
        rating = new SimpleIntegerProperty(0);
        this.setHeight(50);
        this.setWidth(50);
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.fitHeightProperty().bind(heightProperty());
        imageView.fitWidthProperty().bind(widthProperty());
        imageView.rotateProperty().bind(rotationAngle);
        mediaview = new MediaView();
        mediaview.setPreserveRatio(true);
        mediaview.fitHeightProperty().bind(heightProperty());
        mediaview.fitWidthProperty().bind(widthProperty());
        mediaview.rotateProperty().bind(rotationAngle);
        gpsHeight = -1;
    }

    private void setLoadingNode() {
        ProgressIndicator prgInd = new ProgressIndicator();
        prgInd.setId("MediaLoadingProgressIndicator");
        prgInd.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        if (mediaType == MediaTypes.IMAGE) {
            prgInd.maxHeightProperty().bind(imageView.fitHeightProperty().multiply(0.6));
            prgInd.maxWidthProperty().bind(imageView.fitWidthProperty().multiply(0.6));
        } else {
            prgInd.maxHeightProperty().bind(mediaview.fitHeightProperty().multiply(0.6));
            prgInd.maxWidthProperty().bind(mediaview.fitWidthProperty().multiply(0.6));
        }
        this.getChildren().clear();
        this.getChildren().add(prgInd);
    }

    private void setDeletedNode() {
        VBox v = new VBox();
        v.setStyle("-fx-background-color: rgba(80, 80, 80, .7);");
        this.getChildren().add(v);
        this.getChildren().add(restoreIcon);
    }

    private void setRatingNode() {
        if (rating.getValue() > 0) {
            VBox vb = new VBox();
            vb.setAlignment(Pos.BOTTOM_RIGHT);
            HBox hb = new HBox();
            hb.setPadding(new Insets(40, 0, 0, 0));
            for (int i = 0; i < rating.getValue(); i++) {
                FontIcon starIcon = new FontIcon("fa-star");
                starIcon.setId("star-ikonli-font-icon");
                hb.getChildren().add(starIcon);
            }
            vb.getChildren().add(hb);
            this.getChildren().add(vb);
        }
    }

    private void setStacked() {
        VBox vb = new VBox();
        if (stacked.get() == true && stackPos.get() == 1) {
            vb.setAlignment(Pos.BOTTOM_RIGHT);
            vb.setPadding(new Insets(0, -3, -5, 0));
            vb.getChildren().add(layerIcon);
            this.getChildren().add(vb);
        } else {
            this.getChildren().remove(vb);
        }
    }

    private Image setFilters() {
        Image imageWithFilters = getClonedImage(unModifiyAbleImage);
        for (ImageFilter imageFilter : filterList) {
            imageWithFilters = imageFilter.load(imageWithFilters);
            imageFilter.filter(imageFilter.getValues());
        }
        return imageWithFilters;
    }

    public Image getImage() {
        return image;
    }

    public URL getImageUrl() throws MalformedURLException {
        return this.getPathStorage().toUri().toURL();
    }

    public final void setImage(Image image) {
        if (image == null) {
            setLoadingNode();
        } else {
            this.image = image;
            if (unModifiyAbleImage == null) {
                this.unModifiyAbleImage = getClonedImage(image);
            }
            this.image = setFilters();
            imageView.setImage(this.image);
            //calc cropview based on small imageview
            //imageView.setViewport(cropView);
            this.getChildren().clear();
            this.getChildren().add(imageView);
            setRatingNode();
            setStacked();
            if (deleted.getValue() == true) {
                setDeletedNode();
            }
        }
    }

    public Media getMedia() {
        return media;
    }

    public final void setMedia(Media media, VideoTypes type) {
        if (media == null && videoSupported == null) {
            setLoadingNode();
        } else {
            this.media = media;
            this.videoSupported = type;
            setRatingNode();
            if (type == VideoTypes.SUPPORTED) {
                dummyIcon = new FontIcon("fa-file-movie-o:40");
                this.getChildren().clear();
                this.getChildren().add(dummyIcon);
            } else {
                FontIcon filmIcon = new FontIcon("fa-file-movie-o:40");
                filmIcon.setOpacity(0.3);
                dummyIcon = new FontIcon("fa-minus-circle:40");
                this.getChildren().clear();
                this.getChildren().add(filmIcon);
                this.getChildren().add(dummyIcon);
            }
        }
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
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
            }
            if (gpsDateTime != null) {
                prop.setProperty("gpsDateTime", gpsDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            if (gpsHeight != -1) {
                prop.setProperty("gpsHeight", "" + gpsHeight);
            }
            if (gpsPosition != null) {
                prop.setProperty("gpsPosition", gpsPosition);
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
                        rawList.add(ifm);
                    }
                }
            }
            if (rawList.isEmpty() == false) {
                rawList.sort(Comparator.comparing(imageFilter -> imageFilter.getPosition()));
                filterList.addAll(rawList);
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
            if (gpsHeight != -1) {
                gpsHeight = Double.parseDouble(prop.getProperty("gpsHeight", "-1"));
            }
            if (gpsPosition != null) {
                gpsPosition = prop.getProperty("gpsPosition", null);
            }
        } catch (IOException ex) {
            //Do nothing if file not found
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + Objects.hashCode(this.name);
        hash = 79 * hash + Objects.hashCode(this.pathStorage);
        hash = 79 * hash + Objects.hashCode(this.videoSupported);
        hash = 79 * hash + Objects.hashCode(this.mediaType);
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
        if (this.mediaType != other.mediaType) {
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
        return pathStorage;
    }

    public void setPathStorage(Path pathStorage) {
        this.pathStorage = pathStorage;
    }

    public VideoTypes getVideoSupported() {
        return videoSupported;
    }

    public void setVideoSupported(VideoTypes videoSupported) {
        this.videoSupported = videoSupported;
    }

    public MediaView getMediaview() {
        return mediaview;
    }

    public ImageView getImageView() {
        return imageView;
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

    public SimpleStringProperty getTitleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public SimpleStringProperty getCameraProperty() {
        return camera;
    }

    public void setCamera(String camera) {
        this.camera.set(camera);
    }

    public SimpleBooleanProperty getDeletedProperty() {
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

    public SimpleBooleanProperty getSelectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public FileTime getCreationTime() {
        if (creationTime == null) {
            try {
                BasicFileAttributes attr = Files.readAttributes(pathStorage, BasicFileAttributes.class);
                creationTime = attr.creationTime();
            } catch (IOException ex) {
                Logger.getLogger(MediaFile.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return creationTime;
    }

    public void setCreationTime(FileTime creationTime) {
        this.creationTime = creationTime;
    }

    public Path getEditFilePath() {
        String fileNameWithOutExt = pathStorage.toString().substring(0, pathStorage.toString().lastIndexOf("."));
        String fileNameWithExt = fileNameWithOutExt + ".edit";
        Path source = new File(fileNameWithExt).toPath();
        return source.resolveSibling("Ω_" + source.toFile().getName());
    }

    public void setSize(double height, double width) {
        this.setHeight(height);
        this.setWidth(width);
        this.requestLayout();
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
        ifm.setPosition(filterList.indexOf(ifm));
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

    private Image getClonedImage(Image img) {
        if (img == null) {
            return null;
        }
        image = img;
        PixelReader pixelReader = image.getPixelReader();
        int height = (int) image.getHeight();
        int width = (int) image.getWidth();
        byte[] buffer = new byte[width * height * 4];
        pixelReader.getPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), buffer, 0, width * 4);
        Image filteredImage = new WritableImage(pixelReader, width, height);
        return filteredImage;
    }

    public Image getUnModifiyAbleImage() {
        return unModifiyAbleImage;
    }

    public void setUnModifiyAbleImage(Image unModifiyAbleImage) {
        this.unModifiyAbleImage = unModifiyAbleImage;
    }

    public SimpleStringProperty getPlaces() {
        return places;
    }

    public SimpleStringProperty getFaces() {
        return faces;
    }

    public SimpleStringProperty getComments() {
        return comments;
    }

    public String getGpsPosition() {
        return gpsPosition;
    }

    public void setGpsPosition(String gpsPosition) {
        this.gpsPosition = gpsPosition;
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd'T'HH:m:ss','SS");
        try {
            parse = LocalDateTime.parse(dateTimeStr, formatter);
        } catch (DateTimeParseException e) {
            Logger.getLogger(MediaFile.class.getName()).log(Level.SEVERE, "Cannot convert GPS date/time!", e);
        }
        this.gpsDateTime = parse;
    }

    public double getGpsHeight() {
        return gpsHeight;
    }

    public void setGpsHeight(double gpsHeight) {
        this.gpsHeight = gpsHeight;
    }

}
