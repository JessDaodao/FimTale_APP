package com.fimtale.ui;

import android.os.Handler;
import android.os.Looper;
import androidx.viewpager2.widget.ViewPager2;

/** EYPA home carousel timing: default smooth scrolling and five seconds after each selection. */
public final class BannerAutoScroll {
    private static final long INTERVAL_MS = 5000;
    private final ViewPager2 pager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean active;
    private final Runnable advance = new Runnable() {
        @Override public void run() {
            if (!active) return;
            int count = pager.getAdapter() == null ? 0 : pager.getAdapter().getItemCount();
            if (count > 1 && pager.getScrollState() == ViewPager2.SCROLL_STATE_IDLE) {
                pager.setCurrentItem((pager.getCurrentItem() + 1) % count);
            }
            schedule();
        }
    };
    private final ViewPager2.OnPageChangeCallback callback = new ViewPager2.OnPageChangeCallback() {
        @Override public void onPageSelected(int position) { schedule(); }
    };
    public BannerAutoScroll(ViewPager2 pager) {
        this.pager = pager;
        pager.registerOnPageChangeCallback(callback);
    }
    public void start() { active = true; schedule(); }
    public void stop() { active = false; handler.removeCallbacks(advance); }
    public void close() { stop(); pager.unregisterOnPageChangeCallback(callback); }
    private void schedule() {
        // Selection callbacks can run during setCurrentItem; keep just one scheduled advance.
        handler.removeCallbacks(advance);
        if (active && pager.getAdapter() != null && pager.getAdapter().getItemCount() > 1)
            handler.postDelayed(advance, INTERVAL_MS);
    }
}
