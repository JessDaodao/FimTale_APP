package com.app.fimtale.utils;
import android.content.Context;
import android.content.Intent;
import com.app.fimtale.LoginActivity;
public final class DialogHelper {
    private DialogHelper() {}
    public static void openLogin(Context context) { context.startActivity(new Intent(context, LoginActivity.class)); }
    public static void openSite(Context context, String path) {
        context.startActivity(new Intent(context, LoginActivity.class).putExtra(LoginActivity.EXTRA_PATH, path));
    }
}
