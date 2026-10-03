package com.app.fimtale.network;
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
        if (response.code() == 401) return "登录已过期，请重新登录";
        if (response.code() == 403) return "当前账户无权访问";
        if (response.code() == 429) return "请求过于频繁，请稍后重试";
        return "请求失败（" + response.code() + "）";
    }
}
