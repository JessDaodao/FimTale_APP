package com.fimtale.network;

import com.fimtale.R;
import com.fimtale.utils.AppStrings;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import retrofit2.Response;
public final class ApiErrors {
    private ApiErrors() {}
    public static String message(Response<?> response) {
        if (response.errorBody() != null) {
            try {
                JsonObject error = new JsonParser().parse(response.errorBody().string()).getAsJsonObject();
                if (error.has("msg") && !error.get("msg").isJsonNull()) return error.get("msg").getAsString();
            } catch (Exception ignored) {}
        }
        if (response.code() == 401) return AppStrings.get(R.string.error_login_expired);
        if (response.code() == 403) return AppStrings.get(R.string.error_forbidden);
        if (response.code() == 429) return AppStrings.get(R.string.error_rate_limited);
        return AppStrings.get(R.string.error_request_failed, response.code());
    }
}
