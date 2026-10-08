package com.fimtale.editor;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import androidx.appcompat.app.AppCompatActivity;
import com.fimtale.R;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CollapseInputTest {
    private ActivityController<AppCompatActivity> controller;
    private BbCodeEditText body;
    @Before public void setup() {
        controller = Robolectric.buildActivity(AppCompatActivity.class);
        controller.get().setTheme(R.style.Theme_Fimtale); controller.setup().visible().windowFocusChanged(true);
        body = new BbCodeEditText(controller.get(), null);
        body.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        body.setTextSize(18); body.setLineSpacing(6, 1); body.setPadding(16, 12, 16, 12); body.setGravity(Gravity.TOP);
        body.setParagraphIndentEnabled(true);
        controller.get().setContentView(body); body.requestFocus();
    }
    @After public void cleanup() { controller.pause().stop().destroy(); }
    private void draw() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        body.measure(View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        body.layout(0, 0, 480, 900);
        Bitmap bitmap = Bitmap.createBitmap(480, 900, Bitmap.Config.ARGB_8888);
        body.draw(new Canvas(bitmap)); bitmap.recycle();
    }
    private BbCodeSyntax.Node collapse() {
        for (BbCodeSyntax.Node node : BbCodeSyntax.parse(body.getText().toString())) if (node.name.equals("collapse")) return node;
        throw new AssertionError("Collapse delimiters were lost: " + body.getText());
    }
    private void tapContentEnd() {
        tapOffset(collapse().contentEnd);
    }
    private void tapOffset(int offset) {
        int line = body.getLayout().getLineForOffset(offset);
        float x = body.getTotalPaddingLeft() + body.getLayout().getPrimaryHorizontal(offset) + 2;
        float y = body.getTotalPaddingTop() + body.getLayout().getLineBaseline(line) - 2;
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(0, 20, action, x, y, 0);
            body.dispatchTouchEvent(event); event.recycle();
        }
    }
    @Test public void tappingAtTheEndOfACollapseKeepsEnterAndChineseInputInsideIt() {
        body.setText("[collapse=标题]第一行[/collapse]"); body.setSourceVisible(false); draw(); tapContentEnd();
        InputConnection input = body.onCreateInputConnection(new EditorInfo()); assertNotNull(input);
        input.commitText("\n", 1); input.setComposingText("zhong", 1); input.commitText("中文第二行", 1); draw();
        BbCodeSyntax.Node node = collapse();
        String content = body.getText().subSequence(node.contentStart, node.contentEnd).toString();
        assertEquals("第一行\n[indent]中文第二行[/indent]", content);
        assertEquals(node.end, body.length());
        assertTrue(body.getSelectionStart() <= node.contentEnd);
    }
    @Test public void anEmptyInsertedCollapseAcceptsRepeatedParagraphBreaks() {
        body.setText("[collapse=标题][/collapse]"); body.setSourceVisible(false); draw(); tapContentEnd();
        InputConnection input = body.onCreateInputConnection(new EditorInfo());
        input.commitText("第一行", 1); input.commitText("\n\n", 1); input.commitText("第三行", 1); draw();
        BbCodeSyntax.Node node = collapse();
        assertEquals("[indent]第一行[/indent]\n[indent][/indent]\n[indent]第三行[/indent]",
                body.getText().subSequence(node.contentStart, node.contentEnd).toString());
    }
    @Test public void tappingTheLastIndentedLineOfANestedCollapseKeepsNewlinesInsideIt() {
        body.setText("[collapse=外层][collapse=内层][indent]第一行[/indent][/collapse][/collapse]");
        body.setSourceVisible(false); draw(); tapContentEnd();
        InputConnection input = body.onCreateInputConnection(new EditorInfo());
        input.commitText("\n", 1); input.commitText("\n", 1); input.setComposingText("san", 1); input.commitText("第三行", 1); draw();
        assertEquals("[collapse=外层][collapse=内层][indent]第一行[/indent]\n[indent][/indent]\n[indent]第三行[/indent][/collapse][/collapse]",
                body.getText().toString());
    }
    @Test public void tappingOutsideACollapseStillEditsTheFollowingParagraph() {
        String original = "[collapse=标题]折叠正文[/collapse]\n框外正文";
        body.setText(original); body.setSourceVisible(false); draw(); tapOffset(body.length());
        body.onCreateInputConnection(new EditorInfo()).commitText("\n下一段", 1); draw();
        assertEquals(original + "\n[indent]下一段[/indent]", body.getText().toString());
    }
    @Test public void theVisibleCursorCoversOnlyTheTextLineIncludingInNestedCollapses() {
        for (String source : new String[]{"[collapse=标题]正文[/collapse]", "[collapse=外层][collapse=内层]正文[/collapse][/collapse]",
                "[collapse=空框][/collapse]"}) {
            body.setText(source); body.setSelection(Math.max(source.indexOf("正文") + 1, collapse().contentStart));
            body.setSourceVisible(false); draw(); assertCursorFitsTextLine();
        }
    }
    @Test public void cursorHeightIsNormalOnEveryCollapseLineAndInSourceMode() {
        String source = "[collapse=标题]第一行\n中间行\n最后一行[/collapse]\n框外正文";
        body.setText(source); body.setSourceVisible(false);
        for (String line : new String[]{"第一行", "中间行", "最后一行", "框外正文"}) {
            body.setSelection(source.indexOf(line) + 1); draw(); assertCursorFitsTextLine();
        }
        body.setSelection(source.indexOf("第一行") + 1); body.setSourceVisible(true); draw(); assertCursorFitsTextLine();
    }
    private void assertCursorFitsTextLine() {
        Drawable cursor = body.getTextCursorDrawable();
        Rect bounds = new Rect(cursor.getBounds());
        assertFalse("The focused editor must draw a cursor", bounds.isEmpty());
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, bounds.width()), bounds.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); canvas.translate(-bounds.left, -bounds.top); cursor.draw(canvas);
        int first = bitmap.getHeight(), last = -1;
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++) {
            if ((bitmap.getPixel(x, y) >>> 24) != 0) { first = Math.min(first, y); last = Math.max(last, y); }
        }
        bitmap.recycle();
        assertTrue("A visible cursor is required", last >= first);
        assertTrue("Cursor height=" + (last - first + 1) + ", text line=" + body.getLineHeight(), last - first + 1 <= body.getLineHeight() + 2);
    }
}
