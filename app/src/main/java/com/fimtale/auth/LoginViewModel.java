package com.fimtale.auth;

import android.app.Application;
import android.util.Patterns;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import com.fimtale.R;
import com.fimtale.network.ApiErrors;
import com.fimtale.network.FimTaleApiService;
import com.fimtale.network.RetrofitClient;
import com.fimtale.ui.captcha.CaptchaProviders;
import com.fimtale.utils.UserPreferences;
import java.util.function.Consumer;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Form and in-flight requests survive rotation. Passwords and codes are only held in memory. */
public class LoginViewModel extends AndroidViewModel {
    public enum Mode { PASSWORD, EMAIL, REGISTER, RESET }
    private enum Action { PASSWORD, SEND_EMAIL, REGISTER, RESET }
    public final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    public Mode mode = Mode.PASSWORD;
    public String account = "", email = "", emailConfirmation = "", password = "", passwordConfirmation = "", code = "";
    public String message = "";
    public boolean busy, captchaRequested, loggedIn, error, formRestored;
    private final FimTaleApiService api = RetrofitClient.getInstance();
    private Action pending;
    private Call<?> activeCall;

    public LoginViewModel(@NonNull Application application) { super(application); }

    public void chooseMode(Mode next) {
        if (busy || mode == next) return;
        mode = next; message = ""; error = false; changed();
    }

    public void submit() {
        if (busy || loggedIn) return;
        switch (mode) {
            case PASSWORD:
                if (account.trim().isEmpty()) { fail(R.string.login_account_required); return; }
                if (password.isEmpty()) { fail(R.string.login_password_required); return; }
                challenge(Action.PASSWORD); break;
            case EMAIL:
                if (code.trim().isEmpty()) { fail(R.string.login_code_required); return; }
                begin();
                authenticate(api.verifyEmailLogin(code.trim())); break;
            case REGISTER:
                if (account.trim().isEmpty()) { fail(R.string.login_username_required); return; }
                if (!validEmail()) return;
                if (!email.trim().equals(emailConfirmation.trim())) { fail(R.string.login_email_mismatch); return; }
                if (password.length() < 6 || password.length() > 100) { fail(R.string.login_password_length); return; }
                if (!password.equals(passwordConfirmation)) { fail(R.string.login_password_mismatch); return; }
                challenge(Action.REGISTER); break;
            case RESET:
                if (validEmail()) challenge(Action.RESET); break;
        }
    }

    public void sendEmail() {
        if (!busy && mode == Mode.EMAIL && validEmail()) challenge(Action.SEND_EMAIL);
    }

    private boolean validEmail() {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) { fail(R.string.login_email_invalid); return false; }
        return true;
    }

    private void begin() { busy = true; error = false; message = ""; }
    private void challenge(Action action) {
        begin(); pending = action; captchaRequested = true; changed();
    }

    public void captchaResult(String token, String provider) {
        if (!captchaRequested || pending == null) return;
        Action action = pending; pending = null; captchaRequested = false;
        if (token == null || token.isEmpty()) { busy = false; changed(); return; }
        if (!CaptchaProviders.configured().containsKey(provider)) { fail(R.string.login_captcha_invalid); return; }
        switch (action) {
            case PASSWORD:
                authenticate(api.login(new AuthRequests.PasswordLogin(account.trim(), password), token, provider)); break;
            case SEND_EMAIL:
                execute(api.requestEmailLogin(new AuthRequests.Email(email.trim()), token, provider),
                        ignored -> done(R.string.login_email_sent)); break;
            case RESET:
                execute(api.requestResetPassword(new AuthRequests.Email(email.trim()), token, provider),
                        ignored -> done(R.string.login_reset_sent)); break;
            case REGISTER:
                execute(api.register(new AuthRequests.Registration(account.trim(), email.trim(), password), token, provider), ignored -> {
                    mode = Mode.PASSWORD; password = ""; passwordConfirmation = "";
                    done(R.string.login_registration_done);
                }); break;
        }
    }

    private void authenticate(Call<String> request) {
        execute(request, token -> {
            if (token == null || token.isEmpty() || token.length() > 16384 || !token.matches("[!-~]+")) {
                fail(R.string.login_invalid_response); return;
            }
            // Do not replace the current session until the server confirms its identity.
            execute(api.getCurrentUser(token), user -> {
                if (user == null || user.id <= 0 || user.username == null || user.username.trim().isEmpty()) {
                    fail(R.string.login_invalid_response); return;
                }
                UserPreferences.saveSession(getApplication(), token, user);
                password = ""; passwordConfirmation = ""; code = "";
                busy = false; loggedIn = true; changed();
            });
        });
    }

    private <T> void execute(Call<T> request, Consumer<T> success) {
        activeCall = request;
        changed();
        request.enqueue(new Callback<T>() {
            @Override public void onResponse(Call<T> call, Response<T> response) {
                if (activeCall != call) return;
                activeCall = null;
                if (response.isSuccessful()) success.accept(response.body());
                else fail(ApiErrors.message(response));
            }
            @Override public void onFailure(Call<T> call, Throwable failure) {
                if (activeCall != call) return;
                activeCall = null;
                // Do not surface URLs, credentials, captcha tokens, or request bodies in error text.
                fail(R.string.login_network_error);
            }
        });
    }

    private void done(int text) { busy = false; error = false; message = getApplication().getString(text); changed(); }
    private void fail(int text) { fail(getApplication().getString(text)); }
    private void fail(String text) { busy = false; error = true; message = text; changed(); }
    private void changed() { changes.setValue(changes.getValue() + 1); }

    @Override protected void onCleared() {
        Call<?> call = activeCall; activeCall = null;
        if (call != null) call.cancel();
        password = ""; passwordConfirmation = ""; code = ""; pending = null;
    }
}
