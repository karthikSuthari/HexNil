package com.example.hexnil.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.util.Log;

/**
 * Battery change receiver for capturing battery events
 * Works alongside the monitoring service for event-driven updates
 */
public class BatteryReceiver extends BroadcastReceiver {
    
    private static final String TAG = "BatteryReceiver";
    
    public interface BatteryEventListener {
        void onBatteryChanged(Intent batteryIntent);
        void onPowerConnected();
        void onPowerDisconnected();
        void onBatteryLow();
        void onBatteryOkay();
    }
    
    private static BatteryEventListener listener;
    
    public static void setListener(BatteryEventListener l) {
        listener = l;
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        
        Log.d(TAG, "Battery event: " + action);
        
        switch (action) {
            case Intent.ACTION_BATTERY_CHANGED:
                if (listener != null) {
                    listener.onBatteryChanged(intent);
                }
                logBatteryInfo(intent);
                break;
                
            case Intent.ACTION_POWER_CONNECTED:
                if (listener != null) {
                    listener.onPowerConnected();
                }
                break;
                
            case Intent.ACTION_POWER_DISCONNECTED:
                if (listener != null) {
                    listener.onPowerDisconnected();
                }
                break;
                
            case Intent.ACTION_BATTERY_LOW:
                if (listener != null) {
                    listener.onBatteryLow();
                }
                break;
                
            case Intent.ACTION_BATTERY_OKAY:
                if (listener != null) {
                    listener.onBatteryOkay();
                }
                break;
        }
    }
    
    private void logBatteryInfo(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        
        float percentage = (level * 100.0f) / scale;
        
        Log.d(TAG, String.format(
                "Battery: %.0f%%, Status: %s, Health: %s, Plugged: %s, Voltage: %dmV, Temp: %.1f°C",
                percentage,
                getStatusString(status),
                getHealthString(health),
                getPluggedString(plugged),
                voltage,
                temperature / 10.0f
        ));
    }
    
    private String getStatusString(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL: return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Not Charging";
            default: return "Unknown";
        }
    }
    
    private String getHealthString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Over Voltage";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Cold";
            default: return "Unknown";
        }
    }
    
    private String getPluggedString(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "AC";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Wireless";
            case 0: return "Unplugged";
            default: return "Unknown";
        }
    }
}
