package com.fimtale.editor;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.text.Spanned;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import com.fimtale.NativeRobolectricTestRunner;
import com.fimtale.R;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(NativeRobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class InlineTableEditorTest {
    private ActivityController<AppCompatActivity> controller;
    private BbCodeEditorLayout container;
    private BbCodeEditText body;
    @Before public void setup() {
        controller = Robolectric.buildActivity(AppCompatActivity.class);
        controller.get().setTheme(R.style.Theme_Fimtale); controller.setup();
        container = new BbCodeEditorLayout(controller.get(), null);
        body = new BbCodeEditText(controller.get(), null);
        body.setTextSize(20); body.setPadding(10, 12, 10, 12); body.setGravity(android.view.Gravity.TOP);
        body.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        container.addView(body, new FrameLayout.LayoutParams(-1, -1));
        controller.get().setContentView(container);
    }
    @After public void cleanup() { controller.pause().stop().destroy(); }
    private void draw() {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
        container.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        container.layout(0, 0, 500, 900);
        Bitmap bitmap = Bitmap.createBitmap(500, 900, Bitmap.Config.ARGB_8888);
        container.draw(new Canvas(bitmap)); bitmap.recycle();
    }
    private void tap(BbCodeEditText.TableHit hit) {
        float x = hit.bounds.centerX(), y = hit.bounds.centerY();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(0, 20, action, x, y, 0);
            body.dispatchTouchEvent(event); event.recycle();
        }
    }
    @Test public void tappingCellEditsInDocumentWithImeAndPreservesOtherSource() {
        String original = "before\n[table]\n[tr][th colspan=2]Header[/th][/tr]\n[tr][td][b]first[/b][/td][td]second[/td][/tr]\n[/table]\nafter";
        body.setText(original); body.setSourceVisible(false); draw();
        int start = original.indexOf("[b]first");
        BbCodeEditText.TableHit hit = body.tableCellAtSource(start); assertNotNull(hit); tap(hit);
        draw();
        assertTrue(container.isEditingCell());
        BbCodeEditText cell = container.activeEditor();
        assertNotSame(body, cell); assertEquals("[b]first[/b]", cell.getText().toString());
        assertEquals(Math.round(hit.bounds.left), cell.getLeft());
        cell.setSelection(3, 8);
        InputConnection connection = cell.onCreateInputConnection(new EditorInfo());
        assertNotNull(connection);
        connection.setComposingText("中文", 1); connection.commitText("中文🐴", 1); connection.finishComposingText();
        draw();
        assertEquals(original.replace("first", "中文🐴"), body.getText().toString());
        assertTrue(container.isEditingCell());
        assertTrue(body.getText().toString().contains("[th colspan=2]Header[/th]"));
        assertNotNull(body.tableCellAtSource(start));
        body.setSourceVisible(true);
        assertFalse(container.isEditingCell());
        assertEquals(0, body.getText().getSpans(0, body.length(), EditableTableSpan.class).length);
        body.setSourceVisible(false); draw();
        assertEquals(1, body.getText().getSpans(0, body.length(), EditableTableSpan.class).length);
    }
    @Test public void emptyCellCanReceiveTextAndSourceRefreshClosesStaleInput() {
        String source = BbCodeInsertion.table(2, 2, true, "").text;
        body.setText(source); body.setSourceVisible(false); draw();
        int start = source.indexOf("[th]") + 4;
        tap(body.tableCellAtSource(start));
        container.activeEditor().getText().append("hello"); draw();
        assertTrue(body.getText().toString().contains("[th]hello[/th]"));
        body.setText("replacement");
        assertFalse(container.isEditingCell());
        assertEquals("replacement", body.getText().toString());
    }
    @Test public void focusingTableKeepsGridVisibleUntilExplicitSourceMode() {
        String source = BbCodeInsertion.table(2, 2, true, "text").text;
        body.setText(source); body.requestFocus(); body.setSelection(source.indexOf("text"));
        body.setSourceVisible(false); draw();
        assertEquals(1, body.getText().getSpans(0, body.length(), EditableTableSpan.class).length);
        assertEquals(source, body.getText().toString());
    }
    @Test public void typingAndDeletingStyledTextNeverRevealItsDelimiters() {
        body.setText("[b]中文🐴[/b]"); body.requestFocus(); body.setSelection(body.length());
        body.setSourceVisible(false); draw();
        InputConnection input = body.onCreateInputConnection(new EditorInfo());
        input.deleteSurroundingTextInCodePoints(1, 0); draw();
        assertEquals("[b]中文[/b]", body.getText().toString());
        android.text.style.ReplacementSpan[] hidden = body.getText().getSpans(0, 3, android.text.style.ReplacementSpan.class);
        assertEquals(1, hidden.length);
        assertEquals(0, hidden[0].getSize(body.getPaint(), body.getText(), 0, 3, null));
        body.setSourceVisible(true); draw();
        assertEquals(0, body.getText().getSpans(0, body.length(), android.text.style.ReplacementSpan.class).length);
        assertEquals("[b]中文[/b]", body.getText().toString());
    }
    @Test public void markdownEditsUseRichTextInPlaceAndKeepFormatting() {
        String source = "before\n[markdown]**bold** and *italic*[/markdown]\nafter";
        body.setText(source); body.setSourceVisible(false); draw();
        BbCodeBlockPreview preview = body.getText().getSpans(0, body.length(), BbCodeBlockPreview.class)[0];
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(0, 20, action,
                    preview.bounds.centerX() + body.getTotalPaddingLeft(), preview.bounds.centerY() + body.getTotalPaddingTop(), 0);
            body.dispatchTouchEvent(event); event.recycle();
        }
        draw();
        assertNotSame(body, container.activeEditor());
        assertEquals(source, body.getText().toString());
        BbCodeEditText input = container.activeEditor();
        assertEquals("[b]bold[/b] and [i]italic[/i]", input.getText().toString());
        input.setSelection(3, 7);
        input.onCreateInputConnection(new EditorInfo()).commitText("中文", 1);
        draw();
        assertEquals("before\n[b]中文[/b] and [i]italic[/i]\nafter", body.getText().toString());
        body.setSourceVisible(true);
        assertSame(body, container.activeEditor());
    }
    @Test public void enteringAndLeavingMarkdownWithoutTypingPreservesOriginalSource() {
        String source = "[markdown]**keep**\n\n*exact*[/markdown]";
        body.setText(source); body.setSourceVisible(false); draw();
        container.editBlock(new BbCodeEditText.BlockHit(0, source.length(), MarkdownBbCode.convert("**keep**\n\n*exact*"),
                new android.graphics.RectF(10, 12, 490, 150)));
        body.setSourceVisible(true);
        assertEquals(source, body.getText().toString());
    }
    @Test public void cuttingAndPastingAcrossStylesKeepsValidMarkup() {
        body.setText("[b]first[/b][i]second[/i]"); body.setSourceVisible(false);
        body.setSelection(body.getText().toString().indexOf("rst"), body.getText().toString().indexOf("ond"));
        body.onTextContextMenuItem(android.R.id.cut);
        assertEquals("[b]fi[/b][i]ond[/i]", body.getText().toString());
        android.content.ClipboardManager clipboard = controller.get().getSystemService(android.content.ClipboardManager.class);
        assertEquals("rstsec", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        body.onTextContextMenuItem(android.R.id.paste);
        assertEquals("[b]firstsec[/b][i]ond[/i]", body.getText().toString());
        assertEquals(2, BbCodeSyntax.parse(body.getText().toString()).size());
    }
    @Test public void escapedBracketsRenderAsCharactersAndDeleteAsOneCharacter() {
        body.setText("literal &#91;b&#93;"); body.setSourceVisible(false); draw();
        int start = body.getText().toString().indexOf("&#91;");
        android.text.style.ReplacementSpan[] spans = body.getText().getSpans(start, start + 5, android.text.style.ReplacementSpan.class);
        assertEquals(1, spans.length);
        assertEquals((int) Math.ceil(body.getPaint().measureText("[")), spans[0].getSize(body.getPaint(), body.getText(), start, start + 5, null));
        body.requestFocus(); body.setSelection(start + 5);
        body.onCreateInputConnection(new EditorInfo()).deleteSurroundingText(1, 0);
        assertEquals("literal b&#93;", body.getText().toString());
    }
}
