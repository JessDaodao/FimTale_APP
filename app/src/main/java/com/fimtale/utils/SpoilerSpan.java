package com.fimtale.utils;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.method.Touch;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import io.noties.markwon.ext.tables.TableRowSpan;
import io.noties.markwon.image.AsyncDrawableSpan;
import io.noties.markwon.utils.SpanUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.WeakHashMap;

/** An inline mask: revealing it never replaces text or changes page offsets. */
public final class SpoilerSpan extends ClickableSpan {
    private boolean revealed;
    private int maskColor = 0xcc000000;
    private final WeakHashMap<TextView, Object> views = new WeakHashMap<>();

    public boolean isRevealed() { return revealed; }

    @Override public void updateDrawState(TextPaint paint) {
        if (revealed) return;
        paint.setColor(Color.TRANSPARENT);
        paint.bgColor = maskColor;
        paint.setUnderlineText(false);
        paint.setStrikeThruText(false);
        paint.clearShadowLayer();
    }

    @Override public void onClick(View widget) {
        if (revealed) return;
        revealed = true;
        if (widget instanceof TextView) bind((TextView) widget);
        // Page/paragraph slices share the same span, so refresh every bound slice.
        for (TextView view : new ArrayList<>(views.keySet())) {
            if (!(view.getText() instanceof Spannable)) continue;
            Spannable text = (Spannable) view.getText();
            Object anchor = views.get(view);
            int start = text.getSpanStart(anchor), end = text.getSpanEnd(anchor);
            if (start < 0) { views.remove(view); continue; }
            int flags = text.getSpanFlags(anchor);
            text.removeSpan(anchor);
            text.setSpan(anchor, start, end, flags);
            view.invalidate();
        }
    }

