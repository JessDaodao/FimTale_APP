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
import android.text.style.LineHeightSpan;
import com.fimtale.utils.BbCode;
import com.fimtale.utils.BbCodeRendering;
import io.noties.markwon.Markwon;
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
    private Markwon blockRenderer;
    private final Map<String, BbCodeBlockPreview> previews = new HashMap<>();
    private final List<int[]> previewRanges = new ArrayList<>();
    private static final Set<String> PREVIEW_BLOCKS = new HashSet<>(java.util.Arrays.asList(
            "table", "list", "markdown", "collapse", "ref", "handbook", "hr"));

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
        images.clear();
        for (BbCodeBlockPreview preview : previews.values()) preview.close();
        previews.clear(); super.onDetachedFromWindow();
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
            int[] paragraph = BbCodeSyntax.activeParagraph(source, hasFocus() ? getSelectionStart() : -1, hasFocus() ? getSelectionEnd() : -1);
            // Editing any part of a rich block reveals that entire block, including nested syntax.
            for (BbCodeSyntax.Node node : nodes) {
                if (PREVIEW_BLOCKS.contains(node.name) && intersects(paragraph, node.start, node.end)) {
                    paragraph[0] = Math.min(paragraph[0], node.start);
                    paragraph[1] = Math.max(paragraph[1], node.end);
                }
            }
            previewRanges.clear();
            Set<String> usedPreviews = new HashSet<>();
            renderBreakParagraphs(text, source, paragraph, usedPreviews);
            Set<String> usedImages = new HashSet<>();
            List<BbCodeSyntax.Node> sizeAncestors = new ArrayList<>();
            Map<Integer, Integer> sizeSteps = new HashMap<>();
            for (BbCodeSyntax.Node node : nodes) {
                if (insidePreview(node.start, node.end)) continue;
                if ((node.name.equals("mention") || node.name.equals("hash")) && !sourceVisible
                        && !intersects(paragraph, node.start, node.end) && !insideNode("spoiler", node.start, node.end)
                        && source.substring(node.start, node.end).indexOf('\n') < 0) {
                    if (blockRenderer == null) blockRenderer = BbCodeRendering.create(getContext());
                    String label = blockRenderer.toMarkdown(BbCode.toMarkdown(source.substring(node.start, node.end))).toString().trim();
                    span(text, new InlineLabelSpan(label, getLinkTextColors().getDefaultColor()), node.start, node.end);
                    previewRanges.add(new int[]{node.start, node.end});
                    continue;
                }
                if (PREVIEW_BLOCKS.contains(node.name) && !sourceVisible && !intersects(paragraph, node.start, node.end)
                        && !insideNode("spoiler", node.start, node.end) && canPreview(source, node)) {
                    String key = node.start + ":" + source.substring(node.start, node.end);
                    usedPreviews.add(key);
                    BbCodeBlockPreview preview = previews.get(key);
                    if (preview == null) {
                        if (blockRenderer == null) blockRenderer = BbCodeRendering.create(getContext());
                        preview = new BbCodeBlockPreview(this, blockRenderer, source.substring(node.start, node.end), this::scheduleRender);
                        previews.put(key, preview);
                    }
                    int leadingMargin = 0;
                    for (LeadingMarginSpan margin : text.getSpans(node.start, node.start + 1, LeadingMarginSpan.class))
                        leadingMargin += margin.getLeadingMargin(true);
                    preview.setLeadingMargin(leadingMargin);
                    preview.prepare();
                    int firstBreak = source.indexOf('\n', node.start);
                    int firstEnd = firstBreak >= 0 && firstBreak < node.end ? firstBreak : node.end;
                    span(text, preview, node.start, firstEnd);
                    if (firstEnd < node.end) {
                        hideLines(text, source, firstEnd + 1, node.end);
                        span(text, new HiddenSourceLines(), firstEnd + 1, node.end);
                    }
                    previewRanges.add(new int[]{node.start, node.end});
                    continue;
                }
                while (!sizeAncestors.isEmpty() && sizeAncestors.get(sizeAncestors.size() - 1).end <= node.start)
                    sizeAncestors.remove(sizeAncestors.size() - 1);
                if (node.name.equals("img")) {
                    if (insideNode("spoiler", node.start, node.end)) continue;
                    String url = BbCode.safeUrl(source.substring(node.contentStart, node.contentEnd).trim(), true);
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
                            Drawable drawable = image.drawable.getConstantState() == null ? image.drawable : image.drawable.getConstantState().newDrawable().mutate();
                            sizeImage(drawable, node);
                            span(text, new ImageSpan(drawable), node.start, node.end); continue;
                        }
                    }
                    // Keep the URL editable while loading, on failure or under the caret.
                    span(text, new ForegroundColorSpan(getLinkTextColors().getDefaultColor()), node.contentStart, node.contentEnd);
                    continue;
                }
                int parentStep = sizeAncestors.isEmpty() ? 0 : sizeSteps.get(sizeAncestors.get(sizeAncestors.size() - 1).start);
                int step = parentStep;
                if (node.name.equals("size")) {
                    if (node.argument.equalsIgnoreCase("larger")) step = Math.min(8, parentStep + 1);
                    if (node.argument.equalsIgnoreCase("smaller")) step = Math.max(-8, parentStep - 1);
                    sizeAncestors.add(node); sizeSteps.put(node.start, step);
                }
                boolean styled = style(text, source, node, step - parentStep);
                if (styled) {
                    syntax(text, node.start, node.contentStart, paragraph);
                    syntax(text, node.contentEnd, node.end, paragraph);
                }
            }
            Matcher emojis = Pattern.compile(":ftemoji_([a-zA-Z0-9_]+):").matcher(source);
            while (emojis.find()) {
                if (insideLiteral(emojis.start(), emojis.end()) || insidePreview(emojis.start(), emojis.end()) || insideNode("spoiler", emojis.start(), emojis.end()) || sourceVisible
                        || intersects(paragraph, emojis.start(), emojis.end()) || !isAttachedToWindow()) continue;
                String url = SiteUrls.media("/img/ftemoji/" + emojis.group(1) + ".png");
                if (url == null) continue;
                usedImages.add(url);
                InlineImage image = images.get(url);
                if (image == null) {
                    image = new InlineImage(); images.put(url, image);
                    image.requests.load(url).override(Math.round(getTextSize() * 1.2f)).fitCenter().into(image);
                }
                if (image.drawable != null) {
                    image.drawable.setBounds(0, 0, Math.round(getTextSize() * 1.2f), Math.round(getTextSize() * 1.2f));
                    span(text, new ImageSpan(image.drawable), emojis.start(), emojis.end());
                }
            }
            // Apply the concealment last so inner color/background tags cannot reveal a spoiler.
            for (BbCodeSyntax.Node node : nodes) {
                if (node.name.equals("spoiler") && !sourceVisible && !intersects(paragraph, node.start, node.end)
                        && !insidePreview(node.start, node.end)) {
                    span(text, new BackgroundColorSpan(Color.BLACK), node.contentStart, node.contentEnd);
                    span(text, new ForegroundColorSpan(Color.TRANSPARENT), node.contentStart, node.contentEnd);
                }
            }
            java.util.Iterator<Map.Entry<String, BbCodeBlockPreview>> previewIterator = previews.entrySet().iterator();
            while (previewIterator.hasNext()) {
                Map.Entry<String, BbCodeBlockPreview> entry = previewIterator.next();
                if (!usedPreviews.contains(entry.getKey())) { entry.getValue().close(); previewIterator.remove(); }
            }
            java.util.Iterator<Map.Entry<String, InlineImage>> iterator = images.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, InlineImage> entry = iterator.next();
                if (!usedImages.contains(entry.getKey())) { entry.getValue().requests.clear(entry.getValue()); iterator.remove(); }
            }
        } finally { endBatchEdit(); }
    }

    private boolean insidePreview(int start, int end) {
        for (int[] range : previewRanges) if (start >= range[0] && end <= range[1]) return true;
        return false;
    }

    private void renderBreakParagraphs(Editable text, String source, int[] active, Set<String> used) {
        if (sourceVisible) return;
        for (BbCodeSyntax.Node node : nodes) {
            if (!node.name.equals("br") || insidePreview(node.start, node.end)) continue;
            int start = source.lastIndexOf('\n', Math.max(0, node.start - 1)) + 1;
            int end = source.indexOf('\n', node.end);
            if (end < 0) end = source.length();
            if (intersects(active, start, end)) continue;
            boolean crossing = false;
            for (BbCodeSyntax.Node other : nodes) {
                if ((other.start < start && other.end > start) || (other.start < end && other.end > end)) {
                    crossing = true; break;
                }
            }
            // Standalone source paragraphs can show actual hard breaks without inserting newlines
            // into the Editable. Cross-paragraph containers remain available in full preview.
            if (crossing) continue;
            String key = "line:" + start + ":" + source.substring(start, end);
            used.add(key);
            BbCodeBlockPreview preview = previews.get(key);
            if (preview == null) {
                if (blockRenderer == null) blockRenderer = BbCodeRendering.create(getContext());
                preview = new BbCodeBlockPreview(this, blockRenderer, source.substring(start, end), this::scheduleRender);
                previews.put(key, preview);
            }
            preview.prepare();
            span(text, preview, start, end); previewRanges.add(new int[]{start, end});
        }
    }

    private boolean canPreview(String source, BbCodeSyntax.Node node) {
        if (source.substring(node.start, node.end).indexOf('\n') < 0) return true;
        int lineStart = source.lastIndexOf('\n', Math.max(0, node.start - 1)) + 1;
        int lineEnd = source.indexOf('\n', node.end);
        if (lineEnd < 0) lineEnd = source.length();
        // Never collapse a source line containing prose outside this block.
        return source.substring(lineStart, node.start).trim().isEmpty() && source.substring(node.end, lineEnd).trim().isEmpty();
    }

    private void hideLines(Editable text, String source, int start, int end) {
        int lineStart = start;
        for (int i = start; i <= end; i++) {
            if (i == end || source.charAt(i) == '\n') {
                span(text, new HiddenSyntaxSpan(), lineStart, i); lineStart = i + 1;
            }
        }
    }

    private void sizeImage(Drawable drawable, BbCodeSyntax.Node node) {
        int available = Math.max(1, getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight());
        float intrinsicWidth = Math.max(1, drawable.getIntrinsicWidth()), intrinsicHeight = Math.max(1, drawable.getIntrinsicHeight());
        float width = dimension(node.attributes.get("width"), available), height = dimension(node.attributes.get("height"), available);
        if (width <= 0 && height <= 0) { width = intrinsicWidth; height = intrinsicHeight; }
        else if (width <= 0) width = height * intrinsicWidth / intrinsicHeight;
        else if (height <= 0) height = width * intrinsicHeight / intrinsicWidth;
        float scale = Math.min(1f, Math.min(available / width, dp(320) / height));
        drawable.setBounds(0, 0, Math.max(1, Math.round(width * scale)), Math.max(1, Math.round(height * scale)));
    }
    private float dimension(String value, int available) {
        if (value == null || !value.matches("\\d{1,4}(?:\\.\\d{1,2})?(?:px|%|em)?")) return 0;
        if (value.endsWith("%")) return Float.parseFloat(value.substring(0, value.length() - 1)) * available / 100;
        if (value.endsWith("em")) return Float.parseFloat(value.substring(0, value.length() - 2)) * getTextSize();
        return Float.parseFloat(value.replace("px", ""));
    }

    private boolean insideLiteral(int start, int end) {
        return insideNode("code", start, end) || insideNode("markdown", start, end) || insideNode("img", start, end) || insideNode("handbook", start, end);
    }

    private boolean insideNode(String name, int start, int end) {
        for (BbCodeSyntax.Node node : nodes) {
            if (node.name.equals(name)
                    && start >= node.contentStart && end <= node.contentEnd) return true;
        }
        return false;
    }

    private boolean style(Editable text, String source, BbCodeSyntax.Node node, int sizeStep) {
        int start = node.contentStart, end = node.contentEnd;
        switch (node.name) {
            case "b": span(text, new StyleSpan(Typeface.BOLD), start, end); break;
            case "i": span(text, new StyleSpan(Typeface.ITALIC), start, end); break;
            case "u": span(text, new UnderlineSpan(), start, end); break;
            case "s": span(text, new StrikethroughSpan(), start, end); break;
            case "sub": span(text, new SubscriptSpan(), start, end); span(text, new RelativeSizeSpan(.8f), start, end); break;
            case "sup": span(text, new SuperscriptSpan(), start, end); span(text, new RelativeSizeSpan(.8f), start, end); break;
            case "url": case "mention": case "hash": case "ref":
                span(text, new ForegroundColorSpan(getLinkTextColors().getDefaultColor()), start, end);
                span(text, new UnderlineSpan(), start, end); break;
            case "font": span(text, new TypefaceSpan(BbCodeRendering.fontFamily(node.argument)), start, end); break;
            case "code": case "markdown": case "handbook":
                span(text, new TypefaceSpan("monospace"), start, end);
                span(text, new BackgroundColorSpan(ColorUtils.setAlphaComponent(getCurrentTextColor(), 20)), start, end); break;
            case "spoiler": break;
            case "color": case "bg-color":
                Integer color = BbCodeRendering.cssColor(node.argument);
                if (color == null) return false;
                span(text, node.name.equals("color") ? new ForegroundColorSpan(color) : new BackgroundColorSpan(color), start, end);
                break;
            case "size":
                if (!node.argument.equalsIgnoreCase("larger") && !node.argument.equalsIgnoreCase("smaller")) return false;
                if (sizeStep != 0) span(text, new RelativeSizeSpan((float) Math.pow(1.2, sizeStep)), start, end);
                break;
            case "table": case "tr": case "td": case "list": case "p": break;
            case "th": span(text, new StyleSpan(Typeface.BOLD), start, end); break;
            case "collapse":
                span(text, new BackgroundColorSpan(ColorUtils.setAlphaComponent(getCurrentTextColor(), 12)), start, end); break;
            case "*":
                // While editing, source item boundaries stay visible. The inactive list uses the reader renderer.
                return false;
            case "br": case "hr":
                span(text, new ForegroundColorSpan(getLinkTextColors().getDefaultColor()), node.start, node.end);
                return false;
            case "h1": case "h2": case "h3": case "h4": case "h5": case "h6":
                span(text, new StyleSpan(Typeface.BOLD), start, end);
                span(text, new RelativeSizeSpan(1 + (7 - (node.name.charAt(1) - '0')) * .12f), start, end); break;
            case "quote": case "indent": case "left": case "center": case "right": case "justify":
                int lineStart = node.start == 0 ? 0 : source.lastIndexOf('\n', node.start - 1) + 1;
                int nextLine = source.indexOf('\n', node.end);
                int lineEnd = nextLine < 0 ? source.length() : nextLine + 1;
                if (node.name.equals("quote")) span(text, new QuoteSpan(getLinkTextColors().getDefaultColor(), dp(3), dp(10)), lineStart, lineEnd);
                else if (node.name.equals("indent")) {
                    String indent = node.argument.matches("(?:\\d{1,4}(?:\\.\\d{1,3})?)(?:em|rem|px|pt|%)") ? node.argument : "2em";
                    BbCodeRendering.IndentSpan margin = new BbCodeRendering.IndentSpan(indent, getResources().getDisplayMetrics().density);
                    margin.prepare(getTextSize(), Math.max(1, getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight()));
                    span(text, margin, lineStart, lineEnd);
                }
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
    private final class HiddenSourceLines implements LineHeightSpan, android.text.style.UpdateLayout {
        @Override public void chooseHeight(CharSequence text, int start, int end, int spanStart, int vertical, Paint.FontMetricsInt metrics) {
            metrics.ascent = metrics.descent = metrics.top = metrics.bottom = metrics.leading = 0;
            // TextView adds the configured extra spacing even to a zero-height source line.
            if (end < text.length()) metrics.descent = metrics.bottom = -Math.round(getLineSpacingExtra() / Math.max(1f, getLineSpacingMultiplier()));
        }
    }
    private static final class InlineLabelSpan extends ReplacementSpan {
        private final String label;
        private final int color;
        InlineLabelSpan(String label, int color) { this.label = label; this.color = color; }
        @Override public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt metrics) {
            if (metrics != null) paint.getFontMetricsInt(metrics);
            return (int) Math.ceil(paint.measureText(label));
        }
        @Override public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            Paint link = new Paint(paint); link.setColor(color); link.setUnderlineText(true);
            canvas.drawText(label, x, y, link);
        }
    }
    private static final class HiddenSyntaxSpan extends ReplacementSpan {
        @Override public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) { return 0; }
        @Override public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {}
    }
}
