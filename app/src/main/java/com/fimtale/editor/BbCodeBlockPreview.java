package com.fimtale.editor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.widget.TextView;
import com.fimtale.utils.BbCode;
import com.fimtale.utils.BbCodeText;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TableRowSpan;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.AsyncDrawableSpan;
import java.util.ArrayList;
import java.util.List;

/** A read-only block drawn over source; moving the caret into it removes the decoration. */
final class BbCodeBlockPreview extends ReplacementSpan implements AutoCloseable {
    final android.graphics.RectF bounds = new android.graphics.RectF();
    private final TextView editor;
    final String source;
    private final Runnable changed;
    private final Spanned rendered;
    private final List<AsyncDrawable> images = new ArrayList<>();
    private StaticLayout layout;
    private int width;
    private int leadingMargin;
    private boolean dirty = true, active, closed;
    private float textSize;
    private int color;

    BbCodeBlockPreview(TextView editor, Markwon renderer, String source, Runnable changed) {
        this.editor = editor; this.changed = changed; this.source = source;
        Spanned original = renderer.toMarkdown(BbCode.toMarkdown(source));
        for (AsyncDrawableSpan image : original.getSpans(0, original.length(), AsyncDrawableSpan.class)) images.add(image.getDrawable());
        rendered = BbCodeText.normalizeTables(original);
        prepare();
    }
    String editableSource() {
        List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse(source);
        if (!nodes.isEmpty() && nodes.get(0).name.equals("markdown")) {
            BbCodeSyntax.Node markdown = nodes.get(0);
            return MarkdownBbCode.convert(source.substring(markdown.contentStart, markdown.contentEnd));
        }
        StringBuilder normalized = new StringBuilder(source); boolean changed = false;
        for (int i = nodes.size() - 1; i >= 0; i--) {
            BbCodeSyntax.Node node = nodes.get(i);
            if (node.name.equals("br")) { normalized.replace(node.start, node.end, "\n"); changed = true; }
        }
        return changed ? normalized.toString() : null;
    }

    private void invalidateLayout() {
        if (closed) return;
        dirty = true;
        changed.run();
    }

    void setLeadingMargin(int margin) {
        if (leadingMargin != margin) { leadingMargin = margin; dirty = true; }
    }

    void prepare() {
        int available = editor.getWidth() - editor.getCompoundPaddingLeft() - editor.getCompoundPaddingRight();
        if (available <= 0) available = editor.getResources().getDisplayMetrics().widthPixels - 32;
        available = Math.max(1, available - leadingMargin);
        if (width != available || textSize != editor.getTextSize() || color != editor.getCurrentTextColor()) dirty = true;
        if (!active && editor.isAttachedToWindow()) dirty = true;
        if (!dirty) return;
        width = available; textSize = editor.getTextSize(); color = editor.getCurrentTextColor();
        TextPaint paint = new TextPaint(editor.getPaint()); paint.setColor(color);
        for (AsyncDrawable image : images) image.initWithKnownDimensions(width, textSize);
        if (editor.isAttachedToWindow()) {
            if (!active) {
                active = true;
                for (AsyncDrawable image : images) image.setCallback2(new Drawable.Callback() {
                    @Override public void invalidateDrawable(Drawable drawable) { invalidateLayout(); }
                    @Override public void scheduleDrawable(Drawable drawable, Runnable what, long when) {
                        editor.postDelayed(what, Math.max(0, when - SystemClock.uptimeMillis()));
                    }
                    @Override public void unscheduleDrawable(Drawable drawable, Runnable what) { editor.removeCallbacks(what); }
                });
            }
        }
        BbCodeText.prepare(rendered, paint, width);
        for (TableRowSpan row : rendered.getSpans(0, rendered.length(), TableRowSpan.class)) row.invalidator(this::invalidateLayout);
        layout = StaticLayout.Builder.obtain(rendered, 0, rendered.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setUseLineSpacingFromFallbacks(true)
                .setLineSpacing(editor.getLineSpacingExtra(), editor.getLineSpacingMultiplier()).build();
        dirty = false;
    }

    @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        if (metrics != null) {
            metrics.ascent = metrics.top = -layout.getHeight();
            metrics.descent = metrics.bottom = 0;
        }
        return width;
    }
    @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        bounds.set(x, y - layout.getHeight(), x + width, y);
        canvas.save();
        canvas.translate(x, y - layout.getHeight());
        layout.draw(canvas);
        canvas.restore();
    }
    @Override public void close() {
        closed = true;
        for (AsyncDrawable image : images) image.setCallback2(null);
        for (TableRowSpan row : rendered.getSpans(0, rendered.length(), TableRowSpan.class)) row.invalidator(null);
    }
}
