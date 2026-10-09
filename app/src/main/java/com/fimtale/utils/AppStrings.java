package com.fimtale.utils;

import android.content.Context;
import androidx.annotation.StringRes;

/** Application resources for diagnostic and model helpers that have no view context. */
public final class AppStrings {
    private static volatile Context application;

    private AppStrings() {}

    public static void initialize(Context context) {
        application = context.getApplicationContext();
    }

    public static String[] array(@androidx.annotation.ArrayRes int id) {
        return application.getResources().getStringArray(id);
    }

    public static String get(@StringRes int id, Object... arguments) {
        return arguments.length == 0 ? application.getString(id) : application.getString(id, arguments);
    }
}
