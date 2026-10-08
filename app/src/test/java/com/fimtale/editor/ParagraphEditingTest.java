package com.fimtale.editor;

import org.junit.Test;
import static org.junit.Assert.*;

public class ParagraphEditingTest {
    @Test public void everyPastedNewlineStartsAnIndentedParagraph() {
        String source = "existing\nsecond\nthird";
        ParagraphEditing.Edit edit = ParagraphEditing.newlines(source, 8, source.length(), source.length(), source.length());
        assertEquals("existing\n[indent]second[/indent]\n[indent]third[/indent]", edit.source);
        assertEquals(edit.source.indexOf("third") + 5, edit.selectionStart);
    }

    @Test public void splittingAnIndentedStylePreservesItsIndentWidthAndBalancedFormatting() {
        String source = "[indent=3em][b]第一段\n第二段[/b][/indent]";
        int caret = source.indexOf("第二段") + 3;
        ParagraphEditing.Edit edit = ParagraphEditing.newlines(source, source.indexOf('\n'), caret, caret, caret);
        assertEquals("[indent=3em][b]第一段[/b][/indent]\n[indent=3em][b]第二段[/b][/indent]", edit.source);
        assertEquals(edit.source.indexOf("第二段") + 3, edit.selectionEnd);
        assertEquals(4, BbCodeSyntax.parse(edit.source).size());
    }

    @Test public void indentStaysInsideTheCollapseAndInlineStylesCanContinueBeyondTheNewParagraph() {
        String source = "[collapse=标题][b]before\nnew tail\nexisting[/b][/collapse]";
        int newline = source.indexOf('\n');
        ParagraphEditing.Edit edit = ParagraphEditing.newlines(source, newline, newline + 1, newline + 1, newline + 1);
        assertEquals("[collapse=标题][b]before[/b]\n[indent][b]new tail[/b][/indent][b]\nexisting[/b][/collapse]", edit.source);
        assertEquals(edit.source.indexOf("new tail"), edit.selectionStart);
    }

    @Test public void literalBlocksTablesAndExplicitPastedFormatsKeepTheirSource() {
        for (String source : new String[]{"[code]a\nb[/code]", "[markdown]**a**\nb[/markdown]",
                "[table][tr][td]a\nb[/td][/tr][/table]", "[h2]a\nb[/h2]",
                "a\n[indent=4em]b[/indent]\n[quote]quoted[/quote]"}) {
            assertEquals(source, ParagraphEditing.newlines(source, 0, source.length(), source.length(), source.length()).source);
        }
    }

    @Test public void onlyEmptyProseParagraphsAreSeeded() {
        ParagraphEditing.Edit empty = ParagraphEditing.seed("", 0, 0, "中文");
        assertNotNull(empty); assertEquals("[indent][/indent]", empty.source); assertEquals(8, empty.selectionStart);
        String collapse = "[collapse=标题][/collapse]";
        int caret = collapse.indexOf("[/collapse]");
        assertEquals("[collapse=标题][indent][/indent][/collapse]", ParagraphEditing.seed(collapse, caret, caret, "内容").source);
        assertNull(ParagraphEditing.seed("plain", 3, 3, "x"));
        assertNull(ParagraphEditing.seed("[indent][/indent]", 8, 8, "x"));
        assertNull(ParagraphEditing.seed("[code][/code]", 6, 6, "x"));
        assertNull(ParagraphEditing.seed("", 0, 0, "[table][tr][td]x[/td][/tr][/table]"));
    }

    @Test public void joiningParagraphsRemovesTheBoundaryAndRetainsBothSidesFormatting() {
        String source = "[indent][b]first[/b][/indent]\n[indent][i]second[/i][/indent]";
        ParagraphEditing.Edit edit = ParagraphEditing.join(source, source.indexOf('\n'));
        assertNotNull(edit);
        assertEquals("[indent][b]first[/b][i]second[/i][/indent]", edit.source);
        assertEquals(edit.source.indexOf("[i]"), edit.selectionStart);
        source = "first\n[indent]second[/indent]";
        assertEquals("firstsecond", ParagraphEditing.join(source, source.indexOf('\n')).source);
    }
}
