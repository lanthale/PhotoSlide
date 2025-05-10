/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide;

import com.gluonhq.maps.MapPoint;
import com.gluonhq.maps.MapView;
import fr.dudie.nominatim.model.Address;
import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.ResourceBundle;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.converter.NumberStringConverter;
import org.controlsfx.control.textfield.AutoCompletionBinding;
import org.controlsfx.control.textfield.CustomTextField;
import org.controlsfx.control.textfield.TextFields;
import org.kordamp.ikonli.javafx.FontIcon;
import org.photoslide.browsermetadata.GeoCoding;
import org.photoslide.browsermetadata.Tag;

/**
 *
 * @author selfemp
 */
public class ExportDialogController implements Initializable {

    private Collection<String> keywordList;
    @FXML
    private ComboBox<String> fileSequenceCombo;
    @FXML
    private TextField outputDirText;
    @FXML
    private TextField filenamePrefixText;
    @FXML
    private ComboBox<String> fileFormatCombo;
    @FXML
    private Label exampleLabel;

    private Stage root;
    @FXML
    private CheckBox exportSelectedBox;
    @FXML
    private CheckBox exportDeletedFileBox;
    @FXML
    private Slider qualitySlider;
    @FXML
    private Tooltip qSliderToolTip;
    @FXML
    private CheckBox exportAllMetaData;
    @FXML
    private CheckBox exportBasicMetadataBox;
    @FXML
    private CheckBox overwriteFilesBox;
    @FXML
    private Label errorLabelDirectory;
    @FXML
    private FlowPane keywordText;
    @FXML
    private TextField addKeywordTextField;
    @FXML
    private CheckBox replaceKeywordChoiceBox;
    @FXML
    private CheckBox replaceTitleBox;
    @FXML
    private TextField titleTextBox;
    @FXML
    private Button plusButton;
    @FXML
    private CheckBox replaceGPSCheckBox;
    @FXML
    private MapView mapView;
    private GeoCoding geoCoding;
    @FXML
    private CustomTextField customField;
    @FXML
    private Button searchButton;
    @FXML
    private VBox mapSelectionPane;
    @FXML
    private CustomTextField heightTextField;
    private MapPoint circle;
    @FXML
    private TextField qualityTextField;
    @FXML
    private Tooltip outputDirToolTip;
    @FXML
    private ComboBox<String> sortComboBox;

