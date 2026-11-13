/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browsermetadata;


import org.photoslide.I18n;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.nio.file.OpenOption;
import java.nio.charset.StandardCharsets;
import com.icafe4j.image.meta.jpeg.JpegXMP;
import com.adobe.internal.xmp.options.SerializeOptions;
import com.adobe.internal.xmp.options.PropertyOptions;
import com.adobe.internal.xmp.XMPMetaFactory;
import com.adobe.internal.xmp.XMPMeta;
import com.adobe.internal.xmp.XMPException;
import com.adobe.internal.xmp.XMPConst;
import java.util.concurrent.CountDownLatch;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import javafx.application.Platform;
import javafx.util.Duration;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.Set;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.imaging.avi.AviMetadataReader;
import com.drew.imaging.mp4.Mp4MetadataReader;
import com.drew.imaging.quicktime.QuickTimeMetadataReader;
import com.drew.imaging.webp.WebpMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import com.icafe4j.image.ImageIO;
import com.icafe4j.image.ImageParam;
import com.icafe4j.image.ImageType;
import org.photoslide.MainViewController;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.browserlighttable.LighttableController;
import com.icafe4j.image.meta.Metadata;
import com.icafe4j.image.meta.MetadataEntry;
import com.icafe4j.image.meta.MetadataType;
import com.icafe4j.image.meta.exif.Exif;
import com.icafe4j.image.meta.exif.GPSTag;
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
import fr.dudie.nominatim.model.Address;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.beans.binding.Bindings;
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
import javafx.beans.InvalidationListener;
import javafx.scene.effect.ColorAdjust;
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
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import javafx.util.converter.NumberStringConverter;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.CustomTextField;
import org.controlsfx.control.textfield.TextFields;
import org.kordamp.ikonli.javafx.FontIcon;
import org.libheiffx.LibheifImage;
import org.librawjava.LibrawImage;
import org.librawdecodersettings.RawDecoderSettings;
import org.photoslide.MarkerLayer;
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.Utility;
import org.photoslide.datamodel.MediaFile.MediaTypes;
import org.photoslide.editpipeline.EditPipeline;
import org.photoslide.imageops.ExposureFilter;
import org.photoslide.imageops.GainFilter;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.imageops.AbstractImageFilter;
import org.photoslide.imageops.ParamSpec;
import org.photoslide.imageops.lz.HueSaturationFilter;
import org.photoslide.imageops.lz.RelightFilter;
import org.photoslide.imageops.lz.WhiteBalanceFilter;

/**
 *
 * @author selfemp
 */
public class MetadataController implements Initializable {

    private ExecutorService executor;
    private MainViewController mainController;
    private LighttableController lightController;
    // all keywords seen, for the auto completion (read by its background thread)
    private final Set<String> keywordList = new ConcurrentSkipListSet<>();
    private MediaFile actualMediaFile;

    // metadata of actualMediaFile, null while it is loading
    private MetadataResult actualMetadata;
    // the metadata grid does not show actualMetadata yet: built when it gets visible
    private boolean metadataGridDirty;
    private GeoCoding geoCoding;

    private Task<MetadataResult> task;
    private KeywordChangeListener keywordsChangeListener;
    private CommentsChangeListener commentsChangeListener;
    private CaptionChangeListener captionChangeListener;

    // typing saves after a pause, not the whole file for each key
    private static final Duration SAVE_DELAY = Duration.millis(600);
    private final PauseTransition captionSaveDelay = new PauseTransition(SAVE_DELAY);
    private final PauseTransition commentSaveDelay = new PauseTransition(SAVE_DELAY);
    // file of the pending save, null if none
    private MediaFile captionSaveFile;
    private MediaFile commentSaveFile;

    // last queued save per file: loading the file waits for it
    private final Map<MediaFile, Future<?>> pendingWrites = new ConcurrentHashMap<>();

    private ExecutorService executorParallel;
    private ExecutorService executorFilter;

