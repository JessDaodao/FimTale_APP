package com.fimtale.ui;

import android.content.Context;
import android.content.Intent;
import android.text.Spanned;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;
import com.fimtale.ImagePreviewActivity;
import com.fimtale.network.SiteUrls;
import io.noties.markwon.image.AsyncDrawableSpan;

/** Shared entry point for image views and inline BBCode images. */
public final class ImagePreview {
    private ImagePreview() {}

    public static void open(Context context, String source) {
        String url = SiteUrls.media(source);
        if (url == null) return;
        Intent intent = new Intent(context, ImagePreviewActivity.class).putExtra("image_url", url);
        if (!(context instanceof android.app.Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void bind(View view, String source) {
        String url = SiteUrls.media(source);
        view.setOnClickListener(url == null ? null : v -> open(v.getContext(), url));
        view.setClickable(url != null);
    }

    public static Spanned images(Spanned source) {
        SpannableString result = new SpannableString(source);
        for (AsyncDrawableSpan image : result.getSpans(0, result.length(), AsyncDrawableSpan.class)) {
            String url = image.getDrawable().getDestination();
            int start = result.getSpanStart(image), end = result.getSpanEnd(image);
            // Preserve explicit links and don't turn inline emoticons into photo buttons.
            if (end <= start || url.contains("/img/ftemoji/") || SiteUrls.media(url) == null
                    || result.getSpans(start, end, ClickableSpan.class).length > 0) continue;
            result.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) { open(widget.getContext(), url); }
                @Override public void updateDrawState(TextPaint paint) {}
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return result;
    }
}
