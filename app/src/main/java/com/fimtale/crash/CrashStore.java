package com.fimtale.crash;

import com.fimtale.R;

import com.fimtale.utils.AppStrings;

import android.app.ApplicationExitInfo;
import android.content.Context;
import android.util.AtomicFile;
import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** One pending incident, outside Android backup. All instances share the same file lock. */
public final class CrashStore {
    private static final Object LOCK = new Object();
    private static final int MAX_BYTES = 32 * 1024;
    private final AtomicFile file;
    private final Gson gson = new Gson();

    public CrashStore(Context context) { this(new File(context.getNoBackupFilesDir(), "crash-feedback.json")); }
    CrashStore(File path) { file = new AtomicFile(path); }
    private static final class State {
        long checkedAt;
        CrashReport.Environment lastRun;
        String lastScreen = "";
        CrashReport pending;
    }
    static final class Exit {
        final long timestamp;
        final int pid, reason;
        final String processName;
        Exit(long timestamp, int pid, int reason, String processName) {
            this.timestamp = timestamp; this.pid = pid; this.reason = reason; this.processName = processName;
        }
    }

    CrashReport beginLaunch(CrashReport.Environment current, List<Exit> exits) throws IOException {
        synchronized (LOCK) {
            State state = read();
            Exit newest = null;
            for (Exit exit : exits) {
                if (state.checkedAt <= 0 || exit.timestamp < state.checkedAt || exit.timestamp >= current.startedAt
                        || !current.processName.equals(exit.processName)
                        || (exit.reason != ApplicationExitInfo.REASON_CRASH && exit.reason != ApplicationExitInfo.REASON_CRASH_NATIVE)) continue;
                if (newest == null || exit.timestamp > newest.timestamp) newest = exit;
            }
            if (newest != null) {
                CrashReport pending = state.pending;
                // Keep the richer local stack when Android also reports that same process's crash.
                boolean recordedLocally = pending != null && pending.id.startsWith("java:")
                        && pending.environment.pid == newest.pid && pending.timestamp >= state.checkedAt;
                if (!recordedLocally && (pending == null || newest.timestamp > pending.timestamp)) {
                    CrashReport.Environment previous = state.lastRun != null && state.lastRun.pid == newest.pid ? state.lastRun : current;
                    state.pending = CrashReport.systemExit(previous, state.lastScreen, newest);
                }
            }
            // Checkpoint the launch even if Android's exit history is empty/delayed, so handled
            // incidents cannot be rediscovered on a later launch from the same historical entry.
            state.checkedAt = current.startedAt; state.lastRun = current; state.lastScreen = "";
            write(state); return state.pending;
        }
    }
    void record(CrashReport report) throws IOException {
        synchronized (LOCK) {
            State state = read(); state.pending = report;
            if (state.lastRun == null) { state.lastRun = report.environment; state.checkedAt = report.environment.startedAt; }
            write(state);
        }
    }
    void rememberScreen(CrashReport.Environment run, String screen) throws IOException {
        synchronized (LOCK) {
            State state = read();
            if (state.lastRun == null || state.lastRun.startedAt != run.startedAt || state.lastRun.pid != run.pid) return;
            state.lastScreen = CrashReport.limit(screen, 160); write(state);
        }
    }
    public CrashReport pending() { synchronized (LOCK) { return read().pending; } }
    public boolean acknowledge(String id) throws IOException {
        synchronized (LOCK) {
            State state = read();
            if (state.pending == null || !state.pending.id.equals(id)) return false;
            state.pending = null; write(state); return true;
        }
    }
    public void setDeliveryUncertain(String id, boolean uncertain) throws IOException {
        synchronized (LOCK) {
            State state = read();
            if (state.pending == null || !state.pending.id.equals(id)) throw new IOException(AppStrings.get(R.string.crash_feedback_incident_not_pending));
            state.pending.deliveryUncertain = uncertain; write(state);
        }
    }
    private State read() {
        try (FileInputStream input = file.openRead(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > MAX_BYTES) return new State();
                output.write(buffer, 0, count);
            }
            State state = gson.fromJson(new String(output.toByteArray(), StandardCharsets.UTF_8), State.class);
            if (state == null) return new State();
            if (state.pending != null && !state.pending.valid()) state.pending = null;
            return state;
        } catch (IOException | RuntimeException ignored) { return new State(); }
    }
    private void write(State state) throws IOException {
        byte[] bytes = gson.toJson(state).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException(AppStrings.get(R.string.crash_feedback_snapshot_too_large));
        FileOutputStream output = null;
        try { output = file.startWrite(); output.write(bytes); file.finishWrite(output); }
        catch (IOException | RuntimeException error) { if (output != null) file.failWrite(output); throw error; }
    }
}
