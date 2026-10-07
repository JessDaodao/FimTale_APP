package com.fimtale.utils;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import io.noties.markwon.core.spans.TextLayoutSpan;
import io.noties.markwon.ext.tables.TableRowSpan;
import io.noties.markwon.ext.tables.TableSpan;
import io.noties.markwon.ext.tables.TableTheme;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.AsyncDrawableSpan;
import java.util.ArrayList;
import java.util.List;

/** Table metrics belong to the text column, never to the window-sized drawing canvas. */
final class FittedTableRowSpan extends TableRowSpan {
    private final TableTheme theme;
    private final List<Cell> cells;
    private final List<Layout> layouts = new ArrayList<>();
    private final TextPaint textPaint = new TextPaint();
    private final Paint decoration = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean header, odd;
    private int width, cellWidth, padding, height;
    private Invalidator invalidator;

    FittedTableRowSpan(TableTheme theme, List<Cell> cells, boolean header, boolean odd) {
        super(theme, cells, header, odd);
        this.theme = theme; this.cells = cells; this.header = header; this.odd = odd;
    }

    void prepare(Paint paint, int availableWidth) {
        int nextWidth = Math.max(1, availableWidth);
        if (width == nextWidth && !layouts.isEmpty() && textPaint.equalsForTextMeasurement(paint)
                && textPaint.getColor() == paint.getColor()
                && (!(paint instanceof TextPaint) || textPaint.linkColor == ((TextPaint) paint).linkColor)) return;
        width = nextWidth;
        if (paint instanceof TextPaint) textPaint.set((TextPaint) paint);
        else textPaint.set(paint);
        cellWidth = Math.max(1, width / cells.size());
        padding = Math.min(theme.tableCellPadding(), Math.max(0, (cellWidth - (int) Math.ceil(paint.getTextSize())) / 2));
        makeLayouts();
    }

    private void makeLayouts() {
        layouts.clear();
        height = 0;
        TextPaint cellPaint = new TextPaint(textPaint);
        if (header) cellPaint.setFakeBoldText(true);
        for (Cell cell : cells) {
            Spannable text = cell.text() instanceof Spannable ? (Spannable) cell.text() : new SpannableString(cell.text());
            int contentWidth = Math.max(1, cellWidth - 2 * padding);
            BbCodeText.prepare(text, cellPaint, contentWidth);
            Layout.Alignment alignment = cell.alignment() == ALIGN_CENTER ? Layout.Alignment.ALIGN_CENTER
                    : cell.alignment() == ALIGN_RIGHT ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL;
            Layout layout = StaticLayout.Builder.obtain(text, 0, text.length(), cellPaint, contentWidth)
                    .setAlignment(alignment).setIncludePad(false).build();
            TextLayoutSpan.applyTo(text, layout);
            layouts.add(layout);
            height = Math.max(height, layout.getHeight());
            for (AsyncDrawableSpan span : text.getSpans(0, text.length(), AsyncDrawableSpan.class)) {
                AsyncDrawable drawable = span.getDrawable();
                if (drawable.isAttached()) continue;
                drawable.setCallback2(new Drawable.Callback() {
                    @Override public void invalidateDrawable(Drawable who) {
                        if (invalidator != null) { makeLayouts(); invalidator.invalidate(); }
                    }
                    @Override public void scheduleDrawable(Drawable who, Runnable what, long when) {}
                    @Override public void unscheduleDrawable(Drawable who, Runnable what) {}
                });
            }
        }
    }

    @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        prepare(paint, width);
        if (metrics != null) {
            metrics.top = metrics.ascent = -(height + 2 * padding);
            metrics.bottom = metrics.descent = 0;
        }
        return width;
    }

    @Override public void draw(Canvas canvas, CharSequence text, int start, int end,
            float x, int top, int baseline, int bottom, Paint paint) {
        int saved = canvas.save();
        canvas.translate(x, top);
        canvas.clipRect(0, 0, width, bottom - top);
        if (header) theme.applyTableHeaderRowStyle(decoration);
        else if (odd) theme.applyTableOddRowStyle(decoration);
        else theme.applyTableEvenRowStyle(decoration);
        if (decoration.getColor() != 0) canvas.drawRect(0, 0, width, bottom - top, decoration);

        decoration.set(paint);
        theme.applyTableBorderStyle(decoration);
        int border = theme.tableBorderWidth(decoration);
        if (border > 0) {
            if (text instanceof Spanned) for (TableSpan table : ((Spanned) text).getSpans(start, end, TableSpan.class)) {
                if (((Spanned) text).getSpanStart(table) == start) {
                    canvas.drawRect(0, 0, width, border, decoration);
                    break;
                }
            }
            canvas.drawRect(0, bottom - top - border, width, bottom - top, decoration);
            canvas.drawRect(0, 0, border, bottom - top, decoration);
            canvas.drawRect(width - border, 0, width, bottom - top, decoration);
        }
        // Match the row's vertical placement used by shared cell hit testing.
        int contentTop = padding + (bottom - top - height) / 4;
        for (int i = 0; i < layouts.size(); i++) {
            int left = i * cellWidth;
            if (i > 0 && border > 0) canvas.drawRect(left, 0, left + border, bottom - top, decoration);
            int cellSaved = canvas.save();
            canvas.clipRect(left + padding, 0, Math.min(width, left + cellWidth - padding), bottom - top);
            canvas.translate(left + padding, contentTop);
            layouts.get(i).draw(canvas);
            canvas.restoreToCount(cellSaved);
        }
        canvas.restoreToCount(saved);
    }

    @Override public int cellWidth() { return cellWidth; }

    @Override public Layout findLayoutForHorizontalOffset(int x) {
        if (x < 0 || cellWidth <= 0) return null;
        int index = x / cellWidth;
        return index < layouts.size() ? layouts.get(index) : null;
    }

    @Override public void invalidator(Invalidator invalidator) { this.invalidator = invalidator; }
}
