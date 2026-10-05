package com.fimtale;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.EditText;
import android.widget.TextView;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fimtale.network.*;
import com.fimtale.report.ReportDialog;
import com.fimtale.report.ReportViewModel;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import okhttp3.*;
import okio.Buffer;
import org.junit.*;
import org.junit.runner.RunWith;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

/** Both real detail screens with local API fixtures; no production reports are sent. */
@RunWith(AndroidJUnit4.class)
public class ReportDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<Request> submissions = new CopyOnWriteArrayList<>();
    private SharedPreferences session;
    private Map<String, ?> originalSession;
    private Field service;
    private Object originalApi;
    private int originalNight;
    private volatile int status = 200;
    @Before public void setup() throws Exception {
        session = instrumentation.getTargetContext().getSharedPreferences("fimtale_session", 0); originalSession = session.getAll();
        session.edit().clear().putString("token", "report-device-fixture").putString("user_id", "9").commit();
        originalNight = AppCompatDelegate.getDefaultNightMode();
        instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO));
        service = RetrofitClient.class.getDeclaredField("service"); service.setAccessible(true); originalApi = service.get(null);
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(chain -> {
            Request request = chain.request(); String path = request.url().encodedPath(), data; int code = 200;
            if (path.endsWith("report/create_report")) { submissions.add(request); data = "{\"id\":123}"; code = status; }
            else if (path.endsWith("get_user_page_header")) data = "{\"user_id\":456,\"username\":\"测试用户\",\"intro\":\"用户举报入口测试\"}";
            else if (path.endsWith("get_user_page_tab")) data = "{\"content\":{\"items\":[],\"total\":0}}";
            else if (path.endsWith("get_user_auth")) data = "{\"user_id\":9}";
            else if (path.endsWith("get_comments")) data = "{\"items\":[],\"total\":0}";
            else if (path.endsWith("update_read_progress")) data = "{}";
            else if (path.endsWith("get_work")) data = "{\"work\":{\"id\":71323,\"title\":\"举报测试文章\",\"intro\":\"文章举报入口测试\","
                    + "\"preface\":\"用于检查原生举报弹窗。\",\"user\":{\"user_id\":456,\"username\":\"测试用户\"}},\"chapters\":[],\"viewer\":{}}";
            else throw new AssertionError("Unexpected endpoint " + path);
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                    .body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + data + ",\"msg\":\"请稍后再试\"}")).build();
        }).build();
        service.set(null, new Retrofit.Builder().baseUrl(SiteUrls.API).client(client)
                .callbackExecutor(command -> new Handler(Looper.getMainLooper()).post(command))
                .addConverterFactory(new ApiDataConverter()).addConverterFactory(GsonConverterFactory.create()).build().create(FimTaleApiService.class));
    }
    @After public void cleanup() throws Exception {
        service.set(null, originalApi); SharedPreferences.Editor editor = session.edit().clear();
        originalSession.forEach((key, value) -> { if (value instanceof String) editor.putString(key, (String) value); }); editor.commit();
        instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(originalNight));
    }
    private ReportDialog fragment(AppCompatActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
        return (ReportDialog) activity.getSupportFragmentManager().findFragmentByTag(ReportDialog.TAG);
    }
    private AlertDialog dialog(AppCompatActivity activity) { return (AlertDialog) fragment(activity).requireDialog(); }
    private com.fimtale.ui.BottomSheetMenu moreMenu(AppCompatActivity activity) {
        try {
            Field field = activity.getClass().getDeclaredField("moreMenu"); field.setAccessible(true);
            return (com.fimtale.ui.BottomSheetMenu) field.get(activity);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private <T extends AppCompatActivity> void await(ActivityScenario<T> scenario, Predicate<T> ready) {
        long deadline = SystemClock.uptimeMillis() + 8000; AtomicBoolean done = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline && !done.get()) {
            scenario.onActivity(activity -> done.set(ready.test(activity))); SystemClock.sleep(30);
        }
        assertTrue("Fixture did not finish", done.get()); instrumentation.waitForIdleSync();
    }
    private void capture(String name) throws Exception {
        instrumentation.waitForIdleSync(); SystemClock.sleep(200);
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(instrumentation.getTargetContext().getFilesDir(), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }
    private AccessibilityNodeInfo findLabel(AccessibilityNodeInfo node, String label) {
        if (node == null) return null;
        if (label.equals(String.valueOf(node.getText())) || label.equals(String.valueOf(node.getContentDescription()))) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findLabel(node.getChild(i), label);
            if (found != null) return found;
        }
        return null;
    }
    private void clickLabel(String label) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync();
            AccessibilityNodeInfo node = findLabel(instrumentation.getUiAutomation().getRootInActiveWindow(), label);
            while (node != null && !node.isClickable()) node = node.getParent();
            if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                instrumentation.waitForIdleSync(); return;
            }
            SystemClock.sleep(50);
        }
        fail("Missing accessible action: " + label);
    }
    private void assertPayload(int type, long id) throws Exception {
        assertEquals(1, submissions.size()); Request request = submissions.get(0);
        Buffer buffer = new Buffer(); request.body().writeTo(buffer); JsonObject body = new JsonParser().parse(buffer.readUtf8()).getAsJsonObject();
        assertEquals(type, body.get("target_type").getAsInt()); assertEquals(id, body.get("target_id").getAsLong());
        assertEquals("report", body.get("kind").getAsString()); assertEquals("report-device-fixture", request.header("Token"));
    }
    @Test public void userMenuUsesResolvedUserIdAndKeepsNativeDialogAfterRecreation() throws Exception {
        Intent intent = new Intent(instrumentation.getTargetContext(), UserDetailActivity.class).putExtra(UserDetailActivity.EXTRA_USERNAME, "测试用户");
        try (ActivityScenario<UserDetailActivity> scenario = ActivityScenario.launch(intent)) {
            await(scenario, activity -> "测试用户".contentEquals(((TextView) activity.findViewById(R.id.tvUsername)).getText()));
            clickLabel("更多");
            scenario.onActivity(activity -> assertTrue(moreMenu(activity).isShowing()));
            capture("user-more-menu");
            clickLabel("举报用户");
            scenario.onActivity(activity -> {
                assertEquals("用户 @测试用户", ((TextView) dialog(activity).findViewById(R.id.reportTarget)).getText().toString());
                ((EditText) dialog(activity).findViewById(R.id.reportContent)).setText("用户举报说明，附相关事实。");
            });
            scenario.recreate(); instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> assertEquals("用户举报说明，附相关事实。", ((EditText) dialog(activity).findViewById(R.id.reportContent)).getText().toString()));
            capture("report-user");
            scenario.onActivity(activity -> dialog(activity).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            await(scenario, activity -> fragment(activity) == null); assertPayload(7, 456);
        }
    }
    @Test public void workMoreSheetOpensReportAndDisplaysServiceErrorInNightTheme() throws Exception {
        status = 429;
        instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES));
        Intent intent = new Intent(instrumentation.getTargetContext(), TopicDetailActivity.class).putExtra(TopicDetailActivity.EXTRA_TOPIC_ID, 71323);
        try (ActivityScenario<TopicDetailActivity> scenario = ActivityScenario.launch(intent)) {
            await(scenario, activity -> "举报测试文章".contentEquals(activity.getSupportActionBar().getTitle()));
            clickLabel("更多"); clickLabel("举报文章");
            scenario.onActivity(activity -> {
                assertEquals("文章《举报测试文章》", ((TextView) dialog(activity).findViewById(R.id.reportTarget)).getText().toString());
                ((EditText) dialog(activity).findViewById(R.id.reportContent)).setText("文章举报说明\nhttps://example.org/evidence");
                dialog(activity).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            await(scenario, activity -> !new ViewModelProvider(fragment(activity)).get(ReportViewModel.class).submitting);
            scenario.onActivity(activity -> {
                assertEquals("请稍后再试", ((TextView) dialog(activity).findViewById(R.id.reportError)).getText().toString());
                assertTrue(dialog(activity).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
            });
            capture("report-work-night"); assertPayload(1, 71323);
            scenario.onActivity(activity -> dialog(activity).getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        }
    }

    @Test public void readerBottomMenuKeepsAuthorPermissionsAndOpensTheCorrectWork() throws Exception {
        instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES));
        Intent intent = new Intent(instrumentation.getTargetContext(), ReaderActivity.class)
                .putExtra(ReaderActivity.EXTRA_WORK_ID, 71323).putExtra(ReaderActivity.EXTRA_CHAPTER_ID, 0);
        for (boolean author : new boolean[]{false, true}) {
            session.edit().putString("user_id", author ? "456" : "9").commit();
            try (ActivityScenario<ReaderActivity> scenario = ActivityScenario.launch(intent)) {
                await(scenario, activity -> "举报测试文章".equals(String.valueOf(
                        ((androidx.appcompat.widget.Toolbar) activity.findViewById(R.id.topToolbar)).getTitle())));
                scenario.onActivity(activity -> {
                    androidx.appcompat.widget.Toolbar toolbar = activity.findViewById(R.id.topToolbar);
                    toolbar.getMenu().performIdentifierAction(R.id.action_more, 0);
                    com.fimtale.ui.BottomSheetMenu sheet = moreMenu(activity);
                    assertTrue(sheet.isShowing());
                    assertEquals(author, sheet.findViewById(R.id.action_edit_content) != null);
                    assertNotNull(sheet.findViewById(R.id.action_info));
                });
                capture(author ? "reader-more-author" : "reader-more-menu");
                Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(TopicDetailActivity.class.getName(), null, false);
                try {
                    scenario.onActivity(activity -> moreMenu(activity).findViewById(R.id.action_info).performClick());
                    android.app.Activity details = instrumentation.waitForMonitorWithTimeout(monitor, 5000);
                    assertNotNull(details);
                    assertEquals(71323, details.getIntent().getIntExtra(TopicDetailActivity.EXTRA_TOPIC_ID, 0));
                    instrumentation.runOnMainSync(details::finish);
                } finally { instrumentation.removeMonitor(monitor); }
            }
        }
        assertTrue(submissions.isEmpty());
    }
}
