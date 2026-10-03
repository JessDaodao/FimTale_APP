package com.app.fimtale;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.widget.EditText;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.app.fimtale.editor.EditorDraftStore;
import com.app.fimtale.editor.EditorViewModel;
import com.app.fimtale.network.ApiDataConverter;
import com.app.fimtale.network.FimTaleApiService;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.network.SiteUrls;
import com.app.fimtale.utils.UserPreferences;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

/** All API traffic terminates in an in-process fixture. Never publishes to the real site. */
@RunWith(AndroidJUnit4.class)
public class EditorActivityTest {
    private Context context;
    private Object originalApi;
    private final java.util.concurrent.ConcurrentHashMap<String, com.app.fimtale.editor.OnlineDraft> cloud = new java.util.concurrent.ConcurrentHashMap<>();
    private Map<String, ?> originalSession;
    private Field service;
    private final AtomicInteger writes = new AtomicInteger();
    private final AtomicReference<JsonObject> payload = new AtomicReference<>();
    private final CountDownLatch submitted = new CountDownLatch(1), responseGate = new CountDownLatch(1);
    private volatile boolean blockResponse, loseResponse;
    private volatile int responseCode = 200, owner = 9;
    private static final String USER = "9";

    @Before public void setup() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences session = context.getSharedPreferences("fimtale_session", Context.MODE_PRIVATE);
        originalSession = session.getAll();
        session.edit().clear().putString("user_id", USER).putString("user_name", "编辑器测试")
                .putString("token", "fixture-only-token").commit();
        for (int chapter : new int[]{-1, 0, 81}) draft(chapter).delete();
        service = RetrofitClient.class.getDeclaredField("service"); service.setAccessible(true); originalApi = service.get(null);
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(chain -> {
            String path = chain.request().url().encodedPath(); String data; int code = 200;
            if (path.endsWith("get_draft")) {
                com.app.fimtale.editor.OnlineDraft draft = cloud.get(chain.request().url().queryParameter("draft_key"));
                code = draft == null ? 404 : 200; data = new com.google.gson.Gson().toJson(draft);
            } else if (path.endsWith("save_draft")) {
                Buffer b = new Buffer(); chain.request().body().writeTo(b);
                JsonObject input = new JsonParser().parse(b.readUtf8()).getAsJsonObject();
                String key = input.get("draft_key").getAsString();
                com.app.fimtale.editor.OnlineDraft old = cloud.get(key);
                long revision = old == null ? 0 : old.revision;
                if (input.get("expected_revision").getAsLong() != revision) { code = 409; data = "null"; }
                else {
                    com.app.fimtale.editor.OnlineDraft saved = new com.app.fimtale.editor.OnlineDraft();
                    saved.key = key; saved.revision = revision + 1; saved.payload = input.getAsJsonObject("payload");
                    saved.updatedAt = "2026-10-03T00:00:00Z"; cloud.put(key, saved); data = new com.google.gson.Gson().toJson(saved);
                }
            } else if (path.endsWith("delete_draft")) {
                Buffer b = new Buffer(); chain.request().body().writeTo(b);
                JsonObject input = new JsonParser().parse(b.readUtf8()).getAsJsonObject();
                String key = input.get("draft_key").getAsString();
                com.app.fimtale.editor.OnlineDraft old = cloud.get(key);
                if (old != null && old.revision != input.get("expected_revision").getAsLong()) code = 409;
                else cloud.remove(key);
                data = "null";
            } else if (path.endsWith("get_user_auth")) data = "{\"user_id\":9,\"role_id\":1,\"qualify_status\":2,\"space_status\":1}";
            else if (path.endsWith("get_work")) data = "{\"work\":{\"id\":42,\"title\":\"原始标题\",\"preface\":\"[b]原始序言[/b]\",\"intro\":\"简介\","
                    + "\"type\":1,\"length\":3,\"rating\":2,\"origin\":2,\"publish\":1,\"cover\":\"/cover.png\",\"prequel_id\":3,"
                    + "\"origin_link\":\"https://source.example/original\",\"user\":{\"user_id\":" + owner + "},"
                    + "\"tags\":[{\"name\":\"角色\",\"tags\":[{\"id\":5,\"name\":\"暮光闪闪\"}]}]}}";
            else if (path.endsWith("get_chapter")) data = "{\"chapter\":{\"id\":81,\"work_id\":42,\"title\":\"原始章节\",\"content\":\"[i]原始正文[/i]\"}}";
            else if (path.endsWith("create_update_chapter")) {
                assertEquals("fixture-only-token", chain.request().header("Token"));
                Buffer buffer = new Buffer(); chain.request().body().writeTo(buffer);
                payload.set(new JsonParser().parse(buffer.readUtf8()).getAsJsonObject());
                writes.incrementAndGet(); submitted.countDown();
                if (blockResponse) try { responseGate.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new IOException(e); }
                if (loseResponse) throw new IOException("fixture: response lost");
                code = responseCode; data = code == 200 ? "{\"id\":81}" : "null";
            } else { code = 404; data = "null"; }
            return new okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                    .body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + data + ",\"msg\":\"测试响应\"}")).build();
        }).build();
        service.set(null, new Retrofit.Builder().baseUrl(SiteUrls.API).client(client).addConverterFactory(new ApiDataConverter())
                .addConverterFactory(GsonConverterFactory.create()).build().create(FimTaleApiService.class));
    }
    @After public void cleanup() throws Exception {
        responseGate.countDown();
        service.set(null, originalApi);
        SharedPreferences.Editor editor = context.getSharedPreferences("fimtale_session", Context.MODE_PRIVATE).edit().clear();
        for (Map.Entry<String, ?> entry : originalSession.entrySet()) {
            if (entry.getValue() instanceof String) editor.putString(entry.getKey(), (String) entry.getValue());
        }
        editor.commit();
        for (int chapter : new int[]{-1, 0, 81}) draft(chapter).delete();
    }
    private EditorDraftStore draft(int chapter) {
        return new EditorDraftStore(new File(context.getNoBackupFilesDir(), "editor_drafts"), SiteUrls.API, USER, 42, chapter);
    }
    private EditorViewModel ready(ActivityScenario<EditorActivity> scenario) {
        AtomicReference<EditorViewModel> model = new AtomicReference<>();
        scenario.onActivity(a -> model.set(new ViewModelProvider(a).get(EditorViewModel.class)));
        waitFor(() -> model.get().ready && !model.get().busy); return model.get();
    }
    private void waitFor(BooleanSupplier condition) {
        long until = SystemClock.uptimeMillis() + 10000;
        while (!condition.getAsBoolean() && SystemClock.uptimeMillis() < until) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync(); SystemClock.sleep(50);
        }
        assertTrue("Timed out waiting for editor state", condition.getAsBoolean());
    }
    private void save(ActivityScenario<EditorActivity> scenario, EditorViewModel model) throws Exception {
        CountDownLatch done = new CountDownLatch(1); scenario.onActivity(a -> model.saveDraft(done::countDown));
        assertTrue(done.await(5, TimeUnit.SECONDS));
    }
    @Test public void workDraftRetainsRawContentMetadataAndTagsAcrossRotationAndReopen() throws Exception {
        try (ActivityScenario<EditorActivity> scenario = ActivityScenario.launch(EditorActivity.workIntent(context, 42))) {
            EditorViewModel model = ready(scenario);
            scenario.onActivity(a -> {
                assertEquals("[b]原始序言[/b]", ((EditText) a.findViewById(R.id.editorBody)).getText().toString());
                ((EditText) a.findViewById(R.id.editorTitle)).setText("修改标题");
                ((EditText) a.findViewById(R.id.editorBody)).setText("[b]未完成正文🐴[/b]");
            });
            scenario.recreate();
            scenario.onActivity(a -> assertSame(model, new ViewModelProvider(a).get(EditorViewModel.class)));
            save(scenario, model);
        }
        try (ActivityScenario<EditorActivity> scenario = ActivityScenario.launch(EditorActivity.workIntent(context, 42))) {
            EditorViewModel model = ready(scenario);
            scenario.onActivity(a -> {
                assertEquals("修改标题", ((EditText) a.findViewById(R.id.editorTitle)).getText().toString());
                assertEquals("[b]未完成正文🐴[/b]", ((EditText) a.findViewById(R.id.editorBody)).getText().toString());
                assertEquals(3, model.document.work.length); assertEquals(2, model.document.work.origin);
                assertEquals("/cover.png", model.document.work.cover); assertEquals("3", model.document.prequelText);
                assertEquals("暮光闪闪", model.document.tags.get(5));
            });
        }
        assertEquals(0, writes.get());
    }
    @Test public void chapterSubmissionSurvivesRotationWithoutDuplicateWrites() throws Exception {
        blockResponse = true;
        try (ActivityScenario<EditorActivity> scenario = ActivityScenario.launch(EditorActivity.chapterIntent(context, 42, 81))) {
            EditorViewModel model = ready(scenario);
            scenario.onActivity(a -> {
                ((EditText) a.findViewById(R.id.editorBody)).setText("更新后的正文"); a.findViewById(R.id.editorSubmit).performClick();
            });
            assertTrue(submitted.await(5, TimeUnit.SECONDS)); scenario.recreate();
            scenario.onActivity(a -> { assertTrue(model.busy); a.findViewById(R.id.editorSubmit).performClick(); });
            assertEquals(1, writes.get()); responseGate.countDown();
            waitFor(() -> model.finished && !model.busy);
            assertNull(draft(81).read());
            assertEquals(81, payload.get().get("id").getAsInt()); assertEquals(42, payload.get().get("work_id").getAsInt());
            assertEquals("更新后的正文", payload.get().get("content").getAsString());
        }
    }
    @Test public void rejectedSubmissionKeepsDraftAndShowsError() throws Exception {
        responseCode = 403;
        try (ActivityScenario<EditorActivity> scenario = ActivityScenario.launch(EditorActivity.chapterIntent(context, 42, 81))) {
            EditorViewModel model = ready(scenario);
            scenario.onActivity(a -> { ((EditText) a.findViewById(R.id.editorBody)).setText("保留的修改"); a.findViewById(R.id.editorSubmit).performClick(); });
            assertTrue(submitted.await(5, TimeUnit.SECONDS)); waitFor(() -> !model.busy && model.error);
            assertFalse(model.document.submissionUncertain); assertFalse(model.finished); save(scenario, model);
            assertEquals("保留的修改", draft(81).read().chapter.content);
        }
    }
    @Test public void lostCreateResponseRequiresCheckingBeforeRetryAndSurvivesReopen() throws Exception {
        loseResponse = true;
        android.content.Intent draftIntent = EditorActivity.chapterIntent(context, 42, 0).putExtra(EditorActivity.EXTRA_DRAFT_ID, "");
        try (ActivityScenario<EditorActivity> scenario = ActivityScenario.launch(draftIntent)) {
            EditorViewModel model = ready(scenario);
            scenario.onActivity(a -> {
                ((EditText) a.findViewById(R.id.editorTitle)).setText("新章节");
                ((EditText) a.findViewById(R.id.editorBody)).setText("不能重复发表"); a.findViewById(R.id.editorSubmit).performClick();
            });
            assertTrue(submitted.await(5, TimeUnit.SECONDS)); waitFor(() -> !model.busy && model.error);
            assertTrue(model.document.submissionUncertain); save(scenario, model);
        }
        try (ActivityScenario<EditorActivity> scenario = ActivityScenario.launch(draftIntent)) {
            EditorViewModel model = ready(scenario); assertTrue(model.document.submissionUncertain); assertEquals(1, writes.get());
        }
    }
    @Test public void metadataPanelAndBottomToolsPreserveTheBodyAcrossRecreation() throws Exception {
        try (ActivityScenario<EditorActivity> scenario = ActivityScenario.launch(EditorActivity.workIntent(context, 42))) {
            EditorViewModel model = ready(scenario);
            scenario.onActivity(a -> {
                assertEquals(android.view.View.GONE, a.findViewById(R.id.editorMetadataPanel).getVisibility());
                EditText body = a.findViewById(R.id.editorBody); body.setText("全屏正文"); body.setSelection(0, 4);
                a.findViewById(R.id.editorBold).performClick();
                assertEquals("[b]全屏正文[/b]", body.getText().toString());
                ((com.google.android.material.appbar.MaterialToolbar) a.findViewById(R.id.toolbar)).getMenu()
                        .performIdentifierAction(R.id.action_editor_metadata, 0);
                assertEquals(android.view.View.VISIBLE, a.findViewById(R.id.editorMetadataPanel).getVisibility());
                assertEquals(android.view.View.GONE, a.findViewById(R.id.editorWritingPanel).getVisibility());
                ((EditText) a.findViewById(R.id.editorTitle)).setText("元数据标题");
            });
            scenario.recreate();
            scenario.onActivity(a -> {
                assertEquals(android.view.View.VISIBLE, a.findViewById(R.id.editorMetadataPanel).getVisibility());
                a.findViewById(R.id.editorMetadataDone).performClick();
                assertEquals(android.view.View.VISIBLE, a.findViewById(R.id.editorWritingPanel).getVisibility());
                assertEquals("[b]全屏正文[/b]", ((EditText) a.findViewById(R.id.editorBody)).getText().toString());
                assertEquals("元数据标题", model.document.work.title);
            });
            save(scenario, model);
        }
    }
    @Test public void anotherAuthorsChapterCannotBeSubmitted() {
        owner = 8;
        try (ActivityScenario<EditorActivity> scenario = ActivityScenario.launch(EditorActivity.chapterIntent(context, 42, 81))) {
            EditorViewModel model = ready(scenario); assertTrue(model.error);
            scenario.onActivity(a -> a.findViewById(R.id.editorSubmit).performClick());
            waitFor(() -> !model.busy && model.error); assertEquals(0, writes.get());
        }
    }
}
