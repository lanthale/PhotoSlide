/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.print;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterAttributes;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Border;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.photoslide.datamodel.MediaFile;

/**
 *
 * @author selfemp
 */
public class PrintController implements Initializable {
    
    private DialogPane dialogPane;
    @FXML
    private ComboBox<Printer> printerCombo;
    @FXML
    private ComboBox<Paper> paperSizeCombo;
    @FXML
    private TextField copyQTY;
    @FXML
    private CheckBox greyscaleChoice;
    @FXML
    private RadioButton allPagesRadio;
    @FXML
    private RadioButton rangeRadio;
    @FXML
    private TextField pageFromText;
    @FXML
    private TextField pageToText;
    @FXML
    private ComboBox<Integer> copyiesPerPageCombo;
    @FXML
    private ToggleButton portraitButton;
    @FXML
    private ToggleButton landscapeButton;
    @FXML
    private Pagination paganinationControl;
    @FXML
    private GridPane previewPane;
    private ToggleGroup tGroup;
    private double prefHeight;
    private double prefWidth;
    private Set<MediaFile> allPrintItems;
    @FXML
    private CheckBox borderLessBox;
    private Stage stage;
    private double paperRatio;
    private Printer selectedPrinter;
    private PageLayout pageLayout;
    @FXML
    private HBox pageBox;

