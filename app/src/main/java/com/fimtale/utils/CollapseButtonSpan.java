package com.fimtale.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.View;
import com.fimtale.R;
import com.google.android.material.button.MaterialButton;

/** Uses an ordinary MaterialButton's theme, shape, typography and padding in native rich text. */
public final class CollapseButtonSpan extends ReplacementSpan {
    private final MaterialButton button;
    private int availableWidth;

    public CollapseButtonSpan(Context context, String title) {
        TypedValue material = new TypedValue();
        if (!context.getTheme().resolveAttribute(com.google.android.material.R.attr.isMaterialTheme, material, true)
                || material.data == 0) context = new ContextThemeWrapper(context, R.style.Theme_Fimtale);
        button = new MaterialButton(context);
        button.setText(title);
        button.setSingleLine(false);
        availableWidth = Math.max(1, context.getResources().getDisplayMetrics().widthPixels
                - Math.round(48 * context.getResources().getDisplayMetrics().density));
    }

    public static void prepare(CharSequence text, int width) {
        if (!(text instanceof Spanned)) return;
        for (CollapseButtonSpan span : ((Spanned) text).getSpans(0, text.length(), CollapseButtonSpan.class))
            span.availableWidth = Math.max(1, width);
    }

    private void measure() {
        button.measure(View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        button.layout(0, 0, button.getMeasuredWidth(), button.getMeasuredHeight());
    }

    @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        measure();
        if (metrics != null) {
            metrics.top = metrics.ascent = -button.getMeasuredHeight();
            metrics.bottom = metrics.descent = 0;
        }
        return button.getMeasuredWidth();
    }

    @Override public void draw(Canvas canvas, CharSequence text, int start, int end,
            float x, int top, int baseline, int bottom, Paint paint) {
        measure();
        int saved = canvas.save();
        canvas.translate(x, baseline - button.getMeasuredHeight());
        button.draw(canvas);
        canvas.restoreToCount(saved);
    }
}
