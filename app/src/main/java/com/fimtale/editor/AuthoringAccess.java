package com.fimtale.editor;

import android.content.Context;
import com.fimtale.model.AuthorInfo;
import com.fimtale.model.UserAuth;
import com.fimtale.network.RetrofitClient;
import com.fimtale.utils.UserPreferences;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** UI visibility only. The editor and backend check permissions again when saving. */
public final class AuthoringAccess {
    private final Context context;
    private UserAuth auth;
    private Call<UserAuth> call;
    public AuthoringAccess(Context context) { this.context = context; }
    public boolean canEdit(AuthorInfo author) {
        if (author == null || !UserPreferences.isLoggedIn(context)) return false;
        String current = UserPreferences.getUserId(context);
        return current.equals(String.valueOf(author.getId()))
                || (auth != null && current.equals(String.valueOf(auth.userId)) && auth.canEdit(author.getId()));
    }
    public void refresh(Runnable updated) {
        close(); auth = null; updated.run();
        if (!UserPreferences.isLoggedIn(context)) return;
        String token = UserPreferences.getToken(context);
        call = RetrofitClient.getInstance().getUserAuth(token);
        call.enqueue(new Callback<UserAuth>() {
            @Override public void onResponse(Call<UserAuth> request, Response<UserAuth> response) {
                if (request.isCanceled() || !token.equals(UserPreferences.getToken(context))) return;
                auth = response.body(); updated.run();
            }
            @Override public void onFailure(Call<UserAuth> request, Throwable t) {}
        });
    }
    public void close() { if (call != null) call.cancel(); }
}
