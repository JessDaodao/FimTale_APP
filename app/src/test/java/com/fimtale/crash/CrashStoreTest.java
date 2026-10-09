package com.fimtale.crash;

import android.app.Application;
import android.app.ApplicationExitInfo;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class CrashStoreTest {
    private CrashStore store;
    private File file;
    private final CrashReport.Environment first = environment(1000, 100, "1.0 (1)");
    private static CrashReport.Environment environment(long start, int pid, String version) {
        return new CrashReport.Environment(start, pid, "com.fimtale", version, "Android 14 / API 34", "Fixture device");
    }
    private CrashStore.Exit exit(long at, int pid, int reason) { return new CrashStore.Exit(at, pid, reason, "com.fimtale"); }
    private CrashReport report(CrashReport.Environment run, long at) {
        return CrashReport.capture(run, "EditorActivity", new IllegalStateException("private draft and token"), at);
    }
    @Before public void setup() {
        file = new File(RuntimeEnvironment.getApplication().getNoBackupFilesDir(), "crash-feedback.json");
        new android.util.AtomicFile(file).delete(); store = new CrashStore(file);
    }
    @Test public void javaCaptureSurvivesRestartAndIsNotDuplicatedByAndroidExitHistory() throws Exception {
        assertNull(store.beginLaunch(first, Collections.emptyList()));
        CrashReport captured = report(first, 1500); store.record(captured);
        CrashStore reopened = new CrashStore(file);
        CrashReport pending = reopened.beginLaunch(environment(2000, 200, "1.0 (1)"),
                Collections.singletonList(exit(1510, 100, ApplicationExitInfo.REASON_CRASH)));
        assertEquals(captured.id, pending.id); assertTrue(pending.stack.contains("IllegalStateException"));
        assertTrue(reopened.acknowledge(pending.id));
        assertNull(new CrashStore(file).beginLaunch(environment(3000, 300, "1.0 (1)"),
                Collections.singletonList(exit(1510, 100, ApplicationExitInfo.REASON_CRASH))));
    }
    @Test public void nativeCrashUsesTheVersionAndPageOfTheCrashedRun() throws Exception {
        store.beginLaunch(first, Collections.emptyList()); store.rememberScreen(first, "ReaderActivity");
        CrashReport pending = store.beginLaunch(environment(3000, 300, "2.0 (2)"), Arrays.asList(
                exit(1700, 100, ApplicationExitInfo.REASON_CRASH_NATIVE), exit(800, 80, ApplicationExitInfo.REASON_CRASH)));
        assertEquals("android.native_crash", pending.kind); assertEquals(1700, pending.timestamp);
        assertEquals("1.0 (1)", pending.environment.appVersion); assertEquals("ReaderActivity", pending.screen);
    }
    @Test public void normalProcessDeathsAndUnrelatedOrOldCrashesNeverCreateFeedback() throws Exception {
        assertNull(store.beginLaunch(first, Collections.singletonList(exit(800, 80, ApplicationExitInfo.REASON_CRASH))));
        assertNull(store.beginLaunch(environment(3000, 300, "1.0 (1)"), Arrays.asList(
                exit(1100, 100, ApplicationExitInfo.REASON_LOW_MEMORY),
                exit(1200, 100, ApplicationExitInfo.REASON_USER_REQUESTED),
                exit(1300, 100, ApplicationExitInfo.REASON_ANR),
                new CrashStore.Exit(1400, 100, ApplicationExitInfo.REASON_CRASH, "com.fimtale:other"),
                exit(800, 80, ApplicationExitInfo.REASON_CRASH))));
    }
    @Test public void delayedExitHistoryDoesNotResurrectAnAlreadyDeclinedIncident() throws Exception {
        store.beginLaunch(first, Collections.emptyList()); CrashReport captured = report(first, 1500); store.record(captured);
        store.beginLaunch(environment(2000, 200, "1.0 (1)"), Collections.emptyList()); store.acknowledge(captured.id);
        assertNull(store.beginLaunch(environment(3000, 300, "1.0 (1)"),
                Collections.singletonList(exit(1600, 100, ApplicationExitInfo.REASON_CRASH))));
    }
    @Test public void oldFeedbackCannotClearANewerCrashAndUncertainDeliverySurvivesRestart() throws Exception {
        store.beginLaunch(first, Collections.emptyList()); CrashReport old = report(first, 1500); store.record(old);
        store.setDeliveryUncertain(old.id, true);
        assertTrue(new CrashStore(file).pending().deliveryUncertain);
        CrashReport newer = report(environment(2000, 200, "1.0 (1)"), 2500); store.record(newer);
        assertFalse(store.acknowledge(old.id)); assertEquals(newer.id, store.pending().id);
    }
    @Test public void captureOmitsMessagesAndBoundsEvenHugeOrCyclicExceptionChains() {
        RuntimeException cause = new RuntimeException("Token secret; article content");
        IllegalStateException outer = new IllegalStateException("sensitive URL", cause);
        cause.initCause(outer);
        StackTraceElement[] frames = new StackTraceElement[1000];
        Arrays.fill(frames, new StackTraceElement("com.fimtale.EditorActivity", "render", "EditorActivity.java", 42));
        outer.setStackTrace(frames);
        CrashReport report = CrashReport.capture(first, "EditorActivity", outer, 1500);
        assertTrue(report.stack.length() <= CrashReport.MAX_STACK);
        assertTrue(report.stack.contains("EditorActivity.java:42"));
        assertFalse(report.diagnostics().contains("sensitive URL")); assertFalse(report.diagnostics().contains("article content"));
        assertTrue(file.toPath().startsWith(RuntimeEnvironment.getApplication().getNoBackupFilesDir().toPath()));
    }
    @Test public void corruptOrOversizedFilesDoNotBreakTheNextLaunch() throws Exception {
        Files.write(file.toPath(), "{broken".getBytes(StandardCharsets.UTF_8)); assertNull(store.pending());
        Files.write(file.toPath(), new byte[40 * 1024]); assertNull(store.pending());
        assertNull(store.beginLaunch(first, Collections.emptyList()));
        store.record(report(first, 1500)); assertNotNull(store.pending());
    }
    @Test public void handlerPersistsBeforeDelegatingAndAlwaysTerminatesEvenIfStorageFails() throws Exception {
        AtomicInteger calls = new AtomicInteger(); Throwable failure = new IllegalStateException("fixture");
        CrashHandler handler = new CrashHandler(store, first, () -> "ReaderActivity", (thread, error) -> {
            assertSame(failure, error); assertEquals("ReaderActivity", store.pending().screen); calls.incrementAndGet();
        }, calls::incrementAndGet);
        handler.uncaughtException(Thread.currentThread(), failure); assertEquals(2, calls.get());
        File blocker = new File(file.getParentFile(), "not-a-directory"); Files.write(blocker.toPath(), new byte[]{1});
        CrashHandler broken = new CrashHandler(new CrashStore(new File(blocker, "report")), first, () -> "", (thread, error) -> {
            calls.incrementAndGet(); throw new IllegalStateException("previous handler failed");
        }, calls::incrementAndGet);
        assertThrows(IllegalStateException.class, () -> broken.uncaughtException(Thread.currentThread(), failure));
        assertEquals(4, calls.get());
    }
}
