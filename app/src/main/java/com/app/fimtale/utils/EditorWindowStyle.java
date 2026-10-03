package com.app.fimtale.utils;

import android.util.TypedValue;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;

/** Keep the system status bar continuous with the page in both day and night themes. */
public final class EditorWindowStyle {
    private EditorWindowStyle() {}
    public static void apply(AppCompatActivity activity) {
        TypedValue value = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.colorBackground, value, true);
        int background = value.data;
        activity.getWindow().setStatusBarColor(background);
        activity.getWindow().setStatusBarContrastEnforced(false);
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView())
                .setAppearanceLightStatusBars(ColorUtils.calculateLuminance(background) > 0.5);
    }
}
