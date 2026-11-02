package org.photoslide;

import java.io.File;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.application.Preloader.ProgressNotification;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Paint;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ServiceRegistry;
import org.h2.fulltext.FullText;
import org.libheiffx.HEIFImageLoaderFactory;
import org.librawfx.RAWImageLoaderFactory;
import org.photoslide.datamodel.customformats.psdsupport.PSDImageLoaderFactory;
import org.photoslide.datamodel.customformats.tiffsupport.TIFFImageLoaderFactory;
import org.photoslide.datamodel.customformats.webpsupport.WEBPImageLoaderFactory;
import org.photoslide.preferences.RawSettingsStore;
import org.photoslide.pspreloader.PSPreloader;

/**
 * JavaFX App
 */
public class App extends Application {

    public static Scene scene;
    private static Connection searchDBConnection;
    Parent root;

    private static final String WINDOW_POSITION_X = "Window_Position_X";
    private static final String WINDOW_POSITION_Y = "Window_Position_Y";
    private static final String WINDOW_WIDTH = "Window_Width";
    private static final String WINDOW_HEIGHT = "Window_Height";
    private static final double DEFAULT_X = 100;
    private static final double DEFAULT_Y = 200;
    private static final double DEFAULT_WIDTH = 1280;
    private static final double DEFAULT_HEIGHT = 800;
    private static final HashMap<String, LocalDate> SEARCHINDEXFINISHEDMAP = new HashMap<>();
    private static boolean MAXIMIZED = true;
    // 3: metadata without icafe4j number lists
    private static final int SEARCHDB_VERSION = 3;
    /** Characters that separate the words of the full text index. */
    public static final String SEARCH_WHITESPACE_CHARS = " ;-:/.,\\";
    private static final String NODE_NAME = "PhotoSlide";
    private FXMLLoader fxmlLoader;
    private Image iconImage;

    @Override
    public void stop() throws Exception {
        super.stop(); //To change body of generated methods, choose Tools | Templates.                
        closeSearchDBConnection();
    }

    @Override
    public void init() throws Exception {
        super.init(); //To change body of generated methods, choose Tools | Templates.              
        notifyPreloader(new ProgressNotification(0.2));

        notifyPreloader(new ProgressNotification(0.3));
        fxmlLoader = new FXMLLoader(getClass().getResource("/org/photoslide/fxml/MainView.fxml"));
        notifyPreloader(new ProgressNotification(0.4));
        root = (Parent) fxmlLoader.load();
        notifyPreloader(new ProgressNotification(0.5));
        iconImage = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
        try {
            searchDBConnection = openSearchDBConnection();
            prepareSearchDB(searchDBConnection);
        } catch (SQLException e) {
            // unreadable or written by an unsupported H2 version: the index is only a cache, build it again
            Logger.getLogger(App.class.getName()).log(Level.WARNING, "Search database is rebuilt", e);
            closeSearchDBConnection();
            deleteSearchDBFiles();
            try {
                searchDBConnection = openSearchDBConnection();
                prepareSearchDB(searchDBConnection);
            } catch (SQLException ex) {
                Logger.getLogger(App.class.getName()).log(Level.SEVERE, "Cannot create search database", ex);
                closeSearchDBConnection();
            }
        }
        notifyPreloader(new ProgressNotification(0.6));

        TIFFImageLoaderFactory.install();
        PSDImageLoaderFactory.install();
        WEBPImageLoaderFactory.install();

        setDefaultTIFFCodec();
        notifyPreloader(new ProgressNotification(0.7));

        try {
            RAWImageLoaderFactory.install();
            /*Map<String, RawDecoderSettings> settings = RAWImageLoaderFactory.getDecoderSettings();
            RawDecoderSettings sigma = new RawDecoderSettings();
            sigma.setEnableExposureCorrection(false);
            sigma.setExposureShift(1.2f);
            sigma.setAutoBrightness(true);
            sigma.setBrightNess(1.0f);
            sigma.setAutoWhitebalance(false);
            sigma.setWhiteBalance("CAMERA");
            settings.put("Sigma DP2 Merrill", sigma);*/
            RawSettingsStore.loadAndApply();
            HEIFImageLoaderFactory.install();
        } catch (UnsatisfiedLinkError e) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
        }

        if (Logger.getGlobal().getLevel() == Level.FINEST) {
            printAllCodecs();
        }

        //create cache dir
        if (Files.exists(Path.of(Utility.getAppData() + File.separatorChar + "cache")) == false) {
            Files.createDirectory(Path.of(Utility.getAppData() + File.separatorChar + "cache"));
        }

