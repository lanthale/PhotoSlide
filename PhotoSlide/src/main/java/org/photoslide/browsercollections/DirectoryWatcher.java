/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.photoslide.browsercollections;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;

/**
 *
 * @author cleme
 */
public class DirectoryWatcher {

    private CollectionsController controller;
    private WatchService watchService;
    private WatchKey key;

    public DirectoryWatcher(CollectionsController c) {
        controller = c;
    }

    public void startWatch(Path watchPath) throws IOException, InterruptedException {
        watchService
                = FileSystems.getDefault().newWatchService();

        watchPath.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE);
        Path parent = watchPath.getParent();
        parent.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE);
        while ((key = watchService.take()) != null) {
            for (WatchEvent<?> event : key.pollEvents()) {
                Path resolve = watchPath.resolve((Path) event.context());
                Path resolveParent = parent.resolve((Path) event.context());
                if (resolve.toString().endsWith(".edit") == false) {
                    if (controller.getSelectedPath().startsWith(resolve)) {
                        System.out.println("changes " + controller.getSelectedPath());
                        controller.refreshTreeParent();
                    }
                }
                if (resolveParent.toString().endsWith(".edit") == false) {
                    System.out.println("changes " + controller.getSelectedPath());
                    controller.refreshTreeParent();
                }
            }
            List<WatchEvent<?>> pollEvents = key.pollEvents();
            if (pollEvents.isEmpty() == false) {
            }
            key.reset();
        }
    }

    public void stopWatch() {
        if (key != null) {
            key.cancel();
        }
    }

}
