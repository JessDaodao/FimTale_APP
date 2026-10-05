package com.fimtale.editor;

import android.content.Context;
import com.fimtale.BuildConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Same preference and randomized, non-repeating rounds as ft-front/captcha-providers.ts. */
public final class CaptchaProviders {
    private CaptchaProviders() {}
    private static final String PREFERENCES = "fimtale_captcha", PREFERRED = "ft-captcha-provider";

    public static Map<String, String> configured() {
        Map<String, String> keys = new LinkedHashMap<>();
        if (!BuildConfig.TURNSTILE_SITE_KEY.isEmpty()) keys.put("turnstile", BuildConfig.TURNSTILE_SITE_KEY);
        if (!BuildConfig.HCAPTCHA_SITE_KEY.isEmpty()) keys.put("hcaptcha", BuildConfig.HCAPTCHA_SITE_KEY);
        if (!BuildConfig.TENCENT_CAPTCHA_APP_ID.isEmpty()) keys.put("tencent", BuildConfig.TENCENT_CAPTCHA_APP_ID);
        return keys;
    }

    public static String preferred(Context context, List<String> available) {
        String saved = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(PREFERRED, "turnstile");
        return available.contains(saved) ? saved : available.isEmpty() ? null : available.get(0);
    }

    public static void rememberSuccess(Context context, String provider) {
        if (configured().containsKey(provider))
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(PREFERRED, provider).apply();
    }

    public static String roll(List<String> available, List<String> tried, Random random) {
        if (available.isEmpty()) return null;
        List<String> pool = new ArrayList<>(available);
        pool.removeAll(tried);
        if (pool.isEmpty()) { tried.clear(); pool.addAll(available); }
        String next = pool.get(random.nextInt(pool.size()));
        tried.add(next);
        return next;
    }
}
