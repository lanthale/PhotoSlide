/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.datamodel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.lighttable.MediaGridCellFactory;

/**
 *
 * @author selfemp
 */
public class MediaFile extends StackPane {

    private boolean mediaEdited;
    private Image image;
    private Media media;
    private MediaPlayer mediaPlayer;
    private final MediaView mediaview;
    private final ImageView imageView;
    private FontIcon dummyIcon;    

    private String name;
    private Path pathStorage;
    private final SimpleStringProperty title;
    private final SimpleStringProperty camera;
    private final SimpleDoubleProperty rotationAngle;
    private final SimpleIntegerProperty rating;
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
        mediaEdited = false;
        deleted = new SimpleBooleanProperty(false);
        selected = new SimpleBooleanProperty(false);
        stackName = new SimpleStringProperty();
        stackPos = new SimpleIntegerProperty(-1);
        stacked = new SimpleBooleanProperty(false);
        layerIcon = new FontIcon("ti-view-grid");
        restoreIcon = new FontIcon("ti-back-right:22");
        deleted.addListener((ov, t, t1) -> {
            mediaEdited = true;
        });
        mediaType = MediaTypes.NONE;
        title = new SimpleStringProperty();
        camera = new SimpleStringProperty();
        rotationAngle = new SimpleDoubleProperty(0.0);
        rotationAngle.addListener((ov, t, t1) -> {
            mediaEdited = true;
        });
        rating = new SimpleIntegerProperty(0);
        rating.addListener((ov, t, t1) -> {
            mediaEdited = true;
        });
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

    public Image getImage() {
        return image;
    }

    public final void setImage(Image image) {
        if (image == null) {
            setLoadingNode();
        } else {
            this.image = image;
            imageView.setImage(image);
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

        try ( OutputStream output = new FileOutputStream(fileNameWithExt)) {
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

        try ( InputStream input = new FileInputStream(fileNameWithExt)) {

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
        mediaEdited = true;
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

    public boolean isMediaEdited() {
        return mediaEdited;
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

}
