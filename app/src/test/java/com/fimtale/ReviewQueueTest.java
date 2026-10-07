package com.fimtale;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.fimtale.network.ApiDataConverter;
import com.fimtale.network.RetrofitClient;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.network.SiteUrls;
import com.fimtale.review.ReviewActionDialog;
import com.fimtale.review.ReviewEntry;
import com.fimtale.review.ReviewQueueViewModel;
import com.fimtale.review.ReviewQueueViewModel.Section;
import com.fimtale.ui.ProfileFragment;
import com.fimtale.utils.UserPreferences;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Author-side fixtures only: never reads or submits real review records. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ReviewQueueTest {
    private Context context;
    private Field service, factory;
    private Object originalApi;
    private ActivityController<ReviewQueueActivity> controller;
    private ReviewQueueActivity activity;
    private ReviewQueueViewModel model;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile int listStatus = 200, submissionStatus = 200;
    private volatile boolean lostSubmission, submitted, failRefresh, empty;
    private CountDownLatch submissionGate, listGate;
    private volatile String heldSession;

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("fimtale_session", 0).edit().clear().putString("token", "author-session")
                .putString("user_id", "9").putString("user_name", "普通作者").commit();
        factory = ViewModelProvider.AndroidViewModelFactory.class.getDeclaredField("sInstance");
        factory.setAccessible(true); factory.set(null, null);
        service = RetrofitClient.class.getDeclaredField("service"); service.setAccessible(true); originalApi = service.get(null);
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(chain -> {
            Request request = chain.request(); requests.add(request);
            String path = request.url().encodedPath(), data; int status = 200;
            if (path.endsWith("get_user")) data = "{\"id\":9,\"username\":\"普通作者\",\"avatar\":\"\"}";
            else if (path.endsWith("get_review_entries")) {
                if (request.header("Token").equals(heldSession)) gate(listGate);
                status = failRefresh && submitted ? 503 : listStatus;
                if (empty) data = "[]";
                else if ("second-author".equals(request.header("Token"))) data = "[" + entry(55, 999, 1, null) + "]";
                else data = "[" + entry(submitted ? 81 : 0, 101, submitted ? 1 : 0, null) + ","
                        + entry(42, 102, 3, null) + "," + entry(43, 103, 3, "2999-01-01T00:00:00Z") + ","
                        + entry(44, 104, 1, null) + "," + entry(45, 105, 2, null) + "]";
            } else if (path.endsWith("submit_review")) {
                gate(submissionGate);
                if (lostSubmission) throw new java.io.IOException("response lost");
                status = submissionStatus; if (status == 200) submitted = true;
                data = entry(81, 101, 1, null);
            } else throw new AssertionError("Unexpected endpoint (including any admin API): " + path);
            return new okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                    .body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + data + ",\"msg\":\"暂时不能提交该作品\"}")).build();
        }).build();
        service.set(null, new Retrofit.Builder().baseUrl(SiteUrls.API).client(client)
                .callbackExecutor(command -> new Handler(Looper.getMainLooper()).post(command))
                .addConverterFactory(new ApiDataConverter()).addConverterFactory(GsonConverterFactory.create()).build().create(FimTaleApiService.class));
    }
    private static void gate(CountDownLatch gate) throws java.io.IOException {
        if (gate != null) try { gate.await(8, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new java.io.IOException(e); }
    }
    private static String entry(int id, int work, int status, String after) {
        return "{\"id\":" + id + ",\"work_id\":" + work + ",\"work_title\":\"我的作品 " + work + "\",\"status\":" + status
                + ",\"created_at\":\"2026-10-01T10:00:00Z\",\"updated_at\":\"2026-10-02T10:00:00Z\","
                + "\"payload\":{\"state_reason\":\"请补充作品来源\",\"resubmit_after\":" + (after == null ? "null" : "\"" + after + "\"") + "},"
                + "\"assignments\":[{\"reviewer_user_id\":10,\"reviewer_name\":\"不应显示的审核员\"}]}";
    }
    private void launch() throws Exception { launch(new Intent(context, ReviewQueueActivity.class), null); }
    private void launch(Intent intent, Bundle state) throws Exception {
        controller = Robolectric.buildActivity(ReviewQueueActivity.class, intent); controller.get().setTheme(R.style.Theme_Fimtale);
        activity = (state == null ? controller.setup() : controller.setup(state)).get();
        model = new ViewModelProvider(activity).get(ReviewQueueViewModel.class); await(() -> !model.loading);
    }
    @After public void cleanup() throws Exception {
        if (submissionGate != null) submissionGate.countDown();
        if (listGate != null) listGate.countDown();
        if (controller != null) controller.pause().stop().destroy();
        service.set(null, originalApi); factory.set(null, null);
    }
    private void await(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); if (ready.getAsBoolean()) return; Thread.sleep(10);
        }
        fail("Timed out waiting for author review fixture");
    }
    private List<Request> calls(String end) {
        List<Request> result = new java.util.ArrayList<>();
        for (Request request : requests) if (request.url().encodedPath().endsWith(end)) result.add(request);
        return result;
    }
    private JsonObject body(Request request) throws Exception {
        Buffer buffer = new Buffer(); request.body().writeTo(buffer); return new JsonParser().parse(buffer.readUtf8()).getAsJsonObject();
    }
    private AlertDialog dialog() {
        shadowOf(Looper.getMainLooper()).idle();
        ReviewActionDialog fragment = (ReviewActionDialog) activity.getSupportFragmentManager().findFragmentByTag(ReviewActionDialog.TAG);
        assertNotNull(fragment); return (AlertDialog) fragment.requireDialog();
    }
    private void rotate() {
        Configuration next = new Configuration(activity.getResources().getConfiguration()); next.orientation = Configuration.ORIENTATION_LANDSCAPE;
        ReviewQueueActivity before = activity; controller.configurationChange(next); activity = controller.get();
        assertNotSame(before, activity); assertSame(model, new ViewModelProvider(activity).get(ReviewQueueViewModel.class));
    }
    @Test public void ordinaryAuthorUsesPersonalUnpaginatedEndpointAndKeepsZeroIdWorks() throws Exception {
        launch(); assertEquals(5, model.reviews.size()); assertEquals(0, model.reviews.get(0).id);
        assertTrue(model.canSubmit(model.reviews.get(0)));
        Request request = calls("get_review_entries").get(0);
        assertEquals("GET", request.method()); assertEquals("author-session", request.header("Token"));
        assertNull(request.url().query()); assertNull(request.body());
        assertEquals("我的审核", activity.getString(R.string.review_queue_title));
        assertEquals(1, requests.size());
    }
    @Test public void entriesGroupByStatusAndResubmissionDeadline() throws Exception {
        launch(); long now = System.currentTimeMillis();
        assertEquals(Section.READY, ReviewQueueViewModel.section(model.reviews.get(0), now));
        assertEquals(Section.READY, ReviewQueueViewModel.section(model.reviews.get(1), now));
        assertEquals(Section.READY, ReviewQueueViewModel.section(model.reviews.get(2), now));
        assertEquals(Section.PENDING, ReviewQueueViewModel.section(model.reviews.get(3), now));
        assertEquals(Section.COMPLETED, ReviewQueueViewModel.section(model.reviews.get(4), now));
        assertFalse(model.canSubmit(model.reviews.get(2))); assertFalse(model.canSubmit(model.reviews.get(3)));
        assertFalse(model.canSubmit(model.reviews.get(4)));
        ReviewEntry rejected = model.reviews.get(2);
        rejected.payload.resubmitAfter = "2026-10-06T11:30:00+08:00";
        long deadline = Instant.parse("2026-10-06T03:30:00Z").toEpochMilli();
        assertFalse(rejected.canSubmit(deadline)); assertTrue(rejected.canSubmit(deadline + 1));
        rejected.payload.resubmitAfter = "invalid-date"; assertFalse(rejected.canSubmit(now));
    }
    @Test public void submissionRequiresConfirmationAndRotationNeverRepeatsTheRequest() throws Exception {
        submissionGate = new CountDownLatch(1); launch(); model.open(model.reviews.get(0));
        assertEquals(0, calls("submit_review").size());
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); model.confirm();
        await(() -> calls("submit_review").size() == 1); rotate();
        assertFalse(dialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        assertFalse(dialog().getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled());
        submissionGate.countDown(); await(() -> !model.mutating && !model.loading);
        JsonObject input = body(calls("submit_review").get(0));
        assertEquals(1, input.size()); assertEquals(101, input.get("work_id").getAsInt());
        assertEquals("POST", calls("submit_review").get(0).method()); assertEquals("author-session", calls("submit_review").get(0).header("Token"));
        assertEquals(1, calls("submit_review").size()); assertEquals(2, calls("get_review_entries").size());
        assertNull(model.selected); assertEquals(ReviewEntry.PENDING, model.reviews.get(0).status);
    }
    @Test public void rejectedWorkCanBeResubmittedAndCancelledWithoutWriting() throws Exception {
        launch(); model.open(model.reviews.get(1));
        assertEquals("重新提交", dialog().getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle();
        assertNull(model.selected); assertTrue(calls("submit_review").isEmpty());
        model.open(model.reviews.get(2)); assertNull(model.selected);
        model.open(model.reviews.get(1)); dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        await(() -> !model.mutating && !model.loading);
        assertEquals(102, body(calls("submit_review").get(0)).get("work_id").getAsInt());
    }
    @Test public void serverErrorStaysInConfirmationAndAllowsExplicitRetry() throws Exception {
        submissionStatus = 409; launch(); model.open(model.reviews.get(0));
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); await(() -> !model.mutating);
        assertEquals("暂时不能提交该作品", model.dialogError); assertTrue(dialog().isShowing()); assertTrue(model.canConfirm());
        submissionStatus = 200; dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        await(() -> !model.mutating && !model.loading); assertEquals(2, calls("submit_review").size()); assertNull(model.selected);
    }
    @Test public void lostSubmissionResponseRequiresRefreshBeforeRetry() throws Exception {
        lostSubmission = true; launch(); model.open(model.reviews.get(0));
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); await(() -> !model.mutating);
        assertTrue(model.uncertain); assertFalse(model.canConfirm()); model.confirm(); assertEquals(1, calls("submit_review").size());
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle();
        model.open(model.reviews.get(0)); assertNull(model.selected);
        model.refresh(); await(() -> !model.loading); assertFalse(model.uncertain); assertTrue(model.canSubmit(model.reviews.get(0)));
    }
    @Test public void successfulSubmissionCannotRepeatIfRefreshingTheListFails() throws Exception {
        failRefresh = true; launch(); model.open(model.reviews.get(0));
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); await(() -> !model.mutating && !model.loading);
        assertFalse(model.error.isEmpty()); assertEquals(ReviewEntry.PENDING, model.reviews.get(0).status);
        assertFalse(model.canSubmit(model.reviews.get(0))); assertEquals(1, calls("submit_review").size());
    }
    @Test public void guestAndExpiredSessionsCannotKeepAuthorRecords() throws Exception {
        launch(); listStatus = 401; model.refresh(); await(() -> !model.loading);
        assertTrue(model.needsLogin); assertTrue(model.reviews.isEmpty()); assertFalse(UserPreferences.isLoggedIn(context));
        int count = requests.size(); model.refresh(); assertEquals(count, requests.size());
    }
    @Test public void switchingAccountsDiscardsLateOldAccountResults() throws Exception {
        launch(); listGate = new CountDownLatch(1); heldSession = "author-session";
        model.refresh(); await(() -> calls("get_review_entries").size() == 2);
        context.getSharedPreferences("fimtale_session", 0).edit().putString("token", "second-author").commit();
        model.connect(); await(() -> !model.loading);
        assertEquals(1, model.reviews.size()); assertEquals(999, model.reviews.get(0).workId);
        listGate.countDown(); shadowOf(Looper.getMainLooper()).idle();
        assertEquals(999, model.reviews.get(0).workId); assertTrue(calls("submit_review").isEmpty());
    }
    @Test public void deepLinkOnlyHighlightsAnEntryFromThePersonalList() throws Exception {
        launch(new Intent(context, ReviewQueueActivity.class).putExtra(ReviewQueueActivity.EXTRA_REVIEW_ID, 43), null);
        assertTrue(activity.isHighlighted(model.reviews.get(2))); assertFalse(activity.isHighlighted(model.reviews.get(0)));
        assertNull(calls("get_review_entries").get(0).url().query());
    }
    @Test public void emptyResponseAndProcessRestorationNeverReplaySubmission() throws Exception {
        launch(); model.open(model.reviews.get(0)); dialog();
        Bundle state = new Bundle(); controller.pause().saveInstanceState(state).stop().destroy(); controller = null;
        empty = true; launch(new Intent(context, ReviewQueueActivity.class), state);
        assertTrue(model.loaded); assertTrue(model.reviews.isEmpty()); assertNull(model.selected); assertTrue(calls("submit_review").isEmpty());
    }
    @Test public void profileShowsMyReviewsForAnOrdinaryLoggedInAuthorOnTheSecondRow() throws Exception {
        try (ActivityController<AppCompatActivity> host = Robolectric.buildActivity(AppCompatActivity.class)) {
            host.get().setTheme(R.style.Theme_Fimtale); AppCompatActivity screen = host.setup().get();
            ProfileFragment profile = new ProfileFragment();
            screen.getSupportFragmentManager().beginTransaction().add(android.R.id.content, profile).commitNow();
            View entry = screen.findViewById(R.id.btnReviewQueue);
            assertEquals(View.VISIBLE, entry.getVisibility());
            View actions = (View) entry.getParent();
            int width = Math.round(360 * context.getResources().getDisplayMetrics().density);
            actions.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            actions.layout(0, 0, width, actions.getMeasuredHeight());
            View publish = screen.findViewById(R.id.btnPublish);
            assertEquals(publish.getTop(), screen.findViewById(R.id.btnMessages).getTop());
            assertTrue(entry.getTop() > publish.getBottom());
            assertEquals(publish.getLeft(), entry.getLeft());
            assertEquals(width / 4f, entry.getWidth(), 1f);
            assertEquals(entry.getWidth(), screen.findViewById(R.id.btnMyReports).getWidth());
            entry.performClick();
            assertEquals(ReviewQueueActivity.class.getName(), shadowOf(screen).getNextStartedActivity().getComponent().getClassName());
            UserPreferences.clearSession(context); host.pause().resume(); assertEquals(View.GONE, entry.getVisibility());
            assertTrue(calls("get_user_auth").isEmpty());
        }
    }
}
