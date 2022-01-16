/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browsercollections;

import org.photoslide.search.SearchIndex;
import org.photoslide.MainViewController;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.Utility;
import org.photoslide.browserlighttable.LighttableController;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.javafx.StackedFontIcon;

/**
 *
 * @author selfemp
 */
public class CollectionsController implements Initializable {

    @FXML
    private MenuItem pasteMenu;
    @FXML
    private MenuItem deleteMenu;
    private Image iconImage;
    private SearchIndex searchIndexProcess;

    private enum ClipboardMode {
        CUT,
        COPY
    }
    private ExecutorService executor;
    private ExecutorService executorParallel;
    private ScheduledExecutorService executorParallelTimers;

    private Utility util;
    private static final String NODE_NAME = "PhotoSlide";
    private Path selectedPath;
    private LinkedHashMap<String, String> collectionStorage;
    private LinkedHashMap<String, String> collectionStorageSearchIndex;
    private int activeAccordionPane;
    private Path clipboardPath;
    private ClipboardMode clipboardMode;

    private MainViewController mainController;
    @FXML
    private Button renameButton;
    @FXML
    private Button plusButton;
    @FXML
    private MenuButton menuButton;
    @FXML
    private Accordion accordionPane;

    private LighttableController lighttablePaneController;
    private Preferences pref;
    @FXML
    private Button refreshButton;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("collectionsController"));
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryPS("collectionsControllerParallel"));
        executorParallelTimers = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryPS("collectionsControllerParallelScheduled"));
        util = new Utility();
        pref = Preferences.userRoot().node(NODE_NAME);
        collectionStorage = new LinkedHashMap<>();
        collectionStorageSearchIndex = new LinkedHashMap<>();

        iconImage = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
        accordionPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                searchIndexProcess = new SearchIndex(mainController.getMetadataPaneController());
                loadURLs();
            }
        });
    }

    private void loadURLs() {
        Task<Boolean> indexTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                collectionStorageSearchIndex.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach((t) -> {
                    if (this.isCancelled() == false) {
                        searchIndexProcess.createSearchIndex(t.getValue());
                        searchIndexProcess.checkSearchIndex(t.getValue());
                    }
                });
                return true;
            }
        };
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                if (collectionStorage.isEmpty()) {
                    Platform.runLater(() -> {
                        ShowEmptyHelp();
                    });
                } else {
                    collectionStorage.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach((dTree) -> {
                        if (this.isCancelled() == false) {
                            loadDirectoryTree(dTree.getValue());
                        }
                    });
                }
                return true;
            }
        };
        task.setOnSucceeded((WorkerStateEvent t) -> {
            Platform.runLater(() -> {
                if (accordionPane.getPanes().size() > 0) {
                    if (activeAccordionPane == -1) {
                        activeAccordionPane = 0;
                    }
                    accordionPane.setExpandedPane(accordionPane.getPanes().get(activeAccordionPane));
                }
            });
            util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 5, true);
            util.hideNodeAfterTime(mainController.getProgressPane(), 5, true);            
        });
        executorParallel.submit(task);
        executorParallelTimers.schedule(indexTask, 5, TimeUnit.SECONDS);
    }

    public void saveSettings() {
        pref.putInt("activeAccordionPane", accordionPane.getPanes().indexOf(accordionPane.getExpandedPane()));
    }

    public void restoreSettings() {
        try {
            activeAccordionPane = pref.getInt("activeAccordionPane", 0);
            String[] keys = pref.keys();
            for (String key : keys) {
                if (key.contains("URL")) {
                    collectionStorage.put(key, pref.get(key, null));
                }
                if (key.contains("INDEX")) {
                    collectionStorageSearchIndex.put(key, pref.get(key, null));
                }
            }
        } catch (BackingStoreException ex) {
            Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void injectMainController(MainViewController mainController) {
        this.mainController = mainController;
    }

    public void injectLighttableController(LighttableController mainController) {
        this.lighttablePaneController = mainController;
    }

    private void createRootTree(Path root_file, TreeItem parent) throws IOException {
        Platform.runLater(() -> {
            mainController.getProgressPane().setVisible(true);
            mainController.getStatusLabelLeft().setText("Scanning...");
        });
        try ( DirectoryStream<Path> newDirectoryStream = Files.newDirectoryStream(root_file, (entry) -> {
            boolean res = true;
            if (entry.getFileName().toString().startsWith(".")) {
                res = false;
            }
            if (entry.getFileName().toString().startsWith("@")) {
                res = false;
            }
            return res;
        })) {
            Stream<Path> sortedStream = StreamSupport.stream(newDirectoryStream.spliterator(), false).sorted();
            final AtomicInteger i = new AtomicInteger(0);
            final long qty = Files.list(root_file).count();
            sortedStream.forEach((t) -> {
                Platform.runLater(() -> {
                    double prgValue = ((double) (i.addAndGet(1)) / qty * 100);
                    mainController.getProgressbarLabel().setText(t.toString() + " " + String.format("%1$,.0f", prgValue) + "%");
                });

                try {
                    createTree(t, parent);
                } catch (IOException ex) {
                    Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                    util.showError(this.accordionPane, "Cannot create directory tree!", ex);
                }

            });

        }
    }

    private void createTree(Path root_file, TreeItem parent) throws IOException {
        if (Files.isDirectory(root_file)) {
            TreeItem<PathItem> node = new TreeItem(new PathItem(root_file));
            TreeItem placeholder = new TreeItem(new PathItem(Paths.get("Please wait...")));
            parent.getChildren().add(node);
            try ( DirectoryStream<Path> newDirectoryStream = Files.newDirectoryStream(root_file, (entry) -> {
                boolean res = true;
                if (entry.getFileName().toString().startsWith(".")) {
                    res = false;
                }
                if (entry.getFileName().toString().startsWith("@")) {
                    res = false;
                }
                return res;
            })) {
                Stream<Path> sortedStream = StreamSupport.stream(newDirectoryStream.spliterator(), false).sorted();
                sortedStream.forEach((t) -> {

                    Platform.runLater(() -> {
                        if (node.getChildren().isEmpty()) {
                            ProgressIndicator waitPrg = new ProgressIndicator();
                            waitPrg.setPrefSize(15, 15);
                            placeholder.setGraphic(waitPrg);
                            node.getChildren().add(placeholder);
                        }
                    });

                    EventHandler eventH = new EventHandler() {
                        @Override
                        public void handle(Event event) {

                            Task<Boolean> taskTree = new Task<>() {
                                @Override
                                protected Boolean call() throws Exception {
                                    try {
                                        createTree(t, node); // Continue the recursive as usual                                        
                                    } catch (IOException ex) {
                                        Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                                    }
                                    return null;
                                }
                            };
                            taskTree.setOnSucceeded((WorkerStateEvent t) -> {
                                if (node.getChildren().size() > 0) {
                                    if (node.getChildren().contains(placeholder)) {
                                        node.getChildren().remove(placeholder); // Remove placeholder
                                    }
                                    node.removeEventHandler(TreeItem.branchExpandedEvent(), this); // Remove event 
                                }
                            });
                            taskTree.setOnFailed((WorkerStateEvent t) -> {
                                mainController.getStatusLabelLeft().setText(t.getSource().getMessage());
                                util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 10, true);
                            });
                            executor.submit(taskTree);

                        }
                    };
                    node.addEventHandler(TreeItem.branchExpandedEvent(), eventH);
                });
            }
        } else {
            //parent.getChildren().add(new TreeItem(root_file.getFileName()));
        }
    }

    public void Shutdown() {
        if (searchIndexProcess != null) {
            searchIndexProcess.shutdown();
        }
        executor.shutdownNow();
        executorParallel.shutdownNow();
        executorParallelTimers.shutdownNow();
    }

    @FXML
    private void addCollectionAction(ActionEvent event) {
        addExistingPath();
    }

    public void addExistingPath() {
        Stage stage = (Stage) accordionPane.getScene().getWindow();
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory != null) {
            loadDirectoryTree(selectedDirectory.getAbsolutePath());
            String prefKeyForSaving = getPrefKeyForSaving();
            pref.put("URL" + prefKeyForSaving, selectedDirectory.getAbsolutePath());
            if (createSearchIndex(selectedDirectory.getAbsolutePath()) == true) {
                pref.put("INDEX" + prefKeyForSaving, selectedDirectory.getAbsolutePath());
                searchIndexProcess.createSearchIndex(selectedDirectory.getAbsolutePath());
            }
        }
    }

    private boolean createSearchIndex(String p) {
        Alert alert = new Alert(AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO);
        alert = Utility.setDefaultButton(alert, ButtonType.YES);
        alert.setHeaderText("Do you want to include \n'" + p + "'\n to the search index (if no fulltextsearch is not available for that collection)?");
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        alert.setResizable(false);
        Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) accordionPane.getScene().getWindow());
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(iconImage);
        alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() == ButtonType.YES) {
            return true;
        } else {
            return false;
        }
    }

    private String getPrefKeyForSaving() {
        try {
            String[] keys = pref.keys();
            ArrayList<Integer> numbers = new ArrayList<>();
            for (String key : keys) {
                if (key.contains("URL")) {
                    numbers.add(Integer.parseInt(key.substring(3)));
                }
            }
            if (numbers.isEmpty()) {
                return "1";
            } else {
                Integer i = Collections.max(numbers);
                return "" + (i + 1);
            }
        } catch (BackingStoreException ex) {
            Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private void loadDirectoryTree(String selectedRootPath) {
        String path = selectedRootPath;

        ProgressIndicator waitPrg = new ProgressIndicator();
        waitPrg.setPrefSize(15, 15);
        waitPrg.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        TitledPane actCollectionTitlePane = new TitledPane();
        actCollectionTitlePane.setGraphic(waitPrg);
        actCollectionTitlePane.setText(path);
        actCollectionTitlePane.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        actCollectionTitlePane.setAnimated(true);
        actCollectionTitlePane.setTextAlignment(TextAlignment.LEFT);

        Platform.runLater(() -> {
            accordionPane.getPanes().add(actCollectionTitlePane);
        });
        Task<TreeView<PathItem>> task = new Task<TreeView<PathItem>>() {
            @Override
            protected TreeView<PathItem> call() throws Exception {
                TreeItem<PathItem> root = new TreeItem<>(new PathItem(Paths.get(path)));
                TreeView<PathItem> dirTreeView = new TreeView<>();
                dirTreeView.setEditable(true);
                dirTreeView.setCellFactory(TreeCellTextField.forTreeView(new PathItemConverter()));
                dirTreeView.setOnEditCommit((t) -> {
                    PathItem newValue = t.getNewValue();
                    PathItem oldValue = t.getOldValue();
                    try {
                        Files.move(oldValue.getFilePath(), oldValue.getFilePath().resolveSibling(newValue.getFilePath()));
                    } catch (IOException ex) {
                        ((TreeCellTextField) dirTreeView.getCellFactory()).cancelEdit();
                        Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
                dirTreeView.setShowRoot(false);
                dirTreeView.setDisable(true);
                Platform.runLater(() -> {
                    actCollectionTitlePane.setContent(dirTreeView);
                    dirTreeView.setRoot(root);
                    mainController.getProgressPane().setVisible(true);
                    mainController.getStatusLabelLeft().setVisible(true);
                    mainController.getStatusLabelLeft().setText("Scanning...");
                    mainController.getProgressbar().setProgress(-1);
                });
                createRootTree(Paths.get(path), root);
                return dirTreeView;
            }
        };
        task.setOnSucceeded((WorkerStateEvent t) -> {
            FontIcon greenIcon = new FontIcon("ti-agenda");
            greenIcon.setId("CollectorGreen");
            actCollectionTitlePane.setGraphic(greenIcon);
            TreeView<PathItem> dirTreeView = (TreeView<PathItem>) t.getSource().getValue();
            dirTreeView.getRoot().setExpanded(true);
            dirTreeView.setDisable(false);
            if (dirTreeView.getRoot().getChildren().isEmpty()) {
                dirTreeView.setShowRoot(true);
            }
            dirTreeView.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends TreeItem<PathItem>> ov, TreeItem<PathItem> t1, TreeItem<PathItem> t2) -> {
                TreeItem<PathItem> selectedItem = (TreeItem<PathItem>) t2;
                if (selectedItem != null) {
                    selectedPath = selectedItem.getValue().getFilePath();
                    lighttablePaneController.setSelectedPath(selectedItem.getValue().getFilePath());
                }
            });
        });
        task.setOnFailed((WorkerStateEvent t) -> {
            FontIcon redIcon = new FontIcon("ti-agenda");
            redIcon.setId("CollectorRed");
            actCollectionTitlePane.setGraphic(redIcon);
            mainController.getStatusLabelLeft().setText(t.getSource().getMessage());
            util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 10, true);
            Tooltip tip = new Tooltip(t.getSource().getException().getMessage());
            actCollectionTitlePane.setTooltip(tip);
        });
        executorParallel.submit(task);
    }

    public Path getSelectedPath() {
        return selectedPath;
    }

    @FXML
    private void refreshMenuAction(ActionEvent event) {
        refreshTree();
    }

    public void refreshTree() {
        try {
            TreeItem<PathItem> parent;
            TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
            ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
            String selectedItemName = selectedItems.get(0).getValue().toString();
            if (selectedItems.get(0).getParent() == null) {
                parent = selectedItems.get(0);
                parent.getChildren().clear();
                //createTree(parent.getValue().getFilePath(), parent);
                createRootTree(parent.getValue().getFilePath(), parent);
                SortedList<TreeItem<PathItem>> sorted = parent.getChildren().sorted();
                parent.getChildren().setAll(sorted);
                //Optional<TreeItem<PathItem>> findFirst = sorted.stream().filter(obj -> obj.getValue().toString().equalsIgnoreCase(selectedItemName)).findFirst();
                //treeView.getSelectionModel().select(findFirst.get());
            } else {
                parent = selectedItems.get(0).getParent();
                Path filePath = selectedItems.get(0).getValue().getFilePath();
                parent.getChildren().remove(selectedItems.get(0));
                createTree(filePath, parent);
                SortedList<TreeItem<PathItem>> sorted = parent.getChildren().sorted();
                parent.getChildren().setAll(sorted);
                Optional<TreeItem<PathItem>> findFirst = sorted.stream().filter(obj -> obj.getValue().toString().equalsIgnoreCase(selectedItemName)).findFirst();
                treeView.getSelectionModel().select(findFirst.get());
            }
        } catch (IOException ex) {
            Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
            util.showError(this.accordionPane, "Cannot create directory tree", ex);
        }
    }

    @FXML
    private void removeCollectionAction(ActionEvent event) {
        TitledPane expandedPane = accordionPane.getExpandedPane();
        if (expandedPane != null) {
            TreeView<PathItem> content = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
            PathItem value = content.getRoot().getValue();
            String pathToRemoveStr = value.getFilePath().toString();
            String prefKeyForRemoving = getPrefKeyForRemoving(pathToRemoveStr, "URL");
            if (prefKeyForRemoving != null) {
                pref.remove(prefKeyForRemoving);
                lighttablePaneController.resetLightTableView();
                accordionPane.getPanes().remove(accordionPane.getExpandedPane());
            }
            String prefKeyForRemovingIndex = getPrefKeyForRemoving(pathToRemoveStr, "INDEX");
            if (prefKeyForRemovingIndex != null) {
                pref.remove(prefKeyForRemovingIndex);
                searchIndexProcess.removeCollectionFromSearchDB(pathToRemoveStr);
            }
        } else {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setHeaderText("Please expand one pane to delete it!");

            alert.getDialogPane().getStylesheets().add(
                    getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(iconImage);
            Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) accordionPane.getScene().getWindow());
            alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
            alert.show();
        }
    }

    private String getPrefKeyForRemoving(String path, String prefix) {
        try {
            String[] keys = pref.keys();
            for (String key : keys) {
                String value = pref.get(key, "");
                if (path.contains(value)) {
                    if (key.contains(prefix)) {
                        return key;
                    }
                }
            }
        } catch (BackingStoreException ex) {
            Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private boolean checkIfElementInTreeSelected(String message) {
        TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
        ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            StackedFontIcon st = new StackedFontIcon();
            FontIcon f1 = new FontIcon("ti-layout-width-full");
            f1.setIconSize(50);
            FontIcon f2 = new FontIcon("ti-close");
            f2.setIconSize(30);
            st.getChildren().add(f1);
            st.getChildren().add(f2);
            alert.setGraphic(st);
            alert.setContentText(message);
            alert.getDialogPane().getStylesheets().add(
                    getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
            alert.setResizable(false);
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(iconImage);
            Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) accordionPane.getScene().getWindow());
            alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
            alert.showAndWait();
            return true;
        }
        return false;
    }

    @FXML
    private void createEventAction(ActionEvent event) {
        if (checkIfElementInTreeSelected("Please select an element in tree first to create a child collection!")) {
            return;
        }
        TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
        ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
        TreeItem<PathItem> parent = selectedItems.get(0);
        TextInputDialog alert = new TextInputDialog();
        alert.setTitle("Create event");
        alert.setHeaderText("Create event (directory)");
        StackedFontIcon stackIcon = new StackedFontIcon();
        FontIcon fileIcon = new FontIcon("ti-file");
        fileIcon.setIconSize(50);
        FontIcon plusIcon = new FontIcon("ti-plus");
        plusIcon.setIconSize(20);
        stackIcon.getChildren().add(fileIcon);
        stackIcon.getChildren().add(plusIcon);
        alert.setGraphic(stackIcon);
        alert.setContentText("Please enter the name:");
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        alert.setResizable(false);
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(iconImage);
        Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) accordionPane.getScene().getWindow());
        alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        Optional<String> result = alert.showAndWait();
        result.ifPresent((t) -> {
            Path filePath = selectedItems.get(0).getValue().getFilePath();
            String newPath = filePath.toString() + File.separator + t;
            Paths.get(newPath).toFile().mkdir();
            TreeItem<PathItem> newChild = new TreeItem<>(new PathItem(Paths.get(newPath)));
            parent.getChildren().add(newChild);
        });
    }

    @FXML
    private void cutEventAction(ActionEvent event) {
        if (checkIfElementInTreeSelected("Please select an element in the tree to be cut!")) {
            return;
        }
        TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
        ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
        TreeItem<PathItem> item = selectedItems.get(0);
        clipboardPath = item.getValue().getFilePath();
        clipboardMode = ClipboardMode.CUT;
        pasteMenu.setDisable(false);
        treeView.getSelectionModel().clearSelection();
        mainController.getStatusLabelLeft().setVisible(true);
        mainController.getStatusLabelLeft().setText("Cut collection " + clipboardPath.getFileName().toString());
        util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 3, true);
    }

    @FXML
    private void copyEventAction(ActionEvent event) {
        if (checkIfElementInTreeSelected("Please select an element in the tree to be copied!")) {
            return;
        }
        TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
        ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
        TreeItem<PathItem> item = selectedItems.get(0);
        clipboardPath = item.getValue().getFilePath();
        clipboardMode = ClipboardMode.COPY;
        pasteMenu.setDisable(false);
        treeView.getSelectionModel().clearSelection();
        mainController.getStatusLabelLeft().setVisible(true);
        mainController.getStatusLabelLeft().setText("Copy collection " + clipboardPath.getFileName().toString());
        util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 3, true);
    }

    @FXML
    private void deleteEventAction(ActionEvent event) {
        if (checkIfElementInTreeSelected("Please select an element in the tree to be deleted!")) {
            return;
        }
        TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
        ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
        TreeItem<PathItem> item = selectedItems.get(0);
        clipboardPath = item.getValue().getFilePath();
        System.out.println("clipboardPath " + clipboardPath);

        Alert alert = new Alert(AlertType.CONFIRMATION, "Delete event", ButtonType.CANCEL, ButtonType.OK);
        alert.setGraphic(new FontIcon("ti-trash:40"));
//alert.setHeaderText("Delete '" + clipboardPath + "' ?");
        //alert.setContentText("Delete '" + clipboardPath + "' ?");   
        alert.setHeaderText("Delete event");
        Text text = new Text("Delete '" + clipboardPath + "' ?");
        text.setWrappingWidth(400);
        text.setFill(Color.WHITE);
        FlowPane pane = new FlowPane(text);
        pane.setPadding(new Insets(10, 10, 10, 10));
        alert.getDialogPane().setContent(pane);
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(iconImage);
        Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) treeView.getScene().getWindow());
        alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        Optional<ButtonType> resultDiag = alert.showAndWait();
        if (resultDiag.get() == ButtonType.OK) {
            deleteMenu.setDisable(true);
            mainController.getProgressPane().setVisible(true);
            mainController.getProgressbar().setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            mainController.getStatusLabelLeft().setText("Delete collection");
            mainController.getProgressbarLabel().setText("...");
            Task<Boolean> taskDelete = new Task<>() {
                @Override
                protected Boolean call() throws IOException {
                    Files.walk(clipboardPath)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach((t) -> {
                                Platform.runLater(() -> {
                                    mainController.getProgressbarLabel().setText("Delete " + t.getName());
                                });
                                t.delete();
                            });
                    return true;
                }
            };
            taskDelete.setOnSucceeded((t) -> {
                refreshTree();
                treeView.getSelectionModel().clearSelection();
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressbarLabel().textProperty().unbind();
                mainController.getProgressPane().setVisible(false);
                mainController.getStatusLabelLeft().setVisible(false);
                deleteMenu.setDisable(false);
            });
            taskDelete.setOnFailed((t) -> {
                treeView.getSelectionModel().clearSelection();
                util.showError(this.accordionPane, "Cannot delete collection", t.getSource().getException());
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressbarLabel().textProperty().unbind();
                mainController.getProgressPane().setVisible(false);
                mainController.getStatusLabelLeft().setVisible(false);
                deleteMenu.setDisable(true);
                Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, "Cannot cut/copy collection!", t.getSource().getException());
            });
            executor.submit(taskDelete);
        }
    }

    @FXML
    private void pasteEventAction(ActionEvent event) {
        Path sourceFilePath = clipboardPath;

        TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
        ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
        TreeItem<PathItem> item = selectedItems.get(0);
        Path targetPath = item.getValue().getFilePath();
        mainController.getProgressPane().setVisible(true);
        mainController.getProgressbar().setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        mainController.getStatusLabelLeft().setText("Paste collection");
        if (clipboardMode == ClipboardMode.CUT) {
            mainController.getProgressbarLabel().setText("Moving files...");
        } else {
            mainController.getProgressbarLabel().setText("Copying files...");
        }
        mainController.getStatusLabelLeft().setVisible(true);
        Task<Boolean> taskPaste = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                try {
                    if (clipboardMode == ClipboardMode.CUT) {
                        //cut
                        final Path targetFinalPath = Paths.get(targetPath.toString(), sourceFilePath.getFileName().toString());
                        targetPath.toFile().mkdir();
                        copyMoveFolder(sourceFilePath, targetFinalPath, clipboardMode, StandardCopyOption.REPLACE_EXISTING);
                        Files.delete(sourceFilePath);
                    } else {
                        //copy
                        final Path targetFinalPath = Paths.get(targetPath.toString(), sourceFilePath.getFileName().toString());
                        targetPath.toFile().mkdir();
                        copyMoveFolder(sourceFilePath, targetFinalPath, clipboardMode, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ex) {
                    Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                }
                return true;
            }
        };
        taskPaste.setOnSucceeded((t) -> {
            refreshTree();
            treeView.getSelectionModel().clearSelection();
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setVisible(false);
            pasteMenu.setDisable(true);
        });
        taskPaste.setOnFailed((t) -> {
            treeView.getSelectionModel().clearSelection();
            util.showError(this.accordionPane, "Cannot cut/copy collection", t.getSource().getException());
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setVisible(false);
            pasteMenu.setDisable(true);
            Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, "Cannot cut/copy collection!", t.getSource().getException());
        });
        executor.submit(taskPaste);
    }

    public void copyMoveFolder(Path source, Path target, ClipboardMode mode, CopyOption... options)
            throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (mode == ClipboardMode.COPY) {
                    Platform.runLater(() -> {
                        mainController.getProgressbarLabel().setText("Copy " + file.toFile().getName());
                    });
                    Files.copy(file, target.resolve(source.relativize(file)), options);
                } else {
                    Platform.runLater(() -> {
                        mainController.getProgressbarLabel().setText("Move " + file.toFile().getName());
                    });
                    Files.move(file, target.resolve(source.relativize(file)), options);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public LinkedHashMap<String, String> getCollectionStorage() {
        return collectionStorage;
    }

    public LinkedHashMap<String, String> getCollectionStorageSearchIndex() {
        return collectionStorageSearchIndex;
    }

    private void ShowEmptyHelp() {
        Alert alert = new Alert(AlertType.CONFIRMATION, "No Collection are defined.\nDo you want to add the storage of you mediafiles now ?", ButtonType.NO, ButtonType.YES);
        alert.setHeaderText("Add collections");
        alert.setTitle("Collection alert");
        FontIcon ft = new FontIcon("ti-agenda:50");
        alert.setGraphic(ft);
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(iconImage);
        Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) accordionPane.getScene().getWindow());
        alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        Optional<ButtonType> resultDiag = alert.showAndWait();
        if (resultDiag.get() == ButtonType.YES) {
            addExistingPath();
        }
    }

    public void highlightCollection(Path p) {
        //System.out.println("path was " + p);
        AtomicBoolean found = new AtomicBoolean(false);
        Path parent = p.getParent();
        ObservableList<TitledPane> allPanes = accordionPane.getPanes();
        AtomicBoolean finishSearch = new AtomicBoolean(false);
        for (TitledPane titlePane : allPanes) {
            titlePane.setExpanded(true);
            TreeView<PathItem> treeView = (TreeView<PathItem>) titlePane.getContent();
            ObservableList<TreeItem<PathItem>> children = treeView.getRoot().getChildren();
            for (TreeItem<PathItem> treeItem : children) {
                selectPath(treeItem, parent.toString(), treeView, finishSearch);
                if (finishSearch.get()) {
                    break;
                }
            }
            if (finishSearch.get()) {
                break;
            }
        }
    }

    private void selectPath(TreeItem<PathItem> treeViewChild, String parent, TreeView<PathItem> treeView, AtomicBoolean finishSearch) {
        if (parent.equalsIgnoreCase(treeViewChild.getValue().getFilePath().toString())) {
            treeView.getSelectionModel().select(treeViewChild);
            finishSearch.set(true);
            return;
        } else {
            if (parent.contains(treeViewChild.getValue().getFilePath().toString())) {
                treeViewChild.setExpanded(true);
                PauseTransition pause = new PauseTransition(Duration.millis(500));
                pause.setOnFinished((t) -> {
                    ObservableList<TreeItem<PathItem>> children = treeViewChild.getChildren();
                    for (TreeItem<PathItem> treeItem : children) {
                        if (parent.contains(treeItem.getValue().getFilePath().toString())) {
                            selectPath(treeItem, parent, treeView, finishSearch);
                        }
                    }
                });
                pause.play();
            }
        }
    }

    public SearchIndex getSearchIndexProcess() {
        return searchIndexProcess;
    }

    @FXML
    private void renameEventAction(ActionEvent event) {
        /*Alert alert = new Alert(AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO);
        alert = Utility.setDefaultButton(alert, ButtonType.YES);
        alert.setHeaderText("Do you want to include \n'" + p + "'\n to the search index (if no fulltextsearch is not available for that collection)?");
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        alert.setResizable(false);
        Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) accordionPane.getScene().getWindow());
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(iconImage);
        alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() == ButtonType.YES) {
            return true;
        } else {
            return false;
        }*/
    }

}
