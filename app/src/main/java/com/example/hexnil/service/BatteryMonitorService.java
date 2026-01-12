package com.example.hexnil.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.hexnil.MainActivity;
import com.example.hexnil.R;
import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.manager.BatteryDataCollector;
import com.example.hexnil.manager.BatteryHealthManager;
import com.example.hexnil.manager.ChargingAnalyzer;
import com.example.hexnil.manager.ThermalMonitor;
import com.example.hexnil.manager.DrainAnalyzer;
import com.example.hexnil.manager.SafetyMonitor;
import com.example.hexnil.manager.IdleDrainAnalyzer;
import com.example.hexnil.manager.CalibrationManager;
import com.example.hexnil.manager.ProtectionManager;
import com.example.hexnil.model.BatterySnapshot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Foreground service for continuous battery monitoring
 * Collects battery data at regular intervals and triggers analysis
 */
public class BatteryMonitorService extends Service {
    
    private static final String TAG = "BatteryMonitorService";
    private static final String CHANNEL_ID = "hexnil_battery_monitor";
    private static final int NOTIFICATION_ID = 1001;
    
    // Monitoring intervals
    private static final long SNAPSHOT_INTERVAL_NORMAL = 60000;      // 1 minute
    private static final long SNAPSHOT_INTERVAL_CHARGING = 30000;    // 30 seconds when charging
    private static final long SNAPSHOT_INTERVAL_LOW_BATTERY = 30000; // 30 seconds when low
    
    private HexNilDatabase database;
    private Handler handler;
    private ExecutorService executor;
    private PowerManager.WakeLock wakeLock;
    
    // Managers
    private BatteryDataCollector dataCollector;
    private BatteryHealthManager batteryHealthManager;
    private ChargingAnalyzer chargingAnalyzer;
    private ThermalMonitor thermalMonitor;
    private DrainAnalyzer drainAnalyzer;
    private IdleDrainAnalyzer idleDrainAnalyzer;
    private SafetyMonitor safetyMonitor;
    private CalibrationManager calibrationManager;
    private ProtectionManager protectionManager;
    
    // State tracking
    private boolean isCharging = false;
    private boolean isLowBattery = false;
    @SuppressWarnings("FieldCanBeLocal") // Updated during snapshot capture
    private int lastLevel = -1;
    @SuppressWarnings("FieldCanBeLocal") // Updated during snapshot capture for future diagnostics
    private long lastSnapshotTime = 0;
    
