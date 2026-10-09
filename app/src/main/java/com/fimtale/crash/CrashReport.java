package com.fimtale.crash;

import com.fimtale.R;
import com.fimtale.utils.AppStrings;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import com.fimtale.BuildConfig;
import java.time.Instant;

/** A bounded diagnostic snapshot. Exception messages can contain document text or credentials. */
public final class CrashReport {
    static final int MAX_STACK = 2000;
    public final String id, kind, stack, screen;
    public final long timestamp;
    public final Environment environment;
    public boolean deliveryUncertain;

    CrashReport(String id, String kind, String stack, String screen, long timestamp, Environment environment) {
        this.id = id; this.kind = kind; this.stack = stack; this.screen = screen;
        this.timestamp = timestamp; this.environment = environment;
    }

    static final class Environment {
        final long startedAt;
        final int pid;
        final String processName, appVersion, androidVersion, device;
        Environment(long startedAt, int pid, String processName, String appVersion, String androidVersion, String device) {
            this.startedAt = startedAt; this.pid = pid; this.processName = processName;
            this.appVersion = appVersion; this.androidVersion = androidVersion; this.device = device;
        }
        static Environment current(Context context) {
            String process = Application.getProcessName();
            return new Environment(System.currentTimeMillis(), Process.myPid(), process == null ? context.getPackageName() : process,
                    AppStrings.get(R.string.crash_feedback_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    AppStrings.get(R.string.crash_feedback_android_version, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
                    limit(AppStrings.get(R.string.crash_feedback_device_name, Build.MANUFACTURER, Build.MODEL), 160));
        }
    }

    static CrashReport capture(Environment environment, String screen, Throwable error, long timestamp) {
        StringBuilder stack = new StringBuilder(MAX_STACK);
        Throwable current = error;
        // Bound both cause depth and frames, and never call Throwable.toString()/getMessage().
        for (int depth = 0; current != null && depth < 8 && stack.length() < MAX_STACK; depth++) {
            if (depth > 0) stack.append(AppStrings.get(R.string.crash_feedback_stack_cause));
            stack.append(current.getClass().getName()).append('\n');
            StackTraceElement[] frames = current.getStackTrace();
            int count = Math.min(frames.length, 8);
            for (int i = 0; i < count && stack.length() < MAX_STACK; i++)
                stack.append(AppStrings.get(R.string.crash_feedback_stack_frame, frames[i]));
            if (count < frames.length) stack.append(AppStrings.get(R.string.crash_feedback_stack_truncated));
            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
        }
        return new CrashReport("java:" + environment.startedAt + ":" + environment.pid,
                error.getClass().getName(), limit(stack.toString(), MAX_STACK), limit(screen, 160), timestamp, environment);
    }

    static CrashReport systemExit(Environment environment, String screen, CrashStore.Exit exit) {
        boolean nativeCrash = exit.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE;
        return new CrashReport("exit:" + exit.pid + ":" + exit.timestamp,
                nativeCrash ? "android.native_crash" : "android.java_crash",
                AppStrings.get(nativeCrash ? R.string.crash_feedback_native_exit : R.string.crash_feedback_java_exit),
                limit(screen, 160), exit.timestamp, environment);
    }

    boolean valid() {
        return id != null && !id.isEmpty() && kind != null && stack != null && stack.length() <= MAX_STACK
                && screen != null && timestamp > 0 && environment != null && environment.appVersion != null
                && environment.androidVersion != null && environment.device != null;
    }

    public String summary() {
        return AppStrings.get(R.string.crash_feedback_summary, Instant.ofEpochMilli(timestamp), environment.appVersion, environment.androidVersion, environment.device, screen.isEmpty() ? AppStrings.get(R.string.crash_feedback_startup_screen) : screen, kind);
    }
    public String diagnostics() { return AppStrings.get(R.string.crash_feedback_diagnostics, summary(), stack); }
    static String limit(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        int end = max - 1;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + '\u2026';
    }
}
