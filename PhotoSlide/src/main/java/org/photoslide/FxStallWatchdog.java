package org.photoslide;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;

/**
 * Diagnostics: notices when the JavaFX thread does not respond (spinning
 * beachball) and samples its stack while it hangs. Each stall is written with
 * the stacks seen, most frequent first, to fx-stalls.log in the app data
 * folder.
 */
public final class FxStallWatchdog {

    private static final long PING_MS = 20;
    private static final long STALL_MS = 100;
    private static final int FRAMES = 40;
    // a stall that does not end is written while it lasts
    private static final long ONGOING_MS = 3000;

    private static volatile Thread fxThread;
    private static volatile long lastPong;

    private FxStallWatchdog() {
    }

    /** Call on the JavaFX thread. */
    public static void start() {
        fxThread = Thread.currentThread();
        lastPong = System.currentTimeMillis();
        Thread t = new Thread(FxStallWatchdog::run, "fxStallWatchdog");
        t.setDaemon(true);
        t.start();
    }

    private static void run() {
        AtomicBoolean pingPending = new AtomicBoolean(false);
        Map<String, Integer> samples = new LinkedHashMap<>();
        long stallStart = 0;
        long gcAtStart = 0;
        long lastOngoing = 0;
        while (true) {
            try {
                Thread.sleep(PING_MS);
            } catch (InterruptedException ex) {
                return;
            }
            if (pingPending.compareAndSet(false, true)) {
                Platform.runLater(() -> {
                    lastPong = System.currentTimeMillis();
                    pingPending.set(false);
                });
            }
            long now = System.currentTimeMillis();
            long silent = now - lastPong;
            if (silent > STALL_MS) {
                if (stallStart == 0) {
                    stallStart = lastPong;
                    gcAtStart = gcTime();
                }
                samples.merge(stackOf(fxThread), 1, Integer::sum);
                if (silent > ONGOING_MS && now - lastOngoing > ONGOING_MS) {
                    write(" (ongoing)", silent, gcTime() - gcAtStart, samples);
                    lastOngoing = now;
                }
            } else if (stallStart != 0) {
                write("", lastPong - stallStart, gcTime() - gcAtStart, samples);
                lastOngoing = 0;
                samples.clear();
                stallStart = 0;
            }
        }
    }

    /** Accumulated collection time of all collectors (ms); a pause stops this thread too. */
    private static long gcTime() {
        long sum = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            sum += Math.max(0, gc.getCollectionTime());
        }
        return sum;
    }

    private static String stackOf(Thread t) {
        StringBuilder sb = new StringBuilder();
        sb.append("  state ").append(t.getState()).append('\n');
        StackTraceElement[] st = t.getStackTrace();
        if (st.length == 0) {
            sb.append("    (no Java frames: in native code, e.g. the macOS event loop or rendering)\n");
        }
        for (int i = 0; i < Math.min(FRAMES, st.length); i++) {
            sb.append("    at ").append(st[i]).append('\n');
        }
        return sb.toString();
    }

    private static void write(String state, long duration, long gc, Map<String, Integer> samples) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(samples.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        File log = new File(Utility.getAppData() + File.separatorChar + "fx-stalls.log");
        try (PrintWriter w = new PrintWriter(new FileWriter(log, true))) {
            w.println("=== " + LocalDateTime.now() + " FX thread stalled " + duration + " ms, GC " + gc + " ms" + state);
            for (Map.Entry<String, Integer> e : sorted) {
                w.println(" " + e.getValue() + " samples:");
                w.print(e.getKey());
            }
            w.println();
        } catch (IOException ex) {
            // diagnostics only
        }
    }
}
