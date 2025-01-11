package com.example.smartbin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class DashboardActivity extends AppCompatActivity {
    private static final String TAG = "DashboardActivity";
    private static final String PREFS_NAME = "SmartBinPrefs";
    private static final String BASE_URL = "https://bromeo.pythonanywhere.com/";
    private static final String SESSION_COOKIE_KEY = "session_cookie";

    // Custom CookieJar that saves and loads cookies from SharedPreferences
    private class PersistentCookieJar implements CookieJar {
        private SharedPreferences sharedPreferences;

        public PersistentCookieJar(Context context) {
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            // Find and save the session cookie
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

        try {
            // Verify session cookie exists
            if (!isSessionCookiePresent()) {
                Log.e(TAG, "No session cookie found, redirecting to login");
                redirectToLogin();
                return;
            }

            setContentView(R.layout.activity_dashboard);

            // Log session cookie details
            logSessionCookieDetails();

            // Setup buttons
            setupDashboardButtons();

        } catch (Exception e) {
            Log.e(TAG, "Critical error in onCreate", e);
            Toast.makeText(this, "Fatal error loading dashboard: " + e.getMessage(), Toast.LENGTH_LONG).show();
            redirectToLogin();
        }
    }

    private boolean isSessionCookiePresent() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String sessionCookie = prefs.getString(SESSION_COOKIE_KEY, null);

        boolean isCookiePresent = sessionCookie != null && !sessionCookie.isEmpty();
        Log.d(TAG, "Session cookie present: " + isCookiePresent);
        return isCookiePresent;
    }

    private void logSessionCookieDetails() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String sessionCookie = prefs.getString(SESSION_COOKIE_KEY, null);

        Log.d(TAG, "Session Cookie Details:");
        Log.d(TAG, "Cookie present: " + (sessionCookie != null));
        Log.d(TAG, "Cookie length: " + (sessionCookie != null ? sessionCookie.length() : "N/A"));
    }

    private void setupDashboardButtons() {
        Button addDustbinButton = findViewById(R.id.btnAddDustbin);
        Button viewDustbinsButton = findViewById(R.id.btnViewDustbins);

        // Existing button setup logic with added error handling
        if (addDustbinButton != null) {
            addDustbinButton.setOnClickListener(v -> {
                Log.d(TAG, "Add Dustbin button clicked");
                try {
                    Intent intent = new Intent(DashboardActivity.this, AddDustbinActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting AddDustbinActivity", e);
                    Toast.makeText(DashboardActivity.this,
                            "Unable to open Add Dustbin: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        } else {
            Log.e(TAG, "Add Dustbin button is null");
            Toast.makeText(this, "UI Error: Add Dustbin button missing", Toast.LENGTH_LONG).show();
        }

        if (viewDustbinsButton != null) {
            viewDustbinsButton.setOnClickListener(v -> {
                Log.d(TAG, "View Dustbins button clicked");
                try {
                    Intent intent = new Intent(DashboardActivity.this, ViewDustbinsActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Error starting ViewDustbinsActivity", e);
                    Toast.makeText(DashboardActivity.this,
                            "Unable to open View Dustbins: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        } else {
            Log.e(TAG, "View Dustbins button is null");
            Toast.makeText(this, "UI Error: View Dustbins button missing", Toast.LENGTH_LONG).show();
        }
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // Utility method to create Retrofit client with session cookie support
    public Retrofit createRetrofitClient() {
        PersistentCookieJar cookieJar = new PersistentCookieJar(this);

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient httpClient = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .addInterceptor(loggingInterceptor)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}