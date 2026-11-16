package org.photoslide;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Diagnostics: writes the stacks of all threads to slow-load.log in the app
 * data folder, e.g. when a folder takes long to load, to see what the
 * program is busy with meanwhile.
 */
public final class ThreadDump {

    private static final int FRAMES = 25;
    // the log is for the last few cases only
    private static final long MAX_LOG_BYTES = 5L * 1024 * 1024;

    private ThreadDump() {
    }

    public static void write(String reason) {
        File log = new File(Utility.getAppData() + File.separatorChar + "slow-load.log");
        boolean append = !log.exists() || log.length() < MAX_LOG_BYTES;
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        try (PrintWriter w = new PrintWriter(new FileWriter(log, append))) {
            w.println("=== " + LocalDateTime.now() + " " + reason);
            w.println("heap used " + (heap.getUsed() >> 20) + " MB of " + (heap.getMax() >> 20) + " MB, "
                    + "background paused: " + BackgroundPause.isPaused());
            for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                Thread t = e.getKey();
                StackTraceElement[] st = e.getValue();
                // idle pool threads only make the log long
                if (isIdle(st)) {
                    continue;
                }
                w.println("\"" + t.getName() + "\" prio " + t.getPriority() + " " + t.getState());
                for (int i = 0; i < Math.min(FRAMES, st.length); i++) {
                    w.println("    at " + st[i]);
                }
            }
            w.println();
        } catch (IOException ex) {
            // diagnostics only
        }
    }

    /** A pool thread waiting for work. */
    private static boolean isIdle(StackTraceElement[] st) {
        for (StackTraceElement f : st) {
            if (f.getMethodName().equals("getTask") && f.getClassName().endsWith("ThreadPoolExecutor")) {
                return true;
            }
        }
        return false;
    }
}
