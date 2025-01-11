package com.example.smartbin;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.smartbin.ViewDustbinsActivity.DustbinModel;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeDashboardPollingService {
    private static final String TAG = "HomeDashboardPolling";
    private static final int POLLING_INTERVAL = 3000; // 3 seconds
    private static final int RETRY_INTERVAL = 5000; // 5 seconds for retry after error

    private final SmartBinApiService apiService;
    private final Handler pollingHandler;
    private final MutableLiveData<List<DustbinModel>> dashboardData;
    private final MutableLiveData<ConnectionState> connectionState;
    private boolean isPolling = false;

    public enum ConnectionState {
        CONNECTED,
        DISCONNECTED,
        ERROR
    }

    public HomeDashboardPollingService(Context context) {
        this.apiService = new SmartBinApiService(context);
        this.pollingHandler = new Handler(Looper.getMainLooper());
        this.dashboardData = new MutableLiveData<>(new ArrayList<>());
        this.connectionState = new MutableLiveData<>(ConnectionState.DISCONNECTED);
    }

    private final Runnable pollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPolling) return;

            fetchDustbinStates();

            // Schedule next poll
            pollingHandler.postDelayed(this, POLLING_INTERVAL);
        }
    };

    private void fetchDustbinStates() {
        // First get the list of dustbins
        apiService.getDustbins(new Callback<List<DustbinModel>>() {
            @Override
            public void onResponse(Call<List<DustbinModel>> call, Response<List<DustbinModel>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<DustbinModel> bins = response.body();
                    Log.d(TAG, "Received " + bins.size() + " dustbins");

                    // For each bin, fetch its current state
                    for (DustbinModel bin : bins) {
                        fetchBinState(bin.getDustbinId());
                    }

                    dashboardData.postValue(bins);
                    connectionState.postValue(ConnectionState.CONNECTED);
                } else {
                    handleError("Server error: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<DustbinModel>> call, Throwable t) {
                handleError("Network error: " + t.getMessage());
            }
        });
    }

    private void fetchBinState(String binId) {
        apiService.getDustbinState(binId, new Callback<DustbinStateResponse>() {
            @Override
            public void onResponse(Call<DustbinStateResponse> call, Response<DustbinStateResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    DustbinStateResponse stateResponse = response.body();
                    updateBinState(binId, stateResponse.getState());
                }
            }

            @Override
            public void onFailure(Call<DustbinStateResponse> call, Throwable t) {
                Log.e(TAG, "Error fetching state for bin " + binId + ": " + t.getMessage());
            }
        });
    }

    private void updateBinState(String binId, String newState) {
        List<DustbinModel> currentBins = dashboardData.getValue();
        if (currentBins == null) return;

        List<DustbinModel> updatedBins = new ArrayList<>(currentBins);
        for (int i = 0; i < updatedBins.size(); i++) {
            DustbinModel bin = updatedBins.get(i);
            if (bin.getDustbinId().equals(binId)) {
                bin.setState(newState);
                updatedBins.set(i, bin);
                break;
            }
        }
        dashboardData.postValue(updatedBins);
    }

    private void handleError(String error) {
        Log.e(TAG, error);
        connectionState.postValue(ConnectionState.ERROR);

        // Retry after delay
        pollingHandler.postDelayed(pollingRunnable, RETRY_INTERVAL);
    }

    public void startPolling() {
        if (!isPolling) {
            isPolling = true;
            pollingHandler.post(pollingRunnable);
            Log.d(TAG, "Started polling service");
        }
    }

    public void stopPolling() {
        isPolling = false;
        pollingHandler.removeCallbacks(pollingRunnable);
        connectionState.postValue(ConnectionState.DISCONNECTED);
        Log.d(TAG, "Stopped polling service");
    }

    public void forceRefresh() {
        Log.d(TAG, "Force refreshing data");
        fetchDustbinStates();
    }

    public LiveData<List<DustbinModel>> getDashboardData() {
        return dashboardData;
    }

    public LiveData<ConnectionState> getConnectionState() {
        return connectionState;
    }

    // Response class for dustbin state
    public static class DustbinStateResponse {
        private String state;

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }
    }
}