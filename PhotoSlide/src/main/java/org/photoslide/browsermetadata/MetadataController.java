/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browsermetadata;

import com.icafe4j.image.ImageIO;
import com.icafe4j.image.ImageParam;
import com.icafe4j.image.ImageType;
import org.photoslide.MainViewController;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.browserlighttable.LighttableController;
import com.icafe4j.image.meta.Metadata;
import com.icafe4j.image.meta.MetadataEntry;
import com.icafe4j.image.meta.MetadataType;
import com.icafe4j.image.meta.exif.Exif;
import com.icafe4j.image.meta.exif.ExifTag;
import com.icafe4j.image.meta.image.Comments;
import com.icafe4j.image.meta.iptc.IPTC;
import com.icafe4j.image.meta.iptc.IPTCApplicationTag;
import com.icafe4j.image.meta.iptc.IPTCDataSet;
import com.icafe4j.image.meta.iptc.IPTCTag;
import com.icafe4j.image.meta.jpeg.JpegExif;
import com.icafe4j.image.meta.tiff.TiffExif;
import com.icafe4j.image.meta.xmp.XMP;
import com.icafe4j.image.options.PNGOptions;
import com.icafe4j.image.png.Filter;
import com.icafe4j.image.tiff.FieldType;
import com.icafe4j.image.writer.ImageWriter;
import com.sothawo.mapjfx.Coordinate;
import com.sothawo.mapjfx.MapCircle;
import com.sothawo.mapjfx.MapType;
import com.sothawo.mapjfx.MapView;
import com.sothawo.mapjfx.Marker;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.SepiaTone;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TouchEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.textfield.TextFields;
import org.kordamp.ikonli.javafx.FontIcon;
import org.librawfx.LibrawImage;
import org.photoslide.Utility;
import org.photoslide.datamodel.MediaFile.MediaTypes;
import org.photoslide.imageops.ExposureFilter;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.imageops.SampleFilter;
import org.photoslide.imageops.SampleFilter2;

/**
 *
 * @author selfemp
 */
public class MetadataController implements Initializable {

    private ExecutorService executor;
    private MainViewController mainController;
    private LighttableController lightController;
    private Collection<String> keywordList;
    private MediaFile actualMediaFile;

    private Exif exifdata;
    private IPTC iptcdata;
    private XMP xmpdata;
    private List<String> commentsdata;
    private HashMap<String, String> rawMetaData;

    @FXML
    private Accordion accordionPane;
    @FXML
    private TitledPane keywordsPane;
    @FXML
    private TitledPane metadataPane;
    @FXML
    private TitledPane quickDevPane;
    @FXML
    private FlowPane keywordText;
    @FXML
    private TextField addKeywordTextField;
    @FXML
    private TextArea commentText;
    @FXML
    private TextField captionTextField;
    @FXML
    private TextField recordDateField;
    @FXML
    private AnchorPane anchorKeywordPane;

