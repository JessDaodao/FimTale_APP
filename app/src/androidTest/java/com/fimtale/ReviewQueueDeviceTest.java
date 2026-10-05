package com.fimtale;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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
import com.google.android.material.chip.Chip;
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

/** Local author fixtures; no real submissions or administrator endpoints. */
@RunWith(AndroidJUnit4.class)
public class ReviewQueueDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private int originalNight;
    private Field service;
    private Object originalApi;
    private SharedPreferences session;
    private Map<String, ?> originalSession;
    private final AtomicInteger writes = new AtomicInteger();

    @Before public void setup() throws Exception {
        session = instrumentation.getTargetContext().getSharedPreferences("fimtale_session", 0); originalSession = session.getAll();
        session.edit().clear().putString("token", "author-fixture").putString("user_id", "9").commit();
        originalNight = AppCompatDelegate.getDefaultNightMode();
        instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO));
        service = RetrofitClient.class.getDeclaredField("service"); service.setAccessible(true); originalApi = service.get(null);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            String path = chain.request().url().encodedPath();
            if (!"GET".equals(chain.request().method())) { writes.incrementAndGet(); throw new AssertionError("Unexpected write in visual fixture"); }
            if (!path.endsWith("get_review_entries")) throw new AssertionError("Unexpected endpoint " + path);
            StringBuilder rows = new StringBuilder("[");
            for (int i = 0; i < 20; i++) {
                if (i > 0) rows.append(',');
                int status = i == 0 ? 0 : i == 1 || i == 2 ? 3 : i == 3 ? 2 : 1;
                rows.append("{\"id\":").append(i).append(",\"work_id\":").append(71323 + i)
                        .append(",\"work_title\":\"我的作品 ").append(i + 1)
                        .append("\",\"status\":").append(status)
                        .append(",\"created_at\":\"2026-10-05T01:23:00Z\",\"updated_at\":\"2026-10-05T02:34:00Z\",")
                        .append("\"payload\":{\"state_reason\":\"请补充作品来源后重新提交。\",\"resubmit_after\":")
                        .append(i == 2 ? "\"2999-01-01T00:00:00Z\"" : "null")
                        .append("},\"assignments\":[{\"reviewer_user_id\":9,\"reviewer_name\":\"不应显示的审核员\"}]}");
            }
            return new okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                    .body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + rows.append(']') + "}")).build();
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
        long deadline = SystemClock.uptimeMillis() + 8000; AtomicBoolean loaded = new AtomicBoolean();
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
    private void checkNoReviewer(View view) {
        if (view instanceof TextView) assertFalse(((TextView) view).getText().toString().contains("审核员"));
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) checkNoReviewer(((ViewGroup) view).getChildAt(i));
    }
    @Test public void authorCardsAreFilledWithoutReviewerChipsAndHeaderStaysFixed() throws Exception {
        try (ActivityScenario<ReviewQueueActivity> scenario = ActivityScenario.launch(ReviewQueueActivity.class)) {
            loaded(scenario); int[] before = new int[2];
            scenario.onActivity(activity -> {
                MaterialCardView header = activity.findViewById(R.id.toolbarContainer); header.getLocationOnScreen(before);
                float dp = activity.getResources().getDisplayMetrics().density;
                assertEquals(16 * dp, header.getRadius(), 1); assertEquals(0, header.getCardElevation(), 0.1);
                assertEquals(16 * dp, header.getLeft(), 1);
                View refresh = activity.findViewById(R.id.action_review_refresh);
                assertNotNull("Refresh action should be displayed in the floating header", refresh);
                assertTrue(refresh.isShown());
                assertTrue(refresh.getGlobalVisibleRect(new android.graphics.Rect()));
                Chip status = activity.findViewById(R.id.reviewStatus);
                assertEquals(0, status.getChipStrokeWidth(), 0.1);
                assertEquals("待提交", status.getText().toString());
                MaterialCardView card = (MaterialCardView) status.getParent().getParent();
                assertEquals(0, card.getStrokeWidth()); assertEquals(255, android.graphics.Color.alpha(card.getCardBackgroundColor().getDefaultColor()));
                assertTrue(activity.findViewById(R.id.reviewSubmit).isEnabled());
                checkNoReviewer(activity.findViewById(R.id.reviewList));
            });
            capture("my-reviews");
            scenario.onActivity(activity -> ((RecyclerView) activity.findViewById(R.id.reviewList)).scrollBy(0, 650));
            SystemClock.sleep(350); instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                MaterialCardView header = activity.findViewById(R.id.toolbarContainer); int[] after = new int[2]; header.getLocationOnScreen(after);
                assertArrayEquals(before, after); assertEquals(4 * activity.getResources().getDisplayMetrics().density, header.getCardElevation(), 0.1);
                checkNoReviewer(activity.findViewById(R.id.reviewList));
            });
            capture("my-reviews-scrolled"); assertEquals(0, writes.get());
        }
    }
    @Test public void nativeSubmissionConfirmationSurvivesRecreationInNightTheme() throws Exception {
        instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES));
        try (ActivityScenario<ReviewQueueActivity> scenario = ActivityScenario.launch(ReviewQueueActivity.class)) {
            loaded(scenario);
            scenario.onActivity(activity -> {
                ReviewQueueViewModel model = new ViewModelProvider(activity).get(ReviewQueueViewModel.class);
                model.open(model.reviews.get(1)); assertNotNull(actionDialog(activity));
            });
            instrumentation.waitForIdleSync(); scenario.recreate(); instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> {
                ReviewActionDialog fragment = actionDialog(activity); assertNotNull(fragment);
                AlertDialog dialog = (AlertDialog) fragment.requireDialog();
                assertEquals("重新提交", dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
                assertTrue(((TextView) dialog.findViewById(R.id.reviewActionTitle)).getText().toString().contains("我的作品 2"));
                assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
            });
            capture("my-reviews-confirm-night");
            scenario.onActivity(activity -> ((AlertDialog) actionDialog(activity).requireDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
            instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> assertNull(new ViewModelProvider(activity).get(ReviewQueueViewModel.class).selected));
            assertEquals(0, writes.get());
        }
    }
}
