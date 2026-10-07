package com.fimtale.editor;

import android.app.Application;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Looper;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import com.fimtale.R;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import com.fimtale.NativeRobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(NativeRobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class EditorFormatDialogTest {
    public static class HostActivity extends AppCompatActivity implements EditorFormatDialog.Host {
        BbCodeEditText body; int version = 1;
        @Override public void onCreate(Bundle state) {
            setTheme(R.style.Theme_Fimtale); super.onCreate(state);
            body = new BbCodeEditText(this, null); body.setId(R.id.editorBody);
            setContentView(body); body.setText("前正文🐴后"); body.setSelection(1, 5);
        }
        @Override public BbCodeEditText formatBody() { return body; }
        @Override public int formatVersion() { return version; }
        @Override public boolean canInsertFormat() { return true; }
        @Override public void applyFormat(BbCodeInsertion.Edit edit) {
            body.getText().replace(edit.start, edit.end, edit.replacement); body.setSelection(edit.selectionStart, edit.selectionEnd);
        }
    }
    private ActivityController<HostActivity> controller;
    private HostActivity activity;
    @Before public void setup() { controller = Robolectric.buildActivity(HostActivity.class).setup(); activity = controller.get(); }
    @After public void cleanup() { controller.pause().stop().destroy(); }
    private EditorFormatDialog dialog() {
        return (EditorFormatDialog) activity.getSupportFragmentManager().findFragmentByTag(EditorFormatDialog.TAG);
    }
    private void open(EditorFormat format) {
        EditorFormatDialog.create(activity).showNow(activity.getSupportFragmentManager(), EditorFormatDialog.TAG);
        dialog().requireDialog().findViewById(R.id.editorFormatContent).findViewWithTag(format.name()).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }
    private EditText input(int id) { return dialog().requireDialog().findViewById(id); }
    private void insert() { dialog().requireDialog().findViewById(R.id.editorFormatInsert).performClick(); shadowOf(Looper.getMainLooper()).idle(); }
    @Test public void linkFormKeepsOriginalSelectionWhenInputFocusAndConfigurationChange() {
        open(EditorFormat.LINK); input(R.id.editorFormatInput0).setText("https://example.org/");
        assertEquals("正文🐴", input(R.id.editorFormatInput1).getText().toString());
        Configuration next = new Configuration(activity.getResources().getConfiguration()); next.orientation = Configuration.ORIENTATION_LANDSCAPE;
        controller.configurationChange(next); activity = controller.get(); shadowOf(Looper.getMainLooper()).idle();
        assertEquals("https://example.org/", input(R.id.editorFormatInput0).getText().toString());
        assertEquals("正文🐴", input(R.id.editorFormatInput1).getText().toString());
        insert();
        assertEquals("前[url=\"https://example.org/\"]正文🐴[/url]后", activity.body.getText().toString());
        assertEquals(activity.body.length() - 1, activity.body.getSelectionStart());
    }
    @Test public void invalidInputAndCancellationNeverAlterTheDocument() {
        open(EditorFormat.IMAGE); input(R.id.editorFormatInput0).setText("not a url"); insert();
        assertEquals("前正文🐴后", activity.body.getText().toString());
        assertEquals(android.view.View.VISIBLE, dialog().requireDialog().findViewById(R.id.editorFormatError).getVisibility());
        dialog().requireDialog().findViewById(R.id.editorFormatClose).performClick(); shadowOf(Looper.getMainLooper()).idle();
        assertEquals("前正文🐴后", activity.body.getText().toString());
    }
    @Test public void sourceReplacementInvalidatesOldInsertionRanges() {
        open(EditorFormat.COLLAPSE); activity.version++; activity.body.setText("新的正文");
        insert(); assertEquals("新的正文", activity.body.getText().toString()); assertNotNull(dialog());
    }
    @Test public void returningToTheActivityDoesNotEraseFormDrafts() {
        open(EditorFormat.HASH); input(R.id.editorFormatInput0).setText("小马同人");
        controller.pause().stop().start().resume(); shadowOf(Looper.getMainLooper()).idle();
        assertEquals("小马同人", input(R.id.editorFormatInput0).getText().toString());
        insert(); assertEquals("前[hash]小马同人[/hash]后", activity.body.getText().toString());
    }
    @Test public void tableFormProducesEditableCellsAndImmediateFormatsKeepSelection() {
        open(EditorFormat.TABLE); input(R.id.editorFormatInput0).setText("2"); input(R.id.editorFormatInput1).setText("2"); insert();
        assertTrue(activity.body.getText().toString().contains("[th]正文🐴[/th]"));
        assertEquals("正文🐴", activity.body.getText().subSequence(activity.body.getSelectionStart(), activity.body.getSelectionEnd()).toString());
        open(EditorFormat.STRIKE);
        assertEquals("正文🐴", activity.body.getText().subSequence(activity.body.getSelectionStart(), activity.body.getSelectionEnd()).toString());
        assertTrue(activity.body.getText().toString().contains("[th][s]正文🐴[/s][/th]"));
    }
}
