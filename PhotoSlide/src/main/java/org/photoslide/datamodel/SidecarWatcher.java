package org.photoslide.datamodel;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;

/**
 * Watches the XMP sidecars of one folder and reloads the edits of the media
 * files when a sidecar is created, changed or deleted by another program
 * (e.g. Lightroom, a file manager). Changes PhotoSlide made itself are
 * recognised (XmpSidecar.isOwnState) and ignored.
 *
 * The folder is polled once per second (modification time and size of the
 * .xmp files): Java's WatchService only polls every few seconds on macOS.
 */
public final class SidecarWatcher {

    private static final Logger LOG = Logger.getLogger(SidecarWatcher.class.getName());
    private static final long INTERVAL_MS = 1000;

    private record Stamp(long modified, long size) {
    }

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("sidecarWatcher").daemon().factory());
    private final Path folder;
    private final Function<Path, List<MediaFile>> filesFor;
    private final BiConsumer<MediaFile, Path> reload;
    private Map<Path, Stamp> last;
    private ScheduledFuture<?> task;

    /**
     * @param folder the folder shown in the lighttable
     * @param filesFor media files whose sidecar is the given path (JavaFX thread)
     * @param reload called on the JavaFX thread for each such media file
     */
    public SidecarWatcher(Path folder, Function<Path, List<MediaFile>> filesFor, BiConsumer<MediaFile, Path> reload) {
        this.folder = folder;
        this.filesFor = filesFor;
        this.reload = reload;
    }

    public void start() {
        // the first scan reads the whole folder: not on the JavaFX thread. The
        // timer has one thread, so it runs before the first poll
        timer.execute(() -> last = scan());
        task = timer.scheduleWithFixedDelay(this::poll, INTERVAL_MS, INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (task != null) {
            task.cancel(false);
        }
        timer.shutdownNow();
    }

    private Map<Path, Stamp> scan() {
        Map<Path, Stamp> stamps = new HashMap<>();
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(folder, "*.{xmp,XMP}")) {
            for (Path p : dir) {
                BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
                if (a.isRegularFile()) {
                    stamps.put(p, new Stamp(a.lastModifiedTime().toMillis(), a.size()));
                }
            }
        } catch (IOException ex) {
            LOG.log(Level.FINE, "Cannot scan " + folder, ex);
        }
        return stamps;
    }

    private void poll() {
        try {
            Map<Path, Stamp> now = scan();
            Set<Path> changed = new HashSet<>();
            for (Map.Entry<Path, Stamp> e : now.entrySet()) {
                if (!Objects.equals(last.get(e.getKey()), e.getValue())) {
                    changed.add(e.getKey());
                }
            }
            for (Path p : last.keySet()) {
                if (!now.containsKey(p)) {
                    changed.add(p);
                }
            }
            last = now;
            for (Path p : changed) {
                if (XmpSidecar.isOwnState(p)) {
                    continue;
                }
                LOG.log(Level.FINE, "Sidecar changed outside PhotoSlide: {0}", p);
                Platform.runLater(() -> {
                    for (MediaFile m : filesFor.apply(p)) {
                        reload.accept(m, p);
                    }
                });
            }
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "Sidecar watcher failed", ex);
        }
    }
}
