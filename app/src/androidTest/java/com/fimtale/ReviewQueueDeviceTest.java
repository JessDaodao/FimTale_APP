package com.fimtale;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fimtale.network.ApiDataConverter;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.network.RetrofitClient;
import com.fimtale.network.SiteUrls;
import com.fimtale.review.ReviewActionDialog;
import com.fimtale.review.ReviewQueueViewModel;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import org.junit.*;
import org.junit.runner.RunWith;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

/** Local fixtures only; no review decisions or assignments are sent to the live service. */
@RunWith(AndroidJUnit4.class)
public class ReviewQueueDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private int role = 2, originalNight;
    private Field service;
    private Object originalApi;
    private SharedPreferences session;
    private Map<String, ?> originalSession;
    private final AtomicInteger writes = new AtomicInteger();

    @Before public void setup() throws Exception {
        session = instrumentation.getTargetContext().getSharedPreferences("fimtale_session", 0); originalSession = session.getAll();
        session.edit().clear().putString("token", "fixture-session").putString("user_id", "9").commit();
        originalNight = AppCompatDelegate.getDefaultNightMode();
        instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO));
        service = RetrofitClient.class.getDeclaredField("service"); service.setAccessible(true); originalApi = service.get(null);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            String path = chain.request().url().encodedPath(); String data;
            if (!"GET".equals(chain.request().method())) { writes.incrementAndGet(); throw new AssertionError("Unexpected write in visual fixture"); }
            if (path.endsWith("get_user_auth")) data = "{\"user_id\":9,\"role_id\":" + role + "}";
            else if (path.endsWith("get_reviews")) {
                StringBuilder rows = new StringBuilder("[");
                for (int i = 1; i <= 20; i++) {
                    if (i > 1) rows.append(',');
                    rows.append("{\"id\":").append(i).append(",\"work_id\":71323,\"work_title\":\"待审核作品 ").append(i)
                            .append("\",\"status\":1,\"created_at\":\"2026-10-05T01:23:00Z\",\"updated_at\":\"2026-10-05T02:34:00Z\","
                            + "\"previous_review_count\":2,\"payload\":{},\"assignments\":[{\"reviewer_user_id\":9,\"reviewer_name\":\"测试编辑\"}]}");
                }
                data = rows.append(']').toString();
            } else throw new AssertionError("Unexpected request " + path);
            return new okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                    .body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + data + "}")).build();
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
    private void loaded(ActivityScenario<ReviewQueueActivity> scenario) {
        long deadline = SystemClock.uptimeMillis() + 8000;
        AtomicBoolean loaded = new AtomicBoolean();
        while (SystemClock.uptimeMillis() < deadline && !loaded.get()) {
            scenario.onActivity(activity -> {
                ReviewQueueViewModel model = new ViewModelProvider(activity).get(ReviewQueueViewModel.class);
                loaded.set(!model.loading && model.reviews.size() == 20);
            });
            SystemClock.sleep(50);
        }
        assertTrue(loaded.get()); instrumentation.waitForIdleSync();
    }
    private ReviewActionDialog actionDialog(ReviewQueueActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
        return (ReviewActionDialog) activity.getSupportFragmentManager().findFragmentByTag(ReviewActionDialog.TAG);
    }
    private void capture(String name) throws Exception {
        instrumentation.waitForIdleSync(); SystemClock.sleep(300);
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        try (FileOutputStream stream = new FileOutputStream(new File(instrumentation.getTargetContext().getFilesDir(), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        bitmap.recycle();
    }
    @Test public void floatingHeaderMatchesHistoryAndStaysFixedWhenTheQueueScrolls() throws Exception {
        try (ActivityScenario<ReviewQueueActivity> scenario = ActivityScenario.launch(ReviewQueueActivity.class)) {
            loaded(scenario); int[] before = new int[2];
            scenario.onActivity(activity -> {
                MaterialCardView header = activity.findViewById(R.id.toolbarContainer); header.getLocationOnScreen(before);
                float dp = activity.getResources().getDisplayMetrics().density;
                assertEquals(16 * dp, header.getRadius(), 1); assertEquals(0, header.getCardElevation(), 0.1);
                assertEquals(16 * dp, header.getLeft(), 1);
                assertEquals(View.GONE, activity.findViewById(R.id.reviewAssign).getVisibility());
                assertTrue(activity.findViewById(R.id.reviewPass).isEnabled());
            });
            capture("review-queue");
            scenario.onActivity(activity -> ((RecyclerView) activity.findViewById(R.id.reviewList)).scrollBy(0, 650));
            SystemClock.sleep(350); instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                MaterialCardView header = activity.findViewById(R.id.toolbarContainer); int[] after = new int[2]; header.getLocationOnScreen(after);
                assertArrayEquals(before, after); assertEquals(4 * activity.getResources().getDisplayMetrics().density, header.getCardElevation(), 0.1);
            });
            capture("review-queue-scrolled"); assertEquals(0, writes.get());
        }
    }
    @Test public void nativeRejectDialogAndDateTimePickersSurviveRecreationInNightTheme() throws Exception {
        role = 4; instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES));
        try (ActivityScenario<ReviewQueueActivity> scenario = ActivityScenario.launch(ReviewQueueActivity.class)) {
            loaded(scenario);
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.reviewAssign).getVisibility());
                activity.findViewById(R.id.reviewReject).performClick();
                ReviewActionDialog fragment = actionDialog(activity); AlertDialog dialog = (AlertDialog) fragment.requireDialog();
                ((EditText) dialog.findViewById(R.id.reviewReasonInput)).setText("请补充作品来源后重新提交。");
                dialog.findViewById(R.id.reviewChooseTime).performClick();
                fragment.getChildFragmentManager().executePendingTransactions();
            });
            instrumentation.waitForIdleSync();
            scenario.recreate(); instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                ReviewActionDialog fragment = actionDialog(activity);
                MaterialDatePicker<?> date = (MaterialDatePicker<?>) fragment.getChildFragmentManager().findFragmentByTag("resubmit_date");
                assertNotNull(date); date.requireDialog().findViewById(com.google.android.material.R.id.confirm_button).performClick();
                fragment.getChildFragmentManager().executePendingTransactions();
            });
            instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                ReviewActionDialog fragment = actionDialog(activity);
                MaterialTimePicker time = (MaterialTimePicker) fragment.getChildFragmentManager().findFragmentByTag("resubmit_time");
                assertNotNull(time); time.requireDialog().findViewById(com.google.android.material.R.id.material_timepicker_ok_button).performClick();
            });
            instrumentation.waitForIdleSync();
            capture("review-reject-night");
            scenario.onActivity(activity -> {
                ReviewQueueViewModel model = new ViewModelProvider(activity).get(ReviewQueueViewModel.class);
                assertNotNull(model.resubmitAfter); assertEquals("请补充作品来源后重新提交。", model.reason);
                ((AlertDialog) actionDialog(activity).requireDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            });
            assertEquals(0, writes.get());
        }
    }
}
