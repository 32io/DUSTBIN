package com.example.smartbin;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class MonitorFragment extends Fragment {
    private TextView espStatus, sensorStatus;
    private ProgressBar espProgressBar, sensorProgressBar;

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_monitor, container, false);

        // Initialize the UI elements
        espStatus = view.findViewById(R.id.esp_status);
        sensorStatus = view.findViewById(R.id.sensor_status);
        espProgressBar = view.findViewById(R.id.esp_progress_bar);
        sensorProgressBar = view.findViewById(R.id.sensor_progress_bar);

        // Fetch and display the working status of the ESP8266 and ultrasonic sensor
        updateComponentStatus();

        return view;
    }

    private void updateComponentStatus() {
        // Fetch the working status of the ESP8266 and ultrasonic sensor
        boolean espWorking = isESP8266Working();
        boolean sensorWorking = isUltrasonicSensorWorking();

        // Update the UI elements
        espStatus.setText(espWorking ? "Working" : "Not Working");
        sensorStatus.setText(sensorWorking ? "Working" : "Not Working");

        espProgressBar.setProgress(espWorking ? 100 : 0);
        sensorProgressBar.setProgress(sensorWorking ? 100 : 0);
    }

    private boolean isESP8266Working() {
        // Implement the logic to check the working status of the ESP8266
        return true;
    }

    private boolean isUltrasonicSensorWorking() {
        // Implement the logic to check the working status of the ultrasonic sensor
        return true;
    }
}