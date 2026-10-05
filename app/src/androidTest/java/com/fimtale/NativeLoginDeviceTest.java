package com.fimtale;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fimtale.auth.LoginViewModel;
import com.fimtale.network.ApiDataConverter;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.network.RetrofitClient;
import com.fimtale.network.SiteUrls;
import com.fimtale.ui.captcha.CaptchaDialogFragment;
import com.fimtale.utils.UserPreferences;
import com.google.android.material.tabs.TabLayout;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.ResponseBody;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;

/** Native login + real captcha WebView. All API and vendor script responses are local fixtures. */
@RunWith(AndroidJUnit4.class)
public class NativeLoginDeviceTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final AtomicInteger calls = new AtomicInteger();
    private Field service;
    private Object originalApi;
    private int originalNight;
    private SharedPreferences session, captchaPrefs;
    private Map<String, ?> originalSession, originalCaptcha;

    @Before public void setup() throws Exception {
        session = instrumentation.getTargetContext().getSharedPreferences("fimtale_session", 0);
        captchaPrefs = instrumentation.getTargetContext().getSharedPreferences("fimtale_captcha", 0);
        originalSession = session.getAll(); originalCaptcha = captchaPrefs.getAll();
        session.edit().clear().commit(); captchaPrefs.edit().clear().commit();
        originalNight = AppCompatDelegate.getDefaultNightMode();
        instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO));
        service = RetrofitClient.class.getDeclaredField("service"); service.setAccessible(true); originalApi = service.get(null);
        OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain -> {
            calls.incrementAndGet();
            String path = chain.request().url().encodedPath();
            assertTrue(path.endsWith("/login") || path.endsWith("/get_user"));
            String data = path.endsWith("/login") ? "\"device-fixture-session\"" : "{\"id\":42,\"username\":\"测试用户\"}";
            return new okhttp3.Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                    .body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + data + "}")).build();
        }).build();
        service.set(null, new Retrofit.Builder().baseUrl(SiteUrls.API).client(client)
                .callbackExecutor(command -> new Handler(Looper.getMainLooper()).post(command))
                .addConverterFactory(new ApiDataConverter()).addConverterFactory(GsonConverterFactory.create())
                .build().create(FimTaleApiService.class));
    }
    @After public void cleanup() throws Exception {
        service.set(null, originalApi);
        restore(session, originalSession); restore(captchaPrefs, originalCaptcha);
        instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(originalNight));
    }
    private void restore(SharedPreferences prefs, Map<String, ?> values) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        values.forEach((key, value) -> { if (value instanceof String) editor.putString(key, (String) value); });
        editor.commit();
    }
    private CaptchaDialogFragment dialog(LoginActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
        return (CaptchaDialogFragment) activity.getSupportFragmentManager().findFragmentByTag(LoginActivity.CAPTCHA_RESULT + ".dialog");
    }
    private void fixture(CaptchaDialogFragment dialog) {
        WebView web = dialog.requireDialog().findViewById(R.id.captchaWeb);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request.getUrl().getHost() == null) return null;
                String script = "window.turnstile={render:function(el,o){window.fixtureSuccess=()=>o.callback('device-fixture-captcha');"
                        + "setTimeout(()=>o['unsupported-callback'](),30);return 'id'},reset:function(){}};";
                return new WebResourceResponse("application/javascript", "UTF-8", new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)));
            }
        });
    }
    private boolean containsWebView(View view) {
        if (view instanceof WebView) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (containsWebView(((ViewGroup) view).getChildAt(i))) return true;
        return false;
    }
    private void capture(String name) throws Exception {
        instrumentation.waitForIdleSync(); SystemClock.sleep(300);
        Bitmap bitmap = instrumentation.getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(instrumentation.getTargetContext().getFilesDir(), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
    }

    @Test public void nativeLoginRetainsInputAfterRecreationAndAuthenticatesThroughSharedDialog() throws Exception {
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse(containsWebView(activity.findViewById(android.R.id.content)));
                ((EditText) activity.findViewById(R.id.loginAccount)).setText("测试用户");
                ((EditText) activity.findViewById(R.id.loginPassword)).setText("fixture-password");
            });
            capture("native-login");
            scenario.onActivity(activity -> { activity.findViewById(R.id.loginSubmit).performClick(); fixture(dialog(activity)); });
            scenario.recreate();
            scenario.onActivity(activity -> {
                CaptchaDialogFragment fragment = dialog(activity); fixture(fragment);
                assertEquals(1, activity.getSupportFragmentManager().getFragments().stream().filter(f -> f instanceof CaptchaDialogFragment).count());
                ((AlertDialog) fragment.requireDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            });
            instrumentation.waitForIdleSync();
            AtomicReference<LoginViewModel> model = new AtomicReference<>();
            AtomicReference<WebView> web = new AtomicReference<>();
            scenario.onActivity(activity -> {
                model.set(new ViewModelProvider(activity).get(LoginViewModel.class));
                assertFalse(model.get().busy); assertEquals(0, calls.get());
                assertEquals("fixture-password", ((EditText) activity.findViewById(R.id.loginPassword)).getText().toString());
                activity.findViewById(R.id.loginSubmit).performClick();
                CaptchaDialogFragment fragment = dialog(activity); fixture(fragment);
                web.set(fragment.requireDialog().findViewById(R.id.captchaWeb));
            });
            long deadline = SystemClock.uptimeMillis() + 10000;
            while (SystemClock.uptimeMillis() < deadline && calls.get() == 0) {
                instrumentation.runOnMainSync(() -> web.get().evaluateJavascript("window.fixtureSuccess && window.fixtureSuccess()", null));
                SystemClock.sleep(100);
            }
            deadline = SystemClock.uptimeMillis() + 5000;
            while (SystemClock.uptimeMillis() < deadline && !UserPreferences.isLoggedIn(instrumentation.getTargetContext())) SystemClock.sleep(50);
            assertEquals("device-fixture-session", UserPreferences.getToken(instrumentation.getTargetContext()));
            assertEquals("42", UserPreferences.getUserId(instrumentation.getTargetContext()));
            assertEquals(2, calls.get());
        }
    }

    @Test public void emailAndRecoveryFormsUseNativeControlsInNightTheme() throws Exception {
        instrumentation.runOnMainSync(() -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES));
        try (ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class)) {
            scenario.onActivity(activity -> {
                ((TabLayout) activity.findViewById(R.id.loginTabs)).getTabAt(1).select();
                assertTrue(activity.findViewById(R.id.loginEmail).isShown());
                assertTrue(activity.findViewById(R.id.loginCode).isShown());
                assertFalse(activity.findViewById(R.id.loginPassword).isShown());
                assertFalse(containsWebView(activity.findViewById(android.R.id.content)));
            });
            capture("native-email-login-night");
            scenario.onActivity(activity -> {
                ((TabLayout) activity.findViewById(R.id.loginTabs)).getTabAt(0).select();
                activity.findViewById(R.id.loginForgot).performClick();
                assertEquals(View.GONE, activity.findViewById(R.id.loginTabs).getVisibility());
                assertEquals(View.VISIBLE, activity.findViewById(R.id.loginReturn).getVisibility());
                assertEquals(0, calls.get());
            });
        }
    }
}
