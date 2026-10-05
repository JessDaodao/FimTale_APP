package com.fimtale.editor;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Source offsets for BBCode. Unlike rendering, parsing never normalizes the author's text. */
public final class BbCodeSyntax {
    private static final Pattern TAG = Pattern.compile("\\[(/?)([a-zA-Z][a-zA-Z0-9-]*|\\*)((?:[^\\]\"']++|\"[^\"]*+\"|'[^']*+')*+)\\]");
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s]+))");
    private BbCodeSyntax() {}

    public static final class Node {
        public final String name, argument;
        public final Map<String, String> attributes;
        public final int start, contentStart, contentEnd, end;
        private Node(String name, String argument, Map<String, String> attributes, int start, int contentStart, int contentEnd, int end) {
            this.name = name; this.argument = argument; this.attributes = attributes;
            this.start = start; this.contentStart = contentStart; this.contentEnd = contentEnd; this.end = end;
        }
        private Node close(int contentEnd, int end) { return new Node(name, argument, attributes, start, contentStart, contentEnd, end); }
    }

    public static List<Node> parse(String source) {
        List<Node> result = new ArrayList<>(), stack = new ArrayList<>();
        if (source == null || source.isEmpty()) return result;
        Matcher matcher = TAG.matcher(source);
        int cursor = 0;
        while (matcher.find(cursor)) {
            cursor = matcher.end();
            String name = matcher.group(2).toLowerCase(Locale.ROOT);
            boolean closing = !matcher.group(1).isEmpty();
            if (!closing) {
                if (name.equals("*")) {
                    int list = stack.size() - 1;
                    while (list >= 0 && !stack.get(list).name.equals("list")) list--;
                    if (list < 0) continue;
                    while (stack.size() > list + 1) {
                        Node previous = stack.remove(stack.size() - 1);
                        if (previous.name.equals("*")) result.add(previous.close(matcher.start(), matcher.start()));
                    }
                }
                if (stack.size() >= 128) continue;
                String tail = matcher.group(3).trim(), argument = "";
                Map<String, String> attrs = new HashMap<>();
                if (tail.startsWith("=")) argument = unquote(tail.substring(1).trim());
                else {
                    Matcher attr = ATTRIBUTE.matcher(tail);
                    while (attr.find()) attrs.put(attr.group(1).toLowerCase(Locale.ROOT),
                            attr.group(2) != null ? attr.group(2) : attr.group(3) != null ? attr.group(3) : attr.group(4));
                }
                Node node = new Node(name, argument, Collections.unmodifiableMap(attrs), matcher.start(), cursor, -1, -1);
                if (name.equals("br") || name.equals("hr")) { result.add(node.close(cursor, cursor)); continue; }
                if (literal(name)) {
                    Matcher end = Pattern.compile("\\[/" + name + "\\]", Pattern.CASE_INSENSITIVE).matcher(source);
                    // While a raw block is unfinished, its contents still must not become BBCode.
                    if (!end.find(cursor)) break;
                    result.add(node.close(end.start(), end.end())); cursor = end.end();
                } else stack.add(node);
            } else {
                for (int i = stack.size() - 1; i >= 0; i--) {
                    Node opening = stack.get(i);
                    if (!opening.name.equals(name)) continue;
                    for (int inner = stack.size() - 1; inner > i; inner--) {
                        Node item = stack.get(inner);
                        if (item.name.equals("*")) result.add(item.close(matcher.start(), matcher.start()));
                    }
                    stack.subList(i, stack.size()).clear();
                    result.add(opening.close(matcher.start(), matcher.end()));
                    break;
                }
            }
        }
        result.sort(Comparator.comparingInt(node -> node.start));
        return result;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))))
            return value.substring(1, value.length() - 1);
        return value;
    }
    private static boolean literal(String name) {
        return name.equals("code") || name.equals("markdown") || name.equals("img") || name.equals("handbook");
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