    private Task<Boolean> task;
    private KeywordChangeListener keywordsChangeListener;
    private CommentsChangeListener commentsChangeListener;
    private CaptionChangeListener captionChangeListener;
    @FXML
    private StackPane stackPane;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private Label progressLabel;
    @FXML
    private VBox progressPane;
    @FXML
    private GridPane metaDataGrid;
    @FXML
    private Slider apertureSlider;
    private ExecutorService executorParallel;
    private Image shownImage;
    private SampleFilter greyFilter;
    private SampleFilter2 greyFilter2;
    private ImageFilter exposerFilter;
    @FXML
    private TextField gpsPlace;
    @FXML
    private TextField gpsHeight;
    @FXML
    private TextField gpsDateTime;
    private MapView map;
    private Marker markerPos;
    private MapCircle circle;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("metaDataController"));
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryPS("metaDataControllerParallel"));
        keywordList = FXCollections.observableArrayList();
        Platform.runLater(() -> {
            anchorKeywordPane.setDisable(true);
            accordionPane.setExpandedPane(keywordsPane);
            progressPane.setVisible(false);
            map = new MapView();
            map.setMapType(MapType.OSM);
            map.setPrefSize(400, 266);
            map.setMaxSize(400, 266);
            map.initialize();
            markerPos = new Marker(getClass().getResource("/org/photoslide/img/map_marker.png"), -16, -32);
        });
        keywordsChangeListener = new KeywordChangeListener();
        commentsChangeListener = new CommentsChangeListener();
        captionChangeListener = new CaptionChangeListener();
        apertureSlider.valueProperty().addListener((o) -> {
            if (exposerFilter == null) {
                exposerFilter = new ExposureFilter();
                actualMediaFile.addImageFilter(exposerFilter);
                lightController.getImageView().setImage(exposerFilter.load(lightController.getImageView().getImage()));
            }
            double val = apertureSlider.getValue();
            executorParallel.submit(() -> {
                exposerFilter.filter(new float[]{(float) val});
                ImageFilter filterForName = actualMediaFile.getFilterForName(exposerFilter.getName());
                if (filterForName != null) {
                    filterForName.setValues(exposerFilter.getValues());
                }
                actualMediaFile.saveEdits();
            });
            //actualMediaFile.requestLayout();
        });
    }

    public void injectMainController(MainViewController mainController) {
        this.mainController = mainController;
    }

    public void injectLightController(LighttableController lightController) {
        this.lightController = lightController;
    }

    public void setSelectedFile(MediaFile file) {
        actualMediaFile = file;
        resetGUI();
        Platform.runLater(() -> {
            progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            progressPane.setVisible(true);
            progressLabel.setText("Loading metadata...");
        });
        task = new Task<>() {
            @Override
            protected Boolean call() throws IOException {
                try {
                    readBasicMetadata(this, file);
                } catch (IOException | IllegalArgumentException e) {
                    Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, "Cannot read meta data from file '"+file.getName()+"'!", e);
                }
                return null;
            }
        };
        task.setOnSucceeded((WorkerStateEvent t) -> {
            DateTimeFormatter formatter;
            if (actualMediaFile.getRecordTime() != null) {
                recordDateField.setText(actualMediaFile.getRecordTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
            }
            updateUIWithExtendedMetadata();
            if (actualMediaFile.getKeywords() != null) {
                StringTokenizer defaultTokenizer = new StringTokenizer(actualMediaFile.getKeywords(), ";");
                while (defaultTokenizer.hasMoreTokens()) {
                    String nextToken = defaultTokenizer.nextToken();
                    keywordText.getChildren().add(new Tag(nextToken));
                    keywordList.add(nextToken);
                }
                TextFields.bindAutoCompletion(addKeywordTextField, keywordList);
            }
            captionTextField.setText(actualMediaFile.getTitleProperty().get());
            if (actualMediaFile.getComments().get() != null) {
                commentText.appendText(actualMediaFile.getComments().get());
            }
            anchorKeywordPane.setDisable(false);
            progressPane.setVisible(false);
            commentText.textProperty().addListener(commentsChangeListener);

            keywordText.getChildren().addListener(keywordsChangeListener);
            captionTextField.textProperty().addListener(captionChangeListener);

            gpsPlace.setText(actualMediaFile.getGpsPosition());
            if (actualMediaFile.getGpsHeight() != -1) {
                gpsHeight.setText("" + actualMediaFile.getGpsHeight() + " m");
            }
            if (actualMediaFile.getGpsDateTime() != null) {
                gpsDateTime.setText(actualMediaFile.getGpsDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
        });
        task.setOnFailed((t) -> {
            anchorKeywordPane.setDisable(false);
            progressPane.setVisible(false);
            keywordText.getChildren().removeListener(keywordsChangeListener);
            captionTextField.textProperty().removeListener(captionChangeListener);
            commentText.textProperty().removeListener(commentsChangeListener);
        });
        executor.submit(task);
    }

    public synchronized void readBasicMetadata(Task actTask, MediaFile file) throws IOException {
        if (file.isRawImage()) {
            rawMetaData = new LibrawImage(file.getPathStorage().toString()).getMetaData();
            String timeStr = rawMetaData.get("Timestamp (EpocheSec)");            
            LocalDateTime ofEpochSecond = LocalDateTime.ofEpochSecond(Long.parseLong(timeStr), 0, ZoneOffset.UTC);
            file.setRecordTime(ofEpochSecond);
        }
        Map<MetadataType, Metadata> metadataMap = Metadata.readMetadata(file.getPathStorage().toFile());
        for (Map.Entry<MetadataType, Metadata> entry : metadataMap.entrySet()) {
            if (actTask.isCancelled() == false) {
                Metadata meta = entry.getValue();
                Iterator<MetadataEntry> iterator;
                switch (meta.getType()) {
                    case XMP:
                        xmpdata = ((XMP) meta);
                    //XMP.showXMP(xmpdata);
                    case COMMENT:
                        if (meta instanceof Comments) {
                            commentsdata = ((Comments) meta).getComments();
                            StringBuilder sb = new StringBuilder();
                            commentsdata.forEach(comment -> {
                                sb.append(comment);
                            });
                            if (Platform.isFxApplicationThread() == true) {
                                file.getComments().set(sb.toString());
                            } else {
                                Platform.runLater(() -> {
                                    file.getComments().set(sb.toString());
                                });
                            }
                        }
                        break;
                    case IPTC:
                        iptcdata = ((IPTC) meta);
                        iterator = meta.iterator();
                        while (iterator.hasNext()) {
                            MetadataEntry item = iterator.next();
                            switch (item.getKey()) {
                                case "Keywords":
                                    if (Platform.isFxApplicationThread() == true) {
                                        file.setKeywords(item.getValue());
                                    } else {
                                        Platform.runLater(() -> {
                                            file.setKeywords(item.getValue());
                                        });
                                    }
                                    break;
                                case "Caption Abstract":
                                    if (Platform.isFxApplicationThread() == true) {
                                        file.setTitle(item.getValue());
                                    } else {
                                        Platform.runLater(() -> {
                                            file.setTitle(item.getValue());
                                        });
                                    }
                                    break;
                            }
                        }
                        break;
                    case JPG_JFIF:
                        break;
                    case EXIF:
                        if (meta instanceof JpegExif) {
                            exifdata = ((JpegExif) meta);
                        }
                        if (meta instanceof TiffExif) {
                            exifdata = ((TiffExif) meta);
                        }
                        iterator = meta.iterator();
                        while (iterator.hasNext()) {
                            MetadataEntry item = iterator.next();
                            Collection<MetadataEntry> entries = item.getMetadataEntries();
                            if (item.getKey().equalsIgnoreCase("EXIF SubIFD")) {
                                for (MetadataEntry e : entries) {
                                    if (e.getKey().equalsIgnoreCase("DateTime Digitized")) {
                                        if (!e.getValue().equalsIgnoreCase("")) {
                                            LocalDateTime date;
                                            DateTimeFormatter formatter;
                                            try {
                                                formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
                                                date = LocalDateTime.parse(e.getValue(), formatter);
                                            } catch (DateTimeParseException ex) {
                                                formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
                                                date = LocalDateTime.parse(e.getValue(), formatter);
                                            }
                                            file.setRecordTime(date);
                                        } else {
                                            file.setRecordTime(LocalDateTime.now());
                                        }
                                        break;
                                    }
                                }
                            }
                            if (item.getKey().equalsIgnoreCase("GPS SubIFD")) {
                                final StringBuilder gpsLatRefsb = new StringBuilder();
                                final StringBuilder gpsLatsb = new StringBuilder();
                                final StringBuilder gpsLongRefsb = new StringBuilder();
                                final StringBuilder gpsLongsb = new StringBuilder();
                                final StringBuilder gpsTimesb = new StringBuilder();
                                final StringBuilder gpsDatesb = new StringBuilder();
                                final StringBuilder gpsHeightsb = new StringBuilder();
                                entries.stream().forEach((mediaEntry) -> {
                                    switch (mediaEntry.getKey()) {
                                        case "GPS Latitude Ref":
                                            gpsLatRefsb.append(mediaEntry.getValue());
                                            break;
                                        case "GPS Latitude":
                                            gpsLatsb.append(mediaEntry.getValue());
                                            break;
                                        case "GPS Longitude Ref":
                                            gpsLongRefsb.append(mediaEntry.getValue());
                                            break;
                                        case "GPS Longitude":
                                            gpsLongsb.append(mediaEntry.getValue());
                                            break;
                                        case "GPS Altitude":
                                            gpsHeightsb.append(mediaEntry.getValue());
                                            break;
                                        case "GPS Time Stamp":
                                            gpsTimesb.append(mediaEntry.getValue());
                                            break;
                                        case "GPS Date Stamp":
                                            gpsDatesb.append(mediaEntry.getValue());
                                            break;
                                    }
                                });
                                if (gpsHeightsb.toString() != null) {
                                    if (gpsHeightsb.toString().length() > 0) {
                                        String gpsH = gpsHeightsb.toString().substring(0, gpsHeightsb.toString().length() - 1);
                                        double d = -1;
                                        try {
                                            DecimalFormat df = new DecimalFormat();
                                            DecimalFormatSymbols sfs = new DecimalFormatSymbols();
                                            sfs.setDecimalSeparator(',');
                                            sfs.setMonetaryDecimalSeparator('.');
                                            df.setDecimalFormatSymbols(sfs);
                                            d = df.parse(gpsH).doubleValue();
                                        } catch (ParseException ex) {
                                            try {
                                                DecimalFormat df = new DecimalFormat();
                                                DecimalFormatSymbols sfs = new DecimalFormatSymbols();
                                                sfs.setDecimalSeparator('.');
                                                sfs.setMonetaryDecimalSeparator(',');
                                                df.setDecimalFormatSymbols(sfs);
                                                d = df.parse(gpsH).doubleValue();
                                            } catch (ParseException ex2) {
                                                d = -1;
                                            }
                                        }
                                        file.setGpsHeight(d);
                                    }
                                }
                                if (gpsDatesb.toString() != null) {
                                    String gpsDateStr = gpsDatesb.toString().replace(":", ".");
                                    String formatTime = formatTime(gpsTimesb.toString());
                                    file.setGpsDateTime(gpsDateStr + "T" + formatTime);
                                }
                                file.setGpsPosition(gpsLatsb.toString() + gpsLatRefsb.toString() + ";" + gpsLongsb.toString() + gpsLongRefsb.toString());
                            }
                            if (item.getKey().equalsIgnoreCase("IFD0")) {
                                entries.stream().forEach((mediaEntry) -> {
                                    if (mediaEntry.getKey().equalsIgnoreCase("Model")) {
                                        if (Platform.isFxApplicationThread() == true) {
                                            file.setCamera(mediaEntry.getValue());
                                        } else {
                                            Platform.runLater(() -> {
                                                file.setCamera(mediaEntry.getValue());
                                            });
                                        }
                                    }
                                });
                            }
                        }
                        break;
                    case ICC_PROFILE:
                        break;
                    default:
                        break;
                }
                //XMP.showXMP((XMP) meta);
                //XMP.showXMP((XMP) meta);
                //XMP.showXMP((XMP) meta);
                //XMP.showXMP((XMP) meta);
                //System.out.println("type: " + meta.getType().toString());
            } else {
                break;
            }
        }
    }

    private String formatTime(String source) {
        if (source == null) {
            return "";
        }
        if (source.contains(":") == false) {
            return "";
        }
        String finalStr = "";
        String hour = source.substring(0, source.indexOf(":"));
        String minit = source.substring(source.indexOf(hour + ":") + hour.length() + 1, source.lastIndexOf(":"));
        String sec = "";
        if (source.contains(",")) {
            sec = source.substring(source.indexOf(":" + minit + ":") + minit.length() + 2, source.lastIndexOf(","));
            String milis = source.substring(source.lastIndexOf(",") + 1);
            if (hour.length() < 2) {
                hour = "0" + hour;
            }
            if (minit.length() < 2) {
                minit = "0" + minit;
            }
            if (sec.length() < 2) {
                sec = "0" + sec;
            }
            if (milis.length() < 2) {
                milis = "0" + milis;
            }
            finalStr = hour + ":" + minit + ":" + sec + "," + milis;
        } else {
            sec = source.substring(source.indexOf(":" + minit + ":") + minit.length() + 2);
            if (hour.length() < 2) {
                hour = "0" + hour;
            }
            if (minit.length() < 2) {
                minit = "0" + minit;
            }
            if (sec.length() < 2) {
                sec = "0" + sec;
            }
            finalStr = hour + ":" + minit + ":" + sec;
        }
        return finalStr;
    }

    private void updateUIWithExtendedMetadata() {
        AtomicInteger i = new AtomicInteger(1);
        Iterator<MetadataEntry> iterator;

        if (rawMetaData != null) {
            rawMetaData.entrySet().stream().sorted(Map.Entry.<String, String>comparingByKey()).forEach((entry) -> {
                Label key = new Label(entry.getKey());
                Label value = new Label(entry.getValue());
                key.setStyle("-fx-font-size:8pt;");
                value.setStyle("-fx-font-size:8pt;");
                metaDataGrid.addRow(i.get(), key, value);
                i.addAndGet(1);
            });
        }
        //read jpge exif
        if (exifdata != null) {
            iterator = exifdata.iterator();
            while (iterator.hasNext()) {
                MetadataEntry item = iterator.next();
                Collection<MetadataEntry> entries = item.getMetadataEntries();
                entries.forEach(e -> {
                    Label key = new Label(e.getKey());
                    Label value = new Label(e.getValue());
                    key.setStyle("-fx-font-size:8pt;");
                    value.setStyle("-fx-font-size:8pt;");
                    metaDataGrid.addRow(i.get(), key, value);
                    i.addAndGet(1);
                });
            }
        }

        //read iptcdata exif
        if (iptcdata != null) {
            iterator = iptcdata.iterator();
            while (iterator.hasNext()) {
                MetadataEntry item = iterator.next();
                Collection<MetadataEntry> entries = item.getMetadataEntries();
                entries.forEach(e -> {
                    Label key = new Label(e.getKey());
                    Label value = new Label(e.getValue());
                    key.setStyle("-fx-font-size:8pt;");
                    value.setStyle("-fx-font-size:8pt;");
                    metaDataGrid.addRow(i.get(), key, value);
                    i.addAndGet(1);
                });
            }
        }

        //read xmpdata
        if (xmpdata != null) {
            iterator = xmpdata.iterator();
            while (iterator.hasNext()) {
                MetadataEntry item = iterator.next();
                Collection<MetadataEntry> entries = item.getMetadataEntries();
                entries.forEach(e -> {
                    Label key = new Label(e.getKey());
                    Label value = new Label(e.getValue());
                    key.setStyle("-fx-font-size:8pt;");
                    value.setStyle("-fx-font-size:8pt;");
                    metaDataGrid.addRow(i.get(), key, value);
                    i.addAndGet(1);
                });
            }
        }

        metaDataGrid.setDisable(false);
    }

    public void resetGUI() {
        Platform.runLater(() -> {
            metaDataGrid.getChildren().clear();
            metaDataGrid.setDisable(true);
            keywordText.getChildren().clear();
        });
        if (keywordText.getChildren().size() > 0) {
            keywordText.getChildren().removeListener(keywordsChangeListener);
            captionTextField.textProperty().removeListener(captionChangeListener);
            commentText.textProperty().removeListener(commentsChangeListener);
        }
        keywordsChangeListener = new KeywordChangeListener();
        commentsChangeListener = new CommentsChangeListener();
        captionChangeListener = new CaptionChangeListener();

        gpsDateTime.clear();
        gpsHeight.clear();
        gpsPlace.clear();
        commentText.clear();
        captionTextField.clear();
        recordDateField.clear();
        progressPane.setVisible(false);
        anchorKeywordPane.setDisable(true);
        apertureSlider.setValue(1);
        exposerFilter = null;
    }

    @FXML
    private void addKeywordAction(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            keywordText.getChildren().add(new Tag(addKeywordTextField.getText()));
            addKeywordTextField.clear();
        }
    }

    public void exportBasicMetadata(MediaFile mf, String exportFilePath) throws FileNotFoundException, IOException {
        if (exportFilePath.contains(".png")) {
            return;
        }
        FileInputStream fin = new FileInputStream(exportFilePath);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        if (iptcdata == null) {
            iptcdata = new IPTC();
        }
        Map<IPTCTag, List<IPTCDataSet>> dataSets = iptcdata.getDataSets();
        List<IPTCDataSet> keywordListLocal = dataSets.get(IPTCApplicationTag.KEY_WORDS);
        if (keywordListLocal == null) {
            keywordListLocal = new ArrayList<>();
            dataSets.put(IPTCApplicationTag.KEY_WORDS, keywordListLocal);
        }
        keywordListLocal.clear();

        StringTokenizer defaultTokenizer = new StringTokenizer(mf.getKeywords(), ";");
        while (defaultTokenizer.hasMoreTokens()) {
            keywordListLocal.add(new IPTCDataSet(IPTCApplicationTag.KEY_WORDS, defaultTokenizer.nextToken()));
        }
        Metadata.insertIPTC(fin, bout, iptcdata.getDataSet(IPTCApplicationTag.KEY_WORDS), true);
        try (OutputStream outputStream = new FileOutputStream(exportFilePath)) {
            bout.writeTo(outputStream);
        }
        fin.close();
        bout.close();
        fin = new FileInputStream(exportFilePath);
        bout = new ByteArrayOutputStream();
        if (mf.getTitleProperty().get() != null) {
            if (iptcdata.getDataSet(IPTCApplicationTag.OBJECT_NAME) != null) {
                iptcdata.getDataSet(IPTCApplicationTag.OBJECT_NAME).clear();
                iptcdata.getDataSet(IPTCApplicationTag.OBJECT_NAME).add(new IPTCDataSet(IPTCApplicationTag.OBJECT_NAME, mf.getTitleProperty().get()));
            } else {
                iptcdata.addDataSet(new IPTCDataSet(IPTCApplicationTag.OBJECT_NAME, mf.getTitleProperty().get()));
            }
            Metadata.insertIPTC(fin, bout, iptcdata.getDataSet(IPTCApplicationTag.OBJECT_NAME), true);
            try (OutputStream outputStream = new FileOutputStream(exportFilePath)) {
                bout.writeTo(outputStream);
            }
        }
        fin.close();
        bout.close();
        fin = new FileInputStream(exportFilePath);
        bout = new ByteArrayOutputStream();
        if (mf.getTitleProperty().get() != null) {
            if (iptcdata.getDataSet(IPTCApplicationTag.CAPTION_ABSTRACT) != null) {
                iptcdata.getDataSet(IPTCApplicationTag.CAPTION_ABSTRACT).clear();
                iptcdata.getDataSet(IPTCApplicationTag.CAPTION_ABSTRACT).add(new IPTCDataSet(IPTCApplicationTag.CAPTION_ABSTRACT, mf.getTitleProperty().get()));
            } else {
                iptcdata.addDataSet(new IPTCDataSet(IPTCApplicationTag.CAPTION_ABSTRACT, mf.getTitleProperty().get()));
            }
            Metadata.insertIPTC(fin, bout, iptcdata.getDataSet(IPTCApplicationTag.CAPTION_ABSTRACT), true);
            try (OutputStream outputStream = new FileOutputStream(exportFilePath)) {
                bout.writeTo(outputStream);
            }
        }
        fin.close();
        bout.close();
        fin = new FileInputStream(exportFilePath);
        bout = new ByteArrayOutputStream();
        if (iptcdata.getDataSet(IPTCApplicationTag.WRITER_EDITOR) != null) {
            iptcdata.getDataSet(IPTCApplicationTag.WRITER_EDITOR).clear();
            iptcdata.getDataSet(IPTCApplicationTag.WRITER_EDITOR).add(new IPTCDataSet(IPTCApplicationTag.WRITER_EDITOR, "Photoslide " + new Utility().getAppVersion() + " http://www.photoslide.org"));
        } else {
            iptcdata.addDataSet(new IPTCDataSet(IPTCApplicationTag.WRITER_EDITOR, "Photoslide " + new Utility().getAppVersion() + " http://www.photoslide.org"));
        }
        Metadata.insertIPTC(fin, bout, iptcdata.getDataSet(IPTCApplicationTag.WRITER_EDITOR), true);
        try (OutputStream outputStream = new FileOutputStream(exportFilePath)) {
            bout.writeTo(outputStream);
        }
        fin.close();
        bout.close();

        fin = new FileInputStream(exportFilePath);
        bout = new ByteArrayOutputStream();
        if (commentsdata != null) {
            Metadata.insertComments(fin, bout, commentsdata);
            try (OutputStream outputStream = new FileOutputStream(exportFilePath)) {
                bout.writeTo(outputStream);
            }
        }
        fin.close();
        bout.close();
    }

    public void exportCompleteMetdata(MediaFile mf, String exportFilePath, String extension) throws IOException {
        if (exportFilePath.contains(".png")) {
            return;
        }
        exportBasicMetadata(mf, exportFilePath);
        FileInputStream fin = new FileInputStream(exportFilePath);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        Exif exportExif;
        if (extension.contains("jpg")) {
            exportExif = new JpegExif();
        } else {
            exportExif = new TiffExif();
        }
        exportExif.setExifIFD(exifdata.getExifIFD());
        exportExif.setGPSIFD(exifdata.getGPSIFD());
        if (!exportFilePath.contains(".tif")) {
            exportExif.setImageIFD(exifdata.getImageIFD());
        }
        Metadata.insertExif(fin, bout, exportExif);
        try (OutputStream outputStream = new FileOutputStream(exportFilePath)) {
            bout.writeTo(outputStream);
        }
        fin.close();
        bout.close();
        if (!exportFilePath.contains(".jpg")) {
            fin = new FileInputStream(exportFilePath);
            bout = new ByteArrayOutputStream();
            Metadata.insertXMP(fin, bout, xmpdata);
            try (OutputStream outputStream = new FileOutputStream(exportFilePath)) {
                bout.writeTo(outputStream);
            }
            fin.close();
            bout.close();
        }
    }

    private void saveComments() {
        progressPane.setVisible(true);
        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressLabel.setText("Saving comments...");
        Task<Boolean> taskSaveComments = new Task<>() {
            @Override
            protected Boolean call() throws IOException {
                //saveImageEdits();
                if (actualMediaFile.isRawImage()) {
                    actualMediaFile.saveEdits();
                    return true;
                }
                FileInputStream fin;
                ByteArrayOutputStream bout = null;
                try {
                    bout = new ByteArrayOutputStream();
                    fin = new FileInputStream(actualMediaFile.getPathStorage().toFile());

                    Metadata.insertComment(fin, bout, commentText.getText());

                    try (OutputStream outputStream = new FileOutputStream(actualMediaFile.getPathStorage().toFile())) {
                        bout.writeTo(outputStream);
                    }
                    fin.close();
                } catch (IOException ex) {
                    Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    try {
                        if (bout != null) {
                            bout.close();
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                return null;
            }
        };
        taskSaveComments.setOnScheduled((t) -> {
            progressPane.setVisible(false);
        });
        taskSaveComments.setOnFailed((t) -> {
            progressPane.setVisible(true);
        });
        executor.submit(taskSaveComments);
    }

    private String getKeywordsAsString(FlowPane kewordPane) {
        StringBuilder sb = new StringBuilder();
        if (!kewordPane.getChildren().isEmpty()) {
            kewordPane.getChildren().forEach((tagitem) -> {
                sb.append(((Tag) tagitem).getText()).append(";");
            });
            String substring = sb.toString().substring(0, sb.toString().length() - 1);
            return substring;
        } else {
            return "";
        }
    }

    /**
     * Saves the keywords to the file
     *
     */
    private void saveKeywordsTitle(MediaFile file) {
        progressPane.setVisible(true);
        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressLabel.setText("Saving keywords...");
        Task<Boolean> taskKeywordsTitle = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                if (this.isCancelled() == false) {
                    String title = captionTextField.getText();
                    if (title == null) {
                        title = "";
                    }
                    updateKeywordsTitle(file, title, getKeywordsAsString(keywordText));
                }
                return null;
            }
        };
        taskKeywordsTitle.setOnSucceeded((t) -> {
            progressPane.setVisible(false);
            lightController.getTitleLabel().textProperty().unbind();
            lightController.getTitleLabel().setText(captionTextField.getText());
        });
        taskKeywordsTitle.setOnFailed((t) -> {
            progressPane.setVisible(true);
        });
        executor.submit(taskKeywordsTitle);
    }

    /**
     * Saves the keywords to the file
     *
     * @param givenMediaFile if null is specified the actual selected mediafile
     * will be used (var actualMediaFile)
     */
    private void updateKeywordsTitle(MediaFile file, String title, String keywords) throws Exception {
        if (title == null) {
            title = "";
        }
        if (keywords == null) {
            keywords = "";
        }
        FileInputStream fin;
        ByteArrayOutputStream bout = null;
        if (file.isRawImage()) {
            file.setKeywords(keywords);
            file.setTitle(title);
            Platform.runLater(() -> {
                file.saveEdits();
            });
            return;
        }
        try {
            bout = new ByteArrayOutputStream();

            fin = new FileInputStream(file.getPathStorage().toFile());

            if (iptcdata == null) {
                iptcdata = new IPTC();
            }
            Map<IPTCTag, List<IPTCDataSet>> dataSets = iptcdata.getDataSets();
            List<IPTCDataSet> keywordListLocal = dataSets.get(IPTCApplicationTag.KEY_WORDS);
            if (keywordListLocal == null) {
                keywordListLocal = new ArrayList<>();
                dataSets.put(IPTCApplicationTag.KEY_WORDS, keywordListLocal);
            }
            keywordListLocal.clear();

            StringTokenizer defaultTokenizer = new StringTokenizer(keywords, ";");
            while (defaultTokenizer.hasMoreTokens()) {
                keywordListLocal.add(new IPTCDataSet(IPTCApplicationTag.KEY_WORDS, defaultTokenizer.nextToken()));
            }
            List<IPTCDataSet> objNameList = dataSets.get(IPTCApplicationTag.OBJECT_NAME);
            List<IPTCDataSet> captionList = dataSets.get(IPTCApplicationTag.CAPTION_ABSTRACT);
            if (captionList == null) {
                captionList = new ArrayList<>();
                dataSets.put(IPTCApplicationTag.CAPTION_ABSTRACT, captionList);
            }
            if (objNameList != null) {
                objNameList.clear();
            } else {
                objNameList = new ArrayList<>();
                dataSets.put(IPTCApplicationTag.OBJECT_NAME, objNameList);
            }
            objNameList.add(new IPTCDataSet(IPTCApplicationTag.OBJECT_NAME, title));
            if (captionList != null) {
                captionList.clear();
            } else {
                captionList = new ArrayList<>();
                dataSets.put(IPTCApplicationTag.CAPTION_ABSTRACT, captionList);
            }
            captionList.add(new IPTCDataSet(IPTCApplicationTag.CAPTION_ABSTRACT, title));

            List<IPTCDataSet> iptcs = new ArrayList<>();
            dataSets.entrySet().forEach((t) -> {
                List<IPTCDataSet> tagValues = t.getValue();
                tagValues.forEach(tagValue -> {
                    iptcs.add(tagValue);
                });
            });
            Metadata.insertIPTC(fin, bout, iptcs, false);
            try (OutputStream outputStream = new FileOutputStream(file.getPathStorage().toFile())) {
                bout.writeTo(outputStream);
            }
            fin.close();
        } catch (IOException ex) {
            Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (bout != null) {
                    bout.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @FXML
    private void applyKeywordsToAllAction(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Apply Caption/Title and keywords to all mediafiles in event\n" + this.actualMediaFile.getPathStorage(), ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle("Confirmation");
        alert.setHeaderText("Apply Caption/Title/keywords to all mediafiles");
        DialogPane dialogPane = alert.getDialogPane();
        alert.setResizable(true);
        GridPane content = new GridPane();
        content.setAlignment(Pos.CENTER_RIGHT);
        content.setHgap(5);
        content.setVgap(5);
        Label title = new Label("Title/Caption");
        TextField titleText = new TextField(captionTextField.getText());
        titleText.setPrefWidth(300);
        HBox.setHgrow(titleText, Priority.ALWAYS);
        content.addRow(0, title, titleText, new Pane());
        Label keywordLabel = new Label("Keywords");
        ScrollPane scrollpane = new ScrollPane();
        scrollpane.setPrefSize(300, 150);
        HBox.setHgrow(scrollpane, Priority.ALWAYS);
        VBox.setVgrow(scrollpane, Priority.ALWAYS);
        FlowPane keywordsToAllText = new FlowPane();
        keywordsToAllText.setVgap(5);
        keywordsToAllText.setHgap(5);
        keywordsToAllText.setPadding(new Insets(5, 5, 5, 5));
        scrollpane.setContent(keywordsToAllText);
        keywordsToAllText.getChildren().addAll(keywordText.getChildren());
        content.addRow(1, keywordLabel, scrollpane, new Pane());
        TextField addKeywordToAllField = new TextField();
        addKeywordToAllField.setPrefWidth(300);
        HBox.setHgrow(addKeywordToAllField, Priority.ALWAYS);
        addKeywordToAllField.setPromptText("Add keywords...");
        Button addKewordButton = new Button();
        addKewordButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        addKewordButton.setGraphic(new FontIcon("ti-plus"));
        addKewordButton.setOnAction((t) -> {
            keywordsToAllText.getChildren().add(new Tag(addKeywordToAllField.getText()));
            addKeywordToAllField.clear();
            addKeywordToAllField.requestFocus();
        });
        content.addRow(2, new Pane(), addKeywordToAllField, addKewordButton);

        dialogPane.setContent(content);
        dialogPane.getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        Image dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(dialogIcon);
        Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
        alert = Utility.setDefaultButton(alert, ButtonType.CANCEL);
        alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        alert.showAndWait();
        if (alert.getResult() == ButtonType.OK) {

            resetGUI();
            mainController.getProgressPane().setVisible(true);
            mainController.getProgressbar().setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            mainController.getProgressbarLabel().setText("Setting metadata...");
            mainController.getStatusLabelLeft().setText("Setting metadata");
            mainController.getStatusLabelLeft().setVisible(true);

            Task<Boolean> taskApplyToAll = new Task<>() {
                @Override
                protected Boolean call() throws Exception {

                    // for loop through the media files
                    ObservableList<MediaFile> mediaList = lightController.getFullMediaList();
                    int i = 0;
                    for (MediaFile actFile : mediaList) {
                        if (this.isCancelled() == false) {
                            updateProgress(i + 1, mediaList.size());
                            updateMessage("" + (i + 1) + "/" + mediaList.size());
                            if (actFile.isRawImage()) {
                                actFile.saveEdits();
                            } else {
                                if (actFile.getMediaType() == MediaTypes.IMAGE) {
                                    readBasicMetadata(this, actFile);
                                    updateKeywordsTitle(actFile, titleText.getText(), getKeywordsAsString(keywordsToAllText));
                                }
                            }
                            i++;
                        }
                    }
                    return null;
                }
            };
            taskApplyToAll.setOnSucceeded((t) -> {
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressbarLabel().textProperty().unbind();
                Platform.runLater(() -> {
                    mainController.getProgressbarLabel().setText("");
                    mainController.getProgressPane().setVisible(false);
                    mainController.getStatusLabelLeft().setVisible(false);
                });
                actualMediaFile = lightController.getFactory().getSelectedCell().getItem();
                setSelectedFile(actualMediaFile);
            });
            taskApplyToAll.setOnFailed((t) -> {
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressbarLabel().textProperty().unbind();
                mainController.getProgressPane().setVisible(false);
                mainController.getStatusLabelLeft().setVisible(false);
            });
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbar().progressProperty().bind(taskApplyToAll.progressProperty());
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressbarLabel().textProperty().bind(taskApplyToAll.messageProperty());
            executor.submit(taskApplyToAll);
        }
    }

    public void saveSettings() {
    }

    public void restoreSettings() {
    }

    @FXML
    private void resetAction(ActionEvent event) {
        actualMediaFile.removeImageFilter(exposerFilter);
        actualMediaFile.saveEdits();
        Platform.runLater(() -> {
            apertureSlider.setValue(1);
            lightController.getImageView().setImage(exposerFilter.reset());
            actualMediaFile.setImage(exposerFilter.reset());
            exposerFilter = null;
        });
    }

    @FXML
    private void showGPSPosition(ActionEvent event) {
        //System.out.println("action");
        showMap();
    }

    private void showMap() {
        if (actualMediaFile.getGpsPosition() == null || actualMediaFile.getGpsPosition().equalsIgnoreCase(";")) {
            return;
        }
        Utility util = new Utility();
        PopOver popOver = new PopOver();
        popOver.setArrowLocation(PopOver.ArrowLocation.RIGHT_CENTER);
        Label message = new Label("");
        message.setVisible(false);
        message.setManaged(false);
        VBox vb = new VBox();
        vb.setPadding(new Insets(10));
        vb.setSpacing(5);
        HBox hb = new HBox();
        hb.setSpacing(5);
        Button saveAs = new Button("Save map to image");
        saveAs.setOnAction((t) -> {
            WritableImage image = map.snapshot(new SnapshotParameters(), null);
            executorParallel.submit(() -> {
                BufferedImage renderedImage = SwingFXUtils.fromFXImage(image, null);
                //Write the snapshot to the chosen file
                String files = actualMediaFile.getPathStorage().getParent().toString().toString() + File.separator + "MapScreen" + System.currentTimeMillis() + ".png";
                try {
                    FileOutputStream fo = new FileOutputStream(files, false);
                    ImageWriter writer = ImageIO.getWriter(ImageType.PNG);
                    ImageParam.ImageParamBuilder builder = ImageParam.getBuilder();
                    PNGOptions pngOptions = new PNGOptions();
                    pngOptions.setApplyAdaptiveFilter(true);
                    pngOptions.setCompressionLevel(6);
                    pngOptions.setFilterType(Filter.NONE);
                    builder.imageOptions(pngOptions);
                    writer.setImageParam(builder.build());
                    writer.write(renderedImage, fo);
                    fo.close();
                    message.setVisible(true);
                    message.setManaged(true);
                    message.setText("Save successfully!");
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, null, ex);
                    message.setText("Error on saving image!");
                } catch (Exception ex) {
                    Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, null, ex);
                    message.setText("Error on saving image!");
                }
                util.hideNodeAfterTime(message, 3, false);
            });
        });
        saveAs.setId("toolbutton");
        saveAs.setGraphic(new FontIcon("ti-save:16"));
        Button clipboardButton = new Button("Copy GPS position to clipboard");
        clipboardButton.setOnAction((t) -> {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(actualMediaFile.getGpsLatPosAsDouble() + "," + actualMediaFile.getGpsLonPosAsDouble());
            clipboard.setContent(content);
            message.setVisible(true);
            message.setManaged(true);
            message.setText("Copied GPS position to clipboard.");
            util.hideNodeAfterTime(message, 3, false);
        });
        clipboardButton.setId("toolbutton");
        clipboardButton.setGraphic(new FontIcon("ti-clipboard:16"));
        hb.getChildren().add(saveAs);
        hb.getChildren().add(clipboardButton);
        vb.getChildren().add(hb);
        map.setZoom(19);
        double lat = actualMediaFile.getGpsLatPosAsDouble();
        double lon = actualMediaFile.getGpsLonPosAsDouble();
        Coordinate c = new Coordinate(lat, lon);
        map.setCenter(c);
        markerPos.setPosition(c).setVisible(true);
        map.addMarker(markerPos);
        circle = new MapCircle(c, 3);
        circle.setVisible(true);
        //circle.setFillColor(Color.TRANSPARENT);        
        circle.setColor(javafx.scene.paint.Color.BLUE);
        circle.setWidth(1);
        map.addMapCircle(circle);
        map.setEffect(new ColorAdjust(0, -0.5, 0, 0));
        vb.getChildren().add(map);
        vb.getChildren().add(message);
        popOver.setContentNode(vb);
        popOver.show(gpsPlace);
        ((Parent) popOver.getSkin().getNode()).getStylesheets()
                .add(getClass().getResource("/org/photoslide/css/PopOver.css").toExternalForm());
    }

    @FXML
    private void showGPSPositionTouch(TouchEvent event) {
        showMap();
    }

    @FXML
    private void showGPSPositionMouse(MouseEvent event) {
        showMap();
    }

    @FXML
    private void showGPSPositionIconMouse(MouseEvent event) {
        showMap();
    }

    @FXML
    private void showGPSPositionIconTouch(TouchEvent event) {
        showMap();
    }

    private class KeywordChangeListener implements ListChangeListener {

        @Override
        public void onChanged(Change change) {
            saveKeywordsTitle(actualMediaFile);
        }

    }

    private class CommentsChangeListener implements ChangeListener<String> {

        @Override
        public void changed(ObservableValue<? extends String> ov, String t, String t1) {
            saveComments();
        }

    }

    private class CaptionChangeListener implements ChangeListener<String> {

        @Override
        public void changed(ObservableValue<? extends String> ov, String t, String t1) {
            saveKeywordsTitle(actualMediaFile);
        }

    }

    public void setActualMediaFile(MediaFile actualMediaFile) {
        this.actualMediaFile = actualMediaFile;
    }

    public TextField getRecordDateField() {
        return recordDateField;
    }

    public void cancelTasks() {
        if (task != null) {
            keywordText.getChildren().removeListener(keywordsChangeListener);
            captionTextField.textProperty().removeListener(captionChangeListener);
            commentText.textProperty().removeListener(commentsChangeListener);
            progressPane.setVisible(false);
            task.cancel();
        }
    }

    public Slider getApertureSlider() {
        return apertureSlider;
    }

    public Exif getExifdata() {
        return exifdata;
    }

    public IPTC getIptcdata() {
        return iptcdata;
    }

    public XMP getXmpdata() {
        return xmpdata;
    }

    public List<String> getCommentsdata() {
        return commentsdata;
    }

    /**
     * Method of getting all metadata as a string for the search database
     *
     * @return String with all meta key/value pairs in format key:value<newline>
     */
    public String getMetaDataAsString() {
        StringBuilder sb = new StringBuilder();
        Iterator<MetadataEntry> iterator;

        //read jpge exif
        if (this.getExifdata() != null) {
            iterator = this.getExifdata().iterator();
            while (iterator.hasNext()) {
                MetadataEntry item = iterator.next();
                Collection<MetadataEntry> entries = item.getMetadataEntries();
                entries.forEach(e -> {
                    sb.append(e.getKey()).append(":").append(e.getValue()).append(";");
                });
            }
        }

        //read iptcdata exif
        if (this.getIptcdata() != null) {
            iterator = this.getIptcdata().iterator();
            while (iterator.hasNext()) {
                MetadataEntry item = iterator.next();
                Collection<MetadataEntry> entries = item.getMetadataEntries();
                entries.forEach(e -> {
                    sb.append(e.getKey()).append(":").append(e.getValue()).append(";");
                });
            }
        }

        //read xmpdata
        /*if (this.getXmpdata() != null) {
            iterator = this.getXmpdata().iterator();
            while (iterator.hasNext()) {
                MetadataEntry item = iterator.next();
                Collection<MetadataEntry> entries = item.getMetadataEntries();
                entries.forEach(e -> {                    
                    sb.append(e.getKey()).append(":").append(e.getValue()).append(";");                    
                });
            }
        }*/
        return sb.toString();
    }

    public void setExposerFilter(ImageFilter exposerFilter) {
        this.exposerFilter = exposerFilter;
    }

    public HashMap<String, String> getRawMetaData() {
        return rawMetaData;
    }

    public void Shutdown() {
        if (task != null) {
            task.cancel();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        if (executorParallel != null) {
            executorParallel.shutdownNow();
        }
    }
}
