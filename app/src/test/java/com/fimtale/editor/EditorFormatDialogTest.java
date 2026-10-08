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
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class EditorFormatDialogTest {
    public static class HostActivity extends AppCompatActivity implements EditorFormatDialog.Host {
        BbCodeEditText body; int version = 1;
        @Override public void onCreate(Bundle state) {
            setTheme(R.style.Theme_Fimtale); super.onCreate(state);
            body = new BbCodeEditText(this, null); body.setId(R.id.editorBody);
            body.setLinkClickListener((editor, link) -> EditorFormatDialog.editLink(this, link).showNow(getSupportFragmentManager(), EditorFormatDialog.TAG));
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
    private void tapLink(int offset, long duration, boolean drag) {
        activity.body.setSourceVisible(activity.body.isSourceVisible());
        activity.body.measure(android.view.View.MeasureSpec.makeMeasureSpec(500, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY));
        activity.body.layout(0, 0, 500, 900);
        android.text.Layout layout = activity.body.getLayout();
        int line = layout.getLineForOffset(offset);
        float x = activity.body.getTotalPaddingLeft() + layout.getPrimaryHorizontal(offset) + 2;
        float y = activity.body.getTotalPaddingTop() + layout.getLineBaseline(line) - 2;
        for (int action : drag ? new int[]{0, 2, 1} : new int[]{0, 1}) {
            android.view.MotionEvent event = android.view.MotionEvent.obtain(0, action == 0 ? 0 : duration, action, x, y + (action != 0 && drag ? 100 : 0), 0);
            activity.body.dispatchTouchEvent(event); event.recycle();
        }
        shadowOf(Looper.getMainLooper()).idle();
    }
    @Test public void tappingExistingLinkPrefillsTheWholeLinkAndUpdatesOnlyThatLink() {
        String original = "前[url=\"https://example.org/old?a=1&amp;b=2\"][b]完整文字[/b][/url]后 [url]https://second.org/[/url]";
        activity.body.setText(original);
        tapLink(original.indexOf("整文字"), 20, false);
        assertNotNull(dialog());
        assertEquals("https://example.org/old?a=1&b=2", input(R.id.editorFormatInput0).getText().toString());
        assertEquals("完整文字", input(R.id.editorFormatInput1).getText().toString());
        assertEquals("编辑链接", ((android.widget.TextView) dialog().requireDialog().findViewById(R.id.editorFormatTitle)).getText().toString());
        input(R.id.editorFormatInput0).setText("https://example.org/new"); insert();
        assertEquals("前[url=\"https://example.org/new\"][b]完整文字[/b][/url]后 [url]https://second.org/[/url]", activity.body.getText().toString());
    }
    @Test public void bareUrlCanBeEditedAndLinkRemovalKeepsItsText() {
        activity.body.setText("[url]https://example.org/[/url]");
        tapLink(8, 20, false);
        assertEquals("https://example.org/", input(R.id.editorFormatInput0).getText().toString());
        assertEquals("https://example.org/", input(R.id.editorFormatInput1).getText().toString());
        input(R.id.editorFormatInput1).setText("新[标题]"); insert();
        assertEquals("[url=\"https://example.org/\"]新&#91;标题&#93;[/url]", activity.body.getText().toString());
        String source = activity.body.getText().toString();
        tapLink(source.indexOf("新"), 20, false);
        dialog().requireDialog().findViewById(R.id.editorRemoveLink).performClick(); shadowOf(Looper.getMainLooper()).idle();
        assertEquals("新&#91;标题&#93;", activity.body.getText().toString());
    }
    @Test public void scrollingLongPressAndSourceModeDoNotOpenLinkEditing() {
        String source = "[url=https://example.org/]链接文字[/url]";
        activity.body.setText(source);
        tapLink(source.indexOf("链接"), 20, true); assertNull(dialog());
        tapLink(source.indexOf("链接"), android.view.ViewConfiguration.getLongPressTimeout() + 20L, false); assertNull(dialog());
        activity.body.setSourceVisible(true);
        tapLink(source.indexOf("链接"), 20, false); assertNull(dialog());
        assertEquals(source, activity.body.getText().toString());
    }
    @Test public void existingLinkEditSurvivesRotationAndRejectsAStaleRange() {
        activity.body.setText("[url=https://example.org/]文字[/url]");
        tapLink(activity.body.getText().toString().indexOf("文字"), 20, false);
        input(R.id.editorFormatInput1).setText("新文字");
        Configuration next = new Configuration(activity.getResources().getConfiguration()); next.orientation = Configuration.ORIENTATION_LANDSCAPE;
        controller.configurationChange(next); activity = controller.get(); shadowOf(Looper.getMainLooper()).idle();
        assertEquals("新文字", input(R.id.editorFormatInput1).getText().toString());
        activity.body.setText("[url=https://example.org/]已经改变[/url]");
        insert();
        assertEquals("[url=https://example.org/]已经改变[/url]", activity.body.getText().toString());
        assertNotNull(dialog());
    }
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
        dialog().requireDialog().cancel(); shadowOf(Looper.getMainLooper()).idle();
        assertNull(dialog());
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
    @Test public void tableGridSelectionUpdatesDimensionsAndSurvivesRotation() {
        open(EditorFormat.TABLE);
        TableSizePicker picker = dialog().requireDialog().findViewById(R.id.editorTableSizePicker);
        picker.measure(android.view.View.MeasureSpec.makeMeasureSpec(300, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        picker.layout(0, 0, 300, 600);
        for (int action : new int[]{android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE, android.view.MotionEvent.ACTION_UP}) {
            android.view.MotionEvent event = android.view.MotionEvent.obtain(0, 10, action, 105, 135, 0);
            picker.dispatchTouchEvent(event); event.recycle();
        }
        assertEquals("5", input(R.id.editorFormatInput0).getText().toString());
        assertEquals("4", input(R.id.editorFormatInput1).getText().toString());
        Configuration next = new Configuration(activity.getResources().getConfiguration()); next.orientation = Configuration.ORIENTATION_LANDSCAPE;
        controller.configurationChange(next); activity = controller.get(); shadowOf(Looper.getMainLooper()).idle();
        assertEquals("5", input(R.id.editorFormatInput0).getText().toString());
        assertEquals("4", input(R.id.editorFormatInput1).getText().toString());
        insert();
        EditorTable table = EditorTable.parse(activity.body.getText().toString().substring(activity.body.getText().toString().indexOf("[table]")));
        assertEquals(5, table.rows); assertEquals(4, table.columns);
    }
}
