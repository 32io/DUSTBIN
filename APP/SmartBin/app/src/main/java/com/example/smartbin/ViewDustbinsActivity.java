package com.example.smartbin;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ViewDustbinsActivity extends AppCompatActivity {
    private static final String TAG = "ViewDustbinsActivity";

    private RecyclerView dustbinsRecyclerView;
    private DustbinAdapter dustbinAdapter;
    private ProgressBar loadingProgressBar;
    private SmartBinApiService smartBinApiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_dustbins);

        // Initialize services
        smartBinApiService = new SmartBinApiService(this);

        // Initialize UI components
        dustbinsRecyclerView = findViewById(R.id.dustbins_recycler_view);
        loadingProgressBar = findViewById(R.id.loading_progress_bar);

        // Setup RecyclerView
        dustbinsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        dustbinAdapter = new DustbinAdapter(new ArrayList<>());
        dustbinsRecyclerView.setAdapter(dustbinAdapter);

        // Fetch dustbins
        fetchDustbins();
    }

    private void fetchDustbins() {
        // Show loading progress
        loadingProgressBar.setVisibility(View.VISIBLE);

        smartBinApiService.getDustbins(new Callback<List<DustbinModel>>() {
            @Override
            public void onResponse(Call<List<DustbinModel>> call, Response<List<DustbinModel>> response) {
                runOnUiThread(() -> {
                    loadingProgressBar.setVisibility(View.GONE);

                    if (response.isSuccessful() && response.body() != null) {
                        List<DustbinModel> dustbins = response.body();
                        if (dustbins.isEmpty()) {
                            Toast.makeText(ViewDustbinsActivity.this,
                                    "No dustbins found",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            dustbinAdapter.updateDustbins(dustbins);
                        }
                    } else {
                        String errorMessage = "Failed to fetch dustbins";
                        try {
                            if (response.errorBody() != null) {
                                errorMessage += ": " + response.errorBody().string();
                            }
                        } catch (IOException e) {
                            errorMessage += ": Unable to read error details";
                        }
                        Log.e(TAG, errorMessage);
                        Toast.makeText(ViewDustbinsActivity.this,
                                errorMessage,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(Call<List<DustbinModel>> call, Throwable t) {
                runOnUiThread(() -> {
                    loadingProgressBar.setVisibility(View.GONE);
                    Log.e(TAG, "Failed to fetch dustbins: " + t.getMessage(), t);
                    Toast.makeText(ViewDustbinsActivity.this,
                            "Failed to fetch dustbins: " + t.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // Dustbin Model Class
    public static class DustbinModel {
        @SerializedName("dustbin_id")
        private String dustbinId;

        @SerializedName("location")
        private String location;

        @SerializedName("state")
        private String state;

        // Getters
        public String getDustbinId() {
            return dustbinId;
        }

        public String getLocation() {
            return location;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

    }

    // Adapter for RecyclerView
    public class DustbinAdapter extends RecyclerView.Adapter<DustbinAdapter.DustbinViewHolder> {
        private List<DustbinModel> dustbins;

        public DustbinAdapter(List<DustbinModel> dustbins) {
            this.dustbins = dustbins;
        }

        @NonNull
        @Override
        public DustbinViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dustbin, parent, false);
            return new DustbinViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull DustbinViewHolder holder, int position) {
            DustbinModel dustbin = dustbins.get(position);
            holder.dustbinIdText.setText("Dustbin ID: " + dustbin.getDustbinId());
            holder.locationText.setText("Location: " + dustbin.getLocation());
            holder.stateText.setText("State: " + dustbin.getState());

            // Add click listener to navigate to MainActivity
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(ViewDustbinsActivity.this, MainActivity.class);
                // Optional: You can pass dustbin details if needed
                intent.putExtra("DUSTBIN_ID", dustbin.getDustbinId());
                intent.putExtra("DUSTBIN_LOCATION", dustbin.getLocation());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return dustbins.size();
        }

        public void updateDustbins(List<DustbinModel> dustbins) {
            this.dustbins = dustbins;
            notifyDataSetChanged();
        }

        class DustbinViewHolder extends RecyclerView.ViewHolder {
            TextView dustbinIdText, locationText, stateText;

            DustbinViewHolder(@NonNull View itemView) {
                super(itemView);
                dustbinIdText = itemView.findViewById(R.id.dustbin_id_text);
                locationText = itemView.findViewById(R.id.location_text);
                stateText = itemView.findViewById(R.id.state_text);
            }
        }
    }
}