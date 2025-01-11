package com.example.smartbin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";
    private static final String BASE_URL = "https://bromeo.pythonanywhere.com/";
    private static final String PREFS_NAME = "SmartBinPrefs";
    private static final String SESSION_COOKIE_KEY = "session_cookie";
    private static final String KEY_EMAIL = "user_email";



    public interface LoginApiService {
        @POST("login")
        Call<LoginResponse> loginUser(@Body LoginRequest loginRequest);
    }

    public static class LoginRequest {
        @SerializedName("email")
        private String email;
        @SerializedName("password")
        private String password;

        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    public static class LoginResponse {
        @SerializedName("success")
        public boolean success;
        @SerializedName("message")
        public String message;
        @SerializedName("Status")
        public String Status;

        public String getMessage() {
            return message;
        }

        public String getStatus() {
            return Status;
        }
    }

    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private Button loginButton;
    private TextView createAccountLink;
    private ProgressBar loginProgress;
    private LoginApiService loginApiService;
    private SharedPreferences sharedPreferences;
    public static String getKeyEmail() {
        return KEY_EMAIL;
    }


    // Persistent Cookie Storage Manager
    private class SessionCookieJar implements CookieJar {
        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            for (Cookie cookie : cookies) {
                if (cookie.name().equals("session")) {
                    Log.d(TAG, "Saving session cookie: " + cookie.value());
                    sharedPreferences.edit()
                            .putString(SESSION_COOKIE_KEY, cookie.toString())
                            .apply();
                    break;
                }
            }
        }


        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            String savedCookieString = sharedPreferences.getString(SESSION_COOKIE_KEY, null);

            if (savedCookieString != null) {
                try {
                    // Parse the saved cookie string
                    Cookie cookie = Cookie.parse(url, savedCookieString);
                    Log.d(TAG, "Loading session cookie for request to: " + url);
                    return cookie != null ? List.of(cookie) : List.of();
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing saved cookie", e);
                }
            }

            return List.of();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        initializeViews();
        setupApiService();
        setupClickListeners();
    }

    private void setupApiService() {
        // Create a persistent CookieJar
        SessionCookieJar cookieJar = new SessionCookieJar();

        // Add detailed logging interceptor
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message ->
                Log.d("HTTP_LOG", message));
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .cookieJar(cookieJar)  // Add the CookieJar
                .addInterceptor(loggingInterceptor)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .method(original.method(), original.body())
                            .build();
                    return chain.proceed(request);
                })
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        loginApiService = retrofit.create(LoginApiService.class);
    }

    private void initializeViews() {
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        loginButton = findViewById(R.id.login_button);
        createAccountLink = findViewById(R.id.createAccountLink);
        loginProgress = findViewById(R.id.login_progress);
        loginProgress.setVisibility(View.GONE);
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> {
            if (isNetworkAvailable()) {
                attemptLogin();
            } else {
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            }
        });

        createAccountLink.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    private void attemptLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        loginProgress.setVisibility(View.VISIBLE);
        loginButton.setEnabled(false);

        LoginRequest loginRequest = new LoginRequest(email, password);
        loginApiService.loginUser(loginRequest).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                loginProgress.setVisibility(View.GONE);
                loginButton.setEnabled(true);

                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();

                    // Flexible login success check
                    boolean isLoginSuccessful = loginResponse.success
                            || "Logged in successfully".equalsIgnoreCase(loginResponse.message);

                    if (isLoginSuccessful) {
                        Toast.makeText(LoginActivity.this,
                                loginResponse.message != null ? loginResponse.message : "Login Successful!",
                                Toast.LENGTH_SHORT).show();

                        // Save email in SharedPreferences
                        sharedPreferences.edit().putString(KEY_EMAIL, email).apply();

                        // Navigate to Dashboard
                        navigateToDashboard();
                    } else {
                        // More detailed error handling
                        String errorMessage = loginResponse.message != null
                                ? loginResponse.message
                                : "Login failed. Please try again.";
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                } else {
                    // Handle unsuccessful response
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        Log.e(TAG, "Error Body: " + errorBody);
                        Toast.makeText(LoginActivity.this, "Login failed: " + errorBody, Toast.LENGTH_LONG).show();
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading error body", e);
                        Toast.makeText(LoginActivity.this, "Unexpected server error", Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                loginProgress.setVisibility(View.GONE);
                loginButton.setEnabled(true);
                Log.e(TAG, "Login Failure", t);
                Toast.makeText(LoginActivity.this,
                        "Network error: " + (t.getMessage() != null ? t.getMessage() : "Unknown error"),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();
        }
        return false;
    }
}
