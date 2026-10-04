package com.fimtale.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class ParallaxImageView extends AppCompatImageView implements ViewTreeObserver.OnScrollChangedListener {

    private float parallaxFactor = 0.15f;
    private int[] location = new int[2];
    private boolean isAttached = false;
    private boolean isParallaxEnabled = true;

    public ParallaxImageView(Context context) {
        super(context);
        init();
    }

    public ParallaxImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ParallaxImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setScaleType(ScaleType.MATRIX);
    }

    public void setParallaxEnabled(boolean enabled) {
        if (this.isParallaxEnabled != enabled) {
            this.isParallaxEnabled = enabled;
            if (enabled) {
                setScaleType(ScaleType.MATRIX);
                applyParallax();
            } else {
                setScaleType(ScaleType.CENTER_CROP);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isAttached = true;
        getViewTreeObserver().addOnScrollChangedListener(this);
        applyParallax();
    }

    @Override
    protected void onDetachedFromWindow() {
        isAttached = false;
        getViewTreeObserver().removeOnScrollChangedListener(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void onScrollChanged() {
        if (isAttached) {
            applyParallax();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyParallax();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        applyParallax();
    }

    private void applyParallax() {
        if (!isParallaxEnabled) return;

        Drawable drawable = getDrawable();
        if (drawable == null) return;
        
        int vWidth = getWidth();
        int vHeight = getHeight();
        
        if (vWidth == 0 || vHeight == 0) return;

        int dWidth = drawable.getIntrinsicWidth();
        int dHeight = drawable.getIntrinsicHeight();

        if (dWidth <= 0 || dHeight <= 0) return;

        getLocationOnScreen(location);
        int y = location[1];
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        float viewCenterY = y + vHeight / 2f;
        float screenCenterY = screenHeight / 2f;

        float offsetFromCenter = viewCenterY - screenCenterY;

        float translationY = -offsetFromCenter * parallaxFactor;

        Matrix matrix = new Matrix();

        float scale;
        float dx, dy;

        if ((float) dWidth * vHeight > (float) vWidth * dHeight) {
            scale = (float) vHeight / (float) dHeight;
            dx = (vWidth - dWidth * scale) * 0.5f;
            dy = 0;
        } else {
            scale = (float) vWidth / (float) dWidth;
            dy = (vHeight - dHeight * scale) * 0.5f;
            dx = 0;
        }
        
        float maxTranslation = (screenHeight / 2f) * parallaxFactor;
        float minRequiredHeight = vHeight + maxTranslation * 2;
        
        float currentScaledHeight = dHeight * scale;
        
        if (currentScaledHeight < minRequiredHeight) {
            float scaleCorrection = minRequiredHeight / currentScaledHeight;
            scale *= scaleCorrection;
            dx = (vWidth - dWidth * scale) * 0.5f;
            dy = (vHeight - dHeight * scale) * 0.5f;
        }

        dy += translationY;

        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }
}
