package com.app.fimtale;

import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.app.fimtale.model.LogoutRequest;
import com.app.fimtale.model.UserSession;
import com.app.fimtale.network.ApiErrors;
import com.app.fimtale.network.RetrofitClient;
import com.app.fimtale.utils.UserPreferences;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Native account/session management backed by the current user API. */
public class AccountSessionsActivity extends AppCompatActivity {
    private TextView summary, empty;
    private LinearLayout sessions;
    private ProgressBar progress;
    private TextInputState tokenView;
    private Call<List<UserSession>> sessionsCall;
    private Call<String> createTokenCall;
    private Call<Void> logoutCall;
    private ValueAnimator headerAnimator;
    private boolean headerRaised;
    private boolean closed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_account_sessions);
        com.app.fimtale.utils.EditorWindowStyle.apply(this);
        MaterialToolbar toolbar = findViewById(R.id.accountToolbar);
        toolbar.setTitle("");
        toolbar.setBackground(null);
        ((TextView) findViewById(R.id.accountToolbarTitle)).setText("账户与会话");
        toolbar.setNavigationOnClickListener(v -> finish());
        ScrollView scroll = findViewById(R.id.accountScroll);
        View titleCard = findViewById(R.id.accountToolbarContainer);
        titleCard.addOnLayoutChangeListener((v, l, t, r, b, oldLeft, oldTop, oldRight, oldBottom) -> {
            int top = b + dp(16);
            if (scroll.getPaddingTop() != top) {
                scroll.setPadding(scroll.getPaddingLeft(), top, scroll.getPaddingRight(), scroll.getPaddingBottom());
            }
        });
        scroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                animateHeader(scroll.canScrollVertically(-1)));
        summary = findViewById(R.id.accountSummary);
        sessions = findViewById(R.id.accountSessions);
        empty = findViewById(R.id.accountEmpty);
        progress = findViewById(R.id.accountProgress);
        tokenView = new TextInputState(findViewById(R.id.accountTokenLayout), findViewById(R.id.accountToken),
                findViewById(R.id.accountCopyToken));
        findViewById(R.id.accountRefresh).setOnClickListener(v -> loadSessions());
        findViewById(R.id.accountCreateToken).setOnClickListener(v -> createApiToken());
        tokenView.copy.setOnClickListener(v -> copyToken());
        if (!UserPreferences.isLoggedIn(this)) {
            startActivity(new android.content.Intent(this, LoginActivity.class));
            finish();
            return;
        }
        String username = UserPreferences.getUserName(this);
        summary.setText(TextUtils.isEmpty(username) ? "当前已登录 FimTale 账户" : "当前账户：" + username);
        loadSessions();
    }

    private void loadSessions() {
        if (closed) return;
        if (sessionsCall != null) sessionsCall.cancel();
        setLoading(true);
        String token = UserPreferences.getToken(this);
        sessionsCall = RetrofitClient.getInstance().getActiveSessions(token);
        sessionsCall.enqueue(new Callback<List<UserSession>>() {
            @Override public void onResponse(@NonNull Call<List<UserSession>> call, @NonNull Response<List<UserSession>> response) {
                if (!valid(call, sessionsCall)) return;
                setLoading(false); sessionsCall = null;
                if (response.isSuccessful()) renderSessions(response.body());
                else showError(ApiErrors.message(response));
            }
            @Override public void onFailure(@NonNull Call<List<UserSession>> call, @NonNull Throwable error) {
                if (!valid(call, sessionsCall)) return;
                setLoading(false); sessionsCall = null; showError("会话加载失败，请重试");
            }
        });
    }

    private boolean valid(Call<?> call, Call<?> current) {
        return !closed && !isFinishing() && !isDestroyed() && !call.isCanceled() && call == current;
    }

    private void renderSessions(List<UserSession> values) {
        sessions.removeAllViews();
        List<UserSession> list = values == null ? Collections.emptyList() : values;
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        String currentToken = UserPreferences.getToken(this);
        for (UserSession session : list) {
            if (session == null || TextUtils.isEmpty(session.tokenPrefix)) continue;
            boolean current = currentToken.startsWith(session.tokenPrefix);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = dp(12); row.setPadding(pad, pad, 0, pad);
            LinearLayout details = new LinearLayout(this); details.setOrientation(LinearLayout.VERTICAL);
            TextView label = new TextView(this); label.setText(session.tokenPrefix + (current ? "  · 当前会话" : ""));
            label.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            TextView activity = new TextView(this); activity.setText("最近活动：" + formatDate(session.lastActivity));
            activity.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            details.addView(label); details.addView(activity);
            row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton logout = new MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
            logout.setText("登出"); logout.setContentDescription("登出会话 " + session.tokenPrefix);
            logout.setOnClickListener(v -> logout(session));
            row.addView(logout, new LinearLayout.LayoutParams(-2, -2));
            sessions.addView(row, new LinearLayout.LayoutParams(-1, -2));
            View divider = new View(this); divider.setBackgroundColor(resolveColor(com.google.android.material.R.attr.colorOutlineVariant));
            sessions.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
    }

    private void logout(UserSession session) {
        if (logoutCall != null || session == null) return;
        String token = UserPreferences.getToken(this);
        boolean current = !TextUtils.isEmpty(session.tokenPrefix) && token.startsWith(session.tokenPrefix);
        logoutCall = RetrofitClient.getInstance().logoutSession(token, new LogoutRequest(session.tokenPrefix));
        logoutCall.enqueue(new Callback<Void>() {
            @Override public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!valid(call, logoutCall)) return;
                logoutCall = null;
                if (response.isSuccessful()) {
                    if (current) { UserPreferences.clearSession(AccountSessionsActivity.this); finish(); }
                    else loadSessions();
                } else showError(ApiErrors.message(response));
            }
            @Override public void onFailure(@NonNull Call<Void> call, @NonNull Throwable error) {
                if (!valid(call, logoutCall)) return;
                logoutCall = null; showError("登出失败，请重试");
            }
        });
    }

    private void createApiToken() {
        if (createTokenCall != null) return;
        String token = UserPreferences.getToken(this);
        findViewById(R.id.accountCreateToken).setEnabled(false);
        createTokenCall = RetrofitClient.getInstance().createApiKey(token);
        createTokenCall.enqueue(new Callback<String>() {
            @Override public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (!valid(call, createTokenCall)) return;
                createTokenCall = null; findViewById(R.id.accountCreateToken).setEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    tokenView.set(response.body()); loadSessions();
                } else showError(ApiErrors.message(response));
            }
            @Override public void onFailure(@NonNull Call<String> call, @NonNull Throwable error) {
                if (!valid(call, createTokenCall)) return;
                createTokenCall = null; findViewById(R.id.accountCreateToken).setEnabled(true);
                showError("API Token 创建失败，请重试");
            }
        });
    }

    private void copyToken() {
        String value = tokenView.input.getText() == null ? "" : tokenView.input.getText().toString();
        if (value.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) { clipboard.setPrimaryClip(ClipData.newPlainText("FimTale API Token", value));
            android.widget.Toast.makeText(this, "Token 已复制", android.widget.Toast.LENGTH_SHORT).show(); }
    }

    private void setLoading(boolean loading) { progress.setVisibility(loading ? View.VISIBLE : View.GONE); }
    private void animateHeader(boolean raised) {
        if (headerRaised == raised) return;
        headerRaised = raised;
        if (headerAnimator != null) headerAnimator.cancel();
        View surface = findViewById(R.id.accountHeaderSurface);
        headerAnimator = ValueAnimator.ofFloat(surface.getAlpha(), raised ? 1f : 0f);
        headerAnimator.setDuration(200);
        headerAnimator.addUpdateListener(animation -> surface.setAlpha((float) animation.getAnimatedValue()));
        headerAnimator.start();
    }
    private void showError(String message) { android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int resolveColor(int attr) {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(attr, value, true); return value.data;
    }
    private String formatDate(String value) {
        if (TextUtils.isEmpty(value)) return "未知";
        try { return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")); }
        catch (RuntimeException ignored) { return value; }
    }

    @Override protected void onDestroy() {
        closed = true;
        if (sessionsCall != null) sessionsCall.cancel();
        if (createTokenCall != null) createTokenCall.cancel();
        if (logoutCall != null) logoutCall.cancel();
        if (headerAnimator != null) headerAnimator.cancel();
        super.onDestroy();
    }

    private static final class TextInputState {
        final View layout; final com.google.android.material.textfield.TextInputEditText input; final View copy;
        TextInputState(View layout, com.google.android.material.textfield.TextInputEditText input, View copy) {
            this.layout = layout; this.input = input; this.copy = copy;
        }
        void set(String value) { input.setText(value); layout.setVisibility(View.VISIBLE); copy.setVisibility(View.VISIBLE); }
    }
}
