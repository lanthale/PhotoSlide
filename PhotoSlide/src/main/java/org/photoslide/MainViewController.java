package org.photoslide;

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
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javafx.animation.FadeTransition;
import javafx.animation.RotateTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
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
import org.h2.fulltext.FullText;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.editormedia.EditorMediaViewController;
import org.photoslide.editormetadata.EditorMetadataController;
import org.photoslide.editortools.EditorToolsController;
import org.photoslide.imageops.ImageFilter;
import org.photoslide.print.PrintDialog;
import org.photoslide.search.SearchToolsController;
import org.photoslide.search.SearchToolsDialog;

public class MainViewController implements Initializable {

    private ExecutorService executor;

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
    private Image dialogIcon;
    @FXML
    private Button searchButton;
    private SearchToolsController searchtools;
    private SearchToolsDialog searchDialog;
    private PrintDialog printDialog;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        editorMetaDataPane.setVisible(false);
        editorMediaViewPane.setVisible(false);
        editorToolsPane.setVisible(false);
        executor = Executors.newSingleThreadExecutor();
        menuBar.useSystemMenuBarProperty().set(true);
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
        statusLabelLeft.setVisible(false);
        statusLabelRight.setVisible(false);
        progressPane.setVisible(false);
        FontIcon icon = new FontIcon();
        handleMenuDisable(true);
        dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
    }

    public void handleMenuDisable(boolean disabled) {
        Platform.runLater(() -> {
            rotateMenuLeft.setDisable(disabled);
            rotateMenuRight.setDisable(disabled);
            cropMenu.setDisable(disabled);
            rateMenu.setDisable(disabled);
            deleteMenu.setDisable(disabled);
            copyMediaMenu.setDisable(disabled);
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
        collectionsPaneController.Shutdown();
        lighttablePaneController.Shutdown();
        metadataPaneController.Shutdown();
        editorMetaDataPaneController.shutdown();
        editorMediaViewPaneController.shutdown();
        editorToolsPaneController.shutdown();
        executor.shutdownNow();
    }

    @FXML
    private void exportAction(ActionEvent event) {
        ExportDialog diag = new ExportDialog(Alert.AlertType.CONFIRMATION);
        diag.setTitle("Export media files...");
        diag.setHeaderText("Export media files...");
        if ((lighttablePaneController.getFactory() == null) || (lighttablePaneController.getFactory().getSelectedCell() == null)) {
            Alert alert = new Alert(AlertType.ERROR, "Select an image to export!", ButtonType.OK);
            alert.getDialogPane().getStylesheets().add(
                    getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
            alert.setResizable(false);
            Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(dialogIcon);
            alert.showAndWait();
            return;
        }
        diag.getController().setTitel(lighttablePaneController.getFactory().getSelectedCell().getItem().getTitleProperty().getValue());
        diag.getController().setInitOutDir(collectionsPaneController.getSelectedPath().toString());
        diag.setResizable(false);
        Utility.centerChildWindowOnStage((Stage) diag.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
        diag.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        Optional<ButtonType> result = diag.showAndWait();
        if (result.get() == ButtonType.OK) {
            // ... user chose OK
            progressPane.setVisible(true);
            progressbar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            progressbarLabel.setText("Exporting files...");
            statusLabelLeft.setText("Exporting");
            statusLabelLeft.setVisible(true);
            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    String outputDir = diag.getController().getOutputDir();
                    List<MediaFile> exportList = new ArrayList<>();
                    if (diag.getController().getExportSelectedBox().isSelected() == true) {
                        lighttablePaneController.getFullMediaList().stream().filter(c -> c.isSelected() == true).forEach((mfile) -> {
                            exportList.add(mfile);
                        });
                    } else {
                        exportList.addAll(lighttablePaneController.getFullMediaList());
                    }
                    int i = 0;
                    for (MediaFile mediaItem : exportList) {
                        if (this.isCancelled() == false) {
                            updateProgress(i + 1, exportList.size());
                            updateMessage("" + (i + 1) + "/" + exportList.size());
                            if (diag.getController().getExportDeletedFileBox().isSelected() == false) {
                                if (mediaItem.isDeleted() == true) {
                                    continue;
                                }
                            }
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
                            File outFile = new File(outputDir + File.separator + diag.getController().getFilename() + i + "." + imageType.getExtension());
                            if (outFile.exists() == true) {
                                continue;
                            }
                            String url = mediaItem.getImageUrl().toString();
                            Image img = new Image(url, false);
                            ObservableList<ImageFilter> filterList = mediaItem.getFilterListWithoutImageData();
                            Image imageWithFilters = img;
                            for (ImageFilter imageFilter : filterList) {
                                imageWithFilters = imageFilter.load(imageWithFilters);
                                imageFilter.filter(imageFilter.getValues());
                            }
                            img = imageWithFilters;
                            PixelReader reader = img.getPixelReader();
                            BufferedImage fromFXImage;
                            WritableImage newImage;
                            if (mediaItem.getCropView() != null) {
                                newImage = new WritableImage(reader, (int) (mediaItem.getCropView().getMinX()), (int) (mediaItem.getCropView().getMinY()), (int) (mediaItem.getCropView().getWidth()), (int) (mediaItem.getCropView().getHeight()));
                            } else {
                                newImage = new WritableImage(reader, (int) img.getWidth(), (int) img.getHeight());
                            }
                            fromFXImage = SwingFXUtils.fromFXImage(newImage, null);
                            // rotate image in FX or swing
                            if (mediaItem.getRotationAngleProperty().get() != 0) {
                                fromFXImage = getRotatedImage(fromFXImage, mediaItem.getRotationAngleProperty().get());
                            }
                            try {
                                FileOutputStream fo = new FileOutputStream(outputDir + File.separator + diag.getController().getFilename() + i + "." + imageType.getExtension(), false);
                                ImageWriter writer = ImageIO.getWriter(imageType);
                                ImageParam.ImageParamBuilder builder = ImageParam.getBuilder();
                                switch (imageType) {
                                    case TIFF -> {
                                        // Set TIFF-specific options
                                        TIFFOptions tiffOptions = new TIFFOptions();
                                        tiffOptions.setApplyPredictor(true);
                                        tiffOptions.setTiffCompression(Compression.LZW);
                                        tiffOptions.setPhotoMetric(PhotoMetric.SEPARATED);
                                        tiffOptions.setWriteICCProfile(true);
                                        builder.imageOptions(tiffOptions);
                                    }
                                    case PNG -> {
                                        PNGOptions pngOptions = new PNGOptions();
                                        pngOptions.setApplyAdaptiveFilter(true);
                                        pngOptions.setCompressionLevel(6);
                                        pngOptions.setFilterType(Filter.NONE);
                                        builder.imageOptions(pngOptions);
                                    }
                                    case JPG -> {
                                        JPGOptions jpegOptions = new JPGOptions();
                                        jpegOptions.setQuality(96);
                                        jpegOptions.setColorSpace(JPGOptions.COLOR_SPACE_RGB);
                                        jpegOptions.setWriteICCProfile(true);
                                        builder.imageOptions(jpegOptions);
                                    }
                                    default -> {
                                    }
                                }
                                writer.setImageParam(builder.build());
                                writer.write(fromFXImage, fo);
                                fo.close();
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
            executor.submit(task);
        } else {
            Logger.getLogger(MainViewController.class.getName()).log(Level.FINE, "Export dialog cancled!");
        }
    }

    private BufferedImage getRotatedImage(BufferedImage bufferedImage, double angle) {
        AffineTransform transform = new AffineTransform();
        transform.rotate(angle);
        AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
        bufferedImage = op.filter(bufferedImage, null);
        return bufferedImage;
    }

    @FXML
    private void preferencesMenuAction(ActionEvent event) {
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
        Text txtHeader = new Text("PhotoSlide\n" + appVersion + "\n");
        txtHeader.setStyle("-fx-font-family: 'Silom';-fx-fill: #e17c08;-fx-font-size:16pt;-fx-font-weight: bold;");
        txtHeader.setLineSpacing(2);
        txtHeader.setTextAlignment(TextAlignment.LEFT);
        txtHeader.setTextOrigin(VPos.CENTER);
        Text txt = new Text("Thanks to the opensource community:\n - OpenJFX for this great GUI tookit\n - ControlsFX for very nice components\n - iCafe for the image codecs and metadata implementation\n - iKonli for nice icons\n - UndoFX\n\nLicense: GPL v3\n(c) lanthale 2020");
        txt.setStyle("-fx-fill: #e17c08;-fx-font-size:10pt;");
        txt.setLineSpacing(4);
        txt.setTextAlignment(TextAlignment.LEFT);
        txt.setTextOrigin(VPos.CENTER);
        vbText.getChildren().add(txtHeader);
        vbText.getChildren().add(txt);
        hb.getChildren().add(vbText);
        alert.getDialogPane().setContent(hb);
        alert.setResizable(false);
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
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
        editorMetaDataPaneController.resetImageView();
        editorMediaViewPaneController.resetImageView();
        editorToolsPaneController.clearCanvas();
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
                        } else if (FileTypes.isValidImge(fileItem.toString())) {
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
            editorMetaDataPaneController.resetImageView();
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
            editorMediaViewPaneController.resetImageView();
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
            editorToolsPaneController.clearCanvas();
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
                getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
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
        searchDialog.getController().getCloseAction().setOnMouseClicked((mouse) -> {
            searchDialog.setResult(ButtonType.CANCEL);
        });
        searchDialog.getController().setDialogPane(searchDialog.getDialogPane());
        searchDialog.getController().getSearchTextField().requestFocus();
        searchDialog.getController().injectCollectionsController(collectionsPaneController);
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
                getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
        alert.setResizable(false);
        Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) progressPane.getScene().getWindow());
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        stage.getIcons().add(dialogIcon);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() == ButtonType.YES) {
            try {
                Statement stat = App.getSearchDBConnection().createStatement();
                stat.execute("DROP TABLE MEDIAFILES");
            } catch (SQLException ef) {
            }
            try {
                FullText.dropAll(App.getSearchDBConnection());
            } catch (SQLException ef) {
            }
            App.initDB();
            Alert msg = new Alert(AlertType.INFORMATION, "", ButtonType.OK);
            msg.setHeaderText("Reset successfully!\nPlease restart the application to build up again the search index.");
            msg.getDialogPane().getStylesheets().add(
                    getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
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
                getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
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

}
