package com.fimtale.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;
import com.fimtale.R;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial;

/** Creates MDI drawables for ImageView, MaterialButton, and menu icons. */
public final class MdiIcons {
    private MdiIcons() {}

    /** Accepts MDI names such as "book-open-page-variant" or "mdi-account". */
    @NonNull
    public static IconicsDrawable drawable(@NonNull Context context, @NonNull String name) {
        String key = name.startsWith("mdi-") ? name.substring(4) : name;
        key = key.replace('-', '_');
        if (!key.startsWith("cmd_")) key = "cmd_" + key;
        IconicsDrawable drawable = drawable(context, CommunityMaterial.INSTANCE.getIcon(key));
        if (key.equals("cmd_arrow_left") || key.equals("cmd_arrow_right")) {
            drawable.setAutoMirroredCompat(true);
        }
        return drawable;
    }

    public static void inflateMenu(Context context, MenuInflater inflater, int menuRes, Menu menu) {
        inflater.inflate(menuRes, menu);
        applyMenu(context, menu, menuRes);
    }

    /** Menu XML declares actions; their font icons are assigned here after inflation. */
    public static void applyMenu(Context context, Menu menu, int menuRes) {
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            int id = item.getItemId();
            String name = null;
            if (id == R.id.nav_home) name = "home";
            else if (id == R.id.nav_article) name = "book-open-page-variant";
            else if (id == R.id.nav_profile) name = "account";
            else if (id == R.id.action_filter) name = menuRes == R.menu.menu_tag_list ? "magnify" : "filter-variant";
            else if (id == R.id.action_search) name = "magnify";
            else if (id == R.id.action_publish || id == R.id.action_edit_work) name = "pencil";
            else if (id == R.id.action_editor_metadata || id == R.id.action_settings) name = "cog";
            else if (id == R.id.action_toggle_theme) name = "theme-light-dark";
            else if (id == R.id.action_more) name = "dots-vertical";
            else if (id == R.id.action_info || id == R.id.action_tag_info) name = "information";
            if (name != null) item.setIcon(drawable(context, name));
            if (item.hasSubMenu()) applyMenu(context, item.getSubMenu(), menuRes);
        }
    }

    /** MDI supplies battery levels in ten-percent steps; the adjacent text stays exact. */
    public static IconicsDrawable battery(Context context, int percent, boolean charging) {
        int level = Math.max(0, Math.min(100, percent)) / 10 * 10;
        String name;
        if (charging) name = level == 0 ? "battery-charging-outline" : "battery-charging-" + level;
        else name = level == 100 ? "battery" : level == 0 ? "battery-outline" : "battery-" + level;
        return drawable(context, name);
    }

    public static void setError(TextView field, CharSequence message) {
        IconicsDrawable icon = drawable(field.getContext(), "alert-circle");
        icon.setColorList(ColorStateList.valueOf(MaterialColors.getColor(field.getContext(),
                com.google.android.material.R.attr.colorError, Color.RED)));
        field.setError(message, message == null ? null : icon);
    }

    /** A fresh icon using the shared default size and current theme's foreground color. */
    @NonNull
    public static IconicsDrawable drawable(@NonNull Context context, @NonNull IIcon icon) {
        int color = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        return drawablePx(context, icon, context.getResources().getDimensionPixelSize(R.dimen.mdi_icon_size), color);
    }

    @NonNull
    public static IconicsDrawable drawable(@NonNull Context context, @NonNull IIcon icon,
            @IntRange(from = 1) int sizeDp, @ColorInt int color) {
        if (sizeDp < 1) throw new IllegalArgumentException("Icon size must be positive");

        int sizePx = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                sizeDp, context.getResources().getDisplayMetrics()));
        return drawablePx(context, icon, sizePx, color);
    }

    private static IconicsDrawable drawablePx(Context context, IIcon icon, int sizePx, int color) {
        // Iconics keeps its initialization context globally; never give it an Activity.
        IconicsDrawable drawable = new IconicsDrawable(context.getApplicationContext(), icon);
        drawable.setSizeXPx(sizePx);
        drawable.setSizeYPx(sizePx);
        drawable.setColorList(ColorStateList.valueOf(color));
        return drawable;
    }
}
