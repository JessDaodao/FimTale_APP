package com.fimtale;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowInsets;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Checks compositor output and safe button placement on a device, without loading or saving works. */
@RunWith(AndroidJUnit4.class)
public class NavigationBarTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

    @Test public void pageAndBottomSheetBlendIntoNavigationAreaInBothThemes() throws Exception {
        int originalMode = AppCompatDelegate.getDefaultNightMode();
        try {
            for (int mode : new int[]{AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES}) {
                instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(mode));
                try (ActivityScenario<AboutActivity> scenario = ActivityScenario.launch(AboutActivity.class)) {
                    AtomicInteger background = new AtomicInteger();
                    AtomicInteger navigationInset = new AtomicInteger();
                    scenario.onActivity(activity -> {
                        TypedValue value = new TypedValue();
                        activity.getTheme().resolveAttribute(android.R.attr.colorBackground, value, true);
                        background.set(value.data);
                        Insets insets = activity.getWindow().getDecorView().getRootWindowInsets()
                                .getInsets(WindowInsets.Type.navigationBars());
                        navigationInset.set(insets.bottom);
                    });
                    assertTrue("Run with a visible bottom navigation bar", navigationInset.get() > 0);
                    Bitmap page = screenshot("page-" + mode);
                    assertColorClose("Page navigation background", background.get(),
                            page.getPixel(page.getWidth() / 10, page.getHeight() - navigationInset.get() / 2));
                    assertNavigationControlsContrast(page, navigationInset.get(), background.get());
                    page.recycle();

                    AtomicReference<BottomSheetDialog> shown = new AtomicReference<>();
                    scenario.onActivity(activity -> {
                        BottomSheetDialog dialog = new BottomSheetDialog(activity);
                        dialog.setContentView(R.layout.dialog_editor_metadata);
                        dialog.show();
                        dialog.getBehavior().setSkipCollapsed(true);
                        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
                        shown.set(dialog);
                    });
                    try {
                        instrumentation.waitForIdleSync();
                        scenario.onActivity(activity -> {
                            androidx.core.widget.NestedScrollView content = shown.get().findViewById(R.id.editorMetadataSheet);
                            content.fullScroll(View.FOCUS_DOWN);
                        });
                        Bitmap sheet = screenshot("sheet-" + mode);
                        int x = sheet.getWidth() / 10;
                        int surface = sheet.getPixel(x, sheet.getHeight() - navigationInset.get() - 1);
                        assertColorClose("Sheet background extends under navigation controls", surface,
                                sheet.getPixel(x, sheet.getHeight() - navigationInset.get() / 2));
                        assertNavigationControlsContrast(sheet, navigationInset.get(), surface);
                        int navigationTop = sheet.getHeight() - navigationInset.get();
                        scenario.onActivity(activity -> {
                            View done = shown.get().findViewById(R.id.editorMetadataDone);
                            int[] location = new int[2]; done.getLocationOnScreen(location);
                            assertTrue("Bottom action stays above the navigation area",
                                    location[1] + done.getHeight() <= navigationTop);
                        });
                        sheet.recycle();
                    } finally {
                        instrumentation.runOnMainSync(() -> shown.get().dismiss());
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(originalMode));
        }
    }

    private Bitmap screenshot(String name) throws Exception {
        instrumentation.waitForIdleSync();
        SystemClock.sleep(800); // Include the system navigation-bar and bottom-sheet animations.
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File directory = new File(instrumentation.getTargetContext().getFilesDir(), "navigation-bar-checks");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        return bitmap;
    }

    private static void assertColorClose(String message, int expected, int actual) {
        assertTrue(message + ": expected " + Integer.toHexString(expected) + ", got " + Integer.toHexString(actual),
                Math.abs(Color.red(expected) - Color.red(actual)) <= 8
                        && Math.abs(Color.green(expected) - Color.green(actual)) <= 8
                        && Math.abs(Color.blue(expected) - Color.blue(actual)) <= 8);
    }

    private static void assertNavigationControlsContrast(Bitmap image, int bottomInset, int background) {
        int contrastingPixels = 0;
        for (int y = image.getHeight() - bottomInset; y < image.getHeight(); y++) {
            for (int x = image.getWidth() / 4; x < image.getWidth() * 3 / 4; x++) {
                if (androidx.core.graphics.ColorUtils.calculateContrast(image.getPixel(x, y), background) >= 3)
                    contrastingPixels++;
            }
        }
        assertTrue("System navigation controls remain visible", contrastingPixels > 20);
    }
}
