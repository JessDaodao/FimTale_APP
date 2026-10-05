package com.fimtale.editor;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class BbCodeSyntaxTest {
    @Test public void nestedTagsKeepExactSourceOffsetsIncludingEmoji() {
        String source = "前言🐴[b]粗体[i]斜体[/i][/b]结尾";
        List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse(source);
        assertEquals(2, nodes.size());
        assertEquals("b", nodes.get(0).name); assertEquals("i", nodes.get(1).name);
        assertEquals("粗体[i]斜体[/i]", source.substring(nodes.get(0).contentStart, nodes.get(0).contentEnd));
        assertEquals("斜体", source.substring(nodes.get(1).contentStart, nodes.get(1).contentEnd));
        assertEquals("[b]粗体[i]斜体[/i][/b]", source.substring(nodes.get(0).start, nodes.get(0).end));
    }
    @Test public void codeMarkdownAndImageContentsStayLiteral() {
        for (String literal : new String[]{"code", "markdown", "img"}) {
            String source = "[" + literal + "][b]raw[/b][/" + literal + "][i]styled[/i]";
            List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse(source);
            assertEquals(2, nodes.size()); assertEquals(literal, nodes.get(0).name);
            assertEquals("[b]raw[/b]", source.substring(nodes.get(0).contentStart, nodes.get(0).contentEnd));
            assertEquals("i", nodes.get(1).name);
        }
    }
    @Test public void incompleteInputDoesNotCreateCrossingOrInvalidRanges() {
        assertTrue(BbCodeSyntax.parse("[b]still typing [i").isEmpty());
        List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse("[b]one[i]two[/b]three[/i]");
        assertEquals(1, nodes.size()); assertEquals("b", nodes.get(0).name);
        assertTrue(BbCodeSyntax.parse("[/b]plain").isEmpty());
        assertEquals(3, BbCodeSyntax.parse("[br][hr][b]bold[/b]").size());
    }
    @Test public void attributesAndTagNamesMatchWebsiteSyntax() {
        List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse("[COLOR='#f00']红[/COLOR][bg-color=blue]背景[/bg-color][url=https://example.com?q=1]链接[/url]");
        assertEquals(3, nodes.size()); assertEquals("color", nodes.get(0).name);
        assertEquals("#f00", nodes.get(0).argument); assertEquals("bg-color", nodes.get(1).name);
        assertEquals("https://example.com?q=1", nodes.get(2).argument);
    }
    @Test public void caretAndReverseSelectionsRevealWholeActiveParagraphs() {
        String source = "[b]一[/b]\n第二段\n最后";
        int second = source.indexOf("第二段"), last = source.indexOf("最后");
        assertArrayEquals(new int[]{second, last - 1}, BbCodeSyntax.activeParagraph(source, second + 1, second + 1));
        assertArrayEquals(new int[]{0, last - 1}, BbCodeSyntax.activeParagraph(source, second + 1, 1));
        assertArrayEquals(new int[]{last, source.length()}, BbCodeSyntax.activeParagraph(source, source.length(), source.length()));
        assertArrayEquals(new int[]{-1, -1}, BbCodeSyntax.activeParagraph(source, -1, -1));
    }
    @Test public void encodedBracketsAndUnknownTagsAreNeverRewritten() {
        String source = "&#91;b&#93;字面量&#91;/b&#93;[custom=x]保留[/custom]";
        List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse(source);
        assertEquals(1, nodes.size()); assertEquals("custom", nodes.get(0).name);
        assertEquals("[custom=x]保留[/custom]", source.substring(nodes.get(0).start, nodes.get(0).end));
    }
    @Test public void quotedAttributesAndImplicitListItemsHaveExactRanges() {
        String source = "[list=1][*]one[list][*]inner[/list][*]two[/list][img alt=\"a ] b\" width='120']/a.png[/img]";
        List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse(source);
        assertEquals(6, nodes.size());
        assertEquals("one[list][*]inner[/list]", source.substring(nodes.get(1).contentStart, nodes.get(1).contentEnd));
        assertEquals("inner", source.substring(nodes.get(3).contentStart, nodes.get(3).contentEnd));
        assertEquals("two", source.substring(nodes.get(4).contentStart, nodes.get(4).contentEnd));
        assertEquals("a ] b", nodes.get(5).attributes.get("alt"));
        assertEquals("120", nodes.get(5).attributes.get("width"));
    }
    @Test public void literalBodiesAndLongArgumentsCannotLeakFormattingOrOverflowTheStack() {
        for (String tag : new String[]{"code", "markdown", "img", "handbook"}) {
            assertTrue(BbCodeSyntax.parse("[" + tag + "][b]unfinished[/b]").isEmpty());
            List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse("İ[" + tag + "]İ[b]literal[/b][/" + tag + "]");
            assertEquals(1, nodes.size());
        }
        assertEquals(1, BbCodeSyntax.parse("[url=" + "a".repeat(20000) + "]body[/url]").size());
        assertTrue(BbCodeSyntax.parse("[b]".repeat(1000) + "body" + "[/b]".repeat(1000)).size() <= 128);
    }
}
