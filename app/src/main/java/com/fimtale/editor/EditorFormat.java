package com.fimtale.editor;

import com.fimtale.utils.AppStrings;

import com.fimtale.R;

/** Authoring controls shared with ft-front's BBCodeEditorChrome and ft-go's BBCodeEditor. */
public enum EditorFormat {
    BOLD(R.string.editor_tool_bold, "format-bold", 0, "b"), ITALIC(R.string.editor_tool_italic, "format-italic", 0, "i"),
    UNDERLINE(R.string.editor_format_underline, "format-underline", 0, "u"), STRIKE(R.string.editor_format_strike, "format-strikethrough", 0, "s"),
    SUBSCRIPT(R.string.editor_format_subscript, "format-subscript", 0, "sub"), SUPERSCRIPT(R.string.editor_format_superscript, "format-superscript", 0, "sup"),
    LARGER(R.string.editor_format_larger, "format-font-size-increase", 0, "size=larger"), SMALLER(R.string.editor_format_smaller, "format-font-size-decrease", 0, "size=smaller"),
    COLOR(R.string.editor_format_color, "format-color-text", 0, null), BACKGROUND(R.string.editor_format_background, "format-color-highlight", 0, null),
    SPOILER(R.string.editor_format_spoiler, "eye-off-outline", 0, "spoiler"),
    H1(R.string.editor_format_heading_1, "format-header-1", 1, "h1"), H2(R.string.editor_format_heading_2, "format-header-2", 1, "h2"),
    H3(R.string.editor_format_heading_3, "format-header-3", 1, "h3"), H4(R.string.editor_format_heading_4, "format-header-4", 1, "h4"),
    H5(R.string.editor_format_heading_5, "format-header-5", 1, "h5"), H6(R.string.editor_format_heading_6, "format-header-6", 1, "h6"),
    LEFT(R.string.editor_format_left, "format-align-left", 1, "left"), CENTER(R.string.editor_format_center, "format-align-center", 1, "center"),
    RIGHT(R.string.editor_format_right, "format-align-right", 1, "right"), JUSTIFY(R.string.editor_format_justify, "format-align-justify", 1, "justify"),
    INDENT(R.string.editor_format_indent, "format-indent-increase", 1, "indent"), QUOTE(R.string.editor_format_quote, "format-quote-close", 1, "quote"),
    BULLETS(R.string.editor_format_bullets, "format-list-bulleted", 1, null), NUMBERS(R.string.editor_format_numbers, "format-list-numbered", 1, null),
    LINK(R.string.editor_format_link, "link-variant", 2, null), IMAGE(R.string.editor_format_image_link, "image-outline", 2, null),
    MENTION(R.string.editor_format_mention, "at", 2, null), HASH(R.string.editor_format_hash, "pound", 2, null),
    TABLE(R.string.editor_format_table, "table", 2, null), COLLAPSE(R.string.editor_format_collapse, "unfold-less-horizontal", 2, null),
    CODE(R.string.editor_format_code, "code-braces", 2, null), MARKDOWN(R.string.editor_format_markdown, "language-markdown-outline", 2, null),
    REFERENCE(R.string.editor_format_reference, "card-text-outline", 2, null), RULE(R.string.editor_format_rule, "minus", 2, null);
    @androidx.annotation.StringRes public final int label;
    public final String icon, tag;
    public final int group;
    EditorFormat(@androidx.annotation.StringRes int label, String icon, int group, String tag) { this.label = label; this.icon = icon; this.group = group; this.tag = tag; }
    public boolean immediate() { return tag != null || this == BULLETS || this == NUMBERS || this == CODE || this == MARKDOWN || this == RULE; }
    public BbCodeInsertion.Fragment fragment(String selected) {
        if (tag != null) return BbCodeInsertion.wrap(tag, selected, group == 1);
        if (this == BULLETS || this == NUMBERS) return BbCodeInsertion.list(selected, this == NUMBERS);
        if (this == CODE || this == MARKDOWN) return BbCodeInsertion.raw(this == CODE ? "code" : "markdown", selected);
        if (this == RULE) return BbCodeInsertion.atom("[hr]", true);
        throw new IllegalStateException(AppStrings.get(R.string.editor_format_input_required));
    }
}
