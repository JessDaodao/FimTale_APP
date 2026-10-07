package com.fimtale.editor;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real host JNI tests; no Java parser or Android device is used. */
public final class BbCodeNativeTest {
    private MessageDigest digest;

    @Test public void syntaxMatchesEstablishedCompatibilitySnapshot() throws Exception {
        digest = MessageDigest.getInstance("SHA-256");
        int checked = 0;
        List<String> cases = new ArrayList<>(Arrays.asList(
                "", "plain 中文🐴\r\ntext", "前言🐴[b]粗体[i]斜体[/i][/b]结尾",
                "\u0000\ud800[b]\udfff\u0000🐴[/b]\r\n[i]原文[/i]",
                "[b]one[i]two[/b]three[/i]", "[b]still typing [i", "[/b]plain", "[br][hr][b]bold[/b]",
                "&#91;b&#93;字面量&#91;/b&#93;[custom=x]保留[/custom]",
                "[list=1][*]one[list][*]inner[/list][*]two[/list]",
                "[LIST][*][b]one[*]two[/LIST]", "[*]outside[/list]", "[list][*]unfinished",
                "[img alt=\"a ] b\" width='120']/a.png[/img]",
                "[COLOR='#f00']红[/COLOR][bg-color=blue]背景[/bg-color]",
                "[x A=first a=last empty=\"\" _key='a b' bad == ok=one]text[/x]",
                "[x a=unquoted'quote' k=v=tail]body[/x]", "[x a = \"v\"b = 'w']body[/x]",
                "[x a='x\"y' b=\"x'y\"]body[/x]", "[x\u0000a=1\u0000 b=2]text[/x]",
                "[x a\u00a0=1 b\t=\r\n' two ' c=three]text[/x]",
                "[x a='broken b=x\"still]end[b]valid[/b]",
                "[x=   '  保留  '   ]text[/x]", "[x=\u0000value\u0000]text[/x]",
                "[x a=\"quoted [b] literal\" ]z[/x]", "[[[[x]body[/x]",
                "[b]text[/b extra=ignored]", "[İ]not ASCII[/İ][a_b]tail[/a_b]"));
        for (String raw : new String[]{"code", "markdown", "img", "handbook"}) {
            cases.add("İ[" + raw.toUpperCase(java.util.Locale.ROOT) + "]İ[b]literal[/b][/'ignored']"
                    + "[/" + raw + "][i]styled[/i]");
            cases.add("[" + raw + "][b]unfinished[/b]");
            cases.add("[" + raw + "]raw[/" + raw + " extra]still raw");
        }
        try (InputStreamReader input = new InputStreamReader(getClass().getResourceAsStream("/bbcode-corpus.fixture.json"), StandardCharsets.UTF_8)) {
            cases.addAll(Arrays.asList(new Gson().fromJson(input, String[].class)));
        }
        compare(null);
        for (String source : cases) {
            compare(source); checked++;
            // Every keystroke prefix exercises partially typed tags and quoted attributes.
            for (int end = 0; end < source.length(); end++) { compare(source.substring(0, end)); checked++; }
            for (int at = 0; at < source.length(); at += 3) {
                compare(source.substring(0, at) + source.substring(at + 1)); checked++;
            }
        }

        Random random = new Random(0xF17A1E);
        String[] atoms = {"[b]", "[/b]", "[i]", "[/i]", "[x a=\"a ] b\"]", "[/x]", "[code]", "[/CODE]",
                "[list]", "[*]", "[/list]", "[br]", "[img x=y]", "[/img]", "[markdown]", "[/markdown]",
                "[custom=abc]", "[/custom]", "[", "]", "'", "\"", "\n", "\r\n", "\t", "\u0000", "中文🐴"};
        for (int test = 0; test < 5000; test++) {
            StringBuilder source = new StringBuilder();
            int length = random.nextInt(60);
            for (int i = 0; i < length; i++) source.append(atoms[random.nextInt(atoms.length)]);
            compare(source.toString()); checked++;
        }
        String alphabet = "[]/'\"= abcXYZ09_-\t\n\r\u0000\u00a0中文🐴";
        for (int test = 0; test < 10000; test++) {
            StringBuilder source = new StringBuilder();
            int length = random.nextInt(200);
            for (int i = 0; i < length; i++) source.append(alphabet.charAt(random.nextInt(alphabet.length())));
            compare(source.toString()); checked++;
        }
        compare("[url=" + "a".repeat(20000) + "]body[/url]");
        compare("[b]".repeat(1000) + "body" + "[/b]".repeat(1000));
        compare("[x " + "a='b' ".repeat(20000) + "]body[/x]");
        compare("[br]".repeat(20000));
        // Recorded from the Java implementation before its removal. Includes
        // every UTF-16 source unit, node field and sorted attribute entry.
        StringBuilder actual = new StringBuilder();
        for (byte value : digest.digest()) actual.append(String.format(java.util.Locale.ROOT, "%02x", value));
        assertEquals("b655f50a55d75ecd53a8c39e7694daefc5ffec8dd208ddc2d55c634d746db3a3", actual.toString());
        assertEquals(27475, checked);
        assertTrue(BbCodeSyntax.parse("[a'".repeat(100000)).isEmpty());
    }

    @Test public void concurrentCallsHaveIndependentParserState() throws Exception {
        String source = "前言🐴[list][*][b]粗体[/b][*][img alt='a ] b']/a.png[/img][/list]\r\n[code][b]raw[/b][/code]";
        byte[] expected = snapshot(source);
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) jobs.add(() -> {
                for (int round = 0; round < 200; round++) assertArrayEquals(expected, snapshot(source));
                return null;
            });
            for (var job : executor.invokeAll(jobs)) job.get();
        } finally { executor.shutdownNow(); }
    }

    private void compare(String source) throws IOException {
        digest.update(snapshot(source));
    }

    private static byte[] snapshot(String source) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        text(out, source);
        List<BbCodeSyntax.Node> nodes = BbCodeSyntax.parse(source);
        out.writeInt(nodes.size());
        for (BbCodeSyntax.Node node : nodes) {
            text(out, node.name); text(out, node.argument);
            out.writeInt(node.start); out.writeInt(node.contentStart);
            out.writeInt(node.contentEnd); out.writeInt(node.end);
            out.writeInt(node.attributes.size());
            for (var attr : new TreeMap<>(node.attributes).entrySet()) {
                text(out, attr.getKey()); text(out, attr.getValue());
            }
        }
        return bytes.toByteArray();
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        out.writeInt(value == null ? -1 : value.length());
        if (value != null) out.writeChars(value);
    }
}
