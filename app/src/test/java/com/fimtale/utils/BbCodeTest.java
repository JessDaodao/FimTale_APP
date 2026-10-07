package com.fimtale.utils;

import com.fimtale.network.SiteUrls;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class BbCodeTest {
    @Test public void onlyMarkdownBlocksInterpretMarkdownOrHtml() {
        String html = BbCode.toMarkdown("# literal *stars* _underscores_ <b>x</b> &#91;b&#93;literal&#91;/b&#93;\n[markdown]**bold** [b]literal[/b]\n\n<script>alert(1)</script>[/markdown]");
        assertTrue(html.contains("# literal *stars* _underscores_ &lt;b&gt;x&lt;/b&gt; [b]literal[/b]"));
        assertTrue(html.contains("<strong>bold</strong> [b]literal[/b]"));
        assertFalse(html.contains("<script>"));
    }
    @Test public void nestedSameTypeFormattingRestoresOuterStyle() {
        assertEquals("<div><b>a<b>b</b>c</b></div>", BbCode.toMarkdown("[b]a[b]b[/b]c[/b]"));
        String html = BbCode.toMarkdown("[color=teal]a[url=https://example.com][color=navy]b[/color][/url]c[/color]");
        assertTrue(html.contains("<span data-color=\"teal\">a</span>"));
        assertTrue(html.contains("<span data-color=\"navy\">b</span>"));
        assertTrue(html.contains("<span data-color=\"teal\">c</span>"));
    }
    @Test public void paragraphsQuotesAndListsKeepStructure() {
        String html = BbCode.toMarkdown("[quote]a\n\n[quote]b[/quote]c[/quote]\n[list=1][*]one[list][*]inner[/list][*]two[/list]");
        assertTrue(html.contains("<blockquote>a<br><br><blockquote>b</blockquote>c</blockquote>"));
        assertTrue(html.contains("<ol><li>one<ul><li>inner</li></ul></li><li>two</li></ol>"));
        assertEquals("<div>A<br><br><br>B</div>", BbCode.toMarkdown("A\n\n\nB"));
    }
    @Test public void codeProtectsTagsImagesAndWhitespace() {
        String html = BbCode.toMarkdown("[code=java]  [img]https://x.test/a.png[/img]\n\t[b]x[/b] **y**[/code]");
        assertTrue(html.contains("<pre><code>  [img]https://x.test/a.png[/img]&#10;\t[b]x[/b] **y**</code></pre>"));
        assertFalse(html.contains("<img"));
        assertFalse(html.contains("<b>"));
        assertTrue(BbCode.toMarkdown("İ[CODE]İ字面[/CODE]after").contains("<pre><code>İ字面</code></pre>after"));
    }
    @Test public void imageAttributesAndEmojiAreSafe() {
        String html = BbCode.toMarkdown("[img width='120' height=80 alt=\"a > b\"]https://x.test/a(b).png?q=1&x=2[/img] hi :ftemoji_twilightsmile: !");
        assertTrue(html.contains("width=\"120\" height=\"80\""));
        assertTrue(html.contains("alt=\"a &gt; b\""));
        assertTrue(html.contains("a(b).png?q=1&amp;x=2"));
        assertTrue(html.contains("data-emoji=\"true\" width=\"1.2em\""));
    }
    @Test public void spoilersKeepInlineTextAndNestedFormatting() {
        assertEquals("<div>前<span data-spoiler=\"true\"><b>秘密</b></span>后</div>",
                BbCode.toMarkdown("前[spoiler][b]秘密[/b][/spoiler]后"));
        assertTrue(BbCode.toMarkdown("[spoiler]unfinished").contains("[spoiler]unfinished"));
        assertFalse(BbCode.toMarkdown("[spoiler][/spoiler]").contains("data-hidden"));
    }
    @Test public void hiddenBlocksRetainSourceWithoutLoadingImages() {
        String raw = "[b]秘密[/b][img]https://x.test/secret.png[/img]";
        String html = BbCode.toMarkdown("[collapse=说明][spoiler]" + raw + "[/spoiler][/collapse]");
        assertFalse(html.contains("秘密"));
        assertFalse(html.contains("<img"));
        Matcher encoded = Pattern.compile("data-hidden=\"([^\"]*)\"").matcher(html);
        assertTrue(encoded.find());
        assertEquals("[spoiler]" + raw + "[/spoiler]", new String(Base64.getDecoder().decode(encoded.group(1)), StandardCharsets.UTF_8));
        assertTrue(html.contains("说明</span>"));
    }
    @Test public void styleAttributesCannotInjectHtmlOrCss() {
        String html = BbCode.toMarkdown("[color=red;background:url(x)]a[/color][size=9999]b[/size][bg-color=orange]c[/bg-color][indent=21.0pt]d[/indent]");
        assertFalse(html.contains("background:url"));
        assertFalse(html.contains("data-size"));
        assertTrue(html.contains("data-background=\"orange\""));
        assertTrue(html.contains("data-indent=\"21.0pt\""));
        html = BbCode.toMarkdown("[size=larger]".repeat(20) + "large" + "[/size]".repeat(20));
        assertTrue(html.contains("data-size=\"8\""));
    }
    @Test public void urlsAreValidatedInBbcodeAndMarkdown() {
        for (String url : new String[]{"javascript:alert(1)", "data:text/html;base64,QUJD", "data:image/svg+xml;base64,QUJD", "//evil.test/a", "https://user:pass@x.test/a", "file:///etc/passwd", "https://x.test/\\evil"}) {
            assertNull(url, BbCode.safeUrl(url, true));
            String html = BbCode.toMarkdown("[url=" + url + "]label[/url][img]" + url + "[/img]");
            assertFalse(url, html.contains("href=")); assertFalse(url, html.contains("src="));
        }
        assertEquals(SiteUrls.SITE + "/img/a.png", BbCode.safeUrl("/img/a.png", true));
        assertEquals("data:image/png;base64,QUJD", BbCode.safeUrl("data:image/png;base64,QUJD", true));
        String html = BbCode.toMarkdown("[markdown][click](javascript:evil) ![bad](data:text/html;base64,QUJD)[/markdown]");
        assertFalse(html.contains("href=")); assertFalse(html.contains("src=")); assertTrue(html.contains("click"));
    }
    @Test public void mentionsHashtagsAndReferencesUseCurrentEntities() {
        String html = BbCode.toMarkdown("[mention=7]@seven[/mention][hash]火星[/hash][ref type=3 id=42]chapter[/ref]");
        assertTrue(html.contains("/user/@seven?uid=7"));
        assertTrue(html.contains("/search/work?filter=hashtags%3A%22%E7%81%AB%E6%98%9F%22"));
        assertTrue(html.contains("data-ref=\"3:42\""));
        assertFalse(html.contains("href=\"/chapter/"));
    }
    @Test public void tablesPreserveCellsAndMarkdownTablesRender() {
        String html = BbCode.toMarkdown("[quote][table][tr][th colspan=2 align=center]Name[/th][/tr][tr][td rowspan=2]Alice[/td][td]100[/td][/tr][tr][td]101[/td][/tr][/table][/quote]");
        assertTrue(html.contains("<th colspan=\"2\" align=\"center\">Name</th>"));
        assertTrue(html.contains("<td rowspan=\"2\">Alice</td>"));
        html = BbCode.toMarkdown("[markdown]| A | B |\n| --- | --- |\n| ~~x~~ | `y` |[/markdown]");
        assertTrue(html.contains("<table>")); assertTrue(html.contains("<del>x</del>")); assertTrue(html.contains("<code>y</code>"));
    }
    @Test public void malformedAndUnknownTagsDoNotDiscardText() {
        assertTrue(BbCode.toMarkdown("[unknown]text[/unknown]").contains("[unknown]text[/unknown]"));
        assertTrue(BbCode.toMarkdown("[b]unfinished").contains("[b]unfinished"));
        assertTrue(BbCode.toMarkdown("[code]unfinished").contains("[code]unfinished"));
        String html = BbCode.toMarkdown("[b][i]cross[/b]tail[/i]");
        assertTrue(html.contains("cross")); assertTrue(html.contains("tail[/i]"));
    }
    @Test public void realFrontendCorpusAndDeepInputsRender() throws Exception {
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/bbcode-corpus.fixture.json"), StandardCharsets.UTF_8)) {
            String[] corpus = new Gson().fromJson(reader, String[].class);
            assertEquals(20, corpus.length);
            for (String source : corpus) assertTrue(BbCode.toMarkdown(source).startsWith("<div>"));
        }
        String deep = BbCode.toMarkdown("[b]".repeat(1000) + "body" + "[/b]".repeat(1000));
        assertTrue(deep.contains("body"));
        assertTrue(BbCode.toMarkdown("[url=" + "a".repeat(20000) + "]body[/url]").contains("body"));
    }
}
