package com.fimtale.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;

/** Creates MDI drawables for ImageView, MaterialButton, and menu icons. */
public final class MdiIcons {
    private MdiIcons() {}

    /** A fresh 24dp icon using the current Activity's surface foreground color. */
    @NonNull
    public static IconicsDrawable drawable(@NonNull Context context, @NonNull IIcon icon) {
        int color = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorOnSurface, Color.BLACK);
        return drawable(context, icon, 24, color);
    }

    @NonNull
    public static IconicsDrawable drawable(@NonNull Context context, @NonNull IIcon icon,
            @IntRange(from = 1) int sizeDp, @ColorInt int color) {
        if (sizeDp < 1) throw new IllegalArgumentException("Icon size must be positive");

        // Iconics keeps its initialization context globally; never give it an Activity.
        IconicsDrawable drawable = new IconicsDrawable(context.getApplicationContext(), icon);
        int sizePx = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                sizeDp, context.getResources().getDisplayMetrics()));
        drawable.setSizeXPx(sizePx);
        drawable.setSizeYPx(sizePx);
        drawable.setColorList(ColorStateList.valueOf(color));
        return drawable;
    }
}