    // Battery change receiver
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            
            switch (action) {
                case Intent.ACTION_BATTERY_CHANGED:
                    handleBatteryChanged(intent);
                    break;
                case Intent.ACTION_POWER_CONNECTED:
                    handlePowerConnected();
                    break;
                case Intent.ACTION_POWER_DISCONNECTED:
                    handlePowerDisconnected();
                    break;
                case Intent.ACTION_BATTERY_LOW:
                    handleBatteryLow();
                    break;
                case Intent.ACTION_BATTERY_OKAY:
                    handleBatteryOkay();
                    break;
            }
        }
    };
    
    // Periodic snapshot runnable
    private final Runnable snapshotRunnable = new Runnable() {
        @Override
        public void run() {
            captureSnapshot();
            scheduleNextSnapshot();
        }
    };
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Battery Monitor Service created");
        
        database = HexNilDatabase.getInstance(this);
        handler = new Handler(Looper.getMainLooper());
        executor = Executors.newFixedThreadPool(4);
        
        // Initialize managers
        dataCollector = new BatteryDataCollector(this);
        batteryHealthManager = new BatteryHealthManager(this, database);
        chargingAnalyzer = new ChargingAnalyzer(this, database);
        thermalMonitor = new ThermalMonitor(this, database);
        drainAnalyzer = new DrainAnalyzer(this, database);
        idleDrainAnalyzer = new IdleDrainAnalyzer(this, database);
        safetyMonitor = new SafetyMonitor(this, database);
        calibrationManager = new CalibrationManager(this, database);
        protectionManager = new ProtectionManager(database);
        
        // Acquire wake lock for reliable monitoring
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HexNil:BatteryMonitor"
        );
        
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Battery Monitor Service started");
        
        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification());
        
        // Register battery receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        registerReceiver(batteryReceiver, filter);
        
        // Acquire wake lock
        if (!wakeLock.isHeld()) {
            wakeLock.acquire(24 * 60 * 60 * 1000L); // 24 hours max
        }
        
        // Start periodic snapshots
        captureSnapshot();
        scheduleNextSnapshot();
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Battery Monitor Service destroyed");
        
        // Stop snapshots
        handler.removeCallbacks(snapshotRunnable);
        
        // Unregister receiver
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Receiver not registered");
        }
        
        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        // Shutdown executor
        if (executor != null) {
            executor.shutdown();
        }
        
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Battery Monitor",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("HexNil battery monitoring service");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, 
                PendingIntent.FLAG_IMMUTABLE
        );
        
        String statusText = isCharging ? "Monitoring charging..." : "Monitoring battery...";
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("HexNil Battery Monitor")
                .setContentText(statusText)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }
    
    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification());
    }
    
    private void scheduleNextSnapshot() {
        long interval = SNAPSHOT_INTERVAL_NORMAL;
        
        if (isCharging) {
            interval = SNAPSHOT_INTERVAL_CHARGING;
        } else if (isLowBattery) {
            interval = SNAPSHOT_INTERVAL_LOW_BATTERY;
        }
        
        handler.postDelayed(snapshotRunnable, interval);
    }
    
    private void captureSnapshot() {
        executor.execute(() -> {
            try {
                BatterySnapshot snapshot = dataCollector.collectSnapshot();
                
                if (snapshot != null) {
                    // Save to database
                    database.batterySnapshotDao().insert(snapshot);
                    
                    // Update state
                    lastLevel = snapshot.level;
                    lastSnapshotTime = snapshot.timestamp;
                    
                    // Run real-time analysis
                    batteryHealthManager.updateHealth(snapshot);
                    thermalMonitor.analyzeSnapshot(snapshot);
                    safetyMonitor.checkSafety(snapshot);
                    calibrationManager.processSnapshot(snapshot);
                    protectionManager.processSnapshot(snapshot);
                    
                    if (snapshot.isCharging) {
                        chargingAnalyzer.processChargingSnapshot(snapshot);
                    } else {
                        drainAnalyzer.processSnapshot(snapshot);
                    }

                    idleDrainAnalyzer.processSnapshot(snapshot);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error capturing snapshot", e);
            }
        });
    }
    
    private void handleBatteryChanged(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        
        boolean wasCharging = isCharging;
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                     status == BatteryManager.BATTERY_STATUS_FULL;
        
        // Charging state changed - capture immediate snapshot
        if (wasCharging != isCharging) {
            captureSnapshot();
            updateNotification();
            
            if (isCharging) {
                chargingAnalyzer.startChargingSession();
            } else {
                chargingAnalyzer.endChargingSession();
            }
        }
        
        // Significant level change
        if (lastLevel >= 0 && Math.abs(level - lastLevel) >= 5) {
            captureSnapshot();
        }
    }
    
    private void handlePowerConnected() {
        Log.d(TAG, "Power connected");
        isCharging = true;
        captureSnapshot();
        chargingAnalyzer.startChargingSession();
        updateNotification();
    }
    
    private void handlePowerDisconnected() {
        Log.d(TAG, "Power disconnected");
        isCharging = false;
        captureSnapshot();
        chargingAnalyzer.endChargingSession();
        drainAnalyzer.startDrainTracking();
        updateNotification();
    }
    
    private void handleBatteryLow() {
        Log.d(TAG, "Battery low");
        isLowBattery = true;
        captureSnapshot();
        safetyMonitor.checkLowBatterySafety();
    }
    
    private void handleBatteryOkay() {
        Log.d(TAG, "Battery okay");
        isLowBattery = false;
    }
    
    // Public methods for external control
    public static void start(Context context) {
        Intent intent = new Intent(context, BatteryMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
    
    public static void stop(Context context) {
        Intent intent = new Intent(context, BatteryMonitorService.class);
        context.stopService(intent);
    }
}
