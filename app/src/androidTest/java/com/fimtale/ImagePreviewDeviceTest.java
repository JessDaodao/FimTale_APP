package com.fimtale;

import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ImagePreviewDeviceTest {
    private PhotoView find(View view) {
        if (view instanceof PhotoView) return (PhotoView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                PhotoView result = find(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }
    @Test public void fullScreenViewerSupportsZoomAndSafeCloseControl() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), ImagePreviewActivity.class)
                .putExtra("image_url", "https://preview.invalid/fixture.png");
        try (ActivityScenario<ImagePreviewActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                PhotoView photo = find(activity.getWindow().getDecorView());
                assertNotNull(photo);
                Glide.with(activity).clear(photo);
                photo.setImageBitmap(Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888));
                photo.setScale(2f, false);
                assertEquals(2f, photo.getScale(), .01f);
                photo.setScale(1f, false);
                assertEquals(1f, photo.getScale(), .01f);
                java.util.ArrayList<View> matches = new java.util.ArrayList<>();
                activity.getWindow().getDecorView().findViewsWithText(matches, "关闭", View.FIND_VIEWS_WITH_TEXT);
                assertEquals(1, matches.size());
                assertTrue(matches.get(0).isShown());
                matches.get(0).performClick();
                assertTrue(activity.isFinishing());
            });
        }
    }
}
