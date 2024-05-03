/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;

import fr.dudie.nominatim.model.Address;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.photoslide.App;
import org.photoslide.MainViewController;
import org.photoslide.ThreadFactoryBuilder;
import org.photoslide.browsercollections.PathItem;
import org.photoslide.browsermetadata.GeoCoding;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.browsermetadata.MetadataController;

/**
 *
 * @author selfemp
 */
public class SearchIndex {

    private final MetadataController metadataController;
    private final MainViewController mainViewController;
    private final ExecutorService executorParallel;
    private Task<Void> task;
    private Task<Void> taskCheck;
    private boolean terminateFileWalk;
    private boolean fileWalkRunning;

    public SearchIndex(MetadataController metc, MainViewController c) {
        this.metadataController = metc;
        mainViewController = c;
        fileWalkRunning = false;
        terminateFileWalk = false;
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setPriority(1).setNamePrefix("searchIndexExecutor").build());
    }

    public void createCheckSearchIndex(String searchPath) {
        String collectionName = searchPath;
        Logger.getLogger(SearchIndex.class.getName()).log(Level.FINE, "Start time create searchDB: " + LocalDateTime.now());
        PathItem pathItem = new PathItem(Paths.get(searchPath));
        task = new Task<>() {
            @Override
            protected Void call() throws Exception {                
                if (task.isCancelled()) {
                    return null;
                }
                if (App.getSEARCHINDEXFINISHED().isBefore(LocalDate.now())) {
                    Period period = Period.between(App.getSEARCHINDEXFINISHED(), LocalDate.now());
                    if (period.getDays() > 14) {
                        Logger.getLogger(SearchIndex.class.getName()).log(Level.INFO, "Index update not required because it is up to date.");
                        return null;
                    }
                }
                updateTitle("Creating/Updating search index...");
                try {
                    Files.walkFileTree(pathItem.getFilePath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(final Path fileItem, final BasicFileAttributes attrs) throws IOException {
                            int pathlength = fileItem.toString().length();
                            if (pathlength > 35) {
                                updateMessage("..." + fileItem.toString().substring(pathlength - 35, pathlength));
                            } else {
                                updateMessage("" + fileItem.toString());
                            }
                            if (terminateFileWalk == true) {
                                return FileVisitResult.TERMINATE;
                            }
                            if (task.isCancelled() == true) {
                                return FileVisitResult.TERMINATE;
                            }
                            //filter invalid directories
                            if (fileItem.getFileName().toString().startsWith(".")) {
                                return FileVisitResult.CONTINUE;
                            }
                            if (fileItem.getFileName().toString().startsWith("@")) {
                                return FileVisitResult.CONTINUE;
                            }
                            if (fileItem.toString().contains("@")) {
                                return FileVisitResult.CONTINUE;
                            }
                            if (FileTypes.isValidType(fileItem.toString())) {
                                MediaFile m = new MediaFile();
                                m.setName(fileItem.getFileName().toString());
                                m.setPathStorage(fileItem);
                                if (checkIfIndexed(m) == false) {
                                    m.readEdits();
                                    m.getCreationTime();
                                    if (!m.getGpsPosition().equalsIgnoreCase("")) {
                                        if (m.placeProperty().getValue() == null) {
                                            GeoCoding geoCoding = new GeoCoding();
                                            Address geoSearchForGPS = geoCoding.geoSearchForGPS(m.getGpsLonPosAsDouble(), m.getGpsLatPosAsDouble());
                                            m.placeProperty().setValue(geoSearchForGPS.getDisplayName());
                                        }
                                    }
                                    if (FileTypes.isValidVideo(fileItem.toString())) {
                                        m.setMediaType(MediaFile.MediaTypes.VIDEO);
                                    } else if (FileTypes.isValidImage(fileItem.toString())) {
                                        m.setMediaType(MediaFile.MediaTypes.IMAGE);
                                    } else {
                                        m.setMediaType(MediaFile.MediaTypes.NONE);
                                    }
                                    try {
                                        metadataController.readBasicMetadata(task, m);
                                    } catch (Exception ex) {
                                        Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, "Cannot read " + m.getName() + " - " + m.getPathStorage(), ex);
                                    }
                                    if (m.getMediaType() != MediaFile.MediaTypes.NONE) {
                                        insertMediaFileIntoSearchDB(collectionName, m);
                                    }
                                }
                            }
                            return super.visitFile(fileItem, attrs);
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException e)
                                throws IOException {
                            boolean finishedSearch = Files.isSameFile(dir, pathItem.getFilePath());
                            if (finishedSearch) {
                                System.out.println("Finished indexing files");
                                App.setSEARCHINDEXFINISHED(LocalDate.now());
                                checkSearchIndex(pathItem.getFilePath().toString());
                                return FileVisitResult.TERMINATE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException ex) {
                    //Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
                }
                return null;
            }
        };
        task.setOnSucceeded((t) -> {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.FINE, "End time create searchDB: " + LocalDateTime.now());
        });
        task.setOnFailed((t) -> {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, "Error creating searchIndexDB", t.getSource().getException());
        });
        executorParallel.submit(task);
        mainViewController.getTaskProgressView().getTasks().add(task);
    }

    public void checkSearchIndex(String searchPath) {
        PathItem pathItem = new PathItem(Paths.get(searchPath));
        taskCheck = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (taskCheck.isCancelled()) {
                    return null;
                }
                try {
                    updateTitle("Checking search index...");
                    Files.walkFileTree(pathItem.getFilePath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(final Path fileItem, final BasicFileAttributes attrs) throws IOException {
                            int pathlength = fileItem.toString().length();
                            if (pathlength > 35) {
                                updateMessage("..." + fileItem.toString().substring(pathlength - 35, pathlength));
                            } else {
                                updateMessage("" + fileItem.toString());
                            }
                            if (terminateFileWalk == true) {
                                return FileVisitResult.TERMINATE;
                            }
                            if (taskCheck.isCancelled() == true) {
                                return FileVisitResult.TERMINATE;
                            }
                            if (fileItem.toFile().exists() == false) {
                                if (FileTypes.isValidType(fileItem.toString())) {
                                    //remove from index
                                    removeMediaFileInSearchDB(fileItem.toString());
                                }
                            }
                            return super.visitFile(fileItem, attrs);
                        }

                        @Override
                        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException e)
                                throws IOException {
                            boolean finishedSearch = Files.isSameFile(dir, pathItem.getFilePath());
                            if (finishedSearch) {
                                System.out.println("Checking indexing files finished");
                                //App.setSearchIndexFinished(true);
                                checkSearchIndex(pathItem.getFilePath().toString());
                                return FileVisitResult.TERMINATE;
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException ex) {
                    //Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
                }
                return null;
            }
        };
        executorParallel.submit(taskCheck);
        mainViewController.getTaskProgressView().getTasks().add(taskCheck);
    }

    public void shutdown() {
        terminateFileWalk = true;
        if (task != null) {
            task.cancel();
        }
        if (taskCheck != null) {
            taskCheck.cancel();
        }
        executorParallel.shutdown();
    }

    public void insertMediaFileIntoSearchDB(String collectionName, MediaFile m) throws IOException {
        if (App.getSearchDBConnection() == null) {
            return;
        }
        try {
            String collName = "'" + collectionName + "'";
            String name = "'" + m.getName() + "'";
            String path = "'" + m.getPathStorage().toString() + "'";
            String title = null;
            if (m.titleProperty().get() != null) {
                title = "'" + m.titleProperty().get() + "'";
            }
            String keyw = null;
            if (m.getKeywords() != null) {
                keyw = "'" + m.getKeywords() + "'";
            }
            String cam = null;
            if (m.cameraProperty().get() != null) {
                cam = "'" + m.cameraProperty().get() + "'";
            }
            String creationTime = null;
            if (m.getCreationTime() != null) {
                creationTime = "ts '" + m.getCreationTime() + "'";
            }
            String recordTime = null;
            if (m.getRecordTime() != null) {
                recordTime = "ts '" + m.getRecordTime().format(DateTimeFormatter.ISO_DATE) + "'";
            }
            int rating = m.getRatingProperty().getValue();
            String places = null;
            if (m.placeProperty().get() != null) {
                places = "'" + m.placeProperty().get() + "'";
            }
            String gpspos = null;
            if (!m.getGpsPosition().equalsIgnoreCase("")) {
                gpspos = "'" + m.getGpsLatPosAsDouble() + ";" + m.getGpsLonPosAsDouble() + "'";
            }
            String faces = null;
            if (m.facesProperty().get() != null) {
                faces = "'" + m.facesProperty().get() + "'";
            }
            String metadata = "'" + metadataController.getMetaDataAsString().replace("'", "''") + "'";
            Statement stm = App.getSearchDBConnection().createStatement();
            String statementStr = "INSERT INTO MediaFiles (COLLECTIONNAME, NAME, PATHSTORAGE, TITLE, KEYWORDS, CAMERA, RATING, RECORDTIME, CREATIONTIME, PLACES, GPSPOSITION, FACES, METADATA) VALUES(" + collName + "," + name + "," + path + "," + title + "," + keyw + "," + cam + "," + rating + "," + recordTime + "," + creationTime + "," + places + "," + gpspos + "," + faces + "," + metadata + ")";
            stm.execute(statementStr);
        } catch (SQLException ex) {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void updateMediaFileIntoSearchDB(String collectionName, MediaFile m) throws IOException {
        if (App.getSearchDBConnection() == null) {
            return;
        }
        if (checkIfIndexed(m) == false) {
            insertMediaFileIntoSearchDB(collectionName, m);
        } else {
            try {
                StringBuilder strb = new StringBuilder();
                strb.append("UPDATE MediaFiles SET ");
                if (m.titleProperty().get() != null) {
                    strb.append("TITLE='").append(m.titleProperty().get()).append("',");
                }
                if (m.getKeywords() != null) {
                    strb.append("KEYWORDS='").append(m.getKeywords()).append("',");
                }
                if (m.cameraProperty().get() != null) {
                    strb.append("CAMERA='").append(m.cameraProperty().get()).append("',");
                }
                //SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");                
                if (m.getCreationTime() != null) {
                    strb.append("CREATIONTIME=ts '").append(m.getCreationTime()).append("',");
                }
                if (m.getRecordTime() != null) {
                    strb.append("RECORDTIME=ts '").append(m.getRecordTime().format(DateTimeFormatter.ISO_DATE)).append("',");
                }
                if (m.getRatingProperty().getValue() > 0) {
                    strb.append("RATING=").append(m.getRatingProperty().getValue()).append(",");
                }
                if (m.placeProperty().getValue() != null) {
                    strb.append("PLACES=").append(m.placeProperty().getValue()).append(",");
                }
                String stm = strb.toString();
                if (stm.lastIndexOf(",") == stm.length() - 1) {
                    stm = stm.substring(0, stm.length() - 1);
                }
                stm = stm + " WHERE NAME=" + "'" + m.getName() + "'" + " AND PATHSTORAGE=" + "'" + m.getPathStorage().toString() + "'";
                Statement indexStatment = App.getSearchDBConnection().createStatement();
                indexStatment.execute(stm);
            } catch (SQLException ex) {
                Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void removeMediaFileInSearchDB(String name) {
        if (App.getSearchDBConnection() == null) {
            return;
        }
        try {
            Statement indexStatment = App.getSearchDBConnection().createStatement();
            String stm = "DELETE FROM MediaFiles where NAME='" + name + "'";
            indexStatment.execute(stm);
        } catch (SQLException ex) {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void removeCollectionFromSearchDB(String colname) {
        if (App.getSearchDBConnection() == null) {
            return;
        }
        try {
            Statement indexStatment = App.getSearchDBConnection().createStatement();
            String stm = "DELETE FROM MediaFiles where COLLECTIONNAME='" + colname + "'";
            indexStatment.execute(stm);
        } catch (SQLException ex) {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public boolean checkIfIndexed(MediaFile m) {
        if (App.getSearchDBConnection() == null) {
            return false;
        }
        boolean ret = false;
        try {
            Statement indexStatment = App.getSearchDBConnection().createStatement();
            ResultSet rs = indexStatment.executeQuery("SELECT name, places from MediaFiles where NAME='" + m.getName() + "' and PATHSTORAGE='" + m.getPathStorage() + "'");
            if (rs.next()) {
                String place = rs.getString("name");
                ret = true;
            }
        } catch (SQLException ex) {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ret;
    }
}
