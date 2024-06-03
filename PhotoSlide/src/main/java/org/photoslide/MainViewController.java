package org.photoslide;

import animatefx.animation.Bounce;
import com.dlsc.preferencesfx.PreferencesFx;
import com.dlsc.preferencesfx.model.Category;
import org.photoslide.browsercollections.CollectionsController;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.browserlighttable.LighttableController;
import org.photoslide.browsermetadata.MetadataController;
import com.icafe4j.image.ImageIO;
import com.icafe4j.image.ImageParam;
import com.icafe4j.image.ImageType;
import com.icafe4j.image.options.JPGOptions;
import com.icafe4j.image.options.PNGOptions;
import com.icafe4j.image.options.TIFFOptions;
import com.icafe4j.image.png.Filter;
import com.icafe4j.image.tiff.TiffFieldEnum.Compression;
import com.icafe4j.image.tiff.TiffFieldEnum.PhotoMetric;
import com.icafe4j.image.writer.ImageWriter;
import de.jangassen.MenuToolkit;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.animation.FadeTransition;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.controlsfx.control.PopOver;
import org.controlsfx.control.TaskProgressView;
import org.h2.engine.Setting;
import org.h2.fulltext.FullText;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.bookmarksboard.BMBIcon;
import org.photoslide.bookmarksboard.BookmarkBoardController;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFileLoader;
import org.photoslide.editormedia.EditorMediaViewController;
import org.photoslide.editormetadata.EditorMetadataController;
import org.photoslide.editortools.EditorToolsController;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.print.PrintDialog;
import org.photoslide.search.SearchToolsController;
import org.photoslide.search.SearchToolsDialog;

public class MainViewController implements Initializable {

    private ExecutorService executor;
    private ScheduledExecutorService executorParallelScheduled;
    private ExecutorService executorParallel;
    private SoftwareUpdater swUpdater;

    @FXML
    private StackPane leftPane;
    @FXML
    private StackPane middlePane;
    @FXML
    private StackPane rightPane;
    @FXML
    private AnchorPane collectionsPane;
    @FXML
    private AnchorPane editorMetaDataPane;
    @FXML
    private AnchorPane lighttablePane;
    @FXML
    private AnchorPane editorMediaViewPane;
    @FXML
    private AnchorPane metadataPane;
    @FXML
    private AnchorPane editorToolsPane;

    @FXML
    private StackPane progressPane;
    @FXML
    private ProgressBar progressbar;
    @FXML
    private Label progressbarLabel;

    @FXML
    private ToggleButton browseButton;
    @FXML
    private ToggleButton editButton;
    @FXML
    private Label statusLabelLeft;
    @FXML
    private Label statusLabelRight;
    private ToggleGroup group;
    @FXML
    private Font x3;
    @FXML
    private MenuBar menuBar;
    @FXML
    private MenuItem preferencesMenu;
    @FXML
    private MenuItem quitMenu;
    @FXML
    private MenuItem aboutMenu;
    @FXML
    private MenuItem rotateMenuLeft;
    @FXML
    private MenuItem rotateMenuRight;
    @FXML
    private MenuItem cropMenu;
    @FXML
    private MenuItem rateMenu;
    @FXML
    private MenuItem deleteMenu;
    @FXML
    private MenuItem copyMediaMenu;
    @FXML
    private MenuItem pasteMediaMenu;
    @FXML
    private MenuItem stackMenu;
    @FXML
    private MenuItem unstackMenu;
    @FXML
    private MenuItem openMenu;
    @FXML
    private MenuItem bookmarkMenu;
    @FXML
    private Menu windowMenu;
    private Image dialogIcon;
    @FXML
    private Button searchButton;
    @FXML
    private Button bookmarksBoardButton;
    private SearchToolsDialog searchDialog;
    private PrintDialog printDialog;
    private Properties bookmarks;
    private BMBIcon bmbIcon;
    @FXML
    private Button showProcessButton;
    @FXML
    private FontIcon processListIcon;
    private TaskProgressView taskProgressView;
    private PopOver taskPopOver;

