/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
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
    private final static String architecture = System.getProperty("os.arch");
    private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/lanthale/PhotoSlide/releases/latest";
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(java.time.Duration.ofSeconds(20)).build();
    // installer of the newer version, found by the check
    private volatile String downloadUrl;
    private volatile String downloadName;

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
                if (actVersion == null || actVersion.contains("SNAPSHOT")) {
                    return "";
                }
                JsonNode release = latestRelease();
                String newVersion = release.path("tag_name").asText("").replaceFirst("^[vV]", "");
                if (compareVersions(newVersion, actVersion) <= 0) {
                    return "";
                }
                JsonNode asset = installerAsset(release);
                if (asset == null) {
                    Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.INFO, "Version {0} has no installer for {1} {2}", new Object[]{newVersion, System.getProperty("os.name"), architecture});
                    return "";
                }
                downloadUrl = asset.path("browser_download_url").asText();
                downloadName = asset.path("name").asText();
                return newVersion;
            }
        };
        checkTask.setOnFailed((t) -> {
            Logger.getLogger(SoftwareUpdater.class.getName()).log(Level.INFO, "Cannot check for a new version: " + t.getSource().getException());
        });
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
                                if (result.isPresent() && result.get() == ButtonType.YES) {
                                    downloadUpdate(newversion);
                                }
                            });
                        });
                        pause.play();
                    }
                });
        executorParallel.submit(checkTask);
    }

    /**
     * The latest release (no draft, no pre-release) as returned by the GitHub
     * API.
     */
    private static JsonNode latestRelease() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_URL))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "PhotoSlide")
                .timeout(java.time.Duration.ofSeconds(20))
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub answered " + response.statusCode());
        }
        return new ObjectMapper().readTree(response.body());
    }

    /**
     * The installer of the release for this computer: msi on Windows, dmg
     * for the processor on macOS, deb on Linux.
     *
     * @return null if the release has none
     */
    static JsonNode installerAsset(JsonNode release) {
        String os = System.getProperty("os.name").toUpperCase(Locale.ENGLISH);
        boolean arm = architecture.equalsIgnoreCase("aarch64") || architecture.equalsIgnoreCase("arm64");
        for (JsonNode asset : release.path("assets")) {
            String name = asset.path("name").asText("").toLowerCase(Locale.ENGLISH);
            boolean fits;
            if (os.contains("WIN")) {
                fits = name.endsWith(".msi");
            } else if (os.contains("MAC")) {
                fits = name.endsWith(".dmg") && name.contains("aarch64") == arm;
            } else if (os.contains("NUX")) {
                fits = name.endsWith(".deb") && (arm ? name.contains("arm64") || name.contains("aarch64") : name.contains("amd64"));
            } else {
                fits = false;
            }
            if (fits) {
                return asset;
            }
        }
        return null;
    }

    /**
     * Compares versions part by part (2.10.0 is newer than 2.9.1, 2.1 equals
     * 2.1.0); text after the numbers, e.g. "-SNAPSHOT", is ignored.
     */
    static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? leadingNumber(pa[i]) : 0;
            int vb = i < pb.length ? leadingNumber(pb[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int leadingNumber(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        return end == 0 ? 0 : Integer.parseInt(part.substring(0, end));
    }

    private void downloadUpdate(String nextAppVersion) {
        Alert downloadDialog = new Alert(Alert.AlertType.INFORMATION, "Download software", ButtonType.CANCEL);
        downloadDialog.setHeaderText("Downloading new Photoslide " + nextAppVersion + " software");
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
                if (downloadUrl == null) {
                    throw new IOException("No installer found for version " + nextAppVersion);
                }
                Path target = Path.of(System.getProperty("java.io.tmpdir"), downloadName);
                Path part = target.resolveSibling(downloadName + ".part");
                HttpRequest request = HttpRequest.newBuilder(URI.create(downloadUrl))
                        .header("User-Agent", "PhotoSlide")
                        .build();
                HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    response.body().close();
                    throw new IOException("Download of " + downloadUrl + " answered " + response.statusCode());
                }
                long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                long downloaded = 0;
                try (InputStream inputStream = response.body(); OutputStream outputStream = Files.newOutputStream(part)) {
                    byte[] buffer = new byte[65536];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        if (this.isCancelled()) {
                            Files.deleteIfExists(part);
                            return null;
                        }
                        outputStream.write(buffer, 0, length);
                        downloaded += length;
                        updateProgress(downloaded, contentLength);
                    }
                } catch (IOException ex) {
                    Files.deleteIfExists(part);
                    throw ex;
                }
                if (contentLength > 0 && downloaded != contentLength) {
                    Files.deleteIfExists(part);
                    throw new IOException("Download of " + downloadName + " incomplete: " + downloaded + " of " + contentLength + " bytes");
                }
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                return target.toString();
            }
        };
        downloadTask
                .setOnSucceeded((t) -> {
                    if (!Desktop.isDesktopSupported()) {
                        Logger.getLogger(SoftwareUpdater.class
                                .getName()).log(Level.SEVERE, "Desktop is not supported for opening files!");
                        return;
                    }
                    if (downloadTask.getValue() == null) {
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
                    Platform.exit();
                    System.exit(0);
                });
        downloadTask
                .setOnFailed((t) -> {
                    Logger.getLogger(SoftwareUpdater.class
                            .getName()).log(Level.SEVERE, "Failed to download file", t.getSource().getException());
                    downloadDialog.close();
                    Alert error = new Alert(Alert.AlertType.ERROR, "", ButtonType.OK);
                    error.setHeaderText("Download of Photoslide " + nextAppVersion + " failed.\nPlease download it from github.com/lanthale/PhotoSlide/releases");
                    error.getDialogPane().getStylesheets().add(
                            getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
                    error.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
                    error.show();
                });
        pgr.progressProperty().bind(downloadTask.progressProperty());
        executorParallel.submit(downloadTask);
    }

}
