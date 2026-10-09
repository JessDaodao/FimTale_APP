package com.fimtale.crash;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Best-effort local capture only; Android's normal crash handler still receives the failure. */
final class CrashHandler implements Thread.UncaughtExceptionHandler {
    private final CrashStore store;
    private final CrashReport.Environment environment;
    private final Supplier<String> screen;
    private final Thread.UncaughtExceptionHandler previous;
    private final Runnable terminate;
    private final AtomicBoolean recording = new AtomicBoolean();

    CrashHandler(CrashStore store, CrashReport.Environment environment, Supplier<String> screen,
            Thread.UncaughtExceptionHandler previous, Runnable terminate) {
        this.store = store; this.environment = environment; this.screen = screen;
        this.previous = previous; this.terminate = terminate;
    }
    @Override public void uncaughtException(Thread thread, Throwable error) {
        try {
            if (recording.compareAndSet(false, true)) store.record(CrashReport.capture(environment, screen.get(), error, System.currentTimeMillis()));
        } catch (Throwable ignored) {
            // Low-memory and storage failures must never prevent normal process termination.
        } finally {
            try { if (previous != null) previous.uncaughtException(thread, error); }
            finally { terminate.run(); }
        }
    }
}
