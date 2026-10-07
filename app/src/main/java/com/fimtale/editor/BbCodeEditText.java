package com.fimtale.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
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
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
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
    private final Map<String, EditableTableSpan> tables = new HashMap<>();
    interface TableCellListener {
        void edit(TableHit hit); void editBlock(BlockHit hit); void layoutChanged(); void sourceModeChanged(); void textTouched();
    }
    private TableCellListener tableCellListener;
    void setTableCellListener(TableCellListener listener) { tableCellListener = listener; }
    static final class TableHit {
        final EditableTableSpan table;
        final EditableTableSpan.Cell cell;
        final int tableStart;
        final RectF bounds;
        TableHit(EditableTableSpan table, EditableTableSpan.Cell cell, int tableStart, RectF bounds) {
            this.table = table; this.cell = cell; this.tableStart = tableStart; this.bounds = bounds;
        }
        int start() { return tableStart + cell.source.node.contentStart; }
        int end() { return tableStart + cell.source.node.contentEnd; }
    }
    private TableHit pressedCell;
    private BlockHit pressedBlock;
    private float downX, downY;
    static final class BlockHit {
        final int start, end;
        final String content;
        final RectF bounds;
        BlockHit(int start, int end, String content, RectF bounds) {
            this.start = start; this.end = end; this.content = content; this.bounds = bounds;
        }
    }
    private final List<int[]> previewRanges = new ArrayList<>();
    private static final Set<String> PREVIEW_BLOCKS = new HashSet<>(java.util.Arrays.asList(
            "table", "markdown", "ref", "handbook", "hr"));

    public BbCodeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleRender(); }
            @Override public void afterTextChanged(Editable text) { scheduleRender(); }
        });
    }

    public void setSourceVisible(boolean visible) {
        if (visible && tableCellListener != null) tableCellListener.sourceModeChanged();
        sourceVisible = visible; renderNow();
    }
    public boolean isSourceVisible() { return sourceVisible; }

    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        InputConnection connection = super.onCreateInputConnection(info);
        if (connection == null) return null;
        return new InputConnectionWrapper(connection, false) {
            private CharSequence protectedText(CharSequence value) {
                if (sourceVisible || getText() == null) return value;
                int start = Math.min(getSelectionStart(), getSelectionEnd()), end = Math.max(getSelectionStart(), getSelectionEnd());
                int composingStart = android.view.inputmethod.BaseInputConnection.getComposingSpanStart(getText());
                int composingEnd = android.view.inputmethod.BaseInputConnection.getComposingSpanEnd(getText());
                if (composingStart >= 0 && composingEnd > composingStart) { start = composingStart; end = composingEnd; }
                return VisualEditing.replacement(getText().toString(), start, end, value);
            }
            @Override public boolean commitText(CharSequence text, int position) { return super.commitText(protectedText(text), position); }
            @Override public boolean setComposingText(CharSequence text, int position) { return super.setComposingText(protectedText(text), position); }
            @Override public boolean deleteSurroundingText(int before, int after) {
                if (!sourceVisible && ((before == 1 && after == 0) || (before == 0 && after == 1))) return deleteVisual(before == 1);
                return super.deleteSurroundingText(before, after);
            }
            @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) {
                if (!sourceVisible && ((before == 1 && after == 0) || (before == 0 && after == 1))) return deleteVisual(before == 1);
                return super.deleteSurroundingTextInCodePoints(before, after);
            }
        };
    }
    private boolean deleteVisual(boolean backward) {
        Editable text = getText();
        int start = Math.min(getSelectionStart(), getSelectionEnd()), end = Math.max(getSelectionStart(), getSelectionEnd());
        if (text == null || start < 0) return false;
        if (start == end) {
            int[] range = VisualEditing.deletion(text.toString(), start, backward); start = range[0]; end = range[1];
            text.delete(start, end);
        } else text.replace(start, end, VisualEditing.replacement(text.toString(), start, end, ""));
        setSelection(Math.min(start, text.length()));
        return true;
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!sourceVisible && (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL))
            return deleteVisual(keyCode == KeyEvent.KEYCODE_DEL);
        return super.onKeyDown(keyCode, event);
    }
    @Override public boolean onTextContextMenuItem(int id) {
        if (sourceVisible || getText() == null || getSelectionStart() < 0) return super.onTextContextMenuItem(id);
        int start = Math.min(getSelectionStart(), getSelectionEnd()), end = Math.max(getSelectionStart(), getSelectionEnd());
        android.content.ClipboardManager clipboard = getContext().getSystemService(android.content.ClipboardManager.class);
        if (id == android.R.id.copy || id == android.R.id.cut) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("内容", VisualEditing.selectedText(getText().toString(), start, end)));
            if (id == android.R.id.cut) deleteVisual(true);
            return true;
        }
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            android.content.ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return true;
            StringBuilder incoming = new StringBuilder();
            for (int i = 0; i < clip.getItemCount(); i++) {
                if (i > 0) incoming.append('\n');
                incoming.append(clip.getItemAt(i).coerceToText(getContext()));
            }
            CharSequence replacement = VisualEditing.replacement(getText().toString(), start, end, incoming);
            getText().replace(start, end, replacement); setSelection(start + replacement.length()); return true;
        }
        return super.onTextContextMenuItem(id);
    }

    private void scheduleRender() {
        // Callbacks can arrive from TextView's constructor before our fields exist.
        if (render == null) return;
        removeCallbacks(render); postDelayed(render, 80);
    }
    void refreshVisuals() { scheduleRender(); }
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
        for (EditableTableSpan table : tables.values()) table.close();
        tables.clear();
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
        if (sourceVisible) {
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
            previewRanges.clear();
            Set<String> usedPreviews = new HashSet<>();
            Set<String> usedTables = new HashSet<>();
            renderBreakParagraphs(text, source, paragraph, usedPreviews);
            Set<String> usedImages = new HashSet<>();
            List<BbCodeSyntax.Node> sizeAncestors = new ArrayList<>();
            Map<Integer, Integer> sizeSteps = new HashMap<>();
            for (BbCodeSyntax.Node node : nodes) {
                if (insidePreview(node.start, node.end)) continue;
                if (node.name.equals("table") && !sourceVisible
                        && (!insideNode("spoiler", node.start, node.end) || intersects(paragraph, node.start, node.end))) {
                    String key = node.start + ":" + source.substring(node.start, node.end);
                    EditableTableSpan table = tables.get(key);
                    if (table == null) {
                        EditorTable model = EditorTable.parse(source.substring(node.start, node.end));
                        if (model != null) {
                            if (blockRenderer == null) blockRenderer = BbCodeRendering.create(getContext());
                            table = new EditableTableSpan(this, blockRenderer, model); tables.put(key, table);
                        }
                    }
                    if (table != null) {
                        usedTables.add(key); table.prepare();
                        block(text, source, node, table);
                        continue;
                    }
                }
                if ((node.name.equals("mention") || node.name.equals("hash")) && !sourceVisible
                        && !intersects(paragraph, node.start, node.end) && !insideNode("spoiler", node.start, node.end)
                        && source.substring(node.start, node.end).indexOf('\n') < 0) {
                    if (blockRenderer == null) blockRenderer = BbCodeRendering.create(getContext());
                    String label = blockRenderer.toMarkdown(BbCode.toMarkdown(source.substring(node.start, node.end))).toString().trim();
                    span(text, new InlineLabelSpan(label, getLinkTextColors().getDefaultColor()), node.start, node.end);
                    previewRanges.add(new int[]{node.start, node.end});
                    continue;
                }
                if (PREVIEW_BLOCKS.contains(node.name) && !sourceVisible
                        && !insideNode("spoiler", node.start, node.end)) {
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
                    block(text, source, node, preview);
                    continue;
                }
                while (!sizeAncestors.isEmpty() && sizeAncestors.get(sizeAncestors.size() - 1).end <= node.start)
                    sizeAncestors.remove(sizeAncestors.size() - 1);
                if (node.name.equals("img")) {
                    if (insideNode("spoiler", node.start, node.end)) continue;
                    String url = BbCode.safeUrl(source.substring(node.contentStart, node.contentEnd).trim(), true);
                    if (!sourceVisible && url != null
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
                    if (!sourceVisible) {
                        span(text, new InlineLabelSpan("[图片]", getLinkTextColors().getDefaultColor()), node.start, node.end);
                        continue;
                    }
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
                if (insideLiteral(emojis.start(), emojis.end()) || insidePreview(emojis.start(), emojis.end()) || insideNode("spoiler", emojis.start(), emojis.end()) || sourceVisible) continue;
                if (!isAttachedToWindow()) {
                    span(text, new InlineLabelSpan("[表情]", getCurrentTextColor()), emojis.start(), emojis.end()); continue;
                }
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
                } else span(text, new InlineLabelSpan("[表情]", getCurrentTextColor()), emojis.start(), emojis.end());
            }
            if (!sourceVisible) {
                Matcher entities = VisualEditing.ENTITIES.matcher(source);
                while (entities.find()) {
                    if (insideLiteral(entities.start(), entities.end()) || insidePreview(entities.start(), entities.end())) continue;
                    String decoded = android.text.Html.fromHtml(entities.group(), android.text.Html.FROM_HTML_MODE_LEGACY).toString();
                    span(text, new LiteralSpan(decoded), entities.start(), entities.end());
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
            java.util.Iterator<Map.Entry<String, EditableTableSpan>> tableIterator = tables.entrySet().iterator();
            while (tableIterator.hasNext()) {
                Map.Entry<String, EditableTableSpan> entry = tableIterator.next();
                if (!usedTables.contains(entry.getKey())) { entry.getValue().close(); tableIterator.remove(); }
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

    private void block(Editable text, String source, BbCodeSyntax.Node node, ReplacementSpan preview) {
        int firstBreak = source.indexOf('\n', node.start);
        int firstEnd = firstBreak >= 0 && firstBreak < node.end ? firstBreak : node.end;
        span(text, preview, node.start, firstEnd);
        if (firstEnd < node.end) {
            hideLines(text, source, firstEnd + 1, node.end);
            int lineEnd = source.indexOf('\n', node.end);
            if (lineEnd < 0) lineEnd = source.length();
            int hiddenEnd = source.substring(node.end, lineEnd).trim().isEmpty() ? node.end
                    : source.lastIndexOf('\n', node.end - 1) + 1;
            span(text, new HiddenSourceLines(), firstEnd + 1, hiddenEnd);
        }
        previewRanges.add(new int[]{node.start, node.end});
    }

    TableHit tableCellAt(float x, float y) {
        if (sourceVisible || getText() == null) return null;
        float textX = x - getTotalPaddingLeft() + getScrollX();
        float textY = y - getTotalPaddingTop() + getScrollY();
        for (EditableTableSpan table : getText().getSpans(0, length(), EditableTableSpan.class)) {
            EditableTableSpan.Cell cell = table.hit(textX, textY);
            if (cell != null) return tableHit(table, cell);
        }
        return null;
    }
    TableHit tableCellAtSource(int offset) {
        if (sourceVisible || getText() == null) return null;
        for (EditableTableSpan table : getText().getSpans(0, length(), EditableTableSpan.class)) {
            int start = getText().getSpanStart(table);
            for (EditableTableSpan.Cell cell : table.cells)
                if (start + cell.source.node.contentStart == offset) return tableHit(table, cell);
        }
        return null;
    }
    private TableHit tableHit(EditableTableSpan table, EditableTableSpan.Cell cell) {
        RectF bounds = new RectF(cell.bounds);
        bounds.offset(table.bounds.left + getTotalPaddingLeft() - getScrollX(),
                table.bounds.top + getTotalPaddingTop() - getScrollY());
        return new TableHit(table, cell, getText().getSpanStart(table), bounds);
    }
    private BlockHit blockAt(float x, float y) {
        if (getText() == null) return null;
        x += getScrollX() - getTotalPaddingLeft(); y += getScrollY() - getTotalPaddingTop();
        for (BbCodeBlockPreview preview : getText().getSpans(0, length(), BbCodeBlockPreview.class)) {
            if (!preview.bounds.contains(x, y)) continue;
            String editable = preview.editableSource();
            if (editable == null) continue;
            int start = getText().getSpanStart(preview);
            RectF bounds = new RectF(preview.bounds);
            bounds.offset(getTotalPaddingLeft() - getScrollX(), getTotalPaddingTop() - getScrollY());
            return new BlockHit(start, start + preview.source.length(), editable, bounds);
        }
        return null;
    }
    RectF blockBounds(int start, int end) {
        for (BbCodeBlockPreview preview : getText().getSpans(start, Math.min(length(), start + 1), BbCodeBlockPreview.class)) {
            RectF bounds = new RectF(preview.bounds);
            bounds.offset(getTotalPaddingLeft() - getScrollX(), getTotalPaddingTop() - getScrollY()); return bounds;
        }
        Layout layout = getLayout();
        if (layout == null) return null;
        int first = layout.getLineForOffset(Math.max(0, start)), last = layout.getLineForOffset(Math.min(length(), Math.max(start, end - 1)));
        return new RectF(getTotalPaddingLeft(), getTotalPaddingTop() + layout.getLineTop(first) - getScrollY(),
                getWidth() - getTotalPaddingRight(), getTotalPaddingTop() + layout.getLineBottom(last) - getScrollY());
    }
    @Override protected void onDraw(Canvas canvas) {
        // Format insertion and IME commits must not flash their delimiters before the debounce runs.
        if (getText() != null && !getText().toString().equals(parsedSource)) renderNow();
        super.onDraw(canvas);
        if (tableCellListener != null) tableCellListener.layoutChanged();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!sourceVisible && tableCellListener != null && isEnabled()) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                pressedCell = tableCellAt(event.getX(), event.getY());
                pressedBlock = pressedCell == null ? blockAt(event.getX(), event.getY()) : null;
                downX = event.getX(); downY = event.getY();
                if (pressedCell == null && pressedBlock == null) tableCellListener.textTouched();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) { pressedCell = null; pressedBlock = null; }
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP && pressedCell != null) {
                TableHit hit = pressedCell; pressedCell = null;
                tableCellListener.edit(hit); return true;
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP && pressedBlock != null) {
                BlockHit hit = pressedBlock; pressedBlock = null; tableCellListener.editBlock(hit); return true;
            } else if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) { pressedCell = null; pressedBlock = null; }
        }
        return super.onTouchEvent(event);
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
                if (!sourceVisible) {
                    int number = 1; boolean ordered = false;
                    BbCodeSyntax.Node parent = null;
                    for (BbCodeSyntax.Node candidate : nodes) if (candidate.name.equals("list")
                            && candidate.contentStart <= node.start && candidate.contentEnd >= node.end) parent = candidate;
                    if (parent != null) {
                        ordered = parent.argument.equals("1");
                        for (BbCodeSyntax.Node item : nodes) if (item.name.equals("*") && item.start >= parent.contentStart
                                && item.start < node.start) number++;
                    }
                    span(text, new LiteralSpan(ordered ? number + ". " : "• "), node.start, node.contentStart);
                    syntax(text, node.contentEnd, node.end, new int[]{-1, -1});
                }
                return false;
            case "br": case "hr":
                if (!sourceVisible) span(text, new HiddenSyntaxSpan(), node.start, node.end);
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
    private static final class LiteralSpan extends ReplacementSpan {
        private final String text;
        LiteralSpan(String text) { this.text = text; }
        @Override public int getSize(Paint paint, CharSequence source, int start, int end, Paint.FontMetricsInt metrics) {
            if (metrics != null) paint.getFontMetricsInt(metrics);
            return (int) Math.ceil(paint.measureText(text));
        }
        @Override public void draw(Canvas canvas, CharSequence source, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            canvas.drawText(text, x, y, paint);
        }
    }
    private static final class HiddenSyntaxSpan extends ReplacementSpan {
        @Override public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) { return 0; }
        @Override public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {}
    }
}
