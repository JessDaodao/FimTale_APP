package com.fimtale.editor;

import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import androidx.appcompat.app.AppCompatActivity;
import com.fimtale.R;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ParagraphInputTest {
    private ActivityController<AppCompatActivity> controller;
    private BbCodeEditText body;
    private InputConnection input;
    @Before public void setup() {
        controller = Robolectric.buildActivity(AppCompatActivity.class);
        controller.get().setTheme(R.style.Theme_Fimtale); controller.setup();
        body = new BbCodeEditText(controller.get(), null);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        body.setParagraphIndentEnabled(true);
        controller.get().setContentView(body); body.requestFocus(); body.setSelection(0);
        input = body.onCreateInputConnection(new EditorInfo()); assertNotNull(input);
    }
    @After public void cleanup() { controller.pause().stop().destroy(); }
    private String source() { return body.getText().toString(); }

    @Test public void composingChineseAndRepeatedEnterKeepTheCaretInsideIndentedParagraphs() {
        input.setComposingText("zhong", 1);
        assertEquals("[indent]zhong[/indent]", source());
        assertEquals(8, BaseInputConnection.getComposingSpanStart(body.getText()));
        assertEquals(13, BaseInputConnection.getComposingSpanEnd(body.getText()));
        input.commitText("中文🐴", 1);
        input.commitText("\n", 1);
        input.commitText("\n", 1);
        input.setComposingText("di", 1); input.commitText("第二段", 1);
        assertEquals("[indent]中文🐴[/indent]\n[indent][/indent]\n[indent]第二段[/indent]", source());
        assertEquals(source().indexOf("第二段") + 3, body.getSelectionStart());
        assertEquals(-1, BaseInputConnection.getComposingSpanStart(body.getText()));
    }

    @Test public void hardwareEnterSplitsStyledTextAndBackspaceJoinsTheParagraphs() {
        body.setText("[indent][b]前后[/b][/indent]"); body.setSelection(source().indexOf("后"));
        body.onKeyDown(KeyEvent.KEYCODE_ENTER, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        assertEquals("[indent][b]前[/b][/indent]\n[indent][b]后[/b][/indent]", source());
        assertEquals(source().indexOf("后"), body.getSelectionStart());
        input.deleteSurroundingText(1, 0);
        assertEquals("[indent][b]前[/b][b]后[/b][/indent]", source());
        assertEquals(3, BbCodeSyntax.parse(source()).size());
    }

    @Test public void pastedParagraphsCarryIndentIntoSavedBbcodeWithoutAddingSpaceCharacters() {
        ClipboardManager clipboard = controller.get().getSystemService(ClipboardManager.class);
        clipboard.setPrimaryClip(ClipData.newPlainText("", "第一段\r\n第二段\n第三段"));
        body.onTextContextMenuItem(android.R.id.paste);
        assertEquals("[indent]第一段[/indent]\n[indent]第二段[/indent]\n[indent]第三段[/indent]", source());
        assertEquals(source().indexOf("第三段") + 3, body.getSelectionStart());
    }

    @Test public void typingAtTheVisualEndStaysInsideTheParagraphAndEnterInheritsItsIndent() {
        body.setText("[indent=3em]原文[/indent]"); body.setSelection(body.length());
        input.commitText("续写", 1);
        assertEquals("[indent=3em]原文续写[/indent]", source());
        body.setSelection(body.length()); input.commitText("\n下一段", 1);
        assertEquals("[indent=3em]原文续写[/indent]\n[indent=3em]下一段[/indent]", source());
    }

    @Test public void modernImeCallsAndReplacingTheWholeDocumentKeepParagraphFormatting() {
        android.view.inputmethod.TextAttribute attribute = new android.view.inputmethod.TextAttribute.Builder().build();
        input.setComposingText("first", 1, attribute); input.commitText("第一段", 1, attribute);
        input.commitText("\n第二段", 1, attribute);
        assertEquals("[indent]第一段[/indent]\n[indent]第二段[/indent]", source());
        body.setSelection(0, body.length()); input.commitText("替换正文", 1);
        assertEquals("[indent]替换正文[/indent]", source());
    }

    @Test public void undoAndRedoTreatEnterAndItsIndentAsOneEdit() {
        body.setText("[indent]原文[/indent]"); body.setSelection(source().indexOf("[/indent]"));
        String before = source(); input.commitText("\n", 1); String after = source();
        body.onTextContextMenuItem(android.R.id.undo); assertEquals(before, source());
        body.onTextContextMenuItem(android.R.id.redo); assertEquals(after, source());
    }

    @Test public void loadingAndSourceModeRemainLiteralAndCodeDoesNotAcquireParagraphTags() {
        body.setText("existing\ntext"); body.setSourceVisible(false);
        assertEquals("existing\ntext", source());
        body.setText("[code]  first[/code]"); body.setSelection(source().indexOf("[/code]"));
        input.commitText("\n  second", 1);
        assertEquals("[code]  first\n  second[/code]", source());
        body.setText(""); body.setSelection(0); body.setSourceVisible(true);
        input.commitText("plain\nsource", 1);
        assertEquals("plain\nsource", source());
    }
}
