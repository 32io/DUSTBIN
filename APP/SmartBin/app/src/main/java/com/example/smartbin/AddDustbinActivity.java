package com.example.smartbin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AddDustbinActivity extends AppCompatActivity {
    private static final String TAG = "AddDustbinActivity";

    private EditText dustbinIdInput;
    private EditText locationInput;
    private Button addButton;
    private SmartBinApiService smartBinApiService;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_dustbin);

        // Initialize API service and SharedPreferences
        smartBinApiService = new SmartBinApiService(this);
        sharedPreferences = getSharedPreferences("SmartBinPrefs", Context.MODE_PRIVATE);

        // Find UI elements
        dustbinIdInput = findViewById(R.id.dustbinIdInput);
        locationInput = findViewById(R.id.locationInput);
        addButton = findViewById(R.id.addButton);

        // Set up add button click listener
        addButton.setOnClickListener(v -> validateAndAddDustbin());
    }

    private void validateAndAddDustbin() {
        String dustbinId = dustbinIdInput.getText().toString().trim();
        String location = locationInput.getText().toString().trim();
        String email = sharedPreferences.getString("user_email", null);  // Get email from SharedPreferences

        // Validate input
        if (dustbinId.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (email == null) {
            Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Directly add the dustbin using the existing session
        smartBinApiService.addDustbin(dustbinId, location, email, new SmartBinApiService.SimpleCallback() {
            @Override
            public void onSuccess() {
                // Dustbin added successfully
                Toast.makeText(AddDustbinActivity.this,
                        "Dustbin added successfully",
                        Toast.LENGTH_SHORT).show();

                // Navigate to DashboardActivity
                Intent intent = new Intent(AddDustbinActivity.this, DashboardActivity.class);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                // Handle dustbin addition error with detailed logging
                Log.e(TAG, "Failed to add dustbin: " + errorMessage);
                Toast.makeText(AddDustbinActivity.this,
                        "Failed to add dustbin: " + errorMessage,
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
