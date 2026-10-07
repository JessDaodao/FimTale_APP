package com.fimtale.utils;

import android.app.Application;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.text.Layout;
import android.text.Spanned;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.fimtale.ui.ImagePreview;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TableRowSpan;
import io.noties.markwon.image.AsyncDrawableSpan;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SpoilerSpanTest {
    private Markwon renderer;
    private Activity context;
    @Before public void setup() {
        context = Robolectric.buildActivity(Activity.class).setup().get();
        renderer = BbCodeRendering.create(context);
    }

    private Spanned render(String source) {
        return BbCodeText.normalizeTables(renderer.toMarkdown(BbCode.toMarkdown(source)));
    }
    private TextView view(Spanned text) {
        TextView view = new TextView(context);
        view.setLayoutParams(new ViewGroup.LayoutParams(320, ViewGroup.LayoutParams.WRAP_CONTENT));
        view.setTextColor(Color.BLACK);
        view.setTextSize(24);
        BbCodeText.prepare(text, view.getPaint(), 320);
        view.setText(text, TextView.BufferType.SPANNABLE);
        SpoilerSpan.bind(view);
        measure(view);
        return view;
    }
    private void measure(TextView view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }
    private Bitmap draw(TextView view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
    private void tap(TextView view, int offset) {
        Layout layout = view.getLayout();
        int line = layout.getLineForOffset(offset);
        tap(view, layout.getPrimaryHorizontal(offset) + 1 + view.getTotalPaddingLeft(),
                (layout.getLineTop(line) + layout.getLineBottom(line)) / 2f + view.getTotalPaddingTop());
    }
    private void tap(TextView view, float x, float y) {
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(0, 20, action, x, y, 0);
            view.dispatchTouchEvent(event);
            event.recycle();
        }
    }
    private int pixels(Bitmap bitmap, int color) {
        int count = 0;
        for (int y = 0; y < bitmap.getHeight(); y++)
            for (int x = 0; x < bitmap.getWidth(); x++) if (bitmap.getPixel(x, y) == color) count++;
        return count;
    }
    private void tapMask(TextView view) {
        Bitmap bitmap = draw(view);
        for (int y = 0; y < bitmap.getHeight(); y++)
            for (int x = 0; x < bitmap.getWidth(); x++) if (bitmap.getPixel(x, y) == 0xff333333) {
                tap(view, x + 2f, y + 2f);
                return;
            }
        fail("No mask was drawn");
    }
    private SpoilerSpan spoiler(TextView view) {
        Spanned text = (Spanned) view.getText();
        return text.getSpans(0, text.length(), SpoilerSpan.class)[0];
    }

    @Test public void inlineMaskOverridesColorsAndTapRestoresStyleWithoutChangingLayout() {
        TextView view = view(render("前 [spoiler][b][color=red][bg-color=cyan]secret[/bg-color][/color][/b][/spoiler] 后"));
        assertEquals("前 secret 后", view.getText().toString());
        Bitmap before = draw(view);
        assertEquals(0, pixels(before, Color.RED));
        assertEquals(0, pixels(before, Color.CYAN));
        assertTrue(pixels(before, 0xff333333) > 100);
        int height = view.getHeight();
        float end = view.getLayout().getPrimaryHorizontal(8);
        tap(view, 3);
        assertTrue(spoiler(view).isRevealed());
        measure(view);
        Bitmap after = draw(view);
        assertTrue(pixels(after, Color.RED) > 0);
        assertTrue(pixels(after, Color.CYAN) > 100);
        assertEquals(height, view.getHeight());
        assertEquals(end, view.getLayout().getPrimaryHorizontal(8), 0.01f);
        assertEquals("前 secret 后", view.getText().toString());
    }

    @Test public void hiddenLinksRevealBeforeOpeningTheirDestination() {
        TextView view = view(render("[spoiler][url=https://example.com/secret]secret link[/url][/spoiler]"));
        tap(view, 2);
        assertTrue(spoiler(view).isRevealed());
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
        tap(view, 2);
        assertEquals("https://example.com/secret",
                shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity().getDataString());
    }

    @Test public void multilineAndPageSlicesShareRevealStateAndPreserveWrapping() {
        Spanned text = render("[spoiler]" + "长段落用于验证跨页黑条。".repeat(80) + "[/spoiler]");
        TextView first = view((Spanned) text.subSequence(0, 180));
        TextView second = view((Spanned) text.subSequence(180, text.length()));
        assertSame(spoiler(first), spoiler(second));
        assertTrue(first.getLineCount() > 3);
        Bitmap before = draw(second);
        int lines = second.getLineCount();
        tap(first, first.getLayout().getLineStart(2) + 1);
        assertTrue(spoiler(second).isRevealed());
        measure(second);
        assertEquals(lines, second.getLineCount());
        assertFalse(before.sameAs(draw(second)));
        assertEquals(text.toString(), first.getText().toString() + second.getText());
    }

    @Test public void nestedSpoilersRemainHiddenUntilTheirOwnRevealAndNewContentResets() {
        TextView view = view(render("[spoiler]outer [spoiler][color=red]inner[/color][/spoiler] end[/spoiler]"));
        tap(view, 1);
        assertEquals(0, pixels(draw(view), Color.RED));
        tap(view, 8);
        assertTrue(pixels(draw(view), Color.RED) > 0);
        BbCodeRendering.setText(renderer, view, "[spoiler]new text[/spoiler]");
        assertFalse(spoiler(view).isRevealed());
    }

    @Test public void coveredImagesStayInlineAndAreMaskedBeforePreview() {
        Spanned text = ImagePreview.images(render("[spoiler][img]/secret.png[/img][/spoiler]"));
        assertEquals(1, BbCodeText.segments(text).size());
        assertNull(BbCodeText.segments(text).get(0).image);
        AsyncDrawableSpan image = text.getSpans(0, text.length(), AsyncDrawableSpan.class)[0];
        Bitmap photo = Bitmap.createBitmap(40, 24, Bitmap.Config.ARGB_8888);
        photo.eraseColor(Color.RED);
        BitmapDrawable loaded = new BitmapDrawable(context.getResources(), photo);
        loaded.setBounds(0, 0, 40, 24);
        image.getDrawable().setResult(loaded);
        TextView view = view(text);
        assertEquals(0, pixels(draw(view), Color.RED));
        assertTrue("image view height=" + view.getHeight() + " text=" + view.getText()
                + " bounds=" + image.getDrawable().getBounds(), image.getDrawable().hasKnownDimensions());
        measure(view);
        tap(view, text.getSpanStart(image));
        assertTrue(spoiler(view).isRevealed());
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
        assertTrue(pixels(draw(view), Color.RED) > 100);
        tap(view, text.getSpanStart(image));
        assertTrue(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity()
                .getStringExtra("image_url").endsWith("/secret.png"));
    }

    @Test public void spoilerInsideTableCellCanBeRevealed() {
        TextView view = view(render("[table][tr][td][spoiler][color=red]secret[/color][/spoiler][/td][td]public[/td][/tr][/table]"));
        Bitmap before = draw(view);
        assertEquals(0, pixels(before, Color.RED));
        assertTrue(pixels(before, 0xff333333) > 100);
        Spanned text = (Spanned) view.getText();
        TableRowSpan row = text.getSpans(0, text.length(), TableRowSpan.class)[0];
        Layout cell = row.findLayoutForHorizontalOffset(10);
        assertTrue(cell.getText().toString().contains("secret"));
        tapMask(view);
        assertTrue(pixels(draw(view), Color.RED) > 0);
    }

    @Test public void spoilerAroundTableMasksCellsUntilRevealed() {
        TextView view = view(render("[spoiler][table][tr][td][color=red]secret[/color][/td][/tr][/table][/spoiler]"));
        assertEquals(0, pixels(draw(view), Color.RED));
        tap(view, 1, view.getHeight() / 2f);
        assertTrue(pixels(draw(view), Color.RED) > 0);
    }

    @Test public void lastGlyphAndDarkThemeRemainTappableAndVisible() {
        TextView view = view(render("[spoiler]secret[/spoiler]"));
        view.setTextColor(Color.WHITE);
        SpoilerSpan.bind(view);
        android.text.TextPaint paint = new android.text.TextPaint();
        spoiler(view).updateDrawState(paint);
        assertEquals(0xccffffff, paint.bgColor);
        float end = view.getLayout().getPrimaryHorizontal(view.getText().length());
        tap(view, end - 1, view.getHeight() / 2f);
        assertTrue(spoiler(view).isRevealed());
    }

    @Test public void conversationSummaryDoesNotExposeSpoilersWhenRemovingStyles() {
        Spanned text = render("public [spoiler]secret [spoiler]nested[/spoiler][/spoiler] end");
        String summary = BbCodeText.plainPreview(text);
        assertFalse(summary.contains("secret"));
        assertFalse(summary.contains("nested"));
        assertTrue(summary.startsWith("public "));
        assertTrue(summary.endsWith(" end"));
    }
}
