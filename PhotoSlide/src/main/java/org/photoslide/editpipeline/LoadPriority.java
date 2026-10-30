package org.photoslide.editpipeline;

import java.util.concurrent.Callable;

/**
 * Gives the image the user looks at priority over thumbnails: while a
 * preview is loading, no new thumbnail starts. On a network share all reads
 * share the bandwidth, so the preview would otherwise wait for the thumbnails
 * of all visible grid cells.
 */
public final class LoadPriority {

    private static final Object LOCK = new Object();
    private static int foreground;

    private LoadPriority() {
    }

    /** Runs a load the user waits for (e.g. the preview). Any thread. */
    public static <T> T foreground(Callable<T> load) throws Exception {
        synchronized (LOCK) {
            foreground++;
        }
        try {
            return load.call();
        } finally {
            synchronized (LOCK) {
                foreground--;
                LOCK.notifyAll();
            }
        }
    }

    // a hanging preview must not stop the thumbnails for good
    private static final long MAX_WAIT_MS = 10_000;

    /** Called by background loads (thumbnails) before they start. */
    public static void awaitBackground() throws InterruptedException {
        long deadline = System.currentTimeMillis() + MAX_WAIT_MS;
        synchronized (LOCK) {
            long rest;
            while (foreground > 0 && (rest = deadline - System.currentTimeMillis()) > 0) {
                LOCK.wait(rest);
            }
        }
    }
}
