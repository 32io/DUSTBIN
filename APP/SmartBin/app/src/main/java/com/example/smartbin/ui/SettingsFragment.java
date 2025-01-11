package com.example.smartbin.ui;


import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;

import com.example.smartbin.R;
import com.google.android.material.switchmaterial.SwitchMaterial;
import android.view.View;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import android.view.ViewGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;



public class SettingsFragment extends Fragment {
    private SwitchMaterial darkModeSwitch;
    private SharedPreferences sharedPreferences;

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // Initialize the UI elements
        darkModeSwitch = view.findViewById(R.id.dark_mode_switch);

        // Retrieve the user's theme preference from SharedPreferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        boolean isDarkModeEnabled = sharedPreferences.getBoolean("dark_mode", false);
        darkModeSwitch.setChecked(isDarkModeEnabled);

        // Set up the dark mode toggle switch
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Save the user's theme preference in SharedPreferences
            sharedPreferences.edit().putBoolean("dark_mode", isChecked).apply();

            // Apply the theme change
            AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        return view;
    }
}