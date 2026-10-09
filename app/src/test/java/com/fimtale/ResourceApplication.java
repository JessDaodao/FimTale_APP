package com.fimtale;

import android.app.Application;
import com.fimtale.utils.AppStrings;

/** Loads real string resources without starting the production crash and theme services. */
public class ResourceApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        AppStrings.initialize(this);
    }
}
