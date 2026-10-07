package com.fimtale.editor;

import org.junit.Test;
import static org.junit.Assert.*;

public class EditorTableTest {
    @Test public void mergedCellsKeepTheirExactRangesAndGridCoordinates() {
        String source = "[table width=100%]\n[tr][th colspan=2]标题[/th][/tr]\n"
                + "[tr][td rowspan=2][b]中文🐴[/b][/td][td]B[/td][/tr]\n[tr][td]C[/td][/tr][/table]";
        EditorTable table = EditorTable.parse(source);
        assertNotNull(table); assertEquals(3, table.rows); assertEquals(2, table.columns);
        assertEquals(4, table.cells.size());
        assertEquals(2, table.cells.get(0).columnSpan);
        assertEquals(2, table.cells.get(1).rowSpan);
        assertEquals(1, table.cells.get(3).column);
        BbCodeSyntax.Node cell = table.cells.get(1).node;
        assertEquals("[b]中文🐴[/b]", source.substring(cell.contentStart, cell.contentEnd));
        assertEquals("[td rowspan=2]", source.substring(cell.start, cell.contentStart));
    }
    @Test public void nestedTablesDoNotBecomeOuterRowsOrCells() {
        EditorTable table = EditorTable.parse("[table][tr][td][table][tr][td]inner[/td][/tr][/table][/td][td]outer[/td][/tr][/table]");
        assertEquals(1, table.rows); assertEquals(2, table.columns); assertEquals(2, table.cells.size());
    }
}
