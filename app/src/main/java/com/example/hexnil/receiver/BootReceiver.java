package com.example.hexnil.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.hexnil.service.BatteryMonitorService;
import com.example.hexnil.service.RemoteMonitorServer;

/**
 * Boot receiver to start battery monitoring service on device boot.
 * Ensures HexNil starts automatically after device restart for uninterrupted monitoring.
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";
    private static final String PREFS_NAME = "hexnil_prefs";
    private static final String KEY_REMOTE_ENABLED = "remote_server_enabled";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
            "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            Log.i(TAG, "Device booted - starting HexNil services");
            
            // Start the battery monitoring service
            try {
                BatteryMonitorService.start(context);
                Log.d(TAG, "Battery monitoring service started");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start monitoring service", e);
            }
            
            // Start remote server if it was enabled
            try {
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                boolean remoteEnabled = prefs.getBoolean(KEY_REMOTE_ENABLED, false);
                if (remoteEnabled) {
                    RemoteMonitorServer.start(context);
                    Log.d(TAG, "Remote server started");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to start remote server", e);
            }
        }
    }
}
