/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browsercollections;


import org.photoslide.I18n;
import org.photoslide.search.SearchIndex;
import org.photoslide.faces.FaceService;
import org.photoslide.MainViewController;
import org.photoslide.Utility;
import org.photoslide.datamodel.XmpSidecar;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
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
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.javafx.StackedFontIcon;
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.browsercollections.library.Libraries;
import org.photoslide.browsercollections.library.LibraryNode;
import org.photoslide.browsercollections.library.LibraryProvider;
import org.photoslide.browsercollections.library.SystemPicturesFolders;

/**
 *
 * @author selfemp
 */
public class CollectionsController implements Initializable {

    @FXML
    private MenuItem pasteMenu;
    @FXML
    private MenuItem deleteMenu;
    @FXML
    private MenuItem searchIndexMenu;
    /** key of the pane property with the folder of the collection */
    private static final String COLLECTION_PATH = "collectionPath";
    @FXML
    private MenuItem rescanFacesMenu;
    private Image iconImage;
    private SearchIndex searchIndexProcess;
    private DirectoryWatcher directorywatchSelected;
    private DirectoryWatcher directoryRootwatch;

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
        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNamePrefix("collectionsController").setDaemon(true).build());
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNamePrefix("collectionsControllerParallel").setDaemon(true).build());
        executorParallelTimers = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNamePrefix("collectionsControllerParallelScheduled").setDaemon(true).build());
        util = new Utility();
        pref = Preferences.userRoot().node(NODE_NAME);
        menuButton.setOnShowing(e -> updateSearchIndexMenu());
        collectionStorage = new LinkedHashMap<>();
        collectionStorageSearchIndex = new LinkedHashMap<>();
        directorywatchSelected = new DirectoryWatcher(this);
        directoryRootwatch = new DirectoryWatcher(this);

        iconImage = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
        accordionPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                searchIndexProcess = new SearchIndex(mainController.getMetadataPaneController(), mainController);
                searchIndexProcess.setIndexChangedListener(this::updateFileCount);
                loadURLs();
            }
        });
        accordionPane.setOnDragOver((to) -> {
            to.acceptTransferModes(TransferMode.COPY);
            to.consume();
        });
        accordionPane.setOnDragDropped((t) -> {
            setupDropTarget(t);
        });
    }

    private void loadURLs() {
        Task<Boolean> task = new Task<>() {

            @Override
            protected Boolean call() throws Exception {
                if (collectionStorage.isEmpty()) {
                    Platform.runLater(() -> {
                        ShowEmptyHelp();
                    });
                } else {
                    collectionStorage.entrySet().stream().parallel().sorted(Map.Entry.comparingByKey()).parallel().forEachOrdered((dTree) -> {
                        if (this.isCancelled() == false) {
                            //Directorywatch is installed in LightTabelController for selected path only
                            loadDirectoryTree(dTree.getValue());
                            if (Libraries.isLibrary(Path.of(dTree.getValue()))) {
                                return;
                            }
                            try {
                                directoryRootwatch.watch(Path.of(dTree.getValue()), false);
                            } catch (IOException ex) {
                                Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                    });
                }
                return true;
            }
        };
        task.setOnScheduled((t) -> {
            mainController.getTaskProgressView().getTasks().add(task);
        });
        task.setOnSucceeded((WorkerStateEvent t) -> {
            if (!accordionPane.getPanes().isEmpty()) {
                if (activeAccordionPane == -1) {
                    activeAccordionPane = 0;
                }
                accordionPane.setExpandedPane(accordionPane.getPanes().get(activeAccordionPane));
            }
            collectionStorageSearchIndex.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach((entry) -> {
                searchIndexProcess.createCheckSearchIndex(entry.getValue());                
            });
        });
        executorParallel.submit(task);
        /*executorParallelTimers.schedule(() -> {            
        }, 5, TimeUnit.SECONDS);*/
        //mainController.getTaskProgressView().getTasks().add(indexTask);        
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
        try (DirectoryStream<Path> newDirectoryStream = Files.newDirectoryStream(root_file, (entry) -> {
            boolean res = true;
            if (entry.getFileName().toString().startsWith(".")) {
                res = false;
            }
            if (entry.getFileName().toString().startsWith("@")) {
                res = false;
            }
            if (entry.getFileName().toString().startsWith("Ω")) {
                res = false;
            }
            int idx = entry.getFileName().toString().lastIndexOf(".");
            if (idx != -1) {
                String substring = entry.getFileName().toString().substring(idx);
                if (substring.length() == 4) {
                    res = false;
                }
            }
            return res;
        })) {
            Stream<Path> sortedStream = StreamSupport.stream(newDirectoryStream.spliterator(), false).sorted();
            final AtomicInteger i = new AtomicInteger(0);
            final long qty = Files.list(root_file).count();
            sortedStream.parallel().forEachOrdered((t) -> {
                Platform.runLater(() -> {
                    double prgValue = ((double) (i.addAndGet(1)) / qty * 100);
                    mainController.getProgressbarLabel().setText(t.toString() + " " + String.format("%1$,.0f", prgValue) + "%");
                });

                try {
                    createTree(t, parent);
                } catch (IOException ex) {
                    Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                    util.showError(this.accordionPane, I18n.t("coll.cannotCreateTree"), ex);
                }

            });

        }
    }

    private void createTree(Path root_file, TreeItem parent) throws IOException {
        if (Files.isDirectory(root_file)) {
            ProgressIndicator waitPrgMain = new ProgressIndicator();
            waitPrgMain.setPrefSize(15, 15);
            TreeItem<PathItem> node = new TreeItem(new PathItem(root_file));
            TreeItem placeholder = new TreeItem(new PathItem(Paths.get(I18n.t("coll.pleaseWait"))));

            Platform.runLater(() -> {
                parent.getChildren().add(node);
                if (node.getChildren().isEmpty()) {
                    ProgressIndicator waitPrg = new ProgressIndicator();
                    waitPrg.setPrefSize(15, 15);
                    placeholder.setGraphic(waitPrg);
                    if (node.getChildren().contains(placeholder) == false) {
                        node.getChildren().add(placeholder);
                    }
                }
            });

            EventHandler eventH = new EventHandler() {
                @Override
                public void handle(Event event) {

                    Task<Boolean> taskTree = new Task<>() {
                        @Override
                        protected Boolean call() throws Exception {
                            /*long count = Files.find(t, 1, (path, attributes) -> attributes.isDirectory()).count();
                             */

                            try (DirectoryStream<Path> newDirectoryStream = Files.newDirectoryStream(root_file, (entry) -> {
                                boolean res = true;
                                if (entry.getFileName().toString().startsWith(".")) {
                                    res = false;
                                }
                                if (entry.getFileName().toString().startsWith("@")) {
                                    res = false;
                                }
                                if (entry.getFileName().toString().startsWith("Ω")) {
                                    res = false;
                                }
                                int idx = entry.getFileName().toString().lastIndexOf(".");
                                if (idx != -1) {
                                    String substring = entry.getFileName().toString().substring(idx);
                                    if (substring.length() == 4) {
                                        res = false;
                                    }
                                }
                                return res;
                            })) {
                                Stream<Path> sortedStream = StreamSupport.stream(newDirectoryStream.spliterator(), false).sorted();
                                sortedStream.parallel().forEachOrdered((t) -> {
                                    try {
                                        createTree(t, node);
                                    } catch (IOException ex) {
                                        Platform.runLater(() -> {
                                            node.setGraphic(null);
                                        });
                                    }
                                });
                            }

                            return null;
                        }
                    };
                    taskTree.setOnScheduled((k) -> {
                        if (node.getChildren().contains(placeholder)) {
                            node.getChildren().remove(placeholder); // Remove placeholder
                        }
                    });
                    taskTree.setOnRunning((t) -> {
                        node.setGraphic(waitPrgMain);
                    });
                    taskTree.setOnSucceeded((WorkerStateEvent t) -> {
                        node.setGraphic(null);
                        if (!node.getChildren().isEmpty()) {
                            node.removeEventHandler(TreeItem.branchExpandedEvent(), this); // Remove event 
                        }
                    });
                    taskTree.setOnFailed((WorkerStateEvent t) -> {
                        node.setGraphic(null);
                        mainController.getStatusLabelLeft().setText(t.getSource().getMessage());
                        util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 10, true);
                    });
                    executor.submit(taskTree);
                    mainController.getTaskProgressView().getTasks().add(taskTree);
                }
            };
            node.addEventHandler(TreeItem.branchExpandedEvent(), eventH);

        } else {
            //parent.getChildren().add(new TreeItem(root_file.getFileName()));
        }
    }

    /**
     * Fills the tree of a photo library with its top level nodes (background
     * thread; the items are added on the FX thread).
     */
    private void createLibraryTree(LibraryProvider provider, TreeItem<PathItem> parent) throws IOException {
        List<LibraryNode> nodes = provider.topLevel();
        for (LibraryNode node : nodes) {
            addLibraryNode(provider, node, parent);
        }
    }

    /** Adds the node; its sub nodes are read when it is expanded the first time. */
    private void addLibraryNode(LibraryProvider provider, LibraryNode node, TreeItem<PathItem> parent) {
        TreeItem<PathItem> item = new TreeItem<>(new PathItem(Libraries.virtualPath(provider.root(), node.id()), node));
        Platform.runLater(() -> {
            item.setGraphic(new FontIcon(switch (node.kind()) {
                case FOLDER -> "ti-folder";
                case ALBUM -> "ti-gallery";
                case EVENT -> "ti-calendar";
            }));
            parent.getChildren().add(item);
        });
        if (!node.hasChildren()) {
            return;
        }
        TreeItem<PathItem> placeholder = new TreeItem<>(new PathItem(Paths.get(I18n.t("coll.pleaseWait"))));
        Platform.runLater(() -> item.getChildren().add(placeholder));
        EventHandler<TreeItem.TreeModificationEvent<PathItem>> onExpand = new EventHandler<>() {
            @Override
            public void handle(TreeItem.TreeModificationEvent<PathItem> event) {
                if (event.getTreeItem() != item) {
                    return;
                }
                item.removeEventHandler(TreeItem.branchExpandedEvent(), this);
                Task<List<LibraryNode>> load = new Task<>() {
                    @Override
                    protected List<LibraryNode> call() throws Exception {
                        return provider.children(node.id());
                    }
                };
                load.setOnSucceeded(t -> {
                    item.getChildren().remove(placeholder);
                    for (LibraryNode child : load.getValue()) {
                        addLibraryNode(provider, child, item);
                    }
                });
                load.setOnFailed(t -> {
                    item.getChildren().remove(placeholder);
                    // try again when expanded the next time
                    item.addEventHandler(TreeItem.branchExpandedEvent(), this);
                    mainController.getStatusLabelLeft().setVisible(true);
                    mainController.getStatusLabelLeft().setText(load.getException().getMessage());
                    util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 10, true);
                });
                executor.submit(load);
            }
        };
        item.addEventHandler(TreeItem.branchExpandedEvent(), onExpand);
    }

    /** Reads the library again (it is copied again if Photos changed it). */
    private void refreshLibraryTree(TreeView<PathItem> treeView) {
        TreeItem<PathItem> root = treeView.getRoot();
        LibraryProvider provider = Libraries.provider(root.getValue().getFilePath());
        root.getChildren().clear();
        Thread.ofVirtual().start(() -> {
            try {
                createLibraryTree(provider, root);
            } catch (IOException ex) {
                Platform.runLater(() -> showCollectionAlert(AlertType.WARNING, ex.getMessage()));
            }
        });
    }

    public void Shutdown() {
        directorywatchSelected.stopWatch();
        directoryRootwatch.stopWatch();
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

    /** The kinds of collection offered when adding one. */
    private enum AddType {
        FOLDER("coll.typeFolder"),
        APPLE_PHOTOS("coll.typeApplePhotos"),
        SYSTEM_PICTURES("coll.typeSystemPictures");

        private final String key;

        AddType(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return I18n.t(key);
        }
    }

    /**
     * Asks for the kind of the new collection (folder, macOS Photos library,
     * the picture folders of the system) and adds it.
     */
    public void addExistingPath() {
        List<AddType> types = new ArrayList<>(List.of(AddType.FOLDER));
        if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            types.add(AddType.APPLE_PHOTOS);
        }
        types.add(AddType.SYSTEM_PICTURES);
        javafx.scene.control.ChoiceDialog<AddType> dialog = new javafx.scene.control.ChoiceDialog<>(AddType.FOLDER, types);
        dialog.setTitle(I18n.t("coll.addCollections"));
        dialog.setHeaderText(I18n.t("coll.chooseType"));
        dialog.setContentText(I18n.t("coll.type"));
        dialog.setGraphic(new FontIcon("ti-agenda:50"));
        dialog.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
        dialogStage.getIcons().add(iconImage);
        Utility.centerChildWindowOnStage(dialogStage, (Stage) accordionPane.getScene().getWindow());
        dialog.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        Optional<AddType> type = dialog.showAndWait();
        if (type.isEmpty()) {
            return;
        }
        switch (type.get()) {
            case FOLDER -> {
                DirectoryChooser directoryChooser = new DirectoryChooser();
                directoryChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
                File selectedDirectory = directoryChooser.showDialog(accordionPane.getScene().getWindow());
                if (selectedDirectory != null) {
                    addCollection(selectedDirectory.getAbsolutePath(), true);
                }
            }
            case APPLE_PHOTOS -> addApplePhotosLibrary();
            case SYSTEM_PICTURES -> addSystemPictureFolders();
        }
    }

    /** Adds the macOS Photos library: the default one, or one chosen by the user. */
    private void addApplePhotosLibrary() {
        Path library = Path.of(System.getProperty("user.home"), "Pictures", "Photos Library.photoslibrary");
        if (!Files.exists(library)) {
            // the chooser of macOS shows a library as a folder
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(I18n.t("coll.chooseApplePhotos"));
            chooser.setInitialDirectory(new File(System.getProperty("user.home"), "Pictures"));
            File chosen = chooser.showDialog(accordionPane.getScene().getWindow());
            if (chosen == null) {
                return;
            }
            library = chosen.toPath();
            if (!Libraries.isLibrary(library)) {
                showCollectionAlert(AlertType.ERROR, I18n.t("coll.noApplePhotos", library.toString()));
                return;
            }
        }
        if (getPrefKeyForRemoving(library.toString(), "URL") != null) {
            showCollectionAlert(AlertType.INFORMATION, I18n.t("coll.alreadyAdded", library.toString()));
            return;
        }
        // no search index: it would read the whole library folder (previews, caches)
        addCollection(library.toString(), false);
    }

    /** Adds the picture folders of the system (Windows library, XDG, ~/Pictures) as folder collections. */
    private void addSystemPictureFolders() {
        List<Path> folders = SystemPicturesFolders.list();
        List<String> added = new ArrayList<>();
        for (Path folder : folders) {
            String path = folder.toAbsolutePath().toString();
            boolean overlaps = collectionRoots().stream().anyMatch(r -> {
                Path root = Path.of(r);
                return folder.startsWith(root) || root.startsWith(folder);
            });
            if (!overlaps) {
                addCollection(path, false);
                added.add(path);
            }
        }
        if (added.isEmpty()) {
            showCollectionAlert(AlertType.INFORMATION, I18n.t("coll.noSystemPictures"));
        } else if (createSearchIndex(String.join("\n", added))) {
            for (String path : added) {
                String key = getPrefKeyForRemoving(path, "URL");
                if (key != null) {
                    pref.put("INDEX" + key.substring("URL".length()), path);
                    searchIndexProcess.createCheckSearchIndex(path);
                }
            }
        }
    }

    /** Shows the collection and stores it; asks for the search index if wanted. */
    private void addCollection(String path, boolean askSearchIndex) {
        loadDirectoryTree(path);
        String prefKeyForSaving = getPrefKeyForSaving();
        pref.put("URL" + prefKeyForSaving, path);
        collectionStorage.put("URL" + prefKeyForSaving, path);
        if (askSearchIndex && createSearchIndex(path)) {
            pref.put("INDEX" + prefKeyForSaving, path);
            searchIndexProcess.createCheckSearchIndex(path);
        }
    }

    /**
     * True if the selected item of the expanded collection is in a photo
     * library: such a collection is read only. Shows a message then.
     */
    private boolean refuseReadOnly() {
        TitledPane expanded = accordionPane.getExpandedPane();
        if (expanded == null || !Libraries.isLibrary(Path.of(collectionPath(expanded)))) {
            return false;
        }
        showCollectionAlert(AlertType.INFORMATION, I18n.t("coll.libraryReadOnly"));
        return true;
    }

    private boolean createSearchIndex(String p) {
        Alert alert = new Alert(AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO);
        alert = Utility.setDefaultButton(alert, ButtonType.YES);
        alert.setHeaderText(I18n.t("coll.includeInSearchIndex", p));
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
        actCollectionTitlePane.getProperties().put(COLLECTION_PATH, path);
        actCollectionTitlePane.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
        actCollectionTitlePane.setAnimated(true);
        actCollectionTitlePane.setTextAlignment(TextAlignment.LEFT);
        
        Platform.runLater(() -> {
            accordionPane.getPanes().add(actCollectionTitlePane);
        });
        Task<TreeView<PathItem>> task = new Task<TreeView<PathItem>>() {
            @Override
            protected TreeView<PathItem> call() throws Exception {
                final boolean library = Libraries.isLibrary(Paths.get(path));
                TreeItem<PathItem> root = new TreeItem<>(new PathItem(Paths.get(path)));
                TreeView<PathItem> dirTreeView = new TreeView<>();
                // the folders of a library cannot be renamed
                dirTreeView.setEditable(!library);
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
                });
                if (library) {
                    createLibraryTree(Libraries.provider(Paths.get(path)), root);
                } else {
                    createRootTree(Paths.get(path), root);
                }
                return dirTreeView;
            }
        };
        task.setOnSucceeded((WorkerStateEvent t) -> {
            FontIcon greenIcon = new FontIcon("ti-agenda");
            greenIcon.setId("CollectorGreen");
            actCollectionTitlePane.setGraphic(greenIcon);
            updateFileCount(actCollectionTitlePane);
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
                    if (selectedItem.getValue().getLibraryNode() != null) {
                        // a library is read through its database, not watched
                        return;
                    }
                    final Path watchParent = selectedItem.getValue().getFilePath().getParent();
                    // registering may touch a network share: not on the FX thread
                    Thread.ofVirtual().start(() -> {
                        try {
                            directorywatchSelected.watchOnly(watchParent);
                        } catch (IOException ex) {
                            Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
                }
            });
        });
        task.setOnFailed((WorkerStateEvent t) -> {
            FontIcon redIcon = new FontIcon("ti-agenda");
            redIcon.setId("CollectorRed");
            actCollectionTitlePane.setGraphic(redIcon);
            updateFileCount(actCollectionTitlePane);
            mainController.getStatusLabelLeft().setText(t.getSource().getMessage());
            util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 10, true);
            Tooltip tip = new Tooltip(t.getSource().getException().getMessage());
            if (Libraries.isLibrary(Paths.get(path))) {
                // e.g. the missing Full Disk Access: tell it, not only in the tooltip
                showCollectionAlert(AlertType.WARNING, t.getSource().getException().getMessage());
            }
            actCollectionTitlePane.setTooltip(tip);
        });
        executorParallel.submit(task);
        Platform.runLater(() -> {
            mainController.getTaskProgressView().getTasks().add(task);
        });
    }

    public Path getSelectedPath() {
        return selectedPath;
    }

    @FXML
    private void refreshMenuAction(ActionEvent event) {
        refreshTree();
    }

    /**
     * Reads the selected folder (or the collection root) again. The folders
     * are listed in a background thread: on a network share that takes a
     * while. {@link #createTree} and {@link #createRootTree} add the tree
     * items on the FX thread themselves.
     */
    public void refreshTree() {
        if (accordionPane.getExpandedPane() == null
                || !(accordionPane.getExpandedPane().getContent() instanceof TreeView<?>)) {
            return;
        }
        TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
        ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty() || selectedItems.get(0) == null) {
            return;
        }
        final TreeItem<PathItem> selected = selectedItems.get(0);
        final String selectedItemName = selected.getValue().toString();
        final boolean root = selected.getParent() == null;
        final TreeItem<PathItem> parent = root ? selected : selected.getParent();
        final Path filePath = selected.getValue().getFilePath();
        if (Libraries.isLibrary(Path.of(collectionPath(accordionPane.getExpandedPane())))) {
            refreshLibraryTree(treeView);
            return;
        }
        if (root) {
            parent.getChildren().clear();
        } else {
            parent.getChildren().remove(selected);
        }
        Thread.ofVirtual().start(() -> {
            try {
                if (root) {
                    createRootTree(filePath, parent);
                } else {
                    createTree(filePath, parent);
                }
            } catch (IOException ex) {
                Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                Platform.runLater(() -> util.showError(this.accordionPane, I18n.t("coll.cannotCreateTree"), ex));
                return;
            }
            // after the items queued by createTree
            Platform.runLater(() -> {
                FXCollections.sort(parent.getChildren(), Comparator.comparing(t -> t.getValue().getFilePath()));
                if (!root) {
                    parent.getChildren().stream()
                            .filter(obj -> obj.getValue().toString().equalsIgnoreCase(selectedItemName))
                            .findFirst()
                            .ifPresent(item -> treeView.getSelectionModel().select(item));
                }
            });
        });
    }

    /** Called by the directory watcher, on the FX thread. */
    public void refreshTreeParent(String path, String eventKind) {
        if (accordionPane.getExpandedPane() == null
                || !(accordionPane.getExpandedPane().getContent() instanceof TreeView<?> tv)
                || tv.getSelectionModel().getSelectedItems().isEmpty()
                || tv.getSelectionModel().getSelectedItems().get(0) == null
                || tv.getSelectionModel().getSelectedItems().get(0).getParent() == null) {
            return;
        }
        if (eventKind.equalsIgnoreCase("ENTRY_CREATE")) {
            TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
            TreeItem<PathItem> treeItemParent = treeView.getSelectionModel().getSelectedItems().get(0).getParent();
            if (treeView.getRoot().getValue().getFilePath().compareTo(treeItemParent.getValue().getFilePath()) == 0) {
                treeItemParent = treeView.getRoot();
            }
            TreeItem<PathItem> node = new TreeItem(new PathItem(Path.of(path)));
            FilteredList<TreeItem<PathItem>> filtered = treeItemParent.getChildren().filtered((t) -> {
                return t.getValue().getFilePath().compareTo(Path.of(path)) == 0;
            });
            if (filtered.isEmpty() == true) {
                treeItemParent.getChildren().add(node);
                SortedList<TreeItem<PathItem>> sorted = treeItemParent.getChildren().sorted();
                treeItemParent.getChildren().setAll(sorted);
                treeView.refresh();
            }
        }
        if (eventKind.equalsIgnoreCase("ENTRY_DELETE")) {
            TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
            TreeItem<PathItem> treeItemParent = treeView.getSelectionModel().getSelectedItems().get(0).getParent();
            FilteredList<TreeItem<PathItem>> filtered = treeItemParent.getChildren().filtered((t) -> {
                return t.getValue().getFilePath().equals(Path.of(path));
            });
            if (filtered.isEmpty() == false) {
                TreeItem<PathItem> removeItem = filtered.get(0);
                treeItemParent.getChildren().remove(removeItem);
            }

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
            alert.setHeaderText(I18n.t("coll.expandToDelete"));

            alert.getDialogPane().getStylesheets().add(
                    getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(iconImage);
            Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) accordionPane.getScene().getWindow());
            alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
            alert.show();
        }
    }

    /**
     * Adds the expanded collection to the search index, or removes it. A
     * collection added without index (the question when it was added) can
     * so be searched later. The names, titles etc. come from the files and
     * their XMP sidecars, nothing is lost by removing.
     */
    @FXML
    private void toggleSearchIndexAction(ActionEvent event) {
        TitledPane expandedPane = accordionPane.getExpandedPane();
        if (expandedPane == null) {
            showCollectionAlert(AlertType.ERROR, I18n.t("coll.expandForSearchIndex"));
            return;
        }
        if (refuseReadOnly()) {
            return;
        }
        String root = ((TreeView<PathItem>) expandedPane.getContent()).getRoot().getValue().getFilePath().toString();
        String urlKey = getPrefKeyForRemoving(root, "URL");
        if (urlKey == null) {
            return;
        }
        String indexKey = getPrefKeyForRemoving(root, "INDEX");
        if (indexKey == null) {
            if (createSearchIndex(root)) {
                // same number as the collection: "URL3" -> "INDEX3"
                String key = "INDEX" + urlKey.substring("URL".length());
                pref.put(key, root);
                collectionStorageSearchIndex.put(key, root);
                searchIndexProcess.createCheckSearchIndex(root);
            }
        } else {
            Alert alert = new Alert(AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO);
            alert = Utility.setDefaultButton(alert, ButtonType.NO);
            alert.setHeaderText(I18n.t("coll.removeFromSearchIndexQuestion", root));
            styleCollectionAlert(alert);
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.YES) {
                pref.remove(indexKey);
                collectionStorageSearchIndex.remove(indexKey);
                searchIndexProcess.removeCollectionFromSearchDB(root);
            }
        }
    }

    /**
     * Searches all photos of the expanded collection for faces again (only
     * for collections in the search index; the faces in the XMP stay).
     */
    @FXML
    private void rescanFacesAction(ActionEvent event) {
        TitledPane expandedPane = accordionPane.getExpandedPane();
        if (expandedPane == null) {
            showCollectionAlert(AlertType.ERROR, I18n.t("coll.expandForRescanFaces"));
            return;
        }
        if (refuseReadOnly()) {
            return;
        }
        String root = ((TreeView<PathItem>) expandedPane.getContent()).getRoot().getValue().getFilePath().toString();
        if (getPrefKeyForRemoving(root, "INDEX") == null) {
            showCollectionAlert(AlertType.INFORMATION, I18n.t("coll.rescanFacesNeedsIndex"));
            return;
        }
        searchIndexProcess.rescanFaces(root);
    }

    /** The text of the search index entry follows the expanded collection. */
    private void updateSearchIndexMenu() {
        TitledPane expandedPane = accordionPane.getExpandedPane();
        boolean indexed = false;
        if (expandedPane != null && expandedPane.getContent() instanceof TreeView<?> tree && tree.getRoot() != null) {
            String root = ((PathItem) tree.getRoot().getValue()).getFilePath().toString();
            indexed = getPrefKeyForRemoving(root, "INDEX") != null;
        }
        searchIndexMenu.setText(I18n.t(indexed ? "browserCollections.removeFromSearchIndex"
                : "browserCollections.addToSearchIndex"));
        rescanFacesMenu.setDisable(!FaceService.get().modelsAvailable());
    }

    /**
     * The files of the expanded collection were moved to another folder
     * (drive, SMB share): the collection, its search index and the bookmarks
     * point to the new folder. Nothing is copied or indexed again.
     */
    @FXML
    private void relocateCollectionAction(ActionEvent event) {
        TitledPane expandedPane = accordionPane.getExpandedPane();
        if (expandedPane == null) {
            showCollectionAlert(AlertType.ERROR, I18n.t("coll.expandToRelocate"));
            return;
        }
        if (refuseReadOnly()) {
            return;
        }
        String oldRoot = ((TreeView<PathItem>) expandedPane.getContent()).getRoot().getValue().getFilePath().toString();
        String urlKey = getPrefKeyForRemoving(oldRoot, "URL");
        if (urlKey == null) {
            return;
        }
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(I18n.t("coll.newLocationOf", oldRoot));
        File initial = Path.of(oldRoot).getParent() == null ? null : Path.of(oldRoot).getParent().toFile();
        if (initial == null || initial.isDirectory() == false) {
            initial = new File(System.getProperty("user.home"));
        }
        directoryChooser.setInitialDirectory(initial);
        File selectedDirectory = directoryChooser.showDialog(accordionPane.getScene().getWindow());
        if (selectedDirectory == null) {
            return;
        }
        String newRoot = selectedDirectory.getAbsolutePath();
        Path newPath = Path.of(newRoot);
        if (newPath.equals(Path.of(oldRoot))) {
            return;
        }
        for (String root : collectionRoots()) {
            Path rootPath = Path.of(root);
            if (!rootPath.equals(Path.of(oldRoot)) && (newPath.startsWith(rootPath) || rootPath.startsWith(newPath))) {
                showCollectionAlert(AlertType.ERROR, I18n.t("coll.alreadyPartOf", newRoot, root));
                return;
            }
        }
        String indexKey = getPrefKeyForRemoving(oldRoot, "INDEX");
        String baseQuestion = I18n.t("coll.relocateQuestion", oldRoot, newRoot);
        if (indexKey == null) {
            confirmRelocation(expandedPane, oldRoot, newRoot, urlKey, indexKey, baseQuestion);
            return;
        }
        // the check queries the search database and tests files on the new
        // location (often a network share): not on the FX thread
        Thread.ofVirtual().start(() -> {
            String question = baseQuestion;
            int[] check = searchIndexProcess.checkRelocation(oldRoot, newRoot, 20);
            if (check[1] > 0) {
                question += I18n.t("coll.relocateFound", check[0], check[1]);
                if (check[0] == 0) {
                    question += I18n.t("coll.relocateNotMoved");
                }
            }
            final String q = question;
            Platform.runLater(() -> confirmRelocation(expandedPane, oldRoot, newRoot, urlKey, indexKey, q));
        });
    }

    private void confirmRelocation(TitledPane expandedPane, String oldRoot, String newRoot, String urlKey, String indexKey, String question) {
        Path newPath = Path.of(newRoot);
        Alert alert = new Alert(AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO);
        alert = Utility.setDefaultButton(alert, ButtonType.NO);
        alert.setHeaderText(question);
        styleCollectionAlert(alert);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) {
            return;
        }
        pref.put(urlKey, newRoot);
        collectionStorage.put(urlKey, newRoot);
        if (indexKey != null) {
            pref.put(indexKey, newRoot);
            collectionStorageSearchIndex.put(indexKey, newRoot);
            searchIndexProcess.relocateCollection(oldRoot, newRoot);
        }
        mainController.relocateBookmarks(oldRoot, newRoot);
        if (selectedPath != null && selectedPath.startsWith(Path.of(oldRoot))) {
            selectedPath = null;
            lighttablePaneController.resetLightTableView();
        }
        accordionPane.getPanes().remove(expandedPane);
        loadDirectoryTree(newRoot);
        Thread.ofVirtual().start(() -> {
            try {
                directoryRootwatch.watch(newPath, false);
            } catch (IOException ex) {
                Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
    }

    private List<String> collectionRoots() {
        List<String> roots = new ArrayList<>();
        try {
            for (String key : pref.keys()) {
                if (key.startsWith("URL")) {
                    roots.add(pref.get(key, ""));
                }
            }
        } catch (BackingStoreException ex) {
            Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
        }
        roots.removeIf(String::isEmpty);
        return roots;
    }

    private void showCollectionAlert(AlertType type, String text) {
        Alert alert = new Alert(type);
        alert.setHeaderText(text);
        styleCollectionAlert(alert);
        alert.show();
    }

    private void styleCollectionAlert(Alert alert) {
        alert.getDialogPane().getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        alert.setResizable(false);
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(iconImage);
        Utility.centerChildWindowOnStage(stage, (Stage) accordionPane.getScene().getWindow());
        alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
    }

    /** The folder of the collection shown by the pane (its title may also show the number of files). */
    private static String collectionPath(TitledPane pane) {
        Object path = pane.getProperties().get(COLLECTION_PATH);
        return path instanceof String p ? p : pane.getText();
    }

    /** Updates the number of files at the collection (FX thread; see SearchIndex). */
    private void updateFileCount(String collectionName) {
        for (TitledPane pane : accordionPane.getPanes()) {
            String path = collectionPath(pane);
            if (!path.isEmpty() && Path.of(path).equals(Path.of(collectionName))) {
                updateFileCount(pane);
            }
        }
    }

    /**
     * Shows the number of files of the search index after the path of the
     * collection, e.g. "/Volumes/photo-1 (5.300)": only if the collection is
     * available (green icon) and in the search index.
     */
    private void updateFileCount(TitledPane pane) {
        String path = collectionPath(pane);
        if (!isAvailable(pane) || searchIndexProcess == null || getPrefKeyForRemoving(path, "INDEX") == null) {
            pane.setText(path);
            return;
        }
        searchIndexProcess.fileCount(path).thenAccept(count -> Platform.runLater(() -> {
            if (isAvailable(pane) && getPrefKeyForRemoving(path, "INDEX") != null && count > 0) {
                pane.setText(path + " (" + java.text.NumberFormat.getIntegerInstance().format(count) + ")");
            } else {
                pane.setText(path);
            }
        }));
    }

    private static boolean isAvailable(TitledPane pane) {
        return pane.getGraphic() != null && "CollectorGreen".equals(pane.getGraphic().getId());
    }

    private String getPrefKeyForRemoving(String path, String prefix) {
        try {
            String[] keys = pref.keys();
            for (String key : keys) {
                String value = pref.get(key, "");
                // equal, not contained: "/Volumes/photo-1" contains "/Volumes/photo"
                if (key.startsWith(prefix) && !value.isEmpty() && Path.of(value).equals(Path.of(path))) {
                    return key;
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
            alert.setTitle(I18n.t("common.error"));
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
        if (refuseReadOnly() || checkIfElementInTreeSelected(I18n.t("coll.selectToCreate"))) {
            return;
        }
        TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
        ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
        TreeItem<PathItem> parent = selectedItems.get(0);
        TextInputDialog alert = new TextInputDialog();
        alert.setTitle(I18n.t("coll.createEvent"));
        alert.setHeaderText(I18n.t("coll.createEventHeader"));
        StackedFontIcon stackIcon = new StackedFontIcon();
        FontIcon fileIcon = new FontIcon("ti-file");
        fileIcon.setIconSize(50);
        FontIcon plusIcon = new FontIcon("ti-plus");
        plusIcon.setIconSize(20);
        stackIcon.getChildren().add(fileIcon);
        stackIcon.getChildren().add(plusIcon);
        alert.setGraphic(stackIcon);
        alert.setContentText(I18n.t("coll.enterName"));
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
        if (refuseReadOnly() || checkIfElementInTreeSelected(I18n.t("coll.selectToCut"))) {
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
        mainController.getStatusLabelLeft().setText(I18n.t("coll.cutCollection", clipboardPath.getFileName().toString()));
        util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 3, true);
    }

    @FXML
    private void copyEventAction(ActionEvent event) {
        if (refuseReadOnly() || checkIfElementInTreeSelected(I18n.t("coll.selectToCopy"))) {
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
        mainController.getStatusLabelLeft().setText(I18n.t("coll.copyCollection", clipboardPath.getFileName().toString()));
        util.hideNodeAfterTime(mainController.getStatusLabelLeft(), 3, true);
    }

    @FXML
    private void deleteEventAction(ActionEvent event) {
        if (refuseReadOnly() || checkIfElementInTreeSelected(I18n.t("coll.selectToDelete"))) {
            return;
        }
        TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
        ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
        TreeItem<PathItem> item = selectedItems.get(0);
        clipboardPath = item.getValue().getFilePath();
        System.out.println("clipboardPath " + clipboardPath);

        Alert alert = new Alert(AlertType.CONFIRMATION, I18n.t("coll.deleteEvent"), ButtonType.CANCEL, ButtonType.OK);
        alert.setGraphic(new FontIcon("ti-trash:40"));
//alert.setHeaderText("Delete '" + clipboardPath + "' ?");
        //alert.setContentText("Delete '" + clipboardPath + "' ?");   
        alert.setHeaderText(I18n.t("coll.deleteEvent"));
        Text text = new Text(I18n.t("coll.deleteQuestion", clipboardPath));
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
            mainController.getStatusLabelLeft().setText(I18n.t("coll.deleteCollection"));
            mainController.getProgressbarLabel().setText("...");
            Task<Boolean> taskDelete = new Task<>() {
                @Override
                protected Boolean call() throws IOException {
                    Files.walk(clipboardPath)
                            .sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach((t) -> {
                                Platform.runLater(() -> {
                                    mainController.getProgressbarLabel().setText(I18n.t("coll.deleteItem", t.getName()));
                                });
                                t.delete();
                            });
                    return true;
                }
            };
            taskDelete.setOnSucceeded((t) -> {
                TreeItem<PathItem> itemToRemove = treeView.getSelectionModel().getSelectedItem();
                treeView.getSelectionModel().clearSelection();
                TreeItem<PathItem> parent = itemToRemove.getParent();
                parent.getChildren().remove(itemToRemove);
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressbarLabel().textProperty().unbind();
                mainController.getProgressPane().setVisible(false);
                mainController.getStatusLabelLeft().setVisible(false);
                deleteMenu.setDisable(false);
            });
            taskDelete.setOnFailed((t) -> {
                treeView.getSelectionModel().clearSelection();
                util.showError(this.accordionPane, I18n.t("coll.cannotDelete"), t.getSource().getException());
                mainController.getProgressbar().progressProperty().unbind();
                mainController.getProgressbarLabel().textProperty().unbind();
                mainController.getProgressPane().setVisible(false);
                mainController.getStatusLabelLeft().setVisible(false);
                deleteMenu.setDisable(true);
                Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, "Cannot cut/copy collection!", t.getSource().getException());
            });
            executor.submit(taskDelete);
            mainController.getTaskProgressView().getTasks().add(taskDelete);
        }
    }

    @FXML
    private void pasteEventAction(ActionEvent event) {
        if (refuseReadOnly()) {
            return;
        }
        Path sourceFilePath = clipboardPath;

        TreeView<PathItem> treeView = (TreeView<PathItem>) accordionPane.getExpandedPane().getContent();
        ObservableList<TreeItem<PathItem>> selectedItems = treeView.getSelectionModel().getSelectedItems();
        TreeItem<PathItem> item = selectedItems.get(0);
        Path targetPath = item.getValue().getFilePath();
        mainController.getProgressPane().setVisible(true);
        mainController.getProgressbar().setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        mainController.getStatusLabelLeft().setText(I18n.t("coll.pasteCollection"));
        if (clipboardMode == ClipboardMode.CUT) {
            mainController.getProgressbarLabel().setText(I18n.t("coll.movingFiles"));
        } else {
            mainController.getProgressbarLabel().setText(I18n.t("coll.copyingFiles"));
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
                        // all files are moved now: remove the source folder with its (empty) subfolders
                        try (Stream<Path> remaining = Files.walk(sourceFilePath)) {
                            for (Path dir : remaining.sorted(Comparator.reverseOrder()).toList()) {
                                Files.delete(dir);
                            }
                        }
                    } else {
                        //copy
                        final Path targetFinalPath = Paths.get(targetPath.toString(), sourceFilePath.getFileName().toString());
                        targetPath.toFile().mkdir();
                        copyMoveFolder(sourceFilePath, targetFinalPath, clipboardMode, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException ex) {
                    Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                    throw ex;
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
            util.showError(this.accordionPane, I18n.t("coll.cannotCutCopy"), t.getSource().getException());
            mainController.getProgressbar().progressProperty().unbind();
            mainController.getProgressbarLabel().textProperty().unbind();
            mainController.getProgressPane().setVisible(false);
            mainController.getStatusLabelLeft().setVisible(false);
            pasteMenu.setDisable(true);
            Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, "Cannot cut/copy collection!", t.getSource().getException());
        });
        executor.submit(taskPaste);
        mainController.getTaskProgressView().getTasks().add(taskPaste);
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
                        mainController.getProgressbarLabel().setText(I18n.t("coll.copyItem", file.toFile().getName()));
                    });
                    Files.copy(file, target.resolve(source.relativize(file)), options);
                } else {
                    Platform.runLater(() -> {
                        mainController.getProgressbarLabel().setText(I18n.t("coll.moveItem", file.toFile().getName()));
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
        Alert alert = new Alert(AlertType.CONFIRMATION, I18n.t("coll.noCollections"), ButtonType.NO, ButtonType.YES);
        alert.setHeaderText(I18n.t("coll.addCollections"));
        alert.setTitle(I18n.t("coll.collectionAlert"));
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

    private void setupDropTarget(DragEvent t) {
        Dragboard db = t.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            // only the files are taken from the dragboard; the dialog runs after
            // the drop gesture is completed (a nested event loop inside a drop
            // handler blocks the drag source, e.g. the Finder)
            final List<File> dropped = new ArrayList<>(db.getFiles());
            Platform.runLater(() -> importDroppedFiles(dropped));
        }
        t.setDropCompleted(true);
        t.consume();
    }

    /** Adds a new event folder below the root of the collection tree and selects it. */
    private void addEventToTree(TreeView<PathItem> tree, Path eventFolder) {
        TreeItem<PathItem> root = tree.getRoot();
        boolean exists = root.getChildren().stream()
                .anyMatch(item -> item.getValue().getFilePath().equals(eventFolder));
        Thread.ofVirtual().start(() -> {
            if (!exists) {
                try {
                    // adds the item on the FX thread
                    createTree(eventFolder, root);
                } catch (IOException ex) {
                    Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                    return;
                }
            }
            // after the item queued by createTree
            Platform.runLater(() -> {
                FXCollections.sort(root.getChildren(), Comparator.comparing(t -> t.getValue().getFilePath()));
                tree.setShowRoot(false);
                root.getChildren().stream()
                        .filter(item -> item.getValue().getFilePath().equals(eventFolder))
                        .findFirst()
                        .ifPresent(item -> {
                            tree.getSelectionModel().clearSelection();
                            tree.getSelectionModel().select(item);
                            tree.scrollTo(tree.getRow(item));
                        });
            });
        });
    }

    private void importDroppedFiles(List<File> dropped) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, I18n.t("coll.importTarget"), ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(I18n.t("coll.importTargetTitle"));
        alert.setHeaderText(I18n.t("coll.importHeader"));
        FontIcon ft = new FontIcon("ti-import:50");
        alert.setGraphic(ft);
        DialogPane dialogPane = alert.getDialogPane();
        alert.setResizable(true);
        GridPane content = new GridPane();
        content.setAlignment(Pos.CENTER_RIGHT);
        content.setHgap(5);
        content.setVgap(5);
        Label cbLabel = new Label(I18n.t("coll.selectCollection"));
        ComboBox<String> cb = new ComboBox<>();
        cb.setPrefWidth(200);
        accordionPane.getPanes().forEach((pan) -> {
            if (!Libraries.isLibrary(Path.of(collectionPath(pan)))) {
                cb.getItems().add(collectionPath(pan));
            }
        });
        content.addRow(0, cbLabel, cb);
        Label cbRootLabel = new Label(I18n.t("coll.selectEvent"));
        ComboBox<String> cbRootFolder = new ComboBox<>();
        cbRootFolder.setPrefWidth(200);
        content.addRow(1, cbRootLabel, cbRootFolder);
        // the root of the collection is a target too: the folder becomes a new event
        final String[] collectionRoot = new String[1];
        cbRootFolder.setConverter(new javafx.util.StringConverter<String>() {
            @Override
            public String toString(String path) {
                return path != null && path.equals(collectionRoot[0]) ? I18n.t("coll.collectionRootTarget") : path;
            }

            @Override
            public String fromString(String string) {
                return string;
            }
        });
        cb.getSelectionModel().selectedItemProperty().addListener((o) -> {
            cbRootFolder.getItems().clear();
            FilteredList<TitledPane> filtered = accordionPane.getPanes().filtered((panet) -> collectionPath(panet).equalsIgnoreCase(cb.getSelectionModel().getSelectedItem()));
            TreeView<PathItem> tree = (TreeView<PathItem>) filtered.get(0).getContent();
            collectionRoot[0] = tree.getRoot().getValue().getFilePath().toString();
            cbRootFolder.getItems().add(collectionRoot[0]);
            tree.getRoot().getChildren().forEach((treeitem) -> {
                cbRootFolder.getItems().add(treeitem.getValue().getFilePath().toString());
            });
        });
        cbRootFolder.getSelectionModel().selectedItemProperty().addListener((o) -> {
            dialogPane.lookupButton(ButtonType.OK).setDisable(false);
        });
        dialogPane.setContent(content);
        dialogPane.lookupButton(ButtonType.OK).setDisable(true);
        dialogPane.getStylesheets().add(
                getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
        Image dialogIcon = new Image(getClass().getResourceAsStream("/org/photoslide/img/Installericon.png"));
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
        stage.getIcons().add(dialogIcon);
        Utility.centerChildWindowOnStage((Stage) alert.getDialogPane().getScene().getWindow(), (Stage) accordionPane.getScene().getWindow());
        alert = Utility.setDefaultButton(alert, ButtonType.CANCEL);
        alert.getDialogPane().getScene().setFill(Paint.valueOf("rgb(80, 80, 80)"));
        alert.showAndWait();
        if (alert.getResult() == ButtonType.OK) {
            File dropFile = dropped.get(0);
            List<File> fileItem = new ArrayList<>(dropped);
            AtomicLong count = new AtomicLong(0);
            // the folder the files were copied into
            java.util.concurrent.atomic.AtomicReference<Path> created = new java.util.concurrent.atomic.AtomicReference<>();
            Task<Boolean> task = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    // counting a folder tree and looking for sidecars access the
                    // file system for every file: here, not on the FX thread
                    if (dropFile.isDirectory()) {
                        try {
                            count.set(Utility.fileCount(dropFile.toPath()));
                        } catch (IOException ex) {
                            Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else {
                        // copy the sidecars (XMP, former .edit) of the images with them
                        java.util.Set<File> known = new java.util.HashSet<>(fileItem);
                        for (File f : dropped) {
                            if (f.isFile()) {
                                for (Path sidecar : XmpSidecar.existingSidecars(f.toPath())) {
                                    if (known.add(sidecar.toFile())) {
                                        fileItem.add(sidecar.toFile());
                                    }
                                }
                            }
                        }
                        count.set(fileItem.size());
                    }
                    String targetStr = cbRootFolder.getSelectionModel().getSelectedItem() + File.separator + dropFile.toPath().getFileName().toString();
                    updateTitle(I18n.t("coll.copyMediafilesTo", Path.of(cbRootFolder.getSelectionModel().getSelectedItem()).getFileName().toString()));
                    AtomicInteger iatom = new AtomicInteger(1);
                    if (dropFile.isDirectory()) {
                        Path targetPath = Path.of(targetStr + "_" + LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE));
                        created.set(targetPath);
                        updateMessage(I18n.t("coll.copyToTarget"));
                        Files.createDirectory(targetPath);
                        String src = dropFile.getAbsolutePath();
                        String dest = targetPath.toString();
                        Files.walk(Paths.get(src)).forEach(a -> {
                            Path b = Paths.get(dest, a.toString().substring(src.length()));
                            try {
                                if (!a.toString().equals(src)) {
                                    Files.copy(a, b, true ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING} : new CopyOption[]{});
                                    updateMessage(iatom.get() + " / " + count.get());
                                    iatom.addAndGet(1);
                                }
                            } catch (IOException e) {
                            }
                        });
                    } else {
                        //Create Task
                        String targetDir = cbRootFolder.getSelectionModel().getSelectedItem() + File.separator + LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE);
                        Files.createDirectory(Path.of(targetDir));
                        created.set(Path.of(targetDir));
                        fileItem.forEach((t) -> {
                            try {
                                updateMessage(I18n.t("coll.copyItem", t.getName()));
                                Files.copy(t.toPath(), Path.of(targetDir + File.separator + t.getName()), StandardCopyOption.REPLACE_EXISTING);
                                updateMessage(iatom.get() + " / " + count.get());
                                iatom.addAndGet(1);
                            } catch (IOException ex) {
                                Logger.getLogger(CollectionsController.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        });
                    }
                    return true;
                }
            };
            task.setOnScheduled((tk) -> {
                mainController.getStatusLabelLeft().setVisible(true);
                mainController.getProgressPane().setVisible(true);
                mainController.getProgressbarLabel().setText(I18n.t("lighttable.filesFoundLoading", count));
                mainController.getStatusLabelRight().setVisible(true);
            });
            task.setOnFailed((fail) -> {
                Alert alertError = new Alert(AlertType.ERROR, I18n.t("coll.importError"), ButtonType.OK);
                alertError.getDialogPane().getStylesheets().add(
                        getClass().getResource("/org/photoslide/css/Dialogs.css").toExternalForm());
                alertError.setResizable(false);
                alertError.setGraphic(new FontIcon("ti-close:30"));
                Utility.centerChildWindowOnStage((Stage) alertError.getDialogPane().getScene().getWindow(), (Stage) accordionPane.getScene().getWindow());
                Stage stageError = (Stage) alertError.getDialogPane().getScene().getWindow();
                stageError.getIcons().add(dialogIcon);
                stageError.showAndWait();
            });
            task.setOnSucceeded((suc) -> {
                FilteredList<TitledPane> filtered = accordionPane.getPanes().filtered((panet) -> collectionPath(panet).equalsIgnoreCase(cb.getSelectionModel().getSelectedItem()));
                if (filtered.isEmpty() == false) {
                    accordionPane.setExpandedPane(filtered.get(0));
                    TreeView<PathItem> tree = (TreeView<PathItem>) filtered.get(0).getContent();
                    if (cbRootFolder.getSelectionModel().getSelectedItem().equals(collectionRoot[0]) && created.get() != null) {
                        // copied into the root: the new event is added to the tree and
                        // selected (the root is not shown, so it cannot be selected and refreshed)
                        addEventToTree(tree, created.get());
                        return;
                    }
                    FilteredList<TreeItem<PathItem>> filteredTreeItem = tree.getRoot().getChildren().filtered((treeIt) -> treeIt.getValue().getFilePath().compareTo(Path.of(cbRootFolder.getSelectionModel().getSelectedItem())) == 0);
                    if (filteredTreeItem.isEmpty() == false) {
                        tree.getSelectionModel().clearSelection();
                        tree.getSelectionModel().select(filteredTreeItem.get(0));
                        refreshTree();
                    }
                }
            });
            executorParallel.submit(task);
            mainController.getTaskProgressView().getTasks().add(task);
        }
    }

    @FXML
    private void renameEventAction(ActionEvent event) {
        /*Alert alert = new Alert(AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO);
        alert = Utility.setDefaultButton(alert, ButtonType.YES);
        alert.setHeaderText(I18n.t("coll.includeInSearchIndex", p));
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