    @FXML
    private CollectionsController collectionsPaneController;
    @FXML
    private LighttableController lighttablePaneController;
    @FXML
    private MetadataController metadataPaneController;
    @FXML
    private EditorMetadataController editorMetaDataPaneController;
    @FXML
    private EditorMediaViewController editorMediaViewPaneController;
    @FXML
    private EditorToolsController editorToolsPaneController;
    @FXML
    private BookmarkBoardController bookmarksController;
    @FXML
    private SearchToolsController searchtools;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        editorMetaDataPane.setVisible(false);
        editorMediaViewPane.setVisible(false);
        editorToolsPane.setVisible(false);
        executor = Executors.newSingleThreadExecutor();
        executorParallelScheduled = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNamePrefix("mainviewControllerParallelScheduled").build());
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNamePrefix("mainviewControllerParallel").build());
        taskProgressView = new TaskProgressView();
        taskPopOver = new PopOver();
        taskPopOver.setAnimated(true);
        taskPopOver.setArrowLocation(PopOver.ArrowLocation.BOTTOM_LEFT);
        taskPopOver.setDetachable(false);
        taskPopOver.setTitle("Taskmanager");
        taskPopOver.setHeaderAlwaysVisible(true);
        taskPopOver.setFadeInDuration(new Duration(100));
        taskPopOver.setContentNode(taskProgressView);
        taskProgressView.getTasks().addListener((Observable taskChange) -> {
            if (!taskProgressView.getTasks().isEmpty()) {
                processListIcon.setIconColor(Paint.valueOf("lightgreen"));
            } else {
                processListIcon.setIconColor(Paint.valueOf("#c5c5c5"));
            }
        });
        processListIcon.iconColorProperty().addListener((o) -> {
            if (!taskProgressView.getTasks().isEmpty()) {
                processListIcon.setIconColor(Paint.valueOf("lightgreen"));
            } else {
                processListIcon.setIconColor(Paint.valueOf("#c5c5c5"));
            }
        });
        taskProgressView.setPrefSize(300, 200);
        String OS = System.getProperty("os.name").toUpperCase();
        if (OS.contains("MAC")) {
            Platform.runLater(() -> {
                MenuToolkit tk = MenuToolkit.toolkit(Locale.getDefault());
                tk.setForceQuitOnCmdQ(true);
                Menu createDefaultApplicationMenu = tk.createDefaultApplicationMenu("PhotoSlide");
                tk.setApplicationMenu(createDefaultApplicationMenu);
                menuBar.getMenus().get(4).getItems().remove(aboutMenu);
                createDefaultApplicationMenu.getItems().set(0, aboutMenu);
                createDefaultApplicationMenu.getItems().add(1, new SeparatorMenuItem());
                menuBar.getMenus().get(1).getItems().remove(preferencesMenu);
                createDefaultApplicationMenu.getItems().add(2, preferencesMenu);
                createDefaultApplicationMenu.getItems().add(3, new SeparatorMenuItem());
                menuBar.getMenus().get(0).getItems().remove(quitMenu);
                createDefaultApplicationMenu.getItems().set(createDefaultApplicationMenu.getItems().size() - 1, quitMenu);
                windowMenu.getItems().add(new SeparatorMenuItem());
                windowMenu.getItems().add(tk.createMinimizeMenuItem());
                windowMenu.getItems().add(tk.createZoomMenuItem());
                windowMenu.getItems().add(tk.createCycleWindowsItem());
                windowMenu.getItems().add(new SeparatorMenuItem());
                windowMenu.getItems().add(tk.createBringAllToFrontItem());
                windowMenu.getItems().add(new SeparatorMenuItem());
                menuBar.useSystemMenuBarProperty().set(true);
            });
        }
        group = new ToggleGroup();
        browseButton.setToggleGroup(group);
        editButton.setToggleGroup(group);
        browseButton.setSelected(true);
        collectionsPaneController.injectMainController(this);
        lighttablePaneController.injectMainController(this);
        metadataPaneController.injectMainController(this);
        metadataPaneController.injectLightController(lighttablePaneController);
        collectionsPaneController.injectLighttableController(lighttablePaneController);
        lighttablePaneController.injectMetaDataController(metadataPaneController);
        editorMetaDataPaneController.injectMetaDataController(metadataPaneController);
        editorMediaViewPaneController.injectMainController(this);
        editorMediaViewPaneController.injectLightController(lighttablePaneController);
        editorMediaViewPaneController.injectEditorMetaDataController(editorMetaDataPaneController);
        editorMediaViewPaneController.injectEditorToolsController(editorToolsPaneController);
        statusLabelLeft.setVisible(false);
        statusLabelRight.setVisible(false);
        progressPane.setVisible(false);
        handleMenuDisable(true);
        dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
        swUpdater = new SoftwareUpdater(executorParallel, this);
        bmbIcon = new BMBIcon((FontIcon) bookmarksBoardButton.getGraphic());
        bookmarksBoardButton.setGraphic(bmbIcon);
        readBookmarksFile();
        browseButton.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && browseButton != null) {
                swUpdater.checkForSoftwareUpdates();
            }
        });
    }

    public void handleMenuDisable(boolean disabled) {
        Platform.runLater(() -> {
            rotateMenuLeft.setDisable(disabled);
            rotateMenuRight.setDisable(disabled);
            cropMenu.setDisable(disabled);
            rateMenu.setDisable(disabled);
            deleteMenu.setDisable(disabled);
            copyMediaMenu.setDisable(disabled);
            bookmarkMenu.setDisable(disabled);
            pasteMediaMenu.setDisable(!Clipboard.getSystemClipboard().hasFiles());
        });
    }

    public StackPane getProgressPane() {
        return progressPane;
    }

    public ProgressBar getProgressbar() {
        return progressbar;
    }

    public Label getProgressbarLabel() {
        return progressbarLabel;
    }

    public Label getStatusLabelLeft() {
        return statusLabelLeft;
    }

    public Label getStatusLabelRight() {
        return statusLabelRight;
    }

    public void Shutdown() {
        if (searchtools != null) {
            searchtools.shutdown();
        }
        if (searchDialog != null) {
            searchDialog.getController().shutdown();
            searchDialog.setResult(ButtonType.CANCEL);
        }
        if (printDialog != null) {
            printDialog.getController().shutdown();
        }
        if (bookmarksController != null) {
            bookmarksController.shutdown();
        }
        if (swUpdater != null) {
            swUpdater.Shutdown();
        }
        collectionsPaneController.Shutdown();
        lighttablePaneController.Shutdown();
        metadataPaneController.Shutdown();
        editorMetaDataPaneController.shutdown();
        editorMediaViewPaneController.shutdown();
        editorToolsPaneController.shutdown();
        executor.shutdownNow();
        executorParallel.shutdownNow();
        executorParallelScheduled.shutdownNow();
    }

    @FXML
    private void exportAction(ActionEvent event) {
        if ((lighttablePaneController.getFactory() == null) || (lighttablePaneController.getFactory().getSelectedCell() == null)) {
            Alert alert = new Alert(AlertType.ERROR, "Select a MediaFile to export!", ButtonType.OK);
            alert.getDialogPane().getStylesheets().add(
                    getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
            alert.setResizable(false);
            alert.setGraphic(new FontIcon("ti-close:30"));
            Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(dialogIcon);
            alert.showAndWait();
            return;
        }
        exportData(lighttablePaneController.getFactory().getSelectedCell().getItem().titleProperty().getValue(), collectionsPaneController.getSelectedPath().getParent().toString(), lighttablePaneController.getSortedMediaList());
    }

    public boolean exportData(String titel, String initOutDir, SortedList<MediaFile> mediaListToExport) {
        ExportDialog diag = new ExportDialog(Alert.AlertType.CONFIRMATION);
        diag.setGraphic(new FontIcon("ti-export:30"));
        diag.setTitle("Export media files...");
        diag.setHeaderText("Export media files...");

        diag.getController().setTitel(titel);
        if (initOutDir != null) {
            diag.getController().setInitOutDir(initOutDir);
        }
        diag.setResizable(false);
        Utility.centerChildWindowOnStage((Stage) diag.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
        diag.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        Optional<ButtonType> result = diag.showAndWait();
        if (result.get() == ButtonType.OK) {
            Bounce bc = new Bounce(showProcessButton);
            bc.play();
            progressPane.setVisible(true);
            progressbar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            progressbarLabel.setText("Exporting files...");
            statusLabelLeft.setText("Exporting");
            statusLabelLeft.setVisible(true);
            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    updateTitle("Exporting mediafiles...");
                    String outputDir = diag.getController().getOutputDir();
                    List<MediaFile> exportList = new ArrayList<>();
                    if (diag.getController().getExportSelectedBox().isSelected() == true) {
                        mediaListToExport.stream().filter(c -> c.isSelected() == true).forEach((mfile) -> {
                            exportList.add(mfile);
                        });
                    } else {
                        if (diag.getController().getExportDeletedFileBox().isSelected() == false) {
                            mediaListToExport.stream().filter(c -> c.isDeleted() == false).forEach((mfile) -> {
                                exportList.add(mfile);
                            });
                        } else {
                            exportList.addAll(mediaListToExport);
                        }
                    }
                    //sort
                    if (diag.getController().getSortComboBox().getSelectionModel().getSelectedItem().equalsIgnoreCase("Record time based")) {
                        AtomicInteger iatom = new AtomicInteger(1);
                        exportList.parallelStream().forEach((mediaFile) -> {
                            updateMessage("Export: Read record time " + iatom.get() + "/" + exportList.size());
                            try {
                                if (mediaFile.getRecordTime() == null) {
                                    metadataPaneController.setActualMediaFile(mediaFile);
                                    metadataPaneController.readBasicMetadata(this, mediaFile);
                                }
                            } catch (IOException e) {
                                long test_timestamp = mediaFile.getPathStorage().toFile().lastModified();
                                LocalDateTime triggerTime
                                        = LocalDateTime.ofInstant(Instant.ofEpochMilli(test_timestamp), TimeZone.getDefault().toZoneId());
                                mediaFile.setRecordTime(triggerTime);
                            }
                            if (mediaFile.getRecordTime() == null) {
                                long test_timestamp = mediaFile.getPathStorage().toFile().lastModified();
                                LocalDateTime triggerTime
                                        = LocalDateTime.ofInstant(Instant.ofEpochMilli(test_timestamp), TimeZone.getDefault().toZoneId());
                                mediaFile.setRecordTime(triggerTime);
                            }
                            iatom.addAndGet(1);
                        });
                        exportList.sort(Comparator.comparing(MediaFile::getRecordTime));
                    }
                    //end sort
                    int i = 0;
                    for (MediaFile mediaItem : exportList) {
                        if (this.isCancelled() == false) {
                            updateProgress(i + 1, exportList.size());
                            updateMessage("Exporting " + (i + 1) + "/" + exportList.size());
                            try {
                                String fileFormat = diag.getController().getFileFormat();
                                ImageType imageType = ImageType.JPG;
                                switch (fileFormat) {
                                    case "JPG":
                                        imageType = ImageType.JPG;
                                        break;
                                    case "PNG":
                                        imageType = ImageType.PNG;
                                        break;
                                    case "TIFF":
                                        imageType = ImageType.TIFF;
                                        break;
                                }
                                String outFileStr;
                                if (diag.getController().getFilename().equalsIgnoreCase("<Original>_")) {
                                    outFileStr = outputDir + File.separator + mediaItem.getName() + (i + 1) + "." + imageType.getExtension();
                                } else {
                                    outFileStr = outputDir + File.separator + diag.getController().getFilename() + (i + 1) + "." + imageType.getExtension();
                                }
                                File outFile = new File(outFileStr);
                                if (outFile.exists() == true) {
                                    if (diag.getController().getOverwriteFilesBox().isSelected()) {
                                        outFile.delete();
                                    } else {
                                        continue;
                                    }
                                }
                                String url = mediaItem.getImageUrl().toString();
                                Image img = new Image(url, false);
                                ObservableList<ImageFilter> filterList = mediaItem.getFilterListWithoutImageData();
                                Image imageWithFilters = img;
                                for (ImageFilter imageFilter : filterList) {
                                    imageWithFilters = imageFilter.loadMediaData(imageWithFilters);
                                    imageFilter.filterMediaData(imageFilter.getValues());
                                }
                                img = imageWithFilters;
                                PixelReader reader = img.getPixelReader();
                                BufferedImage fromFXImage;
                                WritableImage newImage;
                                if (mediaItem.getCropView() != null && mediaItem.getCropView().getHeight() > 0.0) {
                                    newImage = new WritableImage(reader, (int) (mediaItem.getCropView().getMinX()), (int) (mediaItem.getCropView().getMinY()), (int) (mediaItem.getCropView().getWidth()), (int) (mediaItem.getCropView().getHeight()));
                                } else {
                                    newImage = new WritableImage(reader, (int) img.getWidth(), (int) img.getHeight());
                                }
                                fromFXImage = SwingFXUtils.fromFXImage(newImage, null);
                                // rotate image in FX or swing                            
                                if (mediaItem.getRotationAngleProperty().get() != 0) {
                                    fromFXImage = getRotatedImage(fromFXImage, mediaItem.getRotationAngleProperty().get());
                                }

                                FileOutputStream fo = new FileOutputStream(outFileStr, false);
                                ImageWriter writer = ImageIO.getWriter(imageType);
                                ImageParam.ImageParamBuilder builder = ImageParam.getBuilder();
                                switch (imageType) {
                                    case TIFF:
                                        // Set TIFF-specific options
                                        TIFFOptions tiffOptions = new TIFFOptions();
                                        tiffOptions.setApplyPredictor(true);
                                        tiffOptions.setTiffCompression(Compression.LZW);
                                        tiffOptions.setPhotoMetric(PhotoMetric.SEPARATED);
                                        tiffOptions.setWriteICCProfile(true);
                                        builder.imageOptions(tiffOptions);
                                        break;
                                    case PNG:
                                        PNGOptions pngOptions = new PNGOptions();
                                        pngOptions.setApplyAdaptiveFilter(true);
                                        pngOptions.setCompressionLevel(diag.getController().getQualityValue());
                                        pngOptions.setFilterType(Filter.NONE);
                                        builder.imageOptions(pngOptions);
                                        break;
                                    case JPG:
                                        JPGOptions jpegOptions = new JPGOptions();
                                        jpegOptions.setQuality(diag.getController().getQualityValue());
                                        jpegOptions.setColorSpace(JPGOptions.COLOR_SPACE_RGB);
                                        jpegOptions.setWriteICCProfile(true);
                                        builder.imageOptions(jpegOptions);
                                        break;
                                    default:
                                        break;
                                }
                                writer.setImageParam(builder.build());
                                writer.write(fromFXImage, fo);
                                fo.close();
                                if (diag.getController().getExportAllMetaData().isSelected()) {
                                    metadataPaneController.readBasicMetadata(this, mediaItem);
                                    if (diag.getController().getReplaceTitleBox().isSelected()) {
                                        mediaItem.setTitle(diag.getController().getTitle());
                                    }
                                    if (diag.getController().getReplaceKeywordChoiceBox().isSelected()) {
                                        mediaItem.setKeywords(diag.getController().getKeywordsAsString());
                                    }
                                    if (diag.getController().getReplaceGPSCheckBox().isSelected()) {
                                        if (!diag.getController().getHeightTextField().getText().equalsIgnoreCase("")) {
                                            mediaItem.setGpsHeight(Double.parseDouble(diag.getController().getHeightTextField().getText()));
                                        }
                                        mediaItem.setGpsPositionFromDegree(diag.getController().getSelectedGPSPos());
                                        mediaItem.placeProperty().setValue(diag.getController().getFoundPlaceName());
                                    }
                                    metadataPaneController.exportCompleteMetdata(mediaItem, outFileStr, imageType.getExtension(), diag.getController().getReplaceGPSCheckBox().isSelected());
                                }
                                if (diag.getController().getExportBasicMetadataBox().isSelected()) {
                                    metadataPaneController.readBasicMetadata(this, mediaItem);
                                    if (diag.getController().getReplaceTitleBox().isSelected()) {
                                        Platform.runLater(() -> {
                                            mediaItem.setTitle(diag.getController().getTitle());
                                        });
                                    }
                                    if (diag.getController().getReplaceKeywordChoiceBox().isSelected()) {
                                        Platform.runLater(() -> {
                                            mediaItem.setKeywords(diag.getController().getKeywordsAsString());
                                        });
                                    }
                                    if (!diag.getController().getHeightTextField().getText().equalsIgnoreCase("")) {
                                        mediaItem.setGpsHeight(Double.parseDouble(diag.getController().getHeightTextField().getText()));
                                    }
                                    if (diag.getController().getReplaceGPSCheckBox().isSelected()) {
                                        mediaItem.setGpsPositionFromDegree(diag.getController().getSelectedGPSPos());
                                        Platform.runLater(() -> {
                                            mediaItem.placeProperty().setValue(diag.getController().getFoundPlaceName());
                                        });
                                    }
                                    metadataPaneController.exportBasicMetadata(mediaItem, outFileStr, diag.getController().getReplaceGPSCheckBox().isSelected());
                                }
                            } catch (FileNotFoundException ex) {
                                Logger.getLogger(MainViewController.class.getName()).log(Level.SEVERE, null, ex);
                            } catch (Exception ex) {
                                Logger.getLogger(MainViewController.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            i++;
                        }
                    }
                    return true;
                }
            };
            progressbar.progressProperty().unbind();
            progressbar.progressProperty().bind(task.progressProperty());
            progressbarLabel.textProperty().unbind();
            progressbarLabel.textProperty().bind(task.messageProperty());
            task.setOnSucceeded((t) -> {
                progressbar.progressProperty().unbind();
                progressbarLabel.textProperty().unbind();
                progressPane.setVisible(false);
                statusLabelLeft.setVisible(false);
            });
            task.setOnFailed((t) -> {
                //error during rotated images occuring...                
                Logger.getLogger(MainViewController.class.getName()).log(Level.SEVERE, "Error during export occured!", t.getSource().getException());
                progressbar.progressProperty().unbind();
                progressbarLabel.textProperty().unbind();
                progressPane.setVisible(false);
                statusLabelLeft.setVisible(false);
                Alert errorDiag = new Alert(AlertType.ERROR, "Error during export", ButtonType.OK);
                errorDiag.setContentText("During export the following error occured\n" + t.getSource().getException().getMessage());
                errorDiag.setResizable(false);
                Utility.centerChildWindowOnStage((Stage) errorDiag.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
                errorDiag.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
                errorDiag.show();
            });
            task.setOnCancelled((t) -> {
                progressbar.progressProperty().unbind();
                progressbarLabel.textProperty().unbind();
                progressPane.setVisible(false);
                statusLabelLeft.setVisible(false);
            });
            executor.submit(task);
            taskProgressView.getTasks().add(task);
        } else {
            Logger.getLogger(MainViewController.class.getName()).log(Level.FINE, "Export dialog cancled!");
        }
        return false;
    }

    private BufferedImage getRotatedImage(BufferedImage image, double angle) {
        final double rads = Math.toRadians(angle);
        final double sin = Math.abs(Math.sin(rads));
        final double cos = Math.abs(Math.cos(rads));
        final int w = (int) Math.floor(image.getWidth() * cos + image.getHeight() * sin);
        final int h = (int) Math.floor(image.getHeight() * cos + image.getWidth() * sin);
        final BufferedImage rotatedImage = new BufferedImage(w, h, image.getType());
        final AffineTransform at = new AffineTransform();
        at.translate(w / 2, h / 2);
        at.rotate(rads, 0, 0);
        at.translate(-image.getWidth() / 2, -image.getHeight() / 2);
        final AffineTransformOp rotateOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
        rotateOp.filter(image, rotatedImage);
        return rotatedImage;
    }

    @FXML
    private void preferencesMenuAction(ActionEvent event) {
        StringProperty stringProperty = new SimpleStringProperty("String");
        BooleanProperty booleanProperty = new SimpleBooleanProperty(true);
        IntegerProperty integerProperty = new SimpleIntegerProperty(12);
        DoubleProperty doubleProperty = new SimpleDoubleProperty(6.5);

        PreferencesFx preferencesFx = PreferencesFx.of(App.class, Category.of("probe"), Category.of("probe2"));        
        preferencesFx.getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        preferencesFx.show();
    }

    @FXML
    private void quitMenuAction(ActionEvent event) {
        App.saveSettings((Stage) browseButton.getScene().getWindow(), MainViewController.this);
        System.exit(0);
    }

    @FXML
    private void aboutMenuAction(ActionEvent event) {
        Utility util = new Utility();
        String appVersion = util.getAppVersion();
        Alert alert = new Alert(AlertType.NONE, "About", ButtonType.OK);
        alert.initStyle(StageStyle.UNDECORATED);
        HBox hb = new HBox();
        hb.setAlignment(Pos.TOP_LEFT);
        hb.setSpacing(10);

        ImageView iv = new ImageView(new Image(getClass().getResourceAsStream("/org/photoslide/img/Splashscreen.png")));
        iv.setPreserveRatio(true);
        iv.setFitWidth(400);
        iv.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 10, 0, 0, 0);");
        hb.getChildren().add(iv);
        VBox vbText = new VBox();
        Text txtHeader = new Text("PhotoSlide\n" + System.getProperty("os.arch") + "_" + appVersion + "\n");
        txtHeader.setStyle("-fx-font-family: 'Silom';-fx-fill: white;-fx-font-size:16pt;-fx-font-weight: bold;");
        txtHeader.setLineSpacing(2);
        txtHeader.setTextAlignment(TextAlignment.LEFT);
        txtHeader.setTextOrigin(VPos.CENTER);
        String aboutText = """
                         Thanks to the opensource community:
                          - OpenJFX for this great GUI tookit
                          - Gluon for support of OpenJFX and provide binary's including mobile support! 
                          
                          - ControlsFX for very nice components
                          - iCafe for the image codecs and metadata implementation
                          - iKonli for nice icons
                          - UndoFX for undo support
                          - Worldwind for mapsupport
                          - JMonkey for additional image format support 
                          - Libraw for support of raw image formats
                          - Libheif for support for raw image formats 
                         
                         License: GPL v3
                         (c) lanthale 2022""";
        Text txt = new Text(aboutText);
        txt.setStyle("-fx-fill: white;-fx-font-size:10pt;");
        txt.setLineSpacing(4);
        txt.setTextAlignment(TextAlignment.LEFT);
        txt.setTextOrigin(VPos.CENTER);
        vbText.getChildren().add(txtHeader);
        vbText.getChildren().add(txt);
        hb.getChildren().add(vbText);
        alert.getDialogPane().setContent(hb);
        alert.setResizable(false);
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        stage.getIcons().add(dialogIcon);
        alert.showAndWait();
    }

    @FXML
    private void rotateMenuLeftAction(ActionEvent event) {
        lighttablePaneController.rotateLeftAction();
    }

    @FXML
    private void rotateMenuRightAction(ActionEvent event) {
        lighttablePaneController.rotateRightAction();
    }

    @FXML
    private void cropMenuAction(ActionEvent event) {
        lighttablePaneController.cropAction();
    }

    @FXML
    private void rateMenuAction(ActionEvent event) {
        lighttablePaneController.rateAction();
    }

    @FXML
    private void deleteMenuAction(ActionEvent event) {
        lighttablePaneController.deleteAction();
    }

    @FXML
    private void copyMediaMenuAction(ActionEvent event) {
        lighttablePaneController.copyAction();
    }

    @FXML
    private void pastMediaMenuAction(ActionEvent event) {
        lighttablePaneController.pastAction();
    }

    @FXML
    private void stackMenuAction(ActionEvent event) {
    }

    @FXML
    private void unstackMenuAction(ActionEvent event) {
    }

    @FXML
    private void openMenuAction(ActionEvent event) {
        collectionsPaneController.addExistingPath();
    }

    public void saveSettings() {
        collectionsPaneController.saveSettings();
        lighttablePaneController.saveSettings();
        metadataPaneController.saveSettings();
    }

    void restoreSettings() {
        collectionsPaneController.restoreSettings();
        lighttablePaneController.restoreSettings();
        metadataPaneController.restoreSettings();
    }

    public MetadataController getMetadataPaneController() {
        return metadataPaneController;
    }

    @FXML
    private void browseButtonAction(ActionEvent event) {
        editorMetaDataPaneController.resetUI();
        editorMediaViewPaneController.resetUI();
        editorToolsPaneController.resetUI();
        FadeTransition ft = new FadeTransition(Duration.millis(500), editorMetaDataPane);
        ft.setAutoReverse(false);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished((ActionEvent event1) -> {
            collectionsPane.setOpacity(0);
            collectionsPane.setVisible(true);
            editorMetaDataPane.setVisible(false);
            FadeTransition ft1 = new FadeTransition(Duration.millis(500), collectionsPane);
            ft1.setAutoReverse(false);
            ft1.setFromValue(0.0);
            ft1.setToValue(1.0);
            ft1.play();
        });
        ft.play();
        FadeTransition ft2 = new FadeTransition(Duration.millis(500), editorMediaViewPane);
        ft2.setAutoReverse(false);
        ft2.setFromValue(1.0);
        ft2.setToValue(0.0);
        ft2.setOnFinished((ActionEvent event1) -> {
            lighttablePane.setOpacity(0);
            lighttablePane.setVisible(true);
            editorMediaViewPane.setVisible(false);
            FadeTransition ft1 = new FadeTransition(Duration.millis(500), lighttablePane);
            ft1.setAutoReverse(false);
            ft1.setFromValue(0.0);
            ft1.setToValue(1.0);
            ft1.play();
        });
        ft2.play();
        FadeTransition ft3 = new FadeTransition(Duration.millis(500), editorToolsPane);
        ft3.setAutoReverse(false);
        ft3.setFromValue(1.0);
        ft3.setToValue(0.0);
        ft3.setOnFinished((ActionEvent event1) -> {
            metadataPane.setOpacity(0);
            metadataPane.setVisible(true);
            editorToolsPane.setVisible(false);
            FadeTransition ft1 = new FadeTransition(Duration.millis(500), metadataPane);
            ft1.setAutoReverse(false);
            ft1.setFromValue(0.0);
            ft1.setToValue(1.0);
            ft1.play();
        });
        ft3.play();
    }

    @FXML
    private void editButtonAction(ActionEvent event) {
        RotateTransition rotate = new RotateTransition();
        rotate.setAxis(Rotate.Y_AXIS);
        rotate.setByAngle(90);
        rotate.setCycleCount(1);
        rotate.setDuration(Duration.millis(1000));

        if (collectionsPaneController.getSelectedPath() == null) {
            browseButton.setSelected(true);
            return;
        }
        MediaFile selectedMediaItem = lighttablePaneController.getFactory().getSelectedMediaItem();
        if (selectedMediaItem == null) {
            try {
                Stream<Path> fileList = Files.list(collectionsPaneController.getSelectedPath()).filter((t) -> {
                    return FileTypes.isValidType(t.getFileName().toString());
                }).sorted();
                Path fileItem = fileList.iterator().next();
                if (Files.isDirectory(fileItem) == false) {
                    if (FileTypes.isValidType(fileItem.toString())) {
                        MediaFile m = new MediaFile();
                        m.setName(fileItem.toString());
                        m.setPathStorage(fileItem);
                        m.readEdits();
                        m.getCreationTime();
                        if (FileTypes.isValidVideo(fileItem.toString())) {
                            m.setMediaType(MediaFile.MediaTypes.VIDEO);
                        } else if (FileTypes.isValidImage(fileItem.toString())) {
                            m.setMediaType(MediaFile.MediaTypes.IMAGE);
                            Image img = null;
                            img = new Image(m.getPathStorage().toUri().toURL().toString(), lighttablePaneController.getImageGrid().getCellWidth() + 300, lighttablePaneController.getImageGrid().getCellHeight() + 300, true, false, false);
                            m.setImage(img);
                        } else {
                            m.setMediaType(MediaFile.MediaTypes.NONE);
                        }
                        selectedMediaItem = m;
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(MainViewController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        final MediaFile selMedia = selectedMediaItem;
        FadeTransition ft = new FadeTransition(Duration.millis(250), collectionsPane);
        ft.setAutoReverse(false);
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished((ActionEvent event1) -> {
            editorMetaDataPane.setOpacity(0);
            editorMetaDataPaneController.resetUI();
            editorMetaDataPane.setVisible(true);
            collectionsPane.setVisible(false);
            FadeTransition ft1 = new FadeTransition(Duration.millis(250), editorMetaDataPane);
            ft1.setAutoReverse(false);
            ft1.setFromValue(0.0);
            ft1.setToValue(1.0);
            ft1.setOnFinished((t) -> {
                editorMetaDataPaneController.setMediaFileForEdit(selMedia);
            });
            ft1.play();
        });
        ft.play();
        FadeTransition ft2 = new FadeTransition(Duration.millis(250), lighttablePane);
        ft2.setAutoReverse(false);
        ft2.setFromValue(1.0);
        ft2.setToValue(0.0);
        ft2.setOnFinished((ActionEvent event1) -> {
            editorMediaViewPane.setOpacity(0);
            editorMediaViewPaneController.resetUI();
            editorMediaViewPane.setVisible(true);
            lighttablePane.setVisible(false);
            FadeTransition ft1 = new FadeTransition(Duration.millis(250), editorMediaViewPane);
            ft1.setAutoReverse(false);
            ft1.setFromValue(0.0);
            ft1.setToValue(1.0);
            ft1.setOnFinished((t) -> {
                editorMediaViewPaneController.setMediaFileForEdit(selMedia);
            });
            ft1.play();
        });
        ft2.play();
        FadeTransition ft3 = new FadeTransition(Duration.millis(250), metadataPane);
        ft3.setAutoReverse(false);
        ft3.setFromValue(1.0);
        ft3.setToValue(0.0);
        ft3.setOnFinished((ActionEvent event1) -> {
            editorToolsPane.setOpacity(0);
            editorToolsPaneController.resetUI();
            editorToolsPane.setVisible(true);
            metadataPane.setVisible(false);
            FadeTransition ft1 = new FadeTransition(Duration.millis(250), editorToolsPane);
            ft1.setAutoReverse(false);
            ft1.setFromValue(0.0);
            ft1.setToValue(1.0);
            ft1.setOnFinished((t) -> {
                editorToolsPaneController.setMediaFileForEdit(selMedia);
            });
            ft1.play();
        });
        ft3.play();
    }

    @FXML
    private void searchButtonAction(ActionEvent event) {
        searchAction();
    }

    private void searchAction() {
        searchDialog = new SearchToolsDialog(Alert.AlertType.NONE);
        searchDialog.initStyle(StageStyle.UNDECORATED);
        searchDialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        searchDialog.setResizable(true);
        Utility.centerTopChildWindowOnStage((Stage) searchDialog.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
        Stage stage = (Stage) searchDialog.getDialogPane().getScene().getWindow();
        searchDialog.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        stage.getIcons().add(dialogIcon);
        searchDialog.getDialogPane().setOnKeyPressed((key) -> {
            if (key.getCode() == KeyCode.ESCAPE) {
                searchDialog.setResult(ButtonType.CANCEL);
            }
        });
        searchDialog.setOnHiding((t) -> {
            searchDialog.getController().shutdown();
        });
        searchDialog.getController().getCloseAction().setOnMouseClicked((mouse) -> {
            searchDialog.setResult(ButtonType.CANCEL);
        });
        searchDialog.getController().setDialogPane(searchDialog.getDialogPane());
        searchDialog.getController().getSearchTextField().requestFocus();
        searchDialog.getController().injectCollectionsController(collectionsPaneController);
        searchDialog.getController().injectMainController(this);
        searchDialog.getController().injectMetaDataController(metadataPaneController);
        Optional<ButtonType> result = searchDialog.showAndWait();
    }

    @FXML
    private void searchMenuAction(ActionEvent event) {
        searchAction();
    }

    @FXML
    private void resetFTSearchIndex(ActionEvent event) {
        Alert alert = new Alert(AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO);
        alert = Utility.setDefaultButton(alert, ButtonType.NO);
        alert.setHeaderText("Do you want to reset the search index ?");
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        alert.setResizable(false);
        Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        stage.getIcons().add(dialogIcon);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() == ButtonType.YES) {
            collectionsPaneController.getSearchIndexProcess().shutdown();
            try {
                Statement stat = App.getSearchDBConnection().createStatement();
                stat.execute("DROP TABLE MEDIAFILES");
            } catch (SQLException ef) {
            }
            try {
                FullText.dropAll(App.getSearchDBConnection());
                App.getSearchDBConnection().close();
            } catch (SQLException ef) {
            }
            final File downloadDirectory = new File(Utility.getAppData());
            final File[] files = downloadDirectory.listFiles((dir, name) -> name.matches("SearchMediaFilesDB.*"));
            Arrays.asList(files).stream().forEach(File::delete);
            Alert msg = new Alert(AlertType.INFORMATION, "", ButtonType.OK);
            msg.setHeaderText("Reset successfully!\nPlease restart the application to build up again the search index.");
            msg.getDialogPane().getStylesheets().add(
                    getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
            msg.setResizable(false);
            Utility.centerChildWindowOnStage((Stage) msg.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
            stage = (Stage) msg.getDialogPane().getScene().getWindow();
            stage.getIcons().add(dialogIcon);
            msg.show();
        }
    }

    @FXML
    private void printMediaAction(ActionEvent event) {
        printDialog = new PrintDialog(Alert.AlertType.INFORMATION, "", ButtonType.OK, ButtonType.CANCEL);
        printDialog.setGraphic(new FontIcon("ti-printer:40"));
        printDialog.setTitle("Print dialog");
        printDialog.setHeaderText("Print settings");
        printDialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        printDialog.setResizable(true);
        Utility.centerChildWindowOnStage((Stage) printDialog.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
        Stage stage = (Stage) printDialog.getDialogPane().getScene().getWindow();
        printDialog.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        stage.getIcons().add(dialogIcon);
        printDialog.getController().setDialogPane(printDialog.getDialogPane());
        printDialog.getController().setStage(stage);
        statusLabelLeft.setVisible(true);
        statusLabelLeft.textProperty().unbind();
        statusLabelLeft.setText("");
        printDialog.getController().setAllPrintItems(lighttablePaneController.getFactory().getSelectionModel().getSelection());

        Optional<ButtonType> result = printDialog.showAndWait();
        if (result.get() == ButtonType.OK) {
            printDialog.getController().print(statusLabelLeft, lighttablePaneController.getFactory().getSelectionModel().getSelection());
            statusLabelLeft.setVisible(true);
            statusLabelLeft.textProperty().unbind();
            statusLabelLeft.setText("");
        }
    }

    public void saveBookmarksFile() {
        Thread.ofVirtual().start(() -> {
            String fileNameWithExt = Utility.getAppData() + File.separator + "bookmarks.prop";
            try (OutputStream output = new FileOutputStream(fileNameWithExt)) {
                bookmarks.store(output, null);
                output.flush();
            } catch (IOException ex) {
                Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    private void readBookmarksFile() {
        Thread.ofVirtual().start(() -> {
            String fileNameWithExt = Utility.getAppData() + File.separator + "bookmarks.prop";
            if (new File(fileNameWithExt).exists() == false) {
                bookmarks = new Properties();
                return;
            }
            try (InputStream input = new FileInputStream(fileNameWithExt)) {
                bookmarks = new Properties();
                bookmarks.load(input);
                Platform.runLater(() -> {
                    bmbIcon.setCounter(bookmarks.size());
                });
            } catch (IOException ex) {
                Logger.getLogger(LighttableController.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    public boolean isMediaFileBookmarked(MediaFile m) {
        boolean ret = false;
        Object get = bookmarks.get(m.getName());
        if (get == null) {
            ret = false;
        } else {
            ret = true;
        }
        return ret;
    }

    public void clearBookmars() {
        bookmarks.clear();
        bmbIcon.setCounter(bookmarks.size());
        saveBookmarksFile();
    }

    public void bookmarkMediaFile(MediaFile m) {
        if (m.isBookmarked()) {
            bookmarks.remove(m.getName());
            m.setBookmarked(false);
            lighttablePaneController.getBookmarkButton().setText("Bookmark");
        } else {
            bookmarks.setProperty(m.getName(), m.getPathStorage().toString());
            m.setBookmarked(true);
            lighttablePaneController.getBookmarkButton().setText("Unbookmark");
        }
        bmbIcon.setCounter(bookmarks.size());
    }

    public void removeBookmarkMediaFile(MediaFile m) {
        bookmarks.remove(m.getName());
        m.setBookmarked(false);
        if (lighttablePaneController.getFullMediaList() != null) {
            int indexOf = lighttablePaneController.getFullMediaList().indexOf(m);
            if (indexOf != -1) {
                lighttablePaneController.getFullMediaList().get(indexOf).setBookmarked(false);
            }
        }
        saveBookmarksFile();
        bmbIcon.setCounter(bookmarks.size());
    }

    private List<String> getBookmarks() {
        List<String> retList = new ArrayList<>();
        for (Enumeration<?> names = bookmarks.propertyNames(); names.hasMoreElements();) {
            String key = (String) names.nextElement();
            retList.add((String) bookmarks.get(key));
        }
        return retList;
    }

    @FXML
    private void bookmarksButtonAction(ActionEvent event) {
        PopOver popOver = new PopOver();
        popOver.setDetachable(false);
        popOver.setAnimated(true);
        popOver.setCloseButtonEnabled(true);
        popOver.setAutoHide(true);
        popOver.setTitle("Bookmarks Board");
        popOver.setHeaderAlwaysVisible(true);

        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/org/photoslide/fxml/BookmarkBoard.fxml"));
        Parent root;
        try {
            root = (Parent) fxmlLoader.load();
            bookmarksController = fxmlLoader.<BookmarkBoardController>getController();
            bookmarksController.injectMainController(this);
            bookmarksController.injectPopOverControl(popOver);
            popOver.setOnHidden((t) -> {
                bookmarksController.shutdown();
            });
            bookmarksController.setMediaFileList(getBookmarks());
            bookmarksController.readBookmarks();
            popOver.setContentNode(root);
            popOver.setArrowLocation(PopOver.ArrowLocation.TOP_CENTER);
            popOver.setFadeInDuration(new Duration(100));
            popOver.show(bookmarksBoardButton);
            ((Parent) popOver.getSkin().getNode()).getStylesheets()
                    .add(getClass().getResource("/org/photoslide/css/PopOver.css").toExternalForm());
        } catch (IOException ex) {
            Logger.getLogger(MainViewController.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public Button getBookmarksBoardButton() {
        return bookmarksBoardButton;
    }

    @FXML
    private void bookmarkMenuAction(ActionEvent event) {
        Set<MediaFile> selection = lighttablePaneController.getFactory().getSelectionModel().getSelection();
        for (MediaFile m : selection) {
            bookmarkMediaFile(m);
        }
        saveBookmarksFile();
        bmbIcon.setCounter(bookmarks.size());
    }

    @FXML
    private void wipeAllMediaFileEdits(ActionEvent event) {
        MediaFile selectedMediaItem = lighttablePaneController.getFactory().getSelectedMediaItem();
        selectedMediaItem.removeAllEdits();
        int indexOf = lighttablePaneController.getFullMediaList().indexOf(selectedMediaItem);
        if (selectedMediaItem.getMediaType() == MediaFile.MediaTypes.IMAGE) {
            new MediaFileLoader().loadImage(selectedMediaItem);
            lighttablePaneController.getFullMediaList().set(indexOf, selectedMediaItem);
            try {
                lighttablePaneController.getFactory().loadImage();
            } catch (MalformedURLException ex) {
                Logger.getLogger(MainViewController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (selectedMediaItem.getMediaType() == MediaFile.MediaTypes.VIDEO) {
            new MediaFileLoader().loadVideo(selectedMediaItem);
            lighttablePaneController.getFullMediaList().set(indexOf, selectedMediaItem);
            Thread.ofVirtual().start(lighttablePaneController.getFactory().loadVideo());
        }
    }

    public CollectionsController getCollectionsPaneController() {
        return collectionsPaneController;
    }

    @FXML
    private void selectAllAction(ActionEvent event) {
        lighttablePaneController.getFullMediaList().forEach((mediafile) -> {
            lighttablePaneController.getFactory().getSelectionModel().add(mediafile);
        });
    }

    @FXML
    private void deSelectAllAction(ActionEvent event) {
        lighttablePaneController.getFactory().getSelectionModel().clear();
    }

    public TaskProgressView getTaskProgressView() {
        return taskProgressView;
    }

    @FXML
    private void showProcessListButtonAction(ActionEvent event) {
        taskPopOver.show(showProcessButton);
        ((Parent) taskPopOver.getSkin().getNode()).getStylesheets()
                .add(getClass().getResource("/org/photoslide/css/PopOver.css").toExternalForm());
    }

    @FXML
    private void showBackgroundProcessListMenu(ActionEvent event) {
        taskPopOver.show(showProcessButton);
        ((Parent) taskPopOver.getSkin().getNode()).getStylesheets()
                .add(getClass().getResource("/org/photoslide/css/PopOver.css").toExternalForm());
    }

    @FXML
    private void showMediaStackAction(ActionEvent event) {
        MediaFile item = lighttablePaneController.getFactory().getSelectedMediaItem();
        if (item != null) {
            if (item.isStacked()) {
                lighttablePaneController.getFactory().handleStackButtonAction(item.getStackName(), lighttablePaneController.getFactory().getSelectedCell());
            }
        }
    }

    @FXML
    private void resetMediaCache(ActionEvent event) {
        try {
            Files.newDirectoryStream(Path.of(Utility.getAppData() + File.separatorChar + "cache")).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    file.toFile().deleteOnExit();
                }
            });
        } catch (IOException ex) {
            Logger.getLogger(MainViewController.class.getName()).log(Level.SEVERE, null, ex);
        }
        Alert msg = new Alert(AlertType.INFORMATION, "", ButtonType.OK);
        msg.setHeaderText("Reset successfully!\nPlease restart the application to build up again the cache.");
        msg.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        msg.setResizable(false);
        Utility.centerChildWindowOnStage((Stage) msg.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
        Stage stage = (Stage) msg.getDialogPane().getScene().getWindow();
        stage.getIcons().add(dialogIcon);
        msg.show();
    }

}