    /** Apply after all other styling so inner links/colors cannot expose text. */
    public static void prepare(Spannable text) {
        SpoilerSpan[] spoilers = text.getSpans(0, text.length(), SpoilerSpan.class);
        if (spoilers.length == 0) return;
        // ReplacementSpan draws its own pixels, bypassing TextPaint's text color.
        // Keep the original image/table span so loading and measurement still work.
        for (ReplacementSpan replacement : text.getSpans(0, text.length(), ReplacementSpan.class)) {
            if (replacement instanceof MaskedReplacement) continue;
            int start = text.getSpanStart(replacement), end = text.getSpanEnd(replacement);
            SpoilerSpan[] covering = text.getSpans(start, end, SpoilerSpan.class);
            if (covering.length == 0 || text.getSpans(start, end, MaskedReplacement.class).length > 0) continue;
            text.setSpan(new MaskedReplacement(replacement, covering), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        for (SpoilerSpan spoiler : spoilers) {
            int start = text.getSpanStart(spoiler), end = text.getSpanEnd(spoiler);
            text.removeSpan(spoiler);
            if (end > start) text.setSpan(spoiler, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    public static void bind(TextView view) {
        view.setMovementMethod(Movement.INSTANCE);
        if (!(view.getText() instanceof Spanned)) return;
        bind(view, (Spanned) view.getText(), null);
    }

    private static void bind(TextView view, Spanned text, Object anchor) {
        for (SpoilerSpan spoiler : text.getSpans(0, text.length(), SpoilerSpan.class)) {
            spoiler.maskColor = ColorUtils.setAlphaComponent(view.getCurrentTextColor(), 204);
            spoiler.views.put(view, anchor == null ? spoiler : anchor);
        }
        for (TableRowSpan row : text.getSpans(0, text.length(), TableRowSpan.class)) {
            for (Layout cell : cells(row)) {
                if (cell.getText() instanceof Spanned) bind(view, (Spanned) cell.getText(), anchor == null ? row : anchor);
            }
        }
    }

    private static List<Layout> cells(TableRowSpan row) {
        List<Layout> cells = new ArrayList<>();
        int width = row.cellWidth();
        if (width <= 0) return cells;
        for (int x = 0; ; x += width) {
            Layout cell = row.findLayoutForHorizontalOffset(x);
            if (cell == null) return cells;
            cells.add(cell);
        }
    }

    /** Hidden masks take precedence over the links they cover. */
    public static ClickableSpan clickableAt(TextView view, MotionEvent event) {
        if (!(view.getText() instanceof Spanned) || view.getLayout() == null) return null;
        Layout layout = view.getLayout();
        float x = event.getX() - view.getTotalPaddingLeft() + view.getScrollX();
        int y = (int) event.getY() - view.getTotalPaddingTop() + view.getScrollY();
        return clickableAt(layout, x, y);
    }

    private static ClickableSpan clickableAt(Layout layout, float x, int y) {
        if (!(layout.getText() instanceof Spanned) || y < 0 || y >= layout.getHeight()) return null;
        int line = layout.getLineForVertical(y);
        if (x < layout.getLineLeft(line) || x >= layout.getLineRight(line)) return null;
        int offset = layout.getOffsetForHorizontal(line, x);
        // Android returns the nearest caret. Include the trailing half of each glyph.
        if (offset > layout.getLineStart(line)) {
            float previous = layout.getPrimaryHorizontal(offset - 1), current = layout.getPrimaryHorizontal(offset);
            if (x >= Math.min(previous, current) && x < Math.max(previous, current)) offset--;
        }
        Spanned text = (Spanned) layout.getText();
        ClickableSpan link = null;
        SpoilerSpan hidden = null;
        int hiddenLength = -1;
        for (ClickableSpan span : text.getSpans(offset, offset, ClickableSpan.class)) {
            int start = text.getSpanStart(span), end = text.getSpanEnd(span);
            if (offset < start || offset >= end) continue;
            if (span instanceof SpoilerSpan) {
                SpoilerSpan spoiler = (SpoilerSpan) span;
                if (!spoiler.revealed && end - start > hiddenLength) { hidden = spoiler; hiddenLength = end - start; }
            } else if (link == null) link = span;
        }
        if (hidden != null) return hidden;
        for (TableRowSpan row : text.getSpans(offset, offset, TableRowSpan.class)) {
            int rowX = (int) (x - layout.getPrimaryHorizontal(text.getSpanStart(row)));
            if (rowX < 0 || row.cellWidth() <= 0) continue;
            Layout cell = row.findLayoutForHorizontalOffset(rowX);
            if (cell == null) continue;
            int padding = (row.cellWidth() - cell.getWidth()) / 2;
            int height = 0;
            for (Layout item : cells(row)) height = Math.max(height, item.getHeight());
            int top = layout.getLineTop(line), bottom = layout.getLineBottom(line);
            ClickableSpan target = clickableAt(cell, rowX % row.cellWidth() - padding,
                    y - top - padding - (bottom - top - height) / 4);
            if (target != null) return target;
        }
        return link;
    }

    private static final class Movement extends LinkMovementMethod {
        static final Movement INSTANCE = new Movement();

        @Override public boolean onTouchEvent(TextView view, Spannable text, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
                ClickableSpan span = clickableAt(view, event);
                if (span != null) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        if (text.getSpanStart(span) >= 0)
                            Selection.setSelection(text, text.getSpanStart(span), text.getSpanEnd(span));
                    }
                    else {
                        span.onClick(view);
                        Selection.removeSelection(text);
                    }
                    return true;
                }
                // Revealed spoilers are ordinary text, rather than dead links.
                Selection.removeSelection(text);
                return Touch.onTouchEvent(view, text, event);
            }
            if (action == MotionEvent.ACTION_CANCEL) Selection.removeSelection(text);
            return super.onTouchEvent(view, text, event);
        }
    }

    private static final class MaskedReplacement extends ReplacementSpan {
        private final ReplacementSpan original;
        private final List<SpoilerSpan> spoilers;

        MaskedReplacement(ReplacementSpan original, SpoilerSpan[] spoilers) {
            this.original = original;
            this.spoilers = Arrays.asList(spoilers);
        }

        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
            return original.getSize(paint, text, start, end, metrics);
        }

        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x,
                int top, int y, int bottom, Paint paint) {
            for (SpoilerSpan spoiler : spoilers) if (!spoiler.revealed) {
                if (original instanceof AsyncDrawableSpan)
                    ((AsyncDrawableSpan) original).getDrawable().initWithKnownDimensions(
                            SpanUtils.width(canvas, text), paint.getTextSize());
                Paint.FontMetricsInt metrics = paint.getFontMetricsInt();
                int width = original.getSize(paint, text, start, end, metrics);
                Paint mask = new Paint(); mask.setColor(spoiler.maskColor);
                canvas.drawRect(x, y + metrics.ascent, x + width, y + metrics.descent, mask);
                return;
            }
            original.draw(canvas, text, start, end, x, top, y, bottom, paint);
        }
    }
}
