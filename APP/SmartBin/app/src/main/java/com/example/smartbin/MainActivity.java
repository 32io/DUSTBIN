package com.example.smartbin;
//Dear programmer,
//When I wrote this code, Only God, Claude and I knew how it worked.
//Now, Only God Knows!
//Therefore, if you're trying to optimize this routine and it fails (most certainly),
//please increase this counter as a warning to the next person.
// Total hours wasted here >> 274

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import com.example.smartbin.databinding.ActivityMainBinding;
import com.example.smartbin.databinding.AppBarMainBinding;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;
import androidx.core.view.GravityCompat;
import com.google.android.material.navigation.NavigationView;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements SSEClient.SSEListener {

    private static final String NOTIFICATION_CHANNEL_ID = "smart_bin_channel";
    private static final String SSE_URL = "https://bromeo.pythonanywhere.com/";
    private static final int CRITICAL_FILL_LEVEL = 100;
    private static final String PREF_SESSION_COOKIE = "session_cookie";
    private static final String PREF_NAME = "SmartBinPrefs";

    private ActivityMainBinding binding;
    private AppBarMainBinding appBarBinding;
    private Toolbar toolbar; // Add this at class level
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private AppBarConfiguration mAppBarConfiguration;
    private SSEClient sseClient;
    private NotificationManager notificationManager;
    private Map<String, Integer> binFillLevels = new HashMap<>();
    private TextView badgeTextView;
    private boolean isConnected = false;
    private String sessionCookie = null;
    private NavController navController;
    private SmartBinPollingService pollingService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Bind to app_bar_main.xml
        appBarBinding = AppBarMainBinding.bind(binding.appBarMain);

        // Now use appBarBinding to access the toolbar
        setSupportActionBar(appBarBinding.toolbar);


        setupToolbar();
        initializeNavigation();
        //setupAnimations();
        setupNavigationDrawer();
        setupNotifications();
        initializeSSEClient();
        initializePollingService();

        handleIncomingIntent(getIntent());

        if (savedInstanceState == null) {
            navController.navigate(R.id.nav_home);
        }
    }

    private void setupToolbar() {
        // Since you're using view binding, get the toolbar from the included layout
        AppBarMainBinding appBarBinding = AppBarMainBinding.bind(binding.appBarMain);
        toolbar = appBarBinding.toolbar; // Store reference in class field
        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        binding.drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
    }


    //private void setupAnimations() {
        // Set custom animations for fragment transitions

       // if (navController != null) {
         //   navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
           //     // Apply animations to fragment transitions
             //   FragmentNavigator.Extras extras = new FragmentNavigator.Extras.Builder()
               //         .addSharedElement(toolbar, "toolbar")
                 //       .build();

                // Custom animation for navigation
                //NavOptions navOptions = new NavOptions.Builder()
                  //      .setEnterAnim(R.anim.fade_in)
                    //    .setExitAnim(R.anim.fade_out)
                      //  .setPopEnterAnim(R.anim.fade_in)
                        //.setPopExitAnim(R.anim.fade_out)
                        //.build();


         //   });
      //  }
    //}
    // Add glowing effect to status indicators
  //  private void addGlowingEffect(TextView textView) {
    //    ObjectAnimator glowAnimator = ObjectAnimator.ofFloat(textView, "alpha", 1f, 0.7f);
    //    glowAnimator.setDuration(1500);
      //  glowAnimator.setRepeatMode(ValueAnimator.REVERSE);
        //glowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        //glowAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
      //  glowAnimator.start();
   // }
    private void initializeNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);

        if (navHostFragment == null) {
            throw new IllegalStateException("NavHostFragment not found in layout");
        }

        navController = navHostFragment.getNavController();

        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home, R.id.nav_monitor, R.id.nav_schedule,
                R.id.nav_payment, R.id.nav_payment_history,
                R.id.nav_bin_status, R.id.nav_settings, R.id.nav_analytics)
                .setOpenableLayout(binding.drawerLayout)
                .build();

        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        binding.navView.setNavigationItemSelectedListener(item -> {
            NavigationUI.onNavDestinationSelected(item, navController);
            binding.drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
        if (getIntent().getExtras() == null) {
            navController.navigate(R.id.nav_home);
        }
    }



    private void setupNavigationDrawer() {
        drawerLayout = binding.drawerLayout;
        navigationView = binding.navView;

        View headerView = navigationView.getHeaderView(0);
        badgeTextView = headerView.findViewById(R.id.nav_badge_text);
    }

    private void setupNotifications() {
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Smart Bin Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for Smart Bin status updates");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void initializeSSEClient() {
        String userId = getUserId();
        sseClient = new SSEClient(SSE_URL + "?userId=" + userId, this, sessionCookie);
        connectToSSE();
    }

    private void initializePollingService() {
        pollingService = new SmartBinPollingService(this);
        pollingService.getNotifications().observe(this, notifications -> {
            for (SmartBinPollingService.Notification notification : notifications) {
                processNotification(notification);
            }
        });

        pollingService.getConnectionState().observe(this, state -> {
            switch (state) {
                case CONNECTED:
                    Snackbar.make(binding.getRoot(), "Connected to notification service",
                            Snackbar.LENGTH_SHORT).show();
                    break;
                case DISCONNECTED:
                    Snackbar.make(binding.getRoot(), "Disconnected from notification service",
                            Snackbar.LENGTH_SHORT).show();
                    break;
                case ERROR:
                    Snackbar.make(binding.getRoot(), "Error connecting to notification service",
                            Snackbar.LENGTH_SHORT).show();
                    break;
            }
        });

        pollingService.startPolling();
    }


    private void processNotification(SmartBinPollingService.Notification notification) {
        if ("CRITICAL".equals(notification.getState())) {
            sendNotification("Critical Alert", notification.getMessage());
        }
        if (notification.getDustbinId() != null) {
            try {
                int fillLevel = Integer.parseInt(notification.getState());
                binFillLevels.put(notification.getDustbinId(), fillLevel);
                updateBadgeCount();
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent != null) {
            String dustbinId = intent.getStringExtra("DUSTBIN_ID");
            String dustbinLocation = intent.getStringExtra("DUSTBIN_LOCATION");

            if (dustbinId != null && dustbinLocation != null) {
                Snackbar.make(binding.getRoot(),
                        "Selected Dustbin: " + dustbinId + " at " + dustbinLocation,
                        Snackbar.LENGTH_SHORT).show();
                navController.navigate(R.id.nav_home);
            }
        }
    }
    private void handleBinUpdate(JSONObject message) {
        try {
            // Example logic to process bin update
            String binStatus = message.getString("status"); // Replace "status" with your JSON key
            int fillLevel = message.getInt("fillLevel"); // Replace "fillLevel" with your JSON key
            // Do something with the data
            System.out.println("Bin Status: " + binStatus);
            System.out.println("Fill Level: " + fillLevel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void handleCollectionScheduled(JSONObject message) {
        try {
            // Example logic for collection scheduling
            String scheduledTime = message.getString("scheduledTime"); // Replace with your JSON key
            System.out.println("Collection scheduled for: " + scheduledTime);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handlePaymentConfirmation(JSONObject message) {
        try {
            // Example logic for payment confirmation
            String paymentStatus = message.getString("status"); // Replace with your JSON key
            System.out.println("Payment Status: " + paymentStatus);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private String getUserId() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString("user_id", "default_user");
    }

    private void connectToSSE() {
        try {
            sseClient.connect();
        } catch (Exception e) {
            isConnected = false;
            showConnectivityError();
        }
    }

    private void showConnectivityError() {
        Snackbar.make(binding.getRoot(), "Failed to connect to Smart Bin network",
                Snackbar.LENGTH_SHORT).show();
    }

    private void sendNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        notificationManager.notify(0, builder.build());
    }

    private void updateBadgeCount() {
        long criticalBins = binFillLevels.values().stream()
                .filter(level -> level >= CRITICAL_FILL_LEVEL)
                .count();
        badgeTextView.setText(String.valueOf(criticalBins));
    }

    @Override
    public void onConnectionEstablished() {
        runOnUiThread(() -> {
            isConnected = true;
            Snackbar.make(binding.getRoot(), "Connected to Smart Bin network",
                    Snackbar.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onMessage(JSONObject message) {
        runOnUiThread(() -> {
            try {
                String type = message.getString("type");
                switch (type) {
                    case "bin_update":
                        handleBinUpdate(message);
                        break;
                    case "collection_scheduled":
                        handleCollectionScheduled(message);
                        break;
                    case "payment_confirmation":
                        handlePaymentConfirmation(message);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            Snackbar.make(binding.getRoot(), "Error: " + error,
                    Snackbar.LENGTH_SHORT).show();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pollingService != null) {
            pollingService.stopPolling();
        }
        if (sseClient != null) {
            sseClient.disconnect();
        }
    }
}