    private MarkerLayer markerPos;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        fileFormatCombo.getItems().add("JPG");
        fileFormatCombo.getItems().add("PNG");
        fileFormatCombo.getItems().add("TIFF");
        fileFormatCombo.getSelectionModel().select("JPG");
        fileFormatCombo.getSelectionModel().selectedItemProperty().addListener((o) -> {
            switch (fileFormatCombo.getSelectionModel().getSelectedItem()) {
                case "JPG":
                    qualitySlider.setDisable(false);
                    qualitySlider.setMin(40);
                    qualitySlider.setMax(100);
                    qualitySlider.setValue(93);
                    qualitySlider.setMajorTickUnit(1);
                    qualitySlider.setMinorTickCount(0);
                    qualitySlider.setBlockIncrement(1);
                    break;
                case "PNG":
                    qualitySlider.setDisable(false);
                    qualitySlider.setMin(1);
                    qualitySlider.setMax(9);
                    qualitySlider.setValue(6);
                    qualitySlider.setMajorTickUnit(1);
                    qualitySlider.setMinorTickCount(0);
                    qualitySlider.setBlockIncrement(1);
                    break;
                case "TIFF":
                    qualitySlider.setDisable(true);
                    break;
            }
        });
        exampleLabel.textProperty().bind(filenamePrefixText.textProperty().concat("_1." + fileFormatCombo.getSelectionModel().getSelectedItem()));
        qSliderToolTip.textProperty().bind(qualitySlider.valueProperty().asString());
        qSliderToolTip.textProperty().bind(Bindings.format("%.0f", qualitySlider.valueProperty()));
        qSliderToolTip.setShowDelay(Duration.ZERO);
        qualityTextField.textProperty().bindBidirectional(qualitySlider.valueProperty(), new NumberStringConverter("##"));
        fileSequenceCombo.getItems().add("Title/Caption based");
        fileSequenceCombo.getItems().add("Orginial filename");
        fileSequenceCombo.getItems().add("Custom filname");
        fileSequenceCombo.getSelectionModel().select("Title/Caption based");
        fileSequenceCombo.valueProperty().addListener((o) -> {
            String selectedItem = fileSequenceCombo.getSelectionModel().getSelectedItem();
            if (selectedItem.equalsIgnoreCase("Custom filname")) {
                filenamePrefixText.setDisable(false);
                filenamePrefixText.clear();
            }
            if (selectedItem.equalsIgnoreCase("Orginial filename")) {
                filenamePrefixText.setDisable(true);
                filenamePrefixText.clear();
                filenamePrefixText.setText("<Original>");
            }
        });
        fileSequenceCombo.getSelectionModel().select("Title/Caption based");
        filenamePrefixText.setText("Probe_01");
        filenamePrefixText.setDisable(true);
        exportBasicMetadataBox.setOnAction((t) -> {
            if (exportAllMetaData.isSelected()) {
                exportAllMetaData.setSelected(false);
            }
        });
        exportAllMetaData.setOnAction((t) -> {
            if (exportBasicMetadataBox.isSelected()) {
                exportBasicMetadataBox.setSelected(false);
            }
        });
        replaceTitleBox.setOnAction((t) -> {
            if (replaceTitleBox.isSelected()) {
                titleTextBox.setDisable(false);
            } else {
                titleTextBox.setDisable(true);
            }
        });
        replaceKeywordChoiceBox.setOnAction((t) -> {
            if (replaceKeywordChoiceBox.isSelected()) {
                keywordText.setDisable(false);
                addKeywordTextField.setDisable(false);
            } else {
                keywordText.setDisable(true);
                addKeywordTextField.setDisable(true);
            }
        });
        replaceGPSCheckBox.setOnAction((t) -> {
            if (replaceGPSCheckBox.isSelected()) {
                mapSelectionPane.setDisable(false);
            } else {
                mapSelectionPane.setDisable(true);
            }
        });
        keywordList = FXCollections.observableArrayList();
        geoCoding = new GeoCoding();
        customField.setRight(new FontIcon("ti-close"));
        //mapView.setMapType(MapType.OSM);
        mapView.setPrefSize(350, 90);
        mapView.setMaxSize(350, 90);
        //mapView.initialize();
        MapPoint c = new MapPoint(48.135125, 11.581981);
        mapView.setCenter(c);
        markerPos = new MarkerLayer();
        mapView.addLayer(markerPos);
        //markerPos.addFlag(c);
        mapView.setEffect(new ColorAdjust(0, -0.5, 0, 0));
        mapView.setOnMouseClicked((t) -> {
            t.getPickResult();
            final MapPoint newPosition = mapView.getMapPosition(t.getSceneX(), t.getSceneY());
            mapView.setCenter(newPosition);
            markerPos.removeAllPoints();
            markerPos.addFlag(newPosition);
            Task<Address> taskFind = new Task<>() {
                @Override
                protected Address call() throws Exception {
                    return geoCoding.geoSearchForGPS(newPosition.getLongitude(), newPosition.getLatitude());
                }
            };
            taskFind.setOnSucceeded((k) -> {
                customField.setText(((Address) k.getSource().getValue()).getDisplayName());                
            });
            new Thread(taskFind).start();
        });
        outputDirToolTip.textProperty().bind(outputDirText.textProperty());
        sortComboBox.getItems().addAll("Record time based", "Actual view");
        sortComboBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void outputSelectionButtonAction(ActionEvent event) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setInitialDirectory(new File(outputDirText.getText()));
        File selectedDirectory = directoryChooser.showDialog((Stage) fileSequenceCombo.getScene().getWindow());
        outputDirText.setText(selectedDirectory.getAbsolutePath());
    }

    public String getFileFormat() {
        return fileFormatCombo.getSelectionModel().getSelectedItem();
    }

    public String getFilename() {
        return filenamePrefixText.getText() + "_";
    }

    public String getOutputDir() {
        return outputDirText.getText();
    }

    public void setInitOutDir(String dir) {
        outputDirText.setText(dir);
    }

    public void setTitel(String titel) {
        if (titel != null) {
            filenamePrefixText.setText(titel);
            //exampleLabel.setText(filenamePrefixText.getText() + "_1" + "." + fileFormatCombo.getSelectionModel().getSelectedItem());
        } else {
            fileSequenceCombo.getSelectionModel().select("Custom filname");
        }
    }

    public CheckBox getExportSelectedBox() {
        return exportSelectedBox;
    }

    public CheckBox getExportDeletedFileBox() {
        return exportDeletedFileBox;
    }

    public CheckBox getExportAllMetaData() {
        return exportAllMetaData;
    }

    public CheckBox getExportBasicMetadataBox() {
        return exportBasicMetadataBox;
    }

    public CheckBox getOverwriteFilesBox() {
        return overwriteFilesBox;
    }

    public Label getErrorLabelDirectory() {
        return errorLabelDirectory;
    }

    @FXML
    private void addKeywordAction(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            keywordText.getChildren().add(new Tag(addKeywordTextField.getText()));
            addKeywordTextField.clear();
        }
        event.consume();
    }

    public Collection<String> getKeywordList() {
        return keywordList;
    }

    public String getKeywordsAsString() {
        StringBuilder sb = new StringBuilder();
        if (!keywordText.getChildren().isEmpty()) {
            keywordText.getChildren().forEach((tagitem) -> {
                sb.append(((Tag) tagitem).getText()).append(";");
            });
            String substring = sb.toString().substring(0, sb.toString().length() - 1);
            return substring;
        } else {
            return "";
        }
    }

    public String getTitle() {
        return titleTextBox.getText();
    }

    public CheckBox getReplaceTitleBox() {
        return replaceTitleBox;
    }

    public CheckBox getReplaceKeywordChoiceBox() {
        return replaceKeywordChoiceBox;
    }

    @FXML
    private void plusButtonAction(ActionEvent event) {
        keywordText.getChildren().add(new Tag(addKeywordTextField.getText()));
        addKeywordTextField.clear();
    }

    @FXML
    private void searchButtonAction(ActionEvent event) {
        String name = customField.getText();

        Task<ObservableList<String>> task = new Task<>() {
            @Override
            protected ObservableList<String> call() throws Exception {
                return geoCoding.geoSearchForNameAsStrings(name);
            }
        };
        task.setOnScheduled((t) -> {
            ProgressIndicator ind = new ProgressIndicator();
            ind.setPrefSize(10, 10);
            ind.setMinSize(10, 10);
            ind.setMaxSize(10, 10);
            customField.setRight(ind);
        });
        task.setOnSucceeded((t) -> {
            customField.setRight(null);
            ObservableList<String> geoSearchForNameAsStrings = (ObservableList<String>) t.getSource().getValue();
            //set autocomplete
            AutoCompletionBinding<String> autoComp = TextFields.bindAutoCompletion(customField, geoSearchForNameAsStrings);
            customField.setText(geoSearchForNameAsStrings.get(0));
            customField.requestFocus();
            autoComp.setUserInput(name);
            autoComp.setOnAutoCompleted((o) -> {
                int index = geoSearchForNameAsStrings.indexOf(customField.getText());
                if (index == -1) {
                    index = 0;
                }
                geoCoding.setLastSearchGPSResult(geoCoding.getLastSearchResult().get(index));
                MapPoint c = new MapPoint(geoCoding.getLastSearchResult().get(index).getLatitude(), geoCoding.getLastSearchResult().get(index).getLongitude());
                mapView.setCenter(c);
                markerPos.removeAllPoints();
                markerPos.addFlag(c);
            });
        });
        task.setOnFailed((t) -> {
            customField.setRight(null);
        });
        new Thread(task).start();
    }

    @FXML
    private void searchTFPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            searchButtonAction(null);
        }
        event.consume();
    }

    public CheckBox getReplaceGPSCheckBox() {
        return replaceGPSCheckBox;
    }

    public String getSelectedGPSPos() {
        if (replaceGPSCheckBox.isSelected()) {
            return geoCoding.getLastSearchGPSResult().getLatitude() + ";" + geoCoding.getLastSearchGPSResult().getLongitude();
        }
        return "";
    }

    public double getSelectedGPSPosLat() {
        if (replaceGPSCheckBox.isSelected()) {
            return geoCoding.getLastSearchGPSResult().getLatitude();
        }
        return -1;
    }

    public double getSelectedGPSPosLon() {
        if (replaceGPSCheckBox.isSelected()) {
            return geoCoding.getLastSearchGPSResult().getLongitude();
        }
        return -1;
    }

    public CustomTextField getHeightTextField() {
        return heightTextField;
    }

    public String getFoundPlaceName() {
        return geoCoding.getLastSearchGPSResult().getDisplayName();
    }

    public int getQualityValue() {
        int val = Integer.parseInt(qualityTextField.getText());
        return val;
    }

    public ComboBox<String> getSortComboBox() {
        return sortComboBox;
    }

}
