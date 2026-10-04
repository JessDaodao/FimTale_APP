package com.app.fimtale.utils;

import com.app.fimtale.network.SiteUrls;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts the site's BBCode to the Markdown/HTML supported by the native reader. */
public final class BbCode {
    private BbCode() {}
    public static String toMarkdown(String source) {
        if (source == null || source.isEmpty()) return "";
        List<String> literal = new ArrayList<>();
        Matcher blocks = Pattern.compile("(?is)\\[(markdown|code)(?:=[^\\]]*)?\\](.*?)\\[/\\1\\]").matcher(source);
        StringBuffer protectedText = new StringBuffer();
        while (blocks.find()) {
            String text = blocks.group(2);
            if (blocks.group(1).equalsIgnoreCase("code")) {
                String fence = "```";
                while (text.contains(fence)) fence += "`";
                text = "\n\n" + fence + "\n" + text + "\n" + fence + "\n\n";
            }
            String marker = "\u0000FT_BLOCK_" + literal.size() + "\u0000";
            literal.add(text);
            blocks.appendReplacement(protectedText, Matcher.quoteReplacement(marker));
        }
        blocks.appendTail(protectedText);
        String text = protectedText.toString().replace("\r\n", "\n");
        // BBCode text is literal; only [markdown] blocks opt into Markdown syntax.
        text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        text = replaceImages(text);
        text = replaceFtemoji(text);
        text = text.replaceAll("(?is)\\[url=([^\\]]+)\\](.*?)\\[/url\\]", "[$2]($1)")
                .replaceAll("(?is)\\[url\\](.*?)\\[/url\\]", "[$1]($1)");
        text = text.replaceAll("(?i)\\[b\\]", "<b>").replaceAll("(?i)\\[/b\\]", "</b>")
                .replaceAll("(?i)\\[i\\]", "<i>").replaceAll("(?i)\\[/i\\]", "</i>")
                .replaceAll("(?i)\\[u\\]", "<u>").replaceAll("(?i)\\[/u\\]", "</u>")
                .replaceAll("(?i)\\[s\\]", "<s>").replaceAll("(?i)\\[/s\\]", "</s>");
        for (int i = 1; i <= 6; i++) {
            text = text.replaceAll("(?i)\\[h" + i + "\\]", "\n\n" + "#".repeat(i) + " ")
                    .replaceAll("(?i)\\[/h" + i + "\\]", "\n\n");
        }
        text = text.replaceAll("(?i)\\[hr/?\\]", "\n\n---\n\n")
                .replaceAll("(?i)\\[br/?\\]", "\n")
                .replaceAll("(?i)\\[/?(?:p|left|right|center|justify|indent)(?:[ =][^\\]]*)?\\]", "\n\n")
                .replaceAll("(?i)\\[/?list(?:=[^\\]]*)?\\]", "\n\n")
                .replaceAll("\\[\\*\\]", "\n- ")
                .replaceAll("(?i)\\[/\\*\\]", "\n")
                .replaceAll("(?i)\\[quote(?:=[^\\]]*)?\\]", "\n\n> ")
                .replaceAll("(?i)\\[/quote\\]", "\n\n")
                .replaceAll("(?i)\\[/?(?:color|size|font|sub|sup|mention)(?:[ =][^\\]]*)?\\]", "")
                .replaceAll("(?i)\\[collapse(?:[ =][^\\]]*)?\\]", "\n\n")
                .replaceAll("(?i)\\[/collapse\\]", "\n\n");
        // Keep spoiler text behind the same black bar used by the website.
        text = text.replaceAll("(?is)\\[spoiler(?:[ =][^\\]]*)?\\](.*?)\\[/spoiler\\]",
                "<span style=\"background-color:#000000;color:transparent;padding:0 2px\">&nbsp;&nbsp;&nbsp;&nbsp;</span>");
        for (int i = 0; i < literal.size(); i++) text = text.replace("\u0000FT_BLOCK_" + i + "\u0000", literal.get(i));
        return text.trim();
    }
    private static String replaceImages(String source) {
        Matcher images = Pattern.compile("(?is)\\[img(?:[ =][^\\]]*)?\\](.*?)\\[/img\\]").matcher(source);
        StringBuffer result = new StringBuffer();
        while (images.find()) {
            String url = SiteUrls.media(images.group(1).trim().replace("&amp;", "&"));
            String replacement = url == null ? "" : "\n\n![](" + url.replace("(", "%28").replace(")", "%29") + ")\n\n";
            images.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        images.appendTail(result);
        return result.toString();
    }

    private static String replaceFtemoji(String source) {
        Matcher emojis = Pattern.compile(":ftemoji_([a-zA-Z0-9_]+):").matcher(source);
        StringBuffer result = new StringBuffer();
        while (emojis.find()) {
            String name = emojis.group(1);
            String url = SiteUrls.media("/img/ftemoji/" + name + ".png");
            String replacement = url == null ? emojis.group() : "![ftemoji_" + name + "](" + url + ")";
            emojis.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        emojis.appendTail(result);
        return result.toString();
    }
}
