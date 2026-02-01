package com.example.hexnil;

import android.Manifest;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.hexnil.service.BatteryMonitorService;
import com.example.hexnil.service.RemoteMonitorServer;
import com.example.hexnil.ui.AiAnalysisFragment;
import com.example.hexnil.ui.CalibrationFragment;
import com.example.hexnil.ui.ChargingFragment;
import com.example.hexnil.ui.ComplianceFragment;
import com.example.hexnil.ui.DashboardFragment;
import com.example.hexnil.ui.DeviceInfoFragment;
import com.example.hexnil.ui.DrainFragment;
import com.example.hexnil.ui.HealthFragment;
import com.example.hexnil.ui.IdleFragment;
import com.example.hexnil.ui.ReportsFragment;
import com.example.hexnil.ui.SafetyFragment;
import com.example.hexnil.ui.SettingsFragment;
import com.example.hexnil.ui.ThermalFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements ModuleNavigator {

    private static final String TAG = "MainActivity";
    
    private static final String TAG_DASHBOARD = "dashboard";
    private static final String TAG_CHARGING = "charging";
    private static final String TAG_THERMAL = "thermal";
    private static final String TAG_DRAIN = "drain";
    private static final String TAG_SAFETY = "safety";
    private static final String TAG_HEALTH = "health";
    private static final String TAG_IDLE = "idle";
    private static final String TAG_CALIBRATION = "calibration";
    private static final String TAG_AI = "ai";
    private static final String TAG_REPORTS = "reports";
    private static final String TAG_COMPLIANCE = "compliance";
    private static final String TAG_DEVICE = "device";
    private static final String TAG_SETTINGS = "settings";

    private final Map<String, Fragment> fragmentCache = new HashMap<>();
    private final Map<Integer, String> navMap = new HashMap<>();

    // Permission request launcher
    private ActivityResultLauncher<String[]> permissionLauncher;
    
    // Activity result launchers for special permissions
    private ActivityResultLauncher<Intent> usageStatsLauncher;
    private ActivityResultLauncher<Intent> batteryOptimizationLauncher;
    private ActivityResultLauncher<Intent> backgroundLocationLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize permission launcher for runtime permissions
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                results -> {
                    boolean allGranted = true;
                    StringBuilder deniedPermissions = new StringBuilder();
                    for (Map.Entry<String, Boolean> entry : results.entrySet()) {
                        if (!entry.getValue()) {
                            allGranted = false;
                            deniedPermissions.append(entry.getKey().substring(entry.getKey().lastIndexOf('.') + 1)).append(", ");
                        }
                    }
                    if (allGranted) {
                        Log.d(TAG, "All runtime permissions granted");
                        // After runtime permissions, check special permissions
                        checkSpecialPermissions();
                    } else {
                        Log.w(TAG, "Some permissions denied: " + deniedPermissions);
                        Toast.makeText(this, "Some permissions denied - limited battery monitoring", Toast.LENGTH_LONG).show();
                        checkSpecialPermissions();
                    }
                });

        // Initialize launcher for Usage Stats permission
        usageStatsLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (hasUsageStatsPermission()) {
                        Log.d(TAG, "Usage Stats permission granted");
                    } else {
                        Log.w(TAG, "Usage Stats permission denied - topAppDuringCharge will be unavailable");
                    }
                    requestBatteryOptimizationExemption();
                });

        // Initialize launcher for Battery Optimization exemption
        batteryOptimizationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (isIgnoringBatteryOptimizations()) {
                        Log.d(TAG, "Battery optimization exemption granted");
                    } else {
                        Log.w(TAG, "Battery optimization exemption denied - background monitoring may be interrupted");
                    }
                    // After all permissions, start monitoring services
                    startMonitoringServices();
                });
        
        // Initialize launcher for Background Location (Android 10+)
        backgroundLocationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.d(TAG, "Background location settings checked");
                    requestUsageStatsPermission();
                });

        // Request permissions first
        requestRequiredPermissions();

        // Bottom navigation mapping
        navMap.put(R.id.nav_dashboard, TAG_DASHBOARD);
        navMap.put(R.id.nav_charging, TAG_CHARGING);
        navMap.put(R.id.nav_thermal, TAG_THERMAL);
        navMap.put(R.id.nav_drain, TAG_DRAIN);
        navMap.put(R.id.nav_safety, TAG_SAFETY);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            String tag = navMap.get(item.getItemId());
            if (tag != null) {
                showFragment(tag);
                return true;
            }
            return false;
        });

        // Default screen
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_dashboard);
            showFragment(TAG_DASHBOARD);
        }
    }

    /**
     * Request all required runtime permissions.
     * This includes permissions needed for comprehensive battery data collection.
     */
    private void requestRequiredPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        // ====================================================================
        // PHONE STATE - required for TelephonyManager and signal strength
        // ====================================================================
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE);
        }

        // ====================================================================
        // LOCATION - required for getAllCellInfo() on Android 10+,
        // Wi-Fi SSID/BSSID, and network-based data
        // ====================================================================
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        // ====================================================================
        // BLUETOOTH - required on Android 12+ for Bluetooth state
        // ====================================================================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        // ====================================================================
        // POST NOTIFICATIONS - required on Android 13+
        // ====================================================================
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting runtime permissions: " + permissionsToRequest);
            permissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
            // All runtime permissions already granted, check special permissions
            checkSpecialPermissions();
        }
    }

    /**
     * Check and request special permissions that require user to navigate to Settings.
     * These include: Usage Stats, Battery Optimization exemption, Background Location.
     */
    private void checkSpecialPermissions() {
        // Check Background Location permission first (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                showBackgroundLocationDialog();
                return;
            }
        }
        
        // Then check Usage Stats permission
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission();
            return;
        }
        
        // Finally check Battery Optimization exemption
        if (!isIgnoringBatteryOptimizations()) {
            requestBatteryOptimizationExemption();
            return;
        }
        
        // All permissions granted, start services
        startMonitoringServices();
    }
    
    /**
     * Show dialog explaining why background location is needed.
     */
    private void showBackgroundLocationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Background Location Required")
                .setMessage("HexNil needs background location access to monitor battery behavior " +
                        "based on network conditions (cellular/WiFi) even when the app is in the background.\n\n" +
                        "This helps track how different network conditions affect battery drain.\n\n" +
                        "Please select \"Allow all the time\" on the next screen.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        backgroundLocationLauncher.launch(intent);
                    }
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    Log.w(TAG, "Background location permission skipped");
                    requestUsageStatsPermission();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Check if Usage Stats permission is granted.
     * Required for reading topAppDuringCharge and app usage data.
     */
    private boolean hasUsageStatsPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Request Usage Stats permission via Settings intent.
     */
    private void requestUsageStatsPermission() {
        if (hasUsageStatsPermission()) {
            requestBatteryOptimizationExemption();
            return;
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Usage Access Required")
                .setMessage("HexNil needs Usage Access permission to track which apps are running " +
                        "during charging sessions.\n\n" +
                        "This allows the app to populate the \"topAppDuringCharge\" field and " +
                        "analyze how app usage affects battery drain.\n\n" +
                        "Please enable HexNil in the list on the next screen.")
                .setPositiveButton("Grant Permission", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    usageStatsLauncher.launch(intent);
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    Log.w(TAG, "Usage Stats permission skipped");
                    requestBatteryOptimizationExemption();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Check if the app is exempted from battery optimization.
     */
    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }

    /**
     * Request battery optimization exemption.
     * This is CRITICAL for continuous battery monitoring.
     */
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isIgnoringBatteryOptimizations()) {
            startMonitoringServices();
            return;
        }
        
        new AlertDialog.Builder(this)
                .setTitle("Disable Battery Optimization")
                .setMessage("HexNil requires battery optimization exemption to continuously monitor " +
                        "your battery in the background.\n\n" +
                        "Without this:\n" +
                        "• Charging session data may have gaps\n" +
                        "• Voltage/current/temperature curves may be incomplete\n" +
                        "• CC/CV phase detection may be inaccurate\n\n" +
                        "Please select \"Don't optimize\" or \"Unrestricted\" on the next screen.")
                .setPositiveButton("Disable Optimization", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    batteryOptimizationLauncher.launch(intent);
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    Log.w(TAG, "Battery optimization exemption skipped");
                    Toast.makeText(this, 
                            "Warning: Battery data may be incomplete without optimization exemption",
                            Toast.LENGTH_LONG).show();
                    startMonitoringServices();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Start monitoring services after permissions are handled.
     */
    private void startMonitoringServices() {
        BatteryMonitorService.start(this);
        RemoteMonitorServer.start(this);
    }

    /**
     * Fallback cleanup when activity is destroyed
     * (Services also handle their own lifecycle)
     */
    @Override
    protected void onDestroy() {
        BatteryMonitorService.stop(this);
        RemoteMonitorServer.stop(this);
        super.onDestroy();
    }

    @Override
    public void openModule(@NonNull String tag) {
        showFragment(tag);
    }

    private void showFragment(@NonNull String tag) {
        Fragment fragment = fragmentCache.get(tag);
        if (fragment == null) {
            fragment = createFragment(tag);
            fragmentCache.put(tag, fragment);
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment, tag)
                .commit();
    }

    private Fragment createFragment(String tag) {
        switch (tag) {
            case TAG_CHARGING:
                return new ChargingFragment();
            case TAG_THERMAL:
                return new ThermalFragment();
            case TAG_DRAIN:
                return new DrainFragment();
            case TAG_SAFETY:
                return new SafetyFragment();
            case TAG_HEALTH:
                return new HealthFragment();
            case TAG_IDLE:
                return new IdleFragment();
            case TAG_CALIBRATION:
                return new CalibrationFragment();
            case TAG_AI:
                return new AiAnalysisFragment();
            case TAG_REPORTS:
                return new ReportsFragment();
            case TAG_COMPLIANCE:
                return new ComplianceFragment();
            case TAG_DEVICE:
                return new DeviceInfoFragment();
            case TAG_SETTINGS:
                return new SettingsFragment();
            case TAG_DASHBOARD:
            default:
                return new DashboardFragment();
        }
    }
}
