package com.fimtale.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/** Retains pull-to-refresh gestures while the page supplies its own skeleton indicator. */
public final class SkeletonRefreshLayout extends SwipeRefreshLayout {
    private final View refreshIndicator;
    public SkeletonRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        // SwipeRefreshLayout creates its indicator before XML content is inflated.
        refreshIndicator = getChildAt(0);
    }
    @Override protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child != refreshIndicator) return super.drawChild(canvas, child, drawingTime);
        // SwipeRefreshLayout delivers onRefresh when the indicator's animation ends.
        // Keep drawing it transparently so that animation can advance and finish.
        int saved = canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0);
        try { return super.drawChild(canvas, child, drawingTime); }
        finally { canvas.restoreToCount(saved); }
    }
}
