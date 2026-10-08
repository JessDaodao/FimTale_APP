package com.fimtale.editor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Keyboard edits skip hidden delimiters instead of leaving half of a BBCode pair behind. */
final class VisualEditing {
    static final java.util.regex.Pattern ENTITIES = java.util.regex.Pattern.compile("&(?:#[0-9]+|#x[0-9a-fA-F]+|amp|lt|gt|quot|apos|nbsp);");
    private VisualEditing() {}
    static String decodeEntities(String value) {
        java.util.regex.Matcher entities = ENTITIES.matcher(value);
        StringBuffer result = new StringBuffer();
        while (entities.find()) entities.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(
                android.text.Html.fromHtml(entities.group(), android.text.Html.FROM_HTML_MODE_LEGACY).toString()));
        entities.appendTail(result); return result.toString();
    }
    static int[] deletion(String source, int caret, boolean backward) {
        List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse(source);
        int cursor = Math.max(0, Math.min(source.length(), caret));
        for (BbCodeSyntax.Node node : nodes) {
            boolean touches = backward ? cursor > node.start && cursor <= node.end : cursor >= node.start && cursor < node.end;
            if (touches && (atomic(node.name) || node.contentStart == node.contentEnd)) return new int[]{node.start, node.end};
        }
        boolean moved;
        do {
            moved = false;
            for (BbCodeSyntax.Node node : nodes) {
                int next = cursor;
                if (backward) {
                    if (cursor > node.start && cursor <= node.contentStart) next = node.start;
                    else if (cursor > node.contentEnd && cursor <= node.end) next = node.contentEnd;
                } else {
                    if (cursor >= node.start && cursor < node.contentStart) next = node.contentStart;
                    else if (cursor >= node.contentEnd && cursor < node.end) next = node.end;
                }
                if (next != cursor) { cursor = next; moved = true; }
            }
        } while (moved);
        boolean literal = false;
        for (BbCodeSyntax.Node node : nodes) if (node.name.equals("code") && cursor >= node.contentStart && cursor <= node.contentEnd) literal = true;
        if (!literal) {
            java.util.regex.Matcher entities = ENTITIES.matcher(source);
            while (entities.find()) if (backward ? cursor > entities.start() && cursor <= entities.end()
                    : cursor >= entities.start() && cursor < entities.end()) return new int[]{entities.start(), entities.end()};
        }
        if (backward && cursor > 0) return new int[]{Character.offsetByCodePoints(source, cursor, -1), cursor};
        if (!backward && cursor < source.length()) return new int[]{cursor, Character.offsetByCodePoints(source, cursor, 1)};
        return new int[]{caret, caret};
    }
    private static boolean atomic(String tag) {
        return tag.equals("table") || tag.equals("img") || tag.equals("markdown") || tag.equals("hr")
                || tag.equals("ref") || tag.equals("handbook");
    }
    static CharSequence replacement(String source, int start, int end, CharSequence inserted) {
        if (start < 0 || end <= start || end > source.length()) return inserted;
        List<int[]> keep = new ArrayList<>();
        for (BbCodeSyntax.Node node : BbCodeSyntax.parse(source)) {
            if (node.start >= start && node.end <= end) continue;
            retain(keep, start, end, node.start, node.contentStart);
            retain(keep, start, end, node.contentEnd, node.end);
        }
        if (keep.isEmpty()) return inserted;
        keep.sort(Comparator.comparingInt(range -> range[0]));
        StringBuilder result = new StringBuilder();
        int cursor = start; boolean added = false;
        for (int[] range : keep) {
            if (!added && range[0] > cursor) { result.append(inserted); added = true; }
            result.append(source, range[0], range[1]); cursor = range[1];
        }
        if (!added) result.append(inserted);
        return result;
    }
    private static void retain(List<int[]> ranges, int start, int end, int from, int to) {
        if (from < end && to > start && from < to) ranges.add(new int[]{Math.max(start, from), Math.min(end, to)});
    }
    static String selectedText(String source, int start, int end) {
        List<int[]> tokens = new ArrayList<>();
        for (BbCodeSyntax.Node node : BbCodeSyntax.parse(source)) {
            retain(tokens, start, end, node.start, node.contentStart);
            retain(tokens, start, end, node.contentEnd, node.end);
        }
        tokens.sort(Comparator.comparingInt(range -> range[0]));
        StringBuilder result = new StringBuilder(); int cursor = start;
        for (int[] token : tokens) {
            if (token[0] > cursor) result.append(source, cursor, token[0]);
            cursor = Math.max(cursor, token[1]);
        }
        if (cursor < end) result.append(source, cursor, end);
        return result.toString();
    }
}
