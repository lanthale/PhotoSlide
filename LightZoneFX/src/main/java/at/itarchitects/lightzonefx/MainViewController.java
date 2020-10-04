package at.itarchitects.lightzonefx;

import at.itarchitects.lightzonefx.browser.CollectionsController;
import at.itarchitects.lightzonefx.datamodel.MediaFile;
import at.itarchitects.lightzonefx.lighttable.LighttableController;
import at.itarchitects.lightzonefx.lighttable.MediaGridCell;
import at.itarchitects.lightzonefx.metadata.MetadataController;
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
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
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
import javafx.scene.Node;
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
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Window;

public class MainViewController implements Initializable {

    private FontAwesomeIconView icon;
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
                    getClass().getResource("/at/itarchitects/lightzonefx/fxml/Dialogs.css").toExternalForm());
            Platform.runLater(() -> {
                Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
                Window window = alert.getDialogPane().getScene().getWindow();
                window.setX((screenBounds.getWidth() - window.getWidth()) / 2);
                window.setY((screenBounds.getHeight() - window.getHeight()) / 2);
            });
            alert.setResizable(false);
            alert.showAndWait();
            return;
        }
        diag.getController().setTitel(lighttablePaneController.getFactory().getSelectedCell().getItem().getTitleProperty().getValue());
        diag.getController().setInitOutDir(collectionsPaneController.getSelectedPath().toString());
        Platform.runLater(() -> {
            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            Window window = diag.getDialogPane().getScene().getWindow();
            window.setX((screenBounds.getWidth() - window.getWidth()) / 2);
            window.setY((screenBounds.getHeight() - window.getHeight()) / 2);
        });
        diag.setResizable(false);
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
            // ... user chose CANCEL or closed the dialog
            System.out.println("Cancled");
        }
    }

    @FXML
    private void preferencesMenuAction(ActionEvent event) {
    }

    @FXML
    private void quitMenuAction(ActionEvent event) {
        System.exit(0);
    }

    @FXML
    private void aboutMenuAction(ActionEvent event) {
        Alert alert=new Alert(AlertType.NONE,"About",ButtonType.OK);
        HBox hb=new HBox();
        hb.setAlignment(Pos.TOP_LEFT);
        hb.setSpacing(10);
        
        ImageView iv=new ImageView(new Image(getClass().getResourceAsStream("/at/itarchitects/lightzonefx/img/Splash.png")));
        iv.setPreserveRatio(true);
        iv.setFitWidth(400);
        hb.getChildren().add(iv);
        Text txt=new Text("LightZoneFX\n1.0\n\n\n\n\n(c) ITArchitects 2020\nLicense: GPL v3");
        txt.setStyle("-fx-fill: #e17c08;-fx-font-size:16pt;-fx-font-weight: bold;");
        txt.setLineSpacing(4);
        txt.setTextAlignment(TextAlignment.LEFT);
        txt.setTextOrigin(VPos.CENTER);
        hb.getChildren().add(txt);
        alert.getDialogPane().setContent(hb);
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/at/itarchitects/lightzonefx/fxml/Dialogs.css").toExternalForm());
        alert.showAndWait();
    }

}
