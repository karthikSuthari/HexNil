package com.example.hexnil;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.service.UpdateImpactObserver;
import com.example.hexnil.network.AppwriteClientProvider;
import com.example.hexnil.service.BatteryMonitorService;
import com.example.hexnil.service.RemoteMonitorServer;

/**
 * HexNil Application - OEM-Grade Battery Diagnostics Platform
 * 
 * Initializes core services and ensures autonomous operation:
 * - Database initialization
 * - Notification channels
 * - Battery monitoring service (always running)
 * - Remote server (optional, user-controlled)
 */
public class HexNilApplication extends Application {

    private static final String TAG = "HexNilApplication";
    private static final String CHANNEL_ID_MONITOR = "hexnil_battery_monitor";
    private static final String CHANNEL_ID_REMOTE = "hexnil_remote_server";
    private static final String PREFS_NAME = "hexnil_prefs";
    private static final String KEY_REMOTE_ENABLED = "remote_server_enabled";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "HexNil Application starting...");
        
        // Initialize database
        HexNilDatabase.getInstance(this);
        Log.d(TAG, "Database initialized");
        
        // Initialize Appwrite client
        AppwriteClientProvider.get(this);
        Log.d(TAG, "Appwrite client initialized");
        
        // Create notification channels
        createNotificationChannels();
        Log.d(TAG, "Notification channels created");
        
        // Lock pre-update baseline for Silent Update Impact Observer
        // This runs on every app start so a fresh baseline exists before any OTA
        try {
            UpdateImpactObserver.getInstance(this).refreshBaselineIfNeeded();
            Log.d(TAG, "Update impact baseline check initiated");
        } catch (Exception e) {
            Log.e(TAG, "Failed to refresh update baseline", e);
        }
        
        // Start battery monitoring service (always)
        BatteryMonitorService.start(this);
        Log.i(TAG, "Battery monitoring service started");
        
        // Start remote server if it was enabled before
        if (isRemoteServerEnabled()) {
            RemoteMonitorServer.start(this);
            Log.i(TAG, "Remote server started (was previously enabled)");
        }
        
        Log.i(TAG, "HexNil Application started successfully");
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;
            
            // Battery Monitor Channel
            NotificationChannel monitorChannel = new NotificationChannel(
                    CHANNEL_ID_MONITOR,
                    "Battery Monitor",
                    NotificationManager.IMPORTANCE_LOW
            );
            monitorChannel.setDescription("HexNil battery monitoring service");
            monitorChannel.setShowBadge(false);
            manager.createNotificationChannel(monitorChannel);
            
            // Remote Server Channel
            NotificationChannel remoteChannel = new NotificationChannel(
                    CHANNEL_ID_REMOTE,
                    "Remote Server",
                    NotificationManager.IMPORTANCE_LOW
            );
            remoteChannel.setDescription("HexNil remote monitoring server");
            remoteChannel.setShowBadge(false);
            manager.createNotificationChannel(remoteChannel);
        }
    }
    
    public boolean isRemoteServerEnabled() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getBoolean(KEY_REMOTE_ENABLED, false);
    }
    
    public void setRemoteServerEnabled(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_REMOTE_ENABLED, enabled).apply();
    }
}
