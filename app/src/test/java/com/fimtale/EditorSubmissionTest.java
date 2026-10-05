package com.fimtale;

import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import com.fimtale.editor.EditorDraftStore;
import com.fimtale.editor.EditorViewModel;
import com.fimtale.editor.CaptchaDialogFragment;
import com.fimtale.editor.OnlineDraft;
import com.fimtale.network.ApiDataConverter;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.network.RetrofitClient;
import com.fimtale.network.SiteUrls;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Exercises the visible buttons against an in-process API fixture; never publishes online. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class EditorSubmissionTest {
    private Context context;
    private Field service, modelFactory;
    private Object originalApi;
    private ActivityController<EditorActivity> controller;
    private EditorActivity activity;
    private EditorViewModel model;
    private final ConcurrentHashMap<String, OnlineDraft> cloud = new ConcurrentHashMap<>();
    private final AtomicInteger writes = new AtomicInteger();
    private final AtomicReference<JsonObject> payload = new AtomicReference<>();
    private final AtomicReference<Request> submitted = new AtomicReference<>();

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        // Robolectric creates a new Application per test; AndroidX caches the first one.
        modelFactory = ViewModelProvider.AndroidViewModelFactory.class.getDeclaredField("sInstance");
        modelFactory.setAccessible(true); modelFactory.set(null, null);
        context.getSharedPreferences("fimtale_session", Context.MODE_PRIVATE).edit().clear()
                .putString("user_id", "9").putString("token", "fixture-only-token").commit();
        service = RetrofitClient.class.getDeclaredField("service");
        service.setAccessible(true); originalApi = service.get(null);
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(chain -> {
            Request request = chain.request();
            String path = request.url().encodedPath();
            String data;
            int code = 200;
            if (path.endsWith("get_draft")) {
                OnlineDraft draft = cloud.get(request.url().queryParameter("draft_key"));
                code = draft == null ? 404 : 200;
                data = new Gson().toJson(draft);
            } else if (path.endsWith("save_draft")) {
                JsonObject input = body(request);
                String key = input.get("draft_key").getAsString();
                OnlineDraft old = cloud.get(key);
                long revision = old == null ? 0 : old.revision;
                if (input.get("expected_revision").getAsLong() != revision) { code = 409; data = "null"; }
                else {
                    OnlineDraft saved = new OnlineDraft();
                    saved.key = key; saved.revision = revision + 1; saved.payload = input.getAsJsonObject("payload");
                    cloud.put(key, saved); data = new Gson().toJson(saved);
                }
            } else if (path.endsWith("get_user_auth")) {
                data = "{\"user_id\":9,\"role_id\":1,\"qualify_status\":2,\"space_status\":1}";
            } else if (path.endsWith("get_work")) {
                data = "{\"work\":{\"id\":42,\"title\":\"原始标题\",\"preface\":\"[b]原始正文[/b]\","
                        + "\"intro\":\"简介\",\"type\":1,\"length\":3,\"rating\":2,\"origin\":1,\"publish\":1,"
                        + "\"user\":{\"user_id\":9},\"tags\":[]}}";
            } else if (path.endsWith("get_chapter")) {
                data = "{\"chapter\":{\"id\":81,\"work_id\":42,\"title\":\"原始章节\",\"content\":\"[i]正文[/i]\"}}";
            } else if (path.endsWith("create_update_work") || path.endsWith("create_update_chapter")) {
                payload.set(body(request)); submitted.set(request); writes.incrementAndGet();
                // A server rejection must remain visible while the draft is backed up.
                code = 403; data = "null";
            } else {
                throw new AssertionError("Unexpected API request: " + path);
            }
            return new okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                    .body(ResponseBody.create(MediaType.get("application/json"),
                            "{\"data\":" + data + ",\"msg\":\"测试：提交被拒绝\"}")).build();
        }).build();
        service.set(null, new Retrofit.Builder().baseUrl(SiteUrls.API).client(client)
                .callbackExecutor(command -> new Handler(Looper.getMainLooper()).post(command))
                .addConverterFactory(new ApiDataConverter()).addConverterFactory(GsonConverterFactory.create())
                .build().create(FimTaleApiService.class));
    }

    @After public void cleanup() throws Exception {
        if (controller != null) controller.pause().stop().destroy();
        modelFactory.set(null, null);
        service.set(null, originalApi);
        context.getSharedPreferences("fimtale_session", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private static JsonObject body(Request request) throws java.io.IOException {
        Buffer buffer = new Buffer(); request.body().writeTo(buffer);
        return new JsonParser().parse(buffer.readUtf8()).getAsJsonObject();
    }

    private void launch(boolean chapter) throws Exception {
        launch(42, chapter ? 81 : -1);
    }
    private void launch(int workId, int chapterId) throws Exception {
        new EditorDraftStore(new File(context.getNoBackupFilesDir(), "editor_drafts"), SiteUrls.API, "9", workId, chapterId).delete();
        Intent intent = chapterId >= 0 ? EditorActivity.chapterIntent(context, workId, chapterId) : EditorActivity.workIntent(context, workId);
        controller = Robolectric.buildActivity(EditorActivity.class, intent).setup();
        activity = controller.get(); model = new ViewModelProvider(activity).get(EditorViewModel.class);
        await(() -> model.ready && !model.busy);
    }

    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10);
        }
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("Timed out: " + model.message + "; busy=" + model.busy + "; ready=" + model.ready
                + "; error=" + model.error + "; writes=" + writes.get(), condition.getAsBoolean());
    }

    private MaterialToolbar toolbar() { return activity.findViewById(R.id.toolbar); }

    private Dialog metadata() {
        toolbar().getMenu().performIdentifierAction(R.id.action_editor_metadata, 0);
        shadowOf(Looper.getMainLooper()).idle();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertTrue(dialog.isShowing());
        return dialog;
    }

    private void acceptHandbook(Dialog sheet) {
        ((MaterialCheckBox) sheet.findViewById(R.id.editorHandbook)).setChecked(true);
    }

    private void finishCaptcha() throws Exception {
        await(() -> model.captchaRequested && activity.getSupportFragmentManager().findFragmentByTag(CaptchaDialogFragment.TAG) != null);
        assertNull(shadowOf(activity).getNextStartedActivity());
        CaptchaDialogFragment dialog = (CaptchaDialogFragment) activity.getSupportFragmentManager().findFragmentByTag(CaptchaDialogFragment.TAG);
        assertTrue(dialog.requireDialog().isShowing());
        android.os.Bundle result = new android.os.Bundle();
        result.putString(CaptchaDialogFragment.TOKEN, "fixture-captcha");
        result.putString(CaptchaDialogFragment.PROVIDER, "turnstile");
        activity.getSupportFragmentManager().setFragmentResult(CaptchaDialogFragment.RESULT_KEY, result);
        await(() -> writes.get() == 1 && model.error && !model.busy);
    }

    @Test public void toolbarSavesLatestWorkMetadataAndRawBody() throws Exception {
        launch(false);
        assertEquals("保存", toolbar().getMenu().findItem(R.id.action_editor_submit).getTitle().toString());
        for (int i = 0; i < toolbar().getMenu().size(); i++)
            assertNotEquals("预览", toolbar().getMenu().getItem(i).getTitle().toString());
        Dialog sheet = metadata();
        ((EditText) sheet.findViewById(R.id.editorTitle)).setText("修改标题");
        acceptHandbook(sheet);
        sheet.findViewById(R.id.editorMetadataDone).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        ((EditText) activity.findViewById(R.id.editorBody)).setText("[table][tr][td]修改正文[/td][/tr][/table]");
        assertTrue(toolbar().getMenu().performIdentifierAction(R.id.action_editor_submit, 0));
        assertTrue(model.busy);
        assertFalse(toolbar().getMenu().findItem(R.id.action_editor_submit).isEnabled());
        finishCaptcha();
        assertEquals(42, payload.get().get("id").getAsInt());
        assertEquals("修改标题", payload.get().get("title").getAsString());
        assertEquals("[table][tr][td]修改正文[/td][/tr][/table]", payload.get().get("preface").getAsString());
        assertEquals("fixture-captcha", submitted.get().url().queryParameter("captcha_response"));
        assertFalse(model.finished);
    }

    @Test public void visibleMetadataButtonValidatesThenSubmitsAndShowsFailure() throws Exception {
        launch(false);
        Dialog sheet = metadata();
        EditText title = sheet.findViewById(R.id.editorTitle);
        title.setText("");
        sheet.findViewById(R.id.editorSubmit).performClick();
        assertEquals("请填写标题", title.getError().toString());
        assertEquals(0, writes.get());
        title.setText("元数据修改");
        ((EditText) sheet.findViewById(R.id.editorIntro)).setText("新简介");
        ((Spinner) sheet.findViewById(R.id.editorRating)).setSelection(3);
        acceptHandbook(sheet);
        sheet.findViewById(R.id.editorSubmit).performClick();
        assertNull(title.getError());
        assertTrue(model.busy);
        assertFalse(sheet.findViewById(R.id.editorSubmit).isEnabled());
        assertFalse(title.isEnabled());
        assertEquals(View.VISIBLE, sheet.findViewById(R.id.editorMetadataProgress).getVisibility());
        sheet.findViewById(R.id.editorSubmit).performClick();
        finishCaptcha();
        assertEquals(1, writes.get());
        assertEquals("元数据修改", payload.get().get("title").getAsString());
        assertEquals("新简介", payload.get().get("intro").getAsString());
        assertEquals(3, payload.get().get("rating").getAsInt());
        assertEquals("[b]原始正文[/b]", payload.get().get("preface").getAsString());
        AtomicInteger synced = new AtomicInteger();
        model.saveDraft(synced::incrementAndGet);
        await(() -> synced.get() == 1);
        assertEquals("测试：提交被拒绝", ((TextView) sheet.findViewById(R.id.editorMetadataStatus)).getText().toString());
        assertTrue(sheet.findViewById(R.id.editorSubmit).isEnabled());
        assertEquals(View.GONE, sheet.findViewById(R.id.editorMetadataProgress).getVisibility());
        assertFalse(model.document.submissionUncertain);
        assertEquals("元数据修改", model.document.work.title);
    }

    @Test public void visibleChapterButtonSavesChapterWithoutWorkCaptcha() throws Exception {
        launch(true);
        assertEquals("保存", toolbar().getMenu().findItem(R.id.action_editor_submit).getTitle().toString());
        Dialog sheet = metadata();
        ((EditText) sheet.findViewById(R.id.editorTitle)).setText("修改章节");
        sheet.findViewById(R.id.editorSubmit).performClick();
        await(() -> writes.get() == 1 && model.error && !model.busy);
        assertFalse(model.captchaRequested);
        assertEquals("/api/work/create_update_chapter", submitted.get().url().encodedPath());
        assertEquals(81, payload.get().get("id").getAsInt());
        assertEquals(42, payload.get().get("work_id").getAsInt());
        assertEquals("修改章节", payload.get().get("title").getAsString());
        assertEquals("[i]正文[/i]", payload.get().get("content").getAsString());
        assertEquals("测试：提交被拒绝", ((TextView) sheet.findViewById(R.id.editorMetadataStatus)).getText().toString());
    }

    @Test public void newWorkUsesPublishAction() throws Exception {
        launch(0, -1);
        assertEquals("发表", toolbar().getMenu().findItem(R.id.action_editor_submit).getTitle().toString());
    }

    @Test public void newChapterUsesPublishActionWithinAnExistingWork() throws Exception {
        launch(42, 0);
        assertEquals("发表", toolbar().getMenu().findItem(R.id.action_editor_submit).getTitle().toString());
    }

    @Test public void captchaSurvivesRotationAndCancellationKeepsTheDraft() throws Exception {
        launch(false);
        Dialog sheet = metadata(); acceptHandbook(sheet);
        sheet.findViewById(R.id.editorSubmit).performClick();
        await(() -> model.captchaRequested && activity.getSupportFragmentManager().findFragmentByTag(CaptchaDialogFragment.TAG) != null);
        EditorActivity previous = activity;
        android.content.res.Configuration rotated = new android.content.res.Configuration(activity.getResources().getConfiguration());
        rotated.orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        controller.configurationChange(rotated); activity = controller.get();
        assertNotSame(previous, activity);
        assertSame(model, new ViewModelProvider(activity).get(EditorViewModel.class));
        await(() -> activity.getSupportFragmentManager().findFragmentByTag(CaptchaDialogFragment.TAG) != null);
        long dialogs = activity.getSupportFragmentManager().getFragments().stream().filter(f -> f instanceof CaptchaDialogFragment).count();
        assertEquals(1, dialogs);
        CaptchaDialogFragment dialog = (CaptchaDialogFragment) activity.getSupportFragmentManager().findFragmentByTag(CaptchaDialogFragment.TAG);
        ((androidx.appcompat.app.AlertDialog) dialog.requireDialog()).getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).performClick();
        await(() -> !model.busy);
        assertFalse(model.captchaRequested); assertEquals(0, writes.get());
        assertEquals("[b]原始正文[/b]", model.document.work.preface);
        assertEquals("已取消验证，草稿已保留", model.message);
    }
}
