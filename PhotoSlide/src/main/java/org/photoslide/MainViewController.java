package org.photoslide;

import org.photoslide.browser.CollectionsController;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.lighttable.LighttableController;
import org.photoslide.metadata.MetadataController;
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
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
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
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

public class MainViewController implements Initializable {

    private ExecutorService executor;

    @FXML
    private AnchorPane collectionsPane;
    @FXML
    private AnchorPane lighttablePane;
    @FXML
    private AnchorPane metadataPane;
    @FXML
    private CollectionsController collectionsPaneController;
    @FXML
    private LighttableController lighttablePaneController;
    @FXML
    private MetadataController metadataPaneController;

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

    @Override
    public void initialize(URL url, ResourceBundle rb) {
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
        statusLabelLeft.setVisible(false);
        statusLabelRight.setVisible(false);
        progressPane.setVisible(false);
        FontIcon icon = new FontIcon();
        handleMenuDisable(true);
    }

    public void handleMenuDisable(boolean disabled) {
        Platform.runLater(() -> {
            rotateMenuLeft.setDisable(disabled);
            rotateMenuRight.setDisable(disabled);
            cropMenu.setDisable(disabled);
            rateMenu.setDisable(disabled);
            deleteMenu.setDisable(disabled);
            copyMediaMenu.setDisable(disabled);
            pasteMediaMenu.setDisable(disabled);
        });
    }

    @FXML
    private void browseButtonAction(ActionEvent event) {

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
        collectionsPaneController.Shutdown();
        lighttablePaneController.Shutdown();
        metadataPaneController.Shutdown();
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
            Utility.centerChildWindowOnStage((Stage)alert.getDialogPane().getScene().getWindow(), (Stage)progressPane.getScene().getWindow()); 
            alert.showAndWait();
            return;
        }
        diag.getController().setTitel(lighttablePaneController.getFactory().getSelectedCell().getItem().getTitleProperty().getValue());
        diag.getController().setInitOutDir(collectionsPaneController.getSelectedPath().toString());        
        diag.setResizable(false);
        Utility.centerChildWindowOnStage((Stage)diag.getDialogPane().getScene().getWindow(), (Stage)progressPane.getScene().getWindow()); 
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
                        lighttablePaneController.getList().stream().filter(c -> c.isSelected() == true).forEach((mfile) -> {
                            exportList.add(mfile);
                        });
                    } else {
                        exportList.addAll(lighttablePaneController.getList());
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
                            String url = mediaItem.getImage().getUrl();
                            Image img = new Image(url, false);
                            PixelReader reader = img.getPixelReader();
                            BufferedImage fromFXImage;
                            WritableImage newImage;
                            if (mediaItem.getCropView() != null) {
                                newImage = new WritableImage(reader, (int) (mediaItem.getCropView().getMinX()), (int) (mediaItem.getCropView().getMinY()), (int) (mediaItem.getCropView().getWidth()), (int) (mediaItem.getCropView().getHeight()));
                                fromFXImage = SwingFXUtils.fromFXImage(newImage, null);
                            } else {
                                newImage = new WritableImage(reader, (int) img.getWidth(), (int) img.getHeight());
                                fromFXImage = SwingFXUtils.fromFXImage(newImage, null);
                            }
                            // rotate image in FX or swing

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

    @FXML
    private void preferencesMenuAction(ActionEvent event) {
    }

    @FXML
    private void quitMenuAction(ActionEvent event) {
        App.saveSettings((Stage) browseButton.getScene().getWindow());
        System.exit(0);
    }

    @FXML
    private void aboutMenuAction(ActionEvent event) {
        Utility util=new Utility();
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
        Text txtHeader = new Text("PhotoSlide\n"+appVersion+"\n");
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
        Utility.centerChildWindowOnStage((Stage)alert.getDialogPane().getScene().getWindow(), (Stage)progressPane.getScene().getWindow()); 
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

}
