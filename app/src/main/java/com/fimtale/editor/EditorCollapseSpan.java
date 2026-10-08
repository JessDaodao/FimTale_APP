package com.fimtale.editor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.LeadingMarginSpan;
import androidx.core.graphics.ColorUtils;

/** An open, titled container around the original editable text, including nested formatting. */
final class EditorCollapseSpan implements LeadingMarginSpan {
    final String title;
    final RectF bounds = new RectF();
    final RectF titleBounds = new RectF();
    final int headerHeight;
    final BbCodeSyntax.Node node;
    private final BbCodeEditText editor;
    final int padding;
    private final int headerOffset;
    private final TextPaint titlePaint;

    EditorCollapseSpan(BbCodeEditText editor, BbCodeSyntax.Node node, int headerOffset) {
        this.editor = editor;
        this.node = node;
        String title = node.argument;
        this.title = title.trim().isEmpty() ? "点击展开" : VisualEditing.decodeEntities(title).replace('\n', ' ');
        this.headerOffset = headerOffset;
        padding = Math.max(1, Math.round(12 * editor.getResources().getDisplayMetrics().density));
        titlePaint = new TextPaint(editor.getPaint());
        titlePaint.setTypeface(Typeface.create(editor.getTypeface(), Typeface.BOLD));
        titlePaint.setTextSize(editor.getTextSize() * .9f);
        headerHeight = (int) Math.ceil(titlePaint.descent() - titlePaint.ascent()) + padding * 2;
    }

    @Override public int getLeadingMargin(boolean first) { return padding; }

    @Override public void drawLeadingMargin(Canvas canvas, Paint paint, int x, int dir, int top, int baseline,
            int bottom, CharSequence text, int start, int end, boolean first, Layout layout) {
        int from = node.start, to = node.end;
        if (start >= to || end <= from) return;
        int firstLine = layout.getLineForOffset(from);
        int lastLine = layout.getLineForOffset(to - 1);
        float edge = Math.min(padding / 2f, editor.getTotalPaddingRight() / 2f);
        bounds.set(Math.max(0, x), layout.getLineTop(firstLine) + headerOffset, layout.getWidth() + edge, layout.getLineBottom(lastLine));
        titleBounds.set(bounds.left, bounds.top, bounds.right, bounds.top + headerHeight);
        // TextView can start drawing midway through a long collapse while its title is offscreen.
        int save = canvas.save();
        canvas.clipRect(bounds.left, top, bounds.right, bottom);
        Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
        box.setColor(ColorUtils.setAlphaComponent(editor.getCurrentTextColor(), 12));
        canvas.drawRoundRect(bounds, padding, padding, box);
        titlePaint.setColor(editor.getCurrentTextColor());
        CharSequence label = TextUtils.ellipsize("▾ " + title, titlePaint,
                Math.max(1, bounds.width() - padding * 2), TextUtils.TruncateAt.END);
        canvas.drawText(label, 0, label.length(), bounds.left + padding,
                bounds.top + padding - titlePaint.ascent(), titlePaint);
        canvas.restoreToCount(save);
    }
}
