package com.fimtale;

import android.app.Application;
import android.content.Intent;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.view.View;
import com.fimtale.ui.ImagePreview;
import com.fimtale.utils.BbCode;
import com.fimtale.utils.BbCodeRendering;
import io.noties.markwon.Markwon;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ImagePreviewTest {
    private Application app() { return RuntimeEnvironment.getApplication(); }
    @Test public void inlineImagesOpenPreviewAndBindingIsIdempotent() {
        Markwon renderer = BbCodeRendering.create(app());
        Spanned text = ImagePreview.images(renderer.toMarkdown(BbCode.toMarkdown("before [img]/photo.png[/img] after")));
        text = ImagePreview.images(text);
        ClickableSpan[] spans = text.getSpans(0, text.length(), ClickableSpan.class);
        assertEquals(1, spans.length);
        spans[0].onClick(new View(app()));
        Intent intent = shadowOf(app()).getNextStartedActivity();
        assertEquals(ImagePreviewActivity.class.getName(), intent.getComponent().getClassName());
        assertTrue(intent.getStringExtra("image_url").endsWith("/photo.png"));
    }
    @Test public void preservesLinksAndSkipsEmoticons() {
        Markwon renderer = BbCodeRendering.create(app());
        Spanned original = renderer.toMarkdown(BbCode.toMarkdown("[url=https://example.com]link[/url] :ftemoji_wahaha:"));
        Spanned result = ImagePreview.images(original);
        ClickableSpan[] before = original.getSpans(0, original.length(), ClickableSpan.class);
        ClickableSpan[] after = result.getSpans(0, result.length(), ClickableSpan.class);
        assertEquals(1, after.length);
        assertSame(before[0], after[0]);
    }
    @Test public void recycledPlaceholderDoesNotOpenPreviousImage() {
        View view = new View(app());
        ImagePreview.bind(view, "/photo.png");
        assertTrue(view.isClickable());
        ImagePreview.bind(view, null);
        assertFalse(view.isClickable());
        view.performClick();
        assertNull(shadowOf(app()).getNextStartedActivity());
        ImagePreview.open(app(), "file:///private/image.png");
        assertNull(shadowOf(app()).getNextStartedActivity());
    }
}
