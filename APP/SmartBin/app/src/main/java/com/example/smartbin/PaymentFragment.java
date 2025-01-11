package com.example.smartbin;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import org.json.JSONObject;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PaymentFragment extends Fragment {
    private static final String TAG = "PaymentFragment";

    private TextView fillLevelTextView;
    private EditText phoneNumberEditText, emailEditText;
    private Button payButton;
    private RecyclerView dustbinRecyclerView;
    private DustbinAdapter dustbinAdapter;
    private List<ViewDustbinsActivity.DustbinModel> dustbinList;
    private SmartBinApiService smartBinApiService;
    private int selectedDustbinPosition = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_payment, container, false);

        // Initialize services
        smartBinApiService = new SmartBinApiService(getContext());

        // Initialize UI components
        fillLevelTextView = view.findViewById(R.id.fill_level_text);
        phoneNumberEditText = view.findViewById(R.id.phone_number);
        emailEditText = view.findViewById(R.id.email);
        payButton = view.findViewById(R.id.pay_button);
        dustbinRecyclerView = view.findViewById(R.id.dustbin_recycler_view);

        // Initialize dustbin list
        dustbinList = new ArrayList<>();

        // Setup RecyclerView
        setupDustbinRecyclerView();

        // Fetch dustbins
        fetchDustbins();

        // Setup pay button listener
        payButton.setOnClickListener(v -> initiatePayment());

        return view;
    }

    private void setupDustbinRecyclerView() {
        dustbinAdapter = new DustbinAdapter(dustbinList);
        dustbinRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        dustbinRecyclerView.setAdapter(dustbinAdapter);
    }

    private void fetchDustbins() {
        smartBinApiService.getDustbins(new Callback<List<ViewDustbinsActivity.DustbinModel>>() {
            @Override
            public void onResponse(Call<List<ViewDustbinsActivity.DustbinModel>> call, Response<List<ViewDustbinsActivity.DustbinModel>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    dustbinList.clear(); // Clear existing list
                    dustbinList.addAll(response.body()); // Add new dustbins

                    // Automatically select first dustbin if available
                    if (!dustbinList.isEmpty()) {
                        selectedDustbinPosition = 0;
                    }

                    dustbinAdapter.notifyDataSetChanged();

                    // Update fill level text
                    updateFillLevelText();
                } else {
                    try {
                        String errorMessage = response.errorBody() != null ? response.errorBody().string() : "Failed to fetch dustbins";
                        Toast.makeText(getContext(), errorMessage, Toast.LENGTH_SHORT).show();
                    } catch (IOException e) {
                        Toast.makeText(getContext(), "Failed to fetch dustbins", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<List<ViewDustbinsActivity.DustbinModel>> call, Throwable t) {
                Log.e(TAG, "Error fetching dustbins", t);
                Toast.makeText(getContext(), "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateFillLevelText() {
        if (!dustbinList.isEmpty() && selectedDustbinPosition != -1) {
            ViewDustbinsActivity.DustbinModel selectedDustbin = dustbinList.get(selectedDustbinPosition);
            fillLevelTextView.setText("Dustbin ID: " + selectedDustbin.getDustbinId() +
                    "\nState: " + selectedDustbin.getState());
        }
    }

    private ViewDustbinsActivity.DustbinModel getSelectedDustbin() {
        return (selectedDustbinPosition != -1 && !dustbinList.isEmpty())
                ? dustbinList.get(selectedDustbinPosition)
                : null;
    }

    private void initiatePayment() {
        String phoneNumber = phoneNumberEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();

        // Basic validation
        if (phoneNumber.isEmpty() || email.isEmpty()) {
            Toast.makeText(getContext(), "Please enter phone number and email", Toast.LENGTH_SHORT).show();
            return;
        }

        // Null checks for dustbin list and adapter
        if (dustbinList == null || dustbinList.isEmpty()) {
            Toast.makeText(getContext(), "No dustbins available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get selected dustbin with fallback
        ViewDustbinsActivity.DustbinModel selectedDustbin = getSelectedDustbin();
        if (selectedDustbin == null) {
            Toast.makeText(getContext(), "Please select a dustbin", Toast.LENGTH_SHORT).show();
            return;
        }

        // Prepare payment request
        PaymentRequest paymentRequest = new PaymentRequest(
                email,
                selectedDustbin.getDustbinId(),
                phoneNumber
        );

        // Send payment request
        smartBinApiService.startPayment(paymentRequest, new Callback<PaymentResponse>() {
            @Override
            public void onResponse(Call<PaymentResponse> call, Response<PaymentResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    handlePaymentResponse(response.body());
                } else {
                    try {
                        // More detailed error handling
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        Log.e(TAG, "Full error body: " + errorBody);  // Added logging of full error body

                        String errorMessage = parseErrorMessage(errorBody);

                        Log.e(TAG, "Payment error: " + errorMessage);
                        Toast.makeText(getContext(), "Payment failed: " + errorMessage, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing error response", e);
                        Toast.makeText(getContext(), "An unexpected error occurred", Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<PaymentResponse> call, Throwable t) {
                Log.e(TAG, "Payment request failed", t);
                Toast.makeText(getContext(), "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Improved parseErrorMessage method
    private String parseErrorMessage(String errorBody) {
        if (errorBody == null || errorBody.trim().isEmpty()) {
            return "No error details available";
        }

        try {
            // Additional check to ensure the error body starts with a meaningful JSON structure
            if (!errorBody.trim().startsWith("{")) {
                return errorBody; // Return raw error body if it's not a JSON
            }

            JSONObject errorJson = new JSONObject(errorBody);

            // Multiple paths to extract error message
            String[] possibleErrorPaths = {
                    "details.details.message",
                    "details.error",
                    "error",
                    "message"
            };

            for (String path : possibleErrorPaths) {
                String errorMessage = extractNestedJsonValue(errorJson, path);
                if (errorMessage != null) {
                    return errorMessage;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing JSON", e);
            // Log the actual error body for debugging
            Log.e(TAG, "Raw error body: " + errorBody);
        }

        return "Unknown error occurred";
    }

    // Helper method to safely extract nested JSON values
    private String extractNestedJsonValue(JSONObject json, String path) {
        try {
            String[] keys = path.split("\\.");
            JSONObject currentObj = json;

            for (int i = 0; i < keys.length - 1; i++) {
                if (currentObj.has(keys[i])) {
                    currentObj = currentObj.getJSONObject(keys[i]);
                } else {
                    return null;
                }
            }

            String lastKey = keys[keys.length - 1];
            return currentObj.has(lastKey) ? currentObj.getString(lastKey) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void handlePaymentResponse(PaymentResponse response) {
        // Enhanced response handling
        if (response.hasError()) {
            // If the response indicates an error
            Toast.makeText(getContext(), "Payment error: " + response.getErrorMessage(), Toast.LENGTH_LONG).show();
        } else if (response.getPaymentUrl() != null) {
            // If a payment URL is provided
            Toast.makeText(getContext(), "Payment URL received", Toast.LENGTH_LONG).show();
            // You might want to open the payment URL here
        } else {
            // Generic success message
            Toast.makeText(getContext(), "Payment processed", Toast.LENGTH_LONG).show();
        }
    }

    // Rest of the class remains the same (PaymentRequest, PaymentResponse, DustbinAdapter)
    // ...


    // Payment Request Model
    public static class PaymentRequest {
        private String email;
        private String dustbin_id;
        private String phone;

        public PaymentRequest(String email, String dustbin_id, String phone) {
            this.email = email;
            this.dustbin_id = dustbin_id;
            this.phone = phone;
        }

        public String getEmail() {
            return email;
        }

        public String getDustbinId() {
            return dustbin_id;
        }

        public String getPhone() {
            return phone;
        }
    }

    // Enhanced Payment Response Model
    public static class PaymentResponse {
        private String payment_url;
        private String order_id;
        private String error;
        private String error_details;

        public String getPaymentUrl() {
            return payment_url;
        }

        public String getOrderId() {
            return order_id;
        }

        public boolean hasError() {
            return error != null && !error.isEmpty();
        }

        public String getErrorMessage() {
            return error_details != null ? error_details :
                    error != null ? error :
                            "Unknown error";
        }
    }

    // Dustbin Adapter for RecyclerView (unchanged from previous implementation)
    private class DustbinAdapter extends RecyclerView.Adapter<DustbinAdapter.DustbinViewHolder> {
        private List<ViewDustbinsActivity.DustbinModel> dustbinList;

        public DustbinAdapter(List<ViewDustbinsActivity.DustbinModel> dustbinList) {
            this.dustbinList = dustbinList;
        }

        @NonNull
        @Override
        public DustbinViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dustbin, parent, false);
            return new DustbinViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DustbinViewHolder holder, @SuppressLint("RecyclerView") int position) {
            ViewDustbinsActivity.DustbinModel dustbin = dustbinList.get(position);
            holder.dustbinIdText.setText("Dustbin ID: " + dustbin.getDustbinId());
            holder.locationText.setText("Location: " + dustbin.getLocation());
            holder.stateText.setText("State: " + dustbin.getState());

            holder.itemView.setOnClickListener(v -> {
                // Update selected dustbin position
                selectedDustbinPosition = position;
                notifyDataSetChanged();

                // Update fill level text
                updateFillLevelText();
            });

            // Highlight selected dustbin
            holder.itemView.setBackgroundColor(
                    position == selectedDustbinPosition ?
                            getResources().getColor(android.R.color.holo_blue_light) :
                            getResources().getColor(android.R.color.white)
            );
        }

        @Override
        public int getItemCount() {
            return dustbinList != null ? dustbinList.size() : 0;
        }

        class DustbinViewHolder extends RecyclerView.ViewHolder {
            TextView dustbinIdText, locationText, stateText;

            public DustbinViewHolder(@NonNull View itemView) {
                super(itemView);
                dustbinIdText = itemView.findViewById(R.id.dustbin_id_text);
                locationText = itemView.findViewById(R.id.location_text);
                stateText = itemView.findViewById(R.id.state_text);
            }
        }
    }
}