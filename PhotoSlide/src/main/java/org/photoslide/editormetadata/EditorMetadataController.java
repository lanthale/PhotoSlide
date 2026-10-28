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
import javafx.beans.InvalidationListener;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.browsermetadata.MetadataController;
import org.photoslide.editortools.HistoryView;
import org.photoslide.editortools.StylesView;
import static org.photoslide.datamodel.MediaFile.MediaTypes.IMAGE;
import static org.photoslide.datamodel.MediaFile.MediaTypes.VIDEO;

/**
 *
 * @author selfemp
 */
public class EditorMetadataController implements Initializable {

    private ExecutorService executor;
    @FXML
    private VBox stylesHistoryBox;
    private final StylesView stylesView = new StylesView();
    private final HistoryView historyView = new HistoryView();
    /** Shows the edits in the small preview once they are saved. */
    private final InvalidationListener editsListener = o -> showPreview();
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
    private Task<Boolean> task;
    private Task<Boolean> taskMetaData;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNamePrefix("editorMetadataController").build());
        imageVIew.fitWidthProperty().bind(hboxImage.widthProperty());
        imageVIew.fitHeightProperty().bind(hboxImage.heightProperty());
        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        stylesHistoryBox.getChildren().addAll(stylesView.node(), historyView.node());
    }

    public void injectMetaDataController(MetadataController c) {
        this.metadatacontroller = c;
    }

    public void cancleTask() {
        if (task != null) {
            task.cancel();
            if (taskMetaData != null) {
                taskMetaData.cancel();
            }
            resetUI();
        }
    }

    public void setMediaFileForEdit(MediaFile f) {
        if (f == null) {
            return;
        }
        imageVIew.setImage(null);
        progressMetaDataIndicator.setVisible(true);
        if (selectedMediaFile != null) {
            selectedMediaFile.editVersionProperty().removeListener(editsListener);
        }
        selectedMediaFile = f;
        f.editVersionProperty().addListener(editsListener);
        boolean image = f.getMediaType() == IMAGE && !f.getLoadingError();
        stylesView.setFile(image ? f : null);
        historyView.setFile(image ? f : null);
        gridPaneMetaInfo.getChildren().clear();
        switch (selectedMediaFile.getMediaType()) {
            case VIDEO:
                break;
            case IMAGE:
                imageVIew.setImage(null);
                gridPaneMetaInfo.getChildren().clear();
                progressIndicator.setVisible(true);
                showPreview();
                progressIndicator.setVisible(false);
                break;
        }
        taskMetaData = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                if (selectedMediaFile.getLoadingError() == false) {
                    metadatacontroller.setActualMediaFile(selectedMediaFile);
                    metadatacontroller.readBasicMetadata(this, selectedMediaFile);
                }
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
        //executor.submit(task);
    }

    private void updateUIWithExtendedMetadata() {
        AtomicInteger i = new AtomicInteger(1);
        Iterator<MetadataEntry> iterator;

        if (metadatacontroller.getRawMetaData() != null) {
            metadatacontroller.getRawMetaData().entrySet().forEach((e) -> {
                Label key = new Label(e.getKey());
                Label value = new Label(e.getValue());
                key.setStyle("-fx-font-size:8pt;");
                value.setStyle("-fx-font-size:8pt;");
                gridPaneMetaInfo.getRowConstraints().add(new RowConstraints());
                gridPaneMetaInfo.addRow(i.get(), key, value);
                i.addAndGet(1);
            });
        }
        //read jpge exif
        if (metadatacontroller.getExifdata() != null) {
            iterator = metadatacontroller.getExifdata().iterator();
            while (iterator.hasNext()) {
                if (taskMetaData.isCancelled()) {
                    return;
                }
                MetadataEntry item = iterator.next();
                Collection<MetadataEntry> entries = item.getMetadataEntries();
                entries.forEach(e -> {
                    Label key = new Label(e.getKey());
                    Label value = new Label(e.getValue());
                    key.setStyle("-fx-font-size:8pt;");
                    value.setStyle("-fx-font-size:8pt;");
                    gridPaneMetaInfo.getRowConstraints().add(new RowConstraints());
                    gridPaneMetaInfo.addRow(i.get(), key, value);
                    i.addAndGet(1);
                });
            }
        }

        //read iptcdata exif
        if (metadatacontroller.getIptcdata() != null) {
            iterator = metadatacontroller.getIptcdata().iterator();
            while (iterator.hasNext()) {
                if (taskMetaData.isCancelled()) {
                    return;
                }
                MetadataEntry item = iterator.next();
                Collection<MetadataEntry> entries = item.getMetadataEntries();
                entries.forEach(e -> {
                    Label key = new Label(e.getKey());
                    Label value = new Label(e.getValue());
                    key.setStyle("-fx-font-size:8pt;");
                    value.setStyle("-fx-font-size:8pt;");
                    gridPaneMetaInfo.getRowConstraints().add(new RowConstraints());
                    gridPaneMetaInfo.addRow(i.get(), key, value);
                    i.addAndGet(1);
                });
            }
        }

        //read xmpdata
        if (metadatacontroller.getXmpdata() != null) {
            iterator = metadatacontroller.getXmpdata().iterator();
            while (iterator.hasNext()) {
                if (taskMetaData.isCancelled()) {
                    return;
                }
                MetadataEntry item = iterator.next();
                Collection<MetadataEntry> entries = item.getMetadataEntries();
                entries.forEach(e -> {
                    Label key = new Label(e.getKey());
                    Label value = new Label(e.getValue());
                    key.setStyle("-fx-font-size:8pt;");
                    value.setStyle("-fx-font-size:8pt;");
                    gridPaneMetaInfo.getRowConstraints().add(new RowConstraints());
                    gridPaneMetaInfo.addRow(i.get(), key, value);
                    i.addAndGet(1);
                });
            }
        }
    }

    /** The thumbnail with the tools, crop and quarter turn of the file. JavaFX thread. */
    private void showPreview() {
        MediaFile f = selectedMediaFile;
        if (f == null || f.getMediaType() != IMAGE || f.getLoadingError() || f.getImage() == null) {
            imageVIew.setImage(null);
            return;
        }
        if (f.getUnModifiyAbleImage() == null) {
            f.setUnModifiyAbleImage(f.getClonedImage(f.getImage()));
        }
        Image img = f.setFilters();
        Rectangle2D crop = f.getCropView();
        Point2D size = f.getOrignalImageSize();
        if (crop != null && size != null && size.getX() > 0) {
            double s = img.getWidth() / size.getX();
            imageVIew.setViewport(new Rectangle2D(crop.getMinX() * s, crop.getMinY() * s,
                    crop.getWidth() * s, crop.getHeight() * s));
        } else {
            imageVIew.setViewport(null);
        }
        double rotation = ((f.getRotationAngleProperty().get() % 360) + 360) % 360;
        boolean quarter = Math.abs(rotation - 90) < 1 || Math.abs(rotation - 270) < 1;
        imageVIew.fitWidthProperty().unbind();
        imageVIew.fitHeightProperty().unbind();
        imageVIew.fitWidthProperty().bind(quarter ? hboxImage.heightProperty() : hboxImage.widthProperty());
        imageVIew.fitHeightProperty().bind(quarter ? hboxImage.widthProperty() : hboxImage.heightProperty());
        imageVIew.setRotate(rotation);
        imageVIew.setImage(img);
    }

    public void resetUI() {
        progressMetaDataIndicator.setVisible(true);
        progressMetaDataIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        gridPaneMetaInfo.setOpacity(0);
        this.imageVIew.setImage(null);
        if (selectedMediaFile != null) {
            selectedMediaFile.editVersionProperty().removeListener(editsListener);
        }
        selectedMediaFile = null;
        gridPaneMetaInfo.getChildren().clear();
        while (gridPaneMetaInfo.getRowConstraints().size() > 0) {
            gridPaneMetaInfo.getRowConstraints().remove(0);
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

}
