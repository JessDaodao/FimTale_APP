package com.app.fimtale.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import com.app.fimtale.R;

/** Cover and text placeholders with one diagonal highlight clipped to their shapes. */
public final class ShimmerSkeletonView extends View {
    public enum Layout { HOME, ARTICLES, CARD, TOPIC_DETAIL, USER_DETAIL, HISTORY, HISTORY_ROW, TAGS, TAG_ROW, DRAFTS, COMMENTS }
    private Layout layout = Layout.ARTICLES;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path blocks = new Path();
    private final Path cards = new Path();
    private final Matrix shaderMatrix = new Matrix();
    private final int baseColor, highlightColor, cardColor;
    private LinearGradient shimmer;
    private ValueAnimator animator;
    private float phase;

    public ShimmerSkeletonView(Context context) { this(context, null); }
    public ShimmerSkeletonView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        int background = themeColor(android.R.attr.colorBackground);
        int foreground = themeColor(com.google.android.material.R.attr.colorOnSurface);
        baseColor = ColorUtils.blendARGB(background, foreground, .09f);
        highlightColor = ColorUtils.blendARGB(baseColor, foreground, .12f);
        cardColor = context.getColor(R.color.card_background);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    private int themeColor(int attribute) {
        TypedValue value = new TypedValue(); getContext().getTheme().resolveAttribute(attribute, value, true);
        return value.data;
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    public void setSkeletonLayout(Layout layout) {
        this.layout = layout; rebuild(); requestLayout(); invalidate();
    }
    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = layout == Layout.CARD ? 392 : layout == Layout.HISTORY_ROW ? 120 : layout == Layout.TAG_ROW ? 128 : 800;
        setMeasuredDimension(resolveSize(Math.round(dp(360)), widthMeasureSpec),
                resolveSize(Math.round(dp(height)), heightMeasureSpec));
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { rebuild(); }
    private void block(float left, float top, float right, float bottom, float radius) {
        if (right > left) blocks.addRoundRect(new RectF(left, top, right, bottom), radius, radius, Path.Direction.CW);
    }
    private void card(float top, float margin) {
        float left = margin, right = getWidth() - margin;
        cards.addRoundRect(new RectF(left, top, right, top + dp(376)), dp(16), dp(16), Path.Direction.CW);
        block(left, top, right, top + dp(220), dp(16));
        float textLeft = left + dp(16), textWidth = Math.max(0, right - left - dp(32));
        block(textLeft, top + dp(238), textLeft + textWidth * .82f, top + dp(258), dp(5));
        block(textLeft, top + dp(270), textLeft + textWidth * .4f, top + dp(284), dp(4));
        for (int i = 0; i < 3; i++) {
            float x = textLeft + i * (textWidth * .23f + dp(8));
            block(x, top + dp(300), x + textWidth * .23f, top + dp(324), dp(12));
        }
        block(textLeft, top + dp(342), textLeft + textWidth * .65f, top + dp(354), dp(4));
    }
    private void topicDetail() {
        float left = dp(16), right = getWidth() - left, width = right - left;
        block(left, dp(104), right, dp(324), dp(16));
        block(left, dp(344), left + dp(40), dp(384), dp(20));
        block(left + dp(52), dp(350), right - width * .3f, dp(368), dp(5));
        block(left, dp(402), left + width * .76f, dp(414), dp(4));
        for (int i = 0; i < 3; i++) {
            float x = left + i * (width * .23f + dp(8));
            block(x, dp(434), x + width * .23f, dp(458), dp(12));
        }
        for (int i = 0; i < 3; i++) {
            float x = left + width * (i + .5f) / 3;
            block(x - dp(12), dp(480), x + dp(12), dp(504), dp(6));
            block(x - dp(14), dp(512), x + dp(14), dp(526), dp(4));
        }
        int line = 0;
        for (float y = dp(556); y < getHeight() - dp(110); y += dp(30)) {
            float length = ++line % 4 == 0 ? .64f : .96f;
            block(left, y, left + width * length, y + dp(15), dp(4));
        }
        block(left, getHeight() - dp(72), right - dp(120), getHeight() - dp(24), dp(24));
        block(right - dp(108), getHeight() - dp(72), right, getHeight() - dp(24), dp(24));
    }
    private void userDetail() {
        float width = getWidth(), left = dp(16), right = width - left;
        block(left, dp(104), right, dp(324), dp(16));
        block(width / 2 - dp(50), dp(264), width / 2 + dp(50), dp(364), dp(50));
        block(width * .3f, dp(396), width * .7f, dp(422), dp(6));
        block(width * .38f, dp(438), width * .62f, dp(460), dp(11));
        block(width * .28f, dp(474), width * .72f, dp(486), dp(4));
        block(width * .15f, dp(510), width * .85f, dp(524), dp(4));
        block(width * .24f, dp(536), width * .76f, dp(550), dp(4));
        for (int i = 0; i < 3; i++) {
            float x = left + (right - left) * (i + .5f) / 3;
            block(x - dp(22), dp(576), x + dp(22), dp(596), dp(5));
            block(x - dp(16), dp(606), x + dp(16), dp(618), dp(4));
        }
        block(left, dp(654), left + (right - left) * .32f, dp(672), dp(5));
        for (float y = dp(694); y < getHeight(); y += dp(392)) card(y, dp(24));
    }
    private void textRows() {
        boolean drafts = layout == Layout.DRAFTS;
        boolean tags = layout == Layout.TAGS || layout == Layout.TAG_ROW;
        boolean single = layout == Layout.HISTORY_ROW || layout == Layout.TAG_ROW;
        float left = dp(drafts ? 20 : tags ? (single ? 16 : 24) : single ? 8 : 16);
        float right = getWidth() - left;
        float y = dp(8);
        if (drafts) {
            block(left, dp(12), right * .8f, dp(26), dp(4));
            y = dp(54);
        }
        do {
            float textLeft = drafts ? left : left + dp(16);
            float width = right - textLeft - dp(drafts ? 48 : 16);
            if (!drafts) cards.addRoundRect(new RectF(left, y, right, y + dp(tags ? 112 : 104)), dp(12), dp(12), Path.Direction.CW);
            block(textLeft, y + dp(16), textLeft + width * .7f, y + dp(35), dp(5));
            block(textLeft, y + dp(48), textLeft + width, y + dp(drafts || tags ? 61 : 54), dp(4));
            block(textLeft, y + dp(76), textLeft + width * .55f, y + dp(88), dp(4));
            if (drafts) block(right - dp(20), y + dp(34), right - dp(14), y + dp(56), dp(3));
            y += dp(tags || drafts ? 128 : 120);
        } while (!single && y < getHeight());
    }
    private void rebuild() {
        blocks.reset(); cards.reset();
        if (getWidth() <= 0 || getHeight() <= 0) return;
        if (layout == Layout.TOPIC_DETAIL) topicDetail();
        else if (layout == Layout.USER_DETAIL) userDetail();
        else if (layout == Layout.COMMENTS) {
            for (float y = dp(16); y < getHeight(); y += dp(140)) {
                block(dp(16), y, dp(56), y + dp(40), dp(20));
                float left = dp(68), width = getWidth() - left - dp(16);
                block(left, y + dp(2), left + width * .5f, y + dp(18), dp(4));
                block(left, y + dp(36), left + width, y + dp(49), dp(4));
                block(left, y + dp(60), left + width * .8f, y + dp(73), dp(4));
                block(left, y + dp(92), left + width * .45f, y + dp(108), dp(4));
            }
        }
        else if (layout == Layout.HISTORY || layout == Layout.HISTORY_ROW || layout == Layout.TAGS || layout == Layout.TAG_ROW || layout == Layout.DRAFTS) textRows();
        else feed();
        float band = Math.max(dp(100), getWidth() * .32f);
        // A diagonal gradient creates a tilted light band as it moves horizontally.
        shimmer = new LinearGradient(-band, band * .55f, band, -band * .55f,
                new int[]{baseColor, highlightColor, baseColor}, new float[]{0, .5f, 1}, Shader.TileMode.CLAMP);
    }
    private void feed() {
        float y = dp(8);
        if (layout == Layout.HOME) {
            block(dp(16), y, getWidth() - dp(16), dp(212), dp(16));
            for (int i = 0; i < 3; i++) {
                float x = getWidth() * (i + .5f) / 3;
                block(x - dp(14), dp(238), x + dp(14), dp(266), dp(8));
                block(x - dp(22), dp(274), x + dp(22), dp(286), dp(4));
            }
            block(dp(48), dp(318), getWidth() * .46f, dp(336), dp(5));
            block(getWidth() * .56f, dp(318), getWidth() - dp(48), dp(336), dp(5));
            y = dp(360);
        }
        do {
            card(y, dp(layout == Layout.CARD ? 8 : 16));
            y += dp(392);
        } while (layout != Layout.CARD && y < getHeight());
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setShader(null); paint.setColor(cardColor); canvas.drawPath(cards, paint);
        paint.setColor(baseColor); canvas.drawPath(blocks, paint);
        if (shimmer == null || animator == null || !animator.isStarted()) return;
        float travel = getWidth() + getHeight() * .55f + dp(240);
        shaderMatrix.setTranslate(-getHeight() * .55f - dp(120) + phase * travel, 0);
        shimmer.setLocalMatrix(shaderMatrix); paint.setShader(shimmer);
        canvas.drawPath(blocks, paint); paint.setShader(null);
    }
    private void updateAnimation() {
        boolean visible = isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE && ValueAnimator.areAnimatorsEnabled();
        if (visible && animator == null) {
            animator = ValueAnimator.ofFloat(0, 1); animator.setDuration(1700);
            animator.setRepeatCount(ValueAnimator.INFINITE); animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener(value -> { phase = (float) value.getAnimatedValue(); invalidate(); });
            animator.start();
        } else if (!visible && animator != null) { animator.cancel(); animator = null; invalidate(); }
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation(); }
    @Override public void onVisibilityAggregated(boolean visible) { super.onVisibilityAggregated(visible); updateAnimation(); }
    @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateAnimation(); }
    @Override protected void onDetachedFromWindow() {
        if (animator != null) { animator.cancel(); animator = null; }
        super.onDetachedFromWindow();
    }
}
