package com.fimtale.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.TextView;
import com.fimtale.R;
import io.noties.markwon.core.spans.CodeBlockSpan;
import io.noties.markwon.ext.tables.TableRowSpan;
import io.noties.markwon.image.AsyncDrawableSpan;
import java.util.*;

/** Operations on rendered text: source BBCode/Markdown must never be split for pagination. */
public final class BbCodeText {
    private BbCodeText() {}
    public static final class Segment {
        public final CharSequence text;
        public final String image;
        Segment(CharSequence text, String image) { this.text = text; this.image = image; }
    }

    /** Plain conversation summaries cannot retain spans, so keep concealed text covered. */
    public static String plainPreview(Spanned text) {
        StringBuilder result = new StringBuilder(text);
        for (SpoilerSpan spoiler : text.getSpans(0, text.length(), SpoilerSpan.class)) {
            for (int i = text.getSpanStart(spoiler); i < text.getSpanEnd(spoiler); i++) {
                if (!Character.isWhitespace(result.charAt(i))) result.setCharAt(i, '\u2588');
            }
        }
        return result.toString().replace('\n', ' ').replace("\ufffc", "[图片]");
    }

    public static Spanned normalizeTables(Spanned rendered) {
        SpannableStringBuilder result = new SpannableStringBuilder(rendered);
        normalizeLineBreaks(result);
        TableRowSpan[] rows = result.getSpans(0, result.length(), TableRowSpan.class);
        Arrays.sort(rows, Comparator.comparingInt(result::getSpanStart).reversed());
        for (TableRowSpan row : rows) {
            int start = result.getSpanStart(row), end = result.getSpanEnd(row);
            if (start < 0 || end <= start) continue;
            result.removeSpan(row);
            // The cells have their own styled text; don't leave their images or links on the row marker.
            for (ReplacementSpan span : result.getSpans(start, end, ReplacementSpan.class)) result.removeSpan(span);
            for (ClickableSpan span : result.getSpans(start, end, ClickableSpan.class))
                if (!(span instanceof SpoilerSpan)) result.removeSpan(span);
            result.replace(start, end, "\ufffc");
            result.setSpan(row, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (start + 1 == result.length() || result.charAt(start + 1) != '\n') result.insert(start + 1, "\n");
        }
        SpoilerSpan.prepare(result);
        return new SpannedString(result);
    }

    /** Marks an authored break, so block separators can be deduplicated without collapsing blank lines. */
    static final class SourceLineBreak {}

    static void normalizeLineBreaks(SpannableStringBuilder text) {
        BitSet authored = new BitSet();
        for (SourceLineBreak marker : text.getSpans(0, text.length(), SourceLineBreak.class)) {
            // Markwon may insert a block separator before <br>; the last character is the authored break.
            int offset = text.getSpanEnd(marker) - 1;
            if (offset >= 0 && text.charAt(offset) == '\n') authored.set(offset);
            text.removeSpan(marker);
        }
        BitSet redundant = new BitSet();
        for (int offset = authored.nextSetBit(1); offset >= 0; offset = authored.nextSetBit(offset + 1)) {
            int previous = offset - 1;
            if (text.charAt(previous) == '\n' && !authored.get(previous)
                    && text.getSpans(previous, offset, CodeBlockSpan.class).length == 0) {
                redundant.set(previous);
            }
        }
        // Delete before pagination, while all style/link/table ranges can still move together.
        for (int offset = redundant.length() - 1; offset >= 0; offset = redundant.previousSetBit(offset - 1))
            text.delete(offset, offset + 1);
    }

    public static List<Segment> segments(Spanned text) {
        List<Segment> segments = new ArrayList<>();
        AsyncDrawableSpan[] images = text.getSpans(0, text.length(), AsyncDrawableSpan.class);
        Arrays.sort(images, Comparator.comparingInt(text::getSpanStart));
        int cursor = 0;
        for (AsyncDrawableSpan image : images) {
            int start = text.getSpanStart(image), end = text.getSpanEnd(image);
            String url = image.getDrawable().getDestination();
            // Emoji and images authored within a sentence remain in that sentence.
            if (url.contains("/img/ftemoji/") || start < cursor || text.getSpans(start, end, SpoilerSpan.class).length > 0
                    || !isLineStart(text, start) || !isLineEnd(text, end)) continue;
            if (start > cursor) segments.add(new Segment(text.subSequence(cursor, start), null));
            segments.add(new Segment(null, url)); cursor = end;
        }
        if (cursor < text.length()) segments.add(new Segment(text.subSequence(cursor, text.length()), null));
        return segments;
    }
    private static boolean isLineStart(CharSequence text, int offset) {
        for (int i = offset - 1; i >= 0 && text.charAt(i) != '\n'; i--) if (!Character.isWhitespace(text.charAt(i))) return false;
        return true;
    }
    private static boolean isLineEnd(CharSequence text, int offset) {
        for (int i = offset; i < text.length() && text.charAt(i) != '\n'; i++) if (!Character.isWhitespace(text.charAt(i))) return false;
        return true;
    }

    /** Bound RecyclerView row length without dropping whitespace or cutting a paragraph's spans. */
    public static List<CharSequence> verticalChunks(CharSequence text) {
        List<CharSequence> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n' && i - start >= 1000) { result.add(text.subSequence(start, i + 1)); start = i + 1; }
        }
        if (start < text.length()) result.add(text.subSequence(start, text.length()));
        return result;
    }

