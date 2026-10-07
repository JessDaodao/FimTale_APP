package com.fimtale.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import com.google.android.material.color.MaterialColors;

/** Drag across a grid to choose the table's row and column count. */
public final class TableSizePicker extends View {
    public interface Listener { void selected(int rows, int columns); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int rows = 3, columns = 3;
    private Listener listener;
    public TableSizePicker(Context context) {
        super(context);
        setFocusable(true);
        setSelection(3, 3);
    }
    public void setListener(Listener listener) { this.listener = listener; }
    public void setSelection(int rows, int columns) {
        this.rows = Math.max(1, Math.min(20, rows));
        this.columns = Math.max(1, Math.min(10, columns));
        setContentDescription("表格大小：" + this.rows + " 行，" + this.columns + " 列；可拖动选择或使用下方行列输入框");
        invalidate();
    }
    @Override protected void onMeasure(int width, int height) {
        int w = resolveSize(Math.round(300 * getResources().getDisplayMetrics().density), width);
        setMeasuredDimension(w, resolveSize(Math.round(320 * getResources().getDisplayMetrics().density), height));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = getWidth() / 10f;
        float rowHeight = getHeight() / 20f;
        int selected = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimaryContainer);
        int border = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline);
        for (int r = 0; r < 20; r++) for (int c = 0; c < 10; c++) {
            float left = c * size + 2, top = r * rowHeight + 2;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(r < rows && c < columns ? selected : android.graphics.Color.TRANSPARENT);
            canvas.drawRoundRect(left, top, (c + 1) * size - 2, (r + 1) * rowHeight - 2, 3, 3, paint);
            paint.setColor(border); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1);
            canvas.drawRoundRect(left, top, (c + 1) * size - 2, (r + 1) * rowHeight - 2, 3, 3, paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (getWidth() == 0) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            float unit = getWidth() / 10f;
            setSelection((int) (event.getY() / (getHeight() / 20f)) + 1, (int) (event.getX() / unit) + 1);
            if (listener != null) listener.selected(rows, columns);
            if (action == MotionEvent.ACTION_UP) { getParent().requestDisallowInterceptTouchEvent(false); performClick(); }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) getParent().requestDisallowInterceptTouchEvent(false);
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
