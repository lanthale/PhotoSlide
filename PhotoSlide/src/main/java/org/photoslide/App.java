package org.photoslide;

import java.io.File;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javafx.application.Preloader.ProgressNotification;
import javafx.scene.image.Image;
import javafx.stage.WindowEvent;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ServiceRegistry;
import org.h2.fulltext.FullText;
import org.photoslide.datamodel.customformats.psdsupport.PSDImageLoaderFactory;
import org.photoslide.datamodel.customformats.tiffsupport.TIFFImageLoaderFactory;
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
    private static boolean SEARCHINDEXFINISHED = false;
    private static boolean MAXIMIZED = true;
    private static final String NODE_NAME = "PhotoSlide";
    private FXMLLoader fxmlLoader;
    private Image iconImage;

    @Override
    public void stop() throws Exception {
        super.stop(); //To change body of generated methods, choose Tools | Templates.        
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
        File dbFile = new File(Utility.getAppData() + File.separator + "SearchMediaFilesDB.mv.db");
        if (dbFile.exists() == false) {
            initDB();
        } else {
            try {
                Class.forName("org.h2.Driver");
                searchDBConnection = DriverManager.getConnection("jdbc:h2:" + Utility.getAppData() + File.separator + "SearchMediaFilesDB", "", "");
                checkSearchDBStructure();
            } catch (ClassNotFoundException | SQLException e) {
                Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, e);
            }
        }
        notifyPreloader(new ProgressNotification(0.6));
        setDefaultTIFFCodec();
        notifyPreloader(new ProgressNotification(0.7));
        TIFFImageLoaderFactory.install();
        PSDImageLoaderFactory.install();
        notifyPreloader(new ProgressNotification(0.8));
    }

    @Override
    public void start(Stage stage) throws IOException {
        restoreSettings(stage, fxmlLoader.getController());
        notifyPreloader(new ProgressNotification(0.9));

        stage.setOnCloseRequest((final WindowEvent event) -> {
            MainViewController controller = fxmlLoader.getController();
            saveSettings(stage, controller);
            controller.Shutdown();
        });

        scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/org/photoslide/fxml/MainView.css").toExternalForm());

        stage.setScene(scene);
        stage.getIcons().add(iconImage);
        stage.show();

    }

    public static void saveSettings(Stage stage, MainViewController controller) {
        /*try {
            searchDBConnection.close();
        } catch (SQLException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }*/
        controller.saveSettings();
        Preferences preferences = Preferences.userRoot().node(NODE_NAME);
        preferences.putDouble(WINDOW_POSITION_X, stage.getX());
        preferences.putDouble(WINDOW_POSITION_Y, stage.getY());
        preferences.putDouble(WINDOW_WIDTH, stage.getWidth());
        preferences.putDouble(WINDOW_HEIGHT, stage.getHeight());
        preferences.putBoolean("SEARCHINDEXFINISHED", SEARCHINDEXFINISHED);
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
        SEARCHINDEXFINISHED = pref.getBoolean("SEARCHINDEXFINISHED", SEARCHINDEXFINISHED);
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
            Logger logger = Logger.getLogger("org.photoslide");
            File logFile = new File(appData + File.separator + "photoslide.log");
            Handler handler = new FileHandler(logFile.getAbsolutePath(), 50000, 1, true);
            logger.addHandler(handler);
            logger.setLevel(Level.ALL);
            handler.setFormatter(new SimpleFormatter());
            System.setProperty("javafx.preloader", PSPreloader.class.getCanonicalName());
            Application.launch(App.class, args);
            //com.sun.javafx.application.LauncherImpl.launchApplication(App.class, PSPreloader.class, args);
        } catch (IOException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void initDB() {
        try {
            //setup search database
            Class.forName("org.h2.Driver");
            searchDBConnection = DriverManager.getConnection("jdbc:h2:" + Utility.getAppData() + File.separator + "SearchMediaFilesDB;DB_CLOSE_ON_EXIT=FALSE", "", "");
            Statement stat = searchDBConnection.createStatement();
            stat.execute("CREATE ALIAS FT_INIT FOR \"org.h2.fulltext.FullText.init\"");
            //stat.execute("CALL FT_INIT()");
            FullText.init(searchDBConnection);
            FullText.setIgnoreList(searchDBConnection, "to,this");
            FullText.setWhitespaceChars(searchDBConnection, " ;-:/.");            
            stat = searchDBConnection.createStatement();
            //fulltext search
            stat.execute("""
                         CREATE TABLE
                             "PUBLIC".MEDIAFILES
                             (
                                 collectionname VARCHAR(255) NOT NULL,
                                 name VARCHAR(255) NOT NULL,
                                 pathStorage VARCHAR(1000) NOT NULL,
                                 title VARCHAR(255),
                                 keywords VARCHAR(255),
                                 camera VARCHAR(255),
                                 rating INTEGER,
                                 recordTime TIMESTAMP,
                                 creationTime TIMESTAMP,
                                 places VARCHAR(255),
                                 faces VARCHAR(255),
                                 metadata VARCHAR(4000),
                                 PRIMARY KEY (collectionname, name, pathStorage)
                             )
                         """);            
            FullText.createIndex(searchDBConnection, "PUBLIC", "MEDIAFILES", null);
            //stat.execute("CALL FT_CREATE_INDEX('PUBLIC', 'MEDIAFILES', NULL)");
        } catch (SQLException | ClassNotFoundException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void checkSearchDBStructure() throws SQLException {
        String[] colNames = {"collectionname", "name", "pathStorage", "title", "keywords", "camera", "rating", "recordTime", "creationTime", "places", "faces", "metadata"};

        DatabaseMetaData databaseMetaData = searchDBConnection.getMetaData();
        ResultSet columns = databaseMetaData.getColumns(null, null, "MEDIAFILES", null);
        int columnQTY = 0;
        while (columns.next()) {
            columnQTY++;
        }
        if (columnQTY != colNames.length) {
            Statement stat = searchDBConnection.createStatement();
            try {
                stat.execute("DROP TABLE MEDIAFILES");
            } catch (SQLException ef) {
            }
            try {
                stat.execute("DROP ALIAS FT_INIT");
            } catch (SQLException ef) {
            }
            initDB();
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

    public static void setSearchIndexFinished(boolean finished) {
        SEARCHINDEXFINISHED = finished;
    }

}
