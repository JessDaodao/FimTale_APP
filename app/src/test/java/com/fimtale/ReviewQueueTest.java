package com.fimtale;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.fimtale.network.ApiDataConverter;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.network.RetrofitClient;
import com.fimtale.network.SiteUrls;
import com.fimtale.review.ReviewActionDialog;
import com.fimtale.review.ReviewEntry;
import com.fimtale.review.ReviewQueueViewModel;
import com.fimtale.review.ReviewQueueViewModel.Action;
import com.fimtale.review.ReviewQueueViewModel.Mode;
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

/** In-process API fixtures: no real queue entries are read, resolved or reassigned. */
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
    private volatile int role = 2, count = 1, mutationStatus = 200;
    private volatile boolean lostMutation, expiredSession;
    private CountDownLatch mutationGate;

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("fimtale_session", 0).edit().clear().putString("token", "fixture-session")
                .putString("user_id", "9").putString("user_name", "测试编辑").commit();
        factory = ViewModelProvider.AndroidViewModelFactory.class.getDeclaredField("sInstance"); factory.setAccessible(true); factory.set(null, null);
        service = RetrofitClient.class.getDeclaredField("service"); service.setAccessible(true); originalApi = service.get(null);
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(chain -> {
            Request request = chain.request(); requests.add(request);
            String path = request.url().encodedPath(); String data; int status = 200;
            if (path.endsWith("get_user_auth")) data = "{\"user_id\":9,\"role_id\":" + role + "}";
            else if (path.endsWith("get_user")) data = "{\"id\":9,\"username\":\"测试编辑\",\"avatar\":\"\"}";
            else if (path.endsWith("get_reviews")) {
                if (expiredSession) { UserPreferences.clearSession(context); status = 401; data = "null"; }
                else {
                    StringBuilder items = new StringBuilder("[");
                    int size = "2".equals(request.url().queryParameter("page")) ? 1 : count;
                    String filter = request.url().queryParameter("status");
                    for (int i = 0; i < size; i++) {
                        if (i > 0) items.append(',');
                        items.append(entry(41 + i, filter == null ? 2 : Integer.parseInt(filter)));
                    }
                    data = items.append(']').toString();
                }
            } else if (path.endsWith("get_username_by_id")) data = "\"处理编辑\"";
            else if (path.endsWith("get_team_members")) data = "[{\"user_id\":5,\"username\":\"普通用户\",\"role_id\":1},"
                    + "{\"user_id\":10,\"username\":\"编辑甲\",\"role_id\":2},{\"user_id\":11,\"username\":\"编辑乙\",\"role_id\":4}]";
            else if (path.endsWith("resolve_review") || path.endsWith("set_review_assignments")) {
                if (mutationGate != null) try { mutationGate.await(8, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new java.io.IOException(e); }
                if (lostMutation) throw new java.io.IOException("response lost");
                status = mutationStatus; data = path.endsWith("set_review_assignments") ? "[]" : entry(41, 2);
            } else throw new AssertionError("Unexpected request: " + path);
            return new okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                    .body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + data + ",\"msg\":\"测试：该记录已由其他审核员处理\"}")).build();
        }).build();
        service.set(null, new Retrofit.Builder().baseUrl(SiteUrls.API).client(client)
                .callbackExecutor(command -> new Handler(Looper.getMainLooper()).post(command))
                .addConverterFactory(new ApiDataConverter()).addConverterFactory(GsonConverterFactory.create()).build().create(FimTaleApiService.class));
    }
    private static String entry(int id, int status) {
        return "{\"id\":" + id + ",\"work_id\":71323,\"work_title\":\"测试作品\",\"status\":" + status
                + ",\"created_at\":\"2026-10-01T10:00:00Z\",\"updated_at\":\"2026-10-02T10:00:00Z\","
                + "\"previous_review_count\":2,\"payload\":{\"resolver_user_id\":12,\"state_reason\":\"测试退回原因\"},"
                + "\"assignments\":[{\"reviewer_user_id\":10,\"reviewer_name\":\"编辑甲\"}]}";
    }
    private void launch() throws Exception { launch(new Intent(context, ReviewQueueActivity.class), null); }
    private void launch(Intent intent, Bundle state) throws Exception {
        controller = Robolectric.buildActivity(ReviewQueueActivity.class, intent); controller.get().setTheme(R.style.Theme_Fimtale);
        activity = (state == null ? controller.setup() : controller.setup(state)).get();
        model = new ViewModelProvider(activity).get(ReviewQueueViewModel.class);
        await(() -> !model.loading);
    }
    @After public void cleanup() throws Exception {
        if (mutationGate != null) mutationGate.countDown();
        if (controller != null) controller.pause().stop().destroy();
        service.set(null, originalApi); factory.set(null, null);
    }
    private void await(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle(); if (ready.getAsBoolean()) return; Thread.sleep(10);
        }
        fail("Timed out waiting for queue fixture");
    }
    private List<Request> calls(String end) {
        List<Request> result = new java.util.ArrayList<>();
        for (Request r : requests) if (r.url().encodedPath().endsWith(end)) result.add(r);
        return result;
    }
    private Request lastList() { List<Request> list = calls("get_reviews"); return list.get(list.size() - 1); }
    private JsonObject body(Request request) throws Exception {
        Buffer buffer = new Buffer(); request.body().writeTo(buffer); return new JsonParser().parse(buffer.readUtf8()).getAsJsonObject();
    }
    private AlertDialog dialog() {
        shadowOf(Looper.getMainLooper()).idle();
        ReviewActionDialog dialog = (ReviewActionDialog) activity.getSupportFragmentManager().findFragmentByTag(ReviewActionDialog.TAG);
        assertNotNull(dialog); return (AlertDialog) dialog.requireDialog();
    }
    private void open(Action action) { model.open(action, model.reviews.get(0)); dialog(); }
    private void rotate() {
        Configuration next = new Configuration(activity.getResources().getConfiguration()); next.orientation = Configuration.ORIENTATION_LANDSCAPE;
        ReviewQueueActivity before = activity; controller.configurationChange(next); activity = controller.get();
        assertNotSame(before, activity); assertSame(model, new ViewModelProvider(activity).get(ReviewQueueViewModel.class));
    }

    @Test public void assignedQueueUsesCurrentEditorAndReviewRecordStatus() throws Exception {
        launch(); assertTrue(model.canReview()); assertFalse(model.canAssign()); assertEquals(1, model.reviews.size());
        Request request = lastList();
        assertEquals("GET", request.method()); assertEquals("fixture-session", request.header("Token"));
        assertEquals("9", request.url().queryParameter("assigned_user_id"));
        assertEquals("1", request.url().queryParameter("status")); assertEquals("20", request.url().queryParameter("per_page"));
        assertNull(request.body());
        model.open(Action.ASSIGN, model.reviews.get(0)); assertEquals(Action.NONE, model.action); assertEquals(0, calls("get_team_members").size());
    }
    @Test public void commonUsersCannotFetchOrResolveReviews() throws Exception {
        role = 1; launch(); assertFalse(model.canReview()); assertTrue(model.reviews.isEmpty());
        assertEquals(0, calls("get_reviews").size()); assertEquals(activity.getString(R.string.review_no_permission), model.error);
        model.confirm(); assertEquals(0, calls("resolve_review").size());
    }
    @Test public void modesStatusAndPaginationBuildIndependentQueries() throws Exception {
        count = 20; launch(); assertTrue(model.hasNext);
        model.nextPage(); await(() -> !model.loading); assertEquals(2, model.page); assertFalse(model.hasNext);
        assertEquals("2", lastList().url().queryParameter("page"));
        model.chooseMode(Mode.PENDING); await(() -> !model.loading); assertEquals(1, model.page);
        assertNull(lastList().url().queryParameter("assigned_user_id")); assertEquals("1", lastList().url().queryParameter("status"));
        ((com.google.android.material.tabs.TabLayout) activity.findViewById(R.id.reviewTabs)).getTabAt(2).select();
        await(() -> !model.loading); assertNull(lastList().url().queryParameter("status"));
        assertFalse(model.canAct(model.reviews.get(0)));
        activity.findViewById(R.id.reviewFilterRejected).performClick(); await(() -> !model.loading);
        assertEquals("3", lastList().url().queryParameter("status")); assertNull(lastList().url().queryParameter("assigned_user_id"));
        activity.findViewById(R.id.reviewFilterAll).performClick(); await(() -> !model.loading);
        assertNull(lastList().url().queryParameter("status"));
        await(() -> model.names.containsKey(12)); assertEquals("处理编辑", model.names.get(12));
    }
    @Test public void workHistoryFilterAndSingleRecordOverrideArePreserved() throws Exception {
        launch(new Intent(context, ReviewQueueActivity.class).putExtra(ReviewQueueActivity.EXTRA_WORK_ID, 71323), null);
        assertEquals(Mode.HISTORY, model.mode); assertEquals("71323", lastList().url().queryParameter("work_id"));
        model.reviewFilter = 41; model.chooseMode(Mode.ASSIGNED); await(() -> !model.loading);
        assertEquals("41", lastList().url().queryParameter("review_id"));
        assertNull(lastList().url().queryParameter("assigned_user_id")); assertNull(lastList().url().queryParameter("status"));
        assertNull(lastList().url().queryParameter("work_id"));
        model.clearFilter(); await(() -> !model.loading); assertNull(lastList().url().queryParameter("review_id"));
    }
    @Test public void passRequiresConfirmationAndRotationDoesNotRepeatMutation() throws Exception {
        mutationGate = new CountDownLatch(1); launch(); open(Action.PASS);
        assertEquals(0, calls("resolve_review").size());
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); model.confirm();
        await(() -> calls("resolve_review").size() == 1); rotate();
        assertFalse(dialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled()); assertFalse(dialog().getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled());
        mutationGate.countDown(); await(() -> !model.mutating && !model.loading);
        JsonObject payload = body(calls("resolve_review").get(0)); assertEquals(41, payload.get("review_id").getAsInt());
        assertEquals(2, payload.get("status").getAsInt()); assertFalse(payload.has("reason")); assertFalse(payload.has("resubmit_after"));
        assertEquals(1, calls("resolve_review").size()); assertEquals(2, calls("get_reviews").size()); assertEquals(Action.NONE, model.action);
    }
    @Test public void rejectRetainsDraftAcrossRotationAndSendsReasonAndUtcSchedule() throws Exception {
        launch(); open(Action.REJECT);
        ((EditText) dialog().findViewById(R.id.reviewReasonInput)).setText("  请补充来源  ");
        long after = Instant.parse("2026-10-06T03:30:00Z").toEpochMilli(); model.setResubmitAfter(after); rotate();
        assertEquals("  请补充来源  ", ((EditText) dialog().findViewById(R.id.reviewReasonInput)).getText().toString());
        assertEquals(Long.valueOf(after), model.resubmitAfter);
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); await(() -> !model.mutating && !model.loading);
        JsonObject payload = body(calls("resolve_review").get(0));
        assertEquals(3, payload.get("status").getAsInt()); assertEquals("请补充来源", payload.get("reason").getAsString());
        assertEquals("2026-10-06T03:30:00Z", payload.get("resubmit_after").getAsString());
    }
    @Test public void cancelAndServerRejectionNeverDiscardDialogDraftSilently() throws Exception {
        launch(); open(Action.REJECT); dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle(); assertEquals(0, calls("resolve_review").size()); assertEquals(Action.NONE, model.action);
        mutationStatus = 409; open(Action.REJECT); ((EditText) dialog().findViewById(R.id.reviewReasonInput)).setText("保留原因");
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); await(() -> !model.mutating);
        assertEquals(Action.REJECT, model.action); assertEquals("保留原因", model.reason);
        assertEquals("测试：该记录已由其他审核员处理", model.dialogError); assertTrue(dialog().isShowing());
    }
    @Test public void onlyManagersCanAssignAndEligibleReviewersAreLoaded() throws Exception {
        role = 4; launch(); assertTrue(model.canAssign()); open(Action.ASSIGN); await(() -> !model.loadingReviewers);
        assertEquals(2, model.reviewers.size()); assertEquals(10, model.reviewers.get(0).userId);
        assertTrue(model.selectedReviewers.contains(10)); model.selectedReviewers.clear(); model.selectedReviewers.add(11);
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); await(() -> !model.mutating && !model.loading);
        JsonObject payload = body(calls("set_review_assignments").get(0));
        assertEquals(41, payload.get("review_id").getAsInt()); assertEquals("[11]", payload.get("reviewer_user_ids").toString());
    }
    @Test public void lostMutationResponseRequiresRefreshBeforeAnotherDecision() throws Exception {
        lostMutation = true; launch(); open(Action.PASS); dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        await(() -> !model.mutating); assertTrue(model.uncertain); assertFalse(model.canConfirm());
        model.confirm(); assertEquals(1, calls("resolve_review").size());
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); shadowOf(Looper.getMainLooper()).idle();
        model.open(Action.PASS, model.reviews.get(0)); assertEquals(Action.NONE, model.action);
        model.refresh(); await(() -> !model.loading); assertFalse(model.uncertain);
    }
    @Test public void sessionExpiryDuringApiCallbackClearsQueueAndStopsLoading() throws Exception {
        launch(); expiredSession = true; model.refresh(); await(() -> !model.loading);
        assertTrue(model.needsLogin); assertFalse(model.canReview()); assertTrue(model.reviews.isEmpty());
    }
    @Test public void processRestorationKeepsFiltersAndPageButDoesNotReplayDecision() throws Exception {
        count = 20; launch(); model.chooseMode(Mode.PENDING); await(() -> !model.loading);
        model.nextPage(); await(() -> !model.loading); open(Action.REJECT);
        Bundle state = new Bundle(); controller.pause().saveInstanceState(state).stop().destroy(); controller = null;
        launch(new Intent(context, ReviewQueueActivity.class), state);
        assertEquals(Mode.PENDING, model.mode); assertEquals(2, model.page); assertEquals(Action.NONE, model.action);
        assertEquals(0, calls("resolve_review").size());
    }
    @Test public void profileShowsQueueEntryOnlyAfterReviewPermissionIsConfirmed() throws Exception {
        try (ActivityController<AppCompatActivity> host = Robolectric.buildActivity(AppCompatActivity.class)) {
            host.get().setTheme(R.style.Theme_Fimtale); AppCompatActivity screen = host.setup().get();
            ProfileFragment profile = new ProfileFragment();
            screen.getSupportFragmentManager().beginTransaction().add(android.R.id.content, profile).commitNow();
            await(() -> screen.findViewById(R.id.btnReviewQueue).getVisibility() == View.VISIBLE);
            screen.findViewById(R.id.btnReviewQueue).performClick();
            assertEquals(ReviewQueueActivity.class.getName(), shadowOf(screen).getNextStartedActivity().getComponent().getClassName());
            role = 1; host.pause().resume(); await(() -> calls("get_user_auth").size() == 2);
            shadowOf(Looper.getMainLooper()).idle(); assertEquals(View.GONE, screen.findViewById(R.id.btnReviewQueue).getVisibility());
        }
    }
}
