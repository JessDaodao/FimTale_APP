package com.fimtale;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.view.SupportMenuInflater;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fimtale.ui.icons.MdiButton;
import com.fimtale.ui.icons.MdiImageView;
import com.fimtale.ui.icons.MdiImageButton;
import com.fimtale.ui.icons.MdiShapeableImageView;
import com.fimtale.ui.icons.MdiTextInputLayout;
import com.fimtale.ui.icons.MdiTextView;
import com.fimtale.ui.icons.MdiToolbar;
import com.fimtale.utils.MdiIcons;
import com.google.android.material.textfield.TextInputLayout;
import com.mikepenz.iconics.IconicsDrawable;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Exercises layout inflation and font rendering without activities or network requests. */
@RunWith(AndroidJUnit4.class)
public class MdiLayoutTest {
    private static final int[] LAYOUTS = {
            R.layout.activity_about, R.layout.activity_account_sessions,
            R.layout.activity_content_filters, R.layout.activity_drafts, R.layout.activity_editor,
            R.layout.activity_favorites, R.layout.activity_history, R.layout.activity_login,
            R.layout.activity_main, R.layout.activity_reader, R.layout.activity_search,
            R.layout.activity_settings, R.layout.activity_tag_articles, R.layout.activity_tag_list,
            R.layout.activity_topic_detail, R.layout.activity_user_detail,
            R.layout.dialog_editor_metadata, R.layout.editor_metadata, R.layout.fragment_profile,
            R.layout.grid_item_topic, R.layout.item_editor_draft, R.layout.item_home_header,
            R.layout.item_reader_comment_page, R.layout.item_search_history,
            R.layout.layout_share_image, R.layout.view_work_comments
    };

    @Test public void migratedLayoutsInflateAndRenderInBothThemes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int mode : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
                Context context = themedContext(mode);
                for (int layout : LAYOUTS) {
                    View root = LayoutInflater.from(context).inflate(layout, null, false);
                    assertTrue(context.getResources().getResourceEntryName(layout), checkIcons(root) > 0);
                }
            }
        });
    }

    @Test public void menusAndBatteryStatesRenderFontGlyphs() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context context = themedContext(Configuration.UI_MODE_NIGHT_NO);
            int[] menus = {R.menu.article_menu, R.menu.bottom_nav_menu, R.menu.editor_menu,
                    R.menu.home_menu, R.menu.menu_reader, R.menu.menu_search, R.menu.menu_tag_articles,
                    R.menu.menu_tag_list, R.menu.menu_topic_detail, R.menu.profile_menu};
            for (int resource : menus) {
                Menu menu = new PopupMenu(context, new View(context)).getMenu();
                MdiIcons.inflateMenu(context, new SupportMenuInflater(context), resource, menu);
                int count = 0;
                for (int i = 0; i < menu.size(); i++) {
                    if (menu.getItem(i).getIcon() != null) {
                        assertGlyph(menu.getItem(i).getIcon());
                        count++;
                    }
                }
                assertTrue(context.getResources().getResourceEntryName(resource), count > 0);
            }
            for (int percent = 0; percent <= 100; percent++) {
                assertGlyph(MdiIcons.battery(context, percent, false));
                assertGlyph(MdiIcons.battery(context, percent, true));
            }
            assertTrue(MdiIcons.drawable(context, "arrow-left").isAutoMirrored());
        });
    }

    private static Context themedContext(int nightMode) {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(target.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        return new ContextThemeWrapper(target.createConfigurationContext(config), R.style.Theme_Fimtale);
    }

    private static int checkIcons(View view) {
        int count = 0;
        if (view instanceof MdiImageView || view instanceof MdiImageButton || view instanceof MdiShapeableImageView) {
            assertGlyph(((android.widget.ImageView) view).getDrawable());
            count++;
        } else if (view instanceof MdiButton) {
            assertGlyph(((MdiButton) view).getIcon());
            count++;
        } else if (view instanceof MdiTextView) {
            for (Drawable icon : ((MdiTextView) view).getCompoundDrawablesRelative()) {
                if (icon != null) { assertGlyph(icon); count++; }
            }
        } else if (view instanceof MdiToolbar) {
            MdiToolbar toolbar = (MdiToolbar) view;
            assertGlyph(toolbar.getOverflowIcon());
            if (toolbar.getNavigationIcon() != null) assertGlyph(toolbar.getNavigationIcon());
            if (toolbar.getMenu().findItem(R.id.action_editor_metadata) != null) {
                assertGlyph(toolbar.getMenu().findItem(R.id.action_editor_metadata).getIcon());
            }
            count++;
        } else if (view instanceof MdiTextInputLayout) {
            MdiTextInputLayout input = (MdiTextInputLayout) view;
            assertGlyph(input.getErrorIconDrawable());
            if (input.getEndIconMode() == TextInputLayout.END_ICON_CLEAR_TEXT) {
                assertGlyph(input.getEndIconDrawable());
            }
            count++;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) count += checkIcons(group.getChildAt(i));
        }
        return count;
    }

    private static void assertGlyph(Drawable drawable) {
        assertNotNull("Missing icon", drawable);
        assertTrue("Expected a font drawable: " + drawable, DrawableCompat.unwrap(drawable) instanceof IconicsDrawable);
        Rect previousBounds = new Rect(drawable.getBounds());
        Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        try {
            drawable.setBounds(0, 0, 32, 32);
            drawable.draw(new Canvas(bitmap));
            int[] pixels = new int[32 * 32];
            bitmap.getPixels(pixels, 0, 32, 0, 0, 32, 32);
            boolean visible = false;
            for (int pixel : pixels) if ((pixel >>> 24) != 0) { visible = true; break; }
            assertTrue("Font icon rendered an empty bitmap", visible);
        } finally {
            drawable.setBounds(previousBounds);
            bitmap.recycle();
        }
    }
}
