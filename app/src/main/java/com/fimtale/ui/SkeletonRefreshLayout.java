package com.fimtale.ui;

import android.content.Context;
import android.util.AttributeSet;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/** Shows drag feedback until release; pages then supply their own skeleton loading state. */
public final class SkeletonRefreshLayout extends SwipeRefreshLayout {
    public SkeletonRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
