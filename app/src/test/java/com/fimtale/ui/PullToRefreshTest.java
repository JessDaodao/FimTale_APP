package com.fimtale.ui;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PullToRefreshTest {
    private ActivityController<Activity> controller;
    private PullRefreshLayout refresh;
    private FrameLayout root;
    private View content;
    private final AtomicInteger calls = new AtomicInteger();
    private boolean ready = true, scrolled;
    private long down;

    @Before public void setup() {
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        root = new FrameLayout(controller.get());
        controller.get().setContentView(root);
        TextView text = new TextView(controller.get());
        text.setOnTouchListener((view, event) -> true);
        attach(text);
    }
    @After public void cleanup() { controller.pause().stop().destroy(); }
    private void attach(View view) {
        root.removeAllViews(); content = view; content.setBackgroundColor(Color.CYAN);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        PullToRefresh.attach(content, () -> { calls.incrementAndGet(); ready = false; },
                () -> ready, () -> scrolled || content.canScrollVertically(-1));
        refresh = (PullRefreshLayout) content.getParent();
        layout();
    }
    private void layout() {
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 800);
    }
    private void touch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) down = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        refresh.dispatchTouchEvent(event); event.recycle();
        frames(16);
    }
    private void touch(int action, float y) { touch(action, 200, y); }
    private void frames(int millis) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)); }
    private Bitmap draw() {
        Bitmap bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888);
        refresh.draw(new Canvas(bitmap)); return bitmap;
    }
    private int ringPixels(Bitmap bitmap) {
        int count = 0;
        for (int y = 0; y < Math.min(200, (int) content.getTranslationY()); y++)
            for (int x = 150; x < 250; x++) if (Color.alpha(bitmap.getPixel(x, y)) > 0) count++;
        return count;
    }
    private void dragToBottom() {
        touch(MotionEvent.ACTION_DOWN, 80);
        for (int y = 100; y <= 740; y += 20) touch(MotionEvent.ACTION_MOVE, y);
    }
    private void finish() {
        ready = true; PullToRefresh.finish(content); frames(300); layout();
        assertFalse(refresh.isRefreshing()); assertEquals(0f, content.getTranslationY(), .01f);
        assertEquals(View.GONE, spinner().getVisibility());
    }
    private ProgressBar spinner() {
        for (int i = 0; i < refresh.getChildCount(); i++)
            if (refresh.getChildAt(i) instanceof ProgressBar) return (ProgressBar) refresh.getChildAt(i);
        throw new AssertionError("Expected an Android ProgressBar");
    }
    @Test public void contentFollowsFingerAndArcGrowsToAFullCircleWithoutSpinning() {
        touch(MotionEvent.ACTION_DOWN, 80); touch(MotionEvent.ACTION_MOVE, 170);
        float shortOffset = content.getTranslationY();
        Bitmap shortArc = draw(); int shortPixels = ringPixels(shortArc); shortArc.recycle();
        assertTrue(shortOffset > 0); assertTrue(shortPixels > 0);
        touch(MotionEvent.ACTION_MOVE, 500);
        assertTrue(content.getTranslationY() > shortOffset);
        Bitmap full = draw(); assertTrue(ringPixels(full) > shortPixels);
        frames(1200); Bitmap held = draw();
        assertTrue("The full ring stays still until release", full.sameAs(held));
        assertTrue("The page itself moves down", Color.alpha(full.getPixel(20, 20)) == 0);
        assertEquals(Color.CYAN, full.getPixel(20, 300));
        full.recycle(); held.recycle(); assertEquals(0, calls.get());
    }
    @Test public void releaseSpinsUntilLoadCompletesAndCannotRefreshTwice() {
        dragToBottom(); touch(MotionEvent.ACTION_UP, 740); frames(300);
        assertTrue(refresh.isRefreshing()); assertEquals(1, calls.get());
        assertTrue(content.getTranslationY() > 0);
        ProgressBar spinner = spinner();
        assertTrue(spinner.isIndeterminate()); assertEquals(View.VISIBLE, spinner.getVisibility());
        assertTrue(spinner.getIndeterminateDrawable() instanceof android.graphics.drawable.AnimatedVectorDrawable);
        dragToBottom(); touch(MotionEvent.ACTION_UP, 740);
        assertEquals(1, calls.get()); assertTrue(refresh.isRefreshing());
        finish();
        dragToBottom(); touch(MotionEvent.ACTION_UP, 740); assertEquals(2, calls.get()); finish();
    }
    @Test public void loadingRingHasTheSamePaintedDiameterAndStrokeAsThePullRing() {
        assertMatchingRingGeometry();
    }
    @Test @Config(qualifiers = "420dpi")
    public void ringGeometryAlsoMatchesAtFractionalDensity() {
        assertMatchingRingGeometry();
    }
    private void assertMatchingRingGeometry() {
        dragToBottom(); layout();
        Bitmap pulled = draw();
        int size = spinner().getLayoutParams().width;
        float cx = spinner().getX() + size / 2f, cy = spinner().getY() + size / 2f;
        Bitmap loading = Bitmap.createBitmap(pulled.getWidth(), pulled.getHeight(), Bitmap.Config.ARGB_8888);
        android.graphics.drawable.Drawable drawable = controller.get().getDrawable(com.fimtale.R.drawable.pull_refresh_loading);
        // An unstarted drawable shows the complete path, so compare geometry independently of animation phase.
        drawable.setBounds(0, 0, size, size);
        Canvas canvas = new Canvas(loading); canvas.translate(cx - size / 2f, cy - size / 2f); drawable.draw(canvas);
        android.graphics.Rect pulledBounds = ringBounds(pulled, (int) content.getTranslationY());
        android.graphics.Rect loadingBounds = ringBounds(loading, loading.getHeight());
        assertTrue(pulledBounds.width() > 0);
        assertEquals("Painted outer bounds", pulledBounds, loadingBounds);
        int row = (int) cy;
        int pulledStroke = 0, loadingStroke = 0;
        for (int x = pulledBounds.left; x < cx; x++) {
            if (Color.alpha(pulled.getPixel(x, row)) >= 160) pulledStroke++;
            if (Color.alpha(loading.getPixel(x, row)) >= 160) loadingStroke++;
        }
        assertEquals("Painted stroke width", pulledStroke, loadingStroke);
        pulled.recycle(); loading.recycle();
    }
    private android.graphics.Rect ringBounds(Bitmap bitmap, int height) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        for (int y = 0; y < height; y++) for (int x = 0; x < bitmap.getWidth(); x++)
            // Ignore the half-covered antialias fringe, which differs between Canvas arcs and vectors.
            if (Color.alpha(bitmap.getPixel(x, y)) >= 160) bounds.union(x, y, x + 1, y + 1);
        return bounds;
    }
    @Test public void nestedScrollingConsumesPullAndRetreatAndRefreshesOnlyAfterRelease() {
        assertTrue(refresh.onStartNestedScroll(content, content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH));
        refresh.onNestedScrollAccepted(content, content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH);
        int[] consumed = new int[2];
        refresh.onNestedScroll(content, 0, 0, 0, -300, ViewCompat.TYPE_TOUCH, consumed);
        assertEquals(-300, consumed[1]); assertTrue(content.getTranslationY() > 0);
        assertEquals(0, calls.get());
        consumed[1] = 0;
        refresh.onNestedPreScroll(content, 0, 300, consumed, ViewCompat.TYPE_TOUCH);
        assertEquals(300, consumed[1]); assertEquals(0f, content.getTranslationY(), .01f);
        refresh.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH); frames(300); assertEquals(0, calls.get());
        refresh.onNestedScrollAccepted(content, content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH);
        refresh.onNestedScroll(content, 0, 0, 0, -600, ViewCompat.TYPE_TOUCH, new int[2]);
        frames(1000); Bitmap held = draw(); assertTrue(ringPixels(held) > 100); held.recycle();
        refresh.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH);
        assertEquals(1, calls.get()); assertTrue(refresh.isRefreshing()); finish();
        assertFalse(refresh.onStartNestedScroll(content, content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_NON_TOUCH));
    }
    @Test public void insufficientPullRetreatCancelAndHorizontalSwipesDoNotRefresh() {
        touch(MotionEvent.ACTION_DOWN, 80); touch(MotionEvent.ACTION_MOVE, 130); touch(MotionEvent.ACTION_UP, 130); frames(300);
        assertEquals(0, calls.get()); assertEquals(0f, content.getTranslationY(), .01f);
        dragToBottom(); touch(MotionEvent.ACTION_MOVE, 100); touch(MotionEvent.ACTION_UP, 100); frames(300);
        assertEquals(0, calls.get()); assertFalse(refresh.isRefreshing());
        dragToBottom(); touch(MotionEvent.ACTION_CANCEL, 740); frames(300);
        assertEquals(0, calls.get()); assertEquals(0f, content.getTranslationY(), .01f);
        touch(MotionEvent.ACTION_DOWN, 40, 80); touch(MotionEvent.ACTION_MOVE, 350, 180); touch(MotionEvent.ACTION_UP, 350, 180);
        assertEquals(0, calls.get()); assertEquals(0f, content.getTranslationY(), .01f);
    }
    @Test public void unavailableOrScrolledPagesDoNotPull() {
        ready = false; dragToBottom(); touch(MotionEvent.ACTION_UP, 740); frames(300);
        assertEquals(0, calls.get()); assertEquals(0f, content.getTranslationY(), .01f);
        ready = true; scrolled = true; dragToBottom(); touch(MotionEvent.ACTION_UP, 740); frames(300);
        assertEquals(0, calls.get()); assertEquals(0f, content.getTranslationY(), .01f);
    }
    @Test public void realRecyclerViewAndNestedScrollViewUseTheSameGesture() {
        RecyclerView list = new RecyclerView(controller.get()); list.setLayoutManager(new LinearLayoutManager(controller.get()));
        list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public int getItemCount() { return 30; }
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                TextView text = new TextView(parent.getContext()); text.setLayoutParams(new ViewGroup.LayoutParams(-1, 80));
                return new RecyclerView.ViewHolder(text) {};
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) { ((TextView) holder.itemView).setText("row " + position); }
        });
        NestedScrollView scroll = new NestedScrollView(controller.get());
        TextView body = new TextView(controller.get()); body.setMinHeight(2400); scroll.addView(body);
        for (View view : new View[]{list, scroll}) {
            attach(view); int before = calls.get(); dragToBottom();
            assertTrue(content.getTranslationY() > 0); assertEquals(before, calls.get());
            touch(MotionEvent.ACTION_UP, 740); assertEquals(before + 1, calls.get()); finish();
        }
    }
    @Test public void detachingStopsLoadingAndRestoresTheContentPosition() {
        dragToBottom(); touch(MotionEvent.ACTION_UP, 740); frames(300);
        root.removeView(refresh); frames(1200);
        assertFalse(refresh.isRefreshing()); assertEquals(0f, content.getTranslationY(), .01f);
    }

    @Test public void tappingDuringReturnAnimationDoesNotStartAnotherRefresh() {
        dragToBottom(); touch(MotionEvent.ACTION_UP, 740);
        ready = true; PullToRefresh.finish(content);
        refresh.onNestedScrollAccepted(content, content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH);
        refresh.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH);
        frames(300); assertEquals(1, calls.get()); assertFalse(refresh.isRefreshing());
        assertEquals(0f, content.getTranslationY(), .01f);
    }

    @Test public void cancelledNestedDragReturnsWithoutRefreshing() {
        NestedScrollView scroll = new NestedScrollView(controller.get());
        TextView body = new TextView(controller.get()); body.setMinHeight(2400); scroll.addView(body); attach(scroll);
        dragToBottom(); assertTrue(content.getTranslationY() > 0);
        touch(MotionEvent.ACTION_CANCEL, 740); frames(300);
        assertEquals(0, calls.get()); assertFalse(refresh.isRefreshing());
        assertEquals(0f, content.getTranslationY(), .01f);
    }

    @Test public void ringSitsBelowTheFloatingToolbarInset() {
        content.setPadding(0, 88, 0, 0); dragToBottom(); touch(MotionEvent.ACTION_UP, 740); frames(300);
        ProgressBar spinner = spinner();
        assertTrue("Indicator must be below the toolbar", spinner.getY() >= 88);
        assertEquals(refresh.getWidth() / 2f, spinner.getX() + spinner.getWidth() / 2f, .5f);
        assertEquals(88 + content.getTranslationY() / 2, spinner.getY() + spinner.getHeight() / 2f, .5f);
        assertEquals(spinner, refresh.getChildAt(refresh.getChildCount() - 1));
        finish();
    }
}
