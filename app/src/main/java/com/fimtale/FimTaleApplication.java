package com.fimtale;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import com.mikepenz.iconics.Iconics;
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial;

public class FimTaleApplication extends Application {

    private static FimTaleApplication instance;
    private com.fimtale.crash.CrashFeedback crashFeedback;

    public com.fimtale.crash.CrashFeedback getCrashFeedback() { return crashFeedback; }

    public static FimTaleApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        crashFeedback = com.fimtale.crash.CrashFeedback.install(this);
        Iconics.registerFont(CommunityMaterial.INSTANCE);
        Iconics.init(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        initTheme(prefs);
    }

    private void initTheme(SharedPreferences prefs) {
        boolean followSystem = prefs.getBoolean("theme_follow_system", true);
        if (followSystem) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            boolean manualDarkMode = prefs.getBoolean("manual_dark_mode", false);
            if (manualDarkMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        }
    }
}
