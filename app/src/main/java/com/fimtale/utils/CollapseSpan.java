package com.fimtale.utils;

import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.TextView;
import com.fimtale.ui.CollapseSheet;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.lang.ref.WeakReference;

/** Opens concealed content without modifying the article or its reading position. */
public final class CollapseSpan extends ClickableSpan {
    private final String title;
    private final String source;
    private WeakReference<BottomSheetDialog> openSheet = new WeakReference<>(null);

    public CollapseSpan(String title, String source) {
        this.title = title;
        this.source = source;
    }

    @Override public void onClick(View widget) {
        if (!(widget instanceof TextView)) return;
        BottomSheetDialog existing = openSheet.get();
        if (existing != null && existing.isShowing()) return;
        openSheet = new WeakReference<>(CollapseSheet.show((TextView) widget, title, source));
    }

    @Override public void updateDrawState(TextPaint paint) {
        paint.setUnderlineText(false);
    }
}
