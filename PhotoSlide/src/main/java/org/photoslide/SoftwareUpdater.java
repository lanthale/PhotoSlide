/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide;

import java.awt.Desktop;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;
import javafx.util.Duration;
import javax.net.ssl.HttpsURLConnection;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 *
 * @author selfemp
 */
public class SoftwareUpdater {

    private final ExecutorService executorParallel;
    private Task<String> checkTask;
    private Task<String> downloadTask;
    private final MainViewController controller;
    private final Image dialogIcon;

    public SoftwareUpdater(ExecutorService executorParallel, MainViewController mc) {
        this.executorParallel = executorParallel;
        this.controller = mc;
        dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
    }

    public void Shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
        }
        if (downloadTask != null) {
            downloadTask.cancel();
        }
    }

    public void checkForSoftwareUpdates() {
        if (checkTask != null) {
            return;
        }
        checkTask = new Task<>() {
            @Override
            protected String call() throws Exception {
                Utility util = new Utility();
                String actVersion = util.getAppVersion();
                if (actVersion.contains("SNAPSHOT")) {
                    return "";
                }
                InputStream inputStream = null;
                HttpsURLConnection conn = null;
                String httpsURL = "";
                URL myUrl = null;
                String newVersion = "";
                String version = "";
                int parseInt = 0;
                int count = (int) actVersion.chars().filter(ch -> ch == '.').count();
                switch (count) {
                    case 1:
                        newVersion = checkMajorVersion(actVersion);
                        actVersion = newVersion + ".0";
                    case 2:
                        newVersion = checkMinorVersion(actVersion);
                        break;
                    default: {
                        try {
                            version = actVersion.substring(0, actVersion.indexOf("."));
                            parseInt = Integer.parseInt(version);
                            parseInt = parseInt + 1;
                            newVersion = parseInt + ".0";
                            httpsURL = "https://github.com/lanthale/PhotoSlide/releases/download/v" + newVersion + "/PhotoSlide-" + newVersion + ".pkg";
                            myUrl = new URL(httpsURL);
                            conn = (HttpsURLConnection) myUrl.openConnection();
                            inputStream = conn.getInputStream();
                        } catch (MalformedURLException ex) {
                            newVersion = "";
                            Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IOException ex) {
                            newVersion = "";
                            Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    break;

                }
                if (conn != null) {
                    conn.disconnect();
                }
                return newVersion;
            }
        };
        checkTask.setOnSucceeded(
                (t) -> {
                    if (t.getSource().getValue() != null) {
                        String newversion = checkTask.getValue();
                        if (newversion.equalsIgnoreCase("")) {
                            Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.INFO, "No new version found! " + newversion);
                            return;
                        }
                        PauseTransition pause = new PauseTransition(Duration.seconds(6));
                        pause.setOnFinished((k) -> {
                            Alert confirmDiaglog = new Alert(Alert.AlertType.CONFIRMATION, "Newer version of software available", ButtonType.YES, ButtonType.NO);
                            confirmDiaglog.setHeaderText("Newer version of Photoslide available");
                            confirmDiaglog.setGraphic(new FontIcon("ti-dropbox-alt:40"));
                            confirmDiaglog.setContentText("Version: " + newversion + " is ready for download.\nDo you want to start the downlod and installation ?");
                            confirmDiaglog.setTitle("Downloadmanager");
                            DialogPane dialogPane = confirmDiaglog.getDialogPane();
                            dialogPane.getStylesheets().add(
                                    getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
                            Utility.centerChildWindowOnStage((Stage) confirmDiaglog.getDialogPane().getScene().getWindow(), (Stage) controller.getBookmarksBoardButton().getScene().getWindow());
                            confirmDiaglog.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
                            Platform.runLater(() -> {
                                Optional<ButtonType> result = confirmDiaglog.showAndWait();
                                if (result.get() == ButtonType.YES) {
                                    downloadUpdate(newversion);
                                }
                            });
                        });
                        pause.play();
                    }
                });
        executorParallel.submit(checkTask);
    }

    private String checkMajorVersion(String actVersion) {
        InputStream inputStream = null;
        HttpsURLConnection conn = null;
        String httpsURL = "";
        URL myUrl = null;
        String newVersion = "";
        String version = "";
        int parseInt = 0;
        try {
            version = actVersion.substring(actVersion.lastIndexOf(".") + 1);
            parseInt = Integer.parseInt(version);
            parseInt = parseInt + 1;
            newVersion = actVersion.substring(0, actVersion.lastIndexOf(".") + 1) + parseInt;
            httpsURL = "https://github.com/lanthale/PhotoSlide/releases/download/v" + newVersion + "/PhotoSlide-" + newVersion + ".pkg";
            myUrl = new URL(httpsURL);
            conn = (HttpsURLConnection) myUrl.openConnection();
            inputStream = conn.getInputStream();
        } catch (FileNotFoundException ex2) {
            try {
                version = actVersion.substring(actVersion.lastIndexOf(".") + 1);
                newVersion = actVersion + ".0";
                httpsURL = "https://github.com/lanthale/PhotoSlide/releases/download/v" + newVersion + "/PhotoSlide-" + newVersion + ".pkg";
                myUrl = new URL(httpsURL);
                conn = (HttpsURLConnection) myUrl.openConnection();
                inputStream = conn.getInputStream();
            } catch (FileNotFoundException ex3) {
                newVersion = "";
            } catch (MalformedURLException ex) {
                newVersion = "";
                Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                newVersion = "";
                Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (MalformedURLException ex) {
            newVersion = "";
            Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            newVersion = "";
            Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.SEVERE, null, ex);
        }
        return newVersion;
    }

    private String checkMinorVersion(String actVersion) {
        InputStream inputStream = null;
        HttpsURLConnection conn = null;
        String httpsURL = "";
        URL myUrl = null;
        String newVersion = "-1";
        String backVersion = "-1";
        String version = "";
        String maxMinorVersion;
        int parseInt = 0;
        int errorcount = 0;
        version = actVersion.substring(actVersion.lastIndexOf(".") + 1);
        parseInt = Integer.parseInt(version);
        while (errorcount < 1) {
            parseInt = parseInt + 1;
            newVersion = actVersion.substring(0, actVersion.lastIndexOf(".") + 1) + parseInt;
            backVersion = checkVersionTech(newVersion);
            if (backVersion == null) {
                errorcount++;
            }
        }
        maxMinorVersion = actVersion.substring(0, actVersion.lastIndexOf(".") + 1) + (parseInt-1);
        //Check major version afterwards
        try {
            version = actVersion.substring(actVersion.lastIndexOf(".") + 1);
            String version2 = actVersion.substring(actVersion.indexOf(".") + 1, actVersion.lastIndexOf("."));
            parseInt = Integer.parseInt(version2);
            parseInt = parseInt + 1;
            newVersion = actVersion.substring(0, actVersion.indexOf(".") + 1) + parseInt;
            httpsURL = "https://github.com/lanthale/PhotoSlide/releases/download/v" + newVersion + "/PhotoSlide-" + newVersion + ".pkg";
            myUrl = new URL(httpsURL);
            conn = (HttpsURLConnection) myUrl.openConnection();
            inputStream = conn.getInputStream();
        } catch (FileNotFoundException ex3) {
            newVersion = "";
        } catch (MalformedURLException ex) {
            newVersion = "";
            Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            newVersion = "";
            Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (newVersion.equalsIgnoreCase("")) {
            newVersion = actVersion.substring(0, actVersion.indexOf(".") + 1) + (parseInt - 1);
        } else {
            newVersion = actVersion.substring(0, actVersion.indexOf(".") + 1) + parseInt;
        }
        float foundMinourVersion = Float.parseFloat(maxMinorVersion.substring(0, maxMinorVersion.lastIndexOf(".")));
        float foundMajorVersion = Float.parseFloat(newVersion);
        if (foundMajorVersion > foundMinourVersion) {
            newVersion = actVersion.substring(0, actVersion.indexOf(".") + 1) + parseInt;
        } else {
            newVersion = maxMinorVersion;
        }
        //Check if newVersion is better or nor
        if (actVersion.equalsIgnoreCase(newVersion)) {
            newVersion = "";
        }
        return newVersion;
    }

    private String checkVersionTech(String newVersion) {
        InputStream inputStream = null;
        HttpsURLConnection conn = null;
        String httpsURL = "";
        URL myUrl = null;
        String version = "";
        int parseInt = 0;
        try {
            httpsURL = "https://github.com/lanthale/PhotoSlide/releases/download/v" + newVersion + "/PhotoSlide-" + newVersion + ".pkg";
            myUrl = new URL(httpsURL);
            conn = (HttpsURLConnection) myUrl.openConnection();
            inputStream = conn.getInputStream();
        } catch (FileNotFoundException ex2) {
            newVersion = null;
        } catch (MalformedURLException ex) {
            newVersion = null;
            Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            newVersion = null;
            Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.SEVERE, null, ex);
        }
        return newVersion;
    }

    private void downloadUpdate(String nextAppVersion) {
        Alert downloadDialog = new Alert(Alert.AlertType.INFORMATION, "Download software", ButtonType.CANCEL);
        downloadDialog.setHeaderText("Downloading new Photoslide software");
        downloadDialog.setGraphic(new FontIcon("ti-dropbox-alt:40"));
        downloadDialog.setTitle("Downloadmanager");
        ProgressBar pgr = new ProgressBar();
        downloadDialog.getDialogPane().setContent(pgr);
        DialogPane dialogPane = downloadDialog.getDialogPane();
        Stage stage = (Stage) downloadDialog.getDialogPane().getScene().getWindow();
        stage.getIcons().add(dialogIcon);
        dialogPane.getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        Utility.centerChildWindowOnStage((Stage) downloadDialog.getDialogPane().getScene().getWindow(), (Stage) controller.getBookmarksBoardButton().getScene().getWindow());
        downloadDialog.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));

        Button cancleButton = (Button) downloadDialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancleButton.setOnAction((t) -> {
            downloadTask.cancel();
            downloadDialog.close();
        });
        downloadDialog.show();

        downloadTask = new Task<String>() {
            @Override
            protected String call() throws Exception {
                OutputStream outputStream = null;
                InputStream inputStream = null;
                HttpsURLConnection conn = null;
                String httpsURL = "";
                URL myUrl = null;
                String filename = "";
                String tempDir = System.getProperty("java.io.tmpdir");
                try {
                    Utility util = new Utility();
                    String OS = System.getProperty("os.name").toUpperCase();
                    if (OS.contains("WIN")) {
                        httpsURL = "https://github.com/lanthale/PhotoSlide/releases/download/v" + nextAppVersion + "/PhotoSlide-" + nextAppVersion + ".msi";
                        filename = "PhotoSlide-" + nextAppVersion + ".msi";
                    } else if (OS.contains("MAC")) {
                        httpsURL = "https://github.com/lanthale/PhotoSlide/releases/download/v" + nextAppVersion + "/PhotoSlide-" + nextAppVersion + ".pkg";
                        filename = "PhotoSlide-" + nextAppVersion + ".pkg";
                    } else if (OS.contains("NUX")) {
                        httpsURL = "https://github.com/lanthale/PhotoSlide/releases/download/v" + nextAppVersion + "/PhotoSlide-" + nextAppVersion + ".deb";
                        filename = "PhotoSlide-" + nextAppVersion + ".deb";
                    } else {
                        httpsURL = "";
                    }
                    myUrl = new URL(httpsURL);
                    conn = (HttpsURLConnection) myUrl.openConnection();
                    inputStream = conn.getInputStream();
                    // Open local file writer                    
                    outputStream = new FileOutputStream(tempDir + File.separator + filename);
                    // Limiting byte written to file per loop
                    byte[] buffer = new byte[2048];
                    // Increments file size
                    int length;
                    int downloaded = 0;
                    int contentLength = conn.getContentLength();
                    updateProgress(0, contentLength);
                    // Looping until server finishes
                    while ((length = inputStream.read(buffer)) != -1) {
                        if (this.isCancelled()) {
                            return null;
                        }
                        outputStream.write(buffer, 0, length);
                        downloaded += length;
                        updateProgress(downloaded, contentLength);

                    }
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(SoftwareUpdater.class
                            .getName()).log(Level.SEVERE, "Cannot find downloaded file", ex);

                } catch (IOException ex) {
                    Logger.getLogger(SoftwareUpdater.class
                            .getName()).log(Level.SEVERE, "Cannot access downloaded file", ex);
                } finally {
                    try {
                        if (outputStream != null) {
                            outputStream.close();
                        }
                        if (conn != null) {
                            conn.disconnect();

                        }
                    } catch (IOException ex) {
                        Logger.getLogger(SoftwareUpdater.class
                                .getName()).log(Level.SEVERE, null, ex);
                    }
                }
                return tempDir + File.separator + filename;
            }
        };
        downloadTask
                .setOnSucceeded((t) -> {
                    if (!Desktop.isDesktopSupported()) {
                        Logger.getLogger(SoftwareUpdater.class
                                .getName()).log(Level.SEVERE, "Desktop is not supported for opening files!");
                        return;
                    }
                    Desktop desktop = Desktop.getDesktop();
                    File f = new File(downloadTask.getValue());
                    if (f.exists()) {
                        try {
                            desktop.open(f);

                        } catch (IOException ex) {
                            Logger.getLogger(SoftwareUpdater.class
                                    .getName()).log(Level.SEVERE, "Cannot open file", ex);
                        }
                    }
                    downloadDialog.close();
                    App.saveSettings((Stage) controller.getBookmarksBoardButton().getScene().getWindow(), controller);
                    System.exit(0);
                });
        downloadTask
                .setOnFailed((t) -> {
                    Logger.getLogger(SoftwareUpdater.class
                            .getName()).log(Level.SEVERE, "Failed to download file", t.getSource().getException());
                });
        pgr.progressProperty().bind(downloadTask.progressProperty());
        executorParallel.submit(downloadTask);
    }

}
