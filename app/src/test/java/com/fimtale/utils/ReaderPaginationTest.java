package com.fimtale.utils;

import android.app.Application;
import android.text.*;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import com.fimtale.R;
import com.fimtale.ReaderActivity;
import com.google.gson.Gson;
import io.noties.markwon.Markwon;
import java.io.InputStreamReader;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class, qualifiers = "zh-rCN")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ReaderPaginationTest {
    private Markwon markwon;
    @Before public void setup() { markwon = BbCodeRendering.create(RuntimeEnvironment.getApplication()); }
    private Spanned render(String source) { return BbCodeText.normalizeTables(markwon.toMarkdown(BbCode.toMarkdown(source))); }
    private TextView pageView(float fontSize, float spacing) {
        TextView view = LayoutInflater.from(RuntimeEnvironment.getApplication()).inflate(R.layout.item_reader_page, null, false)
                .findViewById(R.id.pageContentTextView);
        ReaderPagination.configure(view, fontSize, spacing, false);
        return view;
    }
    private void assertFits(Spanned text, float size, float spacing, int width, int height) {
        TextView view = pageView(size, spacing);
        BbCodeText.prepare(text, view.getPaint(), width);
        markwon.setParsedMarkdown(view, text);
        int totalWidth = width + view.getPaddingLeft() + view.getPaddingRight();
        int totalHeight = height + view.getPaddingTop() + view.getPaddingBottom();
        view.measure(View.MeasureSpec.makeMeasureSpec(totalWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(totalHeight, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, totalWidth, totalHeight);
        assertTrue("TextView height=" + view.getLayout().getHeight() + ", page=" + height,
                view.getLayout().getHeight() <= height);
    }
    private List<ReaderPagination.Page> checkPages(Spanned source, float size, float spacing, int width, int height) {
        List<ReaderPagination.Page> pages = ReaderPagination.paginate(source, pageView(size, spacing), width, height);
        int offset = 0; StringBuilder recovered = new StringBuilder();
        for (ReaderPagination.Page page : pages) {
            assertEquals(offset, page.start); assertTrue(page.end > page.start);
            assertFalse("Ordinary text must flow to the next page", page.scrollable);
            assertFits(page.text, size, spacing, width, height);
            recovered.append(page.text); offset = page.end;
        }
        assertEquals(source.length(), offset);
        assertEquals(source.toString(), recovered.toString());
        return pages;
    }

    @Test public void activityPagesFitTheActualTextView() throws Exception {
        // Invoke the Activity entry point as well: this originally produced 630px text in a 560px page.
        String source = "[center][b][size=larger]章节标题[/size][/b][/center]\n\n"
                + ("[indent=28.0pt]" + "这是用于验证分页高度与中文首行缩进的测试文字，包含标点符号。".repeat(5) + "[/indent]\n\n").repeat(30);
        Spanned text = render(source);
        ReaderActivity activity = Robolectric.buildActivity(ReaderActivity.class).get();
        Field font = ReaderActivity.class.getDeclaredField("currentFontSize"); font.setAccessible(true); font.setFloat(activity, 20f);
        Method paginate = ReaderActivity.class.getDeclaredMethod("addPagedText", CharSequence.class, int.class, int.class, int.class, int.class, int.class, float.class, int.class);
        paginate.setAccessible(true); paginate.invoke(activity, text, 1, 0, 0, 312, 560, 1.4f, 0);
        Field pagesField = ReaderActivity.class.getDeclaredField("pages"); pagesField.setAccessible(true);
        List<?> pages = (List<?>) pagesField.get(activity);
        assertTrue(pages.size() > 2);
        StringBuilder restored = new StringBuilder();
        for (Object page : pages) {
            Field content = page.getClass().getDeclaredField("content"); content.setAccessible(true);
            Spanned rendered = (Spanned) content.get(page);
            assertFits(rendered, 20, 1.4f, 312, 560); restored.append(rendered);
        }
        assertEquals(text.toString(), restored.toString());
    }
    @Test public void reportedWorkStructureFitsAcrossSizesAndSpacing() throws Exception {
        // Public work 71323's preface/chapter 71324 with CJK prose anonymized, preserving BBCode,
        // punctuation, paragraph lengths and blank lines. No network needed by the regression test.
        try (InputStreamReader input = new InputStreamReader(getClass().getResourceAsStream("/reader-71323.fixture.json"), StandardCharsets.UTF_8)) {
            for (String source : new Gson().fromJson(input, String[].class)) {
                for (BbCodeText.Segment segment : BbCodeText.segments(render(source))) {
                    if (segment.image != null) continue;
                    for (float size : new float[]{16f, 20f, 28f}) {
                        for (float spacing : new float[]{1f, 1.4f, 1.8f})
                            checkPages((Spanned) segment.text, size, spacing, 312, 560);
                        checkPages((Spanned) segment.text, size, 1.4f, 560, 240);
                    }
                }
            }
        }
    }
    @Test public void largerSystemFontUsesTheSameSpConversionAsTextView() {
        RuntimeEnvironment.setFontScale(1.8f);
        try {
            Spanned text = render("[indent=28.0pt]" + "大字体中文正文。".repeat(160) + "[/indent]");
            checkPages(text, 28, 1.4f, 312, 560);
        } finally { RuntimeEnvironment.setFontScale(1f); }
    }

    @Test public void continuationKeepsStylesWithoutAddingAnotherFirstLineIndent() {
        Spanned source = render("[indent=28.0pt][color=teal][b]" + "连续中文正文。".repeat(180) + "[/b][/color][/indent]");
        List<ReaderPagination.Page> pages = checkPages(source, 20, 1.4f, 312, 360);
        assertTrue(pages.size() > 2);
        Spanned second = pages.get(1).text;
        assertEquals(0, second.getSpans(0, second.length(), BbCodeRendering.IndentSpan.class).length);
        assertEquals(1, second.getSpans(0, 1, io.noties.markwon.core.spans.StrongEmphasisSpan.class).length);
        assertEquals(1, second.getSpans(0, 1, android.text.style.ForegroundColorSpan.class).length);
    }
    @Test public void headingsListsQuotesAndCodeFlowWithoutDroppingCharacters() {
        Spanned source = render("[h1]较大标题[/h1][quote][list=1][*]" + "中英混排 text ".repeat(50)
                + "[*][size=larger]" + "较大文字。".repeat(100) + "[/size][/list][/quote][code]"
                + "  if (x) { /* 中文注释 */ }\n".repeat(70) + "[/code]\n\n尾部正文");
        checkPages(source, 20, 1.4f, 312, 560);
    }
    @Test public void indivisibleTallRowsRemainAccessibleAndPaginationAlwaysAdvances() {
        Spanned table = render("before[table][tr][td]" + "长单元格\n".repeat(40) + "[/td][td]value[/td][/tr][/table]after");
        List<ReaderPagination.Page> pages = ReaderPagination.paginate(table, pageView(20, 1.4f), 312, 360);
        assertTrue(pages.stream().anyMatch(p -> p.scrollable));
        StringBuilder restored = new StringBuilder();
        for (ReaderPagination.Page page : pages) {
            if (!page.scrollable) assertFits(page.text, 20, 1.4f, 312, 360);
            assertTrue(page.end > page.start); restored.append(page.text);
        }
        assertEquals(table.toString(), restored.toString());
        assertEquals(1, ReaderPagination.paginate(new SpannedString("\n"), pageView(20, 1.4f), 312, 1).size());
        List<ReaderPagination.Page> emoji = ReaderPagination.paginate(new SpannedString("😀😀"), pageView(20, 1.4f), 25, 1);
        assertEquals(2, emoji.size()); assertEquals("😀", emoji.get(0).text.toString());
    }
}
