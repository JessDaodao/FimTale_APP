package com.fimtale.utils;

import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ReplacementSpan;
import android.util.TypedValue;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Measures the same styled page that TextView will display, including fallback font metrics. */
public final class ReaderPagination {
    private ReaderPagination() {}

    public static final class Page {
        public final Spanned text;
        public final int start;
        public final int end;
        public final boolean scrollable;
        Page(Spanned text, int start, int end, boolean scrollable) {
            this.text = text; this.start = start; this.end = end; this.scrollable = scrollable;
        }
    }

    public static void configure(TextView view, float sizeSp, float lineSpacing, boolean title) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTypeface(title ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        view.setIncludeFontPadding(false);
        view.setFallbackLineSpacing(true);
        view.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        view.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        view.setLineSpacing(0, lineSpacing);
    }

    public static List<Page> paginate(CharSequence content, TextView prototype, int width, int height) {
        List<Page> pages = new ArrayList<>();
        if (content == null || content.length() == 0 || width <= 0 || height <= 0) return pages;
        Spanned source = content instanceof Spanned ? (Spanned) content : new SpannedString(content);
        TextPaint paint = new TextPaint(prototype.getPaint());
        BbCodeText.prepare(source, paint, width);
        StaticLayout estimate = layout(source, prototype, paint, width);
        int start = 0;
        while (start < source.length()) {
            int firstLine = estimate.getLineForOffset(start);
            int estimatedLastLine = estimate.getLineForVertical(estimate.getLineTop(firstLine) + height);
            int end = Math.max(nextCharacter(source, start), estimate.getLineEnd(estimatedLastLine));
            boolean scrollable = false;
            Spanned page;
            while (true) {
                page = slice(source, start, end);
                StaticLayout measured = layout(page, prototype, paint, width);
                if (measured.getHeight() <= height) break;
                int lastFittingLine = measured.getLineForVertical(height);
                while (lastFittingLine >= 0 && measured.getLineBottom(lastFittingLine) > height) lastFittingLine--;
                if (lastFittingLine < 0) {
                    // A single table row / very large glyph cannot be divided into smaller text lines.
                    // Put just that line in a scrollable page instead of hiding its lower half.
                    end = start + Math.max(nextCharacter(page, 0), measured.getLineEnd(0));
                    page = slice(source, start, end);
                    scrollable = true;
                    break;
                }
                int shorterEnd = start + measured.getLineEnd(lastFittingLine);
                // A trailing newline produces an extra empty TextView line; carry that newline
                // to the next page if removing it is necessary to fit this page.
                if (shorterEnd >= end) shorterEnd = end - Character.charCount(Character.codePointBefore(source, end));
                int nextEnd = Math.max(nextCharacter(source, start), shorterEnd);
                if (nextEnd >= end) {
                    scrollable = true;
                    break;
                }
                end = nextEnd;
            }
            pages.add(new Page(page, start, end, scrollable));
            start = end;
        }
        return pages;
    }

    private static StaticLayout layout(CharSequence text, TextView view, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(view.getTextDirectionHeuristic())
                .setLineSpacing(view.getLineSpacingExtra(), view.getLineSpacingMultiplier())
                .setUseLineSpacingFromFallbacks(view.isFallbackLineSpacing())
                .setIncludePad(view.getIncludeFontPadding())
                .setBreakStrategy(view.getBreakStrategy())
                .setHyphenationFrequency(view.getHyphenationFrequency())
                .build();
    }

    private static int nextCharacter(CharSequence text, int start) {
        int next = start + Character.charCount(Character.codePointAt(text, start));
        if (text instanceof Spanned) {
            Spanned spans = (Spanned) text;
            for (ReplacementSpan span : spans.getSpans(start, next, ReplacementSpan.class))
                next = Math.max(next, spans.getSpanEnd(span));
        }
        return next;
    }

    private static Spanned slice(Spanned source, int start, int end) {
        SpannableString page = new SpannableString(source.subSequence(start, end));
        // A page boundary in the middle of a paragraph is not a new paragraph. In particular,
        // Word-imported [indent=28.0pt] paragraphs must not gain another first-line indent.
        if (start > 0 && source.charAt(start - 1) != '\n') {
            for (BbCodeRendering.IndentSpan indent : page.getSpans(0, page.length(), BbCodeRendering.IndentSpan.class)) {
                if (source.getSpanStart(indent) >= start) continue;
                int spanEnd = page.getSpanEnd(indent);
                page.removeSpan(indent);
                int nextParagraph = page.toString().indexOf('\n') + 1;
                if (nextParagraph > 0 && nextParagraph < spanEnd)
                    page.setSpan(indent, nextParagraph, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return new SpannedString(page);
    }
}
