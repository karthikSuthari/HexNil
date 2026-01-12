package com.example.hexnil.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.hexnil.service.BatteryMonitorService;

/**
 * Boot receiver to start battery monitoring service on device boot
 */
public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BootReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            
            Log.d(TAG, "Device booted - starting battery monitor service");
            
            // Start the battery monitoring service
            BatteryMonitorService.start(context);
        }
    }
}
