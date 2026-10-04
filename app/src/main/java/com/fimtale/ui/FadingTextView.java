package com.fimtale.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

public class FadingTextView extends AppCompatTextView {

    private Paint fadePaint;
    private int fadeHeight;

    public FadingTextView(@NonNull Context context) {
        super(context);
        init();
    }

    public FadingTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FadingTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        fadePaint = new Paint();
        fadeHeight = (int) (getTextSize() * 2.5f);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader(w, h);
    }

    private void updateShader(int w, int h) {
        if (h > 0 && getLineCount() > getMaxLines()) {
            int startY = h - fadeHeight;
            int endY = h;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (getLineCount() > getMaxLines()) {
            int w = getWidth();
            int h = getHeight();
            
            int bgColor = 0xFFFFFFFF;
            
            Shader shader = new LinearGradient(
                    0, h - fadeHeight, 0, h,
                    new int[]{0x00FFFFFF & bgColor, bgColor},
                    null, Shader.TileMode.CLAMP);
            
            fadePaint.setShader(shader);
            
            canvas.drawRect(0, h - fadeHeight, w, h, fadePaint);
        }
    }
}
