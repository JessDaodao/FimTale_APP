package com.fimtale.editor;

/** Authoring controls shared with ft-front's BBCodeEditorChrome and ft-go's BBCodeEditor. */
public enum EditorFormat {
    BOLD("粗体", "format-bold", 0, "b"), ITALIC("斜体", "format-italic", 0, "i"),
    UNDERLINE("下划线", "format-underline", 0, "u"), STRIKE("删除线", "format-strikethrough", 0, "s"),
    SUBSCRIPT("下标", "format-subscript", 0, "sub"), SUPERSCRIPT("上标", "format-superscript", 0, "sup"),
    LARGER("增大字号", "format-font-size-increase", 0, "size=larger"), SMALLER("减小字号", "format-font-size-decrease", 0, "size=smaller"),
    COLOR("文字颜色", "format-color-text", 0, null), BACKGROUND("文字背景", "format-color-highlight", 0, null),
    SPOILER("黑条", "eye-off-outline", 0, "spoiler"),
    H1("标题 1", "format-header-1", 1, "h1"), H2("标题 2", "format-header-2", 1, "h2"),
    H3("标题 3", "format-header-3", 1, "h3"), H4("标题 4", "format-header-4", 1, "h4"),
    H5("标题 5", "format-header-5", 1, "h5"), H6("标题 6", "format-header-6", 1, "h6"),
    LEFT("左对齐", "format-align-left", 1, "left"), CENTER("居中", "format-align-center", 1, "center"),
    RIGHT("右对齐", "format-align-right", 1, "right"), JUSTIFY("两端对齐", "format-align-justify", 1, "justify"),
    INDENT("首行缩进", "format-indent-increase", 1, "indent"), QUOTE("引用", "format-quote-close", 1, "quote"),
    BULLETS("无序列表", "format-list-bulleted", 1, null), NUMBERS("有序列表", "format-list-numbered", 1, null),
    LINK("链接", "link-variant", 2, null), IMAGE("图片链接", "image-outline", 2, null),
    MENTION("提及用户", "at", 2, null), HASH("话题标签", "pound", 2, null),
    TABLE("表格", "table", 2, null), COLLAPSE("折叠区", "unfold-less-horizontal", 2, null),
    CODE("代码块", "code-braces", 2, null), MARKDOWN("Markdown", "language-markdown-outline", 2, null),
    REFERENCE("引用卡片", "card-text-outline", 2, null), RULE("分隔线", "minus", 2, null);
    public final String label, icon, tag;
    public final int group;
    EditorFormat(String label, String icon, int group, String tag) { this.label = label; this.icon = icon; this.group = group; this.tag = tag; }
    public boolean immediate() { return tag != null || this == BULLETS || this == NUMBERS || this == CODE || this == MARKDOWN || this == RULE; }
    public BbCodeInsertion.Fragment fragment(String selected) {
        if (tag != null) return BbCodeInsertion.wrap(tag, selected, group == 1);
        if (this == BULLETS || this == NUMBERS) return BbCodeInsertion.list(selected, this == NUMBERS);
        if (this == CODE || this == MARKDOWN) return BbCodeInsertion.raw(this == CODE ? "code" : "markdown", selected);
        if (this == RULE) return BbCodeInsertion.atom("[hr]", true);
        throw new IllegalStateException("This format needs author input");
    }
}