    private Image shownImage;
    private boolean updatingFilterSliders;
    /** Quick develop sliders bound to one parameter of an editor tool. */
    private final List<QuickParam> quickParams = new ArrayList<>();

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
    private TextField gpsPlace;
    @FXML
    private TextField gpsHeight;
    @FXML
    private TextField gpsDateTime;
    private MapView map;
    @FXML
    private Button gpsPosButton;
    @FXML
    private TextField exposureTextField;
    @FXML
    private TextField gainTextField;
    @FXML
    private TextField biasTextField;
    @FXML
    private Slider exposureSlider;
    @FXML
    private Slider gainSlider;
    @FXML
    private Slider biasSlider;
    @FXML
    private Slider temperatureSlider;
    @FXML
    private Slider tintSlider;
    @FXML
    private TextField temperatureTextField;
    @FXML
    private TextField tintTextField;
    @FXML
    private Slider saturationSlider;
    @FXML
    private Slider vibranceSlider;
    @FXML
    private TextField saturationTextField;
    @FXML
    private TextField vibranceTextField;
    @FXML
    private Slider shadowsSlider;
    @FXML
    private Slider highlightsSlider;
    @FXML
    private TextField shadowsTextField;
    @FXML
    private TextField highlightsTextField;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNamePrefix("metaDataController").build());
        executorParallel = Executors.newThreadPerTaskExecutor(new ThreadFactoryBuilder().setNamePrefix("metaDataControllerParallel").build());
        executorFilter = Executors.newThreadPerTaskExecutor(new ThreadFactoryBuilder().setPriority(10).setNamePrefix("metaDataControllerParallel").build());
        TextFields.bindAutoCompletion(addKeywordTextField, (request) -> {
            String text = request.getUserText().toLowerCase();
            return keywordList.stream().filter(k -> k.toLowerCase().contains(text)).toList();
        });
        anchorKeywordPane.setDisable(true);
        accordionPane.setExpandedPane(keywordsPane);
        // the grid has a row per metadata entry: built only when it is shown
        metadataPane.expandedProperty().addListener((o, oldValue, expanded) -> {
            if (expanded) {
                buildMetadataGrid();
            }
        });
        progressPane.setVisible(false);
        map = new MapView();
        map.setPrefSize(400, 266);
        map.setMaxSize(400, 266);
        //markerPos = new Marker(getClass().getResource("/org/photoslide/img/map_marker.png"), -16, -32);
        keywordsChangeListener = new KeywordChangeListener();
        commentsChangeListener = new CommentsChangeListener();
        captionChangeListener = new CaptionChangeListener();
        captionSaveDelay.setOnFinished((e) -> flushPendingSaves());
        commentSaveDelay.setOnFinished((e) -> flushPendingSaves());
        exposureTextField.textProperty().bindBidirectional(exposureSlider.valueProperty(), new NumberStringConverter("#.##"));
        alignLabelToTrack(exposureSlider);
        exposureSlider.valueProperty().addListener((o) -> {
            if (updatingFilterSliders || actualMediaFile == null) {
                return;
            }
            EditPipeline.setFilterValues(actualMediaFile, ExposureFilter.NAME, ExposureFilter::new,
                    new float[]{(float) exposureSlider.getValue()});
            EditPipeline.filtersChanged(actualMediaFile);
        });
        gainTextField.textProperty().bindBidirectional(gainSlider.valueProperty(), new NumberStringConverter("#.##"));
        biasTextField.textProperty().bindBidirectional(biasSlider.valueProperty(), new NumberStringConverter("#.##"));
        InvalidationListener gainListener = (o) -> {
            if (updatingFilterSliders || actualMediaFile == null) {
                return;
            }
            EditPipeline.setFilterValues(actualMediaFile, GainFilter.NAME, GainFilter::new,
                    new float[]{(float) gainSlider.getValue(), (float) biasSlider.getValue()});
            EditPipeline.filtersChanged(actualMediaFile);
        };
        gainSlider.valueProperty().addListener(gainListener);
        biasSlider.valueProperty().addListener(gainListener);
        alignLabelToTrack(gainSlider);
        alignLabelToTrack(biasSlider);
        bindQuickParam(temperatureSlider, temperatureTextField, WhiteBalanceFilter.NAME, WhiteBalanceFilter::new, "temperature", "#");
        // the tool goes up to 40000 K, a quick slider is only useful in the usual range
        temperatureSlider.setMin(2000);
        temperatureSlider.setMax(12000);
        bindQuickParam(tintSlider, tintTextField, WhiteBalanceFilter.NAME, WhiteBalanceFilter::new, "tint", "#.#");
        bindQuickParam(saturationSlider, saturationTextField, HueSaturationFilter.NAME, HueSaturationFilter::new, "saturation", "#");
        bindQuickParam(vibranceSlider, vibranceTextField, HueSaturationFilter.NAME, HueSaturationFilter::new, "vibrance", "#");
        bindQuickParam(shadowsSlider, shadowsTextField, RelightFilter.NAME, RelightFilter::new, "shadows", "#.##");
        bindQuickParam(highlightsSlider, highlightsTextField, RelightFilter.NAME, RelightFilter::new, "highlights", "#.##");
        // sidecar changed by another program: show the reloaded values
        EditPipeline.addReloadListener(f -> {
            if (f == actualMediaFile) {
                showFilterValues(f);
            }
        });
        geoCoding = new GeoCoding();
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
        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressPane.setVisible(true);
        progressLabel.setText(I18n.t("meta.loading"));
        if (task != null) {
            task.cancel();
        }
        Task<MetadataResult> loadTask = new Task<>() {
            @Override
            protected MetadataResult call() {
                try {
                    if (!awaitPendingWrite(file)) {
                        return null;
                    }
                    return readMetadata(this, file);
                } catch (IOException e) {
                    Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, "Cannot read meta data from file '" + file.getName() + "'!", e);
                } catch (IllegalArgumentException f) {
                }
                return null;
            }
        };
        task = loadTask;
        loadTask.setOnSucceeded((WorkerStateEvent t) -> {
            // another file was selected meanwhile
            if (task != loadTask || actualMediaFile != file) {
                return;
            }
            showMetadata(file, loadTask.getValue());
        });
        loadTask.setOnFailed((t) -> {
            if (task != loadTask) {
                return;
            }
            anchorKeywordPane.setDisable(false);
            progressPane.setVisible(false);
        });
        Thread.ofVirtual().start(loadTask);
        mainController.getTaskProgressView().getTasks().add(loadTask);
    }

    /**
     * Shows the read metadata of the selected file. JavaFX thread.
     */
    private void showMetadata(MediaFile file, MetadataResult metadata) {
        actualMetadata = metadata != null ? metadata : new MetadataResult();
        metadataGridDirty = true;
        if (file.getRecordTime() != null) {
            recordDateField.setText(file.getRecordTime().format(RECORD_TIME));
        }
        if (file.getKeywords() != null) {
            List<Tag> tags = new ArrayList<>();
            StringTokenizer defaultTokenizer = new StringTokenizer(file.getKeywords(), ";");
            while (defaultTokenizer.hasMoreTokens()) {
                String nextToken = defaultTokenizer.nextToken();
                tags.add(new Tag(nextToken));
                keywordList.add(nextToken);
            }
            keywordText.getChildren().setAll(tags);
        }
        captionTextField.setText(file.titleProperty().get());
        if (file.commentsProperty().get() != null) {
            commentText.setText(file.commentsProperty().get());
        }
        anchorKeywordPane.setDisable(false);
        // after filling the fields: filling them must not save the file
        attachSaveListeners();

        if (file.getMediaType() != MediaFile.MediaTypes.VIDEO) {
            gpsPlace.setText(file.getGpsPosition());
            if (file.getGpsHeight() != -1) {
                gpsHeight.setText("" + file.getGpsHeight() + " m");
            }
            if (file.getGpsDateTime() != null) {
                gpsDateTime.setText(file.getGpsDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
        }
        if (metadataPane.isExpanded()) {
            buildMetadataGrid();
        }
        progressPane.setVisible(false);
    }

    private static final DateTimeFormatter EXIF_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
    private static final DateTimeFormatter RECORD_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter DE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final DateTimeFormatter VIDEO_TIME = DateTimeFormatter.ofPattern("yyyy:LLL:dd HH:mm:ss", Locale.US);

    /**
     * Reads only the capture time of a JPEG or HEIF file: "DateTime Digitized"
     * of the EXIF, as readBasicMetadata. metadata-extractor stops reading at
     * the image data, and unlike readBasicMetadata (synchronized, it fills the
     * fields of the metadata view) it runs in parallel: sorting a large folder
     * on a network share by capture time took minutes.
     *
     * @return null for other formats or if the file has no such time
     */
    public static LocalDateTime readCaptureTime(MediaFile file) {
        if (!readsCaptureTime(file)) {
            return null;
        }
        try {
            com.drew.metadata.Metadata metadata = ImageMetadataReader.readMetadata(file.getPathStorage().toFile());
            for (ExifSubIFDDirectory dir : metadata.getDirectoriesOfType(ExifSubIFDDirectory.class)) {
                String time = dir.getString(ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED);
                if (time != null && !time.isBlank()) {
                    try {
                        return LocalDateTime.parse(time.trim(), EXIF_TIME);
                    } catch (DateTimeParseException ex) {
                        // other formats: left to readBasicMetadata
                    }
                }
            }
        } catch (ImageProcessingException | IOException | RuntimeException ex) {
            Logger.getLogger(MetadataController.class.getName()).log(Level.FINE, "Cannot read the capture time of " + file.getName(), ex);
        }
        return null;
    }

    /**
     * @return true if readCaptureTime handles the format (JPEG, PNG, TIFF, HEIF):
     * its null then means the file has no capture time, readBasicMetadata
     * would not find one either
     */
    public static boolean readsCaptureTime(MediaFile file) {
        return !((file.isRawImage() && !file.isHEIFImage()) || file.isVideoFile());
    }

    // a read that hangs (e.g. a damaged file in a native decoder) must not stop the sort
    private static final long CAPTURE_READ_TIMEOUT_S = 30;
    // threads of hanging reads cannot be stopped: daemon threads, outside the read pool
    private static final ExecutorService CAPTURE_READER = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNamePrefix("captureTimeReader").setDaemon(true).build());

    /**
     * Sets the record time of all files that have none yet, in parallel. A
     * file without a readable capture time gets its modification time.
     *
     * @param progress called with the number of finished files, any thread
     */
    public void readRecordTimes(Task<?> task, List<MediaFile> files, IntConsumer progress) {
        AtomicInteger done = new AtomicInteger(0);
        // own pool: the reads block on the network share, the common
        // pool is shared with all parallel streams of the app
        ForkJoinPool readPool = new ForkJoinPool(Math.max(4, Runtime.getRuntime().availableProcessors()));
        try {
            readPool.submit(() -> files.parallelStream().forEach((mediaFile) -> {
                if (task.isCancelled() == false) {
                    if (mediaFile.getRecordTime() == null) {
                        readRecordTime(task, mediaFile);
                    }
                    if (mediaFile.getRecordTime() == null) {
                        long test_timestamp = mediaFile.getPathStorage().toFile().lastModified();
                        mediaFile.setRecordTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(test_timestamp), TimeZone.getDefault().toZoneId()));
                    }
                    progress.accept(done.incrementAndGet());
                }
            })).get();
        } catch (InterruptedException | ExecutionException ex) {
            if (task.isCancelled() == false) {
                Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, null, ex);
            }
        } finally {
            readPool.shutdownNow();
        }
    }

    /**
     * Reads the capture time of one file, at most CAPTURE_READ_TIMEOUT_S
     * seconds. A file whose read hangs keeps no record time and is logged.
     */
    private void readRecordTime(Task<?> task, MediaFile mediaFile) {
        Future<?> read = CAPTURE_READER.submit(() -> {
            // not readBasicMetadata first: it is synchronized and
            // fills the metadata view of the selected file
            if (readsCaptureTime(mediaFile)) {
                mediaFile.setRecordTime(readCaptureTime(mediaFile));
            } else {
                readBasicMetadata(task, mediaFile);
            }
            return null;
        });
        try {
            read.get(CAPTURE_READ_TIMEOUT_S, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            read.cancel(true);
            Logger.getLogger(MetadataController.class.getName()).log(Level.WARNING,
                    "Reading the capture time takes more than {0} s, using the file time: {1}",
                    new Object[]{CAPTURE_READ_TIMEOUT_S, mediaFile.getPathStorage()});
        } catch (ExecutionException ex) {
            Logger.getLogger(MetadataController.class.getName()).log(Level.FINE,
                    "Cannot read the capture time of " + mediaFile.getPathStorage(), ex.getCause());
        } catch (InterruptedException ex) {
            read.cancel(true);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Reads the metadata of the file into the MediaFile (record time, GPS,
     * camera, keywords, title, comments) and returns all metadata. Not
     * synchronized and without side effects on this controller: runs in
     * parallel with the folder loading and the search index.
     */
    public MetadataResult readMetadata(Task<?> actTask, MediaFile file) throws IOException {
        MetadataResult result = new MetadataResult();
        // isRawImage is also true for HEIF and videos: check those first
        if (file.isVideoFile()) {
            readVideoMetadata(file, result);
        } else if (file.isHEIFImage()) {
            readHeifMetadata(file, result);
        } else if (file.isRawImage()) {
            readRawMetadata(file, result);
        } else {
            readImageMetadata(actTask, file, result);
        }
        return result;
    }

    /**
     * Reads the metadata into the MediaFile only.
     */
    public void readBasicMetadata(Task<?> actTask, MediaFile file) throws java.io.EOFException, IOException {
        readMetadata(actTask, file);
    }

    private void readRawMetadata(MediaFile file, MetadataResult result) throws IOException {
        long startraw = System.currentTimeMillis();
        ConcurrentHashMap<String, RawDecoderSettings> settings = new ConcurrentHashMap<>();
        settings.put("Default", new RawDecoderSettings());
        HashMap<String, String> raw = new LibrawImage(file.getPathStorage().toString(), settings).getMetaData();
        if (raw == null) {
            return;
        }
        result.setRaw(raw);
        String timeStr = raw.get("Timestamp (EpocheSec)");
        if (timeStr != null) {
            try {
                file.setRecordTime(LocalDateTime.ofEpochSecond(Long.parseLong(timeStr.trim()), 0, ZoneOffset.UTC));
            } catch (NumberFormatException ex) {
            }
        }
        String gpsPos = raw.get("GPS Position");
        if (gpsPos != null) {
            if (!gpsPos.equalsIgnoreCase("0;0")) {
                file.setGpsPositionFromDegree(gpsPos);
            }
            double altitude = parseDouble(raw.get("GPS Altitude"));
            if (altitude != -1 && altitude != 0.0) {
                file.setGpsHeight(altitude);
            }
        }
        file.setCamera(raw.get("CameraModel"));
        Logger.getLogger(MetadataController.class.getName()).log(Level.FINE, "reading time raw: " + (System.currentTimeMillis() - startraw) + " ms");
    }

    private void readHeifMetadata(MediaFile file, MetadataResult result) throws IOException {
        long startheif = System.currentTimeMillis();
        HashMap<String, String> raw = new LibheifImage(file.getPathStorage().toString()).getMetaData();
        if (raw == null) {
            return;
        }
        result.setRaw(raw);
        String timeStr = raw.get("Date/Time Digitized");
        LocalDateTime date = null;
        if (timeStr != null) {
            try {
                date = LocalDateTime.parse(timeStr, EXIF_TIME);
            } catch (DateTimeParseException ef) {
            }
        }
        String altRaw = raw.get("GPS Altitude");
        if (altRaw != null) {
            int space = altRaw.indexOf(' ');
            double altitude = parseDouble((space > 0 ? altRaw.substring(0, space) : altRaw).replace(",", "."));
            if (altitude != -1 && altitude != 0.0) {
                file.setGpsHeight(altitude);
            }
        }
        String lon = raw.get("GPS Longitude");
        if (lon != null && !lon.equalsIgnoreCase("0;0")) {
            file.setGpsPositionFromDMS(raw.get("GPS Latitude") + raw.get("GPS Latitude Ref") + ";" + lon + raw.get("GPS Longitude Ref"));
        }
        file.setRecordTime(date);
        if (timeStr != null) {
            file.setGpsDateTime(timeStr);
        }
        file.setCamera(raw.get("Model"));
        Logger.getLogger(MetadataController.class.getName()).log(Level.FINE, "reading time heif: " + (System.currentTimeMillis() - startheif) + " ms");
    }

    private void readVideoMetadata(MediaFile file, MetadataResult result) throws IOException {
        long startvideo = System.currentTimeMillis();
        try {
            com.drew.metadata.Metadata metadata = null;
            String extension = file.getName().substring(file.getName().lastIndexOf(".") + 1);
            switch (extension.toLowerCase()) {
                case "mp4" ->
                    metadata = Mp4MetadataReader.readMetadata(file.getPathStorage().toFile());
                case "mov" ->
                    metadata = QuickTimeMetadataReader.readMetadata(file.getPathStorage().toFile());
                case "webp" ->
                    metadata = WebpMetadataReader.readMetadata(file.getPathStorage().toFile());
                case "avi" ->
                    metadata = AviMetadataReader.readMetadata(file.getPathStorage().toFile());
            }
            if (metadata == null) {
                return;
            }
            HashMap<String, String> raw = new HashMap<>();
            for (Directory directory : metadata.getDirectories()) {
                for (com.drew.metadata.Tag tag : directory.getTags()) {
                    raw.put(tag.getTagName(), tag.getDescription());
                }
            }
            result.setRaw(raw);
            // e.g. "Tue Aug 01 12:00:00 CEST 2023"
            String timeStrRaw = raw.get("Creation Time");
            if (timeStrRaw != null) {
                StringTokenizer st = new StringTokenizer(timeStrRaw, " ");
                if (st.countTokens() >= 6) {
                    st.nextToken();
                    String month = st.nextToken();
                    String day = st.nextToken();
                    String time = st.nextToken();
                    st.nextToken();
                    String year = st.nextToken();
                    try {
                        file.setRecordTime(LocalDateTime.parse(year + ":" + month + ":" + day + " " + time, VIDEO_TIME));
                    } catch (DateTimeParseException ef) {
                    }
                }
            }
            String gpsPos = raw.get("GPS Position");
            if (gpsPos != null) {
                if (!gpsPos.equalsIgnoreCase("0;0")) {
                    file.setGpsPositionFromDegree(gpsPos);
                }
                double altitude = parseDouble(raw.get("GPS Altitude"));
                if (altitude != -1 && altitude != 0.0) {
                    file.setGpsHeight(altitude);
                }
            }
            file.setCamera(raw.get("CameraModel"));
        } catch (ImageProcessingException ex) {
            Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, null, ex);
        }
        Logger.getLogger(MetadataController.class.getName()).log(Level.FINE, "reading time video: " + (System.currentTimeMillis() - startvideo) + " ms");
    }

    private void readImageMetadata(Task<?> actTask, MediaFile file, MetadataResult result) throws IOException {
        long startimage = System.currentTimeMillis();
        Map<MetadataType, Metadata> metadataMap;
        try {
            metadataMap = Metadata.readMetadata(file.getPathStorage().toFile());
        } catch (java.lang.IllegalArgumentException | java.io.EOFException e) {
            Logger.getLogger(MetadataController.class.getName()).log(Level.FINE, e.getMessage(), e);
            return;
        }
        // few small entries: sequential, the parallel streams cost more than they saved
        for (Metadata meta : metadataMap.values()) {
            if (actTask != null && actTask.isCancelled()) {
                return;
            }
            switch (meta.getType()) {
                case XMP:
                    result.setXmp((XMP) meta);
                    break;
                case COMMENT:
                    if (meta instanceof Comments comments) {
                        result.setComments(comments.getComments());
                        file.setComments(String.join("", comments.getComments()));
                    }
                    break;
                case IPTC:
                    result.setIptc((IPTC) meta);
                    for (MetadataEntry item : meta) {
                        switch (item.getKey()) {
                            case "Keywords" ->
                                file.setKeywords(item.getValue());
                            case "Caption Abstract" ->
                                file.setTitle(item.getValue());
                        }
                    }
                    break;
                case EXIF:
                    if (meta instanceof Exif exif) {
                        result.setExif(exif);
                    }
                    readExif(meta, file);
                    break;
                default:
                    break;
            }
        }
        Logger.getLogger(MetadataController.class.getName()).log(Level.FINE, "Reading time for metadata complete: " + (System.currentTimeMillis() - startimage) + " ms");
    }

    private void readExif(Metadata meta, MediaFile file) {
        for (MetadataEntry item : meta) {
            Collection<MetadataEntry> entries = item.getMetadataEntries();
            if (item.getKey().equalsIgnoreCase("EXIF SubIFD")) {
                for (MetadataEntry e : entries) {
                    if (e.getKey().equalsIgnoreCase("DateTime Digitized")) {
                        if (e.getValue().isEmpty()) {
                            file.setRecordTime(LocalDateTime.now());
                        } else {
                            try {
                                file.setRecordTime(LocalDateTime.parse(e.getValue(), EXIF_TIME));
                            } catch (DateTimeParseException ex) {
                                try {
                                    file.setRecordTime(LocalDateTime.parse(e.getValue(), DE_TIME));
                                } catch (DateTimeParseException ex2) {
                                    Logger.getLogger(MetadataController.class.getName()).log(Level.FINE, "Cannot parse the capture time " + e.getValue() + " of " + file.getName());
                                }
                            }
                        }
                    }
                }
            } else if (item.getKey().equalsIgnoreCase("GPS SubIFD")) {
                String latRef = "", lat = "", lonRef = "", lon = "", time = "", date = "", height = "";
                for (MetadataEntry e : entries) {
                    switch (e.getKey()) {
                        case "GPS Latitude Ref" ->
                            latRef = e.getValue();
                        case "GPS Latitude" ->
                            lat = e.getValue();
                        case "GPS Longitude Ref" ->
                            lonRef = e.getValue();
                        case "GPS Longitude" ->
                            lon = e.getValue();
                        case "GPS Altitude" ->
                            height = e.getValue();
                        case "GPS Time Stamp" ->
                            time = e.getValue();
                        case "GPS Date Stamp" ->
                            date = e.getValue();
                    }
                }
                if (!height.isEmpty()) {
                    // the value ends with the unit "m"
                    file.setGpsHeight(parseDecimal(height.substring(0, height.length() - 1)));
                }
                file.setGpsDateTime(date.replace(":", ".") + "T" + formatTime(time));
                file.setGpsPositionFromDMS(lat + latRef + ";" + lon + lonRef);
            } else if (item.getKey().equalsIgnoreCase("IFD0")) {
                for (MetadataEntry e : entries) {
                    if (e.getKey().equalsIgnoreCase("Model")) {
                        file.setCamera(e.getValue());
                    }
                }
            }
        }
    }

    /**
     * @return the number, -1 if it is null or no number
     */
    private static double parseDouble(String value) {
        if (value == null) {
            return -1;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    /**
     * Parses a number with a decimal comma (icafe writes e.g. "512,3") or a
     * decimal point.
     *
     * @return the number, -1 if it is no number
     */
    private static double parseDecimal(String value) {
        String s = value.trim();
        // decimal comma first, as before: "1.234,5" is 1234.5
        if (s.contains(",")) {
            s = s.replace(".", "").replace(",", ".");
        }
        return parseDouble(s);
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

    /**
     * Fills the metadata grid with actualMetadata, if not done yet. All rows
     * at once: adding them one by one lays out the grid for each row.
     */
    private void buildMetadataGrid() {
        if (!metadataGridDirty) {
            return;
        }
        metadataGridDirty = false;
        List<Node> cells = new ArrayList<>();
        AtomicInteger row = new AtomicInteger(0);
        if (actualMediaFile != null && actualMediaFile.getMediaType() == MediaFile.MediaTypes.VIDEO) {
            addMetadataRow(cells, row.getAndIncrement(), I18n.t("meta.fileName"), actualMediaFile.getName());
        }
        if (actualMetadata != null) {
            actualMetadata.forEachEntry((key, value) -> addMetadataRow(cells, row.getAndIncrement(), key, value));
        }
        metaDataGrid.getChildren().setAll(cells);
        metaDataGrid.setDisable(false);
    }

    private static void addMetadataRow(List<Node> cells, int row, String key, String value) {
        Label keyLabel = new Label(key);
        Label valueLabel = new Label(value);
        // style class instead of setStyle: no CSS parsing per label
        keyLabel.getStyleClass().add("metadata-entry");
        valueLabel.getStyleClass().add("metadata-entry");
        GridPane.setConstraints(keyLabel, 0, row);
        GridPane.setConstraints(valueLabel, 1, row);
        cells.add(keyLabel);
        cells.add(valueLabel);
    }

    private void attachSaveListeners() {
        // remove first: a listener added twice saves twice
        detachSaveListeners();
        keywordText.getChildren().addListener(keywordsChangeListener);
        captionTextField.textProperty().addListener(captionChangeListener);
        commentText.textProperty().addListener(commentsChangeListener);
    }

    private void detachSaveListeners() {
        flushPendingSaves();
        keywordText.getChildren().removeListener(keywordsChangeListener);
        captionTextField.textProperty().removeListener(captionChangeListener);
        commentText.textProperty().removeListener(commentsChangeListener);
    }

    public void resetGUI() {
        // before clearing the fields: clearing them would save the file
        detachSaveListeners();
        metaDataGrid.getChildren().clear();
        metaDataGrid.setDisable(true);
        keywordText.getChildren().clear();

        actualMetadata = null;
        metadataGridDirty = false;

        gpsDateTime.clear();
        gpsHeight.clear();
        gpsPlace.clear();
        commentText.clear();
        captionTextField.clear();
        recordDateField.clear();
        progressPane.setVisible(false);
        anchorKeywordPane.setDisable(true);
        updatingFilterSliders = true;
        exposureSlider.setValue(0);
        gainSlider.setValue(0.5);
        biasSlider.setValue(0.5);
        for (QuickParam q : quickParams) {
            q.slider().setValue(q.spec().defaults()[0]);
        }
        updatingFilterSliders = false;
    }

    @FXML
    private void addKeywordAction(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            keywordText.getChildren().add(new Tag(addKeywordTextField.getText()));
            addKeywordTextField.clear();
        }
    }

    /**
     * @param metadata the metadata of mf, from readMetadata
     */
    public void exportBasicMetadata(MediaFile mf, MetadataResult metadata, String exportFilePath, boolean overwriteGPS) throws IOException {
        if (isPng(exportFilePath)) {
            return;
        }
        // all inserts in memory, one write
        Path path = Path.of(exportFilePath);
        byte[] data = insertBasicMetadata(Files.readAllBytes(path), mf, metadata, exportFilePath, overwriteGPS);
        writeAtomically(path, data);
    }

    private byte[] insertBasicMetadata(byte[] data, MediaFile mf, MetadataResult metadata, String exportFilePath, boolean overwriteGPS) throws IOException {
        if (metadata.getIptc() == null) {
            metadata.setIptc(new IPTC());
        }
        IPTC iptcdata = metadata.getIptc();
        Map<IPTCTag, List<IPTCDataSet>> dataSets = iptcdata.getDataSets();
        List<IPTCDataSet> keywordListLocal = dataSets.get(IPTCApplicationTag.KEY_WORDS);
        if (keywordListLocal == null) {
            keywordListLocal = new ArrayList<>();
            dataSets.put(IPTCApplicationTag.KEY_WORDS, keywordListLocal);
        }
        keywordListLocal.clear();
        String mfKeyw = mf.getKeywords();
        if (mfKeyw == null) {
            mfKeyw = "";
        }
        StringTokenizer defaultTokenizer = new StringTokenizer(mfKeyw, ";");
        while (defaultTokenizer.hasMoreTokens()) {
            keywordListLocal.add(new IPTCDataSet(IPTCApplicationTag.KEY_WORDS, defaultTokenizer.nextToken()));
        }
        // one insert for all data sets; never empty (writer): an empty IPTC
        // block in a TIFF makes icafe fail on the next insert
        List<IPTCDataSet> iptcs = new ArrayList<>(keywordListLocal);
        if (mf.titleProperty().get() != null) {
            iptcs.addAll(replaceDataSet(iptcdata, IPTCApplicationTag.OBJECT_NAME, mf.titleProperty().get()));
            iptcs.addAll(replaceDataSet(iptcdata, IPTCApplicationTag.CAPTION_ABSTRACT, mf.titleProperty().get()));
        }
        iptcs.addAll(replaceDataSet(iptcdata, IPTCApplicationTag.WRITER_EDITOR, "Photoslide " + new Utility().getAppVersion() + " http://www.photoslide.org"));
        if (overwriteGPS) {
            iptcs.addAll(replaceDataSet(iptcdata, IPTCApplicationTag.CITY, mf.placeProperty().get()));
            iptcs.addAll(replaceDataSet(iptcdata, IPTCApplicationTag.SUB_LOCATION, mf.placeProperty().get()));
        }
        data = transform(data, (in, out) -> Metadata.insertIPTC(in, out, iptcs, true));

        List<String> commentsdata = metadata.getComments();
        if (commentsdata != null) {
            data = transform(data, (in, out) -> Metadata.insertComments(in, out, commentsdata));
        }
        if (overwriteGPS) {
            Exif exportExif = isJpeg(exportFilePath) ? new JpegExif() : new TiffExif();
            exportExif.addGPSField(GPSTag.GPS_LATITUDE, FieldType.RATIONAL, mf.getGpsLatPosAsRational());
            exportExif.addGPSField(GPSTag.GPS_LATITUDE_REF, FieldType.ASCII, mf.getGpsLatPosRef());
            exportExif.addGPSField(GPSTag.GPS_LONGITUDE, FieldType.RATIONAL, mf.getGpsLonPosAsRational());
            exportExif.addGPSField(GPSTag.GPS_LONGITUDE_REF, FieldType.ASCII, mf.getGpsLonPosRef());
            exportExif.addGPSField(GPSTag.GPS_ALTITUDE, FieldType.RATIONAL, mf.getGpsHeightAsRational());
            data = transform(data, (in, out) -> Metadata.insertExif(in, out, exportExif));
        }
        return data;
    }

    /**
     * Sets the value as the only one of the tag.
     *
     * @return the data sets of the tag
     */
    private static List<IPTCDataSet> replaceDataSet(IPTC iptc, IPTCApplicationTag tag, String value) {
        List<IPTCDataSet> list = iptc.getDataSet(tag);
        if (list != null) {
            list.clear();
            list.add(new IPTCDataSet(tag, value));
        } else {
            iptc.addDataSet(new IPTCDataSet(tag, value));
            list = iptc.getDataSet(tag);
        }
        return list;
    }

    /**
     * @param metadata the metadata of mf, from readMetadata
     */
    public void exportCompleteMetdata(MediaFile mf, MetadataResult metadata, String exportFilePath, String extension, boolean overwriteGPS) throws IOException {
        if (isPng(exportFilePath)) {
            return;
        }
        Path path = Path.of(exportFilePath);
        byte[] data = insertBasicMetadata(Files.readAllBytes(path), mf, metadata, exportFilePath, overwriteGPS);
        Exif exifdata = metadata.getExif();
        if (exifdata != null) {
            String ext = extension.toLowerCase();
            Exif exportExif = ext.contains("jpg") || ext.contains("jpeg") ? new JpegExif() : new TiffExif();
            exportExif.setExifIFD(exifdata.getExifIFD());
            exportExif.setGPSIFD(exifdata.getGPSIFD());
            if (!isTiff(exportFilePath)) {
                exportExif.setImageIFD(exifdata.getImageIFD());
            }
            data = transform(data, (in, out) -> Metadata.insertExif(in, out, exportExif));
        }
        XMP xmpdata = metadata.getXmp();
        if (xmpdata != null && !isJpeg(exportFilePath)) {
            data = transform(data, (in, out) -> Metadata.insertXMP(in, out, xmpdata));
        }
        writeAtomically(path, data);
    }

    @FunctionalInterface
    private interface MetadataInsert {

        void insert(InputStream in, OutputStream out) throws IOException;
    }

    /**
     * @return the image data with the metadata inserted, in memory
     */
    private static byte[] transform(byte[] data, MetadataInsert insert) throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(data.length + 64 * 1024);
        insert.insert(new ByteArrayInputStream(data), bout);
        return bout.toByteArray();
    }

    /**
     * Replaces the file with the data. Written to a temporary file next to it
     * first: an interrupted write (crash, network share gone, disk full)
     * leaves the original intact. The creation time (the lighttable sorts by
     * it), permissions and extended attributes (e.g. Finder tags) of the
     * original are kept.
     */
    static void writeAtomically(Path target, byte[] data) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        boolean keepTmp = false;
        try {
            if (!Files.exists(target)) {
                writeAndSync(tmp, data, true);
                Files.move(tmp, target);
                return;
            }
            FileTime created = Files.readAttributes(target, BasicFileAttributes.class).creationTime();
            // a copy with all attributes (on macOS also Finder tags, which the
            // Java attribute views do not see); on APFS a clone without copying data
            Files.copy(target, tmp, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
            writeAndSync(tmp, data, false);
            if (setCreationTime(tmp, created)) {
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                // the file system cannot set the creation time: overwrite the
                // original in place, which keeps it; until that succeeded the
                // temporary file stays as a complete copy
                keepTmp = true;
                writeAndSync(target, data, false);
                keepTmp = false;
            }
        } finally {
            if (keepTmp) {
                Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, "Writing {0} failed, the new content is in {1}", new Object[]{target, tmp});
            } else {
                Files.deleteIfExists(tmp);
            }
        }
    }

    private static void writeAndSync(Path path, byte[] data, boolean create) throws IOException {
        OpenOption[] options = create
                ? new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE}
                : new OpenOption[]{StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
        try (FileChannel ch = FileChannel.open(path, options)) {
            ByteBuffer buf = ByteBuffer.wrap(data);
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
            ch.force(true);
        }
    }

    /**
     * @return true if the file has the creation time now
     */
    private static boolean setCreationTime(Path path, FileTime created) {
        try {
            Files.getFileAttributeView(path, BasicFileAttributeView.class).setTimes(null, null, created);
            FileTime now = Files.readAttributes(path, BasicFileAttributes.class).creationTime();
            return now.to(TimeUnit.SECONDS) == created.to(TimeUnit.SECONDS);
        } catch (IOException | UnsupportedOperationException ex) {
            return false;
        }
    }

    /**
     * Writes title, description and keywords into the XMP embedded in the
     * JPEG: Lightroom and others prefer it to IPTC. The other XMP properties
     * (also an extended XMP) are kept.
     */
    static byte[] updateEmbeddedXmp(byte[] data, String title, String description, String keywords) throws IOException {
        XMP old = (XMP) Metadata.readMetadata(new ByteArrayInputStream(data)).get(MetadataType.XMP);
        String xml;
        try {
            XMPMeta meta = old != null && old.getData() != null
                    ? XMPMetaFactory.parseFromBuffer(old.getData())
                    : XMPMetaFactory.create();
            putXmpText(meta, "title", title);
            putXmpText(meta, "description", description);
            meta.deleteProperty(XMPConst.NS_DC, "subject");
            if (keywords != null) {
                StringTokenizer t = new StringTokenizer(keywords, ";");
                while (t.hasMoreTokens()) {
                    String k = t.nextToken().trim();
                    if (!k.isEmpty()) {
                        meta.appendArrayItem(XMPConst.NS_DC, "subject", new PropertyOptions().setArray(true), k, null);
                    }
                }
            }
            // compact: the main XMP of a JPEG must fit into one 64 KB segment
            xml = XMPMetaFactory.serializeToString(meta, new SerializeOptions().setUseCompactFormat(true).setPadding(0));
        } catch (XMPException ex) {
            throw new IOException("Cannot update the XMP", ex);
        }
        XMP xmp = old != null && old.hasExtendedXmp()
                ? new JpegXMP(xml, new String(old.getExtendedXmpData(), StandardCharsets.UTF_8))
                : new JpegXMP(xml);
        return transform(data, (in, out) -> Metadata.insertXMP(in, out, xmp));
    }

    private static void putXmpText(XMPMeta meta, String name, String value) throws XMPException {
        if (value == null || value.isEmpty()) {
            meta.deleteProperty(XMPConst.NS_DC, name);
        } else {
            meta.setLocalizedText(XMPConst.NS_DC, name, "", XMPConst.X_DEFAULT, value);
        }
    }

    private static boolean isJpeg(String path) {
        String p = path.toLowerCase();
        return p.endsWith(".jpg") || p.endsWith(".jpeg");
    }

    private static boolean isTiff(String path) {
        String p = path.toLowerCase();
        return p.endsWith(".tif") || p.endsWith(".tiff");
    }

    private static boolean isPng(String path) {
        return path.toLowerCase().endsWith(".png");
    }

    /**
     * Saves the GPS position stored in the MediaFile: into the XMP sidecar
     * for RAW, HEIF and videos, else into the EXIF of the file.
     *
     * @throws IOException if the file cannot be accessed
     */
    private void saveGPSData(MediaFile file) throws IOException {
        if (file.isRawImage()) {
            // never write into the RAW itself
            file.saveEdits();
            return;
        }
        Exif exportExif = isJpeg(file.getName()) ? new JpegExif() : new TiffExif();
        exportExif.addGPSField(GPSTag.GPS_LATITUDE, FieldType.RATIONAL, file.getGpsLatPosAsRational());
        exportExif.addGPSField(GPSTag.GPS_LATITUDE_REF, FieldType.ASCII, file.getGpsLatPosRef());
        exportExif.addGPSField(GPSTag.GPS_LONGITUDE, FieldType.RATIONAL, file.getGpsLonPosAsRational());
        exportExif.addGPSField(GPSTag.GPS_LONGITUDE_REF, FieldType.ASCII, file.getGpsLonPosRef());
        exportExif.addGPSField(GPSTag.GPS_ALTITUDE, FieldType.RATIONAL, file.getGpsHeightAsRational());
        Path path = file.getPathStorage();
        writeAtomically(path, transform(Files.readAllBytes(path), (in, out) -> Metadata.insertExif(in, out, exportExif, true)));
    }

    /**
     * Saves the comment: into the XMP sidecar for RAW, HEIF and videos, else
     * into the file. JavaFX thread.
     */
    private void saveComments(MediaFile file) {
        // read on the JavaFX thread: the task may run after another file was selected
        final String comment = commentText.getText();
        // also for RAW: saveEdits writes it from the MediaFile
        file.setComments(comment);
        final String title = file.getTitle().get();
        final String keywords = file.getKeywords();
        progressPane.setVisible(true);
        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressLabel.setText(I18n.t("meta.savingComments"));
        Task<Boolean> taskSaveComments = new Task<>() {
            @Override
            protected Boolean call() {
                if (file.isRawImage()) {
                    file.saveEdits();
                    return true;
                }
                try {
                    Path path = file.getPathStorage();
                    byte[] data = transform(Files.readAllBytes(path), (in, out) -> Metadata.insertComment(in, out, comment));
                    if (isJpeg(file.getName())) {
                        data = updateEmbeddedXmp(data, title, comment, keywords);
                    }
                    writeAtomically(path, data);
                } catch (IOException ex) {
                    Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, "Cannot save the comment of " + file.getPathStorage(), ex);
                }
                return null;
            }
        };
        taskSaveComments.setOnScheduled((t) -> {
            hideSaveProgress();
        });
        taskSaveComments.setOnFailed((t) -> {
            Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, "Cannot save the comment of " + file.getPathStorage(), t.getSource().getException());
            hideSaveProgress();
        });
        submitWrite(file, taskSaveComments);
        mainController.getTaskProgressView().getTasks().add(taskSaveComments);
    }

    /**
     * Queues a save of the file. All saves run one after the other in the
     * order queued.
     */
    private void submitWrite(MediaFile file, Runnable write) {
        FutureTask<Void> future = new FutureTask<>(write, null) {
            @Override
            protected void done() {
                pendingWrites.remove(file, this);
            }
        };
        pendingWrites.put(file, future);
        executor.execute(future);
    }

    /**
     * Waits until the saves of the file queued so far are written: reading it
     * earlier shows (and puts into the MediaFile) the old values.
     *
     * @return false if interrupted (the loading was cancelled)
     */
    private boolean awaitPendingWrite(MediaFile file) {
        Future<?> write = pendingWrites.get(file);
        if (write == null) {
            return true;
        }
        try {
            write.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | CancellationException ex) {
            // logged by the save, the file is read as it is
        }
        return true;
    }

    /**
     * Hides the progress of a save, unless the metadata of the selected file
     * are still loading (same progress pane). JavaFX thread.
     */
    private void hideSaveProgress() {
        if (task == null || task.isDone()) {
            progressPane.setVisible(false);
        }
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
     * Saves the keywords and the title to the file. JavaFX thread.
     */
    private void saveKeywordsTitle(MediaFile file) {
        // read on the JavaFX thread: the task may run after another file was selected
        final String title = captionTextField.getText() == null ? "" : captionTextField.getText();
        final String keywords = getKeywordsAsString(keywordText);
        // also for RAW: saveEdits writes them from the MediaFile
        file.setKeywords(keywords);
        file.setTitle(title);
        final String description = file.getComments().get();
        if (actualMetadata == null) {
            actualMetadata = new MetadataResult();
        }
        if (actualMetadata.getIptc() == null) {
            actualMetadata.setIptc(new IPTC());
        }
        final IPTC iptc = actualMetadata.getIptc();
        progressPane.setVisible(true);
        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        progressLabel.setText(I18n.t("meta.savingKeywords"));
        Task<Boolean> taskKeywordsTitle = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                if (this.isCancelled() == false) {
                    updateKeywordsTitle(file, iptc, title, keywords, description);
                }
                return null;
            }
        };
        taskKeywordsTitle.setOnSucceeded((t) -> {
            hideSaveProgress();
        });
        taskKeywordsTitle.setOnFailed((t) -> {
            Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, "Cannot save the keywords of " + file.getPathStorage(), t.getSource().getException());
            hideSaveProgress();
        });
        submitWrite(file, taskKeywordsTitle);
        mainController.getTaskProgressView().getTasks().add(taskKeywordsTitle);
    }

    /**
     * Saves the keywords and the title to the file: into the XMP sidecar for
     * RAW, HEIF and videos (from the MediaFile, which must have the values
     * already), else into the IPTC of the file.
     *
     * @param iptcdata the IPTC metadata of the file, gets the new values
     * @param description the comment, for the XMP embedded in a JPEG
     */
    private void updateKeywordsTitle(MediaFile file, IPTC iptcdata, String title, String keywords, String description) {
        if (file.isRawImage()) {
            file.saveEdits();
            return;
        }
        if (title == null) {
            title = "";
        }
        if (keywords == null) {
            keywords = "";
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
        replaceDataSet(iptcdata, IPTCApplicationTag.OBJECT_NAME, title);
        replaceDataSet(iptcdata, IPTCApplicationTag.CAPTION_ABSTRACT, title);

        List<IPTCDataSet> iptcs = new ArrayList<>();
        dataSets.values().forEach(iptcs::addAll);
        try {
            Path path = file.getPathStorage();
            byte[] data = transform(Files.readAllBytes(path), (in, out) -> Metadata.insertIPTC(in, out, iptcs, false));
            if (isJpeg(file.getName())) {
                data = updateEmbeddedXmp(data, title, description, keywords);
            }
            writeAtomically(path, data);
        } catch (IOException ex) {
            Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, "Cannot save the keywords of " + file.getPathStorage(), ex);
        }
    }

    /**
     * Runs on the JavaFX thread and waits for it.
     */
    private static void runOnFxThread(Runnable r) throws InterruptedException {
        if (Platform.isFxApplicationThread()) {
            r.run();
            return;
        }
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } finally {
                done.countDown();
            }
        });
        done.await();
    }

    @FXML
    private void applyKeywordsToAllAction(ActionEvent event) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, I18n.t("meta.applyAllQuestion", this.actualMediaFile.getPathStorage()), ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(I18n.t("common.confirmation"));
        alert.setHeaderText(I18n.t("meta.applyAllHeader"));
        FontIcon ft = new FontIcon("ti-pencil-alt:50");
        alert.setGraphic(ft);
        DialogPane dialogPane = alert.getDialogPane();
        alert.setResizable(true);
        GridPane content = new GridPane();
        content.setAlignment(Pos.CENTER_RIGHT);
        content.setHgap(5);
        content.setVgap(5);
        Label title = new Label(I18n.t("meta.titleCaption"));
        TextField titleText = new TextField(captionTextField.getText());
        titleText.setPrefWidth(300);
        HBox.setHgrow(titleText, Priority.ALWAYS);
        content.addRow(0, title, titleText, new Pane());
        Label keywordLabel = new Label(I18n.t("browserMetadata.keywords"));
        ScrollPane scrollpane = new ScrollPane();
        scrollpane.setPrefSize(300, 150);
        HBox.setHgrow(scrollpane, Priority.NEVER);
        VBox.setVgrow(scrollpane, Priority.ALWAYS);
        FlowPane keywordsToAllText = new FlowPane();
        keywordsToAllText.setVgap(5);
        keywordsToAllText.setHgap(5);
        keywordsToAllText.setPadding(new Insets(5, 5, 5, 5));
        scrollpane.setFitToWidth(true);
        scrollpane.setContent(keywordsToAllText);
        // copies: moving the tags would remove them from keywordText and save the file without keywords
        keywordText.getChildren().forEach((tag) -> keywordsToAllText.getChildren().add(new Tag(((Tag) tag).getText())));
        content.addRow(1, keywordLabel, scrollpane, new Pane());
        TextField addKeywordToAllField = new TextField();
        addKeywordToAllField.setPrefWidth(300);
        HBox.setHgrow(addKeywordToAllField, Priority.ALWAYS);
        addKeywordToAllField.setPromptText(I18n.t("browserMetadata.addKeywords"));
        addKeywordToAllField.setOnKeyPressed((t) -> {
            if (t.getCode().equals(KeyCode.ENTER)) {
                keywordsToAllText.getChildren().add(new Tag(addKeywordToAllField.getText()));
                addKeywordToAllField.clear();
                addKeywordToAllField.requestFocus();
                t.consume();
            }
        });
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
            mainController.getProgressbarLabel().setText(I18n.t("meta.settingMetadata"));
            mainController.getStatusLabelLeft().setText(I18n.t("meta.settingMetadataShort"));
            mainController.getStatusLabelLeft().setVisible(true);

            final String allTitle = titleText.getText();
            final String allKeywords = getKeywordsAsString(keywordsToAllText);
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
                                // saveEdits writes the values of the MediaFile
                                runOnFxThread(() -> {
                                    actFile.setKeywords(allKeywords);
                                    actFile.setTitle(allTitle);
                                });
                                actFile.saveEdits();
                            } else {
                                if (actFile.getMediaType() == MediaTypes.IMAGE) {
                                    MetadataResult metadata = readMetadata(this, actFile);
                                    // after the read: it sets the old values (later on the JavaFX thread)
                                    runOnFxThread(() -> {
                                        actFile.setKeywords(allKeywords);
                                        actFile.setTitle(allTitle);
                                    });
                                    IPTC iptc = metadata.getIptc() != null ? metadata.getIptc() : new IPTC();
                                    updateKeywordsTitle(actFile, iptc, allTitle, allKeywords, actFile.getComments().get());
                                }
                            }
                            i++;
                        }
                    }
                    return null;
                }
            };
            taskApplyToAll.setOnScheduled((t) -> {
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressbar().progressProperty().bind(taskApplyToAll.progressProperty());
                mainController.getProgressbarLabel().textProperty().unbind();
                mainController.getProgressbarLabel().textProperty().bind(taskApplyToAll.messageProperty());
            });
            taskApplyToAll.setOnSucceeded((t) -> {
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressbarLabel().textProperty().unbind();
                mainController.getProgressbarLabel().setText("");
                mainController.getProgressPane().setVisible(false);
                mainController.getStatusLabelLeft().setVisible(false);
                actualMediaFile = lightController.getFactory().getSelectedCell().getItem();
                setSelectedFile(actualMediaFile);
            });
            taskApplyToAll.setOnFailed((t) -> {
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressbarLabel().textProperty().unbind();
                mainController.getProgressPane().setVisible(false);
                mainController.getStatusLabelLeft().setVisible(false);
            });
            executor.submit(taskApplyToAll);
            mainController.getTaskProgressView().getTasks().add(taskApplyToAll);
        }
    }

    public void saveSettings() {
    }

    public void restoreSettings() {
    }

    @FXML
    private void resetExposureAction(ActionEvent event) {
        resetFilter(ExposureFilter.NAME);
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
        Button saveAs = new Button(I18n.t("meta.saveMap"));
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
                    message.setText(I18n.t("meta.saveOk"));
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, null, ex);
                    message.setText(I18n.t("meta.saveError"));
                } catch (Exception ex) {
                    Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, null, ex);
                    message.setText(I18n.t("meta.saveError"));
                }
                util.hideNodeAfterTime(message, 3, false);
            });
        });
        saveAs.setId("toolbutton");
        saveAs.setGraphic(new FontIcon("ti-save:16"));
        Button clipboardButton = new Button(I18n.t("meta.copyGps"));
        clipboardButton.setOnAction((t) -> {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(actualMediaFile.getGpsLatPosAsDouble() + "," + actualMediaFile.getGpsLonPosAsDouble());
            clipboard.setContent(content);
            message.setVisible(true);
            message.setManaged(true);
            message.setText(I18n.t("meta.copiedGps"));
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
        MapPoint c = new MapPoint(lat, lon);
        map.setCenter(c);
        MarkerLayer markerPos = new MarkerLayer();
        markerPos.addFlag(c);
        map.addLayer(markerPos);
        popOver.setOnHidden((e) -> map.removeLayer(markerPos));
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

    @FXML
    private void changeGPSPosAction(ActionEvent event) {
        if (actualMediaFile == null) {
            return;
        }
        Utility util = new Utility();
        // the position chosen on the map or by the search, null if none yet
        final MapPoint[] chosen = new MapPoint[1];
        MarkerLayer markerPos = new MarkerLayer();
        PopOver popOver = new PopOver();
        popOver.setArrowLocation(PopOver.ArrowLocation.TOP_RIGHT);
        popOver.setTitle(I18n.t("meta.changeGps"));
        popOver.setAnimated(true);
        popOver.setHeaderAlwaysVisible(true);
        Label message = new Label("");
        message.setVisible(false);
        message.setManaged(false);
        VBox vb = new VBox();
        vb.setPadding(new Insets(10));
        vb.setSpacing(5);
        popOver.setContentNode(vb);
        popOver.show(gpsPosButton);
        HBox hb = new HBox();
        hb.setAlignment(Pos.CENTER_LEFT);
        hb.setSpacing(5);
        CustomTextField searchField = new CustomTextField();
        HBox.setHgrow(searchField, Priority.ALWAYS);
        Button searchButton = new Button();
        searchButton.setGraphic(new FontIcon("ti-search:16"));
        searchButton.setId("toolbutton");
        searchButton.setOnAction((t) -> {
            searchButtonAction(searchField, (point) -> {
                chosen[0] = point;
                map.setCenter(point);
                markerPos.removeAllPoints();
                markerPos.addFlag(point);
            });
        });
        searchField.setOnKeyPressed((p) -> {
            event.consume();
        });
        Button saveButton = new Button();
        saveButton.setGraphic(new FontIcon("ti-save:20"));
        saveButton.setId("toolbutton");
        CustomTextField heightField = new CustomTextField();
        if (actualMediaFile.getGpsHeight() != -1) {
            heightField.setText("" + actualMediaFile.getGpsHeight());
        }
        saveButton.setOnAction((q) -> {
            final MediaFile file = actualMediaFile;
            String heightStr = heightField.getText() == null ? "" : heightField.getText().replace("m", "").replace(",", ".").trim();
            double height = -1;
            if (!heightStr.isEmpty()) {
                try {
                    height = Double.parseDouble(heightStr);
                } catch (NumberFormatException ex) {
                    showGPSMessage(util, message, I18n.t("meta.invalidHeight", heightField.getText()));
                    return;
                }
            }
            if (chosen[0] == null && !file.hasGpsPosition()) {
                showGPSMessage(util, message, I18n.t("meta.choosePosition"));
                return;
            }
            // into the MediaFile first: saveGPSData and the XMP sidecar write its values
            if (chosen[0] != null) {
                file.setGpsPositionFromDegree(chosen[0].getLatitude() + ";" + chosen[0].getLongitude());
                if (searchField.getText() != null && !searchField.getText().isBlank()) {
                    file.placeProperty().set(searchField.getText());
                }
            }
            file.setGpsHeight(height);
            gpsPlace.setText(file.getGpsPosition());
            if (file.getGpsHeight() != -1) {
                gpsHeight.setText("" + file.getGpsHeight() + " m");
            }
            // not on the JavaFX thread: rewrites the whole file
            submitWrite(file, () -> {
                try {
                    saveGPSData(file);
                    Platform.runLater(() -> showGPSMessage(util, message, I18n.t("meta.gpsSaved")));
                } catch (IOException ex) {
                    Logger.getLogger(MetadataController.class.getName()).log(Level.SEVERE, "Cannot save the GPS position of " + file.getPathStorage(), ex);
                    Platform.runLater(() -> showGPSMessage(util, message, I18n.t("meta.gpsSaveError")));
                }
            });
        });
        hb.getChildren().add(new Label(I18n.t("meta.place")));
        hb.getChildren().add(searchField);
        hb.getChildren().add(searchButton);
        hb.getChildren().add(new Label(I18n.t("meta.height")));
        heightField.setPrefWidth(80);
        heightField.setMinWidth(80);
        hb.getChildren().add(heightField);
        HBox hbSaveBox = new HBox();
        hbSaveBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(hbSaveBox, Priority.ALWAYS);
        hbSaveBox.getChildren().add(saveButton);
        vb.getChildren().add(hbSaveBox);
        vb.getChildren().add(hb);
        if (actualMediaFile.hasGpsPosition()) {
            MapPoint c = new MapPoint(actualMediaFile.getGpsLatPosAsDouble(), actualMediaFile.getGpsLonPosAsDouble());
            map.setZoom(19);
            map.setCenter(c);
            markerPos.addFlag(c);
        } else {
            // no position yet: the whole world, to choose one
            map.setZoom(2);
            map.setCenter(new MapPoint(30, 0));
        }
        map.addLayer(markerPos);
        // the map is shared: without this, markers of earlier files stay on it
        popOver.setOnHidden((e) -> {
            map.removeLayer(markerPos);
            map.setOnMouseClicked(null);
        });
        map.setEffect(new ColorAdjust(0, -0.5, 0, 0));
        vb.getChildren().add(map);
        vb.getChildren().add(message);
        ((Parent) popOver.getSkin().getNode()).getStylesheets()
                .add(getClass().getResource("/org/photoslide/css/PopOver.css").toExternalForm());
        map.setOnMouseClicked((t) -> {
            t.getPickResult();
            final MapPoint newPosition = map.getMapPosition(t.getSceneX(), t.getSceneY());
            chosen[0] = newPosition;
            markerPos.removeAllPoints();
            markerPos.addFlag(newPosition);
            Task<Address> taskFind = new Task<>() {
                @Override
                protected Address call() throws Exception {
                    return geoCoding.geoSearchForGPS(newPosition.getLongitude(), newPosition.getLatitude());
                }
            };
            taskFind.setOnSucceeded((k) -> {
                searchField.setText(((Address) k.getSource().getValue()).getDisplayName());
            });
            new Thread(taskFind).start();
        });
    }

    private void showGPSMessage(Utility util, Label message, String text) {
        message.setText(text);
        message.setVisible(true);
        message.setManaged(true);
        util.hideNodeAfterTime(message, 3, false);
    }

    /**
     * @param onSelected called with the position of the place the user
     * selected from the found ones
     */
    private void searchButtonAction(CustomTextField customField, Consumer<MapPoint> onSelected) {
        String name = customField.getText();

        Task<ObservableList<String>> task = new Task<>() {
            @Override
            protected ObservableList<String> call() throws Exception {
                return geoCoding.geoSearchForNameAsStrings(name);
            }
        };
        task.setOnScheduled((t) -> {
            ProgressIndicator ind = new ProgressIndicator();
            ind.setPrefSize(10, 10);
            ind.setMinSize(10, 10);
            ind.setMaxSize(10, 10);
            customField.setRight(ind);
        });
        task.setOnSucceeded((t) -> {
            customField.setRight(null);
            ObservableList<String> geoSearchForNameAsStrings = (ObservableList<String>) t.getSource().getValue();
            //set autocomplete
            AutoCompletionBinding<String> autoComp = TextFields.bindAutoCompletion(customField, geoSearchForNameAsStrings);
            customField.setText(geoSearchForNameAsStrings.get(0));
            customField.requestFocus();
            autoComp.setUserInput(name);
            autoComp.setOnAutoCompleted((o) -> {
                int index = geoSearchForNameAsStrings.indexOf(customField.getText());
                if (index == -1) {
                    index = 0;
                }
                Address found = geoCoding.getLastSearchResult().get(index);
                geoCoding.setLastSearchGPSResult(found);
                onSelected.accept(new MapPoint(found.getLatitude(), found.getLongitude()));

            });
        });
        task.setOnFailed((t) -> {
            customField.setRight(null);
        });
        new Thread(task).start();
    }

    @FXML
    private void resetGainFilterAction(ActionEvent event) {
        resetFilter(GainFilter.NAME);
    }

    @FXML
    private void resetWhiteBalanceAction(ActionEvent event) {
        resetFilter(WhiteBalanceFilter.NAME);
    }

    @FXML
    private void resetSaturationAction(ActionEvent event) {
        resetFilter(HueSaturationFilter.NAME);
    }

    @FXML
    private void resetRelightAction(ActionEvent event) {
        resetFilter(RelightFilter.NAME);
    }

    /**
     * Binds a quick develop slider to one parameter of an editor tool: range
     * and default come from the tool, moving the slider adds the tool to the
     * file (with defaults for its other values) or changes only this value.
     */
    private void bindQuickParam(Slider slider, TextField field, String filterName,
            Supplier<? extends AbstractImageFilter> factory, String key, String format) {
        AbstractImageFilter defaults = factory.get();
        ParamSpec spec = defaults.params().stream()
                .filter(p -> p.key().equals(key))
                .findFirst()
                .orElseThrow();
        slider.setMin(spec.min());
        slider.setMax(spec.max());
        slider.setBlockIncrement(spec.step());
        slider.setValue(spec.defaults()[0]);
        field.textProperty().bindBidirectional(slider.valueProperty(), new NumberStringConverter(format));
        slider.valueProperty().addListener((o) -> {
            if (updatingFilterSliders || actualMediaFile == null) {
                return;
            }
            ImageFilter existing = actualMediaFile.getFilterForName(filterName);
            float[] values = (existing != null ? existing : defaults).getValues();
            values[spec.index()] = (float) slider.getValue();
            EditPipeline.setFilterValues(actualMediaFile, filterName, factory::get, values);
            EditPipeline.filtersChanged(actualMediaFile);
        });
        quickParams.add(new QuickParam(slider, filterName, spec));
        alignLabelToTrack(slider);
    }

    /**
     * Indents the label above a quick develop slider so it starts where the
     * track starts (the skin insets the track by half a thumb).
     */
    private static void alignLabelToTrack(Slider slider) {
        if (!(slider.getParent() instanceof HBox row) || !(row.getParent() instanceof VBox box)
                || !(box.getChildren().get(0) instanceof Label label)) {
            return;
        }
        slider.skinProperty().addListener((o, oldSkin, skin) -> {
            Node track = slider.lookup(".track");
            if (track == null) {
                return;
            }
            label.paddingProperty().bind(Bindings.createObjectBinding(
                    () -> new Insets(0, 0, 0, row.getPadding().getLeft() + track.getBoundsInParent().getMinX()),
                    track.boundsInParentProperty()));
        });
    }

    private record QuickParam(Slider slider, String filterName, ParamSpec spec) {
    }

    private void resetFilter(String name) {
        if (actualMediaFile == null) {
            return;
        }
        ImageFilter filter = actualMediaFile.getFilterForName(name);
        if (filter != null) {
            actualMediaFile.removeImageFilter(filter);
        }
        showFilterValues(actualMediaFile);
        EditPipeline.filtersChanged(actualMediaFile);
    }

    /**
     * Shows the filter settings of the file in the sliders without changing
     * the file.
     */
    public void showFilterValues(MediaFile file) {
        ImageFilter exposure = file.getFilterForName(ExposureFilter.NAME);
        ImageFilter gain = file.getFilterForName(GainFilter.NAME);
        updatingFilterSliders = true;
        try {
            exposureSlider.setValue(exposure != null ? exposure.getValues()[0] : 0);
            gainSlider.setValue(gain != null ? gain.getValues()[0] : 0.5);
            biasSlider.setValue(gain != null ? gain.getValues()[1] : 0.5);
            for (QuickParam q : quickParams) {
                ImageFilter f = file.getFilterForName(q.filterName());
                q.slider().setValue(f != null ? f.getValues()[q.spec().index()] : q.spec().defaults()[0]);
            }
        } finally {
            updatingFilterSliders = false;
        }
    }

    private class KeywordChangeListener implements ListChangeListener {

        @Override
        public void onChanged(Change change) {
            // saves the caption too
            captionSaveFile = null;
            captionSaveDelay.stop();
            saveKeywordsTitle(actualMediaFile);
        }

    }

    private class CommentsChangeListener implements ChangeListener<String> {

        @Override
        public void changed(ObservableValue<? extends String> ov, String t, String t1) {
            commentSaveFile = actualMediaFile;
            commentSaveDelay.playFromStart();
        }

    }

    private class CaptionChangeListener implements ChangeListener<String> {

        @Override
        public void changed(ObservableValue<? extends String> ov, String t, String t1) {
            captionSaveFile = actualMediaFile;
            captionSaveDelay.playFromStart();
        }

    }

    /**
     * Saves the caption and the comment typed in the last SAVE_DELAY now:
     * before the fields are cleared or another file is shown. JavaFX thread.
     */
    private void flushPendingSaves() {
        if (captionSaveFile != null) {
            MediaFile file = captionSaveFile;
            captionSaveFile = null;
            captionSaveDelay.stop();
            saveKeywordsTitle(file);
        }
        if (commentSaveFile != null) {
            MediaFile file = commentSaveFile;
            commentSaveFile = null;
            commentSaveDelay.stop();
            saveComments(file);
        }
    }

    public TextField getRecordDateField() {
        return recordDateField;
    }

    public void cancelTasks() {
        if (task != null) {
            detachSaveListeners();
            progressPane.setVisible(false);
            task.cancel();
        }
    }

    public Slider getApertureSlider() {
        return exposureSlider;
    }

    /**
     * @return the metadata shown for the file, null if it is not the selected
     * file or still loading
     */
    public MetadataResult getMetadata(MediaFile file) {
        return file == actualMediaFile ? actualMetadata : null;
    }

    public Slider getGainSlider() {
        return gainSlider;
    }

    public Slider getBiasSlider() {
        return biasSlider;
    }

    public void Shutdown() {
        if (task != null) {
            task.cancel();
        }
        if (Platform.isFxApplicationThread()) {
            flushPendingSaves();
        }
        if (executor != null) {
            // queued saves are written, not dropped
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (executorParallel != null) {
            executorParallel.shutdownNow();
        }
    }
}
