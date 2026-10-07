package com.fimtale.ui;

import android.view.View;
import android.view.ViewGroup;
import java.util.function.BooleanSupplier;

/** Adds the shared pull gesture; pages finish it when their asynchronous load completes. */
public final class PullToRefresh {
    private PullToRefresh() {}

    public static void attach(View content, Runnable refresh, BooleanSupplier ready) {
        attach(content, refresh, ready, () -> content.canScrollVertically(-1));
    }

    public static void attach(View content, Runnable refresh, BooleanSupplier ready, BooleanSupplier scrolled) {
        ViewGroup parent = (ViewGroup) content.getParent();
        int index = parent.indexOfChild(content);
        ViewGroup.LayoutParams params = content.getLayoutParams();
        parent.removeView(content);
        PullRefreshLayout swipe = new PullRefreshLayout(content.getContext(), null);
        swipe.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        parent.addView(swipe, index, params);
        swipe.setOnChildScrollUpCallback((layout, child) -> !ready.getAsBoolean() || scrolled.getAsBoolean());
        swipe.setOnRefreshListener(() -> {
            if (ready.getAsBoolean()) refresh.run();
            else swipe.setRefreshing(false);
        });
    }

    private static PullRefreshLayout find(View content) {
        for (View view = content; view != null; view = view.getParent() instanceof View ? (View) view.getParent() : null)
            if (view instanceof PullRefreshLayout) return (PullRefreshLayout) view;
        return null;
    }
    public static boolean isRefreshing(View content) {
        PullRefreshLayout refresh = find(content);
        return refresh != null && refresh.isRefreshing();
    }
    public static void finish(View content) {
        PullRefreshLayout refresh = find(content);
        if (refresh != null) refresh.setRefreshing(false);
    }
}
