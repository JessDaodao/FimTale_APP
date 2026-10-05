package com.fimtale.utils;

import android.app.Application;
import android.graphics.Color;
import android.text.*;
import android.text.style.*;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.spans.StrongEmphasisSpan;
import io.noties.markwon.core.spans.EmphasisSpan;
import io.noties.markwon.ext.tables.TableRowSpan;
import io.noties.markwon.image.AsyncDrawableSpan;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BbCodeRenderingTest {
    private Markwon renderer;
    @Before public void setup() { renderer = BbCodeRendering.create(RuntimeEnvironment.getApplication()); }
    private Spanned render(String source) { return BbCodeText.normalizeTables(renderer.toMarkdown(BbCode.toMarkdown(source))); }
    @Test public void rendersLiteralTextStylesAndNewlines() {
        Spanned text = render("# *literal*\n[b]bold[/b]\n[color=teal]a[color=navy]b[/color]c[/color]\n[code]  [b]code[/b]\n  line2[/code]");
        assertTrue(text.toString(), text.toString().contains("# *literal*\nbold"));
        assertTrue(text.toString(), text.toString().contains("  [b]code[/b]\n  line2"));
        assertEquals(1, text.getSpans(0, text.length(), StrongEmphasisSpan.class).length);
        int b = text.toString().indexOf("abc") + 1;
        ForegroundColorSpan[] colors = text.getSpans(b, b + 1, ForegroundColorSpan.class);
        assertEquals(1, colors.length); assertEquals(Color.parseColor("navy"), colors[0].getForegroundColor());
    }
    @Test public void codeImagesAreLiteralAndEmojiStaysInline() {
        Spanned text = render("a :ftemoji_wahaha: b\n[code]![alt](https://x.test/x.png)[img]literal[/img][/code]\n[img]/a.png[/img]\nafter");
        assertEquals(2, text.getSpans(0, text.length(), AsyncDrawableSpan.class).length);
        java.util.List<BbCodeText.Segment> segments = BbCodeText.segments(text);
        assertEquals(3, segments.size());
        assertTrue(segments.get(0).text.toString().contains("a :ftemoji_wahaha: b"));
        assertNotNull(segments.get(1).image); assertTrue(segments.get(2).text.toString().contains("after"));
    }
    @Test public void hiddenContentIsClickableAndNotRenderedOrLoaded() {
        Spanned text = render("[spoiler]secret[img]/secret.png[/img][/spoiler][collapse=Note][b]body[/b][/collapse]");
        assertFalse(text.toString().contains("secret"));
        assertEquals(0, text.getSpans(0, text.length(), AsyncDrawableSpan.class).length);
        assertEquals(2, text.getSpans(0, text.length(), ClickableSpan.class).length);
    }
    @Test public void tablesHaveMeasuredAtomicRows() {
        Spanned text = render("before[table][tr][th colspan=2]Name[/th][/tr][tr][td][b]Alice[/b][/td][td]100[/td][/tr][/table]after");
        TableRowSpan[] rows = text.getSpans(0, text.length(), TableRowSpan.class);
        assertEquals(text.toString(), 2, rows.length);
        TextPaint paint = new TextPaint(); paint.setTextSize(20);
        BbCodeText.prepare(text, paint, 400);
        for (TableRowSpan row : rows) {
            assertEquals(1, text.getSpanEnd(row) - text.getSpanStart(row));
            android.graphics.Paint.FontMetricsInt metrics = new android.graphics.Paint.FontMetricsInt();
            assertEquals(400, row.getSize(paint, text, text.getSpanStart(row), text.getSpanEnd(row), metrics));
            assertTrue(metrics.descent - metrics.ascent > 20);
        }
        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, 400).build();
        assertTrue(layout.getHeight() > 60);
        String wide = "[table][tr]" + "[td]cell[/td]".repeat(40) + "[td]last[/td][/tr][/table]";
        Spanned fallback = render(wide);
        BbCodeText.prepare(fallback, paint, 160);
        assertTrue(fallback.toString().contains("last"));
        assertEquals(0, fallback.getSpans(0, fallback.length(), TableRowSpan.class).length);
    }
    @Test public void pageAndVerticalSlicesRetainCrossBoundaryFormatting() {
        Spanned text = render("[color=teal][b]" + "长段落用于验证跨页样式。".repeat(300) + "[/b][/color]");
        TextPaint paint = new TextPaint(); paint.setTextSize(24);
        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, 320).build();
        assertTrue(layout.getLineCount() > 10);
        int split = layout.getLineStart(5);
        Spanned second = (Spanned) text.subSequence(split, text.length());
        assertEquals(1, second.getSpans(0, 1, StrongEmphasisSpan.class).length);
        assertEquals(1, second.getSpans(0, 1, ForegroundColorSpan.class).length);
        Spanned multiline = render("[i]" + "line\n".repeat(500) + "[/i]");
        for (CharSequence chunk : BbCodeText.verticalChunks(multiline)) {
            assertTrue(chunk instanceof Spanned);
            assertEquals(1, ((Spanned) chunk).getSpans(0, chunk.length(), EmphasisSpan.class).length);
        }
    }
    @Test public void cssColorsMatchWebChannelOrder() {
        assertEquals(Integer.valueOf(0x88112233), BbCodeRendering.cssColor("#1238"));
        assertEquals(Integer.valueOf(0x80ff0000), BbCodeRendering.cssColor("rgba(255, 0, 0, 0.5)"));
        assertEquals(Integer.valueOf(0xff00ff00), BbCodeRendering.cssColor("hsl(120, 100%, 50%)"));
    }
    @Test public void nestedBlocksAlignmentAndRelativeSizesProduceNativeSpans() {
        Spanned text = render("[quote]outer[quote]inner[/quote][/quote][list=1][*]one[list][*]nested[/list][*]two[/list]"
                + "[center]middle[/center][indent=2em]indented[/indent][size=larger][size=larger]large[/size][/size][sub]a[/sub][sup]b[/sup]");
        assertEquals(2, text.getSpans(0, text.length(), io.noties.markwon.core.spans.BlockQuoteSpan.class).length);
        assertEquals(2, text.getSpans(0, text.length(), io.noties.markwon.core.spans.OrderedListItemSpan.class).length);
        assertEquals(1, text.getSpans(0, text.length(), io.noties.markwon.core.spans.BulletListItemSpan.class).length);
        assertEquals(Layout.Alignment.ALIGN_CENTER, text.getSpans(0, text.length(), AlignmentSpan.class)[0].getAlignment());
        assertEquals(1.44f, text.getSpans(0, text.length(), RelativeSizeSpan.class)[0].getSizeChange(), 0.001f);
        TextPaint paint = new TextPaint(); paint.setTextSize(24);
        BbCodeText.prepare(text, paint, 400);
        assertEquals(48, text.getSpans(0, text.length(), BbCodeRendering.IndentSpan.class)[0].getLeadingMargin(true));
    }
    @Test public void frontendCorpusRendersAndMeasuresOnAndroid() throws Exception {
        try (java.io.InputStreamReader input = new java.io.InputStreamReader(getClass().getResourceAsStream("/bbcode-corpus.fixture.json"), java.nio.charset.StandardCharsets.UTF_8)) {
            String[] corpus = new com.google.gson.Gson().fromJson(input, String[].class);
            TextPaint paint = new TextPaint(); paint.setTextSize(20);
            for (String source : corpus) {
                Spanned text = render(source);
                BbCodeText.prepare(text, paint, 400);
                StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, 400).build();
                assertTrue(layout.getLineCount() > 0);
            }
        }
    }
}
