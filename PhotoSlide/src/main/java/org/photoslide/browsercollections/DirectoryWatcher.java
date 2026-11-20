/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browsercollections;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
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
import java.util.HashMap;
import java.util.Map;
import javafx.application.Platform;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.photoslide.datamodel.FileTypes;

/**
 * Watches folders for new or deleted sub folders and updates the collection
 * tree. One watch service and one (daemon) thread per watcher, no matter how
 * often the watched folders change: before, every selected event started a
 * pool thread that blocked in {@link WatchService#take()} forever and kept
 * the program from quitting.
 *
 * @author cleme
 */
public class DirectoryWatcher {

    private final CollectionsController controller;
    private final Object lock = new Object();
    // guarded by lock
    private WatchService watchService;
    private final Map<WatchKey, Path> keys = new HashMap<>();
    private boolean stopped;

    public DirectoryWatcher(CollectionsController c) {
        controller = c;
    }

    /** Watches {@code watchPath} in addition to the folders watched already. */
    public void watch(Path watchPath, boolean recursive) throws IOException {
        synchronized (lock) {
            if (stopped) {
                return;
            }
            WatchService ws = service();
            register(ws, watchPath);
            if (recursive) {
                Files.walkFileTree(watchPath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        register(ws, dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

    /** Watches {@code watchPath} only, the folders watched so far are dropped. */
    public void watchOnly(Path watchPath) throws IOException {
        synchronized (lock) {
            keys.keySet().forEach(WatchKey::cancel);
            keys.clear();
        }
        watch(watchPath, false);
    }

    /** Stops watching for good, the thread ends. */
    public void stopWatch() {
        synchronized (lock) {
            stopped = true;
            keys.clear();
            if (watchService != null) {
                try {
                    watchService.close();
                } catch (IOException ex) {
                    Logger.getLogger(DirectoryWatcher.class.getName()).log(Level.SEVERE, null, ex);
                }
                watchService = null;
            }
        }
    }

    // lock held
    private void register(WatchService ws, Path dir) throws IOException {
        WatchKey key = dir.register(ws, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, dir);
    }

    // lock held
    private WatchService service() throws IOException {
        if (watchService == null) {
            WatchService ws = FileSystems.getDefault().newWatchService();
            watchService = ws;
            Thread.ofVirtual().name("directoryWatcher").start(() -> loop(ws));
        }
        return watchService;
    }

    private void loop(WatchService ws) {
        try {
            while (true) {
                WatchKey key = ws.take();
                Path dir;
                synchronized (lock) {
                    dir = keys.get(key);
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (dir == null || event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    String pathElement = dir.resolve((Path) event.context()).toString();
                    String eventType = event.kind().toString();
                    if (!FileTypes.isValidType(pathElement)) {
                        // the tree belongs to the FX thread
                        Platform.runLater(() -> controller.refreshTreeParent(pathElement, eventType));
                    }
                }
                key.reset();
            }
        } catch (ClosedWatchServiceException | InterruptedException ex) {
            // stopped
        }
    }

}
