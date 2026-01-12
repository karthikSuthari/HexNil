package com.example.hexnil;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.network.AppwriteClientProvider;
import com.example.hexnil.service.BatteryMonitorService;

/**
 * Initializes core services and database on app start.
 */
public class HexNilApplication extends Application {

    private static final String CHANNEL_ID = "hexnil_battery_monitor";

    @Override
    public void onCreate() {
        super.onCreate();
        HexNilDatabase.getInstance(this);
        AppwriteClientProvider.get(this);
        createNotificationChannel();
        BatteryMonitorService.start(this);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Battery Monitor",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("HexNil battery monitoring service");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
