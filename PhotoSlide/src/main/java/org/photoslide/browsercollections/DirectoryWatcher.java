/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browsercollections;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.photoslide.datamodel.FileTypes;

/**
 *
 * @author cleme
 */
public class DirectoryWatcher {

    private CollectionsController controller;
    private WatchService watchService;
    private WatchKey key;
    private String pathElement;
    private String eventType;

    public DirectoryWatcher(CollectionsController c) {
        controller = c;
    }

    public void startWatch(Path watchPath, boolean recrusive) throws IOException, InterruptedException {
        watchService
                = FileSystems.getDefault().newWatchService();

        watchPath.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        if (recrusive == true) {
            registerRecursive(watchPath);
        }
        while ((key = watchService.take()) != null) {
            for (WatchEvent<?> event : key.pollEvents()) {
                Path resolveP = watchPath.resolve((Path) event.context());
                pathElement = resolveP.toString();
                eventType = event.kind().toString();
                boolean validFileType = FileTypes.isValidType(pathElement);
                if (validFileType == false) {
                    controller.refreshTreeParent(pathElement, eventType);
                }
            }
            List<WatchEvent<?>> pollEvents = key.pollEvents();
            if (pollEvents.isEmpty() == true) {
            }
            key.reset();
        }
    }

    private void registerRecursive(final Path root) throws IOException {
        // register all subfolders
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void stopWatch() {
        if (key != null) {
            key.cancel();
        }
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException ex) {
            Logger.getLogger(DirectoryWatcher.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