    /** Reflow after the real column width is known, including narrower cards and window resizing. */
    public static void bindWidth(TextView view) {
        if (view.getTag(R.id.bbcode_width_listener) != null) return;
        View.OnLayoutChangeListener listener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left == oldRight - oldLeft) return;
            CharSequence text = view.getText();
            if (!(text instanceof Spanned) || ((Spanned) text).getSpans(0, text.length(), ReplacementSpan.class).length == 0) return;
            int width = right - left - view.getTotalPaddingLeft() - view.getTotalPaddingRight();
            if (width <= 1) return;
            prepare(text, view.getPaint(), width);
            // TextView caches replacement metrics; changing a span's width alone doesn't invalidate them.
            view.setText(text);
            SpoilerSpan.bind(view);
        };
        view.setTag(R.id.bbcode_width_listener, listener);
        view.addOnLayoutChangeListener(listener);
    }

    public static void prepare(CharSequence text, TextPaint paint, int width) {
        if (!(text instanceof Spanned) || width <= 1) return;
        Spanned spans = (Spanned) text;
        CollapseButtonSpan.prepare(text, width);
        for (BbCodeRendering.IndentSpan indent : spans.getSpans(0, spans.length(), BbCodeRendering.IndentSpan.class)) indent.prepare(paint.getTextSize(), width);
        List<TableRowSpan> rows = new ArrayList<>();
        for (TableRowSpan row : spans.getSpans(0, spans.length(), TableRowSpan.class)) {
            if (row instanceof FittedTableRowSpan) {
                int start = spans.getSpanStart(row), end = spans.getSpanEnd(row);
                int margin = 0;
                for (LeadingMarginSpan indent : spans.getSpans(start, end, LeadingMarginSpan.class))
                    margin += indent.getLeadingMargin(true);
                ((FittedTableRowSpan) row).prepare(paint, Math.max(1, width - margin));
            } else rows.add(row);
        }
        if (rows.isEmpty()) return;
        // Markwon initializes table metrics during draw. Warm those metrics before StaticLayout,
        // including on font changes at the same width, so pagination sees the actual row height.
        Bitmap scratch = Bitmap.createBitmap(width + 1, 1, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(scratch);
        for (TableRowSpan row : rows) {
            row.draw(canvas, new SpannedString(" "), 0, 1, 0, 0, 0, 0, paint);
            for (int x = 0; row.cellWidth() > 0; x += row.cellWidth()) {
                android.text.Layout cell = row.findLayoutForHorizontalOffset(x);
                if (cell == null) break;
                CollapseButtonSpan.prepare(cell.getText(), Math.max(1, cell.getWidth() - 1));
            }
        }
        scratch.recycle();
        scratch = Bitmap.createBitmap(width, 1, Bitmap.Config.ARGB_8888); canvas = new Canvas(scratch);
        for (TableRowSpan row : rows) row.draw(canvas, new SpannedString(" "), 0, 1, 0, 0, 0, 0, paint);
        scratch.recycle();
    }
}
