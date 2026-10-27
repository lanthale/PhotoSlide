package org.photoslide.preferences;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.librawdecodersettings.RawDecoderSettings;
import org.librawfx.RAWImageLoaderFactory;
import org.photoslide.Utility;
import org.photoslide.imageops.EditPipeline;

/**
 * Laedt/speichert die RAW-Decoder-Settings pro Kameramodell als JSON im
 * App-Data-Verzeichnis und uebertraegt sie in die globale Map von LibRawFX.
 *
 * Key-Format: "<maker> <model>" (wie von getCameraMaker() gemeldet) oder "Default".
 * Aufruf einmalig beim Start (nach RAWImageLoaderFactory.install()).
 */
public final class RawSettingsStore {

    private static final Logger LOG = Logger.getLogger(RawSettingsStore.class.getName());
    /** FAIL_ON_UNKNOWN_PROPERTIES aus: aeltere JSON-Profile enthalten Felder, die es
     *  im Modell nicht (mehr) gibt (z.B. exposureCorrection). Diese werden ignoriert
     *  statt das ganze Profil abzulehnen; die restlichen Werte laden normal. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    public static final String DEFAULT_KEY = "Default";
    private static final String FILE_NAME = "rawdecodersettings.json";

    private RawSettingsStore() {
    }

    private static File settingsFile() {
        return new File(Utility.getAppData(), FILE_NAME);
    }

    /** Liest die JSON-Datei und wendet alle Profile auf LibRawFX an. */
    public static ConcurrentHashMap<String, RawDecoderSettings> loadAndApply() {
        ConcurrentHashMap<String, RawDecoderSettings> result = new ConcurrentHashMap<>();
        File file = settingsFile();
        if (!file.isFile()) {
            return result;
        }
        try {
            JsonNode root = MAPPER.readTree(file);
            root.fields().forEachRemaining(entry -> {
                try {
                    RawDecoderSettingsFx fx = MAPPER.treeToValue(entry.getValue(), RawDecoderSettingsFx.class);
                    result.put(entry.getKey(), fx.toRawDecoderSettings());
                } catch (IOException ex) {
                    LOG.log(Level.WARNING, "Cannot parse RAW settings for {0}", entry.getKey());
                }
            });
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Cannot read rawdecodersettings.json", ex);
        }
        apply(result);
        return result;
    }

    /** Uebertraegt die Profile in die globale LibRawFX-Settings-Map. */
    public static void apply(Map<String, RawDecoderSettings> profiles) {
        ConcurrentHashMap<String, RawDecoderSettings> map = RAWImageLoaderFactory.getDecoderSettings();
        profiles.forEach((key, settings) -> map.put(key, settings)); // Neue Objekte => Thread-Safety-konform
    }

    /** Speichert alle Profile (Modell -> Werte) als JSON. */
    public static void save(Map<String, RawDecoderSettingsFx> profiles) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            profiles.forEach((key, fx) -> root.set(key, MAPPER.valueToTree(fx)));
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(settingsFile(), root);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Cannot save rawdecodersettings.json", ex);
        }
    }

    /** Laedt ein gespeichertes Profil als Fx-Modell (oder Defaults). */
    public static RawDecoderSettingsFx loadProfile(String cameraKey) {
        File file = settingsFile();
        if (file.isFile()) {
            try {
                JsonNode node = MAPPER.readTree(file).get(cameraKey);
                if (node != null && !node.isNull()) {
                    return MAPPER.treeToValue(node, RawDecoderSettingsFx.class);
                }
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Cannot load profile {0}", cameraKey);
            }
        }
        return new RawDecoderSettingsFx();
    }

    /** Alle vorhandenen Profilnamen (Keys der JSON-Datei, alphabetisch sortiert). */
    public static java.util.List<String> listProfileNames() {
        File file = settingsFile();
        java.util.List<String> names = new java.util.ArrayList<>();
        names.add(DEFAULT_KEY); // "Default" immer anbieten
        if (file.isFile()) {
            try {
                MAPPER.readTree(file).fieldNames().forEachRemaining(name -> {
                    if (!names.contains(name)) {
                        names.add(name);
                    }
                });
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "Cannot list profiles", ex);
            }
        }
        java.util.Collections.sort(names);
        return names;
    }

    public static boolean hasProfile(String cameraKey) {
        return listProfileNames().contains(cameraKey);
    }

    /**
     * Speichert ein einzelnes Profil: JSON + sofortige Aktivierung in der
     * globalen LibRawFX-Map (neues, fertig konfiguriertes Objekt).
     */
    public static void saveProfile(String key, RawDecoderSettingsFx profile) {
        // 1) LibRawFX-Map: Thread-Safety-konform
        RAWImageLoaderFactory.getDecoderSettings().put(key, profile.toRawDecoderSettings());
        // dekodierte Bilder im Cache sind mit den alten Werten entstanden
        EditPipeline.clearCache();
        // 2) Persistenz
        ConcurrentHashMap<String, RawDecoderSettingsFx> profiles = new ConcurrentHashMap<>();
        java.util.List<String> names = listProfileNames();
        if (!names.contains(key)) {
            names.add(key);
        }
        for (String name : names) {
            if (name.equals(key)) {
                profiles.put(name, profile);
            } else {
                RawDecoderSettingsFx fx = loadProfile(name);
                profiles.put(name, fx);
            }
        }
        save(profiles);
    }

    /** Loescht ein Profil aus JSON und LibRawFX-Map. */
    public static void deleteProfile(String key) {
        if (DEFAULT_KEY.equals(key)) {
            LOG.log(Level.WARNING, "The default profile cannot be deleted, only reset.");
            return;
        }
        RAWImageLoaderFactory.getDecoderSettings().remove(key);
        EditPipeline.clearCache();
        ConcurrentHashMap<String, RawDecoderSettingsFx> profiles = new ConcurrentHashMap<>();
        for (String name : listProfileNames()) {
            if (!name.equals(key)) {
                profiles.put(name, loadProfile(name));
            }
        }
        save(profiles);
    }
}