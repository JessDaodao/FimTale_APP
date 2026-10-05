package com.fimtale.ui;

import android.view.View;
import android.view.ViewGroup;
import java.util.function.BooleanSupplier;

/** Adds the shared pull gesture while the page retains its existing loading feedback. */
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
        SkeletonRefreshLayout swipe = new SkeletonRefreshLayout(content.getContext(), null);
        swipe.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        parent.addView(swipe, index, params);
        swipe.setOnChildScrollUpCallback((layout, child) -> !ready.getAsBoolean() || scrolled.getAsBoolean());
        swipe.setOnRefreshListener(() -> {
            swipe.setRefreshing(false);
            if (ready.getAsBoolean()) refresh.run();
        });
    }
}
