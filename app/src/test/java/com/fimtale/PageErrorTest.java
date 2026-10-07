package com.fimtale;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.lifecycle.ViewModelProvider;
import com.fimtale.network.ApiDataConverter;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.network.RetrofitClient;
import com.fimtale.network.SiteUrls;
import com.fimtale.ui.PageErrorView;
import com.fimtale.ui.SkeletonRefreshLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
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

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PageErrorTest {
    private Context context;
    private Field service, updateService;
    private Object originalApi, originalUpdate;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile boolean failing = true;
    private ActivityController<? extends Activity> controller;

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("fimtale_session", 0).edit().clear()
                .putString("token", "error-fixture").putString("user_id", "9").putString("user_name", "测试用户").commit();
        Field factory = ViewModelProvider.AndroidViewModelFactory.class.getDeclaredField("sInstance");
        factory.setAccessible(true); factory.set(null, null);
        service = RetrofitClient.class.getDeclaredField("service"); service.setAccessible(true); originalApi = service.get(null);
        updateService = RetrofitClient.class.getDeclaredField("updateService"); updateService.setAccessible(true); originalUpdate = updateService.get(null);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            Request request = chain.request(); requests.add(request);
            String path = request.url().encodedPath();
            String data = path.endsWith("get_timeline") || path.endsWith("get_review_entries")
                    || path.endsWith("list_tags") || path.endsWith("get_active_sessions") ? "[]" : "{\"items\":[],\"total\":0}";
            if (path.endsWith("get_user_auth")) data = "{\"user_id\":9,\"qualify_status\":1}";
            if (path.endsWith("get_work")) data = "{\"work\":{\"id\":42,\"title\":\"测试文章\",\"preface\":\"正文\"},\"chapters\":[]}";
            if (path.endsWith("get_timeline_update_count")) data = "0";
            boolean auxiliary = path.equals("/update/") || path.endsWith("get_user_auth") || path.endsWith("get_timeline_update_count");
            int code = failing && !auxiliary ? 503 : 200;
            return new okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Fixture")
                    .body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + data + ",\"msg\":\"服务器暂时不可用\"}")).build();
        }).build();
        FimTaleApiService api = new Retrofit.Builder().baseUrl(SiteUrls.API).client(client)
                .callbackExecutor(command -> new Handler(Looper.getMainLooper()).post(command))
                .addConverterFactory(new ApiDataConverter()).addConverterFactory(GsonConverterFactory.create())
                .build().create(FimTaleApiService.class);
        service.set(null, api); updateService.set(null, api);
    }
    @After public void cleanup() throws Exception {
        if (controller != null) controller.pause().stop().destroy();
        service.set(null, originalApi); updateService.set(null, originalUpdate);
    }
    private void await(BooleanSupplier condition) throws Exception {
        long end = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20);
        }
        assertTrue("Expected page state", condition.getAsBoolean());
    }
    private PageErrorView shownError(View view) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof PageErrorView) return (PageErrorView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            PageErrorView error = shownError(((ViewGroup) view).getChildAt(i));
            if (error != null) return error;
        }
        return null;
    }
    private <T extends Activity> T launch(Class<T> type, Intent intent) {
        ActivityController<T> active = Robolectric.buildActivity(type, intent).setup();
        controller = active; return active.get();
    }
    private void assertPageError(Class<? extends Activity> type, Intent intent) throws Exception {
        Activity activity = launch(type, intent);
        View root = activity.findViewById(android.R.id.content);
        await(() -> shownError(root) != null);
        PageErrorView error = shownError(root);
        assertEquals("加载失败", ((TextView) error.findViewById(R.id.pageErrorTitle)).getText().toString());
        int before = requests.size(); error.findViewById(R.id.pageErrorRetry).performClick();
        await(() -> requests.size() > before && shownError(root) != null);
    }

    @Test public void wrapperKeepsRefreshChildAndContentWhileRetryFiresOnce() {
        Context themed = new ContextThemeWrapper(context, R.style.Theme_Fimtale);
        FrameLayout host = new FrameLayout(themed);
        SkeletonRefreshLayout refresh = new SkeletonRefreshLayout(themed, null);
        TextView content = new TextView(themed); content.setText("已加载的内容");
        refresh.addView(content); host.addView(refresh);
        PageErrorView error = PageErrorView.wrap(content);
        assertSame(refresh, content.getParent());
        AtomicInteger retries = new AtomicInteger(); error.show(null, retries::incrementAndGet, true);
        error.findViewById(R.id.pageErrorRetry).performClick();
        error.findViewById(R.id.pageErrorRetry).performClick();
        assertEquals(1, retries.get()); assertEquals(View.GONE, error.getVisibility());
        assertEquals("已加载的内容", content.getText().toString());
        error.show("离线", retries::incrementAndGet, true);
        error.findViewById(R.id.pageErrorContinue).performClick();
        error.show("离线", retries::incrementAndGet, true);
        assertEquals(View.GONE, error.getVisibility());
        error.hide(); error.show("离线", retries::incrementAndGet, true);
        assertEquals(View.VISIBLE, error.getVisibility());
    }
    @Test public void homeErrorRetainsNavigationAndRetryCanBecomeSuccessfulEmptyContent() throws Exception {
        MainActivity activity = launch(MainActivity.class, new Intent(context, MainActivity.class));
        View root = activity.findViewById(android.R.id.content);
        await(() -> shownError(root) != null);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.bottom_navigation).getVisibility());
        failing = false; shownError(root).findViewById(R.id.pageErrorRetry).performClick();
        await(() -> shownError(root) == null && activity.findViewById(R.id.loadingSkeleton).getVisibility() == View.GONE);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.homeList).getVisibility());
    }
    @Test public void anEmptyInlineSectionStillMeasuresAnAccessibleErrorPanel() {
        Context themed = new ContextThemeWrapper(context, R.style.Theme_Fimtale);
        FrameLayout host = new FrameLayout(themed);
        TextView content = new TextView(themed); content.setVisibility(View.GONE);
        host.addView(content, new FrameLayout.LayoutParams(-1, -2));
        PageErrorView error = PageErrorView.wrap(content); error.show(null, () -> {});
        int width = Math.round(360 * context.getResources().getDisplayMetrics().density);
        int height = Math.round(640 * context.getResources().getDisplayMetrics().density);
        host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
        host.layout(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight());
        assertTrue(error.getHeight() > 0);
        assertTrue(error.getHeight() <= height);
        assertTrue(error.findViewById(R.id.pageErrorRetry).getHeight() > 0);
    }
    @Test public void timelineAndArticlesAndProfileShareTheSameError() throws Exception {
        MainActivity activity = launch(MainActivity.class, new Intent(context, MainActivity.class));
        BottomNavigationView nav = activity.findViewById(R.id.bottom_navigation);
        for (int id : new int[]{R.id.nav_timeline, R.id.nav_article, R.id.nav_profile}) {
            nav.setSelectedItemId(id); activity.getSupportFragmentManager().executePendingTransactions();
            await(() -> shownError(activity.findViewById(R.id.fragment_container)) != null);
        }
    }
    @Test public void historyUsesRetryScreen() throws Exception { assertPageError(HistoryActivity.class, new Intent(context, HistoryActivity.class)); }
    @Test public void favoritesUseRetryScreen() throws Exception { assertPageError(FavoritesActivity.class, new Intent(context, FavoritesActivity.class)); }
    @Test public void tagsUseRetryScreen() throws Exception { assertPageError(TagListActivity.class, new Intent(context, TagListActivity.class)); }
    @Test public void tagArticlesUseRetryScreen() throws Exception { assertPageError(TagArticlesActivity.class, new Intent(context, TagArticlesActivity.class).putExtra(TagArticlesActivity.EXTRA_WORK_TYPE, 3)); }
    @Test public void searchUsesRetryScreen() throws Exception { assertPageError(SearchActivity.class, new Intent(context, SearchActivity.class).putExtra(SearchActivity.EXTRA_QUERY, "小马")); }
    @Test public void profileDetailsUseRetryScreen() throws Exception { assertPageError(UserDetailActivity.class, new Intent(context, UserDetailActivity.class).putExtra(UserDetailActivity.EXTRA_USERNAME, "作者")); }
    @Test public void workDetailsUseRetryScreen() throws Exception { assertPageError(TopicDetailActivity.class, new Intent(context, TopicDetailActivity.class).putExtra(TopicDetailActivity.EXTRA_TOPIC_ID, 42)); }
    @Test public void workReadingActionsReturnAfterScrollingOutOfComments() throws Exception {
        failing = false;
        TopicDetailActivity activity = launch(TopicDetailActivity.class,
                new Intent(context, TopicDetailActivity.class).putExtra(TopicDetailActivity.EXTRA_TOPIC_ID, 42));
        await(() -> activity.findViewById(R.id.detailLoadingSkeleton).getVisibility() == View.GONE);
        controller.visible();
        float density = context.getResources().getDisplayMetrics().density;
        activity.findViewById(R.id.detailContentTextView).setMinimumHeight(Math.round(1600 * density));
        View comments = activity.findViewById(R.id.workCommentsSection);
        comments.setMinimumHeight(Math.round(500 * density));
        View root = activity.getWindow().getDecorView();
        int width = Math.round(360 * density), height = Math.round(800 * density);
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        androidx.core.widget.NestedScrollView scroll = activity.findViewById(R.id.scrollView);
        View reading = activity.findViewById(R.id.readingActionsBar);
        View composer = activity.findViewById(R.id.commentComposerBar);
        TextView draft = activity.findViewById(R.id.commentComposerInput);
        draft.setText("尚未发送的评论");
        for (int i = 0; i < 3; i++) {
            scroll.scrollTo(0, comments.getTop());
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
            assertEquals(View.GONE, reading.getVisibility());
            assertEquals(View.VISIBLE, composer.getVisibility());
            // A fast scroll can skip the visibility threshold and hide the entire comments block.
            scroll.scrollTo(0, 0);
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200));
            assertFalse(comments.getGlobalVisibleRect(new android.graphics.Rect()));
            assertEquals(View.VISIBLE, reading.getVisibility());
            assertEquals(1f, reading.getAlpha(), 0.01f);
            assertEquals(View.GONE, composer.getVisibility());
            assertEquals("尚未发送的评论", draft.getText().toString());
        }
        activity.findViewById(R.id.startReadingButton).performClick();
        Intent reader = shadowOf(activity).getNextStartedActivity();
        assertEquals(ReaderActivity.class.getName(), reader.getComponent().getClassName());
        assertEquals(42, reader.getIntExtra(ReaderActivity.EXTRA_WORK_ID, -1));
    }
    @Test public void accountSessionsUseRetryScreen() throws Exception { assertPageError(AccountSessionsActivity.class, new Intent(context, AccountSessionsActivity.class)); }
    @Test public void filtersUseRetryScreen() throws Exception { assertPageError(ContentFiltersActivity.class, new Intent(context, ContentFiltersActivity.class)); }
    @Test public void reviewsUseRetryScreen() throws Exception { assertPageError(ReviewQueueActivity.class, new Intent(context, ReviewQueueActivity.class)); }
    @Test public void draftsUseRetryScreen() throws Exception { assertPageError(DraftsActivity.class, new Intent(context, DraftsActivity.class)); }
    @Test public void editorLoadFailureUsesRetryScreen() throws Exception { assertPageError(EditorActivity.class, EditorActivity.workIntent(context, 42)); }
    @Test public void readerLoadFailureUsesRetryScreen() throws Exception { assertPageError(ReaderActivity.class,
            new Intent(context, ReaderActivity.class).putExtra(ReaderActivity.EXTRA_WORK_ID, 42).putExtra(ReaderActivity.EXTRA_CHAPTER_ID, 101)); }
    @Test public void siteOnlyShowsMainFrameFailuresAndRetriesTheFailedUrl() {
        SiteActivity activity = launch(SiteActivity.class, new Intent(context, SiteActivity.class).putExtra(SiteActivity.EXTRA_PATH, "/channel/7"));
        android.webkit.WebView web = activity.findViewById(R.id.site_webview);
        android.webkit.WebViewClient client = web.getWebViewClient();
        String url = SiteUrls.SITE + "/channel/7";
        client.onPageStarted(web, url, null);
        android.webkit.WebResourceResponse response = new android.webkit.WebResourceResponse("text/html", "UTF-8", 503,
                "Unavailable", java.util.Collections.emptyMap(), new java.io.ByteArrayInputStream(new byte[0]));
        client.onReceivedHttpError(web, webRequest(url + "/image.png", false), response);
        View root = activity.findViewById(android.R.id.content);
        assertNull(shownError(root));
        client.onReceivedHttpError(web, webRequest(url, true), response);
        client.onPageFinished(web, url);
        PageErrorView error = shownError(root); assertNotNull(error);
        error.findViewById(R.id.pageErrorRetry).performClick();
        assertEquals(url, shadowOf(web).getLastLoadedUrl());
    }
    private android.webkit.WebResourceRequest webRequest(String url, boolean main) {
        return new android.webkit.WebResourceRequest() {
            @Override public android.net.Uri getUrl() { return android.net.Uri.parse(url); }
            @Override public boolean isForMainFrame() { return main; }
            @Override public boolean isRedirect() { return false; }
            @Override public boolean hasGesture() { return false; }
            @Override public String getMethod() { return "GET"; }
            @Override public java.util.Map<String, String> getRequestHeaders() { return java.util.Collections.emptyMap(); }
        };
    }
}
