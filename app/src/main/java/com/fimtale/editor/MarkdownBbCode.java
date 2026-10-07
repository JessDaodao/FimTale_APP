package com.fimtale.editor;

import com.fimtale.utils.BbCode;
import java.util.Arrays;
import org.commonmark.node.*;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.parser.Parser;

/** A Markdown block becomes native rich text only when its visual contents are edited. */
final class MarkdownBbCode {
    private MarkdownBbCode() {}
    static String convert(String source) {
        Node root = Parser.builder().extensions(Arrays.asList(TablesExtension.create(), StrikethroughExtension.create())).build().parse(source);
        return children(root).replaceAll("\\n+$", "");
    }
    private static String children(Node parent) {
        StringBuilder out = new StringBuilder();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) out.append(render(node));
        return out.toString();
    }
    private static String render(Node node) {
        if (node instanceof Text) return BbCodeInsertion.literal(((Text) node).getLiteral());
        if (node instanceof SoftLineBreak || node instanceof HardLineBreak) return "\n";
        if (node instanceof Code) return "[font=monospace]" + BbCodeInsertion.literal(((Code) node).getLiteral()) + "[/font]";
        if (node instanceof FencedCodeBlock) return BbCodeInsertion.raw("code", ((FencedCodeBlock) node).getLiteral()).text + "\n";
        if (node instanceof IndentedCodeBlock) return BbCodeInsertion.raw("code", ((IndentedCodeBlock) node).getLiteral()).text + "\n";
        if (node instanceof HtmlInline) return BbCodeInsertion.literal(((HtmlInline) node).getLiteral());
        if (node instanceof HtmlBlock) return BbCodeInsertion.literal(((HtmlBlock) node).getLiteral());
        if (node instanceof ThematicBreak) return "[hr]\n";
        String body = children(node);
        if (node instanceof StrongEmphasis) return wrap("b", body);
        if (node instanceof Emphasis) return wrap("i", body);
        if (node instanceof Strikethrough) return wrap("s", body);
        if (node instanceof Heading) return wrap("h" + ((Heading) node).getLevel(), body) + "\n";
        if (node instanceof Paragraph) return body + (node.getNext() == null ? "" : "\n\n");
        if (node instanceof BlockQuote) return wrap("quote", body) + "\n";
        if (node instanceof BulletList) return wrap("list", "\n" + body) + "\n";
        if (node instanceof OrderedList) return "[list=1]\n" + body + "[/list]\n";
        if (node instanceof ListItem) return "[*]" + body + "\n";
        if (node instanceof Link) {
            String url = BbCode.safeUrl(((Link) node).getDestination(), false);
            return url == null ? body : BbCodeInsertion.link(url, body).text;
        }
        if (node instanceof Image) {
            String url = BbCode.safeUrl(((Image) node).getDestination(), true);
            return url == null ? body : "[img]" + BbCodeInsertion.literal(url) + "[/img]";
        }
        if (node instanceof TableBlock) return "[table]\n" + body + "[/table]\n";
        if (node instanceof TableRow) return "[tr]" + body + "[/tr]\n";
        if (node instanceof TableCell) {
            TableCell cell = (TableCell) node;
            String tag = cell.isHeader() ? "th" : "td";
            String align = cell.getAlignment() == null ? "" : " align=" + cell.getAlignment().name().toLowerCase(java.util.Locale.ROOT);
            return "[" + tag + align + "]" + body + "[/" + tag + "]";
        }
        return body;
    }
    private static String wrap(String tag, String body) { return "[" + tag + "]" + body + "[/" + tag + "]"; }
}
