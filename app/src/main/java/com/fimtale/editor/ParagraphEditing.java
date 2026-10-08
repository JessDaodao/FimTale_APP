package com.fimtale.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Paragraph edits use the same two-em [indent] format as the web editor. */
final class ParagraphEditing {
    private static final String OPEN = "[indent]", CLOSE = "[/indent]";
    private static final Set<String> INLINE = new HashSet<>(Arrays.asList(
            "b", "i", "u", "s", "sub", "sup", "color", "bg-color", "font", "size", "url", "spoiler"));
    private static final Set<String> CONTAINERS = new HashSet<>(Arrays.asList(
            "collapse", "quote", "left", "center", "right", "justify", "p"));
    private static final Set<String> SPECIAL = new HashSet<>(Arrays.asList(
            "code", "markdown", "handbook", "img", "table", "tr", "td", "th", "list", "*",
            "h1", "h2", "h3", "h4", "h5", "h6", "mention", "hash", "ref", "hr", "br"));
    private ParagraphEditing() {}

    static final class Edit {
        String source;
        int selectionStart, selectionEnd;
        Edit(String source, int start, int end) { this.source = source; selectionStart = start; selectionEnd = end; }
        private void insert(int at, String value) {
            source = source.substring(0, at) + value + source.substring(at);
            // A closing tag inserted at the caret belongs after the caret.
            if (selectionStart > at) selectionStart += value.length();
            if (selectionEnd > at) selectionEnd += value.length();
        }
        private void replaceBreak(int at, String value) {
            source = source.substring(0, at) + value + source.substring(at + 1);
            if (selectionStart > at) selectionStart += value.length() - 1;
            if (selectionEnd > at) selectionEnd += value.length() - 1;
        }
    }

    /** Seed only an empty paragraph being typed into; loading a draft never changes its source. */
    static Edit seed(String source, int start, int end, String incoming) {
        if (start < 0 || end < start || end > source.length() || incoming.isEmpty()) return null;
        for (BbCodeSyntax.Node node : BbCodeSyntax.parse(incoming))
            if (!INLINE.contains(node.name)) return null;
        int from = start == 0 ? 0 : source.lastIndexOf('\n', start - 1) + 1;
        int to = source.indexOf('\n', end);
        if (to < 0) to = source.length();
        List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse(source);
        for (BbCodeSyntax.Node node : nodes) {
            if (start < node.contentStart || end > node.contentEnd) continue;
            if (node.name.equals("indent") || SPECIAL.contains(node.name)) return null;
            if (CONTAINERS.contains(node.name)) { from = Math.max(from, node.contentStart); to = Math.min(to, node.contentEnd); }
        }
        if (from > start || to < end) return null;
        for (BbCodeSyntax.Node node : nodes) {
            if (node.start >= start && node.end <= end) continue;
            if (node.start >= from && node.end <= to && !INLINE.contains(node.name)) return null;
            if (INLINE.contains(node.name) && ((node.start < from && node.end > from) || (node.start < to && node.end > to))) return null;
        }
        String remaining = source.substring(from, start) + source.substring(end, to);
        if (!VisualEditing.selectedText(remaining, 0, remaining.length()).trim().isEmpty()) return null;
        return new Edit(source.substring(0, from) + OPEN + source.substring(from, to) + CLOSE + source.substring(to),
                start + OPEN.length(), end + OPEN.length());
    }

    /** Split only new, explicitly inserted line breaks; automatic line wrapping is untouched. */
    static Edit newlines(String source, int from, int to, int selectionStart, int selectionEnd) {
        Edit edit = new Edit(source, selectionStart, selectionEnd);
        for (int at = Math.min(to, source.length()) - 1; at >= Math.max(0, from); at--)
            if (source.charAt(at) == '\n') indentAfterBreak(edit, at);
        return edit;
    }

    private static void indentAfterBreak(Edit edit, int at) {
        String source = edit.source;
        List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse(source), parents = new ArrayList<>();
        int container = -1, indent = -1;
        for (BbCodeSyntax.Node node : nodes) {
            if (node.start > at || node.end <= at) continue;
            if (at < node.contentStart || at >= node.contentEnd || SPECIAL.contains(node.name)) return;
            if (!INLINE.contains(node.name) && !CONTAINERS.contains(node.name) && !node.name.equals("indent")) return;
            parents.add(node);
            if (CONTAINERS.contains(node.name)) container = parents.size() - 1;
            if (node.name.equals("indent")) indent = parents.size() - 1;
        }
        if (indent > container) {
            List<BbCodeSyntax.Node> split = parents.subList(indent, parents.size());
            edit.replaceBreak(at, closing(source, split) + "\n" + opening(source, split));
            return;
        }

        int end = source.indexOf('\n', at + 1);
        if (end < 0) end = source.length();
        if (container >= 0) end = Math.min(end, parents.get(container).contentEnd);
        // Existing block/indent markup on the following line already owns its paragraph format.
        for (BbCodeSyntax.Node node : nodes)
            if (node.start > at && node.start < end && !INLINE.contains(node.name)) return;
        List<BbCodeSyntax.Node> split = parents.subList(container + 1, parents.size());
        List<BbCodeSyntax.Node> continuing = new ArrayList<>();
        for (BbCodeSyntax.Node node : nodes)
            if (INLINE.contains(node.name) && node.contentStart < end && node.contentEnd >= end
                    && (container < 0 || node.start > parents.get(container).start)) continuing.add(node);
        edit.insert(end, closing(source, continuing) + CLOSE + opening(source, continuing));
        edit.replaceBreak(at, closing(source, split) + "\n" + OPEN + opening(source, split));
    }

    private static String opening(String source, List<BbCodeSyntax.Node> nodes) {
        StringBuilder result = new StringBuilder();
        for (BbCodeSyntax.Node node : nodes) result.append(source, node.start, node.contentStart);
        return result.toString();
    }
    private static String closing(String source, List<BbCodeSyntax.Node> nodes) {
        StringBuilder result = new StringBuilder();
        for (int i = nodes.size() - 1; i >= 0; i--) {
            BbCodeSyntax.Node node = nodes.get(i); result.append(source, node.contentEnd, node.end);
        }
        return result.toString();
    }

    /** Backspace/Delete across a paragraph boundary also joins its indent wrappers. */
    static Edit join(String source, int newline) {
        if (newline < 0 || newline >= source.length() || source.charAt(newline) != '\n') return null;
        BbCodeSyntax.Node left = null, right = null;
        for (BbCodeSyntax.Node node : BbCodeSyntax.parse(source)) if (node.name.equals("indent")) {
            if (node.end == newline) left = node;
            if (node.start == newline + 1) right = node;
        }
        if (right == null) return null;
        if (left != null) return new Edit(source.substring(0, left.contentEnd) + source.substring(right.contentStart), left.contentEnd, left.contentEnd);
        return new Edit(source.substring(0, newline) + source.substring(right.contentStart, right.contentEnd) + source.substring(right.end), newline, newline);
    }
}
