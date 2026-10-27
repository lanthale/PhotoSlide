package org.photoslide.preferences;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.Property;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.librawdecodersettings.RawDecoderSettings;
import org.librawfx.RAWImageLoaderFactory;
import org.librawjava.LibrawImage;
import org.photoslide.datamodel.FxImage16;
import org.photoslide.datamodel.Image16;

/**
 * Controller des RAW Settings Lab: Beispielbild laden, Einstellungen live
 * ausprobieren (mit Referenzvergleich) und pro Kameramodell speichern.
 *
 * Thread-Safety: Die Vorschau laeuft ueber setSettingsOverride() und beruehrt
 * die globale Map nicht. Erst "Save" tauscht ein komplett konfiguriertes
 * RawDecoderSettings-Objekt in RAWImageLoaderFactory.getDecoderSettings() aus.
 */
public class RawSettingsLabController implements Initializable {

    private static final Logger LOG = Logger.getLogger(RawSettingsLabController.class.getName());
    // Nur Werte, die RawDecoderToNativeTranslator tatsaechlich verarbeitet:
    // user_qual: 0=linear, 1=VNG, 2=PPG, 3=AHD, 4=DCB, 11=DHT, 12=mod. AHD
    private static final List<String> QUALITY_VALUES = List.of("Linear (0)", "VNG (1)", "PPG (2)",
            "AHD (3)", "DCB (4)", "DHT (11)", "Modified AHD (12)");
    // output_color: 0=raw, 1=sRGB, 2=Adobe, 3=Wide, 4=ProPhoto, 5=XYZ, 6=ACES
    private static final List<String> COLOR_SPACE_VALUES = List.of("Raw (0)", "sRGB (1)", "Adobe (2)",
            "Wide (3)", "ProPhoto (4)", "XYZ (5)", "ACES (6)");
    // fbdd_noiserd: 0=off, 1..3 (LibRaw)
    private static final List<String> NOISE_VALUES = List.of("Off (0)", "FBDD light (1)", "FBDD strong (2)", "FBDD strongest (3)");

    @FXML private Label fileLabel;
    @FXML private Label cameraLabel;
    @FXML private ListView<String> profileList;
    @FXML private Button addProfileButton;
    @FXML private Button deleteProfileButton;
    @FXML private Button applyButton;
    @FXML private Button resetButton;
    @FXML private Button saveButton;
    @FXML private Label statusLabel;
    @FXML private ImageView referenceView;
    @FXML private ImageView previewView;
    @FXML private ProgressIndicator referenceProgress;
    @FXML private ProgressIndicator previewProgress;
    @FXML private javafx.scene.control.ScrollPane referenceScrollPane;
    @FXML private javafx.scene.control.ScrollPane previewScrollPane;
    @FXML private org.controlsfx.control.ToggleSwitch zoomSwitch;
    @FXML private HBox imageBox;
    @FXML private VBox settingsBox;
    @FXML private TreeView<String> settingsTree;
    @FXML private VBox rawLabRoot;
    @FXML private StackPane generalPane;

    private static final String TREE_RAW = "RAW Developer Settings";
    private static final String TREE_GENERAL = "General";

