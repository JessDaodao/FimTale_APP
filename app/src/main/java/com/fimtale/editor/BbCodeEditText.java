package com.fimtale.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.core.graphics.ColorUtils;
import com.fimtale.network.SiteUrls;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Inline BBCode rendering over the original Editable, preserving selection, IME and undo. */
public final class BbCodeEditText extends AppCompatEditText {
    private final List<Object> decoration = new ArrayList<>();
    private final Map<String, InlineImage> images = new HashMap<>();
    private final Runnable render = this::renderNow;
    private List<BbCodeSyntax.Node> nodes = new ArrayList<>();
    private String parsedSource;
    private boolean sourceVisible;

    public BbCodeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleRender(); }
            @Override public void afterTextChanged(Editable text) { scheduleRender(); }
        });
    }

    public void setSourceVisible(boolean visible) { sourceVisible = visible; renderNow(); }
    public boolean isSourceVisible() { return sourceVisible; }

    private void scheduleRender() {
        // Callbacks can arrive from TextView's constructor before our fields exist.
        if (render == null) return;
        removeCallbacks(render); postDelayed(render, 80);
    }
    @Override protected void onSelectionChanged(int start, int end) { super.onSelectionChanged(start, end); scheduleRender(); }
    @Override protected void onFocusChanged(boolean focused, int direction, Rect rect) { super.onFocusChanged(focused, direction, rect); scheduleRender(); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { super.onSizeChanged(w, h, oldw, oldh); scheduleRender(); }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); scheduleRender(); }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(render);
        Editable text = getText();
        if (text != null) for (Object span : decoration) text.removeSpan(span);
        decoration.clear();
        for (InlineImage image : images.values()) image.requests.clear(image);
        images.clear(); super.onDetachedFromWindow();
    }

    private void span(Editable text, Object span, int start, int end) {
        if (start >= end) return;
        text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); decoration.add(span);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private boolean intersects(int[] paragraph, int start, int end) {
        return paragraph[0] >= 0 && start <= paragraph[1] && end > paragraph[0];
    }
    private void syntax(Editable text, int start, int end, int[] paragraph) {
        if (sourceVisible || intersects(paragraph, start, end)) {
            span(text, new ForegroundColorSpan(ColorUtils.setAlphaComponent(getCurrentTextColor(), 140)), start, end);
        } else span(text, new HiddenSyntaxSpan(), start, end);
    }

    private void renderNow() {
        Editable text = getText(); if (text == null) return;
        String source = text.toString();
        if (!source.equals(parsedSource)) { nodes = BbCodeSyntax.parse(source); parsedSource = source; }
        // Only remove spans owned by this renderer; composing/selection/suggestion spans survive.
        beginBatchEdit();
        try {
            for (Object span : decoration) text.removeSpan(span);
            decoration.clear();
            addVisualParagraphIndent(text, source);
            int[] paragraph = BbCodeSyntax.activeParagraph(source, hasFocus() ? getSelectionStart() : -1, hasFocus() ? getSelectionEnd() : -1);
            Set<String> usedImages = new HashSet<>();
            List<BbCodeSyntax.Node> sizeAncestors = new ArrayList<>();
            for (BbCodeSyntax.Node node : nodes) {
                while (!sizeAncestors.isEmpty() && sizeAncestors.get(sizeAncestors.size() - 1).end <= node.start)
                    sizeAncestors.remove(sizeAncestors.size() - 1);
                if (node.name.equals("img")) {
                    if (insideNode("spoiler", node.start, node.end)) continue;
                    String url = SiteUrls.media(source.substring(node.contentStart, node.contentEnd).trim());
                    if (!sourceVisible && !intersects(paragraph, node.start, node.end) && url != null
                            && source.substring(node.start, node.end).indexOf('\n') < 0 && isAttachedToWindow()) {
                        usedImages.add(url);
                        InlineImage image = images.get(url);
                        if (image == null) {
                            image = new InlineImage(); images.put(url, image);
                            image.requests.load(url).override(Math.max(dp(100), getWidth() - getPaddingLeft() - getPaddingRight()), dp(320))
                                    .fitCenter().into(image);
                        }
                        if (image.drawable != null) {
                            Drawable drawable = image.drawable;
                            int width = Math.max(1, drawable.getIntrinsicWidth()), height = Math.max(1, drawable.getIntrinsicHeight());
                            float scale = Math.min(1f, Math.min((float) Math.max(dp(100), getWidth() - getPaddingLeft() - getPaddingRight()) / width, (float) dp(320) / height));
                            drawable.setBounds(0, 0, Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
                            span(text, new ImageSpan(drawable), node.start, node.end); continue;
                        }
                    }
                    // Keep the URL editable while loading, on failure or under the caret.
                    span(text, new ForegroundColorSpan(getLinkTextColors().getDefaultColor()), node.contentStart, node.contentEnd);
                    continue;
                }
                boolean styled = style(text, source, node, sizeAncestors.size(),
                        !sourceVisible && !intersects(paragraph, node.start, node.end));
                if (node.name.equals("size")) sizeAncestors.add(node);
                if (styled) {
                    syntax(text, node.start, node.contentStart, paragraph);
                    syntax(text, node.contentEnd, node.end, paragraph);
                }
            }
            Matcher emojis = Pattern.compile(":ftemoji_([a-zA-Z0-9_]+):").matcher(source);
            while (emojis.find()) {
                if (insideLiteral(emojis.start(), emojis.end()) || insideNode("spoiler", emojis.start(), emojis.end()) || sourceVisible
                        || intersects(paragraph, emojis.start(), emojis.end()) || !isAttachedToWindow()) continue;
                String url = SiteUrls.media("/img/ftemoji/" + emojis.group(1) + ".png");
                if (url == null) continue;
                usedImages.add(url);
                InlineImage image = images.get(url);
                if (image == null) {
                    image = new InlineImage(); images.put(url, image);
                    image.requests.load(url).override(dp(24), dp(24)).fitCenter().into(image);
                }
                if (image.drawable != null) {
                    image.drawable.setBounds(0, 0, dp(24), dp(24));
                    span(text, new ImageSpan(image.drawable), emojis.start(), emojis.end());
                }
            }
            java.util.Iterator<Map.Entry<String, InlineImage>> iterator = images.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, InlineImage> entry = iterator.next();
                if (!usedImages.contains(entry.getKey())) { entry.getValue().requests.clear(entry.getValue()); iterator.remove(); }
            }
        } finally { endBatchEdit(); }
    }

    /** Applies the two-character first-line indent without changing the saved Editable text. */
    private void addVisualParagraphIndent(Editable text, String source) {
        Paint paint = getPaint();
        int indent = Math.max(1, Math.round(paint.measureText("\u3000\u3000")));
        int paragraphStart = 0;
        for (int i = 0; i <= source.length(); i++) {
            if (i < source.length() && source.charAt(i) != '\n') continue;
            if (i > paragraphStart) {
                span(text, new LeadingMarginSpan.Standard(indent, 0), paragraphStart, i);
            }
            paragraphStart = i + 1;
        }
    }

    private boolean insideLiteral(int start, int end) {
        return insideNode("code", start, end) || insideNode("markdown", start, end) || insideNode("img", start, end);
    }

    private boolean insideNode(String name, int start, int end) {
        for (BbCodeSyntax.Node node : nodes) {
            if (node.name.equals(name)
                    && start >= node.contentStart && end <= node.contentEnd) return true;
        }
        return false;
    }

    private boolean style(Editable text, String source, BbCodeSyntax.Node node, int sizeDepth, boolean renderSpoiler) {
        int start = node.contentStart, end = node.contentEnd;
        switch (node.name) {
            case "b": span(text, new StyleSpan(Typeface.BOLD), start, end); break;
            case "i": span(text, new StyleSpan(Typeface.ITALIC), start, end); break;
            case "u": span(text, new UnderlineSpan(), start, end); break;
            case "s": span(text, new StrikethroughSpan(), start, end); break;
            case "sub": span(text, new SubscriptSpan(), start, end); span(text, new RelativeSizeSpan(.8f), start, end); break;
            case "sup": span(text, new SuperscriptSpan(), start, end); span(text, new RelativeSizeSpan(.8f), start, end); break;
            case "url":
                span(text, new ForegroundColorSpan(getLinkTextColors().getDefaultColor()), start, end);
                span(text, new UnderlineSpan(), start, end); break;
            case "font": span(text, new TypefaceSpan(node.argument), start, end); break;
            case "code":
                span(text, new TypefaceSpan("monospace"), start, end);
                span(text, new BackgroundColorSpan(ColorUtils.setAlphaComponent(getCurrentTextColor(), 20)), start, end); break;
            case "spoiler":
                if (renderSpoiler) {
                    span(text, new BackgroundColorSpan(Color.BLACK), start, end);
                    span(text, new ForegroundColorSpan(Color.TRANSPARENT), start, end);
                }
                break;
            case "color": case "bg-color":
                try {
                    String color = node.argument;
                    if (color.matches("#[0-9a-fA-F]{3}")) color = "#" + color.charAt(1) + color.charAt(1) + color.charAt(2) + color.charAt(2) + color.charAt(3) + color.charAt(3);
                    int value = Color.parseColor(color);
                    span(text, node.name.equals("color") ? new ForegroundColorSpan(value) : new BackgroundColorSpan(value), start, end);
                } catch (IllegalArgumentException ignored) { return false; }
                break;
            case "size":
                float size;
                if (node.argument.equals("larger")) size = 1.2f;
                else if (node.argument.equals("smaller")) size = 1 / 1.2f;
                else return false;
                // The website bounds relative sizing to eight nested steps as well.
                if (sizeDepth < 8) span(text, new RelativeSizeSpan(size), start, end); break;
            case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
                span(text, new StyleSpan(Typeface.BOLD), start, end);
                span(text, new RelativeSizeSpan(1 + (7 - (node.name.charAt(1) - '0')) * .12f), start, end); break;
            case "quote": case "indent": case "left": case "center": case "right":
                int lineStart = node.start == 0 ? 0 : source.lastIndexOf('\n', node.start - 1) + 1;
                int nextLine = source.indexOf('\n', node.end);
                int lineEnd = nextLine < 0 ? source.length() : nextLine + 1;
                if (node.name.equals("quote")) span(text, new QuoteSpan(getLinkTextColors().getDefaultColor(), dp(3), dp(10)), lineStart, lineEnd);
                else if (node.name.equals("indent")) span(text, new LeadingMarginSpan.Standard(dp(24)), lineStart, lineEnd);
                else span(text, new AlignmentSpan.Standard(node.name.equals("center") ? Layout.Alignment.ALIGN_CENTER
                        : node.name.equals("right") ? Layout.Alignment.ALIGN_OPPOSITE : Layout.Alignment.ALIGN_NORMAL), lineStart, lineEnd);
                break;
            default: return false; // Unsupported/unfinished markup remains visible and round-trips unchanged.
        }
        return true;
    }

    private final class InlineImage extends CustomTarget<Drawable> {
        final RequestManager requests = Glide.with(getContext());
        Drawable drawable;
        @Override public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) { drawable = resource; scheduleRender(); }
        @Override public void onLoadCleared(@Nullable Drawable placeholder) { drawable = null; }
    }
    private static final class HiddenSyntaxSpan extends ReplacementSpan {
        @Override public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) { return 0; }
        @Override public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {}
    }
}
