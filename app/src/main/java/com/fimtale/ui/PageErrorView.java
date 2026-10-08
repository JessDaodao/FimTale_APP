package com.fimtale.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.fimtale.R;

/** Shared, retryable content error. Navigation and previously loaded data remain intact. */
public final class PageErrorView extends FrameLayout {
    private View content;
    private int previousAccessibility;
    private Runnable retry;
    private String lastMessage, dismissedMessage;
    public PageErrorView(Context context) { this(context, null); }
    public PageErrorView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.view_page_error, this, true);
        setVisibility(GONE);
        findViewById(R.id.pageErrorRetry).setOnClickListener(view -> {
            Runnable action = retry;
            hide();
            if (action != null) action.run();
        });
        findViewById(R.id.pageErrorContinue).setOnClickListener(view -> {
            String message = lastMessage;
            hide(); dismissedMessage = message;
        });
    }
    /** Wrap the refresh container too, keeping the error overlay outside the translated content. */
    public static PageErrorView wrap(View content) {
        View anchor = content.getParent() instanceof PullRefreshLayout ? (View) content.getParent() : content;
        ViewGroup parent = (ViewGroup) anchor.getParent();
        // Reader page holders reuse their views when binding another chapter.
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof PageErrorView && ((PageErrorView) child).content == anchor)
                return (PageErrorView) child;
        }
        int index = parent.indexOfChild(anchor);
        ViewGroup.LayoutParams params = anchor.getLayoutParams();
        FrameLayout host = new FrameLayout(content.getContext());
        parent.removeView(anchor);
        host.addView(anchor, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        PageErrorView error = new PageErrorView(content.getContext());
        error.content = anchor;
        host.addView(error, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        parent.addView(host, index, params);
        return error;
    }
    public void show(String message, Runnable retry) { show(message, retry, false); }
    public void show(String message, Runnable retry, boolean hasContent) {
        String detail = message == null || message.trim().isEmpty()
                ? getContext().getString(R.string.page_error_message) : message;
        if (detail.equals(dismissedMessage)) return;
        lastMessage = detail;
        this.retry = retry;
        ((TextView) findViewById(R.id.pageErrorMessage)).setText(detail);
        findViewById(R.id.pageErrorContinue).setVisibility(hasContent ? VISIBLE : GONE);
        if (getVisibility() != VISIBLE && content != null) {
            previousAccessibility = content.getImportantForAccessibility();
            content.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        setVisibility(VISIBLE);
    }
    public void hide() {
        if (getVisibility() == VISIBLE && content != null) content.setImportantForAccessibility(previousAccessibility);
        retry = null; dismissedMessage = null; setVisibility(GONE);
    }
}
