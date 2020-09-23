/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.itarchitects.lightzonefx;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author selfemp
 */
public class ThreadFactoryLZFX implements ThreadFactory {
    // Note:  The source code for this class was based entirely on 
    // Executors.DefaultThreadFactory class from the JDK8 source.
    // The only change made is the ability to configure the thread
    // name prefix.


    private static final AtomicInteger poolNumber = new AtomicInteger(1);
    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    /**
     * Creates a new ThreadFactory where threads are created with a name prefix
     * of <code>prefix</code>.
     *
     * @param prefix Thread name prefix. Never use a value of "pool" as in that
     *      case you might as well have used
     *      {@link java.util.concurrent.Executors#defaultThreadFactory()}.
     */
    public ThreadFactoryLZFX(String prefix) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup()
                : Thread.currentThread().getThreadGroup();
        namePrefix = prefix + "-"
                + poolNumber.getAndIncrement()
                + "-thread-";
    }


    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r,
                namePrefix + threadNumber.getAndIncrement(),
                0);
        if (t.isDaemon()) {
            t.setDaemon(false);
        }
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        return t;
    }
}
