package com.fimtale;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import com.fimtale.utils.GravitySensorHelper;
import java.util.WeakHashMap;

public class FimTaleApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private final WeakHashMap<Activity, GravitySensorHelper> gravityHelpers = new WeakHashMap<>();
    private boolean isGravityModeEnabled = false;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private static FimTaleApplication instance;

    public static FimTaleApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        registerActivityLifecycleCallbacks(this);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        initTheme(prefs);
        isGravityModeEnabled = prefs.getBoolean("gravity_mode", false);

        preferenceChangeListener = (sharedPreferences, key) -> {
            if ("gravity_mode".equals(key)) {
                isGravityModeEnabled = sharedPreferences.getBoolean(key, false);
                updateAllHelpers();
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
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

    private void updateAllHelpers() {
        for (Activity activity : gravityHelpers.keySet()) {
            GravitySensorHelper helper = gravityHelpers.get(activity);
            if (helper != null) {
                if (isGravityModeEnabled) {
                    helper.start();
                } else {
                    helper.stop();
                }
            }
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        GravitySensorHelper helper = new GravitySensorHelper(activity, activity.findViewById(android.R.id.content));
        gravityHelpers.put(activity, helper);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        GravitySensorHelper helper = gravityHelpers.get(activity);
        if (helper != null && isGravityModeEnabled) {
            helper.start();
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        GravitySensorHelper helper = gravityHelpers.get(activity);
        if (helper != null) {
            helper.stop();
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        gravityHelpers.remove(activity);
    }
}
