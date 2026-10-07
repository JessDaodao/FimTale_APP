package com.fimtale.utils;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Looper;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.LineBackgroundSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import androidx.core.widget.NestedScrollView;
import com.fimtale.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.spans.StrongEmphasisSpan;
import io.noties.markwon.ext.tables.TableRowSpan;
import io.noties.markwon.image.AsyncDrawableSpan;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class CollapseTest {
    private ActivityController<Activity> controller;
    private TextView article;
    private Markwon renderer;

    @Before public void setup() {
        controller = Robolectric.buildActivity(Activity.class);
        controller.get().setTheme(R.style.Theme_Fimtale);
        controller.setup().visible();
        article = new TextView(controller.get());
        article.setTextSize(20);
        controller.get().setContentView(article);
        renderer = BbCodeRendering.create(controller.get());
    }
    @After public void cleanup() {
        for (Dialog dialog : ShadowDialog.getShownDialogs()) dialog.dismiss();
        controller.pause().stop().destroy();
    }
    private Spanned text(TextView view) { return (Spanned) view.getText(); }
    private void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
    private void render(String source) {
        BbCodeRendering.setText(renderer, article, source);
        layout(article, 360, 800);
    }
    private CollapseSpan button(TextView view) {
        return text(view).getSpans(0, view.length(), CollapseSpan.class)[0];
    }
    private void tapButton(TextView view) {
        int offset = text(view).getSpanStart(button(view));
        android.text.Layout layout = view.getLayout();
        int line = layout.getLineForOffset(offset);
        float x = layout.getPrimaryHorizontal(offset) + 16 + view.getPaddingLeft();
        float y = layout.getLineBaseline(line) - 16 + view.getPaddingTop();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(0, 10, action, x, y, 0);
            assertTrue(view.onTouchEvent(event)); event.recycle();
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
    }
    private BottomSheetDialog sheet() {
        BottomSheetDialog sheet = (BottomSheetDialog) ShadowDialog.getLatestDialog();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100));
        layout(sheet.getWindow().getDecorView(), 360, 800);
        shadowOf(Looper.getMainLooper()).idle();
        return sheet;
    }

    @Test public void buttonUsesTheStandardMaterialButtonDimensionsAndBackground() {
        render("[collapse=Note]secret[/collapse]");
        CollapseButtonSpan span = text(article).getSpans(0, article.length(), CollapseButtonSpan.class)[0];
        CollapseButtonSpan.prepare(article.getText(), 360);
        MaterialButton expected = new MaterialButton(controller.get()); expected.setText("Note"); expected.setSingleLine(false);
        expected.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        expected.layout(0, 0, expected.getMeasuredWidth(), expected.getMeasuredHeight());
        Paint.FontMetricsInt metrics = new Paint.FontMetricsInt();
        int width = span.getSize(article.getPaint(), article.getText(), 0, article.length(), metrics);
        assertEquals(expected.getMeasuredWidth(), width);
        assertEquals(expected.getMeasuredHeight(), metrics.descent - metrics.ascent);
        Bitmap actual = Bitmap.createBitmap(width, expected.getHeight(), Bitmap.Config.ARGB_8888);
        Bitmap standard = Bitmap.createBitmap(width, expected.getHeight(), Bitmap.Config.ARGB_8888);
        span.draw(new Canvas(actual), article.getText(), 0, article.length(), 0, 0, expected.getHeight(), expected.getHeight(), article.getPaint());
        expected.draw(new Canvas(standard));
        assertTrue("The disclosure should draw the normal MD3 button", actual.sameAs(standard));
        actual.recycle(); standard.recycle();
        MotionEvent outside = MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN,
                width + 20, article.getLayout().getLineBaseline(0) - 16, 0);
        assertNull("The empty part of the row is not a button", SpoilerSpan.clickableAt(article, outside));
        outside.recycle();
    }

    @Test public void opensAndClosesSheetWithoutChangingTheArticleOrAddingABodyBackground() {
        render("before[collapse=Note][b]secret[/b][spoiler]mask[/spoiler][/collapse]after");
        String before = article.getText().toString();
        int height = article.getLayout().getHeight();
        tapButton(article);
        BottomSheetDialog sheet = sheet();
        TextView content = sheet.findViewById(R.id.collapseSheetContent);
        assertEquals("Note", ((TextView) sheet.findViewById(R.id.collapseSheetTitle)).getText().toString());
        assertEquals("secretmask", content.getText().toString());
        assertEquals(1, text(content).getSpans(0, content.length(), StrongEmphasisSpan.class).length);
        assertFalse(text(content).getSpans(0, content.length(), SpoilerSpan.class)[0].isRevealed());
        assertNull(content.getBackground());
        assertEquals(0, text(content).getSpans(0, content.length(), LineBackgroundSpan.class).length);
        assertEquals(before, article.getText().toString());
        assertEquals(height, article.getLayout().getHeight());
        sheet.findViewById(R.id.collapseSheetClose).performClick();
        assertFalse(sheet.isShowing());
        tapButton(article);
        assertTrue(sheet().isShowing());
    }

    @Test public void concealedButtonMustBeRevealedBeforeOpeningItsSheet() {
        render("[spoiler][collapse=hidden]secret[/collapse][/spoiler]");
        tapButton(article);
        assertTrue(text(article).getSpans(0, article.length(), SpoilerSpan.class)[0].isRevealed());
        assertNull(ShadowDialog.getLatestDialog());
        tapButton(article);
        assertTrue(sheet().isShowing());
    }

    @Test public void entireButtonIsClickableIncludingItsRightHalfAndWrappedTitle() {
        for (String title : new String[]{"点击展开", "这是一段较长的展开按钮标题".repeat(5)}) {
            render("before[collapse=" + title + "]secret[/collapse]after");
            Spanned text = text(article);
            CollapseButtonSpan button = text.getSpans(0, text.length(), CollapseButtonSpan.class)[0];
            int start = text.getSpanStart(button), end = text.getSpanEnd(button);
            Paint.FontMetricsInt metrics = new Paint.FontMetricsInt();
            int width = button.getSize(article.getPaint(), text, start, end, metrics);
            android.text.Layout layout = article.getLayout();
            int baseline = layout.getLineBaseline(layout.getLineForOffset(start));
            for (float fraction : new float[]{0.05f, 0.49f, 0.51f, 0.95f}) {
                MotionEvent event = MotionEvent.obtain(0, 10, MotionEvent.ACTION_DOWN,
                        layout.getPrimaryHorizontal(start) + width * fraction,
                        baseline + metrics.ascent / 2f, 0);
                assertSame("title=" + title + ", fraction=" + fraction, button(article), SpoilerSpan.clickableAt(article, event));
                event.recycle();
            }
        }
    }

    @Test public void draggingOrCancellingAButtonPressDoesNotOpenItsSheet() {
        render("[collapse=点击展开]secret[/collapse]");
        for (boolean cancel : new boolean[]{true, false}) {
            int[] actions = cancel ? new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP}
                    : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP};
            for (int action : actions) {
                float x = action == MotionEvent.ACTION_MOVE ? 250 : 16;
                MotionEvent event = MotionEvent.obtain(0, 10, action, x, article.getLayout().getLineBaseline(0) - 16, 0);
                article.dispatchTouchEvent(event); event.recycle();
            }
            assertNull(ShadowDialog.getLatestDialog());
        }
        tapButton(article);
        assertTrue(sheet().isShowing());
    }

    @Test public void nestedSheetsAndLongContentRemainScrollable() {
        render("[collapse=outer]" + "long body\n".repeat(200) + "[collapse=inner]nested[/collapse][/collapse]");
        tapButton(article);
        BottomSheetDialog parent = sheet();
        TextView content = parent.findViewById(R.id.collapseSheetContent);
        NestedScrollView scroll = parent.findViewById(R.id.collapseSheetScroll);
        assertTrue(scroll.canScrollVertically(1));
        assertFalse(content.getText().toString().contains("nested"));
        button(content).onClick(content);
        BottomSheetDialog child = sheet();
        assertNotSame(parent, child);
        assertEquals("nested", ((TextView) child.findViewById(R.id.collapseSheetContent)).getText().toString());
        child.dismiss();
        assertTrue(parent.isShowing());
        assertFalse(article.getText().toString().contains("long body"));
    }

    @Test public void imagesAndTableLinksRenderOnlyInTheSheet() {
        render("[collapse=media][img]/secret.png[/img][table][tr][td][url=https://example.com]link[/url][/td][/tr][/table][/collapse]");
        assertEquals(0, text(article).getSpans(0, article.length(), AsyncDrawableSpan.class).length);
        tapButton(article);
        TextView content = sheet().findViewById(R.id.collapseSheetContent);
        assertEquals(1, text(content).getSpans(0, content.length(), AsyncDrawableSpan.class).length);
        TableRowSpan row = text(content).getSpans(0, content.length(), TableRowSpan.class)[0];
        Spanned cell = (Spanned) row.findLayoutForHorizontalOffset(0).getText();
        assertEquals(1, cell.getSpans(0, cell.length(), ClickableSpan.class).length);
    }
}