    private final RawDecoderSettingsFx model = new RawDecoderSettingsFx();
    private final javafx.beans.property.SimpleStringProperty cameraKey
            = new javafx.beans.property.SimpleStringProperty(this, "cameraKey", null);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "raw-settings-lab");
        t.setDaemon(true);
        return t;
    });
    private Path sampleFile;
    /** Verhindert Rekursion, wenn die Liste programmatisch neu geladen wird. */
    private boolean updatingSelection;
    /** Entprellung fuer Live-Preview: aendert sich ein Setting, wartet der Timer
     *  kurz (Slider-Drag feuert permanent) und dekodiert dann EINMAL neu. */
    private final javafx.animation.PauseTransition autoApplyTimer
            = new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
    /** true waehrend applyProfile() das Modell programmatisch laedt — die
     *  Live-Listener sollen dann nicht zusaetzlich feuern (applyProfile dekodiert selbst). */
    private boolean applyingProfile;
    /** Anzahl aktuell laufender/gequeueteter Decode-Tasks pro ProgressIndicator-Slot.
     *  Nur wenn ALLE fertig sind, darf der Indikator versteckt werden — sonst
     *  wuerde der Hide-Timer eines schnellen frueheren Tasks den Indikator
     *  verstecken, waehrend ein spaeterer Task noch laeuft. */
    private final java.util.Map<ProgressIndicator, java.util.concurrent.atomic.AtomicInteger> pendingDecodes
            = new java.util.concurrent.ConcurrentHashMap<>();

    /** Selektiert ein Profil und aktiviert es sofort (Model laden + Preview dekodieren). */
    private void selectAndApplyProfile(String key) {
        updatingSelection = true;
        profileList.getItems().setAll(RawSettingsStore.listProfileNames()); // Liste auffrischen (neu/geoelscht)
        profileList.getSelectionModel().select(key);
        updatingSelection = false;
        applyProfile(key); // auch aufrufen, wenn die Selektion sich nicht geaendert hat
    }

    /** Laedt ein Profil ins Model und dekodiert die Vorschau sofort neu. */
    private void applyProfile(String key) {
        RawDecoderSettingsFx profile = RawSettingsStore.loadProfile(key);
        applyingProfile = true;
        try {
            model.fromRawDecoderSettings(profile.toRawDecoderSettings());
        } finally {
            applyingProfile = false;
        }
        statusLabel.setText("Profile '" + key + "' loaded");
        // Das geladene Bild gehoert zu genau einem Kamera-Profil. Klick auf ein
        // anderes Profil => Bild gehoert nicht mehr dazu: verwerfen statt
        // es mit fremden Settings weiter anzuzeigen.
        boolean imageBelongsToProfile = cameraKey.get() != null
                && cameraKey.get().equals(key)
                && sampleFile != null;
        if (!imageBelongsToProfile) {
            clearSampleImage();
            return;
        }
        imageBox.setVisible(true);
        imageBox.setManaged(true);
        // Vorschau sofort neu dekodieren (Referenz bleibt Default-profil zum Vergleich)
        decodePreview();
    }

    /** Verwirft das geladene Beispielbild: Views leeren, Leiste deselektieren. */
    private void clearSampleImage() {
        sampleFile = null;
        cameraKey.set(null);
        fileLabel.setText("No image loaded");
        cameraLabel.setText("—");
        referenceView.setImage(null);
        previewView.setImage(null);
        referenceProgress.setVisible(false);
        previewProgress.setVisible(false);
        // laufende/gequeuete Decodes entwertet: Indikator-Zaehler zuruecksetzen
        pendingDecodes.values().forEach(c -> c.set(0));
        imageBox.setVisible(false);
        imageBox.setManaged(false);
    }

    /** Live-Preview: 400 ms nach der letzten Aenderung neu dekodieren.
     *  Slider-Drags feuern in schneller Folge — der Timer wird dabei immer nur
     *  neu gestartet, erst wenn der User aufhoert, laeuft er einmal ab. */
    private void scheduleAutoApply() {
        if (applyingProfile || sampleFile == null || cameraKey.get() == null) {
            return;
        }
        autoApplyTimer.playFromStart();
    }

    /** Laedt alle gespeicherten Profilnamen in die Liste und waehlt eines aus. */
    private void refreshProfileList(String keyToSelect) {
        updatingSelection = true;
        profileList.getItems().setAll(RawSettingsStore.listProfileNames());
        if (keyToSelect != null) {
            profileList.getSelectionModel().select(keyToSelect);
        } else if (!profileList.getItems().isEmpty()) {
            profileList.getSelectionModel().selectFirst();
        }
        updatingSelection = false;
    }

    @Override
    public void initialize(java.net.URL url, java.util.ResourceBundle rb) {
        buildTree();
        // Profil-Verwaltung: Klick auf Profil => Settings sofort aktiv
        profileList.getSelectionModel().selectedItemProperty().addListener((obs, oldKey, newKey) -> {
            if (updatingSelection || newKey == null) {
                return;
            }
            applyProfile(newKey);
        });
        deleteProfileButton.disableProperty().bind(profileList.getSelectionModel().selectedItemProperty().isNull());
        refreshProfileList(null);
        // Live-Preview: jede Settings-Aenderung dekodiert automatisch neu (entprellt)
        autoApplyTimer.setOnFinished(e -> {
            if (sampleFile != null && cameraKey.get() != null) {
                decodePreview();
            }
        });
        model.properties().forEach(p -> p.addListener((obs, oldVal, newVal) -> scheduleAutoApply()));
        buildSettingsPanel();
        // Zoom: initial Fit-to-view (ImageView an Viewport gebunden)
        applyZoomMode(referenceScrollPane, referenceView, false);
        applyZoomMode(previewScrollPane, previewView, false);
        // ToggleSwitch hat KEINE onAction-Property (anders als ToggleButton) =>
        // Aenderungen ueber die selectedProperty abonnieren.
        zoomSwitch.selectedProperty().addListener((obs, wasSelected, isSelected)
                -> zoomToggleAction());
        applyButton.disableProperty().bind(cameraKey.isNull().or(fileLabel.textProperty().isEqualTo("No image loaded")));
        resetButton.disableProperty().bind(cameraKey.isNull());
        saveButton.disableProperty().bind(profileList.getSelectionModel().selectedItemProperty().isNull());
    }

    // ------------------------------------------------------------------
    // Kategorie-Baum (linke Spalte)
    // ------------------------------------------------------------------

    /** Baut den Kategorien-Baum: neue Kategorie = 1 Zeile + Case im Listener. */
    private void buildTree() {
        TreeItem<String> root = new TreeItem<>("Settings"); // unsichtbar (showRoot=false)
        root.setExpanded(true);
        root.getChildren().addAll(
                new TreeItem<>(TREE_GENERAL),   // Default-Tab: General (leer)
                new TreeItem<>(TREE_RAW));      // darunter: RAW Developer Settings
        settingsTree.setRoot(root);
        settingsTree.getSelectionModel().selectFirst(); // General vorauswaehlen
        settingsTree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                return;
            }
            showPage(TREE_GENERAL.equals(newItem.getValue()));
        });
        // Initial-Zustand: der Listener feuert NICHT fuer die Startauswahl,
        // daher die Seite hier explizit setzen (General ist vorselektiert).
        showPage(TREE_GENERAL.equals(settingsTree.getSelectionModel().getSelectedItem().getValue()));
    }

    /** Blendet entweder die RAW-Seite oder die General-Seite ein. */
    private void showPage(boolean isGeneral) {
        rawLabRoot.setVisible(!isGeneral);
        rawLabRoot.setManaged(!isGeneral);
        generalPane.setVisible(isGeneral);
        generalPane.setManaged(isGeneral);
    }

    // ------------------------------------------------------------------
    // UI-Aufbau (programmatisch, damit neue Settings nur wenige Zeilen kosten)
    // ------------------------------------------------------------------

    private void buildSettingsPanel() {
        CheckBox cameraWb = new CheckBox("Use camera white balance");
        // Translator: nur "CAMERA" (case-insensitive) schaltet use_camera_wb=1;
        // eigene Multiplikatoren (user_mul) schalten Kamera-/Auto-WB ab.
        cameraWb.selectedProperty().addListener((obs, o, n)
                -> model.setWhiteBalance(Boolean.TRUE.equals(n) ? "CAMERA" : "NONE"));
        model.whiteBalanceProperty().addListener((obs, o, n)
                -> cameraWb.setSelected("CAMERA".equalsIgnoreCase(n)));

        // DCB-Optionen greifen nur bei Interpolation quality 4 (DCB)
        HBox dcbIterRow = (HBox) spinner("DCB iterations, -1=LibRaw default (dcb_iterations)",
                -1, 10, -1, model.dcbIterationsProperty());
        CheckBox dcbEnhance = new CheckBox("DCB enhance (dcb_enhance_fl)");
        dcbEnhance.selectedProperty().bindBidirectional(model.dcbEnhanceFlProperty());
        javafx.beans.binding.BooleanExpression notDcb = model.rawQualityProperty().isEqualTo(4).not();
        dcbIterRow.disableProperty().bind(notDcb);
        dcbEnhance.disableProperty().bind(notDcb);

        settingsBox.getChildren().addAll(
                settingsCard("Demosaicing",
                        choice("Interpolation quality (user_qual)", QUALITY_VALUES, model.rawQualityProperty(),
                                v -> Integer.parseInt(v.replaceAll("\\D+", ""))),
                        check("Half size output (fast preview, also halves the editing resolution)", model.halfSizeOutputProperty()),
                        check("Interpolate RGB as 4 colors (four_color_rgb)", model.interpolate4ColorsProperty()),
                        check("Green matching (green_matching)", model.greenMatchingProperty()),
                        dcbIterRow,
                        dcbEnhance,
                        spinner("Median filter passes (med_passes)", 0, 10, 0, model.medianFilterPassesProperty())),
                        // 16-Bit bewusst ausgeblendet: Editor und Leuchttisch dekodieren immer
                        // ueber readPixelData16, die 8-Bit-Wege erzwingen 8 Bit. Das Flag wirkt
                        // daher nirgends. Modell/JSON bleibt.
                        // check("16-bit output (output_bps)", model.sixteenBitsImageProperty())),
                settingsCard("White balance & color",
                        cameraWb,
                        check("Auto white balance (use_auto_wb)", model.autoWhitebalanceProperty()),
                        check("Custom WB multipliers — disables camera/auto WB (user_mul)",
                                model.useUserMultipliersProperty()),
                        slider("WB multiplier R", 0.0, 4.0, model.userMulRProperty()),
                        slider("WB multiplier G1", 0.0, 4.0, model.userMulG1Property()),
                        slider("WB multiplier B", 0.0, 4.0, model.userMulBProperty()),
                        slider("WB multiplier G2", 0.0, 4.0, model.userMulG2Property()),
                        check("Greybox for auto WB (greybox)", model.useGreyBoxProperty()),
                        spinner("Greybox X", 0, 100000, 0, model.greyBoxXProperty()),
                        spinner("Greybox Y", 0, 100000, 0, model.greyBoxYProperty()),
                        spinner("Greybox width", 0, 100000, 0, model.greyBoxWProperty()),
                        spinner("Greybox height", 0, 100000, 0, model.greyBoxHProperty()),
                        choice("Output color space (output_color) — thumbnails only, editing is always sRGB", COLOR_SPACE_VALUES, model.outputColorSpaceProperty(),
                                v -> Integer.parseInt(v.replaceAll("\\D+", ""))),
                        choice("Camera matrix: -1 untouched, 0 off, 1 default, 3 always (use_camera_matrix)",
                                List.of("Untouched (-1)", "Off (0)", "Default (1)", "Always (3)"),
                                model.useCameraMatrixProperty(),
                                v -> Integer.parseInt(v.replaceAll("[^\\-0-9]", ""))),
                        spinner("Saturation level, -1=dcraw default (user_sat)",
                                -1, 20000, -1, model.saturationProperty())),
                settingsCard("Brightness / exposure",
                        check("Auto brightness (auto_bright) — thumbnails only", model.autoBrightnessProperty()),
                        slider("Auto brightness threshold (auto_bright_thr) — thumbnails only", 0.0, 0.1, model.autoBrightnessThresholdProperty()),
                        slider("Brightness (bright) — thumbnails only", 0.25, 4.0, model.brightNessProperty()),
                        check("Exposure correction (exp_correc)", model.enableExposureCorrectionProperty()),
                        slider("Exposure shift (exp_shift)", 0.25, 8.0, model.exposureShiftProperty()),
                        slider("Highlight preservation (exp_preser)", 0.0, 1.0, model.expoCorrectionHighlightProperty()),
                        spinner("Highlight mode 0 clip,1 unclip,2 blend,3-9 rebuild,0=legacy switch (highlight)",
                                0, 9, 0, model.unclipColorsProperty()),
                        check("Fix colors in highlights — legacy (highlight 1)", model.fixColorsHighlightsProperty()),
                        slider("Auto maximum adjust threshold (adjust_maximum_thr)", 0.0, 1.0, model.adjustMaximumThresholdProperty()),
                        check("Custom gamma (gamm) — thumbnails only", model.useCustomGammaProperty()),
                        slider("Gamma power = 1/gamma, sRGB ~0.45 (gamm[0]) — thumbnails only", 0.05, 1.0, model.gammaPowerProperty()),
                        slider("Gamma toe slope, sRGB 4.5 (gamm[1]) — thumbnails only", 0.0, 10.0, model.gammaToeSlopeProperty()),
                        check("Enable black point (user_black)", model.enableBlackPointProperty()),
                        spinner("Black point", 0, 4096, 0, model.blackPointProperty()),
                        check("Black level per channel (user_cblack)", model.useChannelBlackProperty()),
                        spinner("Channel black R", 0, 4096, 0, model.channelBlack0Property()),
                        spinner("Channel black G1", 0, 4096, 0, model.channelBlack1Property()),
                        spinner("Channel black B", 0, 4096, 0, model.channelBlack2Property()),
                        spinner("Channel black G2", 0, 4096, 0, model.channelBlack3Property())),
                settingsCard("Noise reduction",
                        choice("FBDD noise reduction (fbdd_noiserd)", NOISE_VALUES, model.noiseReductionProperty(),
                                v -> Integer.parseInt(v.replaceAll("\\D+", ""))),
                        spinner("Wavelet denoise threshold, 0=off (threshold)", 0, 1000, 0, model.nrThresholdProperty())),
                settingsCard("Chromatic aberration",
                        slider("CA scale red, 1.0=off (aber)", 0.5, 2.0, model.aberRedProperty()),
                        slider("CA scale blue, 1.0=off (aber)", 0.5, 2.0, model.aberBlueProperty())),
                settingsCard("Geometry / frames",
                        choice("Orientation: -1 from file, 0 none, 3=180°, 5=90° CCW, 6=90° CW (user_flip)",
                                List.of("From file (-1)", "None (0)", "180 degrees (3)", "90 CCW (5)", "90 CW (6)"),
                                model.userFlipProperty(),
                                v -> Integer.parseInt(v.replaceAll("[^\\-0-9]", ""))),
                        check("Do not stretch/rotate pixels (use_fuji_rotate=0)", model.dontStretchPixelsProperty()),
                        check("Crop box (cropbox)", model.useCropBoxProperty()),
                        spinner("Crop X", 0, 100000, 0, model.cropBoxXProperty()),
                        spinner("Crop Y", 0, 100000, 0, model.cropBoxYProperty()),
                        spinner("Crop width", 0, 100000, 0, model.cropBoxWProperty()),
                        spinner("Crop height", 0, 100000, 0, model.cropBoxHProperty()),
                        spinner("Shot select, multi-shot files (shot_select)", 0, 10, 0, model.shotSelectProperty())),
                settingsCard("Output format (ImageIO mode)",
                        check("Output TIFF instead of bitmap (output_tiff)", model.outputTiffProperty()),
                        doubleField("Coolscan NEF gamma, 1.0=LibRaw default (coolscan_nef_gamma)", model.coolScanNEFGammaProperty()),
                        intField("Processing options bit mask, 0=none, e.g. 0x200 (rawparams.options)", model.rawProcessingOptionsProperty())),
                settingsCard("Profiles & correction files (need absolute paths)",
                        textField("Output color profile (output_profile) — thumbnails only", model.outputProfilePathProperty()),
                        textField("Camera color profile (camera_profile)", model.cameraProfilePathProperty()),
                        textField("Bad pixels file (bad_pixels)", model.badPixelsPathProperty()),
                        textField("Dark frame file (dark_frame)", model.darkFramePathProperty())));
    }

    /**
     * Eine Sektions-"Card": Titel + Trennlinie, darunter sauber
     * linksbuendig ausgerichtete Zeilen (Label-Spalte fix 240px).
     */
    private VBox settingsCard(String title, Node... nodes) {
        Label label = new Label(title);
        label.getStyleClass().add("settings-section-title");
        VBox card = new VBox(6, label, new Separator());
        card.getStyleClass().add("settings-card");
        for (Node node : nodes) {
            HBox row = (node instanceof HBox h) ? h : new HBox(node); // Checkbox o.ae. einpacken
            row.getStyleClass().add("settings-row");
            // Alle Zeilen vertikal zentrieren: Slider, +/-Buttons und Wert-Label
            // sollen auf einer gemeinsamen horizontalen Linie sitzen.
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            card.getChildren().add(row);
        }
        return card;
    }

    private Node check(String text, javafx.beans.property.BooleanProperty prop) {
        CheckBox cb = new CheckBox(text);
        cb.selectedProperty().bindBidirectional(prop);
        return cb;
    }

    /** Slider mit +/--Stepper: Feinjustierung in 0.001er-Schritten, da der Slider
     *  selbst nur grob einstellt. Der Wert wird mit 3 Nachkommastellen angezeigt. */
    private Node slider(String text, double min, double max, javafx.beans.property.DoubleProperty prop) {
        Slider slider = new Slider(min, max, prop.get());
        slider.setShowTickLabels(false);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit((max - min) / 4);
        slider.setPrefWidth(200);
        slider.setMinWidth(120);
        slider.setMaxWidth(Double.MAX_VALUE); // Slider nutzt den Restplatz der Zeile
        slider.valueProperty().bindBidirectional(prop);
        Label value = new Label(String.format("%.3f", prop.get()));
        value.getStyleClass().add("slider-value");
        value.setPrefWidth(52);
        prop.addListener((obs, o, n) -> value.setText(String.format("%.3f", n.doubleValue())));
        Button minus = stepButton("−", prop, min, max, -0.001);
        Button plus = stepButton("+", prop, min, max, +0.001);
        // Beide Buttons ohne Abstand direkt nebeneinander (innerer HBox spacing=0)
        HBox stepper = new HBox(0, minus, plus);
        stepper.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox row = new HBox(6, labeled(text), slider, stepper, value);
        HBox.setHgrow(slider, javafx.scene.layout.Priority.ALWAYS); // Slider kriegt den Restplatz
        return row;
    }

    /** Kleiner +/--Button neben dem Slider: aendert den Wert um den Schritt,
     *  begrenzt auf [min, max]. Der Listener am Model triggert die Live-Preview. */
    private Button stepButton(String symbol, javafx.beans.property.DoubleProperty prop,
                               double min, double max, double step) {
        Button btn = new Button(symbol);
        btn.getStyleClass().add("step-button");
        btn.setOnAction(e -> prop.set(Math.max(min, Math.min(max, prop.get() + step))));
        return btn;
    }

    private Node spinner(String text, int min, int max, int def, javafx.beans.property.IntegerProperty prop) {
        Spinner<Integer> spinner = new Spinner<>(min, max, prop.get());
        spinner.setEditable(true);
        spinner.setPrefWidth(110);
        spinner.valueProperty().addListener((obs, o, n) -> {
            if (n != null) prop.set(n);
        });
        HBox box = new HBox(10, labeled(text), spinner);
        return box;
    }

    private Node choice(String text, List<String> values, Property<Number> prop,
                        java.util.function.Function<String, Integer> converter) {
        ChoiceBox<String> box = new ChoiceBox<>(FXCollections.observableArrayList(values));
        box.setPrefWidth(170);
        // Modell -> Box (Profil laden): passenden Eintrag suchen und selektieren
        java.util.function.Consumer<Number> sync = v -> {
            for (String item : values) {
                if (converter.apply(item) == v.intValue()) {
                    if (!item.equals(box.getSelectionModel().getSelectedItem())) {
                        box.getSelectionModel().select(item);
                    }
                    return;
                }
            }
        };
        sync.accept((Number) prop.getValue());
        prop.addListener((obs, o, n) -> sync.accept(n));
        box.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) prop.setValue(converter.apply(n));
        });
        return new HBox(10, labeled(text), box);
    }

    /** Textfeld fuer String-Properties (z.B. Dateipfade fuer Profile/Darkframe). */
    private Node textField(String text, javafx.beans.property.StringProperty prop) {
        TextField tf = new TextField(prop.get());
        tf.setPrefWidth(300);
        tf.textProperty().bindBidirectional(prop);
        return new HBox(10, labeled(text), tf);
    }

    /** Textfeld fuer int-Werte, dezimal oder hex (0x…), z.B. rawProcessingOptions. */
    private Node intField(String text, javafx.beans.property.IntegerProperty prop) {
        TextField tf = new TextField(String.valueOf(prop.get()));
        tf.setPrefWidth(300);
        tf.textProperty().addListener((obs, o, n) -> {
            try {
                String t = n == null ? "" : n.trim();
                prop.set(t.isEmpty() ? 0 : Integer.decode(t));
                tf.getStyleClass().remove("invalid");
            } catch (NumberFormatException ex) {
                tf.getStyleClass().add("invalid");
            }
        });
        return new HBox(10, labeled(text), tf);
    }

    /** Textfeld fuer double-Werte mit Genauigkeit jenseits von Slider-Schritten. */
    private Node doubleField(String text, javafx.beans.property.DoubleProperty prop) {
        TextField tf = new TextField(String.valueOf(prop.get()));
        tf.setPrefWidth(110);
        tf.textProperty().addListener((obs, o, n) -> {
            try {
                prop.set(Double.parseDouble(n == null ? "" : n.trim()));
                tf.getStyleClass().remove("invalid");
            } catch (NumberFormatException ex) {
                tf.getStyleClass().add("invalid");
            }
        });
        return new HBox(10, labeled(text), tf);
    }

    /** Fixe Label-Spalte fuer saubere vertikale Ausrichtung aller Zeilen. */
    private Label labeled(String text) {
        Label l = new Label(text);
        l.setPrefWidth(230);
        l.setMinWidth(230);
        l.setWrapText(true);
        return l;
    }

    // ------------------------------------------------------------------
    // Aktionen
    // ------------------------------------------------------------------

    /** Oeffnet den Dateiauswahl-Dialog, laedt das Bild als Sample und erkennt die
     *  Kamera. Liefert false, wenn der User abbricht oder die Datei unlesbar ist. */
    private boolean chooseSample(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open sample RAW image");
        if (sampleFile != null) {
            chooser.setInitialDirectory(sampleFile.toFile().getParentFile());
        }
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return false;
        }
        try {
            LibrawImage meta = new LibrawImage(file.toString(), new ConcurrentHashMap<>());
            String maker = meta.getCameraMaker();          // "<maker> <model>"
            sampleFile = file.toPath();
            fileLabel.setText(file.getName());
            cameraKey.set(maker);
            cameraLabel.setText(maker);
            imageBox.setVisible(true);
            imageBox.setManaged(true);
            return true;
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
            showError("Cannot open RAW file: " + ex.getMessage());
            return false;
        }
    }

    @FXML
    private void openSampleAction(ActionEvent event) {
        statusLabel.setText("");
        if (!chooseSample(((Node) event.getSource()).getScene().getWindow())) {
            return;
        }
        // Kamera-Profil: existiert noch keins, automatisch mit Default-Settings anlegen
        String cameraKeyStr = cameraKey.get();
        if (!RawSettingsStore.hasProfile(cameraKeyStr)) {
            RawSettingsStore.saveProfile(cameraKeyStr, RawDecoderSettingsFx.defaults());
        }
        selectAndApplyProfile(cameraKeyStr);
        decodeReference();
        decodePreview();
    }

    @FXML
    private void applyAction(ActionEvent event) {
        decodePreview();
    }

    /**
     * Schaltet zwischen Fit-to-view (Toggle aus) und 100%-Pixelansicht (Toggle an)
     * fuer BEIDE Bildfenster.
     *
     * Wichtig: ScrollPane.setFitToWidth/Height skaliert das Bild NICHT — es
     * streckt nur den Content-Knoten (StackPane) auf die Viewport-Groesse, das
     * ImageView darin bliebe in Originalpixelgroesse. Die Skalierung passiert
     * daher am ImageView selbst:
     * - Fit:   fitWidth/fitHeight an die Viewport-Groesse des ScrollPanes binden
     *          (preserveRatio=true => das Bild wird vollstaendig eingepasst).
     * - 100%:  Bindings loesen, fitWidth/fitHeight = -1 => Originalpixel,
     *          Scrollbalken erscheinen, beide Fenster springen auf 0,0.
     */
    /** Wird von der selectedProperty des ToggleSwitch aufgerufen (kein FXML-Handler). */
    private void zoomToggleAction() {
        boolean pixelView = zoomSwitch.isSelected();
        applyZoomMode(referenceScrollPane, referenceView, pixelView);
        applyZoomMode(previewScrollPane, previewView, pixelView);
    }

    /** Setzt den Zoom-Modus fuer ein Bildfenster (siehe zoomToggleAction). */
    private void applyZoomMode(javafx.scene.control.ScrollPane pane, ImageView view, boolean pixelView) {
        if (pixelView) {
            view.fitWidthProperty().unbind();
            view.fitHeightProperty().unbind();
            view.setFitWidth(-1);
            view.setFitHeight(-1);
            pane.setFitToWidth(false);
            pane.setFitToHeight(false);
            pane.setHvalue(0); // oben links anfangen zu vergleichen
            pane.setVvalue(0);
        } else {
            view.fitWidthProperty().bind(pane.widthProperty());
            view.fitHeightProperty().bind(pane.heightProperty());
            pane.setFitToWidth(false); // Skalierung macht das ImageView, nicht das ScrollPane
            pane.setFitToHeight(false);
        }
    }

    @FXML
    private void resetAction(ActionEvent event) {
        model.fromRawDecoderSettings(new RawDecoderSettingsFx().toRawDecoderSettings());
        decodePreview();
    }

    /** "+" – neues Profil anlegen. Ohne geladenes Bild MUSS zuerst ein Sample
     *  gewaehlt werden: nur daraus laesst sich der passende Profilname (Kamera)
     *  bestimmen — raten ist nicht noetig und soll nicht moeglich sein. */
    @FXML
    private void addProfileAction(ActionEvent event) {
        if (cameraKey.get() == null || sampleFile == null) {
            if (!chooseSample(((Node) event.getSource()).getScene().getWindow())) {
                statusLabel.setTextFill(Color.web("#ff6961"));
                statusLabel.setText("Select a sample image first — the profile name is derived from the camera.");
                return;
            }
        }
        TextInputDialog dialog = new TextInputDialog(cameraKey.get());
        dialog.setTitle("New RAW decoder profile");
        dialog.setHeaderText("Profile name (detected from the sample image: maker + model)");
        dialog.setContentText("Name:");
        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.isBlank()) {
                return;
            }
            String key = name.trim();
            if (profileList.getItems().contains(key)) {
                showError("A profile with this name already exists.");
                return;
            }
            RawSettingsStore.saveProfile(key, model);   // aktuelle Settings ins neue Profil
            refreshProfileList(key);                    // selektiert es => Settings aktiv
        });
    }

    /** "-" – selektiertes Profil loeschen. */
    @FXML
    private void deleteProfileAction(ActionEvent event) {
        String key = profileList.getSelectionModel().getSelectedItem();
        if (key == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete RAW decoder profile '" + key + "'?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                RawSettingsStore.deleteProfile(key);
                // Default selektieren UND aktivieren (auch wenn er schon selektiert war)
                selectAndApplyProfile(RawSettingsStore.DEFAULT_KEY);
                statusLabel.setTextFill(Color.web("#ff6961"));
                statusLabel.setText("Deleted profile '" + key + "'");
            }
        });
    }

    /** Speichert die aktuellen Settings in das selektierte Profil. */
    @FXML
    private void saveAction(ActionEvent event) {
        String key = profileList.getSelectionModel().getSelectedItem();
        if (key == null) {
            showError("Select a profile first.");
            return;
        }
        RawSettingsStore.saveProfile(key, model);
        statusLabel.setTextFill(Color.web("#7bd88f"));
        statusLabel.setText("Saved profile '" + key + "'");
    }

    // ------------------------------------------------------------------
    // Decoding / Vorschau
    // ------------------------------------------------------------------

    /** Referenzbild mit den Defaults (bzw. dem Default-Profil) dekodieren. */
    private void decodeReference() {
        RawDecoderSettingsFx refProfile = RawSettingsStore.loadProfile(RawSettingsStore.DEFAULT_KEY);
        runDecode(refProfile.toRawDecoderSettings(),
                img -> referenceView.setImage(img), referenceProgress);
    }

    private void decodePreview() {
        runDecode(model.toRawDecoderSettings(),
                img -> previewView.setImage(img), previewProgress);
    }

    /**
     * Dekodiert das Beispielbild mit den uebergebenen Settings in einem
     * Hintergrund-Thread. setSettingsOverride() laesst die globale Map
     * unberuehrt; die Aenderungen gelten nur fuer diesen Aufruf.
     * Dekodiert wird wie im Leuchttisch und Editor: 16 Bit linear (sRGB,
     * ohne Gamma und Auto-Helligkeit), angezeigt ueber Display16. Die
     * Vorschau zeigt also das Bild, das der Editor ohne Bearbeitung zeigt.
     * Der ProgressIndicator wird ab Task-Start eingeblendet (das View hat
     * dadurch schon seine endgueltige Groesse) und nach Abschluss versteckt —
     * spaetestens aber erst nach MIN_PROGRESS_MILLIS, damit auch sehr schnelle
     * Decodes (Datei im OS-Cache) fuer den User sichtbares Feedback geben.
     */
    private static final long MIN_PROGRESS_MILLIS = 600;

    private void runDecode(RawDecoderSettings settings, java.util.function.Consumer<Image> onDone,
                           ProgressIndicator progress) {
        if (sampleFile == null) {
            return;
        }
        statusLabel.setTextFill(Color.web("#9f9f9f"));
        statusLabel.setText("Decoding…");
        progress.setVisible(true);
        java.util.concurrent.atomic.AtomicInteger pending = pendingDecodes.computeIfAbsent(progress,
                p -> new java.util.concurrent.atomic.AtomicInteger());
        pending.incrementAndGet();
        long start = System.currentTimeMillis();
        Path file = sampleFile;
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() throws Exception {
                LibrawImage librawImage = new LibrawImage(file.toString(), new ConcurrentHashMap<>());
                librawImage.setSettingsOverride(settings);
                byte[] bytes = java.nio.file.Files.readAllBytes(file);
                Image16 linear = Image16.fromRaw(librawImage.readPixelData16(bytes, true));
                return FxImage16.toImage(linear);
            }
        };
        task.setOnSucceeded(e -> {
            hideProgressAfterMinimum(progress, start);
            onDone.accept(task.getValue());
            statusLabel.setTextFill(Color.web("#9f9f9f"));
            statusLabel.setText("Ready");
        });
        task.setOnFailed(e -> {
            hideProgressAfterMinimum(progress, start);
            statusLabel.setTextFill(Color.web("#ff6961"));
            statusLabel.setText("Decode failed: " + task.getException().getMessage());
            LOG.log(Level.SEVERE, "Decode failed", task.getException());
        });
        executor.submit(task);
    }

    /** Zaehlt den Task als fertig und versteckt den Indikator erst, wenn kein
     *  anderer Task mehr laeuft UND die Mindest-Anzeigezeit abgelaufen ist. */
    private void hideProgressAfterMinimum(ProgressIndicator progress, long start) {
        java.util.concurrent.atomic.AtomicInteger pending = pendingDecodes.get(progress);
        if (pending != null && pending.decrementAndGet() > 0) {
            return; // es laufen noch weitere Decodes => Indikator anlassen
        }
        long remaining = MIN_PROGRESS_MILLIS - (System.currentTimeMillis() - start);
        if (remaining <= 0) {
            progress.setVisible(false);
            return;
        }
        javafx.animation.PauseTransition hide
                = new javafx.animation.PauseTransition(javafx.util.Duration.millis(remaining));
        hide.setOnFinished(e -> {
            java.util.concurrent.atomic.AtomicInteger still = pendingDecodes.get(progress);
            if (still == null || still.get() == 0) {
                progress.setVisible(false);
            }
        });
        hide.playFromStart();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.show();
    }
}