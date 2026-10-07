package com.fimtale.editor;

import org.junit.Test;
import static org.junit.Assert.*;

public class VisualEditingTest {
    @Test public void backspaceSkipsClosingTagsAndDeletesAWholeUnicodeCodePoint() {
        String source = "[b]中文🐴[/b]";
        int[] range = VisualEditing.deletion(source, source.length(), true);
        assertEquals("🐴", source.substring(range[0], range[1]));
        String next = source.substring(0, range[0]) + source.substring(range[1]);
        assertEquals("[b]中文[/b]", next);
    }
    @Test public void forwardDeleteSkipsOpeningTagsAndAtomicTablesDeleteAsAUnit() {
        String source = "[b][i]word[/i][/b]";
        int[] range = VisualEditing.deletion(source, 0, false);
        assertEquals("w", source.substring(range[0], range[1]));
        String table = BbCodeInsertion.table(2, 2, true, "A").text;
        range = VisualEditing.deletion("before" + table, 6 + table.length(), true);
        assertEquals(6, range[0]); assertEquals(6 + table.length(), range[1]);
    }
    @Test public void replacingAcrossFormatsRetainsBalancedDelimiters() {
        String source = "[b]first[/b][i]second[/i]";
        int start = source.indexOf("rst"), end = source.indexOf("ond");
        CharSequence replacement = VisualEditing.replacement(source, start, end, "new");
        String result = source.substring(0, start) + replacement + source.substring(end);
        assertEquals("[b]finew[/b][i]ond[/i]", result);
        assertEquals(2, BbCodeSyntax.parse(result).size());
        assertEquals("plain", VisualEditing.replacement(source, 0, source.length(), "plain"));
    }
}
