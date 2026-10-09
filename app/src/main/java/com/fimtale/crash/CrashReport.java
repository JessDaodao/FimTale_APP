package com.fimtale.crash;

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
                    BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")",
                    "Android " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT,
                    limit(Build.MANUFACTURER + " " + Build.MODEL, 160));
        }
    }

    static CrashReport capture(Environment environment, String screen, Throwable error, long timestamp) {
        StringBuilder stack = new StringBuilder(MAX_STACK);
        Throwable current = error;
        // Bound both cause depth and frames, and never call Throwable.toString()/getMessage().
        for (int depth = 0; current != null && depth < 8 && stack.length() < MAX_STACK; depth++) {
            if (depth > 0) stack.append("Caused by: ");
            stack.append(current.getClass().getName()).append('\n');
            StackTraceElement[] frames = current.getStackTrace();
            int count = Math.min(frames.length, 8);
            for (int i = 0; i < count && stack.length() < MAX_STACK; i++)
                stack.append("  at ").append(frames[i]).append('\n');
            if (count < frames.length) stack.append("  …\n");
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
                "系统记录了" + (nativeCrash ? "原生崩溃" : "应用崩溃") + "，未获取到 Java 异常堆栈。",
                limit(screen, 160), exit.timestamp, environment);
    }

    boolean valid() {
        return id != null && !id.isEmpty() && kind != null && stack != null && stack.length() <= MAX_STACK
                && screen != null && timestamp > 0 && environment != null && environment.appVersion != null
                && environment.androidVersion != null && environment.device != null;
    }

    public String summary() {
        return "Android 崩溃反馈\n时间：" + Instant.ofEpochMilli(timestamp) + "\n应用版本：" + environment.appVersion
                + "\n系统：" + environment.androidVersion + "\n设备：" + environment.device
                + "\n页面：" + (screen.isEmpty() ? "启动阶段" : screen) + "\n错误类型：" + kind;
    }
    public String diagnostics() { return summary() + "\n\n" + stack; }
    static String limit(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        int end = max - 1;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…";
    }
}
