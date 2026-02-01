package com.example.hexnil;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize permission launcher
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                results -> {
                    boolean allGranted = true;
                    for (Boolean granted : results.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (allGranted) {
                        Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Some permissions denied - limited functionality", Toast.LENGTH_LONG)
                                .show();
                    }
                    // Start services after permission request (regardless of result)
                    startMonitoringServices();
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
     */
    private void requestRequiredPermissions() {
        List<String> permissionsToRequest = new ArrayList<>();

        // Phone state - required for TelephonyManager
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE);
        }

        // Location - required for getAllCellInfo() on Android 10+
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        // Bluetooth connect - required on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        // Post notifications - required on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toArray(new String[0]));
        } else {
            // All permissions already granted, start services
            startMonitoringServices();
        }
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
