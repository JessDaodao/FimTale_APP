package com.fimtale.editor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Standalone host JVM test: fails if JNI is unavailable; needs no Android device. */
public final class BbCodeNativeTest {
    private static volatile int sink;

    public static void main(String[] args) throws Exception {
        if (!BbCodeSyntax.nativeAvailable()) throw new AssertionError("The native library must be loaded");
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
        if (args.length > 0) {
            for (String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.US_ASCII))
                cases.add(new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8));
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
        if (!BbCodeSyntax.parseNative("[a'".repeat(100000)).isEmpty())
            throw new AssertionError("Malformed input must not produce nodes");

        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) jobs.add(() -> {
                for (int round = 0; round < 20; round++) for (String source : cases) compare(source);
                return null;
            });
            for (var job : executor.invokeAll(jobs)) job.get();
        } finally { executor.shutdownNow(); }
        System.out.println("Native JNI parity passed: " + checked + " cases, stress inputs and 4 concurrent workers");
        if (Arrays.asList(args).contains("--benchmark")) benchmark();
    }

    private static void compare(String source) {
        List<BbCodeSyntax.Node> expected = BbCodeSyntax.parseJava(source);
        List<BbCodeSyntax.Node> actual = BbCodeSyntax.parseNative(source);
        if (expected.size() != actual.size()) fail(source, "node count: " + expected.size() + " != " + actual.size());
        for (int i = 0; i < expected.size(); i++) {
            var a = expected.get(i);
            var b = actual.get(i);
            if (!a.name.equals(b.name) || !a.argument.equals(b.argument) || !a.attributes.equals(b.attributes)
                    || a.start != b.start || a.contentStart != b.contentStart || a.contentEnd != b.contentEnd || a.end != b.end)
                fail(source, "node " + i + ": " + describe(a) + " != " + describe(b));
        }
        if (BbCodeSyntax.parse(source).size() != actual.size()) fail(source, "public entry point differs");
    }

    private static String describe(BbCodeSyntax.Node node) {
        return node.name + " argument=" + node.argument + " attributes=" + node.attributes + " ["
                + node.start + "," + node.contentStart + "," + node.contentEnd + "," + node.end + "]";
    }

    private static void fail(String source, String reason) {
        throw new AssertionError(reason + "\nUTF-16 input: " + Arrays.toString(source.toCharArray()));
    }

    private static void benchmark() {
        String[] sources = {
                "普通中文章节内容，包含 Emoji 🐴。\n".repeat(2000),
                "[p][b]章节🐴[/b][color=red]文字[/color][url=/work/1]链接[/url][/p]\n".repeat(500),
                "[list][*]one[b]bold[/b][*]two[/list][x alt=\"a ] b\" k=v]text[/x]\n".repeat(500)
        };
        for (String source : sources) {
            compare(source);
            for (int i = 0; i < 80; i++) { measure(source, false); measure(source, true); }
            long[] javaTimes = new long[101], nativeTimes = new long[101];
            for (int i = 0; i < javaTimes.length; i++) {
                if ((i & 1) == 0) { javaTimes[i] = measure(source, false); nativeTimes[i] = measure(source, true); }
                else { nativeTimes[i] = measure(source, true); javaTimes[i] = measure(source, false); }
            }
            Arrays.sort(javaTimes); Arrays.sort(nativeTimes);
            System.out.printf(java.util.Locale.ROOT, "%d UTF-16 units: Java %.3f ms, JNI + Node assembly %.3f ms (host median)%n",
                    source.length(), javaTimes[50] / 1e6, nativeTimes[50] / 1e6);
        }
    }

    private static long measure(String source, boolean nativeParser) {
        long start = System.nanoTime();
        sink = (nativeParser ? BbCodeSyntax.parseNative(source) : BbCodeSyntax.parseJava(source)).size();
        return System.nanoTime() - start;
    }
}
