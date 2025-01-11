package com.example.smartbin.ui.home;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.IOException;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.smartbin.LoginActivity;
import com.example.smartbin.R;
import com.example.smartbin.SmartBinApiService;
import com.example.smartbin.ViewDustbinsActivity.DustbinModel;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HomeFragment extends Fragment {
    private static final String TAG = "HomeFragment";
    private static final String PREFS_NAME = "SmartBinPrefs";
    private static final long POLLING_INTERVAL = 5000; // 5 seconds

    private TextView binFillLevel;
    private TextView collectionStatus;
    private TextView realTimeClock;
    private TextView binStateDetails;
    private CircularProgressIndicator binFillProgressBar;
    private TextView lastUpdatedText;
    private TextView dateDisplay;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ConstraintLayout loadingOverlay;
    private Chip temperatureChip;
    private Chip humidityChip;
    private ExtendedFloatingActionButton scheduleFab;

    private SharedPreferences sharedPreferences;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private Handler pollingHandler;
    private boolean isPolling = false;
    private int lastKnownPercentage = -1; // Track last known percentage to avoid unnecessary updates

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClockAndDate();
            clockHandler.postDelayed(this, 1000);
        }
    };

    private final Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (isPolling && isAdded() && getView() != null) {
                fetchBinState();
                pollingHandler.postDelayed(this, POLLING_INTERVAL);
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        initializeViews(view);
        initializeServices();
        setupSwipeRefresh();
        setupFab();
        startClock();

        pollingHandler = new Handler(Looper.getMainLooper());

        String userEmail = sharedPreferences.getString(LoginActivity.getKeyEmail(), null);
        if (userEmail != null) {
            setupPollingService();
            showLoadingState(true);
        } else {
            showNotLoggedInMessage();
        }

        return view;
    }

    private void startClock() {
        clockHandler.post(clockRunnable);
    }

    private void initializeViews(View view) {
        realTimeClock = view.findViewById(R.id.real_time_clock);
        dateDisplay = view.findViewById(R.id.date_display);
        binStateDetails = view.findViewById(R.id.bin_state_details);
        binFillProgressBar = view.findViewById(R.id.bin_fill_progress_bar);
        lastUpdatedText = view.findViewById(R.id.last_updated_text);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_layout);
        loadingOverlay = view.findViewById(R.id.loading_overlay);
        temperatureChip = view.findViewById(R.id.temperature_chip);
        humidityChip = view.findViewById(R.id.humidity_chip);
        scheduleFab = view.findViewById(R.id.schedule_fab);

        // Initialize progress bar
        if (binFillProgressBar != null) {
            binFillProgressBar.setVisibility(View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                binFillProgressBar.setMin(0);
            }
            binFillProgressBar.setMax(100);
            binFillProgressBar.setProgress(0); // Set initial progress
        } else {
            Log.e(TAG, "Progress bar not found in layout");
        }
    }

    private void initializeServices() {
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void setupPollingService() {
        fetchBinState();
        startPolling();
    }

    private void startPolling() {
        isPolling = true;
        pollingHandler.post(pollingRunnable);
    }

    private void stopPolling() {
        isPolling = false;
        if (pollingHandler != null) {
            pollingHandler.removeCallbacks(pollingRunnable);
        }
    }

    private void fetchBinState() {
        if (!isAdded() || getView() == null) return;

        SmartBinApiService smartBinApiService = new SmartBinApiService(requireContext());
        smartBinApiService.getDustbins(new Callback<List<DustbinModel>>() {
            @Override
            public void onResponse(Call<List<DustbinModel>> call, Response<List<DustbinModel>> response) {
                if (!isAdded() || getView() == null) return;

                requireActivity().runOnUiThread(() -> {
                    showLoadingState(false);
                    if (response.isSuccessful() && response.body() != null) {
                        List<DustbinModel> dustbins = response.body();
                        if (!dustbins.isEmpty()) {
                            updateUIWithBinData(dustbins.get(0));
                        } else {
                            showSnackbar("No dustbins found", true);
                        }
                    } else {
                        handleApiError(response);
                    }
                });
            }

            @Override
            public void onFailure(Call<List<DustbinModel>> call, Throwable t) {
                if (!isAdded() || getView() == null) return;

                requireActivity().runOnUiThread(() -> {
                    showLoadingState(false);
                    Log.e(TAG, "Network error: " + t.getMessage(), t);
                    showSnackbar("Connection error. Please try again.", true);
                });
            }
        });
    }

    private void handleApiError(Response<List<DustbinModel>> response) {
        String errorMessage = "Failed to fetch bin state";
        try {
            if (response.errorBody() != null) {
                errorMessage += ": " + response.errorBody().string();
            }
        } catch (IOException e) {
            errorMessage += ": Unable to read error details";
        }
        Log.e(TAG, errorMessage);
        showSnackbar(errorMessage, true);
    }

    private void updateUIWithBinData(DustbinModel dustbin) {
        if (!isAdded() || getActivity() == null) {
            Log.d(TAG, "Fragment not attached, skipping UI update");
            return;
        }

        try {
            String binState = dustbin.getState();
            Log.d(TAG, "Raw bin state received: " + binState);

            // Parse the state and update progress bar
            int statePercentage = parseStatePercentage(binState);
            Log.d(TAG, "Calculated fill percentage: " + statePercentage);

            // Only update if the percentage has changed
            if (statePercentage != lastKnownPercentage) {
                lastKnownPercentage = statePercentage;
                if (binFillProgressBar != null) {
                    Log.d(TAG, "Updating progress bar to: " + statePercentage);
                    binFillProgressBar.setProgressCompat(statePercentage, true);
                } else {
                    Log.e(TAG, "Progress bar is null!");
                }
            }

            // Update bin details
            if (binStateDetails != null) {
                String details = String.format(Locale.getDefault(),
                        "Bin ID: %s\nFill Level: %d%%\nLocation: %s",
                        dustbin.getDustbinId(),
                        statePercentage,
                        dustbin.getLocation() != null ? dustbin.getLocation() : "Unknown"
                );
                binStateDetails.setText(details);
            }

            // Update timestamp
            updateLastUpdatedTime();

        } catch (Exception e) {
            Log.e(TAG, "Error updating UI with bin data", e);
            showSnackbar("Error updating bin data", true);
        }
    }

    private int parseStatePercentage(String state) {
        if (state == null) return 0;

        Log.d(TAG, "Parsing state: " + state);

        // First try to parse as a direct number
        try {
            int percentage = Integer.parseInt(state.trim());
            Log.d(TAG, "Parsed direct number: " + percentage);
            return Math.min(Math.max(percentage, 0), 100); // Ensure value is between 0 and 100
        } catch (NumberFormatException e) {
            Log.d(TAG, "Not a direct number, trying other formats");

            // Try parsing "full-X" format
            try {
                String[] parts = state.split("-");
                if (parts.length > 1) {
                    int percentage = Integer.parseInt(parts[1].trim());
                    Log.d(TAG, "Parsed from full-X format: " + percentage);
                    return Math.min(Math.max(percentage, 0), 100);
                }
            } catch (NumberFormatException ex) {
                Log.d(TAG, "Not in full-X format either");
            }
        }

        // Fallback to text-based states
        int mappedValue = mapStateToPercentage(state);
        Log.d(TAG, "Fallback to mapped value: " + mappedValue);
        return mappedValue;
    }

    private int mapStateToPercentage(String state) {
        if (state == null) return 0;

        switch (state.toLowerCase()) {
            case "empty":
                return 0;
            case "quarter":
                return 25;
            case "half":
                return 50;
            case "three_quarters":
                return 75;
            case "full":
                return 100;
            default:
                Log.w(TAG, "Unknown bin state: " + state);
                return 0;
        }
    }

    private void updateLastUpdatedTime() {
        if (lastUpdatedText != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            lastUpdatedText.setText("Last updated: " + sdf.format(new Date()));
        }
    }

    private void updateClockAndDate() {
        if (realTimeClock != null && dateDisplay != null) {
            SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM d", Locale.getDefault());
            Date now = new Date();

            realTimeClock.setText(timeFormat.format(now));
            dateDisplay.setText(dateFormat.format(now));
        }
    }

    private void setupSwipeRefresh() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                showLoadingState(true);
                fetchBinState();
                swipeRefreshLayout.setRefreshing(false);
            });
        }
    }

    private void setupFab() {
        if (scheduleFab != null) {
            scheduleFab.setOnClickListener(v ->
                    showSnackbar("Schedule feature coming soon!", false)
            );
        }
    }

    private void showLoadingState(boolean show) {
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void showSnackbar(String message, boolean isError) {
        View view = getView();
        if (view != null) {
            Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_SHORT);
            if (isError) {
                snackbar.setBackgroundTint(getResources().getColor(R.color.error_color));
            }
            snackbar.show();
        }
    }

    @SuppressLint("SetTextI18n")
    private void showNotLoggedInMessage() {
        if (binStateDetails != null) {
            binStateDetails.setText("Please log in to view bin status");
        }
        showSnackbar("Please log in to view bin status", true);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sharedPreferences.getString(LoginActivity.getKeyEmail(), null) != null) {
            startPolling();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        stopPolling();
    }

    @Override
    public void onDestroyView() {
        stopPolling();
        pollingHandler = null;
        clockHandler.removeCallbacks(clockRunnable);

        // Clear view references
        realTimeClock = null;
        dateDisplay = null;
        binStateDetails = null;
        binFillProgressBar = null;
        lastUpdatedText = null;
        swipeRefreshLayout = null;
        loadingOverlay = null;
        temperatureChip = null;
        humidityChip = null;
        scheduleFab = null;

        super.onDestroyView();
    }
}