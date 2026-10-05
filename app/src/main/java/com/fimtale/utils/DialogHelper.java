package com.fimtale.utils;
import android.content.Context;
import android.content.Intent;
import com.fimtale.LoginActivity;
import com.fimtale.SiteActivity;
public final class DialogHelper {
    private DialogHelper() {}
    public static void openLogin(Context context) { context.startActivity(new Intent(context, LoginActivity.class)); }
    public static void openSite(Context context, String path) {
        context.startActivity(new Intent(context, SiteActivity.class).putExtra(SiteActivity.EXTRA_PATH, path));
    }
}
