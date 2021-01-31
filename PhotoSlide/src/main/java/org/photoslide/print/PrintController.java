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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterAttributes;
import javafx.print.PrinterJob;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.transform.Scale;
import javafx.util.StringConverter;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.datamodel.MediaFile;

/**
 *
 * @author selfemp
 */
public class PrintController implements Initializable {

    private ExecutorService executor;
    private ExecutorService executorParallel;
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

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("SearchToolExecutor"));
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryPS("SearchToolExecutor"));
        tGroup = new ToggleGroup();
        prefHeight = -1;
        prefWidth = -1;
        allPrintItems = new HashSet<>();
        portraitButton.setToggleGroup(tGroup);
        landscapeButton.setToggleGroup(tGroup);
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
            if (prefHeight == -1) {
                prefHeight = previewPane.getPrefHeight();
            }
            if (prefWidth == -1) {
                prefWidth = previewPane.getPrefWidth();
            }
            if (selPaper != null) {
                double ratio = selPaper.getHeight() / selPaper.getWidth();
                double newWidth = prefHeight / ratio;
                long roundWith = Math.round(newWidth);
                previewPane.setMaxSize(roundWith, prefHeight);
                previewPane.setPrefSize(roundWith, prefHeight);
                if (Platform.isFxApplicationThread()) {
                    showPreview();
                } else {
                    Platform.runLater(() -> {
                        showPreview();
                    });
                }
            }
        });
        executorParallel.submit(() -> {
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
        executor.shutdown();
        executorParallel.shutdown();
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

        Printer selectedPrinter = printerCombo.getSelectionModel().getSelectedItem();
        // Create a printer job for the default printer
        PrinterJob job = PrinterJob.createPrinterJob(selectedPrinter);

        if (job != null) {
            // Show the printer job status
            jobStatus.textProperty().bind(job.jobStatusProperty().asString());

            PageOrientation pgOrient;
            if (portraitButton.isSelected()) {
                pgOrient = PageOrientation.PORTRAIT;
            } else {
                pgOrient = PageOrientation.LANDSCAPE;
            }

            PageLayout pageLayout = selectedPrinter.createPageLayout(paperSizeCombo.getValue(), pgOrient, Printer.MarginType.DEFAULT);
            job.getJobSettings().setPageLayout(pageLayout);

            final double scaleX = pageLayout.getPrintableWidth() / previewPane.getWidth();
            final double scaleY = pageLayout.getPrintableHeight() / previewPane.getHeight();
            final double scale = Math.min(scaleX, scaleY);
            if (scale < 1.0) {
                previewPane.getTransforms().add(new Scale(scale, scale));
            }

            ObservableList<Image> pageList = FXCollections.observableArrayList();
            boolean printing = false;
            int i = 0;
            for (MediaFile next : allPrintItems) {
                String url;
                try {
                    url = next.getImageUrl().toString();
                    Image img = new Image(url, true);
                    pageList.add(img);
                    Integer qtyPerPage = copyiesPerPageCombo.getSelectionModel().getSelectedItem();
                    if (i != 0 && (i % qtyPerPage == 0 || i == (allPrintItems.size() - 1))) {
                        //tableView.setItems(pageList);
                        int row = 0;
                        int col = 0;
                        for (Image image : pageList) {
                            previewPane.add(new ImageView(image), row, col);
                            switch (qtyPerPage) {
                                case 1 -> {
                                }
                                case 2 -> {
                                    row++;
                                    if (row > 1) {
                                        row = 1;
                                    }
                                }
                                case 3 -> {
                                    if (col == 0) {
                                        col++;
                                    } else {
                                        row++;
                                        col = 0;
                                    }
                                }
                                case 4 -> {
                                    if (col == 0) {
                                        col++;
                                    } else {
                                        row++;
                                        col = 0;
                                    }
                                }
                                case 5 -> {
                                    if (col == 0) {
                                        col++;
                                    } else {
                                        row++;
                                        col = 0;
                                    }
                                }
                                case 6 -> {
                                    if (col == 0) {
                                        col++;
                                    } else {
                                        row++;
                                        col = 0;
                                    }
                                }
                            }
                        }
                        //setting of items to print
                        printing = job.printPage(previewPane);
                        pageList.clear();
                    }
                } catch (MalformedURLException ex) {
                    Logger.getLogger(PrintController.class.getName()).log(Level.SEVERE, null, ex);
                }
                i++;
            }
            //tableView.setItems(allPrintItems);
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
        while (previewPane.getRowConstraints().size() > 1) {
            previewPane.getRowConstraints().remove(0);
        }

        while (previewPane.getColumnConstraints().size() > 1) {
            previewPane.getColumnConstraints().remove(0);
        }
        int i = 0;
        String url;
        previewPane.getChildren().clear();
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
            ImageView view = new ImageView(image);
            view.setPreserveRatio(true);
            final int cVal = correctVal;
            image.progressProperty().addListener((ov, t, t1) -> {
                if ((Double) t1 == 1.0 && !image.isError()) {
                    if (image.getWidth() > image.getHeight()) {
                        view.setRotate(90);
                        view.setFitHeight(previewPane.getPrefWidth() / qtyPerPage);
                        view.setFitWidth((previewPane.getPrefHeight() + cVal) / qtyPerPage);
                    } else {
                        view.setRotate(0);
                        view.setFitHeight((previewPane.getPrefHeight() + cVal) / qtyPerPage);
                        view.setFitWidth(previewPane.getPrefWidth() / qtyPerPage);
                    }
                }
            });
            previewPane.add(view, row, col);
            switch (qtyPerPage) {
                case 1 -> {
                }
                case 2 -> {
                    row++;
                    if (row > 1) {
                        row = 1;
                    }
                }
                case 3 -> {
                    if (col == 0) {
                        col++;
                    } else {
                        row++;
                        col = 0;
                    }
                }
                case 4 -> {
                    if (col == 0) {
                        col++;
                    } else {
                        row++;
                        col = 0;
                    }
                }
                case 5 -> {
                    if (col == 0) {
                        col++;
                    } else {
                        row++;
                        col = 0;
                    }
                }
                case 6 -> {
                    if (col == 0) {
                        col++;
                    } else {
                        row++;
                        col = 0;
                    }
                }
            }
        }
    }

    public void setAllPrintItems(Set<MediaFile> allPrintItems) {
        this.allPrintItems = allPrintItems;
    }    

}
