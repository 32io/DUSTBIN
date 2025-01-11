package com.example.smartbin;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

//Implementation yet to be completed. Awaiting modification of server side code.
public class ScheduleFragment extends Fragment {
    private EditText dateInput, timeInput;
    private Button scheduleButton;
    private TextView scheduleStatus;

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_schedule, container, false);

        // Initialize the UI elements
        dateInput = view.findViewById(R.id.date_input);
        timeInput = view.findViewById(R.id.time_input);
        scheduleButton = view.findViewById(R.id.schedule_button);
        scheduleStatus = view.findViewById(R.id.schedule_status);

        // Set up the schedule trash collection functionality
        scheduleButton.setOnClickListener(v -> scheduleTrashCollection());

        return view;
    }

    private void scheduleTrashCollection() {
        String dateString = dateInput.getText().toString();
        String timeString = timeInput.getText().toString();

        // Validate the input and schedule the trash collection
        if (isValidSchedule(dateString, timeString)) {
            // Save the schedule and send a notification
            saveSchedule(dateString, timeString);
            scheduleStatus.setText("Trash collection scheduled successfully!");
            scheduleStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.green));
        } else {
            scheduleStatus.setText("Invalid date or time. Please try again.");
            scheduleStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.red));
        }
    }

    private boolean isValidSchedule(String dateString, String timeString) {
        // Implement the logic to validate the date and time input
        // You can use the Java Date and Time API for this
        return true;
    }

    private void saveSchedule(String dateString, String timeString) {
        // Implement the logic to save the schedule and send a notification
    }
}