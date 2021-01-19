/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browsermetadata;

import org.photoslide.MainViewController;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.browserlighttable.LighttableController;
import com.icafe4j.image.meta.Metadata;
import com.icafe4j.image.meta.MetadataEntry;
import com.icafe4j.image.meta.MetadataType;
import com.icafe4j.image.meta.exif.Exif;
import com.icafe4j.image.meta.image.Comments;
import com.icafe4j.image.meta.iptc.IPTC;
import com.icafe4j.image.meta.iptc.IPTCApplicationTag;
import com.icafe4j.image.meta.iptc.IPTCDataSet;
import com.icafe4j.image.meta.iptc.IPTCTag;
import com.icafe4j.image.meta.jpeg.JpegExif;
import com.icafe4j.image.meta.tiff.TiffExif;
import com.icafe4j.image.meta.xmp.XMP;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
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
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
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
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
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
import org.controlsfx.control.textfield.TextFields;
import org.kordamp.ikonli.javafx.FontIcon;
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

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("metaDataController"));
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryPS("metaDataControllerParallel"));
        keywordList = FXCollections.observableArrayList();
        Platform.runLater(() -> {
            anchorKeywordPane.setDisable(true);
            accordionPane.setExpandedPane(keywordsPane);
            progressPane.setVisible(false);
        });
        keywordsChangeListener = new KeywordChangeListener();
        commentsChangeListener = new CommentsChangeListener();
        captionChangeListener = new CaptionChangeListener();
        apertureSlider.valueProperty().addListener((o) -> {
            if (exposerFilter == null) {
                lightController.getImageView().setCache(true);
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
                readBasicMetadata(this);
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

    public synchronized void readBasicMetadata(Task actTask) throws IOException {
        Map<MetadataType, Metadata> metadataMap = Metadata.readMetadata(actualMediaFile.getPathStorage().toFile());
        for (Map.Entry<MetadataType, Metadata> entry : metadataMap.entrySet()) {
            if (actTask.isCancelled() == false) {
                Metadata meta = entry.getValue();
                Iterator<MetadataEntry> iterator;
                switch (meta.getType()) {
                    case XMP ->
                        xmpdata = ((XMP) meta);
                    //XMP.showXMP(xmpdata);
                    case COMMENT -> {
                        if (meta instanceof Comments) {
                            commentsdata = ((Comments) meta).getComments();
                            StringBuilder sb = new StringBuilder();
                            commentsdata.forEach(comment -> {
                                sb.append(comment);
                            });
                            if (Platform.isFxApplicationThread() == true) {
                                actualMediaFile.getComments().set(sb.toString());
                            } else {
                                Platform.runLater(() -> {
                                    actualMediaFile.getComments().set(sb.toString());
                                });
                            }
                        }
                    }
                    case IPTC -> {
                        iptcdata = ((IPTC) meta);
                        iterator = meta.iterator();
                        while (iterator.hasNext()) {
                            MetadataEntry item = iterator.next();
                            switch (item.getKey()) {
                                case "Keywords" -> {
                                    if (Platform.isFxApplicationThread() == true) {
                                        actualMediaFile.setKeywords(item.getValue());
                                    } else {
                                        Platform.runLater(() -> {
                                            actualMediaFile.setKeywords(item.getValue());
                                        });
                                    }
                                }
                                case "Caption Abstract" -> {
                                    if (Platform.isFxApplicationThread() == true) {
                                        actualMediaFile.setTitle(item.getValue());
                                    } else {
                                        Platform.runLater(() -> {
                                            actualMediaFile.setTitle(item.getValue());
                                        });
                                    }
                                }
                            }
                        }
                    }
                    case JPG_JFIF -> {
                    }
                    case EXIF -> {
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
                                            actualMediaFile.setRecordTime(date);
                                        } else {
                                            actualMediaFile.setRecordTime(LocalDateTime.now());
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
                                        case "GPS Latitude Ref" ->
                                            gpsLatRefsb.append(mediaEntry.getValue());
                                        case "GPS Latitude" ->
                                            gpsLatsb.append(mediaEntry.getValue());
                                        case "GPS Longitude Ref" ->
                                            gpsLongRefsb.append(mediaEntry.getValue());
                                        case "GPS Longitude" ->
                                            gpsLongsb.append(mediaEntry.getValue());
                                        case "GPS Altitude" ->
                                            gpsHeightsb.append(mediaEntry.getValue());
                                        case "GPS Time Stamp" ->
                                            gpsTimesb.append(mediaEntry.getValue());
                                        case "GPS Date Stamp" ->
                                            gpsDatesb.append(mediaEntry.getValue());
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
                                        actualMediaFile.setGpsHeight(d);
                                    }
                                }
                                if (gpsDatesb.toString() != null) {
                                    String gpsDateStr = gpsDatesb.toString().replace(":", ".");
                                    String formatTime = formatTime(gpsTimesb.toString());
                                    actualMediaFile.setGpsDateTime(gpsDateStr + "T" + formatTime);
                                }
                                actualMediaFile.setGpsPosition(gpsLatsb.toString() + gpsLatRefsb.toString() + ";" + gpsLongsb.toString() + gpsLongRefsb.toString());
                            }
                            if (item.getKey().equalsIgnoreCase("IFD0")) {
                                entries.stream().forEach((mediaEntry) -> {
                                    if (mediaEntry.getKey().equalsIgnoreCase("Model")) {
                                        if (Platform.isFxApplicationThread() == true) {
                                            actualMediaFile.setCamera(mediaEntry.getValue());
                                        } else {
                                            Platform.runLater(() -> {
                                                actualMediaFile.setCamera(mediaEntry.getValue());
                                            });
                                        }
                                    }
                                });
                            }
                        }
                    }
                    case ICC_PROFILE -> {
                    }
                    default -> {
                    }
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
        keywordText.getChildren().removeListener(keywordsChangeListener);
        captionTextField.textProperty().removeListener(captionChangeListener);
        commentText.textProperty().removeListener(commentsChangeListener);
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

    private void saveAction(ActionEvent event) {
        FileInputStream fin;
        ByteArrayOutputStream bout = null;
        try {
            bout = new ByteArrayOutputStream();
            fin = new FileInputStream(actualMediaFile.getPathStorage().toFile());

            List<IPTCDataSet> iptcs = new ArrayList<>();
            /*StringTokenizer defaultTokenizer = new StringTokenizer(keywordText.getText(), ";");

            while (defaultTokenizer.hasMoreTokens()) {
                iptcs.add(new IPTCDataSet(IPTCApplicationTag.KEY_WORDS, defaultTokenizer.nextToken()));
            }*/

            keywordText.getChildren().forEach((tagitem) -> {
                String text = ((Tag) tagitem).getText();
                iptcs.add(new IPTCDataSet(IPTCApplicationTag.KEY_WORDS, text));
            });

            iptcs.add(new IPTCDataSet(IPTCApplicationTag.OBJECT_NAME, captionTextField.getText()));
            Metadata.insertIPTC(fin, bout, iptcs, true);

            fin.close();
            try ( OutputStream outputStream = new FileOutputStream(actualMediaFile.getPathStorage().toFile())) {
                bout.writeTo(outputStream);
            }
            bout.close();

            bout = new ByteArrayOutputStream();
            fin = new FileInputStream(actualMediaFile.getPathStorage().toFile());

            Metadata.insertComment(fin, bout, commentText.getText());

            fin.close();
            try ( OutputStream outputStream = new FileOutputStream(actualMediaFile.getPathStorage().toFile())) {
                bout.writeTo(outputStream);
            }

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
        System.out.println("Save meta data");
    }

    private void saveComments() {
        progressPane.setVisible(true);
        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressLabel.setText("Saving comments...");
        Task<Boolean> taskSaveComments = new Task<>() {
            @Override
            protected Boolean call() throws IOException {
                //saveImageEdits();
                FileInputStream fin;
                ByteArrayOutputStream bout = null;
                try {
                    bout = new ByteArrayOutputStream();
                    fin = new FileInputStream(actualMediaFile.getPathStorage().toFile());

                    Metadata.insertComment(fin, bout, commentText.getText());

                    try ( OutputStream outputStream = new FileOutputStream(actualMediaFile.getPathStorage().toFile())) {
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
    private void saveKeywordsTitle() {
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
                    updateKeywordsTitle(title, getKeywordsAsString(keywordText));
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
    private void updateKeywordsTitle(String title, String keywords) throws Exception {
        if (title == null) {
            title = "";
        }
        if (keywords == null) {
            keywords = "";
        }
        FileInputStream fin;
        ByteArrayOutputStream bout = null;
        try {
            bout = new ByteArrayOutputStream();

            fin = new FileInputStream(actualMediaFile.getPathStorage().toFile());

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
            try ( OutputStream outputStream = new FileOutputStream(actualMediaFile.getPathStorage().toFile())) {
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
                getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
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
                            actualMediaFile = actFile;
                            if (actFile.getMediaType() == MediaTypes.IMAGE) {
                                readBasicMetadata(this);
                                updateKeywordsTitle(titleText.getText(), getKeywordsAsString(keywordsToAllText));
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
        Platform.runLater(() -> {
            apertureSlider.setValue(1);
            lightController.getImageView().setImage(exposerFilter.reset());
            exposerFilter = null;
        });
    }

    private class KeywordChangeListener implements ListChangeListener {

        @Override
        public void onChanged(Change change) {
            saveKeywordsTitle();
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
            saveKeywordsTitle();
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