        notifyPreloader(new ProgressNotification(0.8));
    }

    @Override
    public void start(Stage stage) throws IOException {
        restoreSettings(stage, fxmlLoader.getController());
        notifyPreloader(new ProgressNotification(0.9));

        scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        scene.setFill(Paint.valueOf("rgb(80, 80, 80)"));
        final KeyCombination keyComb = new KeyCodeCombination(KeyCode.Q, KeyCombination.META_DOWN);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, (event) -> {
            if (keyComb.match(event)) {
                event.consume();
                App.saveSettings((Stage) scene.getWindow(), fxmlLoader.getController());
                MainViewController controller = fxmlLoader.getController();
                controller.Shutdown();
                Platform.exit();
            }
        });
        stage.setScene(scene);
        stage.getIcons().add(iconImage);
        stage.show();
        FxStallWatchdog.start();

    }

    public static void saveSettings(Stage stage, MainViewController controller) {
        controller.saveSettings();
        Preferences preferences = Preferences.userRoot().node(NODE_NAME);
        preferences.putDouble(WINDOW_POSITION_X, stage.getX());
        preferences.putDouble(WINDOW_POSITION_Y, stage.getY());
        preferences.putDouble(WINDOW_WIDTH, stage.getWidth());
        preferences.putDouble(WINDOW_HEIGHT, stage.getHeight());
        SEARCHINDEXFINISHEDMAP.entrySet().iterator().forEachRemaining((t) -> {
            preferences.putLong(t.getKey(), t.getValue().toEpochDay());
        });
        preferences.putBoolean("MAXIMIZED", stage.isMaximized());
        try {
            preferences.flush();
        } catch (BackingStoreException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static void restoreSettings(Stage stage, MainViewController controller) {
        // Pull the saved preferences and set the stage size and start location
        controller.restoreSettings();
        Preferences pref = Preferences.userRoot().node(NODE_NAME);
        double x = pref.getDouble(WINDOW_POSITION_X, DEFAULT_X);
        double y = pref.getDouble(WINDOW_POSITION_Y, DEFAULT_Y);
        double width = pref.getDouble(WINDOW_WIDTH, DEFAULT_WIDTH);
        double height = pref.getDouble(WINDOW_HEIGHT, DEFAULT_HEIGHT);
        try {
            List<String> list = Arrays.asList(pref.keys());
            list.stream().filter((t) -> t.startsWith("SEARCHINDEX_")).forEach((t) -> {
                SEARCHINDEXFINISHEDMAP.put(t, LocalDate.ofEpochDay(pref.getLong(t, 0)));
            });
        } catch (BackingStoreException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }

        MAXIMIZED = pref.getBoolean("MAXIMIZED", MAXIMIZED);
        stage.setMaximized(MAXIMIZED);
        stage.setX(x);
        stage.setY(y);
        stage.setWidth(width);
        stage.setHeight(height);
    }

    public static void main(String[] args) {
        try {
            String appData = Utility.getAppData();
            Logger logger = Logger.getLogger("org");
            File logFile = new File(appData + File.separator + "photoslide.log");
            Handler handler = new FileHandler(logFile.getAbsolutePath(), 50000, 1, true);

            // Default: INFO (jpackage). In der IDE per -Dphotoslide.loglevel=ALL überschreiben
            Level level = resolveLogLevel();
            logger.setLevel(level);
            handler.setLevel(level);          // wichtig: Handler filtert separat!
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);

            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                Logger.getLogger(App.class.getName())
                        .log(Level.SEVERE, "Uncaught exception in thread " + t.getName(), e);
            });

            System.setProperty("javafx.preloader", PSPreloader.class.getCanonicalName());
            Application.launch(App.class, args);
        } catch (IOException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static Level resolveLogLevel() {
        String value = System.getProperty("photoslide.loglevel", "INFO");
        try {
            return Level.parse(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Level.INFO;
        }
    }

    /**
     * Opens a connection to the search database. The indexer uses its own
     * connection, so a search does not wait for the indexer.
     */
    public static Connection openSearchDBConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:h2:" + Utility.getAppData() + File.separator + "SearchMediaFilesDB", "", "");
    }

    private static void closeSearchDBConnection() {
        if (searchDBConnection != null) {
            try {
                searchDBConnection.close();
            } catch (SQLException ex) {
            }
            searchDBConnection = null;
        }
    }

    private static void deleteSearchDBFiles() {
        File[] files = new File(Utility.getAppData()).listFiles((dir, name) -> name.startsWith("SearchMediaFilesDB."));
        if (files != null) {
            Arrays.asList(files).forEach(File::delete);
        }
    }

    /**
     * Creates the search database, or creates it again if it has another
     * schema version (it is only a cache of the media files).
     */
    public static void prepareSearchDB(Connection con) throws SQLException {
        if (searchDBVersion(con) == SEARCHDB_VERSION) {
            return;
        }
        try (Statement stat = con.createStatement()) {
            try {
                FullText.dropAll(con);
            } catch (SQLException ef) {
                // no full text index yet
            }
            stat.execute("DROP TABLE IF EXISTS MEDIAFILES");
            stat.execute("DROP TABLE IF EXISTS SCHEMAINFO");
            stat.execute("DROP ALIAS IF EXISTS FT_INIT");
            FullText.init(con);
            FullText.setIgnoreList(con, "to,this");
            FullText.setWhitespaceChars(con, SEARCH_WHITESPACE_CHARS);
            stat.execute("CREATE TABLE \"PUBLIC\".MEDIAFILES"
                    + "("
                    + "pathStorage VARCHAR(1000) PRIMARY KEY,"
                    + "collectionname VARCHAR(1000) NOT NULL,"
                    + "name VARCHAR(255) NOT NULL,"
                    + "title VARCHAR(1000),"
                    + "keywords VARCHAR(4000),"
                    + "camera VARCHAR(255),"
                    + "rating INTEGER,"
                    + "recordTime TIMESTAMP,"
                    + "creationTime TIMESTAMP,"
                    + "places VARCHAR(1000),"
                    + "gpsposition VARCHAR(255)," //Format: "gpslatAsDouble;gpslongAsDouble"
                    + "faces VARCHAR(1000),"
                    + "metadata CLOB,"
                    + "lastModified BIGINT," // newest modification time of the file and its sidecar in ms
                    + "fileSize BIGINT"
                    + ")");
            stat.execute("CREATE INDEX MEDIAFILES_COLLECTION ON MEDIAFILES(collectionname)");
            // only text columns: every indexed column costs words in FT.WORDS and FT.MAP on each insert
            FullText.createIndex(con, "PUBLIC", "MEDIAFILES", "PATHSTORAGE,TITLE,KEYWORDS,CAMERA,PLACES,FACES,METADATA");
            stat.execute("CREATE TABLE SCHEMAINFO(version INTEGER)");
            stat.execute("INSERT INTO SCHEMAINFO VALUES(" + SEARCHDB_VERSION + ")");
        }
    }

    private static int searchDBVersion(Connection con) {
        try (Statement stat = con.createStatement(); ResultSet rs = stat.executeQuery("SELECT version FROM SCHEMAINFO")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            return 0;
        }
    }

    private void setDefaultTIFFCodec() {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        ImageReaderSpi jaiProvider = lookupProviderByName(registry, "com.github.jaiimageio.impl.plugins.tiff.TIFFImageReaderSpi");
        ImageReaderSpi twelvProvider = lookupProviderByName(registry, "com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi");
        if (jaiProvider != null && twelvProvider != null) {
            registry.deregisterServiceProvider(jaiProvider);
        }
    }

    private void printAllCodecs() {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        Iterator<ImageReaderSpi> readers
                = registry.getServiceProviders(ImageReaderSpi.class, true);
        while (readers.hasNext()) {
            ImageReaderSpi spi = readers.next();
            Logger.getLogger(App.class.getName()).log(Level.FINEST, spi.getClass().getName()
                    + " | " + spi.getDescription(java.util.Locale.getDefault())
                    + " | Formate: " + Arrays.toString(spi.getFormatNames())
                    + " | Endungen: " + Arrays.toString(spi.getFileSuffixes()));
        }
    }

    private static <T> T lookupProviderByName(final ServiceRegistry registry, final String providerClassName) {
        try {
            return (T) registry.getServiceProviderByClass(Class.forName(providerClassName));
        } catch (ClassNotFoundException ignore) {
            return null;
        }
    }

    public static Connection getSearchDBConnection() {
        return searchDBConnection;
    }

    public static LocalDate getSEARCHINDEXFINISHED(String collectionName) {
        return SEARCHINDEXFINISHEDMAP.get("SEARCHINDEX_" + collectionName);
    }

    public static void setSEARCHINDEXFINISHED(String collectionName, LocalDate SEARCHINDEXFINISHED) {
        SEARCHINDEXFINISHEDMAP.put("SEARCHINDEX_" + collectionName, SEARCHINDEXFINISHED);
    }

}
