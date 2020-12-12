/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.editormetadata;

import com.icafe4j.image.meta.MetadataEntry;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Accordion;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.metadata.MetadataController;

/**
 *
 * @author selfemp
 */
public class EditorMetadataController implements Initializable {

    private ExecutorService executor;
    @FXML
    private Accordion editorAccordion;
    private MediaFile selectedMediaFile;
    @FXML
    private ImageView imageVIew;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private StackPane stackPane;
    @FXML
    private GridPane gridPaneMetaInfo;
    private MetadataController metadatacontroller;
    @FXML
    private HBox hboxImage;
    @FXML
    private ProgressIndicator progressMetaDataIndicator;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newCachedThreadPool(new ThreadFactoryPS("editorMetadataController"));
        imageVIew.fitWidthProperty().bind(hboxImage.widthProperty());
        imageVIew.fitHeightProperty().bind(hboxImage.heightProperty());
    }

    public void injectMetaDataController(MetadataController c) {
        this.metadatacontroller = c;
    }

    public void setMediaFileForEdit(MediaFile f) {
        if (f == null) {
            return;
        }
        imageVIew.setImage(null);
        selectedMediaFile = f;
        gridPaneMetaInfo.getChildren().clear();
        Task<Boolean> task = new Task<>() {
            private ObservableList<ImageFilter> filterList;
            private Image imageWithFilters;
            private Image img;

            @Override
            protected Boolean call() throws Exception {
                switch (selectedMediaFile.getMediaType()) {
                    case VIDEO -> {

                    }
                    case IMAGE -> {
                        Platform.runLater(() -> {
                            progressIndicator.progressProperty().unbind();
                            imageVIew.setImage(null);
                            progressIndicator.setVisible(true);
                        });
                        String url = selectedMediaFile.getImageUrl().toString();
                        img = new Image(url, true);
                        Platform.runLater(() -> {
                            progressIndicator.progressProperty().bind(img.progressProperty());
                            img.progressProperty().addListener((ov, t, t1) -> {
                                if ((Double) t1 == 1.0 && !img.isError()) {
                                    progressIndicator.setVisible(false);
                                    imageWithFilters = img;
                                    filterList = selectedMediaFile.getFilterListWithoutImageData();
                                    for (ImageFilter imageFilter : filterList) {
                                        imageWithFilters = imageFilter.load(imageWithFilters);
                                        imageFilter.filter(imageFilter.getValues());
                                    }
                                    img = imageWithFilters;
                                    imageVIew.setImage(img);
                                } else {
                                    progressIndicator.setVisible(true);
                                }
                            });
                            imageVIew.setImage(img);
                        });
                    }
                }
                return true;
            }
        };
        Task<Boolean> taskMetaData = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                metadatacontroller.setSelectedFile(selectedMediaFile);
                metadatacontroller.readBasicMetadata(this);
                return true;
            }

        };
        taskMetaData.setOnFailed((t) -> {
            Logger.getLogger(EditorMetadataController.class.getName()).log(Level.SEVERE, "Cannot load metadata in editor ...", t.getSource().getException());
        });
        taskMetaData.setOnSucceeded((t) -> {            
            updateUIWithExtendedMetadata();
            progressMetaDataIndicator.setVisible(false);
            FadeTransition ft1 = new FadeTransition(Duration.millis(500), gridPaneMetaInfo);
            ft1.setAutoReverse(false);
            ft1.setFromValue(0.0);
            ft1.setToValue(1.0);
            ft1.play();
        });
        executor.submit(taskMetaData);
        executor.submit(task);
    }

    private void updateUIWithExtendedMetadata() {
        AtomicInteger i = new AtomicInteger(1);
        Iterator<MetadataEntry> iterator;

        //read jpge exif
        if (metadatacontroller.getJpegExifdata() != null) {
            iterator = metadatacontroller.getJpegExifdata().iterator();
            while (iterator.hasNext()) {
                MetadataEntry item = iterator.next();
                Collection<MetadataEntry> entries = item.getMetadataEntries();
                entries.forEach(e -> {
                    Label key = new Label(e.getKey());
                    Label value = new Label(e.getValue());
                    key.setStyle("-fx-font-size:8pt;");
                    value.setStyle("-fx-font-size:8pt;");
                    gridPaneMetaInfo.addRow(i.get(), key, value);
                    i.addAndGet(1);
                });
            }
        }

        //read iptcdata exif
        if (metadatacontroller.getIptcdata() != null) {
            iterator = metadatacontroller.getIptcdata().iterator();
            while (iterator.hasNext()) {
                MetadataEntry item = iterator.next();
                Collection<MetadataEntry> entries = item.getMetadataEntries();
                entries.forEach(e -> {
                    Label key = new Label(e.getKey());
                    Label value = new Label(e.getValue());
                    key.setStyle("-fx-font-size:8pt;");
                    value.setStyle("-fx-font-size:8pt;");
                    gridPaneMetaInfo.addRow(i.get(), key, value);
                    i.addAndGet(1);
                });
            }
        }

        //read xmpdata
        if (metadatacontroller.getXmpdata() != null) {
            iterator = metadatacontroller.getXmpdata().iterator();
            while (iterator.hasNext()) {
                MetadataEntry item = iterator.next();
                Collection<MetadataEntry> entries = item.getMetadataEntries();
                entries.forEach(e -> {
                    Label key = new Label(e.getKey());
                    Label value = new Label(e.getValue());
                    key.setStyle("-fx-font-size:8pt;");
                    value.setStyle("-fx-font-size:8pt;");
                    gridPaneMetaInfo.addRow(i.get(), key, value);
                    i.addAndGet(1);
                });
            }
        }
    }

    public void resetImageView() {
        progressMetaDataIndicator.setVisible(true);
        gridPaneMetaInfo.setOpacity(0);
        this.imageVIew.setImage(null);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

}
