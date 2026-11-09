package com.logvex.crystal.remote.core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Daemon threads and a shared scheduler for the remote-control layer.
 *
 * <p>Everything here is a daemon so a client that is shutting down is never held open by a
 * networking thread, and the scheduler is shared so a fleet of twenty agents does not create
 * twenty timer threads. Thread names are prefixed {@code logvex-remote-} so a stack dump or a
 * profiler immediately shows which threads belong to this feature.
 *
 * <p>Minecraft-independent.
 */
public final class RemoteThreads {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newScheduledThreadPool(2, namedFactory("timer"));

    private RemoteThreads() {
    }

    /** A daemon thread factory whose threads share the {@code logvex-remote-} prefix. */
    public static ThreadFactory namedFactory(String role) {
        return runnable -> {
            Thread thread = new Thread(runnable, "logvex-remote-" + role + "-" + COUNTER.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** The shared daemon scheduler used for heartbeats and reconnection timers. */
    public static ScheduledExecutorService scheduler() {
        return SCHEDULER;
    }
}
