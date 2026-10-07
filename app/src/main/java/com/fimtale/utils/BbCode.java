package com.fimtale.utils;

import com.fimtale.network.SiteUrls;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** FimTale's BBCode dialect. Only [markdown] opts into Markdown; all other text is literal. */
public final class BbCode {
    private static final Set<String> TAGS = new HashSet<>(Arrays.asList(
            "b", "i", "u", "s", "sub", "sup", "color", "bg-color", "size", "font", "url",
            "h1", "h2", "h3", "h4", "h5", "h6", "p", "quote", "left", "center", "right", "justify",
            "indent", "list", "*", "table", "tr", "th", "td", "img", "code", "markdown", "handbook",
            "collapse", "spoiler", "mention", "hash", "ref", "br", "hr"));
    private static final Set<String> RAW = new HashSet<>(Arrays.asList("code", "markdown", "img", "handbook"));
    private static final Pattern TOKEN = Pattern.compile("\\[(/?)([a-zA-Z][a-zA-Z0-9-]*|\\*)((?:[^\\]\"']++|\"[^\"]*+\"|'[^']*+')*+)\\]");
    private static final Pattern ATTR = Pattern.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s]+))");
    private static final Pattern EMOJI = Pattern.compile(":ftemoji_([a-zA-Z0-9_]+):");
    private static final List<Extension> MD_EXTENSIONS = Arrays.asList(TablesExtension.create(), StrikethroughExtension.create());
    private static final Parser MARKDOWN = Parser.builder().extensions(MD_EXTENSIONS).build();
    private static final HtmlRenderer MARKDOWN_HTML = HtmlRenderer.builder().extensions(MD_EXTENSIONS)
            .escapeHtml(true).softbreak("<br>").build();
    private BbCode() {}

    /** Returns safe HTML accepted by Markwon, retaining the existing public entry point. */
    public static String toMarkdown(String source) {
        if (source == null || source.isEmpty()) return "";
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        return "<div>" + render(parse(normalized), new Style()) + "</div>";
    }

    private static final class Tag {
        String name, open = "", raw, text, body = "";
        boolean closed;
        int bodyStart;
        Map<String, String> attrs = new HashMap<>();
        List<Tag> children = new ArrayList<>();
        Tag(String name) { this.name = name; }
    }

    private static void text(Tag parent, String value) {
        if (value.isEmpty()) return;
        Tag text = new Tag(""); text.text = value; parent.children.add(text);
    }

    private static Tag parse(String source) {
        Tag root = new Tag("root"); root.closed = true;
        List<Tag> stack = new ArrayList<>(); stack.add(root);
        Matcher matcher = TOKEN.matcher(source);
        int cursor = 0;
        while (matcher.find(cursor)) {
            Tag parent = stack.get(stack.size() - 1);
            text(parent, source.substring(cursor, matcher.start()));
            cursor = matcher.end();
            String name = matcher.group(2).toLowerCase(Locale.ROOT);
            boolean closing = !matcher.group(1).isEmpty();
            if (!TAGS.contains(name)) { text(parent, matcher.group()); continue; }
            if (closing) {
                int match = stack.size() - 1;
                while (match > 0 && !stack.get(match).name.equals(name)) match--;
                if (match == 0) { text(parent, matcher.group()); continue; }
                // Incomplete descendants keep their opening token visible; never consume following prose.
                for (int i = stack.size() - 1; i >= match; i--) {
                    Tag tag = stack.remove(i);
                    tag.closed = i == match || tag.name.equals("*");
                    if (tag.name.equals("collapse"))
                        tag.body = source.substring(tag.bodyStart, matcher.start());
                }
                continue;
            }
            if (name.equals("*")) {
                int list = stack.size() - 1;
                while (list > 0 && !stack.get(list).name.equals("list")) list--;
                if (list == 0) { text(parent, matcher.group()); continue; }
                while (stack.size() > list + 1) {
                    Tag tag = stack.remove(stack.size() - 1);
                    tag.closed = tag.name.equals("*");
                }
                parent = stack.get(list);
            }
            if (stack.size() >= 128) { text(parent, matcher.group()); continue; }
            Tag tag = new Tag(name); tag.open = matcher.group(); tag.bodyStart = cursor;
            String tail = matcher.group(3).trim();
            if (tail.startsWith("=")) tag.attrs.put("value", unquote(tail.substring(1).trim()));
            else {
                Matcher attrs = ATTR.matcher(tail);
                while (attrs.find()) tag.attrs.put(attrs.group(1).toLowerCase(Locale.ROOT),
                        attrs.group(2) != null ? attrs.group(2) : attrs.group(3) != null ? attrs.group(3) : attrs.group(4));
            }
            parent.children.add(tag);
            if (name.equals("br") || name.equals("hr")) { tag.closed = true; continue; }
            if (RAW.contains(name)) {
                Matcher close = Pattern.compile("\\[/" + name + "\\]", Pattern.CASE_INSENSITIVE).matcher(source);
                if (!close.find(cursor)) { text(tag, source.substring(cursor)); cursor = source.length(); break; }
                int end = close.start();
                tag.raw = source.substring(cursor, end); tag.closed = true;
                cursor = end + name.length() + 3;
            } else stack.add(tag);
        }
        text(stack.get(stack.size() - 1), source.substring(cursor));
        return root;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))))
            return s.substring(1, s.length() - 1);
        return s;
    }

    private static final class Style {
        String color = "", background = "", font = "";
        int size;
        Style() {}
        Style(Style other) { color = other.color; background = other.background; font = other.font; size = other.size; }
        String apply(String text) {
            String attrs = attribute("data-color", color) + attribute("data-background", background)
                    + attribute("data-font", font) + (size == 0 ? "" : attribute("data-size", String.valueOf(size)));
            return attrs.isEmpty() || text.isEmpty() ? text : "<span" + attrs + ">" + text + "</span>";
        }
    }

    private static String render(Tag tag, Style inherited) {
        if (tag.text != null) return inherited.apply(literal(tag.text));
        String name = tag.name, value = decode(tag.attrs.getOrDefault("value", ""));
        Style style = new Style(inherited);
        if (tag.closed) {
            if (name.equals("color") && safeColor(value)) style.color = value;
            if (name.equals("bg-color") && safeColor(value)) style.background = value;
            if (name.equals("size")) {
                if (value.equalsIgnoreCase("larger")) style.size = Math.min(8, style.size + 1);
                if (value.equalsIgnoreCase("smaller")) style.size = Math.max(-8, style.size - 1);
            }
            if (name.equals("font") && value.matches("[\\p{L} ,_-]{1,80}")) style.font = value;
            if (name.equals("collapse")) {
                String title = value.isEmpty() ? "点击展开" : value;
                String encoded = Base64.getEncoder().encodeToString(tag.body.getBytes(StandardCharsets.UTF_8));
                String control = "<span" + attribute("data-hidden", encoded) + attribute("data-title", title)
                        + ">" + escape(title) + "</span>";
                return "<p>" + control + "</p>";
            }
        }
        StringBuilder children = new StringBuilder();
        for (Tag child : tag.children) {
            if ((name.equals("list") || name.equals("table") || name.equals("tr"))
                    && child.text != null && child.text.trim().isEmpty()) continue;
            children.append(render(child, style));
        }
        String body = children.toString();
        if (!tag.closed && !name.equals("root")) return literal(tag.open) + body;
        if (name.matches("b|i|u|s|sub|sup|h[1-6]|p|table|tr|th|td")) {
            String attrs = "";
            if (name.equals("td") || name.equals("th")) {
                attrs = positiveAttr(tag, "colspan", 32) + positiveAttr(tag, "rowspan", 100)
                        + attribute("align", alignment(tag.attrs.get("align")));
                // Keep even an empty cell addressable by the native table renderer.
                if (body.isEmpty()) body = "&#160;";
            }
            return "<" + name + attrs + ">" + body + "</" + name + ">";
        }
        switch (name) {
            case "spoiler": return "<span data-spoiler=\"true\">" + body + "</span>";
            case "br": return "<br>";
            case "hr": return "<hr>";
            case "quote": return "<blockquote>" + (value.isEmpty() ? "" : "<b>" + literal(value) + "</b><br>") + body + "</blockquote>";
            case "left": case "right": case "center": case "justify":
                return "<div data-align=\"" + name + "\">" + body + "</div>";
            case "indent":
                String indent = value.isEmpty() ? "2em" : value;
                return "<div" + attribute("data-indent", indent.matches("(?:\\d{1,4}(?:\\.\\d{1,3})?)(?:em|rem|px|pt|%)") ? indent : "2em") + ">" + body + "</div>";
            case "list":
                String list = value.matches("[1aAiI]") ? "ol" : "ul";
                return "<" + list + ">" + body + "</" + list + ">";
            case "*": return "<li>" + body + "</li>";
            case "url": return link(safeUrl(value.isEmpty() ? plain(tag).trim() : value, false), body);
            case "mention":
                String user = plain(tag).trim().replaceFirst("^@", "");
                if (user.isEmpty()) {
                    for (Tag child : tag.children) {
                        Matcher legacyName = Pattern.compile("/user/@([^/?#]+)").matcher(child.attrs.getOrDefault("value", ""));
                        if (child.name.equals("url") && legacyName.find()) { user = legacyName.group(1); break; }
                    }
                }
                String uid = positive(value, Integer.MAX_VALUE);
                return link(SiteUrls.SITE + "/user/@" + encode(user) + (uid.isEmpty() ? "" : "?uid=" + uid), "@" + escape(user));
            case "hash":
                String hash = plain(tag).trim().replaceAll("^#+|#+$", "");
                String query = "hashtags:\"" + escapeLucene(hash) + "\"";
                return link(SiteUrls.SITE + "/search/work?filter=" + encode(query), "#" + escape(hash) + "#");
            case "ref":
                String id = positive(tag.attrs.get("id"), Integer.MAX_VALUE);
                String type = tag.attrs.getOrDefault("type", "");
                if (id.isEmpty() || !type.matches("[13457]")) return body;
                String label = plain(tag).trim();
                if (label.isEmpty()) label = (type.equals("1") ? "作品" : type.equals("3") ? "章节" : type.equals("4") ? "评论" : type.equals("5") ? "频道" : "用户") + " #" + id;
                return "<p><span data-ref=\"" + type + ":" + id + "\">" + escape(label) + "</span></p>";
            case "img":
                String src = safeUrl(decode(tag.raw).trim(), true);
                if (src == null) return "";
                return "<p><img" + attribute("src", src) + attribute("alt", decode(tag.attrs.getOrDefault("alt", "")))
                        + dimensionAttr(tag, "width") + dimensionAttr(tag, "height") + "></p>";
            case "code": return "<pre><code>" + escape(decode(tag.raw)).replace("\n", "&#10;") + "</code></pre>";
            case "markdown": return markdown(decode(tag.raw));
            case "handbook": return "<p>（此处为网站手册，App 暂不提供内嵌手册）</p>";
            default: return body;
        }
    }

    private static String markdown(String source) {
        Node doc = MARKDOWN.parse(source.replaceAll("(?i)<br\\s*/?>", "\n\n"));
        doc.accept(new AbstractVisitor() {
            @Override public void visit(Link link) {
                String url = safeUrl(link.getDestination(), false);
                visitChildren(link);
                if (url == null) unwrap(link); else link.setDestination(url);
            }
            @Override public void visit(Image img) {
                String url = safeUrl(img.getDestination(), true);
                if (url == null) unwrap(img); else img.setDestination(url);
            }
            private void unwrap(Node node) {
                while (node.getFirstChild() != null) node.insertBefore(node.getFirstChild());
                node.unlink();
            }
        });
        String html = MARKDOWN_HTML.render(doc);
        Matcher code = Pattern.compile("(?s)<pre>.*?</pre>").matcher(html);
        StringBuffer result = new StringBuffer();
        while (code.find()) code.appendReplacement(result, Matcher.quoteReplacement(code.group().replace("\n", "&#10;")));
        code.appendTail(result);
        return result.toString().replace("\n", "");
    }

    private static String plain(Tag tag) {
        if (tag.text != null) return decode(tag.text);
        if (tag.raw != null) return decode(tag.raw);
        StringBuilder out = new StringBuilder();
        for (Tag child : tag.children) out.append(plain(child));
        return out.toString();
    }
    private static String literal(String value) {
        String decoded = decode(value);
        Matcher emoji = EMOJI.matcher(decoded);
        StringBuilder out = new StringBuilder(); int end = 0;
        while (emoji.find()) {
            out.append(escapeLines(decoded.substring(end, emoji.start())));
            out.append("<img data-emoji=\"true\" width=\"1.2em\" height=\"1.2em\"")
                    .append(attribute("src", SiteUrls.SITE + "/img/ftemoji/" + emoji.group(1) + ".png"))
                    .append(attribute("alt", emoji.group())).append(">");
            end = emoji.end();
        }
        return out.append(escapeLines(decoded.substring(end))).toString();
    }
    private static String escapeLines(String s) { return escape(s).replace("\n", "<br>").replace("  ", " &#160;"); }
    private static String decode(String s) {
        return s == null ? "" : s.replace("&#91;", "[").replace("&#93;", "]");
    }
    static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    private static String attribute(String key, String value) { return value == null || value.isEmpty() ? "" : " " + key + "=\"" + escape(value) + "\""; }
    private static String link(String url, String body) { return url == null ? body : "<a" + attribute("href", url) + ">" + body + "</a>"; }
    private static String encode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String escapeLucene(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ("+-!(){}[]^\"~*?:\\/".indexOf(c) >= 0) out.append('\\');
            else if ((c == '&' || c == '|') && i + 1 < value.length() && value.charAt(i + 1) == c) {
                out.append('\\').append(c).append(c); i++; continue;
            }
            out.append(c);
        }
        return out.toString();
    }
    private static String alignment(String s) { return s != null && s.matches("left|center|right|justify") ? s : ""; }
    private static String positive(String value, int max) {
        try { int number = Integer.parseInt(value); return number > 0 ? String.valueOf(Math.min(max, number)) : ""; }
        catch (RuntimeException e) { return ""; }
    }
    private static String positiveAttr(Tag tag, String attr, int max) { return attribute(attr, positive(tag.attrs.get(attr), max)); }
    private static String dimensionAttr(Tag tag, String attr) {
        String value = tag.attrs.getOrDefault(attr, "");
        return attribute(attr, value.matches("\\d{1,4}(?:\\.\\d{1,2})?(?:px|%|em)?") ? value : "");
    }
    static boolean safeColor(String value) {
        return value.matches("#[a-fA-F0-9]{3,8}|[a-zA-Z]{1,24}|(?:rgb|hsl)a?\\([0-9.,% +\\-]+\\)");
    }
    public static String safeUrl(String value, boolean image) {
        if (value == null) return null;
        String url = value.trim();
        if (url.isEmpty() || url.matches("(?s).*[\\x00-\\x20\\x7f\\\\].*") || url.startsWith("//")) return null;
        if (image && url.matches("(?i)data:image/(?:png|jpe?g|gif|webp);base64,[a-z0-9+/]+={0,2}")) return url;
        try {
            URI uri = URI.create(url);
            if (url.startsWith("/") || (!image && url.startsWith("#"))) uri = URI.create(SiteUrls.SITE + "/").resolve(uri);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) return null;
            return uri.toASCIIString();
        } catch (IllegalArgumentException e) { return null; }
    }
}
