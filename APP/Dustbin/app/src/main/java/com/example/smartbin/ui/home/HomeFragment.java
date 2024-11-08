package com.example.smartbin.ui.home;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartbin.R;
import com.example.smartbin.databinding.FragmentHomeBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private Timer timer;
    private Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // Initialize the clock update
        startClock();

        // Initialize sensor readings update
        updateSensorReadings();

        return root;
    }

    private void startClock() {
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.post(() -> {
                    String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                    binding.digitalClock.setText(currentTime);
                });
            }
        }, 0, 1000);
    }

    private void updateSensorReadings() {
        // Simulated data; replace with actual sensor reading code
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String sensorData = "Sensor Readings: " + getSensorData();
                binding.sensorReadings.setText(sensorData);

                // Repeat this every 5 seconds for updated sensor data
                handler.postDelayed(this, 5000);
            }
        }, 5000);
    }

    private String getSensorData() {
        // Placeholder for actual sensor data
        return "80% Full";  // Replace with actual sensor integration
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        timer.cancel(); // Stop the clock updates
        binding = null;
    }
}
