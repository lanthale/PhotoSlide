/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browser;

import org.photoslide.MainViewController;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.Utility;
import org.photoslide.lighttable.LighttableController;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
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
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.MenuButton;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

/**
 *
 * @author selfemp
 */
public class CollectionsController implements Initializable {

    private ExecutorService executor;
    private Utility util;
    private static final String NODE_NAME = "PhotoSlide";
    private Path selectedPath;
    private LinkedHashMap<String, String> collectionStorage;
    private int activeAccordionPane;

    private MainViewController mainController;
    @FXML
    private Button minusButton;
    @FXML
    private Button plusButton;
    @FXML
    private MenuButton menuButton;
    @FXML
    private Accordion accordionPane;

    private LighttableController lighttablePaneController;
    private Preferences pref;
    private static TreeItem placeholder;
    private ProgressIndicator waitPrg;
    @FXML
    private Button refreshButton;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryPS("collectionsController"));
        util = new Utility();
        pref = Preferences.userRoot().node(NODE_NAME);
        collectionStorage = new LinkedHashMap<>();
        placeholder = new TreeItem("Please wait...");
        waitPrg = new ProgressIndicator();
        waitPrg.setPrefSize(15, 15);
        placeholder.setGraphic(waitPrg);
        accordionPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                loadURLs();
            }
        });
    }

    private void loadURLs() {
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                String[] keys = pref.keys();
                pref.getInt("activeAccordionPane", 0);
                for (String key : keys) {
                    if (key.contains("URL")) {
                        collectionStorage.put(key, pref.get(key, null));
                    }
                }
                collectionStorage.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach((t) -> {
                    loadDirectoryTree(t.getValue());
                });
                return true;
            }
        };
        task.setOnSucceeded((WorkerStateEvent t) -> {
            Platform.runLater(() -> {
                if (accordionPane.getPanes().size() > 0) {
                    accordionPane.setExpandedPane(accordionPane.getPanes().get(activeAccordionPane));
                }
            });
        });
        executor.submit(task);
    }

    public void saveActiveCollectionsPane() {
        pref.putInt("activeAccordionPane", activeAccordionPane);
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
                //try {
                Platform.runLater(() -> {
                    double prgValue = ((double) (i.addAndGet(1)) / qty * 100);
                    //System.out.println(String.format("%1$,.2f", prgValue));
                    //mainController.getProgressbar().setProgress(prgValue);
                    mainController.getProgressbarLabel().setText(t.toString() + " " + String.format("%1$,.0f", prgValue) + "%");
                });
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                }

                Task<Boolean> taskTree = new Task<>() {
                    @Override
                    protected Boolean call() throws Exception {
                        createTree(t, parent);
                        return null;
                    }
                };
                taskTree.setOnSucceeded((k) -> {
                    mainController.getProgressPane().setVisible(false);
                    mainController.getStatusLabelLeft().setVisible(false);
                });
                taskTree.setOnFailed((t2) -> {
                    Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, t2.getSource().getException());
                    util.showError("Cannot create directory tree", t2.getSource().getException());
                });
                executor.submit(taskTree);
                //} catch (IOException ex) {
                //    Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                //}                
            });

        }
    }

    private void createTree(Path root_file, TreeItem parent) throws IOException {
        if (Files.isDirectory(root_file)) {
            TreeItem<PathItem> node = new TreeItem(new PathItem(root_file));
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
                                    node.getChildren().remove(placeholder); // Remove placeholder
                                }
                                node.removeEventHandler(TreeItem.branchExpandedEvent(), this); // Remove event                                                                
                            });
                            taskTree.setOnFailed((WorkerStateEvent t) -> {
                                mainController.getStatusLabelLeft().setText(t.getSource().getMessage());
                                util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 10);
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
        executor.shutdownNow();
    }

    @FXML
    private void plusButtonAction(ActionEvent event) {
        addExistingPath();
    }

    public void addExistingPath() {
        Stage stage = (Stage) accordionPane.getScene().getWindow();
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory != null) {
            loadDirectoryTree(selectedDirectory.getAbsolutePath());
            pref.put("URL" + getPrefKeyForSaving(), selectedDirectory.getAbsolutePath());
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
            Integer i = Collections.max(numbers);
            return "" + (i + 1);
        } catch (BackingStoreException ex) {
            Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private void loadDirectoryTree(String selectedRootPath) {
        String path = selectedRootPath;
        TreeItem<PathItem> root = new TreeItem<>(new PathItem(Paths.get(path)));
        TreeView<PathItem> dirTreeView = new TreeView<>();
        dirTreeView.setShowRoot(false);
        dirTreeView.setDisable(true);
        TitledPane firstTitlePane = new TitledPane(path, dirTreeView);
        firstTitlePane.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        firstTitlePane.setAnimated(true);
        firstTitlePane.setTextAlignment(TextAlignment.LEFT);
        Platform.runLater(() -> {
            accordionPane.getPanes().add(firstTitlePane);
        });
        Task<TreeItem<PathItem>> task = new Task<TreeItem<PathItem>>() {
            @Override
            protected TreeItem<PathItem> call() throws Exception {
                Platform.runLater(() -> {
                    dirTreeView.setRoot(root);
                    mainController.getProgressPane().setVisible(true);
                    mainController.getStatusLabelLeft().setVisible(true);
                    mainController.getStatusLabelLeft().setText("Scanning...");
                    mainController.getProgressbar().setProgress(-1);
                });
                Thread.sleep(100);
                createRootTree(Paths.get(path), root);
                return root;
            }
        };
        task.setOnSucceeded((WorkerStateEvent t) -> {
            root.setExpanded(true);
            dirTreeView.setDisable(false);
            if (root.getChildren().isEmpty()) {
                dirTreeView.setShowRoot(true);
            }
            dirTreeView.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends TreeItem<PathItem>> ov, TreeItem<PathItem> t1, TreeItem<PathItem> t2) -> {
                TreeItem<PathItem> selectedItem = (TreeItem<PathItem>) t2;
                if (selectedItem != null) {
                    selectedPath = selectedItem.getValue().getFilePath();
                    lighttablePaneController.setSelectedPath(selectedItem.getValue().getFilePath());
                }
            });
            mainController.getStatusLabelLeft().setVisible(false);
            mainController.getProgressPane().setVisible(false);
        });
        task.setOnFailed((WorkerStateEvent t) -> {
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setText(t.getSource().getMessage());
            util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 10);
            Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, t.getSource().getException());
            util.showError("Cannot create directory tree", t.getSource().getException());
        });
        executor.submit(task);
    }

    public Path getSelectedPath() {
        return selectedPath;
    }

    @FXML
    private void refreshMenuAction(ActionEvent event) {
        try {
            TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
            ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
            String selectedItemName = selectedItems.get(0).getValue().toString();
            TreeItem<PathItem> parent = selectedItems.get(0).getParent();
            Path filePath = selectedItems.get(0).getValue().getFilePath();
            parent.getChildren().remove(selectedItems.get(0));
            createTree(filePath, parent);
            SortedList<TreeItem<PathItem>> sorted = parent.getChildren().sorted();
            parent.getChildren().setAll(sorted);
            Optional<TreeItem<PathItem>> findFirst = sorted.stream().filter(obj -> obj.getValue().toString().equalsIgnoreCase(selectedItemName)).findFirst();
            treeView.getSelectionModel().select(findFirst.get());
        } catch (IOException ex) {
            Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
            util.showError("Cannot create directory tree", ex);
        }
    }

    @FXML
    private void minusButtonAction(ActionEvent event) {
        TreeView<PathItem> content = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
        PathItem value = content.getRoot().getValue();

        collectionStorage.entrySet().stream().filter(c -> c.getValue().equalsIgnoreCase(value.getFilePath().toString())).forEach((t) -> {
            pref.remove(t.getKey());
        });
        lighttablePaneController.resetLightTableView();
        accordionPane.getPanes().remove(accordionPane.getExpandedPane());
    }

    @FXML
    private void createCollectionAction(ActionEvent event) {
        TextInputDialog alert = new TextInputDialog();
        alert.setTitle("Create event");
        alert.setHeaderText("Create event (directory)");
        alert.setContentText("Please enter the name:");
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.getStylesheets().add(
                getClass().getResource("/org/photoslide/fxml/Dialogs.css").toExternalForm());
        alert.setResizable(false);
        Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) accordionPane.getScene().getWindow());
        Optional<String> result = alert.showAndWait();
        result.ifPresent((t) -> {
            TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
            ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
            TreeItem<PathItem> parent = selectedItems.get(0).getParent();
            Path filePath = selectedItems.get(0).getValue().getFilePath();
            String newPath = filePath.toString() + File.separator + t;
            //Paths.get(newPath).toFile().mkdir();                 
            TreeItem<PathItem> newChild = new TreeItem<>(new PathItem(Paths.get(newPath)));
            parent.getChildren().add(newChild);
            System.out.println("newPath " + newPath);
        });
    }

    @FXML
    private void moveCollectionAction(ActionEvent event) {
    }

    @FXML
    private void copyCollectionAction(ActionEvent event) {
    }

    @FXML
    private void deleteCollectionAction(ActionEvent event) {
    }

}
