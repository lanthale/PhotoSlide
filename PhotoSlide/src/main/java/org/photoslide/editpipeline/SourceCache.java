package org.photoslide.editpipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.photoslide.datamodel.Image16;

/**
 * The decoded full size sources (linear working space, RAW with its base
 * look) of the last files, shared between all views: when the editor and the
 * lighttable show the same file, it is decoded only once, and a second view
 * asking while it is decoded waits for that result.
 *
 * The key is the path and the modification time of the file, so a changed
 * file is decoded again. One 24 MP source takes about 144 MB.
 */
public final class SourceCache {

    /** Decodes a file (called only by the first view asking for it). */
    @FunctionalInterface
    public interface Loader {

        Image16 load() throws IOException;
    }

    /** Full size sources kept in memory. */
    private static final int SIZE = 2;

    /** Least recently used first (access order). */
    private static final Map<String, CompletableFuture<Image16>> ENTRIES = new LinkedHashMap<>(4, 0.75f, true);

    private SourceCache() {
    }

    /**
     * The source of the file: from the cache, or decoded by the loader. Blocks
     * until it is available. Any thread except the JavaFX thread.
     */
    public static Image16 get(Path path, Loader loader) throws IOException {
        String key = path.toAbsolutePath() + "|" + Files.getLastModifiedTime(path).toMillis();
        CompletableFuture<Image16> future;
        boolean owner = false;
        synchronized (ENTRIES) {
            future = ENTRIES.get(key);
            if (future == null) {
                future = new CompletableFuture<>();
                ENTRIES.put(key, future);
                owner = true;
                Iterator<String> it = ENTRIES.keySet().iterator();
                while (ENTRIES.size() > SIZE && it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
        }
        if (owner) {
            try {
                future.complete(loader.load());
            } catch (IOException | RuntimeException ex) {
                future.completeExceptionally(ex);
                synchronized (ENTRIES) {
                    ENTRIES.remove(key);
                }
            }
        }
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Loading interrupted: " + path, ex);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof IOException io) {
                throw io;
            }
            throw new IOException("Cannot load " + path, ex.getCause());
        }
    }

    /** Drops the file, so the next get() decodes it again. */
    public static void invalidate(Path path) {
        String prefix = path.toAbsolutePath() + "|";
        synchronized (ENTRIES) {
            ENTRIES.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    /** Drops everything, e.g. after the RAW decoder settings changed. */
    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }
}
