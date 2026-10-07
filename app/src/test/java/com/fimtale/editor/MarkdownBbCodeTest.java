package com.fimtale.editor;

import com.fimtale.utils.BbCode;
import org.junit.Test;
import static org.junit.Assert.*;

public class MarkdownBbCodeTest {
    @Test public void retainsHeadingsStylesLinksCodeAndListStructure() {
        String result = MarkdownBbCode.convert("## 标题\n\n**粗体** *斜体* ~~删除~~ [链接](https://example.org)\n\n- 第一项\n- 第二项\n\n```\n[b]代码[/b]\n```");
        assertTrue(result.contains("[h2]标题[/h2]"));
        assertTrue(result.contains("[b]粗体[/b] [i]斜体[/i] [s]删除[/s]"));
        assertTrue(result.contains("[url=\"https://example.org\"]链接[/url]"));
        assertTrue(result.contains("[*]第一项\n[*]第二项"));
        assertTrue(result.contains("[code][b]代码[/b]\n[/code]"));
    }
    @Test public void markdownTablesBecomeEditableBbcodeTables() {
        String result = MarkdownBbCode.convert("| A | B |\n| :--- | ---: |\n| one | **two** |");
        EditorTable table = EditorTable.parse(result);
        assertNotNull(table); assertEquals(2, table.rows); assertEquals(2, table.columns);
        assertEquals("th", table.cells.get(0).node.name);
        assertEquals("right", table.cells.get(1).node.attributes.get("align"));
        assertTrue(result.contains("[b]two[/b]"));
    }
    @Test public void literalBracketsRemainLiteralInsteadOfBecomingFormatting() {
        String result = MarkdownBbCode.convert("[b]literal[/b]");
        assertTrue(result.contains("&#91;b&#93;literal&#91;/b&#93;"));
        assertFalse(BbCode.toMarkdown(result).contains("<b>literal</b>"));
    }
}
