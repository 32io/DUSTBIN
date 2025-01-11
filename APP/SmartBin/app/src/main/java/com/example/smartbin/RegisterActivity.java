package com.example.smartbin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputLayout;
import okhttp3.Cookie;
import okhttp3.OkHttpClient;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

public class RegisterActivity extends AppCompatActivity {

    private static final String TAG = "RegisterActivity";

    // API Interface
    public interface RegisterApiService {
        @POST("signup")
        Call<SignupResponse> signupUser(@Body SignupRequest signupRequest);

        @POST("add_dustbin")
        Call<DustbinResponse> registerDustbin(@Body DustbinRequest dustbinRequest);
    }

    // User Signup Request Model
    public static class SignupRequest {
        private String email;
        private String password;

        public SignupRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }
    }

    // User Signup Response Model
    public static class SignupResponse {
        private boolean success;
        private String message;

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    // Dustbin Registration Request Model
    public static class DustbinRequest {
        private String dustbin_id;
        private String location;
        private String state;

        public DustbinRequest(String dustbin_id, String location) {
            this.dustbin_id = dustbin_id;
            this.location = location;
            this.state = "empty";  // Default state as per server implementation
        }
    }

    // Dustbin Registration Response Model
    public static class DustbinResponse {
        private boolean success;
        private String message;

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    private EditText editTextEmail, editTextPassword, editTextBinId, editTextLocation;
    private TextInputLayout inputLayoutEmail, inputLayoutPassword, inputLayoutBinId, inputLayoutLocation;
    private Button buttonRegister;
    private ProgressBar progressBar;
    private RegisterApiService registerApiService;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        sharedPreferences = getSharedPreferences("SmartBinPrefs", Context.MODE_PRIVATE);

        initializeViews();
        setupApiService();
        setupInputValidation();
        setupRegisterButton();
    }

    private void initializeViews() {
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextBinId = findViewById(R.id.editTextBinId);
        editTextLocation = findViewById(R.id.editTextLocation);

        inputLayoutEmail = findViewById(R.id.inputLayoutEmail);
        inputLayoutPassword = findViewById(R.id.inputLayoutPassword);
        inputLayoutBinId = findViewById(R.id.inputLayoutBinId);
        inputLayoutLocation = findViewById(R.id.inputLayoutLocation);

        buttonRegister = findViewById(R.id.buttonRegister);
        progressBar = findViewById(R.id.progressBar);

        progressBar.setVisibility(View.GONE);
    }

    private void setupApiService() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .cookieJar(new SessionCookieJar())
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://bromeo.pythonanywhere.com/")
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        registerApiService = retrofit.create(RegisterApiService.class);
    }

    private class SessionCookieJar implements CookieJar {
        private List<Cookie> cookies = new ArrayList<>();

        @Override
        public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
            this.cookies = cookies;
        }

        @Override
        public List<Cookie> loadForRequest(HttpUrl url) {
            return cookies;
        }
    }

    private void setupInputValidation() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validateInputs();
            }
        };

        editTextEmail.addTextChangedListener(textWatcher);
        editTextPassword.addTextChangedListener(textWatcher);
        editTextBinId.addTextChangedListener(textWatcher);
        editTextLocation.addTextChangedListener(textWatcher);
    }

    private void setupRegisterButton() {
        buttonRegister.setOnClickListener(v -> {
            if (validateInputs()) {
                registerUser();
            }
        });
    }

    private boolean validateInputs() {
        boolean isValid = true;

        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String binId = editTextBinId.getText().toString().trim();
        String location = editTextLocation.getText().toString().trim();

        // Email validation
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            inputLayoutEmail.setError("Valid email is required");
            isValid = false;
        } else {
            inputLayoutEmail.setError(null);
        }

        // Password validation
        if (password.isEmpty() || password.length() < 6) {
            inputLayoutPassword.setError("Password must be at least 6 characters");
            isValid = false;
        } else {
            inputLayoutPassword.setError(null);
        }

        // Bin ID validation
        if (binId.isEmpty()) {
            inputLayoutBinId.setError("Bin ID is required");
            isValid = false;
        } else {
            inputLayoutBinId.setError(null);
        }

        // Location validation
        if (location.isEmpty()) {
            inputLayoutLocation.setError("Location is required");
            isValid = false;
        } else {
            inputLayoutLocation.setError(null);
        }

        return isValid;
    }

    private void registerUser() {
        progressBar.setVisibility(View.VISIBLE);
        buttonRegister.setEnabled(false);

        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        SignupRequest signupRequest = new SignupRequest(email, password);

        registerApiService.signupUser(signupRequest).enqueue(new Callback<SignupResponse>() {
            @Override
            public void onResponse(Call<SignupResponse> call, Response<SignupResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "User registered successfully: " + response.body().getMessage());
                    // Save the email in SharedPreferences
                    sharedPreferences.edit().putString("user_email", email).apply();

                    // User registration successful, proceed to register dustbin
                    registerDustbin();
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        handleRegistrationError("Registration failed: " + errorBody);
                        Log.e(TAG, "Error Body: " + errorBody);
                    } catch (IOException e) {
                        handleRegistrationError("Registration failed: Error reading error body");
                        Log.e(TAG, "Error reading error body", e);
                    }
                }
            }

            @Override
            public void onFailure(Call<SignupResponse> call, Throwable t) {
                handleRegistrationError("Network error: " + t.getMessage());
                Log.e(TAG, "Signup Failure", t);
            }
        });
    }

    private void registerDustbin() {
        String binId = editTextBinId.getText().toString().trim();
        String location = editTextLocation.getText().toString().trim();
        String email = sharedPreferences.getString("user_email", null);  // Retrieve email from SharedPreferences

        if (email == null) {
            handleRegistrationError("Email is required for dustbin registration");
            return;
        }

        DustbinRequest dustbinRequest = new DustbinRequest(binId, location);

        registerApiService.registerDustbin(dustbinRequest).enqueue(new Callback<DustbinResponse>() {
            @Override
            public void onResponse(Call<DustbinResponse> call, Response<DustbinResponse> response) {
                progressBar.setVisibility(View.GONE);
                buttonRegister.setEnabled(true);

                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "Dustbin registered successfully: " + response.body().getMessage());
                    Toast.makeText(RegisterActivity.this,
                            "Registration completed successfully!",
                            Toast.LENGTH_SHORT).show();
                    navigateToLogin();
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        handleRegistrationError("Dustbin registration failed: " + errorBody);
                        Log.e(TAG, "Error Body: " + errorBody);
                    } catch (IOException e) {
                        handleRegistrationError("Dustbin registration failed: Error reading error body");
                        Log.e(TAG, "Error reading error body", e);
                    }
                }
            }

            @Override
            public void onFailure(Call<DustbinResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                buttonRegister.setEnabled(true);
                handleRegistrationError("Network error: " + t.getMessage());
                Log.e(TAG, "Dustbin Registration Failure", t);
            }
        });
    }

    private void handleRegistrationError(String errorMessage) {
        progressBar.setVisibility(View.GONE);
        buttonRegister.setEnabled(true);
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}

