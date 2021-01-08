/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.search;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.concurrent.Task;
import org.photoslide.App;
import org.photoslide.ThreadFactoryPS;
import org.photoslide.browsercollections.PathItem;
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.browsermetadata.MetadataController;

/**
 *
 * @author selfemp
 */
public class SearchIndex {

    private final MetadataController metadataController;
    private final ExecutorService executorParallel;
    private Task<Void> task;
    private Task<Void> taskCheck;
    private boolean terminateFileWalk;
    private boolean fileWalkRunning;

    public SearchIndex(MetadataController metc) {
        this.metadataController = metc;
        fileWalkRunning = false;
        terminateFileWalk = false;
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryPS("searchIndexExecutor"));
    }

    public void createSearchIndex(String searchPath) {
        String collectionName = searchPath.toString();
        Logger.getLogger(SearchIndex.class.getName()).log(Level.INFO, "Start time create searchDB: " + LocalDateTime.now());
        PathItem pathItem = new PathItem(Paths.get(searchPath));
        task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (task.isCancelled()) {
                    return null;
                }
                updateTitle("createSearchIndex");
                try {
                    Files.walkFileTree(pathItem.getFilePath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(final Path fileItem, final BasicFileAttributes attrs) throws IOException {
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
                            if (FileTypes.isValidType(fileItem.toString())) {
                                MediaFile m = new MediaFile();
                                m.setName(fileItem.getFileName().toString());
                                m.setPathStorage(fileItem);
                                if (checkIfIndexed(m) == false) {
                                    m.readEdits();
                                    m.getCreationTime();
                                    if (FileTypes.isValidVideo(fileItem.toString())) {
                                        m.setMediaType(MediaFile.MediaTypes.VIDEO);
                                        insertMediaFileIntoSearchDB(collectionName, m);
                                    } else if (FileTypes.isValidImge(fileItem.toString())) {
                                        m.setMediaType(MediaFile.MediaTypes.IMAGE);
                                        metadataController.setActualMediaFile(m);
                                        try {
                                            metadataController.readBasicMetadata(task);
                                        } catch (IllegalArgumentException | IOException ex) {
                                            //Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
                                        }
                                        insertMediaFileIntoSearchDB(collectionName, m);
                                    } else {
                                        m.setMediaType(MediaFile.MediaTypes.NONE);
                                    }
                                }
                            }
                            return super.visitFile(fileItem, attrs);
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException e)
                                throws IOException {
                            if (e == null) {
                                //System.out.println("postVisistDir "+dir.toString());
                                return FileVisitResult.CONTINUE;
                            } else {
                                // directory iteration failed
                                throw e;
                            }
                        }
                    });
                } catch (IOException ex) {
                    //Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
                }
                return null;
            }
        };
        task.setOnSucceeded((t) -> {
            //App.setSearchIndexFinished(true);
            Logger.getLogger(SearchIndex.class.getName()).log(Level.INFO, "End time create searchDB: " + LocalDateTime.now());
        });
        task.setOnFailed((t) -> {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.INFO, "Error creating searchIndexDB", t.getSource().getException());
        });
        executorParallel.submit(task);

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
                    Files.walkFileTree(pathItem.getFilePath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(final Path fileItem, final BasicFileAttributes attrs) throws IOException {
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
                    });
                } catch (IOException ex) {
                    //Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
                }
                return null;
            }
        };
        executorParallel.submit(taskCheck);
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

    public void insertMediaFileIntoSearchDB(String collectionName, MediaFile m) {
        if (App.getSearchDBConnection() == null) {
            return;
        }
        try {
            String collName = "'" + collectionName + "'";
            String name = "'" + m.getName() + "'";
            String path = "'" + m.getPathStorage().toString() + "'";
            String title = null;
            if (m.getTitleProperty().get() != null) {
                title = "'" + m.getTitleProperty().get() + "'";
            }
            String keyw = null;
            if (m.getKeywords() != null) {
                keyw = "'" + m.getKeywords() + "'";
            }
            String cam = null;
            if (m.getCameraProperty().get() != null) {
                cam = "'" + m.getCameraProperty().get() + "'";
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
            if (m.getPlaces().get() != null) {
                places = "'" + m.getPlaces().get() + "'";
            }
            String faces = null;
            if (m.getFaces().get() != null) {
                faces = "'" + m.getFaces().get() + "'";
            }
            String metadata = "'" + metadataController.getMetaDataAsString().replace("'", "''") + "'";
            Statement stm = App.getSearchDBConnection().createStatement();
            String statementStr = "INSERT INTO MediaFiles (COLLECTIONNAME, NAME, PATHSTORAGE, TITLE, KEYWORDS, CAMERA, RATING, RECORDTIME, CREATIONTIME, PLACES, FACES, METADATA) VALUES(" + collName + "," + name + "," + path + "," + title + "," + keyw + "," + cam + "," + rating + "," + recordTime + "," + creationTime + "," + places + "," + faces + "," + metadata + ")";
            stm.execute(statementStr);
        } catch (SQLException ex) {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void updateMediaFileIntoSearchDB(String collectionName, MediaFile m) {
        if (App.getSearchDBConnection() == null) {
            return;
        }
        if (checkIfIndexed(m) == false) {
            insertMediaFileIntoSearchDB(collectionName, m);
        } else {
            try {
                StringBuilder strb = new StringBuilder();
                strb.append("UPDATE MediaFiles SET ");
                if (m.getTitleProperty().get() != null) {
                    strb.append("TITLE='").append(m.getTitleProperty().get()).append("',");
                }
                if (m.getKeywords() != null) {
                    strb.append("KEYWORDS='").append(m.getKeywords()).append("',");
                }
                if (m.getCameraProperty().get() != null) {
                    strb.append("CAMERA='").append(m.getCameraProperty().get()).append("',");
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
                String place = rs.getString("places");
                if (place == null) {
                    //removeMediaFileInSearchDB(m.getName());
                    ret = true;
                } else {
                    ret = true;
                }
            }
        } catch (SQLException ex) {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ret;
    }
}
