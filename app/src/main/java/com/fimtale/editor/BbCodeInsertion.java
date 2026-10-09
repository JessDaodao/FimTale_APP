package com.fimtale.editor;

import com.fimtale.R;
import com.fimtale.utils.AppStrings;

import com.fimtale.utils.BbCode;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Builds one source edit with an explicit selection, shared by toolbar and insertion forms. */
public final class BbCodeInsertion {
    private BbCodeInsertion() {}
    public static final class Fragment {
        public final String text;
        public final int selectionStart, selectionEnd;
        public final boolean block;
        private Fragment(String text, int start, int end, boolean block) {
            this.text = text; selectionStart = start; selectionEnd = end; this.block = block;
        }
    }
    public static final class Edit {
        public final int start, end, selectionStart, selectionEnd;
        public final String replacement;
        private Edit(int start, int end, String replacement, int selectionStart, int selectionEnd) {
            this.start = start; this.end = end; this.replacement = replacement;
            this.selectionStart = selectionStart; this.selectionEnd = selectionEnd;
        }
    }
    public static Edit at(String source, int anchor, int focus, Fragment fragment) {
        int start = Math.min(source.length(), Math.max(0, Math.min(anchor, focus)));
        int end = Math.min(source.length(), Math.max(start, Math.max(anchor, focus)));
        String before = fragment.block && start > 0 && source.charAt(start - 1) != '\n' ? "\n" : "";
        String after = fragment.block && end < source.length() && source.charAt(end) != '\n' ? "\n" : "";
        return new Edit(start, end, before + fragment.text + after,
                start + before.length() + fragment.selectionStart, start + before.length() + fragment.selectionEnd);
    }
    public static Fragment wrap(String tag, String text, boolean block) {
        String name = tag.split("[ =]", 2)[0], open = "[" + tag + "]";
        return new Fragment(open + text + "[/" + name + "]", open.length(), open.length() + text.length(), block);
    }
    public static Fragment atom(String text, boolean block) { return new Fragment(text, text.length(), text.length(), block); }
    public static Fragment raw(String tag, String content) {
        Matcher closing = Pattern.compile("\\[/" + Pattern.quote(tag) + "\\]", Pattern.CASE_INSENSITIVE).matcher(content);
        StringBuffer escaped = new StringBuffer();
        while (closing.find()) closing.appendReplacement(escaped, Matcher.quoteReplacement(literal(closing.group())));
        closing.appendTail(escaped);
        return wrap(tag, escaped.toString(), true);
    }
    public static Fragment list(String selected, boolean ordered) {
        String[] lines = selected.split("\n", -1);
        StringBuilder items = new StringBuilder();
        for (String line : lines) items.append("[*]").append(line).append('\n');
        String open = ordered ? "[list=1]\n" : "[list]\n";
        String text = open + items + "[/list]";
        int first = open.length() + 3;
        return new Fragment(text, first, first + lines[0].length(), true);
    }
    public static Fragment table(int rows, int columns, boolean header, String selected) {
        if (rows < 1 || rows > 20 || columns < 1 || columns > 10) throw new IllegalArgumentException(AppStrings.get(R.string.editor_table_size_invalid));
        StringBuilder text = new StringBuilder("[table]\n"); int start = 0, end = 0;
        for (int r = 0; r < rows; r++) {
            text.append("[tr]");
            for (int c = 0; c < columns; c++) {
                String tag = header && r == 0 ? "th" : "td";
                text.append('[').append(tag).append(']');
                if (r == 0 && c == 0) { start = text.length(); text.append(selected); end = text.length(); }
                text.append("[/").append(tag).append(']');
            }
            text.append("[/tr]\n");
        }
        text.append("[/table]");
        return new Fragment(text.toString(), start, end, true);
    }
    public static Fragment link(String url, String label) {
        String href = validUrl(url, false);
        String text = label.isEmpty() ? literal(url.trim()) : label;
        return atom("[url=" + attribute(href) + "]" + text + "[/url]", false);
    }
    public static Fragment image(String url, String width, String height, String alt) {
        String src = validUrl(url, true), attrs = dimension("width", width) + dimension("height", height);
        if (!alt.trim().isEmpty()) attrs += " alt=" + attribute(alt.trim());
        return atom("[img" + attrs + "]" + literal(src) + "[/img]", true);
    }
    private static String dimension(String name, String value) {
        if (value.trim().isEmpty()) return "";
        try {
            int size = Integer.parseInt(value.trim());
            if (size > 0 && size <= 4096) return " " + name + "=" + size;
        } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException(AppStrings.get(R.string.editor_image_dimensions_invalid));
    }
    public static String validUrl(String input, boolean image) {
        String url = input.trim(), checked = BbCode.safeUrl(url, image);
        if (checked == null) throw new IllegalArgumentException(AppStrings.get(R.string.editor_url_invalid));
        return checked;
    }
    public static Fragment mention(int userId, String username) {
        if (userId <= 0 || username.trim().isEmpty()) throw new IllegalArgumentException(AppStrings.get(R.string.editor_user_not_found));
        return atom("[mention=" + userId + "]" + literal(username.trim()) + "[/mention]", false);
    }
    public static Fragment reference(int type, int id, String description) {
        if (id <= 0 || !(type == 1 || type == 3 || type == 4 || type == 5 || type == 7)) throw new IllegalArgumentException(AppStrings.get(R.string.editor_reference_id_invalid));
        return atom("[ref type=" + type + " id=" + id + "]" + literal(description) + "[/ref]", true);
    }
    public static String literal(String text) { return text.replace("[", "&#91;").replace("]", "&#93;"); }
    public static String attribute(String text) {
        String value = literal(text);
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) throw new IllegalArgumentException(AppStrings.get(R.string.editor_single_line_required));
        if (!value.contains("\"")) return "\"" + value + "\"";
        if (!value.contains("'")) return "'" + value + "'";
        throw new IllegalArgumentException(AppStrings.get(R.string.editor_quotes_invalid));
    }
}
