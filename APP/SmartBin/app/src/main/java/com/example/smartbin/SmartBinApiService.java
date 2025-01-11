package com.example.smartbin;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public class SmartBinApiService {
    private static final String TAG = "SmartBinApiService";
    private static final String BASE_URL = "https://bromeo.pythonanywhere.com/";
    private static final String PREFS_NAME = "SmartBinPrefs";
    private static final String KEY_EMAIL = "user_email";
    private static final String KEY_PASSWORD = "user_password";

    private Context context;
    private SharedPreferences sharedPreferences;
    private Retrofit retrofit;
    private ApiService apiService;

    // API Service Interface
    public interface ApiService {
        @POST("signup")
        Call<Void> signup(@Body Map<String, String> userData);

        @POST("login")
        Call<Void> login(@Body Map<String, String> userData);

        @POST("add_dustbin")
        Call<Void> addDustbin(@Body Map<String, String> dustbinData);

        @GET("dustbins")
        Call<List<ViewDustbinsActivity.DustbinModel>> getDustbins();

        @GET("dustbin/{binId}/state")
        Call<HomeDashboardPollingService.DustbinStateResponse> getDustbinState(@Path("binId") String binId);

        @POST("payment_start")
        Call<PaymentFragment.PaymentResponse> initiatePayment(@Body Map<String, String> paymentData);
    }

    public static class SessionPersistentCookieJar implements CookieJar {
        private SharedPreferences sharedPreferences;

        public SessionPersistentCookieJar(Context context) {
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }

        @Override
        public void saveFromResponse(@NonNull HttpUrl url, @NonNull List<Cookie> cookies) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            for (Cookie cookie : cookies) {
                editor.putString(cookie.name(), cookie.toString());
                Log.d(TAG, "Saving cookie: " + cookie.name() + " = " + cookie.toString());
            }
            editor.apply();
        }

        @NonNull
        @Override
        public List<Cookie> loadForRequest(@NonNull HttpUrl url) {
            List<Cookie> cookies = new ArrayList<>();
            Map<String, ?> allCookies = sharedPreferences.getAll();

            for (Map.Entry<String, ?> entry : allCookies.entrySet()) {
                Cookie cookie = Cookie.parse(url, (String) entry.getValue());
                if (cookie != null) {
                    cookies.add(cookie);
                    Log.d(TAG, "Loading cookie: " + entry.getKey() + " = " + entry.getValue());
                }
            }

            return cookies;
        }
    }

    public SmartBinApiService(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initializeRetrofit();
    }

    private void initializeRetrofit() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .cookieJar(new SessionPersistentCookieJar(context))
                .addInterceptor(logging)
                .build();

        retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    public void getDustbins(Callback<List<ViewDustbinsActivity.DustbinModel>> callback) {
        apiService.getDustbins().enqueue(callback);
    }
    public Call<HomeDashboardPollingService.DustbinStateResponse> getDustbinState(String binId, Callback<HomeDashboardPollingService.DustbinStateResponse> callback) {
        Call<HomeDashboardPollingService.DustbinStateResponse> call = apiService.getDustbinState(binId);
        call.enqueue(callback);
        return call;
    }
    public class DustbinStateResponse {
        private String status;
        private double fillLevel;
        private String lastUpdated;
        // Add getters and setters

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public double getFillLevel() {
            return fillLevel;
        }

        public void setFillLevel(double fillLevel) {
            this.fillLevel = fillLevel;
        }

        public String getLastUpdated() {
            return lastUpdated;
        }

        public void setLastUpdated(String lastUpdated) {
            this.lastUpdated = lastUpdated;
        }
    }

    // Enhanced payment request method with detailed debugging
    public void startPayment(PaymentFragment.PaymentRequest paymentRequest, Callback<PaymentFragment.PaymentResponse> callback) {
        Map<String, String> paymentData = new HashMap<>();
        paymentData.put("email", paymentRequest.getEmail());
        paymentData.put("dustbin_id", paymentRequest.getDustbinId());
        paymentData.put("phone", paymentRequest.getPhone());

        // Log detailed request information
        Log.d(TAG, "Payment Request Details:");
        Log.d(TAG, "Email: " + paymentRequest.getEmail());
        Log.d(TAG, "Dustbin ID: " + paymentRequest.getDustbinId());
        Log.d(TAG, "Phone: " + paymentRequest.getPhone());

        apiService.initiatePayment(paymentData).enqueue(new Callback<PaymentFragment.PaymentResponse>() {
            @Override
            public void onResponse(Call<PaymentFragment.PaymentResponse> call, Response<PaymentFragment.PaymentResponse> response) {
                // Detailed logging for response
                Log.d(TAG, "Response Code: " + response.code());

                if (response.isSuccessful()) {
                    Log.d(TAG, "Payment Success");
                    callback.onResponse(call, response);
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        Log.e(TAG, "Full Error Response: " + errorBody);

                        // Parse and log specific error details
                        JSONObject errorJson = new JSONObject(errorBody);
                        Log.e(TAG, "Detailed Error Message: " +
                                (errorJson.has("details") ?
                                        errorJson.getJSONObject("details").getString("message") :
                                        "No specific message"));
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error response", e);
                    }

                    callback.onResponse(call, response);
                }
            }

            @Override
            public void onFailure(Call<PaymentFragment.PaymentResponse> call, Throwable t) {
                Log.e(TAG, "Network Failure", t);
                callback.onFailure(call, t);
            }
        });
    }

    public void addDustbin(String dustbinId, String location, String email, SimpleCallback callback) {
        Map<String, String> dustbinData = new HashMap<>();
        dustbinData.put("dustbin_id", dustbinId);
        dustbinData.put("location", location);
        dustbinData.put("email", email);  // Ensure email is added

        apiService.addDustbin(dustbinData).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    handleErrorCallback(response, callback);
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    private void handleErrorCallback(Response<?> response, SimpleCallback callback) {
        try {
            String errorMessage = response.errorBody() != null ?
                    response.errorBody().string() : "Unknown error";
            callback.onError(errorMessage);
        } catch (IOException e) {
            callback.onError("Unable to read error details");
        }
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String errorMessage);
    }
}