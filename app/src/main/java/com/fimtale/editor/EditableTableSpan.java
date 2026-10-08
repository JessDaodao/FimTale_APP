package com.fimtale.editor;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import com.fimtale.utils.BbCode;
import com.fimtale.utils.BbCodeText;
import com.google.android.material.color.MaterialColors;
import io.noties.markwon.Markwon;
import java.util.ArrayList;
import java.util.List;

/** Draws the table in the document and exposes cell bounds for an inline input overlay. */
final class EditableTableSpan extends ReplacementSpan implements AutoCloseable {
    static final class Cell {
        final EditorTable.Cell source;
        final RectF bounds = new RectF();
        final Spanned text;
        StaticLayout layout;
        Cell(EditorTable.Cell source, Spanned text) { this.source = source; this.text = text; }
    }
    final EditorTable table;
    final List<Cell> cells = new ArrayList<>();
    final RectF bounds = new RectF();
    final int padding;
    private final BbCodeEditText editor;
    private int width, height;
    private float textSize;
    private int color;
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<io.noties.markwon.image.AsyncDrawable> images = new ArrayList<>();
    private boolean scheduled;
    private int leadingMargin;

    void setLeadingMargin(int margin) {
        if (leadingMargin != margin) { leadingMargin = margin; width = 0; }
    }

    EditableTableSpan(BbCodeEditText editor, Markwon renderer, EditorTable table) {
        this.editor = editor; this.table = table;
        padding = Math.round(8 * editor.getResources().getDisplayMetrics().density);
        for (EditorTable.Cell cell : table.cells) {
            String source = table.source.substring(cell.node.contentStart, cell.node.contentEnd);
            Spanned text = BbCodeText.normalizeTables(renderer.toMarkdown(BbCode.toMarkdown(source)));
            cells.add(new Cell(cell, text));
            for (io.noties.markwon.image.AsyncDrawableSpan image : text.getSpans(0, text.length(), io.noties.markwon.image.AsyncDrawableSpan.class))
                images.add(image.getDrawable());
        }
        prepare();
    }
    void prepare() {
        int available = editor.getWidth() - editor.getCompoundPaddingLeft() - editor.getCompoundPaddingRight();
        if (available <= 0) available = editor.getResources().getDisplayMetrics().widthPixels - padding * 4;
        available = Math.max(table.columns * 4, available - leadingMargin);
        if (width == available && textSize == editor.getTextSize() && color == editor.getCurrentTextColor()
                && (scheduled || !editor.isAttachedToWindow())) return;
        width = available; textSize = editor.getTextSize(); color = editor.getCurrentTextColor();
        float unit = width / (float) table.columns;
        int[] heights = new int[table.rows];
        java.util.Arrays.fill(heights, Math.round(textSize * 1.4f) + padding * 2);
        for (Cell cell : cells) {
            TextPaint paint = new TextPaint(editor.getPaint()); paint.setColor(color);
            if (cell.source.node.name.equals("th")) paint.setTypeface(Typeface.create(paint.getTypeface(), Typeface.BOLD));
            int w = Math.max(1, Math.round(unit * cell.source.columnSpan) - padding * 2);
            for (io.noties.markwon.image.AsyncDrawableSpan image : cell.text.getSpans(0, cell.text.length(), io.noties.markwon.image.AsyncDrawableSpan.class))
                image.getDrawable().initWithKnownDimensions(w, textSize);
            BbCodeText.prepare(cell.text, paint, w);
            String align = cell.source.node.attributes.getOrDefault("align", "left");
            cell.layout = StaticLayout.Builder.obtain(cell.text, 0, cell.text.length(), paint, w)
                    .setAlignment(align.equals("center") ? Layout.Alignment.ALIGN_CENTER
                            : align.equals("right") ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false).setLineSpacing(editor.getLineSpacingExtra(), editor.getLineSpacingMultiplier()).build();
            int current = 0;
            for (int r = cell.source.row; r < cell.source.row + cell.source.rowSpan; r++) current += heights[r];
            int extra = Math.max(0, cell.layout.getHeight() + padding * 2 - current);
            heights[cell.source.row + cell.source.rowSpan - 1] += extra;
        }
        int[] top = new int[table.rows + 1];
        for (int r = 0; r < heights.length; r++) top[r + 1] = top[r] + heights[r];
        height = top[table.rows];
        for (Cell cell : cells) cell.bounds.set(unit * cell.source.column, top[cell.source.row],
                unit * (cell.source.column + cell.source.columnSpan), top[cell.source.row + cell.source.rowSpan]);
        if (!scheduled && editor.isAttachedToWindow()) {
            scheduled = true;
            for (io.noties.markwon.image.AsyncDrawable image : images) image.setCallback2(new android.graphics.drawable.Drawable.Callback() {
                @Override public void invalidateDrawable(android.graphics.drawable.Drawable drawable) {
                    width = 0; editor.refreshVisuals();
                }
                @Override public void scheduleDrawable(android.graphics.drawable.Drawable drawable, Runnable runnable, long when) {
                    editor.postDelayed(runnable, Math.max(0, when - android.os.SystemClock.uptimeMillis()));
                }
                @Override public void unscheduleDrawable(android.graphics.drawable.Drawable drawable, Runnable runnable) { editor.removeCallbacks(runnable); }
            });
        }
    }
    Cell hit(float x, float y) {
        if (!bounds.contains(x, y)) return null;
        for (Cell cell : cells) if (cell.bounds.contains(x - bounds.left, y - bounds.top)) return cell;
        return null;
    }
    @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
        if (metrics != null) { metrics.ascent = metrics.top = -height; metrics.descent = metrics.bottom = 0; }
        return width;
    }
    @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        bounds.set(x, y - height, x + width, y);
        int save = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        int stroke = MaterialColors.getColor(editor, com.google.android.material.R.attr.colorOutline);
        int header = MaterialColors.getColor(editor, com.google.android.material.R.attr.colorSurfaceVariant);
        for (Cell cell : cells) {
            if (cell.source.node.name.equals("th")) {
                border.setStyle(Paint.Style.FILL); border.setColor(header); canvas.drawRect(cell.bounds, border);
            }
            border.setStyle(Paint.Style.STROKE); border.setStrokeWidth(1); border.setColor(stroke);
            canvas.drawRect(cell.bounds, border);
            int cellSave = canvas.save();
            canvas.clipRect(cell.bounds);
            canvas.translate(cell.bounds.left + padding, cell.bounds.top + padding);
            cell.layout.draw(canvas);
            canvas.restoreToCount(cellSave);
        }
        canvas.restoreToCount(save);
    }
    @Override public void close() {
        for (io.noties.markwon.image.AsyncDrawable image : images) image.setCallback2(null);
    }
}
