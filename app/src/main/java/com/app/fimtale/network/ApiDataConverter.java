package com.app.fimtale.network;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;
/** The current API returns {data, msg}; HTTP status determines success. */
public final class ApiDataConverter extends Converter.Factory {
    private final Gson gson = new Gson();
    @Override public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        return body -> {
            try (ResponseBody source = body) {
                return decode(source.string(), type);
            }
        };
    }
    public Object decode(String json, Type type) throws IOException {
        try {
            JsonObject envelope = new JsonParser().parse(json).getAsJsonObject();
            if (!envelope.has("data")) throw new IOException("服务器响应缺少 data");
            JsonElement data = envelope.get("data");
            return gson.fromJson(data, type);
        } catch (RuntimeException e) { throw new IOException("无法解析服务器响应", e); }
    }
}
