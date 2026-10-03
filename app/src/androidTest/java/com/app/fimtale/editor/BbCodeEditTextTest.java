package com.app.fimtale.editor;

import android.content.Context;
import android.text.Editable;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.text.style.StyleSpan;
import android.view.ContextThemeWrapper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Compiled locally; running this suite requires a separately authorized Android test target. */
@RunWith(AndroidJUnit4.class)
public class BbCodeEditTextTest {
    private BbCodeEditText editor() {
        Context context = new ContextThemeWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar);
        return new BbCodeEditText(context, null);
    }
    @Test public void renderingPreservesRawTextSelectionAndComposingSpans() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            BbCodeEditText editor = editor();
            String source = "[b]中文🐴[/b]\n输入中";
            editor.setText(source); editor.setSelection(source.length());
            Editable text = editor.getText(); Object composing = new Object();
            text.setSpan(composing, source.indexOf("输入中"), source.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_COMPOSING);
            editor.setSourceVisible(false);
            assertEquals(source, text.toString()); assertEquals(source.length(), editor.getSelectionStart());
            assertEquals(source.indexOf("输入中"), text.getSpanStart(composing));
            assertTrue((text.getSpanFlags(composing) & Spanned.SPAN_COMPOSING) != 0);
            assertEquals(1, text.getSpans(0, text.length(), StyleSpan.class).length);
            assertEquals(2, text.getSpans(0, text.length(), ReplacementSpan.class).length);
            editor.setSourceVisible(true);
            assertEquals(source, text.toString());
            assertEquals(0, text.getSpans(0, text.length(), ReplacementSpan.class).length);
        });
    }
    @Test public void incompleteOrRemovedTagsDoNotLeaveStaleStyles() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            BbCodeEditText editor = editor(); editor.setText("[b]正文[/b]"); editor.setSourceVisible(false);
            assertEquals(1, editor.getText().getSpans(0, editor.length(), StyleSpan.class).length);
            editor.getText().delete(0, 3); editor.setSourceVisible(false);
            assertEquals("正文[/b]", editor.getText().toString());
            assertEquals(0, editor.getText().getSpans(0, editor.length(), StyleSpan.class).length);
            assertEquals(0, editor.getText().getSpans(0, editor.length(), ReplacementSpan.class).length);
        });
    }
}
