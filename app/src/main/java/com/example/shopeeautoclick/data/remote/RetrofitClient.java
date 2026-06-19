package com.example.shopeeautoclick.data.remote;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class RetrofitClient {
    public static final String DEFAULT_BASE_URL = "http://10.0.2.2:8001/";
    public static final String PREFS_NAME = "auto_click_config";
    public static final String KEY_BASE_URL = "base_url";

    private static Retrofit retrofit;
    private static String activeBaseUrl;

    private RetrofitClient() {
    }

    public static synchronized ApiService getApiService(Context context) {
        String baseUrl = getConfiguredBaseUrl(context);
        if (retrofit == null || !baseUrl.equals(activeBaseUrl)) {
            activeBaseUrl = baseUrl;
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .callTimeout(90, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(ApiService.class);
    }

    public static String getConfiguredBaseUrl(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
        if (value == null || value.trim().isEmpty()) {
            value = DEFAULT_BASE_URL;
        }

        value = value.trim();
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return value;
    }

    public static synchronized void setBaseUrl(Context context, String baseUrl) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, baseUrl)
                .apply();
        retrofit = null;
        activeBaseUrl = null;
    }
}
