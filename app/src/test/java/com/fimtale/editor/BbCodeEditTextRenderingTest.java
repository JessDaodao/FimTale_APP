package com.fimtale.editor;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.*;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.fimtale.R;
import com.fimtale.utils.BbCodeRendering;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import com.fimtale.NativeRobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;

@RunWith(NativeRobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BbCodeEditTextRenderingTest {
    private BbCodeEditText editor;
    @Before public void setup() {
        editor = new BbCodeEditText(new ContextThemeWrapper(RuntimeEnvironment.getApplication(),
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar), null);
        editor.setTextSize(18); editor.setLineSpacing(6, 1);
        editor.setLayoutParams(new LinearLayout.LayoutParams(400, 800));
        editor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        editor.layout(0, 0, 400, 800);
    }
    private Editable render(String source) { editor.setText(source); editor.setSourceVisible(false); return editor.getText(); }
    @Test public void stylesUseReaderColorSizingAndIndentRules() {
        Editable text = render("[color=rgba(255,0,0,0.5)]red[/color][bg-color=hsl(120,100%,50%)]green[/bg-color]\n"
                + "[indent=28.0pt]缩进正文[/indent]\n[code]  [b]literal[/b]\n  second[/code]");
        assertEquals(0x80ff0000, text.getSpans(0, text.length(), ForegroundColorSpan.class)[0].getForegroundColor());
        assertEquals(0xff00ff00, text.getSpans(0, text.length(), BackgroundColorSpan.class)[0].getBackgroundColor());
        BbCodeRendering.IndentSpan[] indent = text.getSpans(0, text.length(), BbCodeRendering.IndentSpan.class);
        assertEquals(1, indent.length); assertEquals(0, indent[0].getLeadingMargin(false));
        assertEquals(Math.round(28 * 4 / 3f * editor.getResources().getDisplayMetrics().density), indent[0].getLeadingMargin(true));
        assertEquals(0, text.getSpans(0, text.length(), StyleSpan.class).length);
        assertEquals(1, text.getSpans(0, text.length(), LeadingMarginSpan.class).length);
        text = render("[size=larger]".repeat(10) + "[size=smaller]word[/size]" + "[/size]".repeat(10));
        int word = text.toString().indexOf("word");
        float multiplier = 1;
        for (RelativeSizeSpan span : text.getSpans(word, word + 4, RelativeSizeSpan.class)) multiplier *= span.getSizeChange();
        assertEquals(Math.pow(1.2, 7), multiplier, 0.001);
    }
    @Test public void complexBlocksRenderOverSourceAndRawModeRemovesAllReplacements() {
        String source = "[table]\n[tr][th colspan=2]Header[/th][/tr]\n[tr][td]A[/td][td]B[/td][/tr]\n[/table]\n"
                + "[markdown]\n## Title\n\n**bold**\n[/markdown]\n[collapse=Note][b]hidden[/b][/collapse]\n[ref type=1 id=42]work[/ref]\n[list=1][*]one[*]two[/list]";
        Editable text = render(source);
        assertEquals(2, text.getSpans(0, text.length(), BbCodeBlockPreview.class).length);
        assertEquals(1, text.getSpans(0, text.length(), EditableTableSpan.class).length);
        editor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST));
        editor.layout(0, 0, 400, editor.getMeasuredHeight());
        Bitmap bitmap = Bitmap.createBitmap(400, Math.max(1, editor.getMeasuredHeight()), Bitmap.Config.ARGB_8888);
        editor.draw(new Canvas(bitmap)); bitmap.recycle();
        assertEquals(source, text.toString());
        editor.setSourceVisible(true);
        assertEquals(0, text.getSpans(0, text.length(), ReplacementSpan.class).length);
        assertEquals(source, text.toString());
    }
    @Test public void hiddenSourceLinesDoNotLeaveBlankSpaceBehindThePreview() {
        String source = "[markdown]" + "\n".repeat(30) + "**正文**\n[/markdown]";
        render(source);
        editor.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.AT_MOST));
        editor.layout(0, 0, 400, editor.getMeasuredHeight());
        assertTrue("Collapsed source height=" + editor.getLayout().getHeight(), editor.getLayout().getHeight() < 100);
        assertEquals(source, editor.getText().toString());
    }
    @Test public void mentionsAndHashtagsHaveInlinePreviewsAndRemainEditable() {
        String source = "hi [mention=7]seven[/mention] [hash]火星[/hash]";
        Editable text = render(source);
        assertEquals(2, text.getSpans(0, text.length(), ReplacementSpan.class).length);
        editor.requestFocus(); editor.setSelection(source.indexOf("seven")); editor.setSourceVisible(false);
        assertEquals(4, text.getSpans(0, text.length(), ReplacementSpan.class).length);
        assertEquals(source, text.toString());
    }
    @Test public void hardBreaksAndRulesRenderWithoutAddingNewlinesToTheDraft() {
        String source = "[b]first[/b][br]second\n[hr]\nnext";
        Editable text = render(source);
        assertEquals(2, text.getSpans(0, text.length(), BbCodeBlockPreview.class).length);
        assertEquals(source, text.toString());
        editor.requestFocus(); editor.setSelection(source.indexOf("second")); editor.setSourceVisible(false);
        assertEquals(2, text.getSpans(0, text.length(), BbCodeBlockPreview.class).length);
        assertEquals(source, text.toString());
    }
    @Test public void caretAndComposingTextKeepBlocksRenderedUntilSourceIsRequested() {
        String source = "[markdown]\n**正文**\n[/markdown]\n输入中";
        Editable text = render(source);
        assertEquals(1, text.getSpans(0, text.length(), BbCodeBlockPreview.class).length);
        int selection = source.indexOf("正文");
        editor.requestFocus(); editor.setSelection(selection);
        Object composing = new Object();
        text.setSpan(composing, selection, selection + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_COMPOSING);
        editor.setSourceVisible(false);
        assertTrue(editor.hasFocus());
        assertEquals(1, text.getSpans(0, text.length(), BbCodeBlockPreview.class).length);
        assertSame(text, editor.getText()); assertEquals(source, text.toString());
        assertEquals(selection, editor.getSelectionStart()); assertEquals(selection, text.getSpanStart(composing));
        assertTrue((text.getSpanFlags(composing) & Spanned.SPAN_COMPOSING) != 0);
        text.removeSpan(composing);
        editor.setSourceVisible(true);
        assertEquals(0, text.getSpans(0, text.length(), BbCodeBlockPreview.class).length);
    }
    @Test public void spoilerConcealmentOverridesNestedColorsAndBlocks() {
        Editable text = render("[spoiler][color=red]secret[/color][bg-color=blue]hidden[/bg-color][markdown]**raw**[/markdown][/spoiler]");
        int secret = text.toString().indexOf("secret");
        TextPaint paint = new TextPaint(); paint.setColor(Color.WHITE);
        for (ForegroundColorSpan color : text.getSpans(secret, secret + 6, ForegroundColorSpan.class)) color.updateDrawState(paint);
        assertEquals(Color.TRANSPARENT, paint.getColor());
        assertEquals(0, text.getSpans(0, text.length(), BbCodeBlockPreview.class).length);
    }
    @Test public void renderingEmitsNoTextEditsAndPreservesCorpusExactly() throws Exception {
        final int[] edits = {0};
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { edits[0]++; }
            public void afterTextChanged(Editable s) {}
        };
        try (InputStreamReader input = new InputStreamReader(getClass().getResourceAsStream("/bbcode-corpus.fixture.json"), StandardCharsets.UTF_8)) {
            for (String source : new Gson().fromJson(input, String[].class)) {
                editor.setText(source); editor.addTextChangedListener(watcher); edits[0] = 0;
                editor.setSelection(source.length());
                editor.setSourceVisible(false); editor.setSourceVisible(true);
                assertEquals(source, editor.getText().toString());
                assertEquals(source.length(), editor.getSelectionStart());
                assertEquals(0, edits[0]); editor.removeTextChangedListener(watcher);
            }
        }
    }
    @Test public void fullPreviewUsesReaderSpansWithoutChangingAnUnsavedDraft() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        ContextThemeWrapper themed = new ContextThemeWrapper(activity, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
        String source = "[table][tr][th]Header[/th][/tr][tr][td]A[/td][/tr][/table][markdown]**bold**[/markdown][spoiler]hidden[/spoiler]";
        Editable draft = render(source);
        androidx.appcompat.app.AlertDialog dialog = EditorPreviewDialog.show(themed, source, 18);
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        TextView content = dialog.findViewById(R.id.editorPreviewContent);
        Spanned preview = (Spanned) content.getText();
        assertEquals(2, preview.getSpans(0, preview.length(), io.noties.markwon.ext.tables.TableRowSpan.class).length);
        assertTrue(preview.toString().contains("hidden"));
        assertEquals(1, preview.getSpans(0, preview.length(), ClickableSpan.class).length);
        com.fimtale.utils.SpoilerSpan spoiler = preview.getSpans(0, preview.length(), com.fimtale.utils.SpoilerSpan.class)[0];
        assertFalse(spoiler.isRevealed());
        spoiler.onClick(content);
        assertTrue(spoiler.isRevealed());
        assertTrue(dialog.isShowing());
        dialog.dismiss(); assertEquals(source, draft.toString()); activity.finish();
    }
}
