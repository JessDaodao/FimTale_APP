package com.fimtale.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Source offsets for complete BBCode pairs. Parsing never rewrites the author's text. */
public final class BbCodeSyntax {
    private static final Pattern TAG = Pattern.compile("\\[(/?)([a-z][a-z0-9-]*)(?:[= ]([^\\]\\r\\n]*))?\\]", Pattern.CASE_INSENSITIVE);
    private BbCodeSyntax() {}

    public static final class Node {
        public final String name, argument;
        public final int start, contentStart, contentEnd, end;
        private Node(String name, String argument, int start, int contentStart, int contentEnd, int end) {
            this.name = name; this.argument = argument;
            this.start = start; this.contentStart = contentStart; this.contentEnd = contentEnd; this.end = end;
        }
    }

    public static List<Node> parse(String source) {
        List<Node> result = new ArrayList<>(), stack = new ArrayList<>();
        Matcher matcher = TAG.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(2).toLowerCase(Locale.ROOT);
            boolean closing = !matcher.group(1).isEmpty();
            if (!stack.isEmpty()) {
                Node last = stack.get(stack.size() - 1);
                // These bodies are literal; apparent BBCode within them must not be styled.
                if (literal(last.name) && !(closing && last.name.equals(name))) continue;
            }
            if (!closing) {
                if (name.equals("br") || name.equals("hr")) continue;
                String argument = matcher.group(3) == null ? "" : matcher.group(3).trim();
                if (argument.length() >= 2 && ((argument.startsWith("\"") && argument.endsWith("\""))
                        || (argument.startsWith("'") && argument.endsWith("'")))) argument = argument.substring(1, argument.length() - 1);
                stack.add(new Node(name, argument, matcher.start(), matcher.end(), -1, -1));
            } else {
                for (int i = stack.size() - 1; i >= 0; i--) {
                    Node opening = stack.get(i);
                    if (!opening.name.equals(name)) continue;
                    // Discard unclosed inner tags rather than creating crossing spans.
                    stack.subList(i, stack.size()).clear();
                    result.add(new Node(name, opening.argument, opening.start, opening.contentStart, matcher.start(), matcher.end()));
                    break;
                }
            }
        }
        result.sort(java.util.Comparator.comparingInt(node -> node.start));
        return result;
    }

    private static boolean literal(String name) {
        return name.equals("code") || name.equals("markdown") || name.equals("img");
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
