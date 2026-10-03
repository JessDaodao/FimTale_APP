package com.app.fimtale.network;
import com.app.fimtale.FimTaleApplication;
import com.app.fimtale.utils.UserPreferences;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
public final class RetrofitClient {
    private static FimTaleApiService service;
    private static FimTaleApiService updateService;
    private RetrofitClient() {}
    public static synchronized FimTaleApiService getInstance() {
        if (service == null) {
            // A lost POST response must not be retried automatically: creating a work/chapter is not idempotent.
            OkHttpClient client = new OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).addInterceptor(chain -> {
                Request original = chain.request();
                Request.Builder request = original.newBuilder();
                String token = UserPreferences.getToken(FimTaleApplication.getInstance());
                if (original.header("Token") == null && !token.isEmpty() && SiteUrls.isApi(original.url().toString())) {
                    request.header("Token", token);
                }
                Response response = chain.proceed(request.build());
                if (response.code() == 401 && !token.isEmpty()
                        && token.equals(response.request().header("Token"))
                        && token.equals(UserPreferences.getToken(FimTaleApplication.getInstance()))) {
                    UserPreferences.clearSession(FimTaleApplication.getInstance());
                }
                return response;
            }).build();
            service = new Retrofit.Builder().baseUrl(SiteUrls.API).client(client)
                    .addConverterFactory(new ApiDataConverter())
                    .addConverterFactory(GsonConverterFactory.create()).build().create(FimTaleApiService.class);
        }
        return service;
    }
    public static synchronized FimTaleApiService getUpdateService() {
        if (updateService == null) {
            updateService = new Retrofit.Builder().baseUrl("https://ftapp.eqad.fun/")
                    .addConverterFactory(GsonConverterFactory.create()).build().create(FimTaleApiService.class);
        }
        return updateService;
    }
}
