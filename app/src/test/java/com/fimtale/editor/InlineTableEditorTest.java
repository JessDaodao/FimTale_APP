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
import com.fimtale.R;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = com.fimtale.ResourceApplication.class)
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
    @Test public void focusingATableCellKeepsTheDocumentAtItsCurrentScrollPosition() {
        String source = "正文段落\n".repeat(80) + BbCodeInsertion.table(2, 2, true, "表格内容").text + "\n尾部\n".repeat(30);
        android.widget.ScrollView scroll = scrollingDocument(source);
        int tableLine = body.getLayout().getLineForOffset(source.indexOf("[table]"));
        scroll.scrollTo(0, body.getLayout().getLineTop(tableLine) - 100); drawScroll(scroll);
        int before = scroll.getScrollY(); assertTrue(before > 500);
        java.util.List<Integer> scrollPositions = new java.util.ArrayList<>();
        java.util.List<Boolean> visibleFocusedCells = new java.util.ArrayList<>();
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> scrollPositions.add(y));
        container.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != null && newFocus.getId() == R.id.editorTableCell)
                visibleFocusedCells.add(newFocus.getGlobalVisibleRect(new android.graphics.Rect()));
        });
        BbCodeEditText.TableHit hit = body.tableCellAtSource(source.indexOf("表格内容")); assertNotNull(hit);
        assertNotNull("The visible table must be hit-testable: " + hit.bounds + ", scroll=" + before,
                body.tableCellAt(hit.bounds.centerX(), hit.bounds.centerY()));
        tap(hit);
        assertTrue("Tapping must open the cell editor before layout", container.isEditingCell());
        drawScroll(scroll);
        assertTrue("Layout must keep the cell editor open", container.isEditingCell());
        assertEquals("Tapping a visible cell must not jump to the document top", before, scroll.getScrollY());
        assertTrue(container.activeEditor().hasFocus());
        container.activeEditor().setSelection(0);
        container.activeEditor().onCreateInputConnection(new EditorInfo()).commitText("编辑", 1); drawScroll(scroll);
        assertEquals(before, scroll.getScrollY());
        BbCodeEditText.TableHit updated = body.tableCellAtSource(body.getText().toString().indexOf("编辑"));
        tap(body.tableCellAtSource(updated.tableStart + updated.table.cells.get(1).source.node.contentStart));
        drawScroll(scroll);
        assertEquals(before, scroll.getScrollY());
        assertTrue("Focus changes must not briefly jump away and back: " + scrollPositions,
                scrollPositions.stream().allMatch(y -> y == before));
        assertFalse(visibleFocusedCells.isEmpty());
        assertTrue("The focused cell must already be in the visible table", visibleFocusedCells.stream().allMatch(Boolean::booleanValue));
    }
    @Test public void theActiveCellRemainsVisibleWhenTheKeyboardShrinksTheViewport() {
        String source = "正文段落\n".repeat(80) + BbCodeInsertion.table(2, 2, true, "表格内容").text + "\n尾部\n".repeat(30);
        android.widget.ScrollView scroll = scrollingDocument(source);
        int tableLine = body.getLayout().getLineForOffset(source.indexOf("[table]"));
        scroll.scrollTo(0, body.getLayout().getLineTop(tableLine) - 300); drawScroll(scroll);
        tap(body.tableCellAtSource(source.indexOf("表格内容"))); drawScroll(scroll);
        int before = scroll.getScrollY();
        scroll.setSmoothScrollingEnabled(false);
        android.view.ViewGroup.LayoutParams params = scroll.getLayoutParams(); params.height = 250; scroll.setLayoutParams(params);
        drawScroll(scroll);
        assertTrue("Revealing the cell may scroll down, but never back to the document top", scroll.getScrollY() >= before);
        BbCodeEditText cell = container.activeEditor(); assertTrue(cell.hasFocus());
        android.graphics.Rect visible = new android.graphics.Rect();
        assertTrue(cell.getGlobalVisibleRect(visible));
        assertEquals("The input must stay above the keyboard", cell.getHeight(), visible.height());
    }
    private android.widget.ScrollView scrollingDocument(String source) {
        android.widget.ScrollView scroll = new android.widget.ScrollView(controller.get()); scroll.setFillViewport(true);
        ((android.view.ViewGroup) container.getParent()).removeView(container);
        scroll.addView(container, new android.widget.ScrollView.LayoutParams(-1, -2));
        controller.get().setContentView(scroll, new android.view.ViewGroup.LayoutParams(500, 500));
        controller.visible().windowFocusChanged(true);
        body.setText(source); body.setSourceVisible(false);
        drawScroll(scroll);
        return scroll;
    }
    private void drawScroll(android.widget.ScrollView scroll) {
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
        int height = scroll.getLayoutParams().height;
        scroll.measure(View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        scroll.layout(0, 0, 500, height);
        Bitmap bitmap = Bitmap.createBitmap(500, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.translate(-scroll.getScrollX(), -scroll.getScrollY());
        scroll.draw(canvas); bitmap.recycle();
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
    @Test public void markdownTapsRequestSourceEditingAndKeepTheBlockIntact() {
        String source = "before\n[markdown]**bold** and *italic*[/markdown]\nafter";
        java.util.List<BbCodeSyntax.Node> requested = new java.util.ArrayList<>();
        body.setMarkdownClickListener((editor, markdown) -> { assertSame(body, editor); requested.add(markdown); });
        body.setText(source); body.setSourceVisible(false); draw();
        BbCodeBlockPreview preview = body.getText().getSpans(0, body.length(), BbCodeBlockPreview.class)[0];
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(0, 20, action,
                    preview.bounds.centerX() + body.getTotalPaddingLeft(), preview.bounds.centerY() + body.getTotalPaddingTop(), 0);
            body.dispatchTouchEvent(event); event.recycle();
        }
        draw();
        assertSame(body, container.activeEditor());
        assertEquals(source, body.getText().toString());
        assertEquals(1, requested.size());
        assertEquals("**bold** and *italic*", source.substring(requested.get(0).contentStart, requested.get(0).contentEnd));
        body.setSourceVisible(true);
        assertSame(body, container.activeEditor());
    }
    @Test public void markdownInsideATableCellUsesTheSameSourceEditor() {
        String source = "[table][tr][td][markdown]**keep**[/markdown][/td][/tr][/table]";
        java.util.List<BbCodeEditText> requested = new java.util.ArrayList<>();
        body.setMarkdownClickListener((editor, markdown) -> requested.add(editor));
        body.setText(source); body.setSourceVisible(false); draw();
        tap(body.tableCellAtSource(source.indexOf("[markdown]"))); draw(); draw();
        BbCodeEditText cell = container.activeEditor();
        BbCodeBlockPreview preview = cell.getText().getSpans(0, cell.length(), BbCodeBlockPreview.class)[0];
        android.graphics.RectF visible = new android.graphics.RectF(preview.bounds);
        visible.offset(cell.getTotalPaddingLeft() - cell.getScrollX(), cell.getTotalPaddingTop() - cell.getScrollY());
        assertTrue(visible.intersect(0, 0, cell.getWidth(), cell.getHeight()));
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(0, 20, action, visible.centerX(), visible.centerY(), 0);
            cell.dispatchTouchEvent(event); event.recycle();
        }
        assertEquals(java.util.Collections.singletonList(cell), requested);
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
