package com.fimtale.editor;

import java.util.ArrayList;
import java.util.List;

/** Cell coordinates and exact source ranges, decoded from the shared native BBCode parser. */
final class EditorTable {
    static final class Cell {
        final BbCodeSyntax.Node node;
        final int row, column, rowSpan, columnSpan;
        Cell(BbCodeSyntax.Node node, int row, int column, int rowSpan, int columnSpan) {
            this.node = node; this.row = row; this.column = column;
            this.rowSpan = rowSpan; this.columnSpan = columnSpan;
        }
    }
    final String source;
    final List<Cell> cells = new ArrayList<>();
    int rows, columns;
    private EditorTable(String source) { this.source = source; }

    static EditorTable parse(String source) {
        List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse(source);
        if (nodes.isEmpty() || !nodes.get(0).name.equals("table")) return null;
        BbCodeSyntax.Node table = nodes.get(0);
        EditorTable result = new EditorTable(source);
        List<BbCodeSyntax.Node> rows = new ArrayList<>();
        List<BbCodeSyntax.Node> nested = new ArrayList<>();
        for (BbCodeSyntax.Node node : nodes)
            if (node != table && node.name.equals("table")) nested.add(node);
        for (BbCodeSyntax.Node node : nodes) {
            if (!node.name.equals("tr") || inside(node, nested)) continue;
            rows.add(node);
        }
        if (rows.isEmpty()) return null;
        result.rows = rows.size();
        int[] occupied = new int[32];
        for (int r = 0; r < rows.size(); r++) {
            BbCodeSyntax.Node row = rows.get(r);
            int column = 0;
            for (BbCodeSyntax.Node cell : nodes) {
                if (!(cell.name.equals("td") || cell.name.equals("th")) || cell.start < row.contentStart
                        || cell.end > row.contentEnd || inside(cell, nested)) continue;
                int columns = positive(cell.attributes.get("colspan"), 32);
                while (column < 32 && !free(occupied, column, columns)) column++;
                if (column + columns > 32) return null;
                int rowspan = positive(cell.attributes.get("rowspan"), rows.size() - r);
                result.cells.add(new Cell(cell, r, column, rowspan, columns));
                for (int c = column; c < column + columns; c++) occupied[c] = rowspan;
                column += columns;
                result.columns = Math.max(result.columns, column);
            }
            for (int c = 0; c < occupied.length; c++) occupied[c] = Math.max(0, occupied[c] - 1);
        }
        return result.cells.isEmpty() ? null : result;
    }
    private static boolean inside(BbCodeSyntax.Node node, List<BbCodeSyntax.Node> parents) {
        for (BbCodeSyntax.Node parent : parents)
            if (node.start >= parent.contentStart && node.end <= parent.contentEnd) return true;
        return false;
    }
    private static boolean free(int[] occupied, int start, int count) {
        if (start + count > occupied.length) return false;
        for (int i = start; i < start + count; i++) if (occupied[i] > 0) return false;
        return true;
    }
    private static int positive(String value, int max) {
        try { return Math.max(1, Math.min(max, Integer.parseInt(value))); }
        catch (RuntimeException ignored) { return 1; }
    }
}
