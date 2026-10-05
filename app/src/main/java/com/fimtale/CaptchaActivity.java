package com.fimtale;

import com.fimtale.utils.MdiIcons;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fimtale.network.SiteUrls;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A real captcha widget on the configured site origin. No native JavaScript bridge. */
public class CaptchaActivity extends AppCompatActivity {
    public static final String EXTRA_TOKEN = "captcha_token", EXTRA_PROVIDER = "captcha_provider";
    private WebView web;
    private Spinner providers;
    private boolean loaded;
    private int generation;
    private final List<String> ids = new ArrayList<>(), keys = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();
    private static class Result { String token, provider; }
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (isFinishing() || web == null) return;
            int current = generation;
            if (loaded) web.evaluateJavascript("JSON.stringify(window.__ftCaptchaResult || null)", value -> {
                if (current != generation || isFinishing() || isDestroyed()) return;
                try {
                    Result result = gson.fromJson(gson.fromJson(value, String.class), Result.class);
                    if (result != null && result.token != null && !result.token.isEmpty() && result.token.length() <= 16384
                            && ids.get(providers.getSelectedItemPosition()).equals(result.provider)) {
                        setResult(RESULT_OK, new Intent().putExtra(EXTRA_TOKEN, result.token).putExtra(EXTRA_PROVIDER, result.provider));
                        finish();
                    }
                } catch (RuntimeException ignored) {}
            });
            handler.postDelayed(this, 400);
        }
    };
    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        MaterialToolbar toolbar = new com.fimtale.ui.icons.MdiToolbar(this); toolbar.setTitle("发表验证");
        toolbar.setNavigationIcon(MdiIcons.drawable(this, "arrow-left")); toolbar.setNavigationOnClickListener(v -> finish());
        layout.addView(toolbar);
        List<String> labels = new ArrayList<>();
        addProvider(labels, "Cloudflare", "turnstile", BuildConfig.TURNSTILE_SITE_KEY);
        addProvider(labels, "hCaptcha", "hcaptcha", BuildConfig.HCAPTCHA_SITE_KEY);
        addProvider(labels, "腾讯验证码", "tencent", BuildConfig.TENCENT_CAPTCHA_APP_ID);
        if (ids.isEmpty()) { Toast.makeText(this, "当前构建未配置验证码", Toast.LENGTH_LONG).show(); finish(); return; }
        LinearLayout choices = new LinearLayout(this);
        providers = new Spinner(this);
        providers.setContentDescription("验证方式");
        providers.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        choices.addView(providers, new LinearLayout.LayoutParams(0, (int) (56 * getResources().getDisplayMetrics().density), 1));
        MaterialButton retry = new MaterialButton(this); retry.setText("重试"); retry.setOnClickListener(v -> loadWidget()); choices.addView(retry);
        layout.addView(choices);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true); web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false); web.getSettings().setAllowContentAccess(false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Widgets may navigate their own frames. The top document is always our bundled page.
                return request.isForMainFrame();
            }
            @Override public void onPageFinished(WebView view, String url) { loaded = true; }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) Toast.makeText(CaptchaActivity.this, "验证加载失败，可重试或切换验证方式", Toast.LENGTH_LONG).show();
            }
        });
        layout.addView(web, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(layout);
        providers.setSelection(state == null ? 0 : Math.min(state.getInt("provider"), ids.size() - 1));
        providers.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) { loadWidget(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }
    private void addProvider(List<String> labels, String label, String id, String key) {
        if (key.isEmpty()) return;
        labels.add(label); ids.add(id); keys.add(key);
    }
    private void loadWidget() {
        loaded = false; generation++;
        try (InputStream in = getAssets().open("editor_captcha.html")) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] bytes = new byte[4096]; int count;
            while ((count = in.read(bytes)) != -1) buffer.write(bytes, 0, count);
            String html = new String(buffer.toByteArray(), StandardCharsets.UTF_8);
            Map<String, String> config = new LinkedHashMap<>();
            config.put("provider", ids.get(providers.getSelectedItemPosition())); config.put("key", keys.get(providers.getSelectedItemPosition()));
            web.loadDataWithBaseURL(SiteUrls.SITE + "/app-captcha/", html.replace("/*CONFIG*/", gson.toJson(config)), "text/html", "UTF-8", null);
        } catch (Exception e) { Toast.makeText(this, "无法加载验证页面", Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (providers != null) out.putInt("provider", providers.getSelectedItemPosition()); super.onSaveInstanceState(out);
    }
    @Override protected void onResume() { super.onResume(); handler.post(poll); }
    @Override protected void onPause() { handler.removeCallbacks(poll); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacks(poll); if (web != null) web.destroy(); super.onDestroy(); }
}
