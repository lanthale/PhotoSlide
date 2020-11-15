/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browser;

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
import org.photoslide.datamodel.FileTypes;
import org.photoslide.datamodel.MediaFile;
import org.photoslide.metadata.MetadataController;

/**
 *
 * @author selfemp
 */
public class SearchIndex {

    private final MetadataController metadataController;
    private final ExecutorService executorParallel;

    public SearchIndex(MetadataController metc) {
        this.metadataController = metc;
        executorParallel = Executors.newCachedThreadPool(new ThreadFactoryPS("searchIndexExecutor"));
    }

    public void createSearchIndex(String searchPath) {
        Logger.getLogger(SearchIndex.class.getName()).log(Level.INFO, "Start time create searchDB: " + LocalDateTime.now());
        PathItem pathItem = new PathItem(Paths.get(searchPath));
        Task<Boolean> task = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                final Task runTask = this;
                try {
                    Files.walkFileTree(pathItem.getFilePath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(final Path fileItem, final BasicFileAttributes attrs) throws IOException {
                            if (runTask.isCancelled()) {
                                return FileVisitResult.TERMINATE;
                            }
                            if (FileTypes.isValidType(fileItem.toString())) {
                                MediaFile m = new MediaFile();
                                m.setName(fileItem.toString());
                                m.setPathStorage(fileItem);                                
                                if (checkIfIndexed(m) == false) {
                                    m.readEdits();
                                    m.getCreationTime();
                                    if (FileTypes.isValidVideo(fileItem.toString())) {
                                        m.setMediaType(MediaFile.MediaTypes.VIDEO);
                                        insertMediaFileIntoSearchDB(m);
                                    } else if (FileTypes.isValidImge(fileItem.toString())) {
                                        m.setMediaType(MediaFile.MediaTypes.IMAGE);
                                        metadataController.setActualMediaFile(m);
                                        insertMediaFileIntoSearchDB(m);
                                    } else {
                                        m.setMediaType(MediaFile.MediaTypes.NONE);
                                    }
                                }
                            }
                            return super.visitFile(fileItem, attrs);
                        }
                    });
                } catch (IOException ex) {
                    Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
                }
                return true;
            }
        };
        task.setOnSucceeded((t) -> {
            App.setSearchIndexFinished(true);
            Logger.getLogger(SearchIndex.class.getName()).log(Level.INFO, "End time create searchDB: " + LocalDateTime.now());
        });
        task.setOnFailed((t) -> {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.INFO, "Error creating searchIndexDB", t.getSource().getException());
        });
        executorParallel.submit(task);

    }

    public void checkSearchIndex(String searchPath) {
        PathItem pathItem = new PathItem(Paths.get(searchPath));
        Task<Boolean> taskCheck = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                final Task runTask = this;
                try {
                    Files.walkFileTree(pathItem.getFilePath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(final Path fileItem, final BasicFileAttributes attrs) throws IOException {
                            if (runTask.isCancelled()) {
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
                    Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
                }
                return true;
            }
        };
        executorParallel.submit(taskCheck);
    }

    public void shutdown() {
        executorParallel.shutdownNow();
    }

    public void insertMediaFileIntoSearchDB(MediaFile m) {
        try {
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
            Statement indexStatment = App.getSearchDBConnection().createStatement();
            String stm = "INSERT INTO MediaFiles VALUES(" + name + "," + path + "," + title + "," + keyw + "," + cam + "," + rating + "," + recordTime + "," + creationTime + ")";
            indexStatment.execute(stm);
        } catch (SQLException ex) {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void updateMediaFileIntoSearchDB(MediaFile m) {
        if (checkIfIndexed(m) == false) {
            insertMediaFileIntoSearchDB(m);
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
                if (stm.lastIndexOf(",")==stm.length()-1){
                    stm=stm.substring(0,stm.length()-1);
                }
                stm=stm+" WHERE NAME="+"'"+m.getName()+"'"+" AND PATHSTORAGE="+"'"+m.getPathStorage().toString()+"'";                
                Statement indexStatment = App.getSearchDBConnection().createStatement();
                indexStatment.execute(stm);
            } catch (SQLException ex) {
                Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public void removeMediaFileInSearchDB(String name) {
        try {
            Statement indexStatment = App.getSearchDBConnection().createStatement();
            String stm = "DELETE FROM MediaFiles where NAME='" + name + "'";
            indexStatment.execute(stm);
        } catch (SQLException ex) {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public boolean checkIfIndexed(MediaFile m) {
        boolean ret = false;
        try {
            Statement indexStatment = App.getSearchDBConnection().createStatement();
            ResultSet rs = indexStatment.executeQuery("SELECT name from MediaFiles where NAME='" + m.getName() + "' and PATHSTORAGE='" + m.getPathStorage() + "'");
            if (rs.next()) {
                ret = true;
            }
        } catch (SQLException ex) {
            Logger.getLogger(SearchIndex.class.getName()).log(Level.SEVERE, null, ex);
        }
        return ret;
    }
}
