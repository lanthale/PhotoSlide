package org.photoslide.preferences;


import org.photoslide.I18n;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
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
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.librawdecodersettings.RawDecoderSettings;
import org.photoslide.editpipeline.ColorCalibration;
import org.photoslide.editpipeline.ColorShading;
import org.photoslide.editpipeline.RawLook;
import org.librawfx.RAWImageLoaderFactory;
import org.librawjava.LibrawImage;
import org.librawjava.OutputColor;
import org.kordamp.ikonli.javafx.FontIcon;
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
    private static final List<String> QUALITY_VALUES = List.of(I18n.t("raw.q.linear"), "VNG (1)", "PPG (2)",
            "AHD (3)", "DCB (4)", "DHT (11)", I18n.t("raw.q.modifiedAhd"));
    // output_color: 0=raw, 1=sRGB, 2=Adobe, 3=Wide, 4=ProPhoto, 5=XYZ, 6=ACES
    // fbdd_noiserd: 0=off, 1..3 (LibRaw)
    private static final List<String> NOISE_VALUES = List.of(I18n.t("raw.nr.off"), I18n.t("raw.nr.light"), I18n.t("raw.nr.strong"), I18n.t("raw.nr.strongest"));

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

    private static final String TREE_RAW = I18n.t("raw.treeRaw");
    private static final String TREE_GENERAL = I18n.t("rawSettingsLab.general");

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
            model.copyLookFrom(profile);
        } finally {
            applyingProfile = false;
        }
        statusLabel.setText(I18n.t("raw.profileLoaded", key));
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
        fileLabel.setText(I18n.t("rawSettingsLab.noImageLoaded"));
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
        // calibration: clicks on the right image, markers drawn over it
        calibrationMarkers.setMouseTransparent(true);
        ((StackPane) previewView.getParent()).getChildren().add(calibrationMarkers);
        previewView.setOnMouseClicked(this::calibrationClick);
        // Zoom: initial Fit-to-view (ImageView an Viewport gebunden)
        applyZoomMode(referenceScrollPane, referenceView, false);
        applyZoomMode(previewScrollPane, previewView, false);
        // ToggleSwitch hat KEINE onAction-Property (anders als ToggleButton) =>
        // Aenderungen ueber die selectedProperty abonnieren.
        zoomSwitch.selectedProperty().addListener((obs, wasSelected, isSelected)
                -> zoomToggleAction());
        applyButton.disableProperty().bind(cameraKey.isNull().or(fileLabel.textProperty().isEqualTo(I18n.t("rawSettingsLab.noImageLoaded"))));
        resetButton.disableProperty().bind(cameraKey.isNull());
        saveButton.disableProperty().bind(profileList.getSelectionModel().selectedItemProperty().isNull());
    }

    // ------------------------------------------------------------------
    // Kategorie-Baum (linke Spalte)
    // ------------------------------------------------------------------

    /** Baut den Kategorien-Baum: neue Kategorie = 1 Zeile + Case im Listener. */
    private void buildTree() {
        TreeItem<String> root = new TreeItem<>(I18n.t("raw.settings")); // unsichtbar (showRoot=false)
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
        CheckBox cameraWb = new CheckBox(I18n.t("raw.cameraWb"));
        // Translator: nur "CAMERA" (case-insensitive) schaltet use_camera_wb=1;
        // eigene Multiplikatoren (user_mul) schalten Kamera-/Auto-WB ab.
        cameraWb.selectedProperty().addListener((obs, o, n)
                -> model.setWhiteBalance(Boolean.TRUE.equals(n) ? "CAMERA" : "NONE"));
        model.whiteBalanceProperty().addListener((obs, o, n)
                -> cameraWb.setSelected("CAMERA".equalsIgnoreCase(n)));

        // DCB-Optionen greifen nur bei Interpolation quality 4 (DCB)
        HBox dcbIterRow = (HBox) spinner(I18n.t("raw.dcbIterations"),
                -1, 10, -1, model.dcbIterationsProperty());
        CheckBox dcbEnhance = new CheckBox(I18n.t("raw.dcbEnhance"));
        dcbEnhance.selectedProperty().bindBidirectional(model.dcbEnhanceFlProperty());
        javafx.beans.binding.BooleanExpression notDcb = model.rawQualityProperty().isEqualTo(4).not();
        dcbIterRow.disableProperty().bind(notDcb);
        dcbEnhance.disableProperty().bind(notDcb);

        settingsBox.getChildren().addAll(
                settingsCard(I18n.t("raw.card.baseLook"),
                        slider(I18n.t("raw.baseExposure"), -2.0, 3.0, model.baseExposureProperty()),
                        check(I18n.t("raw.useDngBaseline"), model.useDngBaselineProperty()),
                        check(I18n.t("raw.isoGain"),
                                model.isoGainProperty()),
                        choice(I18n.t("raw.toneCurve"), TONE_CURVES, model.toneCurveProperty(), TONE_CURVES::indexOf)),
                settingsCard(I18n.t("raw.card.colorCalibration"),
                        check(I18n.t("raw.useColorMatrix"), model.useColorMatrixProperty()),
                        calibrationRow(),
                        check(I18n.t("raw.useShading"), model.useShadingProperty()),
                        slider(I18n.t("raw.shadingStrength"), 0.0, 1.5,
                                model.shadingStrengthProperty()),
                        shadingRow()),
                settingsCard(I18n.t("raw.card.demosaicing"),
                        choice(I18n.t("raw.quality"), QUALITY_VALUES, model.rawQualityProperty(),
                                v -> Integer.parseInt(v.replaceAll("\\D+", ""))),
                        check(I18n.t("raw.halfSize"), model.halfSizeOutputProperty()),
                        check(I18n.t("raw.fourColor"), model.interpolate4ColorsProperty()),
                        check(I18n.t("raw.greenMatching"), model.greenMatchingProperty()),
                        dcbIterRow,
                        dcbEnhance,
                        spinner(I18n.t("raw.medPasses"), 0, 10, 0, model.medianFilterPassesProperty())),
                        // 16-Bit bewusst ausgeblendet: Editor und Leuchttisch dekodieren immer
                        // ueber readPixelData16, die 8-Bit-Wege erzwingen 8 Bit. Das Flag wirkt
                        // daher nirgends. Modell/JSON bleibt.
                        // check("16-bit output (output_bps)", model.sixteenBitsImageProperty())),
                settingsCard(I18n.t("raw.card.whiteBalance"),
                        cameraWb,
                        check(I18n.t("raw.autoWb"), model.autoWhitebalanceProperty()),
                        check(I18n.t("raw.userMul"),
                                model.useUserMultipliersProperty()),
                        slider(I18n.t("raw.mulR"), 0.0, 4.0, model.userMulRProperty()),
                        slider(I18n.t("raw.mulG1"), 0.0, 4.0, model.userMulG1Property()),
                        slider(I18n.t("raw.mulB"), 0.0, 4.0, model.userMulBProperty()),
                        slider(I18n.t("raw.mulG2"), 0.0, 4.0, model.userMulG2Property()),
                        check(I18n.t("raw.greybox"), model.useGreyBoxProperty()),
                        spinner(I18n.t("raw.greyboxX"), 0, 100000, 0, model.greyBoxXProperty()),
                        spinner(I18n.t("raw.greyboxY"), 0, 100000, 0, model.greyBoxYProperty()),
                        spinner(I18n.t("raw.greyboxW"), 0, 100000, 0, model.greyBoxWProperty()),
                        spinner(I18n.t("raw.greyboxH"), 0, 100000, 0, model.greyBoxHProperty()),
                        choice(I18n.t("raw.cameraMatrix"),
                                List.of(I18n.t("raw.cm.untouched"), I18n.t("raw.nr.off"), I18n.t("raw.cm.default"), I18n.t("raw.cm.always")),
                                model.useCameraMatrixProperty(),
                                v -> Integer.parseInt(v.replaceAll("[^\\-0-9]", ""))),
                        spinner(I18n.t("raw.userSat"),
                                -1, 20000, -1, model.saturationProperty())),
                settingsCard(I18n.t("raw.card.highlights"),
                        spinner(I18n.t("raw.highlightMode"),
                                0, 9, 0, model.unclipColorsProperty()),
                        check(I18n.t("raw.fixHighlights"), model.fixColorsHighlightsProperty()),
                        slider(I18n.t("raw.adjustMax"), 0.0, 1.0, model.adjustMaximumThresholdProperty()),
                        check(I18n.t("raw.enableBlack"), model.enableBlackPointProperty()),
                        spinner(I18n.t("raw.blackPoint"), 0, 4096, 0, model.blackPointProperty()),
                        check(I18n.t("raw.channelBlack"), model.useChannelBlackProperty()),
                        spinner(I18n.t("raw.cblackR"), 0, 4096, 0, model.channelBlack0Property()),
                        spinner(I18n.t("raw.cblackG1"), 0, 4096, 0, model.channelBlack1Property()),
                        spinner(I18n.t("raw.cblackB"), 0, 4096, 0, model.channelBlack2Property()),
                        spinner(I18n.t("raw.cblackG2"), 0, 4096, 0, model.channelBlack3Property())),
                settingsCard(I18n.t("tool.noiseReduction"),
                        choice(I18n.t("raw.fbdd"), NOISE_VALUES, model.noiseReductionProperty(),
                                v -> Integer.parseInt(v.replaceAll("\\D+", ""))),
                        spinner(I18n.t("raw.wavelet"), 0, 1000, 0, model.nrThresholdProperty())),
                settingsCard(I18n.t("raw.card.aberration"),
                        slider(I18n.t("raw.caRed"), 0.5, 2.0, model.aberRedProperty()),
                        slider(I18n.t("raw.caBlue"), 0.5, 2.0, model.aberBlueProperty())),
                settingsCard(I18n.t("raw.card.geometry"),
                        choice(I18n.t("raw.orientation"),
                                List.of(I18n.t("raw.or.file"), I18n.t("raw.or.none"), I18n.t("raw.or.180"), I18n.t("raw.or.ccw"), I18n.t("raw.or.cw")),
                                model.userFlipProperty(),
                                v -> Integer.parseInt(v.replaceAll("[^\\-0-9]", ""))),
                        check(I18n.t("raw.noStretch"), model.dontStretchPixelsProperty()),
                        spinner(I18n.t("raw.shotSelect"), 0, 10, 0, model.shotSelectProperty())),
                settingsCard(I18n.t("raw.card.files"),
                        textField(I18n.t("raw.badPixels"), model.badPixelsPathProperty()),
                        textField(I18n.t("raw.darkFrame"), model.darkFramePathProperty()),
                        doubleField(I18n.t("raw.coolscan"), model.coolScanNEFGammaProperty()),
                        intField(I18n.t("raw.options"), model.rawProcessingOptionsProperty())),
                settingsCard(I18n.t("raw.card.advanced"),
                        hint(I18n.t("raw.hint.advanced")),
                        check(I18n.t("raw.expCorrec"), model.enableExposureCorrectionProperty()),
                        slider(I18n.t("raw.expShift"), 0.25, 8.0, model.exposureShiftProperty()),
                        slider(I18n.t("raw.expPreser"), 0.0, 1.0, model.expoCorrectionHighlightProperty()),
                        hint(I18n.t("raw.hint.expShift")),
                        textField(I18n.t("raw.cameraProfile"), model.cameraProfilePathProperty()),
                        hint(I18n.t("raw.hint.cameraProfile")),
                        check(I18n.t("raw.cropBox"), model.useCropBoxProperty()),
                        spinner(I18n.t("raw.cropX"), 0, 100000, 0, model.cropBoxXProperty()),
                        spinner(I18n.t("raw.cropY"), 0, 100000, 0, model.cropBoxYProperty()),
                        spinner(I18n.t("raw.cropW"), 0, 100000, 0, model.cropBoxWProperty()),
                        spinner(I18n.t("raw.cropH"), 0, 100000, 0, model.cropBoxHProperty()),
                        hint(I18n.t("raw.hint.cropBox"))));
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

    /** Grey explanation below the settings it belongs to. */
    private Node hint(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        // wrap inside the card instead of widening the dialog
        l.setMinWidth(0);
        l.setPrefWidth(560);
        l.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        l.setTextFill(Color.web("#9f9f9f"));
        l.setStyle("-fx-font-size: 0.9em;");
        return l;
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
        chooser.setTitle(I18n.t("raw.openSample"));
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
            showError(I18n.t("raw.cannotOpen", ex.getMessage()));
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
        model.copyLookFrom(new RawDecoderSettingsFx());
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
                statusLabel.setText(I18n.t("raw.selectSampleFirst"));
                return;
            }
        }
        TextInputDialog dialog = new TextInputDialog(cameraKey.get());
        dialog.setTitle(I18n.t("raw.newProfile"));
        dialog.setHeaderText(I18n.t("raw.newProfileHeader"));
        dialog.setContentText(I18n.t("raw.name"));
        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.isBlank()) {
                return;
            }
            String key = name.trim();
            if (profileList.getItems().contains(key)) {
                showError(I18n.t("raw.profileExists"));
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
                I18n.t("raw.deleteQuestion", key), ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                RawSettingsStore.deleteProfile(key);
                // Default selektieren UND aktivieren (auch wenn er schon selektiert war)
                selectAndApplyProfile(RawSettingsStore.DEFAULT_KEY);
                statusLabel.setTextFill(Color.web("#ff6961"));
                statusLabel.setText(I18n.t("raw.deleted", key));
            }
        });
    }

    /** Speichert die aktuellen Settings in das selektierte Profil. */
    @FXML
    private void saveAction(ActionEvent event) {
        String key = profileList.getSelectionModel().getSelectedItem();
        if (key == null) {
            showError(I18n.t("raw.selectProfile"));
            return;
        }
        RawSettingsStore.saveProfile(key, model);
        statusLabel.setTextFill(Color.web("#7bd88f"));
        statusLabel.setText(I18n.t("raw.saved", key));
    }

    // ------------------------------------------------------------------
    // Decoding / Vorschau
    // ------------------------------------------------------------------

    /** Referenzbild mit den Defaults (bzw. dem Default-Profil) dekodieren. */
    private void decodeReference() {
        RawDecoderSettingsFx refProfile = RawSettingsStore.loadProfile(RawSettingsStore.DEFAULT_KEY);
        runDecode(refProfile.toRawDecoderSettings(), refProfile.toLook(),
                img -> referenceView.setImage(img), referenceProgress);
    }

    private void decodePreview() {
        runDecode(model.toRawDecoderSettings(), model.toLook(),
                img -> previewView.setImage(img), previewProgress);
    }

    /**
     * Dekodiert das Beispielbild mit den uebergebenen Settings in einem
     * Hintergrund-Thread. setSettingsOverride() laesst die globale Map
     * unberuehrt; die Aenderungen gelten nur fuer diesen Aufruf.
     * Dekodiert wird wie im Leuchttisch und Editor: 16 Bit linear (ProPhoto,
     * ohne Gamma und Auto-Helligkeit), angezeigt ueber Display16. Die
     * Vorschau zeigt also das Bild, das der Editor ohne Bearbeitung zeigt.
     * Der ProgressIndicator wird ab Task-Start eingeblendet (das View hat
     * dadurch schon seine endgueltige Groesse) und nach Abschluss versteckt —
     * spaetestens aber erst nach MIN_PROGRESS_MILLIS, damit auch sehr schnelle
     * Decodes (Datei im OS-Cache) fuer den User sichtbares Feedback geben.
     */
    private static final long MIN_PROGRESS_MILLIS = 600;

    // ------------------------------------------------------------------
    // Colour calibration from a ColorChecker
    // ------------------------------------------------------------------

    private final Pane calibrationMarkers = new Pane();
    /** Clicked corner patches, as fractions of the image width / height. */
    private final List<double[]> calibrationClicks = new ArrayList<>();
    private final Label calibrationInfo = new Label();
    private boolean calibrating;

    private Node calibrationRow() {
        Button calibrate = new Button(I18n.t("raw.calibrateCC"), new FontIcon("fa-crosshairs"));
        calibrate.setTooltip(new Tooltip(I18n.t("raw.calibrateCCTip")));
        calibrate.setOnAction(e -> startCalibration());
        Button remove = new Button(I18n.t("raw.remove"));
        remove.setOnAction(e -> {
            model.setUseColorMatrix(false);
            model.setColorMatrix("");
            calibrationMarkers.getChildren().clear();
        });
        remove.disableProperty().bind(model.colorMatrixProperty().isEmpty());
        calibrationInfo.setTextFill(Color.web("#9f9f9f"));
        Runnable info = () -> calibrationInfo.setText(RawLook.parseMatrix(model.getColorMatrix()) == null
                ? I18n.t("raw.notCalibrated") : I18n.t("raw.calibrated"));
        model.colorMatrixProperty().addListener((obs, o, n) -> info.run());
        info.run();
        HBox row = new HBox(10, calibrate, remove, calibrationInfo);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    private void startCalibration() {
        if (sampleFile == null || previewView.getImage() == null) {
            showError(I18n.t("raw.openCCFirst"));
            return;
        }
        calibrating = true;
        calibrationClicks.clear();
        calibrationMarkers.getChildren().clear();
        previewView.setCursor(Cursor.CROSSHAIR);
        statusLabel.setTextFill(Color.web("#ffd24d"));
        statusLabel.setText(I18n.t("raw.clickCorners"));
    }

    private void calibrationClick(MouseEvent e) {
        if (!calibrating || previewView.getImage() == null) {
            return;
        }
        Bounds b = previewView.getBoundsInLocal();
        double fx = e.getX() / b.getWidth();
        double fy = e.getY() / b.getHeight();
        calibrationClicks.add(new double[]{fx, fy});
        mark(fx, fy, 7, Color.web("#ffd24d"));
        if (calibrationClicks.size() < 4) {
            statusLabel.setText(I18n.t("raw.cornersToGo", 4 - calibrationClicks.size()));
            return;
        }
        calibrating = false;
        previewView.setCursor(null);
        runCalibration(new ArrayList<>(calibrationClicks));
    }

    private final Label shadingInfo = new Label();

    private Node shadingRow() {
        Button calibrate = new Button(I18n.t("raw.calibrateShading"), new FontIcon("fa-th"));
        calibrate.setTooltip(new Tooltip(I18n.t("raw.calibrateShadingTip")));
        calibrate.setOnAction(e -> runShadingCalibration());
        Button remove = new Button(I18n.t("raw.remove"));
        remove.setOnAction(e -> {
            model.setUseShading(false);
            model.setShadingMap("");
        });
        remove.disableProperty().bind(model.shadingMapProperty().isEmpty());
        shadingInfo.setTextFill(Color.web("#9f9f9f"));
        Runnable info = () -> {
            ColorShading cs = ColorShading.parse(model.getShadingMap());
            shadingInfo.setText(cs == null ? I18n.t("raw.notCalibrated")
                    : I18n.t("raw.calibratedStrength", cs.strength()));
        };
        model.shadingMapProperty().addListener((obs, o, n) -> info.run());
        info.run();
        HBox row = new HBox(10, calibrate, remove, shadingInfo);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    /** All RAW photos of the sample's camera in its folder with an embedded camera JPEG. */
    private void runShadingCalibration() {
        if (sampleFile == null || cameraKey.get() == null) {
            showError(I18n.t("raw.openCameraFirst"));
            return;
        }
        RawDecoderSettings settings = model.toRawDecoderSettings();
        double[] matrix = model.isUseColorMatrix() ? RawLook.parseMatrix(model.getColorMatrix()) : null;
        Path folder = sampleFile.getParent();
        String camera = cameraKey.get();
        statusLabel.setTextFill(Color.web("#9f9f9f"));
        Task<ColorShading.Calibration> task = new Task<>() {
            @Override
            protected ColorShading.Calibration call() throws Exception {
                updateMessage(I18n.t("raw.lookingFor", camera));
                List<Path> files = new ArrayList<>();
                try (var list = java.nio.file.Files.list(folder)) {
                    for (Path p : list.sorted().toList()) {
                        if (files.size() >= MAX_SHADING_PHOTOS || !p.getFileName().toString().toLowerCase().endsWith(".x3f")) {
                            continue;
                        }
                        try {
                            if (camera.equals(new LibrawImage(p.toString(), new ConcurrentHashMap<>()).getCameraMaker())) {
                                files.add(p);
                            }
                        } catch (IOException ex) {
                            LOG.log(Level.FINE, "Skipping " + p, ex);
                        }
                    }
                }
                return ColorShading.calibrate(files, settings, matrix,
                        (done, total) -> updateMessage(I18n.t("raw.shadingProgress", done, total)));
            }
        };
        statusLabel.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> {
            statusLabel.textProperty().unbind();
            ColorShading.Calibration c = task.getValue();
            if (c == null) {
                statusLabel.setTextFill(Color.web("#ff6961"));
                statusLabel.setText(I18n.t("raw.notEnoughPhotos"));
                return;
            }
            model.setShadingMap(c.shading().format());
            model.setUseShading(true);
            shadingInfo.setText(I18n.t("raw.calibratedFrom", c.photos(), c.shading().strength()));
            statusLabel.setText(I18n.t("raw.shadingCalibrated"));
        });
        task.setOnFailed(e -> {
            statusLabel.textProperty().unbind();
            statusLabel.setTextFill(Color.web("#ff6961"));
            statusLabel.setText(I18n.t("raw.shadingFailed", task.getException().getMessage()));
            LOG.log(Level.SEVERE, "Colour shading failed", task.getException());
        });
        executor.submit(task);
    }

    /** Enough for a stable median; more only takes longer. */
    private static final int MAX_SHADING_PHOTOS = 60;

    /** Decodes the sample with the current settings (no look) and fits the matrix. */
    private void runCalibration(List<double[]> clicks) {
        statusLabel.setTextFill(Color.web("#9f9f9f"));
        statusLabel.setText(I18n.t("raw.calibrating"));
        RawDecoderSettings settings = model.toRawDecoderSettings();
        // the matrix does not contain the white balance: a fixed one only fits photos in the same light
        boolean fixedWhiteBalance = model.isUseUserMultipliers();
        Path file = sampleFile;
        Task<ColorCalibration.Result> task = new Task<>() {
            @Override
            protected ColorCalibration.Result call() throws Exception {
                LibrawImage libraw = new LibrawImage(file.toString(), new ConcurrentHashMap<>());
                libraw.setSettingsOverride(settings);
                Image16 linear = Image16.fromRaw(libraw.readPixelData16(java.nio.file.Files.readAllBytes(file),
                        true, OutputColor.PROPHOTO));
                double[][] corners = new double[4][];
                for (int i = 0; i < 4; i++) {
                    corners[i] = new double[]{clicks.get(i)[0] * linear.width(), clicks.get(i)[1] * linear.height()};
                }
                ColorCalibration.Result r = ColorCalibration.fit(linear, corners);
                if (r != null) {
                    // markers in fractions of the image
                    for (double[] p : r.patches()) {
                        p[0] /= linear.width();
                        p[1] /= linear.height();
                    }
                }
                return r;
            }
        };
        task.setOnSucceeded(e -> {
            ColorCalibration.Result r = task.getValue();
            if (r == null) {
                statusLabel.setTextFill(Color.web("#ff6961"));
                statusLabel.setText(I18n.t("raw.chartNotFound"));
                return;
            }
            calibrationMarkers.getChildren().clear();
            for (double[] p : r.patches()) {
                mark(p[0], p[1], 4, Color.web("#4dd2ff"));
            }
            model.setColorMatrix(RawLook.formatMatrix(r.matrix()));
            model.setUseColorMatrix(true);
            calibrationInfo.setText(I18n.t("raw.colourError", r.errorBefore(), r.errorAfter(),
                    fixedWhiteBalance ? I18n.t("raw.colourErrorNote") : ""));
            statusLabel.setTextFill(Color.web("#9f9f9f"));
            statusLabel.setText(I18n.t("raw.calibratedSave"));
        });
        task.setOnFailed(e -> {
            statusLabel.setTextFill(Color.web("#ff6961"));
            statusLabel.setText(I18n.t("raw.calibrationFailed", task.getException().getMessage()));
            LOG.log(Level.SEVERE, "Calibration failed", task.getException());
        });
        executor.submit(task);
    }

    /** Circle over the right image at a position given in fractions of the image. */
    private void mark(double fx, double fy, double radius, Color color) {
        Bounds b = previewView.getBoundsInLocal();
        javafx.geometry.Point2D scene = previewView.localToScene(fx * b.getWidth(), fy * b.getHeight());
        javafx.geometry.Point2D p = calibrationMarkers.sceneToLocal(scene);
        Circle c = new Circle(p.getX(), p.getY(), radius, Color.TRANSPARENT);
        c.setStroke(color);
        c.setStrokeWidth(2);
        calibrationMarkers.getChildren().add(c);
    }

    /** Labels of RawLook.ToneCurve, index = value of the profile. */
    private static final List<String> TONE_CURVES = java.util.Arrays.stream(RawLook.ToneCurve.values())
            .map(RawLook.ToneCurve::label).toList();

    private void runDecode(RawDecoderSettings settings, RawLook look, java.util.function.Consumer<Image> onDone,
                           ProgressIndicator progress) {
        if (sampleFile == null) {
            return;
        }
        statusLabel.setTextFill(Color.web("#9f9f9f"));
        statusLabel.setText(I18n.t("raw.decoding"));
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
                Image16 linear = Image16.fromRaw(librawImage.readPixelData16(bytes, true, OutputColor.PROPHOTO));
                return FxImage16.toImage(look.apply(linear, look.fileStops(file, librawImage)));
            }
        };
        task.setOnSucceeded(e -> {
            hideProgressAfterMinimum(progress, start);
            onDone.accept(task.getValue());
            statusLabel.setTextFill(Color.web("#9f9f9f"));
            statusLabel.setText(I18n.t("raw.ready"));
        });
        task.setOnFailed(e -> {
            hideProgressAfterMinimum(progress, start);
            statusLabel.setTextFill(Color.web("#ff6961"));
            statusLabel.setText(I18n.t("raw.decodeFailed", task.getException().getMessage()));
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