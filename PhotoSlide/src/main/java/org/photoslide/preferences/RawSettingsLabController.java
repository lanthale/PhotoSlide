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
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.librawdecodersettings.RawDecoderSettings;
import org.librawfx.RAWImageLoaderFactory;
import org.librawjava.LibrawImage;

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
        model.fromRawDecoderSettings(profile.toRawDecoderSettings());
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
        imageBox.setVisible(false);
        imageBox.setManaged(false);
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
        buildSettingsPanel();
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
                new TreeItem<>(TREE_RAW),
                new TreeItem<>(TREE_GENERAL));
        settingsTree.setRoot(root);
        settingsTree.getSelectionModel().selectFirst(); // RAW Developer Settings vorauswaehlen
        settingsTree.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null) {
                return;
            }
            boolean isGeneral = TREE_GENERAL.equals(newItem.getValue());
            rawLabRoot.setVisible(!isGeneral);
            rawLabRoot.setManaged(!isGeneral);
            generalPane.setVisible(isGeneral);
            generalPane.setManaged(isGeneral);
        });
    }

    // ------------------------------------------------------------------
    // UI-Aufbau (programmatisch, damit neue Settings nur wenige Zeilen kosten)
    // ------------------------------------------------------------------

    private void buildSettingsPanel() {
        CheckBox cameraWb = new CheckBox("Use camera white balance");
        // Translator: nur "CAMERA" (case-insensitive) schaltet use_camera_wb=1
        cameraWb.selectedProperty().addListener((obs, o, n)
                -> model.setWhiteBalance(Boolean.TRUE.equals(n) ? "CAMERA" : "NONE"));
        model.whiteBalanceProperty().addListener((obs, o, n)
                -> cameraWb.setSelected("CAMERA".equalsIgnoreCase(n)));

        settingsBox.getChildren().addAll(
                settingsCard("Demosaicing",
                        choice("Interpolation quality (user_qual)", QUALITY_VALUES, model.rawQualityProperty(),
                                v -> Integer.parseInt(v.replaceAll("\\D+", ""))),
                        check("Half size output (fast preview)", model.halfSizeOutputProperty()),
                        check("Fix colors in highlights", model.fixColorsHighlightsProperty()),
                        spinner("Median filter passes (med_passes)", 0, 10, 0, model.medianFilterPassesProperty()),
                        check("16-bit output (output_bps)", model.sixteenBitsImageProperty())),
                settingsCard("White balance & color",
                        cameraWb,
                        check("Auto white balance (use_auto_wb)", model.autoWhitebalanceProperty()),
                        choice("Output color space (output_color)", COLOR_SPACE_VALUES, model.outputColorSpaceProperty(),
                                v -> Integer.parseInt(v.replaceAll("\\D+", ""))),
                        spinner("Saturation 0-20000, 0=dcraw default (user_sat)",
                                0, 20000, 0, model.saturationProperty())),
                settingsCard("Brightness / exposure",
                        check("Auto brightness (auto_bright)", model.autoBrightnessProperty()),
                        slider("Auto brightness threshold (auto_bright_thr)", 0.0, 0.1, model.autoBrightnessThresholdProperty()),
                        slider("Brightness (bright)", 0.25, 4.0, model.brightNessProperty()),
                        check("Exposure correction (exp_correc)", model.enableExposureCorrectionProperty()),
                        spinner("Exposure correction mode 0/1 (exp_correc)", 0, 1, 1, model.exposureCorrectionProperty()),
                        slider("Exposure shift (exp_shift)", 0.25, 8.0, model.exposureShiftProperty()),
                        slider("Highlight preservation (exp_preser)", 0.0, 1.0, model.expoCorrectionHighlightProperty()),
                        check("Enable black point (user_black)", model.enableBlackPointProperty()),
                        spinner("Black point", 0, 4096, 0, model.blackPointProperty())),
                settingsCard("Noise reduction",
                        choice("FBDD noise reduction (fbdd_noiserd)", NOISE_VALUES, model.noiseReductionProperty(),
                                v -> Integer.parseInt(v.replaceAll("\\D+", "")))),
                settingsCard("Output format (ImageIO mode)",
                        check("Output TIFF instead of bitmap (output_tiff)", model.outputTiffProperty())));
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
            card.getChildren().add(row);
        }
        return card;
    }

    private Node check(String text, javafx.beans.property.BooleanProperty prop) {
        CheckBox cb = new CheckBox(text);
        cb.selectedProperty().bindBidirectional(prop);
        return cb;
    }

    private Node slider(String text, double min, double max, javafx.beans.property.DoubleProperty prop) {
        Slider slider = new Slider(min, max, prop.get());
        slider.setShowTickLabels(false);
        slider.setShowTickMarks(true);
        slider.setMajorTickUnit((max - min) / 4);
        slider.setPrefWidth(200);
        slider.valueProperty().bindBidirectional(prop);
        Label value = new Label(String.format("%.3f", prop.get()));
        value.getStyleClass().add("slider-value");
        value.setPrefWidth(52);
        prop.addListener((obs, o, n) -> value.setText(String.format("%.3f", n.doubleValue())));
        HBox row = new HBox(10, labeled(text), slider, value);
        return row;
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
        box.getSelectionModel().selectFirst();
        box.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) prop.setValue(converter.apply(n));
        });
        return new HBox(10, labeled(text), box);
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
     * Der ProgressIndicator wird ab Task-Start eingeblendet (das View hat
     * dadurch schon seine endgueltige Groesse) und nach Abschluss versteckt.
     */
    private void runDecode(RawDecoderSettings settings, java.util.function.Consumer<Image> onDone,
                           ProgressIndicator progress) {
        if (sampleFile == null) {
            return;
        }
        statusLabel.setTextFill(Color.web("#9f9f9f"));
        statusLabel.setText("Decoding…");
        progress.setVisible(true);
        Path file = sampleFile;
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() throws Exception {
                LibrawImage librawImage = new LibrawImage(file.toString(), new ConcurrentHashMap<>());
                librawImage.setSettingsOverride(settings);
                int[] pixels = librawImage.readPixelData();
                int w = librawImage.getImageWidth();
                int h = librawImage.getImageHeight();
                WritableImage image = new WritableImage(w, h);
                image.getPixelWriter().setPixels(0, 0, w, h,
                        PixelFormat.getIntArgbInstance(), pixels, 0, w);
                return image;
            }
        };
        task.setOnSucceeded(e -> {
            progress.setVisible(false);
            onDone.accept(task.getValue());
            statusLabel.setTextFill(Color.web("#9f9f9f"));
            statusLabel.setText("Ready");
        });
        task.setOnFailed(e -> {
            progress.setVisible(false);
            statusLabel.setTextFill(Color.web("#ff6961"));
            statusLabel.setText("Decode failed: " + task.getException().getMessage());
            LOG.log(Level.SEVERE, "Decode failed", task.getException());
        });
        executor.submit(task);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.show();
    }
}