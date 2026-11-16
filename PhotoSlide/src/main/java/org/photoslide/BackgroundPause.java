package org.photoslide;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lets background work (search index, face scan) step aside while the user
 * waits for something, e.g. the media list of a folder that was clicked.
 * Whoever the user waits for calls {@link #pause(Object)} and
 * {@link #resume(Object)} when done; background loops call
 * {@link #awaitResumed(BooleanSupplier)} between their steps.
 */
public final class BackgroundPause {

    private static final Logger LOG = Logger.getLogger(BackgroundPause.class.getName());
    /** a holder that never resumes must not stop the background work forever */
    private static final long MAX_PAUSE_MS = 10 * 60 * 1000L;
    private static final Object LOCK = new Object();
    private static final Set<Object> HOLDERS = ConcurrentHashMap.newKeySet();
    private static volatile long pausedSince;

    private BackgroundPause() {
    }

    /** Pauses the background work until {@code holder} calls {@link #resume}. */
    public static void pause(Object holder) {
        synchronized (LOCK) {
            if (HOLDERS.isEmpty()) {
                pausedSince = System.currentTimeMillis();
            }
            if (HOLDERS.add(holder)) {
                LOG.log(Level.FINE, "Background work paused by {0}", holder);
            }
        }
    }

    public static void resume(Object holder) {
        synchronized (LOCK) {
            if (HOLDERS.remove(holder)) {
                LOG.log(Level.FINE, "Background work resumed by {0}", holder);
            }
            if (HOLDERS.isEmpty()) {
                LOCK.notifyAll();
            }
        }
    }

    public static boolean isPaused() {
        return !HOLDERS.isEmpty() && System.currentTimeMillis() - pausedSince < MAX_PAUSE_MS;
    }

    /**
     * Blocks the calling background thread while paused.
     *
     * @param abort checked regularly, e.g. the task was cancelled
     * @return true if it had to wait
     */
    public static boolean awaitResumed(BooleanSupplier abort) throws InterruptedException {
        boolean waited = false;
        synchronized (LOCK) {
            while (isPaused() && !abort.getAsBoolean()) {
                waited = true;
                LOCK.wait(500);
            }
        }
        return waited;
    }
}
