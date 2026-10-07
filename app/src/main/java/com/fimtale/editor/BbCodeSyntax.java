package com.fimtale.editor;

import java.util.*;

/** Source offsets for BBCode. Unlike rendering, parsing never normalizes the author's text. */
public final class BbCodeSyntax {
    private BbCodeSyntax() {}

    public static final class Node {
        public final String name, argument;
        public final Map<String, String> attributes;
        public final int start, contentStart, contentEnd, end;
        private Node(String name, String argument, Map<String, String> attributes, int start, int contentStart, int contentEnd, int end) {
            this.name = name; this.argument = argument; this.attributes = attributes;
            this.start = start; this.contentStart = contentStart; this.contentEnd = contentEnd; this.end = end;
        }
    }

    public static List<Node> parse(String source) {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        int[] packed = BbCodeNative.parse(source);
        List<Node> result = new ArrayList<>();
        // See bbcode-syntax.h for the packed UTF-16 offset record format.
        for (int i = 0; i < packed.length;) {
            int start = packed[i++], contentStart = packed[i++], contentEnd = packed[i++], end = packed[i++];
            String name = source.substring(packed[i++], packed[i++]).toLowerCase(Locale.ROOT);
            String argument = source.substring(packed[i++], packed[i++]);
            int count = packed[i++];
            Map<String, String> attrs = new HashMap<>();
            for (int a = 0; a < count; a++) {
                String key = source.substring(packed[i++], packed[i++]).toLowerCase(Locale.ROOT);
                attrs.put(key, source.substring(packed[i++], packed[i++]));
            }
            result.add(new Node(name, argument, Collections.unmodifiableMap(attrs), start, contentStart, contentEnd, end));
        }
        return result;
    }

    /** The paragraph under the caret stays editable, including its delimiters. */
    public static int[] activeParagraph(String source, int selectionStart, int selectionEnd) {
        if (selectionStart < 0 || selectionEnd < 0) return new int[]{-1, -1};
        int from = Math.min(source.length(), Math.min(selectionStart, selectionEnd));
        int to = Math.min(source.length(), Math.max(selectionStart, selectionEnd));
        int start = from == 0 ? 0 : source.lastIndexOf('\n', from - 1) + 1;
        int end = source.indexOf('\n', to);
        return new int[]{start, end < 0 ? source.length() : end};
    }
}
