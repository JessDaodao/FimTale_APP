package com.fimtale.crash;

import android.app.Application;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import com.fimtale.LoginActivity;
import com.fimtale.R;
import com.fimtale.network.ApiDataConverter;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.network.RetrofitClient;
import com.fimtale.network.SiteUrls;
import com.fimtale.utils.UserPreferences;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import okhttp3.*;
import okio.Buffer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Only local HTTP fixtures: no real feedback is submitted. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = com.fimtale.ResourceApplication.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class CrashFeedbackTest {
    public static class Host extends AppCompatActivity {
        @Override public void onCreate(Bundle state) { setTheme(R.style.Theme_Fimtale); super.onCreate(state); }
    }
    private Application app;
    private ActivityController<Host> controller;
    private Host activity;
    private CrashStore store;
    private CrashReport report;
    private Field service, factory;
    private Object originalApi;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private volatile boolean loseResponse, emptyResponse;
    private CountDownLatch gate;

    @Before public void setup() throws Exception {
        app = RuntimeEnvironment.getApplication(); UserPreferences.saveToken(app, "crash-fixture-session");
        new android.util.AtomicFile(new java.io.File(app.getNoBackupFilesDir(), "crash-feedback.json")).delete();
        store = new CrashStore(app);
        long now = System.currentTimeMillis();
        CrashReport.Environment previous = new CrashReport.Environment(now - 2000, 123, app.getPackageName(), "0.6.0 (7)", "Android 14 / API 34", "Fixture device");
        store.beginLaunch(previous, Collections.emptyList());
        report = CrashReport.capture(previous, "EditorActivity", new IllegalStateException("private document"), now - 1000); store.record(report);
        factory = ViewModelProvider.AndroidViewModelFactory.class.getDeclaredField("sInstance"); factory.setAccessible(true); factory.set(null, null);
        service = RetrofitClient.class.getDeclaredField("service"); service.setAccessible(true); originalApi = service.get(null);
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(chain -> {
            Request request = chain.request(); requests.add(request);
            if (gate != null) try { gate.await(8, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new java.io.IOException(e); }
            if (loseResponse) throw new java.io.IOException("Response lost");
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                    .body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + (emptyResponse ? "null" : "{\"id\":42}")
                            + ",\"msg\":\"请稍后重试\"}")).build();
        }).build();
        service.set(null, new Retrofit.Builder().baseUrl(SiteUrls.API).client(client)
                .callbackExecutor(command -> new Handler(Looper.getMainLooper()).post(command))
                .addConverterFactory(new ApiDataConverter()).addConverterFactory(GsonConverterFactory.create()).build().create(FimTaleApiService.class));
        controller = Robolectric.buildActivity(Host.class).setup(); activity = controller.get();
    }
    @After public void cleanup() throws Exception {
        if (gate != null) gate.countDown();
        controller.pause().stop().destroy(); service.set(null, originalApi); factory.set(null, null);
    }
    private CrashFeedbackDialog fragment() { return (CrashFeedbackDialog) activity.getSupportFragmentManager().findFragmentByTag(CrashFeedbackDialog.TAG); }
    private AlertDialog dialog() { return (AlertDialog) fragment().requireDialog(); }
    private CrashFeedbackViewModel model() { return new ViewModelProvider(fragment()).get(CrashFeedbackViewModel.class); }
    private void open() { CrashFeedbackDialog.show(activity, store.pending()); }
    private void describe(String value) { ((EditText) dialog().findViewById(R.id.crashFeedbackDescription)).setText(value); }
    private void submit() { dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); }
    private void await(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); if (ready.getAsBoolean()) return; Thread.sleep(10);
        }
        fail("Timed out waiting for local feedback fixture");
    }
    @Test public void previewAndDeclineNeverSendAndRemoveThePendingIncident() throws Exception {
        open(); assertTrue(requests.isEmpty());
        dialog().findViewById(R.id.crashFeedbackShowDetails).performClick();
        TextView details = dialog().findViewById(R.id.crashFeedbackDetails);
        assertEquals(android.view.View.VISIBLE, details.getVisibility());
        assertEquals(report.diagnostics(), details.getText().toString()); assertFalse(details.getText().toString().contains("private document"));
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle();
        assertNull(fragment()); assertNull(new CrashStore(app).pending()); assertTrue(requests.isEmpty());
    }
    @Test public void explicitConsentPostsPreviewedDiagnosticsInTheFeedbackBody() throws Exception {
        open(); describe("  点击表格时闪退  "); assertTrue(requests.isEmpty()); submit(); await(() -> fragment() == null);
        assertEquals(1, requests.size()); Request request = requests.get(0);
        assertEquals("POST", request.method()); assertEquals("/api/report/create_report", request.url().encodedPath());
        assertEquals("crash-fixture-session", request.header("Token")); assertNull(request.url().query());
        Buffer buffer = new Buffer(); request.body().writeTo(buffer);
        JsonObject body = new JsonParser().parse(buffer.readUtf8()).getAsJsonObject();
        assertEquals("system_feedback", body.get("kind").getAsString()); assertFalse(body.has("target_id")); assertFalse(body.has("target_type"));
        assertEquals(report.diagnostics() + app.getString(R.string.crash_feedback_notes, "点击表格时闪退"), body.get("content").getAsString());
        assertFalse(body.has("debug_context")); assertEquals(2, body.size()); assertNull(store.pending());
        assertFalse(body.toString().contains("private document")); assertFalse(body.toString().contains("crash-fixture-session"));
    }
    @Test public void systemExitDiagnosticsAreSentInTheBodyWithoutOptionalNotes() throws Exception {
        report = CrashReport.systemExit(report.environment, "ReaderActivity",
                new CrashStore.Exit(report.timestamp, 123, android.app.ApplicationExitInfo.REASON_CRASH_NATIVE, app.getPackageName()));
        store.record(report); open(); describe("  \n  "); submit(); await(() -> fragment() == null);
        assertEquals(1, requests.size());
        Buffer buffer = new Buffer(); requests.get(0).body().writeTo(buffer);
        JsonObject body = new JsonParser().parse(buffer.readUtf8()).getAsJsonObject();
        assertEquals(report.diagnostics(), body.get("content").getAsString()); assertFalse(body.has("debug_context"));
    }
    @Test public void inlineDiagnosticsAndNotesEscapeReportMarkupAndPreserveLineBreaks() throws Exception {
        report = new CrashReport(report.id, report.kind,
                "java.lang.IllegalStateException\n  at fixture.Editor.render(Editor[1]&2.java:42)\n",
                report.screen, report.timestamp, report.environment);
        store.record(report); open(); describe("[b]点击表格[/b] & 换行\n再次编辑"); submit(); await(() -> fragment() == null);
        assertEquals(1, requests.size());
        Buffer buffer = new Buffer(); requests.get(0).body().writeTo(buffer);
        JsonObject body = new JsonParser().parse(buffer.readUtf8()).getAsJsonObject();
        String content = body.get("content").getAsString();
        assertTrue(content.contains("java.lang.IllegalStateException\n  at fixture.Editor.render(Editor&#91;1&#93;&amp;2.java:42)\n"));
        assertTrue(content.contains("&#91;b&#93;点击表格&#91;/b&#93; &amp; 换行\n再次编辑"));
        assertFalse(body.has("debug_context"));
    }
    @Test public void rotationKeepsOneInFlightSubmissionAndTheDescription() throws Exception {
        gate = new CountDownLatch(1); open(); describe("旋转前的说明"); CrashFeedbackViewModel before = model(); submit();
        await(() -> requests.size() == 1); submit(); before.submit(); assertFalse(fragment().isCancelable());
        Configuration next = new Configuration(activity.getResources().getConfiguration()); next.orientation = Configuration.ORIENTATION_LANDSCAPE;
        controller.configurationChange(next); activity = controller.get(); shadowOf(Looper.getMainLooper()).idle();
        assertSame(before, model()); assertEquals("旋转前的说明", model().description()); assertEquals(1, requests.size());
        assertFalse(dialog().getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled());
        gate.countDown(); await(() -> fragment() == null); assertEquals(1, requests.size()); assertNull(store.pending());
    }
    @Test public void guestAndExpiredSessionsRequireLoginAndNeverSubmitAutomatically() throws Exception {
        UserPreferences.saveToken(app, ""); open(); describe("说明"); submit();
        assertEquals(LoginActivity.class.getName(), shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        assertTrue(requests.isEmpty()); assertNotNull(store.pending());
        UserPreferences.saveToken(app, "new-session"); controller.pause().resume();
        assertFalse(model().needsLogin); assertEquals("说明", model().description()); assertTrue(requests.isEmpty());
        status = 401; submit(); await(() -> model().needsLogin);
        assertNotNull(store.pending()); assertEquals("", UserPreferences.getToken(app));
    }
    @Test public void rejectionKeepsTheReportAndAllowsExplicitRetry() throws Exception {
        status = 429; open(); describe("当时的操作"); submit(); await(() -> !model().submitting);
        assertEquals("请稍后重试", model().error); assertEquals("当时的操作", model().description()); assertNotNull(store.pending());
        assertFalse(store.pending().deliveryUncertain);
        status = 200; submit(); await(() -> fragment() == null); assertEquals(2, requests.size()); assertNull(store.pending());
    }
    @Test public void lostResponseAndProcessRestorationNeverReplayAPostOrClaimSuccess() throws Exception {
        loseResponse = true; open(); submit(); await(() -> !model().submitting);
        assertFalse(model().complete); assertEquals(activity.getString(R.string.crash_feedback_uncertain), model().error);
        assertTrue(new CrashStore(app).pending().deliveryUncertain);
        SavedStateHandle saved = new SavedStateHandle(); saved.set("crash_id", report.id);
        CrashFeedbackViewModel recreated = new CrashFeedbackViewModel(app, saved);
        assertFalse(recreated.submitting); assertFalse(recreated.complete);
        assertEquals(activity.getString(R.string.crash_feedback_uncertain), recreated.error); assertEquals(1, requests.size());
        loseResponse = false; emptyResponse = true; submit(); await(() -> !model().submitting);
        assertFalse(model().complete); assertNotNull(store.pending()); assertEquals(2, requests.size());
    }
    @Test public void accountChangeIgnoresTheOldRequestCompletion() throws Exception {
        gate = new CountDownLatch(1); open(); submit(); await(() -> requests.size() == 1);
        UserPreferences.saveToken(app, "different-session"); model().connect();
        assertFalse(model().submitting); assertFalse(model().error.isEmpty());
        gate.countDown(); shadowOf(Looper.getMainLooper()).idle();
        assertFalse(model().complete); assertNotNull(store.pending()); assertEquals(1, requests.size());
    }
    @Test public void aNewLaunchOffersFeedbackOnceAndWaitsForAForegroundPage() throws Exception {
        CrashFeedback feedback = new CrashFeedback(app, store, Runnable::run); feedback.start();
        shadowOf(Looper.getMainLooper()).idle(); assertNull(fragment());
        controller.pause().resume(); shadowOf(Looper.getMainLooper()).idle(); assertNotNull(fragment());
        dialog().cancel(); shadowOf(Looper.getMainLooper()).idle(); assertNull(fragment()); assertNull(store.pending());
        controller.pause().resume(); shadowOf(Looper.getMainLooper()).idle(); assertNull(fragment()); assertTrue(requests.isEmpty());
    }
}
