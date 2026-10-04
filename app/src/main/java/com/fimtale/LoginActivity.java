package com.fimtale;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import com.fimtale.model.CurrentUser;
import com.fimtale.network.RetrofitClient;
import com.fimtale.network.SiteUrls;
import com.fimtale.utils.UserPreferences;
import com.google.android.material.appbar.MaterialToolbar;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Uses the current site's login and captcha UI, then verifies its Token with the API. */
public class LoginActivity extends AppCompatActivity {
    public static final String EXTRA_PATH = "site_path";
    private WebView webView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean verifying;
    private boolean loginMode;
    private boolean pageLoaded;
    private String lastRejectedToken;
    private Call<CurrentUser> verification;
    private final Runnable checkSession = new Runnable() {
        @Override public void run() {
            if (!isFinishing()) readSession();
            handler.postDelayed(this, 1000);
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_login);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        String path = getIntent().getStringExtra(EXTRA_PATH);
        loginMode = path == null;
        if (path == null || !path.startsWith("/") || path.startsWith("//")) path = "/user/login";
        toolbar.setTitle(loginMode ? "登录 FimTale" : "FimTale");
        webView = findViewById(R.id.login_webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        String token = UserPreferences.getToken(this);
        if (!token.isEmpty()) cookies.setCookie(SiteUrls.SITE, "ft_token=" + token + "; Path=/; Secure; SameSite=Lax");
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (SiteUrls.isSite(url)) return false;
                if (request.isForMainFrame() && ("https".equals(request.getUrl().getScheme()) || "http".equals(request.getUrl().getScheme()))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                    catch (android.content.ActivityNotFoundException ignored) {}
                }
                return request.isForMainFrame();
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                pageLoaded = false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                if (SiteUrls.isSite(url)) readSession();
            }
        });
        webView.loadUrl(SiteUrls.SITE + path);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack(); else finish();
            }
        });
    }
    private void readSession() {
        if (!pageLoaded || verifying || !SiteUrls.isSite(webView.getUrl())) return;
        String cookies = CookieManager.getInstance().getCookie(SiteUrls.SITE);
        if (cookies == null) cookies = "";
        for (String cookie : cookies.split(";")) {
            String value = cookie.trim();
            if (!value.startsWith("ft_token=")) continue;
            String token = Uri.decode(value.substring("ft_token=".length()));
            if (token.isEmpty() || token.equals(lastRejectedToken)) return;
            if (!loginMode && token.equals(UserPreferences.getToken(this))) return;
            verifying = true;
            verification = RetrofitClient.getInstance().getCurrentUser(token);
            verification.enqueue(new Callback<CurrentUser>() {
                @Override public void onResponse(Call<CurrentUser> call, Response<CurrentUser> response) {
                    verifying = false;
                    if (isFinishing() || isDestroyed()) return;
                    CurrentUser user = response.body();
                    if (response.isSuccessful() && user != null && user.id > 0) {
                        UserPreferences.saveToken(LoginActivity.this, token);
                        UserPreferences.saveUserId(LoginActivity.this, String.valueOf(user.id));
                        UserPreferences.saveUserName(LoginActivity.this, user.username);
                        UserPreferences.saveAvatar(LoginActivity.this, user.getAvatar());
                        CookieManager.getInstance().flush();
                        if (loginMode) {
                            Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        }
                    } else {
                        lastRejectedToken = token;
                        Toast.makeText(LoginActivity.this, "登录状态无效，请重新登录", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onFailure(Call<CurrentUser> call, Throwable t) { verifying = false; }
            });
            return;
        }
        if (!loginMode && UserPreferences.isLoggedIn(this)) UserPreferences.clearSession(this);
    }
    @Override protected void onResume() { super.onResume(); handler.post(checkSession); }
    @Override protected void onPause() { handler.removeCallbacks(checkSession); super.onPause(); }
    @Override protected void onDestroy() {
        handler.removeCallbacks(checkSession);
        if (verification != null) verification.cancel();
        webView.destroy();
        super.onDestroy();
    }
}