    @Override
    public void initialize(URL url, ResourceBundle rb) {        
        tGroup = new ToggleGroup();
        prefHeight = -1;
        prefWidth = -1;
        allPrintItems = new HashSet<>();
        portraitButton.setToggleGroup(tGroup);
        landscapeButton.setToggleGroup(tGroup);
        landscapeButton.selectedProperty().addListener((o) -> {
            if (previewPane.getChildren().isEmpty() == false) {
                ObservableList<Node> children = previewPane.getChildren();
                for (Node node : children) {
                    node.setRotate(90);
                }
            }
        });
        portraitButton.selectedProperty().addListener((o) -> {
            if (previewPane.getChildren().isEmpty() == false) {
                ObservableList<Node> children = previewPane.getChildren();
                for (Node node : children) {
                    node.setRotate(0);
                }
            }
        });
        copyiesPerPageCombo.setItems(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6));
        copyiesPerPageCombo.getSelectionModel().selectFirst();
        paperSizeCombo.setConverter(new StringConverter<Paper>() {
            @Override
            public String toString(Paper item) {
                if (item == null) {
                    return null;
                } else {
                    return item.getName() + " (" + item.getWidth() + " x " + item.getHeight() + ")";
                }
            }

            @Override
            public Paper fromString(String string) {
                PrinterAttributes printerAttributes = printerCombo.getSelectionModel().getSelectedItem().getPrinterAttributes();
                Set<Paper> supportedPapers = printerAttributes.getSupportedPapers();
                for (Paper supportedPaper : supportedPapers) {
                    if (string.contains(supportedPaper.getName())) {
                        return supportedPaper;
                    }
                }
                return null;
            }
        });
        printerCombo.getSelectionModel().selectedItemProperty().addListener((sPrinter) -> {
            PrinterAttributes printerAttributes = printerCombo.getSelectionModel().getSelectedItem().getPrinterAttributes();
            Set<Paper> supportedPapers = printerAttributes.getSupportedPapers();
            paperSizeCombo.setItems(FXCollections.observableArrayList(new ArrayList<>(supportedPapers)));
            Platform.runLater(() -> {
                PageLayout defaultPageLayout = printerCombo.getSelectionModel().getSelectedItem().getDefaultPageLayout();
                paperSizeCombo.getSelectionModel().select(defaultPageLayout.getPaper());
            });
        });
        paperSizeCombo.getSelectionModel().selectedItemProperty().addListener((o) -> {
            Paper selPaper = paperSizeCombo.getSelectionModel().getSelectedItem();
            PageOrientation pgOrient;
            if (portraitButton.isSelected()) {
                pgOrient = PageOrientation.PORTRAIT;
            } else {
                pgOrient = PageOrientation.LANDSCAPE;
            }
            if (prefHeight == -1) {
                prefHeight = previewPane.getPrefHeight();
            }
            if (prefWidth == -1) {
                prefWidth = previewPane.getPrefWidth();
            }
            if (selPaper != null) {
                selectedPrinter = printerCombo.getSelectionModel().getSelectedItem();
                pageLayout = selectedPrinter.createPageLayout(selPaper, pgOrient, Printer.MarginType.DEFAULT);
                paperRatio = selPaper.getHeight() / selPaper.getWidth();
                double wRatio = pageLayout.getPaper().getWidth() / pageBox.getWidth();
                double leftMargin = pageLayout.getLeftMargin() / wRatio;
                double rightMargin = pageLayout.getRightMargin() / wRatio;
                double hRatio = pageLayout.getPaper().getHeight() / pageBox.getHeight();
                double topMargin = pageLayout.getLeftMargin() / hRatio;
                double buttonMargin = pageLayout.getLeftMargin() / hRatio;
                HBox.setMargin(previewPane, new Insets(topMargin, rightMargin, buttonMargin, leftMargin));
                double newWidth = prefHeight / paperRatio;
                long roundWith = Math.round(newWidth);
                pageBox.setMaxSize(newWidth, prefHeight);
                pageBox.setPrefSize(newWidth, prefHeight);
                if (Platform.isFxApplicationThread()) {
                    showPreview();
                } else {
                    Platform.runLater(() -> {
                        showPreview();
                    });
                }
            }
        });
        Thread.ofVirtual().start(() -> {
            ObservableSet<Printer> printers = Printer.getAllPrinters();
            printerCombo.setItems(FXCollections.observableArrayList(new ArrayList<>(printers)));
            Printer defaultprinter = Printer.getDefaultPrinter();
            Platform.runLater(() -> {
                printerCombo.getSelectionModel().select(defaultprinter);
            });
        });
        borderLessBox.selectedProperty().addListener((o) -> {
            showPreview();
        });
        copyiesPerPageCombo.getSelectionModel().selectedItemProperty().addListener((ov, t, t1) -> {
            if (t.intValue() != t1.intValue()) {
                showPreview();
            }
        });
    }

    public void shutdown() {        
    }

    public void setDialogPane(DialogPane pane) {
        this.dialogPane = pane;
    }

    @FXML
    private void allPagesRadioAction(ActionEvent event) {
    }

    @FXML
    private void rangeRadioAction(ActionEvent event) {
    }

    @FXML
    private void pageFormTextAction(ActionEvent event) {
    }

    @FXML
    private void pageToTextAction(ActionEvent event) {
    }

    public void print(Label jobStatus, Set<MediaFile> allPrintItems) {

        jobStatus.textProperty().unbind();
        jobStatus.setText("Creating print job...");

        // Create a printer job for the default printer
        PrinterJob job = PrinterJob.createPrinterJob(selectedPrinter);

        if (job != null) {
            // Show the printer job status
            jobStatus.textProperty().bind(job.jobStatusProperty().asString());
            job.getJobSettings().setPageLayout(pageLayout);

            boolean openPrintDialog = job.showPrintDialog(stage);

            previewPane.setEffect(null);
            previewPane.getStyleClass().clear();
            previewPane.setStyle("-fx-background-color:white");

            final double scaleX = pageLayout.getPrintableWidth() / pageBox.getWidth();
            final double scaleY = pageLayout.getPrintableHeight() / pageBox.getHeight();
            final double scale = Math.min(scaleX, scaleY);
            previewPane.getTransforms().add(new Scale(scale, scale));
            boolean printing = false;
            printing = job.printPage(previewPane);
            if (printing) {
                job.endJob();
                jobStatus.textProperty().unbind();
                jobStatus.setText("Finished.");
            } else {

            }
        } else {
            // Write Error Message
            jobStatus.setText("Not possible to create print job!");
        }
    }

    public void showPreview() {
        int correctVal = 0;
        if (borderLessBox.isSelected()) {
            correctVal = 5;
        }
        previewPane.getChildren().clear();
        while (previewPane.getRowConstraints().size() > 0) {
            previewPane.getRowConstraints().remove(0);
        }

        while (previewPane.getColumnConstraints().size() > 0) {
            previewPane.getColumnConstraints().remove(0);
        }
        int i = 0;
        String url;
        ObservableList<Image> pageList = FXCollections.observableArrayList();
        for (MediaFile next : allPrintItems) {
            try {
                url = next.getImageUrl().toString();
                Image img = new Image(url, true);
                pageList.add(img);
            } catch (MalformedURLException ex) {
                Logger.getLogger(PrintController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        Integer qtyPerPage = copyiesPerPageCombo.getSelectionModel().getSelectedItem();
        int row = 0;
        int col = 0;
        for (Image image : pageList) {
            ProgressIndicator prg = new ProgressIndicator();
            prg.progressProperty().bind(image.progressProperty());
            ImageView view = new ImageView();
            view.setPreserveRatio(true);
            final int cVal = correctVal;
            final int colImg = col;
            final int rowImg = row;
            image.progressProperty().addListener((ov, t, t1) -> {
                if ((Double) t1 == 1.0 && !image.isError()) {
                    previewPane.getChildren().remove(prg);
                    previewPane.add(view, colImg, rowImg);
                    if (qtyPerPage == 1 || qtyPerPage == 2) {
                        view.setFitHeight((previewPane.getHeight()));
                        view.setFitWidth((previewPane.getWidth()));
                    } else {
                        if (image.getWidth() < image.getHeight()) {
                            view.setFitHeight((previewPane.getHeight()) / 3);
                            view.setFitWidth((previewPane.getWidth()) / 3);
                        } else {
                            view.setFitHeight((previewPane.getHeight()) / 2);
                            view.setFitWidth((previewPane.getWidth()) / 2);
                        }

                    }
                }
            });
            if (previewPane.getRowConstraints().size() != (row + 1)) {
                previewPane.getRowConstraints().add(row, new RowConstraints());
                previewPane.getRowConstraints().get(row).setFillHeight(true);
                previewPane.getRowConstraints().get(row).setVgrow(Priority.ALWAYS);
                previewPane.getRowConstraints().get(row).setValignment(VPos.CENTER);
                previewPane.getRowConstraints().get(row).setMinHeight(0);
            }
            if (previewPane.getColumnConstraints().size() != (col + 1)) {
                previewPane.getColumnConstraints().add(col, new ColumnConstraints());
                previewPane.getColumnConstraints().get(col).setFillWidth(true);
                previewPane.getColumnConstraints().get(col).setHgrow(Priority.ALWAYS);
                previewPane.getColumnConstraints().get(col).setHalignment(HPos.CENTER);
                previewPane.getColumnConstraints().get(col).setMinWidth(0);
            }
            view.setFitHeight(50);
            view.setFitWidth(50);
            previewPane.add(prg, col, row);
            view.setImage(image);

            switch (pageList.indexOf(image) + 1) {
                case 1:
                    row++;
                    break;
                case 2:
                    col++;
                    row = 0;
                    break;
                case 3:
                    row++;
                    break;
                case 4:
                    col = 0;
                    row++;
                    break;
                case 5:
                    col++;
                    break;
                case 6:
                    row++;
                    col = 0;
                    break;
            }
            if (qtyPerPage == pageList.indexOf(image) + 1) {
                return;
            }
        }
    }

    public void setAllPrintItems(Set<MediaFile> allPrintItems) {
        this.allPrintItems = allPrintItems;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

}
