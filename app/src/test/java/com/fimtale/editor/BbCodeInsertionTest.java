package com.fimtale.editor;

import com.fimtale.utils.BbCode;
import org.junit.Test;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = com.fimtale.ResourceApplication.class)
public class BbCodeInsertionTest {
    private String apply(String source, BbCodeInsertion.Edit edit) {
        return source.substring(0, edit.start) + edit.replacement + source.substring(edit.end);
    }
    @Test public void inlineFormatsPreserveUnicodeAndReverseSelections() {
        String source = "前🐴正文后";
        BbCodeInsertion.Edit edit = BbCodeInsertion.at(source, 5, 3, BbCodeInsertion.wrap("u", "正文", false));
        assertEquals("前🐴[u]正文[/u]后", apply(source, edit));
        assertEquals("正文", apply(source, edit).substring(edit.selectionStart, edit.selectionEnd));
        assertEquals("<div>前🐴<u>正文</u>后</div>", BbCode.toMarkdown(apply(source, edit)));
    }
    @Test public void emptyInlineInsertionPlacesCaretInsideTheTags() {
        BbCodeInsertion.Edit edit = BbCodeInsertion.at("前后", 1, 1, BbCodeInsertion.wrap("size=larger", "", false));
        String source = apply("前后", edit);
        assertEquals("前[size=larger][/size]后", source);
        assertEquals(source.indexOf("[/size]"), edit.selectionStart);
        assertEquals(edit.selectionStart, edit.selectionEnd);
    }
    @Test public void blocksDoNotMergeWithNeighboringParagraphsOrAddDuplicateBreaks() {
        BbCodeInsertion.Fragment heading = BbCodeInsertion.wrap("h2", "标题", true);
        BbCodeInsertion.Edit edit = BbCodeInsertion.at("前标题后", 1, 3, heading);
        assertEquals("前\n[h2]标题[/h2]\n后", apply("前标题后", edit));
        assertEquals("标题", apply("前标题后", edit).substring(edit.selectionStart, edit.selectionEnd));
        edit = BbCodeInsertion.at("前\n标题\n后", 2, 4, heading);
        assertEquals("前\n[h2]标题[/h2]\n后", apply("前\n标题\n后", edit));
    }
    @Test public void listsCreateAnItemForEachSelectedLineAndRemainReadable() {
        for (boolean ordered : new boolean[]{false, true}) {
            BbCodeInsertion.Fragment fragment = BbCodeInsertion.list("苹果\n[b]香蕉[/b]\n", ordered);
            String html = BbCode.toMarkdown(fragment.text);
            assertTrue(html.contains(ordered ? "<ol>" : "<ul>"));
            assertTrue(html.contains("<li>苹果"));
            assertTrue(html.contains("<li><b>香蕉</b>"));
            assertEquals("苹果", fragment.text.substring(fragment.selectionStart, fragment.selectionEnd));
        }
        BbCodeInsertion.Fragment empty = BbCodeInsertion.list("", false);
        assertEquals(empty.selectionStart, empty.selectionEnd);
        assertTrue(empty.text.substring(0, empty.selectionStart).endsWith("[*]"));
    }
    @Test public void tablesPreserveTheSelectionInAnEditableCellWithOptionalHeaders() {
        BbCodeInsertion.Fragment table = BbCodeInsertion.table(3, 2, true, "中文🐴");
        String html = BbCode.toMarkdown(table.text);
        assertTrue(html.contains("<th>中文🐴</th>"));
        assertEquals(3, html.split("<tr>", -1).length - 1);
        assertEquals(4, html.split("<td>", -1).length - 1);
        assertEquals("中文🐴", table.text.substring(table.selectionStart, table.selectionEnd));
        assertFalse(BbCode.toMarkdown(BbCodeInsertion.table(1, 1, false, "").text).contains("<th>"));
        assertThrows(IllegalArgumentException.class, () -> BbCodeInsertion.table(0, 2, true, ""));
        assertThrows(IllegalArgumentException.class, () -> BbCodeInsertion.table(20, 11, true, ""));
    }
    @Test public void linksImagesAndAttributesCannotBreakGeneratedSyntax() {
        String link = BbCodeInsertion.link("https://example.org/?a=1&b=2", "[b]链接[/b]").text;
        String html = BbCode.toMarkdown(link);
        assertTrue(html.contains("href=\"https://example.org/?a=1&amp;b=2\""));
        assertTrue(html.contains("<b>链接</b>"));
        String image = BbCodeInsertion.image("/img/test.png", "120", "80", "说明 [b] 与 \"引号\"").text;
        html = BbCode.toMarkdown(image);
        assertTrue(html.contains("width=\"120\"")); assertTrue(html.contains("height=\"80\""));
        assertTrue(html.contains("alt=\"说明 [b] 与 &quot;引号&quot;\""));
        assertEquals(1, BbCodeSyntax.parse(image).size());
        assertThrows(IllegalArgumentException.class, () -> BbCodeInsertion.link("javascript:alert(1)", "x"));
        assertThrows(IllegalArgumentException.class, () -> BbCodeInsertion.image("https://example.org/a.png", "-3", "", ""));
    }
    @Test public void rawBlocksPreserveTheirContentEvenWhenItContainsClosingTags() {
        String source = "[b]literal[/b]\n[/CoDe]tail";
        String html = BbCode.toMarkdown(BbCodeInsertion.raw("code", source).text);
        assertTrue(html.contains("[b]literal[/b]"));
        assertTrue(html.contains("[/CoDe]tail"));
        assertFalse(html.contains("<b>literal</b>"));
        String markdown = BbCodeInsertion.raw("markdown", "## Heading\n\n**strong**").text;
        assertTrue(BbCode.toMarkdown(markdown).contains("<strong>strong</strong>"));
    }
    @Test public void referencesAndMentionsUseWebsiteIdsAndEscapeLiteralLabels() {
        String mention = BbCodeInsertion.mention(7, "pony[b]").text;
        assertEquals(1, BbCodeSyntax.parse(mention).size());
        assertTrue(BbCode.toMarkdown(mention).contains("?uid=7"));
        String reference = BbCodeInsertion.reference(3, 42, "章节 [b]").text;
        assertTrue(BbCode.toMarkdown(reference).contains("data-ref=\"3:42\""));
        assertTrue(BbCode.toMarkdown(reference).contains("章节 [b]"));
        assertThrows(IllegalArgumentException.class, () -> BbCodeInsertion.reference(2, 42, ""));
    }
    @Test public void everyImmediateFormatUsesRecognizedRenderableTags() {
        for (EditorFormat format : EditorFormat.values()) if (format.immediate()) {
            BbCodeInsertion.Fragment fragment = format.fragment("正文");
            assertFalse(format.name(), BbCodeSyntax.parse(fragment.text).isEmpty());
            String html = BbCode.toMarkdown(fragment.text);
            assertTrue(format.name(), format == EditorFormat.RULE ? html.contains("<hr>")
                    : format == EditorFormat.SPOILER ? html.contains("data-spoiler") : html.contains("正文"));
            assertFalse(format.name(), html.contains("[/"));
        }
    }
}
