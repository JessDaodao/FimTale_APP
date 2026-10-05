package com.fimtale;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import com.fimtale.auth.LoginViewModel;
import com.fimtale.auth.LoginViewModel.Mode;
import com.fimtale.network.ApiDataConverter;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.network.RetrofitClient;
import com.fimtale.network.SiteUrls;
import com.fimtale.ui.captcha.CaptchaDialogFragment;
import com.fimtale.utils.DialogHelper;
import com.fimtale.utils.UserPreferences;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** All authentication, registration and email requests terminate in local fixtures. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LoginActivityTest {
    private Context context;
    private Field service, factory;
    private Object originalApi;
    private ActivityController<LoginActivity> controller;
    private LoginActivity activity;
    private LoginViewModel model;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private volatile int loginStatus = 200;
    private volatile boolean invalidUser, failNetwork;
    private CountDownLatch holdUser;

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        factory = ViewModelProvider.AndroidViewModelFactory.class.getDeclaredField("sInstance");
        factory.setAccessible(true); factory.set(null, null);
        context.getSharedPreferences("fimtale_session", 0).edit().clear().commit();
        context.getSharedPreferences("fimtale_captcha", 0).edit().clear().commit();
        service = RetrofitClient.class.getDeclaredField("service");
        service.setAccessible(true); originalApi = service.get(null);
        OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false).addInterceptor(chain -> {
            Request request = chain.request(); requests.add(request);
            if (failNetwork) throw new java.io.IOException("fixture transport failure");
            String path = request.url().encodedPath();
            int status = 200; String data = "null";
            if (path.endsWith("/login") || path.endsWith("/verify_email_login")) {
                status = loginStatus; data = "\"fixture-session\"";
            } else if (path.endsWith("/get_user")) {
                if (holdUser != null) try { holdUser.await(8, TimeUnit.SECONDS); }
                catch (InterruptedException e) { throw new java.io.IOException(e); }
                data = invalidUser ? "{}" : "{\"id\":9,\"username\":\"测试用户\",\"avatar\":\"/avatar.png\"}";
            } else if (!path.endsWith("/register") && !path.endsWith("/request_email_login")
                    && !path.endsWith("/request_reset_password")) throw new AssertionError("Unexpected API request: " + path);
            return new okhttp3.Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("Fixture")
                    .body(ResponseBody.create(MediaType.get("application/json"), "{\"data\":" + data
                            + ",\"msg\":\"密码不正确（测试）\"}")).build();
        }).build();
        service.set(null, new Retrofit.Builder().baseUrl(SiteUrls.API).client(client)
                .callbackExecutor(command -> new Handler(Looper.getMainLooper()).post(command))
                .addConverterFactory(new ApiDataConverter()).addConverterFactory(GsonConverterFactory.create())
                .build().create(FimTaleApiService.class));
        launch(null);
    }

    private void launch(Bundle state) {
        controller = Robolectric.buildActivity(LoginActivity.class);
        controller.get().setTheme(R.style.Theme_Fimtale);
        activity = (state == null ? controller.setup() : controller.setup(state)).get();
        model = new ViewModelProvider(activity).get(LoginViewModel.class);
    }
    @After public void cleanup() throws Exception {
        if (holdUser != null) holdUser.countDown();
        if (controller != null) controller.pause().stop().destroy();
        service.set(null, originalApi); factory.set(null, null);
    }
    private void input(int id, String text) { ((EditText) activity.findViewById(id)).setText(text); }
    private void click(int id) { activity.findViewById(id).performClick(); }
    private void mode(int tab) { ((TabLayout) activity.findViewById(R.id.loginTabs)).getTabAt(tab).select(); }
    private void credentials() { input(R.id.loginAccount, "  测试用户  "); input(R.id.loginPassword, " password with spaces "); }
    private CaptchaDialogFragment captcha() {
        shadowOf(Looper.getMainLooper()).idle();
        return (CaptchaDialogFragment) activity.getSupportFragmentManager().findFragmentByTag(LoginActivity.CAPTCHA_RESULT + ".dialog");
    }
    private void solve() {
        assertTrue(model.captchaRequested); assertNotNull(captcha());
        Bundle result = new Bundle(); result.putString(CaptchaDialogFragment.TOKEN, "fixture-captcha");
        result.putString(CaptchaDialogFragment.PROVIDER, "turnstile");
        activity.getSupportFragmentManager().setFragmentResult(LoginActivity.CAPTCHA_RESULT, result);
        shadowOf(Looper.getMainLooper()).idle();
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) return;
            Thread.sleep(10);
        }
        fail("Timed out waiting for fixture");
    }
    private JsonObject body(Request request) throws Exception {
        Buffer buffer = new Buffer(); request.body().writeTo(buffer);
        return new JsonParser().parse(buffer.readUtf8()).getAsJsonObject();
    }
    private boolean containsWebView(View view) {
        if (view instanceof WebView) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (containsWebView(((ViewGroup) view).getChildAt(i))) return true;
        return false;
    }
    private void rotate() {
        Configuration next = new Configuration(activity.getResources().getConfiguration());
        next.orientation = Configuration.ORIENTATION_LANDSCAPE;
        LoginActivity previous = activity;
        controller.configurationChange(next); activity = controller.get();
        assertNotSame(previous, activity);
        assertSame(model, new ViewModelProvider(activity).get(LoginViewModel.class));
    }

    @Test public void loginIsNativeAndValidationDoesNotStartVerificationOrNetwork() {
        assertFalse(containsWebView(activity.findViewById(android.R.id.content)));
        assertTrue(activity.findViewById(R.id.loginSubmit) instanceof com.google.android.material.button.MaterialButton);
        click(R.id.loginSubmit);
        assertEquals(activity.getString(R.string.login_account_required), model.message);
        credentials(); input(R.id.loginPassword, ""); click(R.id.loginSubmit);
        assertEquals(activity.getString(R.string.login_password_required), model.message);
        assertNull(captcha()); assertTrue(requests.isEmpty());
    }

    @Test public void passwordLoginWaitsForCaptchaAndCommitsVerifiedIdentityOnce() throws Exception {
        credentials(); click(R.id.loginSubmit); click(R.id.loginSubmit);
        assertNotNull(captcha()); assertTrue(requests.isEmpty());
        assertFalse(activity.findViewById(R.id.loginSubmit).isEnabled());
        solve(); model.captchaResult("duplicate", "hcaptcha");
        await(() -> model.loggedIn);
        assertEquals(2, requests.size());
        Request login = requests.get(0);
        assertEquals("POST", login.method()); assertEquals("", login.header("Token"));
        assertEquals("fixture-captcha", login.url().queryParameter("captcha_response"));
        assertEquals("turnstile", login.url().queryParameter("captcha_type"));
        assertEquals("测试用户", body(login).get("username").getAsString());
        assertFalse(body(login).has("email"));
        assertEquals(" password with spaces ", body(login).get("password").getAsString());
        assertEquals("fixture-session", requests.get(1).header("Token"));
        assertEquals("fixture-session", UserPreferences.getToken(context));
        assertEquals("9", UserPreferences.getUserId(context));
        assertEquals("测试用户", UserPreferences.getUserName(context));
        assertEquals("", model.password); assertTrue(activity.isFinishing());
        assertEquals(android.app.Activity.RESULT_OK, shadowOf(activity).getResultCode());
    }

    @Test public void emailAddressUsesEmailFieldForPasswordLogin() throws Exception {
        credentials(); input(R.id.loginAccount, "pony@example.test"); click(R.id.loginSubmit); solve();
        await(() -> model.loggedIn);
        assertEquals("pony@example.test", body(requests.get(0)).get("email").getAsString());
        assertFalse(body(requests.get(0)).has("username"));
    }

    @Test public void rejectionKeepsCredentialsAndPreviousSessionAndRetryRequiresFreshCaptcha() throws Exception {
        UserPreferences.saveToken(context, "previous-session"); loginStatus = 401;
        credentials(); click(R.id.loginSubmit); solve(); await(() -> !model.busy);
        assertFalse(model.loggedIn); assertTrue(model.error);
        assertEquals("密码不正确（测试）", ((TextView) activity.findViewById(R.id.loginMessage)).getText().toString());
        assertEquals("previous-session", UserPreferences.getToken(context));
        assertEquals(" password with spaces ", model.password);
        assertEquals(1, requests.size()); assertTrue(activity.findViewById(R.id.loginSubmit).isEnabled());
        click(R.id.loginSubmit); assertNotNull(captcha()); assertEquals(1, requests.size());
        loginStatus = 200; solve(); await(() -> model.loggedIn); assertEquals(3, requests.size());
    }

    @Test public void invalidIdentityDoesNotReplacePreviousSession() throws Exception {
        UserPreferences.saveToken(context, "previous-session"); invalidUser = true;
        credentials(); click(R.id.loginSubmit); solve(); await(() -> !model.busy);
        assertFalse(model.loggedIn); assertEquals("previous-session", UserPreferences.getToken(context));
        assertEquals(activity.getString(R.string.login_invalid_response), model.message);
    }

    @Test public void emailFlowVerifiesBeforeSendingAndUsesGetForOneTimeCode() throws Exception {
        mode(1); input(R.id.loginEmail, "pony@example.test"); click(R.id.loginSendEmail);
        assertTrue(requests.isEmpty()); solve(); await(() -> !model.busy);
        assertEquals(activity.getString(R.string.login_email_sent), model.message);
        assertEquals("/api/user/request_email_login", requests.get(0).url().encodedPath());
        assertEquals("pony@example.test", body(requests.get(0)).get("email").getAsString());
        input(R.id.loginCode, "  fixture+a/b=  "); click(R.id.loginSubmit); await(() -> model.loggedIn);
        assertEquals(3, requests.size());
        Request verify = requests.get(1);
        assertEquals("GET", verify.method()); assertEquals("/api/user/verify_email_login", verify.url().encodedPath());
        assertEquals("fixture+a/b=", verify.url().queryParameter("token"));
        assertNull(verify.url().queryParameter("captcha_response"));
        assertFalse(model.captchaRequested);
    }

    @Test public void registrationAndPasswordRecoveryShareNativeCaptcha() throws Exception {
        mode(2); credentials(); input(R.id.loginEmail, "pony@example.test");
        input(R.id.loginEmailConfirmation, "different@example.test"); input(R.id.loginPasswordConfirmation, model.password);
        click(R.id.loginSubmit); assertFalse(model.captchaRequested); assertEquals(0, requests.size());
        input(R.id.loginEmailConfirmation, "pony@example.test"); click(R.id.loginSubmit); solve(); await(() -> !model.busy);
        assertEquals("/api/user/register", requests.get(0).url().encodedPath());
        assertEquals("pony@example.test", body(requests.get(0)).get("email").getAsString());
        assertEquals(Mode.PASSWORD, model.mode); assertFalse(UserPreferences.isLoggedIn(context));
        assertEquals("", model.password); assertEquals(activity.getString(R.string.login_registration_done), model.message);
        click(R.id.loginForgot); assertEquals(Mode.RESET, model.mode);
        click(R.id.loginSubmit); solve(); await(() -> !model.busy);
        assertEquals("/api/user/request_reset_password", requests.get(1).url().encodedPath());
        assertEquals(activity.getString(R.string.login_reset_sent), model.message);
    }

    @Test public void cancellationAfterRotationRetainsInputAndDoesNotLogIn() {
        credentials(); click(R.id.loginSubmit); assertNotNull(captcha()); rotate();
        assertEquals(1, activity.getSupportFragmentManager().getFragments().stream().filter(f -> f instanceof CaptchaDialogFragment).count());
        ((AlertDialog) captcha().requireDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(model.busy); assertFalse(model.captchaRequested);
        assertEquals(" password with spaces ", ((EditText) activity.findViewById(R.id.loginPassword)).getText().toString());
        assertTrue(requests.isEmpty()); assertFalse(UserPreferences.isLoggedIn(context));
    }

    @Test public void rotationWhileIdentityLoadsDoesNotRestartRequestsOrCommitEarly() throws Exception {
        holdUser = new CountDownLatch(1);
        credentials(); click(R.id.loginSubmit); solve(); await(() -> requests.size() == 2);
        assertFalse(UserPreferences.isLoggedIn(context)); rotate();
        assertTrue(model.busy); assertNull(captcha());
        holdUser.countDown(); await(() -> model.loggedIn);
        assertEquals(2, requests.size()); assertEquals(android.app.Activity.RESULT_OK, shadowOf(activity).getResultCode());
    }

    @Test public void processRecreationDoesNotRestorePasswordsCodesOrSubmitPendingCaptcha() {
        credentials(); input(R.id.loginCode, "secret-one-time-code"); click(R.id.loginSubmit); assertNotNull(captcha());
        Bundle state = new Bundle();
        controller.pause().saveInstanceState(state).stop().destroy(); controller = null;
        launch(state); shadowOf(Looper.getMainLooper()).idle();
        assertEquals("  测试用户  ", model.account);
        assertEquals("", model.password); assertEquals("", model.code);
        assertEquals("", ((EditText) activity.findViewById(R.id.loginPassword)).getText().toString());
        assertFalse(model.busy); assertFalse(model.captchaRequested); assertNull(captcha()); assertTrue(requests.isEmpty());
    }

    @Test public void networkFailureCanBeRetriedWithoutLosingInput() throws Exception {
        failNetwork = true; credentials(); click(R.id.loginSubmit); solve(); await(() -> !model.busy);
        assertTrue(model.error); assertEquals(activity.getString(R.string.login_network_error), model.message);
        assertFalse(UserPreferences.isLoggedIn(context)); assertFalse(model.password.isEmpty());
        assertTrue(activity.findViewById(R.id.loginSubmit).isEnabled());
    }

    @Test public void siteContentUsesSeparateActivity() {
        DialogHelper.openSite(activity, "/work/4");
        Intent started = shadowOf(activity).getNextStartedActivity();
        assertEquals(SiteActivity.class.getName(), started.getComponent().getClassName());
        assertEquals("/work/4", started.getStringExtra(SiteActivity.EXTRA_PATH));
        assertNull(shadowOf(activity).getNextStartedActivity());
    }
}
