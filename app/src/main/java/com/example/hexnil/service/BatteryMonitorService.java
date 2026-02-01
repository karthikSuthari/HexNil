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
import android.net.ConnectivityManager;
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
import com.example.hexnil.oem.LimitationsRegistry;
import com.example.hexnil.state.DefaultNetworkTracker;
import com.example.hexnil.state.DeviceStateCache;
import com.example.hexnil.manager.BatteryDataCollector;
import com.example.hexnil.manager.BatteryHealthManager;
import com.example.hexnil.manager.ChargingAnalyzer;
import com.example.hexnil.manager.ThermalMonitor;
import com.example.hexnil.manager.DrainAnalyzer;
import com.example.hexnil.manager.SafetyMonitor;
import com.example.hexnil.manager.IdleDrainAnalyzer;
import com.example.hexnil.manager.CalibrationManager;
import com.example.hexnil.manager.ProtectionManager;
import com.example.hexnil.manager.AppUsageCollector;
import com.example.hexnil.manager.HexNilAI;
import com.example.hexnil.ai.ThermalPerformanceIntelligence;
import com.example.hexnil.intelligence.AppIntelligenceCollector;
import com.example.hexnil.manager.RefreshRateMonitor;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.AppBatteryUsage;
import com.example.hexnil.model.AIAnalysisResult;
import com.example.hexnil.model.CpuGpuLoadSample;
import com.example.hexnil.manager.CpuGpuCollector;
import com.example.hexnil.manager.SystemLoadCollector;
import com.example.hexnil.manager.StressAnalyzer;
import com.example.hexnil.validation.CpuGpuValidator;
import com.example.hexnil.manager.MemoryMonitor;
import com.example.hexnil.manager.AppLaunchTracker;
import com.example.hexnil.model.CrashEvent;
import com.example.hexnil.model.AnrEvent;
import com.example.hexnil.model.DailyAggregate;
import com.example.hexnil.model.RegressionEvent;
import com.example.hexnil.manager.CrashAnrCollector;
import com.example.hexnil.manager.PerformanceAggregator;
import com.example.hexnil.manager.RegressionDetector;
import com.example.hexnil.worker.PerformanceAggregationWorker;
import com.example.hexnil.manager.CellularCollector;
import com.example.hexnil.manager.WifiCollector;
import com.example.hexnil.manager.BluetoothCollector;
import com.example.hexnil.manager.ModemFirmwareTracker;
import com.example.hexnil.model.NetworkCellularSnapshot;
import com.example.hexnil.model.NetworkWifiSnapshot;
import com.example.hexnil.model.NetworkBluetoothSnapshot;
import com.example.hexnil.model.ModemFirmwareEvent;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OEM-Grade Battery Monitoring Foreground Service
 * 
 * Provides continuous, validated battery data collection with:
 * - Adaptive sampling intervals based on device state
 * - Guaranteed unique snapshots (no reuse)
 * - Quality validation and anomaly detection
 * - Event-driven captures for state transitions
 * 
 * SAMPLING STRATEGY:
 * - Charging: 30s (frequent for charge curve analysis)
 * - Discharging: 60s (balanced coverage/efficiency)
 * - Idle/Doze: 5min (minimal overhead when device idle)
 * - Low Battery: 30s (critical state monitoring)
 * - Thermal Event: 15s (rapid monitoring when hot)
 * - State Transition: Immediate capture
 */
public class BatteryMonitorService extends Service {

    private static final String TAG = "BatteryMonitorService";
    private static final String CHANNEL_ID = "hexnil_battery_monitor";
    private static final int NOTIFICATION_ID = 1001;

    // =========================================================================
    // ADAPTIVE SAMPLING INTERVALS (milliseconds)
    // =========================================================================

    /** Normal discharging - balanced sampling */
    private static final long INTERVAL_DISCHARGING = 60000; // 1 minute

    /** Charging - frequent sampling for curve analysis */
    private static final long INTERVAL_CHARGING = 30000; // 30 seconds

    /** Fast charging - more frequent for rapid state changes */
    private static final long INTERVAL_FAST_CHARGING = 20000; // 20 seconds

    /** Low battery (<20%) - critical monitoring */
    private static final long INTERVAL_LOW_BATTERY = 30000; // 30 seconds

    /** Critical battery (<10%) - very frequent */
    private static final long INTERVAL_CRITICAL_BATTERY = 15000; // 15 seconds

    /** Device idle/doze - minimal overhead */
    private static final long INTERVAL_IDLE = 300000; // 5 minutes

    /** High temperature (>40°C) - thermal monitoring */
    private static final long INTERVAL_THERMAL_EVENT = 15000; // 15 seconds

    /** Screen off - reduced sampling */
    private static final long INTERVAL_SCREEN_OFF = 120000; // 2 minutes

    /** Minimum interval between any two snapshots */
    private static final long MIN_SNAPSHOT_INTERVAL = 5000; // 5 seconds

    /** AI Analysis interval - run comprehensive analysis periodically */
    private static final long INTERVAL_AI_ANALYSIS = 30 * 60 * 1000; // 30 minutes

    // =========================================================================
    // THRESHOLDS
    // =========================================================================

    private static final int LOW_BATTERY_THRESHOLD = 20; // 20%
    private static final int CRITICAL_BATTERY_THRESHOLD = 10; // 10%
    private static final int HIGH_TEMP_THRESHOLD = 400; // 40°C (in tenths)
    private static final int CRITICAL_TEMP_THRESHOLD = 450; // 45°C (in tenths)

    // =========================================================================
    // SERVICE STATE
    // =========================================================================

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
    private AppUsageCollector appUsageCollector;
    private HexNilAI hexNilAI;
    private AppIntelligenceCollector appIntelligenceCollector;
    private ThermalPerformanceIntelligence thermalPerfIntelligence;
    private RefreshRateMonitor refreshRateMonitor;
    private PowerManager powerManager;

    // Event-driven state cache (screen, network, charging) — no background polling
    private DeviceStateCache stateCache;
    private DefaultNetworkTracker networkTracker;
    private LimitationsRegistry limitationsRegistry;

    // CPU/GPU/Stress collectors
    private CpuGpuCollector cpuGpuCollector;
    private SystemLoadCollector systemLoadCollector;
    private StressAnalyzer stressAnalyzer;
    private CpuGpuValidator cpuGpuValidator;

    // Performance monitoring collectors
    private MemoryMonitor memoryMonitor;
    private AppLaunchTracker appLaunchTracker;
    private CrashAnrCollector crashAnrCollector;
    private PerformanceAggregator performanceAggregator;
    private RegressionDetector regressionDetector;

    // Network & Connectivity collectors
    private CellularCollector cellularCollector;
    private WifiCollector wifiCollector;
    private BluetoothCollector bluetoothCollector;
    private ModemFirmwareTracker modemFirmwareTracker;

    // AI Analysis tracking
    private volatile long lastPhase2AnalysisTime = 0;
    private static final long INTERVAL_PHASE2_ANALYSIS = 60 * 60 * 1000; // 1 hour
    private volatile long lastAIAnalysisTime = 0;
    private volatile long serviceStartTime = 0;

    // State tracking
    private volatile boolean isCharging = false;
    private volatile boolean isFastCharging = false;
    private volatile boolean isLowBattery = false;
    private volatile boolean isCriticalBattery = false;
    private volatile boolean isHighTemp = false;
    private volatile boolean isScreenOn = true;
    private volatile boolean isDozeMode = false;
    private volatile int lastLevel = -1;
    private volatile int lastTemperature = 0;
    private volatile long lastSnapshotTime = 0;
    private volatile String currentSamplingContext = "normal";
    private volatile long currentInterval = INTERVAL_DISCHARGING;

    // Battery change receiver
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null)
                return;

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
                case Intent.ACTION_SCREEN_ON:
                    handleScreenOn();
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    handleScreenOff();
                    break;
            }
        }
    };

    // Device idle (doze) receiver
    private final BroadcastReceiver dozeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    boolean wasDoze = isDozeMode;
                    isDozeMode = pm.isDeviceIdleMode();
                    if (wasDoze != isDozeMode) {
                        Log.d(TAG, "Doze mode changed: " + isDozeMode);
                        updateSamplingInterval();
                        if (!isDozeMode) {
                            // Exiting doze - capture snapshot
                            captureSnapshot("doze_exit");
                        }
                    }
                }
            }
        }
    };

    // Periodic snapshot runnable
    private final Runnable snapshotRunnable = new Runnable() {
        @Override
        public void run() {
            captureSnapshot(currentSamplingContext);
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

        stateCache = new DeviceStateCache();
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            networkTracker = new DefaultNetworkTracker(cm, stateCache);
            networkTracker.register();
        }
        limitationsRegistry = new LimitationsRegistry(this);

        dataCollector = new BatteryDataCollector(this);
        dataCollector.setDeviceStateCache(stateCache);
        dataCollector.setLimitationsRegistry(limitationsRegistry);
        dataCollector.resetSession();

        batteryHealthManager = new BatteryHealthManager(this, database);
        chargingAnalyzer = new ChargingAnalyzer(this, database);
        thermalMonitor = new ThermalMonitor(this, database);
        drainAnalyzer = new DrainAnalyzer(this, database);
        idleDrainAnalyzer = new IdleDrainAnalyzer(this, database);
        safetyMonitor = new SafetyMonitor(this, database);
        calibrationManager = new CalibrationManager(this, database);
        protectionManager = new ProtectionManager(database);
        appUsageCollector = new AppUsageCollector(this);
        hexNilAI = new HexNilAI(this, database);
        appIntelligenceCollector = new AppIntelligenceCollector(this);
        thermalPerfIntelligence = new ThermalPerformanceIntelligence(database);
        refreshRateMonitor = new RefreshRateMonitor(this, database);

        // Initialize CPU/GPU/Stress collectors
        cpuGpuCollector = new CpuGpuCollector();
        systemLoadCollector = new SystemLoadCollector(this);
        stressAnalyzer = new StressAnalyzer();
        cpuGpuValidator = new CpuGpuValidator();

        // Initialize performance monitoring collectors
        memoryMonitor = new MemoryMonitor(this, database);
        appLaunchTracker = new AppLaunchTracker(this, database);
        crashAnrCollector = new CrashAnrCollector(this, database);
        performanceAggregator = new PerformanceAggregator(this, database);
        regressionDetector = new RegressionDetector(this, database);
        Log.d(TAG, "Performance monitoring collectors initialized");

        // Initialize Network & Connectivity collectors
        cellularCollector = new CellularCollector(this);
        wifiCollector = new WifiCollector(this);
        bluetoothCollector = new BluetoothCollector(this);
        modemFirmwareTracker = new ModemFirmwareTracker(this, database);
        Log.d(TAG, "Network & Connectivity collectors initialized");

        // Track service start time for AI analysis periods
        serviceStartTime = System.currentTimeMillis();

        // Schedule periodic performance aggregation
        schedulePerformanceAggregation();

        // Acquire wake lock for reliable monitoring
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "HexNil:BatteryMonitor");

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Battery Monitor Service started");

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createNotification());

        // Register battery receiver with full set of actions
        IntentFilter batteryFilter = new IntentFilter();
        batteryFilter.addAction(Intent.ACTION_BATTERY_CHANGED);
        batteryFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        batteryFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        batteryFilter.addAction(Intent.ACTION_BATTERY_LOW);
        batteryFilter.addAction(Intent.ACTION_BATTERY_OKAY);
        batteryFilter.addAction(Intent.ACTION_SCREEN_ON);
        batteryFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(batteryReceiver, batteryFilter);

        // Register doze mode receiver (API 23+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            IntentFilter dozeFilter = new IntentFilter();
            dozeFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
            registerReceiver(dozeReceiver, dozeFilter);
        }

        // Acquire wake lock
        if (!wakeLock.isHeld()) {
            wakeLock.acquire(24 * 60 * 60 * 1000L); // 24 hours max
        }

        initializeState();
        captureSnapshot("service_start");
        scheduleNextSnapshot();

        // Start refresh rate monitoring
        if (refreshRateMonitor != null) {
            refreshRateMonitor.startMonitoring();
            Log.d(TAG, "Refresh rate monitoring started");
        }

        // Schedule initial AI analysis after some data is collected
        handler.postDelayed(() -> {
            Log.d(TAG, "Running initial AI analysis after service start");
            triggerImmediateAIAnalysis();
        }, 5 * 60 * 1000); // 5 minutes after start

        return START_STICKY;
    }

    /**
     * Initialize current device state for proper interval selection.
     */
    private void initializeState() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);

            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
            lastLevel = level;
            lastTemperature = temp;
            isLowBattery = level <= LOW_BATTERY_THRESHOLD;
            isCriticalBattery = level <= CRITICAL_BATTERY_THRESHOLD;
            isHighTemp = temp >= HIGH_TEMP_THRESHOLD;
        }

        // Check screen state
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            isScreenOn = pm.isInteractive();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                isDozeMode = pm.isDeviceIdleMode();
            }
        }

        updateSamplingInterval();
        Log.d(TAG, "Initialized state: charging=" + isCharging + ", level=" + lastLevel +
                ", lowBattery=" + isLowBattery + ", highTemp=" + isHighTemp +
                ", screenOn=" + isScreenOn + ", doze=" + isDozeMode +
                ", interval=" + currentInterval + "ms");
    }

    private void schedulePerformanceAggregation() {
        Log.d(TAG, "Scheduling periodic performance aggregation worker (Daily)");

        // Set constraints: requires charging to avoid battery impact during heavy
        // aggregation
        Constraints constraints = new Constraints.Builder()
                .setRequiresCharging(true)
                .build();

        // Schedule for every 6 hours
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                PerformanceAggregationWorker.class,
                6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .addTag("performance_aggregation")
                .build();

        // Enqueue unique work to avoid multiple clones
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "PerformanceAggregation",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest);

        Log.i(TAG, "Performance aggregation worker scheduled successfully (24h period)");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Battery Monitor Service destroyed");

        // Capture final snapshot
        captureSnapshot("service_stop");

        // Stop snapshots
        handler.removeCallbacks(snapshotRunnable);

        if (networkTracker != null) {
            networkTracker.unregister();
        }
        try {
            unregisterReceiver(batteryReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Battery receiver not registered");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                unregisterReceiver(dozeReceiver);
            } catch (Exception e) {
                Log.w(TAG, "Doze receiver not registered");
            }
        }

        // Release wake lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // Shutdown executor
        if (executor != null) {
            executor.shutdown();
        }

        // Shutdown App Intelligence collector
        if (appIntelligenceCollector != null) {
            appIntelligenceCollector.shutdown();
        }

        // Stop refresh rate monitoring
        if (refreshRateMonitor != null) {
            refreshRateMonitor.stopMonitoring();
            Log.d(TAG, "Refresh rate monitoring stopped");
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
                    NotificationManager.IMPORTANCE_LOW);
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
                PendingIntent.FLAG_IMMUTABLE);

        String statusText = buildStatusText();

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("HexNil Battery Monitor")
                .setContentText(statusText)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private String buildStatusText() {
        StringBuilder sb = new StringBuilder();

        if (isCharging) {
            sb.append(isFastCharging ? "⚡ Fast charging" : "🔌 Charging");
        } else {
            sb.append("🔋 Monitoring");
        }

        if (lastLevel >= 0) {
            sb.append(" (").append(lastLevel).append("%)");
        }

        if (isHighTemp) {
            sb.append(" 🌡️");
        }

        if (isLowBattery) {
            sb.append(" ⚠️");
        }

        return sb.toString();
    }

    private void updateNotification() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification());
    }

    // =========================================================================
    // ADAPTIVE SAMPLING INTERVAL LOGIC
    // =========================================================================

    /**
     * Determines the optimal sampling interval based on current device state.
     * Priority order: Thermal > Critical Battery > Charging > Low Battery > Idle >
     * Screen > Normal
     */
    private void updateSamplingInterval() {
        long newInterval;
        String newContext;

        // Highest priority: Thermal events
        if (isHighTemp) {
            newInterval = INTERVAL_THERMAL_EVENT;
            newContext = "thermal_event";
        }
        // Critical battery - very frequent monitoring
        else if (isCriticalBattery) {
            newInterval = INTERVAL_CRITICAL_BATTERY;
            newContext = "critical_battery";
        }
        // Charging states
        else if (isCharging) {
            if (isFastCharging) {
                newInterval = INTERVAL_FAST_CHARGING;
                newContext = "fast_charging";
            } else {
                newInterval = INTERVAL_CHARGING;
                newContext = "charging";
            }
        }
        // Low battery
        else if (isLowBattery) {
            newInterval = INTERVAL_LOW_BATTERY;
            newContext = "low_battery";
        }
        // Device idle (doze mode)
        else if (isDozeMode) {
            newInterval = INTERVAL_IDLE;
            newContext = "idle";
        }
        // Screen off - reduced sampling
        else if (!isScreenOn) {
            newInterval = INTERVAL_SCREEN_OFF;
            newContext = "screen_off";
        }
        // Normal discharging
        else {
            newInterval = INTERVAL_DISCHARGING;
            newContext = "discharging";
        }

        // Only update if changed
        if (newInterval != currentInterval || !newContext.equals(currentSamplingContext)) {
            Log.d(TAG, "Sampling interval changed: " + currentInterval + "ms -> " + newInterval +
                    "ms, context: " + currentSamplingContext + " -> " + newContext);
            currentInterval = newInterval;
            currentSamplingContext = newContext;
        }
    }

    private void scheduleNextSnapshot() {
        handler.postDelayed(snapshotRunnable, currentInterval);
    }

    // =========================================================================
    // SNAPSHOT CAPTURE (CORE LOGIC - FIXED)
    // =========================================================================

    /**
     * Captures a new battery snapshot with full validation.
     * CRITICAL: Every call creates a NEW snapshot with fresh data.
     * 
     * @param context The sampling context triggering this capture
     */
    private void captureSnapshot(String context) {
        // Enforce minimum interval (prevent rapid-fire captures)
        long now = System.currentTimeMillis();
        if (lastSnapshotTime > 0 && (now - lastSnapshotTime) < MIN_SNAPSHOT_INTERVAL) {
            Log.d(TAG, "Skipping snapshot - too soon since last (" +
                    (now - lastSnapshotTime) + "ms < " + MIN_SNAPSHOT_INTERVAL + "ms)");
            return;
        }

        executor.execute(() -> {
            try {
                // CRITICAL: Collect fresh snapshot with validation
                BatterySnapshot snapshot = dataCollector.collectSnapshot(context, currentInterval);

                if (snapshot == null) {
                    Log.e(TAG, "Failed to collect snapshot - null returned");
                    return;
                }

                // Check if snapshot is usable
                if (!snapshot.isUsable()) {
                    Log.w(TAG, "Low quality snapshot captured: " + snapshot.validationNotes);
                    // Still save for diagnostics, but flag it
                }

                // CRITICAL: Save to database - this creates a new row with auto-generated ID
                long insertedId = database.batterySnapshotDao().insert(snapshot);
                snapshot.id = insertedId;

                Log.d(TAG, "Snapshot captured: id=" + insertedId +
                        ", seq=" + snapshot.sequenceNumber +
                        ", level=" + snapshot.level + "%" +
                        ", quality=" + snapshot.qualityScore +
                        ", context=" + context);

                // Update state tracking
                lastLevel = snapshot.level;
                lastTemperature = snapshot.temperature;
                lastSnapshotTime = snapshot.timestamp;

                // Check for state changes that affect sampling
                checkStateChanges(snapshot);

                // Run real-time analysis on valid snapshots
                if (snapshot.isUsable()) {
                    runAnalysis(snapshot);
                }

                // Collect network data (cellular/wifi/bluetooth/modem)
                collectNetworkData(context);

            } catch (Exception e) {
                Log.e(TAG, "Error capturing snapshot", e);
            }
        });
    }

    /**
     * Check if snapshot indicates state changes that require interval adjustment.
     */
    private void checkStateChanges(BatterySnapshot snapshot) {
        boolean changed = false;

        // Check charging state
        if (snapshot.isCharging != isCharging) {
            isCharging = snapshot.isCharging;
            changed = true;
        }

        // Check fast charging
        if (snapshot.isFastCharging != isFastCharging) {
            isFastCharging = snapshot.isFastCharging;
            changed = true;
        }

        // Check low battery threshold
        boolean nowLowBattery = snapshot.level <= LOW_BATTERY_THRESHOLD;
        if (nowLowBattery != isLowBattery) {
            isLowBattery = nowLowBattery;
            changed = true;
        }

        // Check critical battery
        boolean nowCritical = snapshot.level <= CRITICAL_BATTERY_THRESHOLD;
        if (nowCritical != isCriticalBattery) {
            isCriticalBattery = nowCritical;
            changed = true;
        }

        // Check temperature
        boolean nowHighTemp = snapshot.temperature >= HIGH_TEMP_THRESHOLD;
        if (nowHighTemp != isHighTemp) {
            isHighTemp = nowHighTemp;
            changed = true;

            if (nowHighTemp) {
                Log.w(TAG, "High temperature detected: " + snapshot.getTemperatureCelsius() + "°C");
            }
        }

        if (changed) {
            updateSamplingInterval();
            handler.post(this::updateNotification);
        }
    }

    /**
     * Run analysis managers on the captured snapshot.
     */
    private void runAnalysis(BatterySnapshot snapshot) {
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

        // Collect app usage periodically
        collectAppUsage(snapshot);

        // Collect App Intelligence data (isolated module)
        if (appIntelligenceCollector != null) {
            appIntelligenceCollector.collect(snapshot);
        }

        // Sample refresh rate and provide context
        if (refreshRateMonitor != null) {
            refreshRateMonitor.sampleRefreshRate();
            refreshRateMonitor.addContextFromSnapshot(
                    snapshot.getTemperatureCelsius(),
                    snapshot.getCurrentMilliamps(),
                    snapshot.isCharging);
        }

        // Run periodic AI analysis
        checkAndRunAIAnalysis();

        // Collect CPU/GPU/Stress samples
        collectCpuGpuSample(snapshot);

        // =====================================================================
        // Performance Monitoring Data Collection
        // =====================================================================

        // Capture memory snapshot
        if (memoryMonitor != null) {
            memoryMonitor.captureSnapshot(
                    snapshot.level,
                    snapshot.getTemperatureCelsius(),
                    snapshot.isCharging,
                    snapshot.isScreenOnOrFalse());
        }

        // Collect crash/ANR events (Android 11+)
        if (crashAnrCollector != null && crashAnrCollector.isSupported()) {
            crashAnrCollector.collectEvents(
                    snapshot.level,
                    snapshot.getTemperatureCelsius(),
                    memoryMonitor != null && memoryMonitor.isLowMemory());
        }

        // Collect app launch events periodically (every 5 minutes)
        collectAppLaunchMetrics(snapshot);
    }

    /**
     * Collect CPU/GPU metrics and stress analysis, save to database.
     */
    private void collectCpuGpuSample(BatterySnapshot snapshot) {
        try {
            // Collect CPU/GPU metrics
            CpuGpuCollector.CpuGpuMetrics metrics = cpuGpuCollector.collectAll();
            SystemLoadCollector.SystemLoad load = systemLoadCollector.collect();

            // Create sample
            CpuGpuLoadSample sample = new CpuGpuLoadSample();
            sample.timestamp = System.currentTimeMillis();

            // CPU metrics - use collector value, or derive from load average if unavailable
            // Note: On Android 10+, /proc/stat is restricted, so we use load average as
            // proxy
            int cpuPercent = metrics.systemCpuPercent;
            if (cpuPercent < 0 && snapshot.cpuLoadTotal != null && snapshot.cpuLoadTotal > 0) {
                cpuPercent = snapshot.cpuLoadTotal;
            }
            if (cpuPercent < 0 && load.load1m > 0) {
                int numCores = Runtime.getRuntime().availableProcessors();
                cpuPercent = (int) Math.min(100, (load.load1m / numCores) * 100);
            }
            sample.systemCpuPercent = cpuPercent;

            int gpuVal = metrics.gpuPercent >= 0 ? metrics.gpuPercent
                    : (snapshot.gpuLoad != null ? snapshot.gpuLoad : -1);
            sample.gpuPercent = gpuVal;
            sample.isGpuAvailable = metrics.isGpuAvailable;

            // System load
            sample.load1m = load.load1m;
            sample.load5m = load.load5m;
            sample.load15m = load.load15m;
            sample.runnableProcesses = load.runnableProcesses;
            sample.isThrottled = load.isThrottled;
            sample.throttleLevel = load.throttleLevel;

            // System thermal status (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
                sample.thermalStatus = powerManager.getCurrentThermalStatus();
                // If system reports throttling but our collector missed it, sync them
                if (sample.thermalStatus >= PowerManager.THERMAL_STATUS_LIGHT) {
                    sample.isThrottled = true;
                    if (sample.throttleLevel == 0) {
                        sample.throttleLevel = sample.thermalStatus;
                    }
                }
            }

            // Context from battery snapshot
            sample.temperature = snapshot.getTemperatureCelsius();
            sample.batteryLevel = snapshot.level;
            sample.drainRatePerHour = snapshot.dischargeRate;

            // App attribution (top foreground app)
            sample.packageName = snapshot.topAppPackage;
            sample.appName = snapshot.topAppPackage; // Will be resolved later
            sample.sourceType = isScreenOn ? "foreground" : "background";
            sample.isActive = isScreenOn;

            // Add to stress analyzer for trending
            int bright = snapshot.screenBrightness != null ? snapshot.screenBrightness : 0;
            StressAnalyzer.StressSample stressSample = new StressAnalyzer.StressSample(
                    sample.systemCpuPercent, sample.gpuPercent,
                    sample.temperature, sample.batteryLevel,
                    sample.drainRatePerHour, sample.isThrottled, sample.throttleLevel,
                    sample.thermalStatus,
                    snapshot.isScreenOnOrFalse(), bright, snapshot.topAppPackage, snapshot.isCharging);
            stressAnalyzer.addSample(stressSample);

            // Get stress analysis
            StressAnalyzer.StressResult stressResult = stressAnalyzer.analyze();

            // Join observed signals with semicolon for DB storage
            String signals = "";
            if (stressResult.observedSignals != null && !stressResult.observedSignals.isEmpty()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    signals = String.join("; ", stressResult.observedSignals);
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < stressResult.observedSignals.size(); i++) {
                        sb.append(stressResult.observedSignals.get(i));
                        if (i < stressResult.observedSignals.size() - 1)
                            sb.append("; ");
                    }
                    signals = sb.toString();
                }
            }

            sample.setStressResult(stressResult.stressScore, stressResult.stressCategory, stressResult.explanation,
                    stressResult.inferredLoad, stressResult.confidenceLevel,
                    stressResult.detectionMethod, signals);

            // Validate before insert
            CpuGpuValidator.ValidationResult validation = cpuGpuValidator.validate(sample);

            // Save to database (even if warnings, for diagnostics)
            if (validation.isValid || "warning".equals(validation.status)) {
                long insertedId = database.cpuGpuLoadSampleDao().insert(sample);

                // Log high stress situations
                if (stressResult.stressScore >= 50) {
                    Log.w(TAG, "High stress detected: score=" + stressResult.stressScore +
                            " category=" + stressResult.stressCategory +
                            " cause=" + stressResult.explanation);
                } else {
                    Log.d(TAG, "CPU/GPU sample: cpu=" + sample.systemCpuPercent + "%" +
                            ", gpu=" + sample.gpuPercent + "%" +
                            ", stress=" + String.format("%.0f", stressResult.stressScore) +
                            " (" + stressResult.stressCategory + ")");
                }
            } else {
                Log.w(TAG, "CPU/GPU sample rejected: " + validation.notes);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error collecting CPU/GPU sample", e);
        }
    }

    // Last time we collected app launch metrics
    private volatile long lastAppLaunchCollectionTime = 0;
    private static final long APP_LAUNCH_COLLECTION_INTERVAL = 5 * 60 * 1000; // 5 minutes

    // Last time we ran daily aggregation
    private volatile long lastDailyAggregationTime = 0;
    private static final long DAILY_AGGREGATION_INTERVAL = 6 * 60 * 60 * 1000; // 6 hours

    /**
     * Collect app launch metrics periodically.
     * Uses UsageStatsManager to detect app launches.
     */
    private void collectAppLaunchMetrics(BatterySnapshot snapshot) {
        if (appLaunchTracker == null)
            return;

        long now = System.currentTimeMillis();
        if (lastAppLaunchCollectionTime > 0 &&
                (now - lastAppLaunchCollectionTime) < APP_LAUNCH_COLLECTION_INTERVAL) {
            return; // Not time yet
        }

        // Collect launches since last collection
        long sinceTime = lastAppLaunchCollectionTime > 0
                ? lastAppLaunchCollectionTime
                : now - APP_LAUNCH_COLLECTION_INTERVAL;

        float memoryUsagePercent = memoryMonitor != null
                ? memoryMonitor.getCurrentMemoryUsagePercent()
                : 50.0f;

        appLaunchTracker.collectLaunchEvents(
                sinceTime,
                snapshot.level,
                snapshot.getTemperatureCelsius(),
                snapshot.isCharging,
                memoryUsagePercent,
                0 // thermal headroom not available from snapshot
        );

        lastAppLaunchCollectionTime = now;

        // Check if it's time for daily aggregation
        checkAndRunDailyAggregation(now);
    }

    /**
     * Run daily aggregation and regression detection once per day.
     */
    private void checkAndRunDailyAggregation(long now) {
        if (performanceAggregator == null || regressionDetector == null)
            return;

        // Check if 24 hours have passed since last aggregation
        if (lastDailyAggregationTime > 0 &&
                (now - lastDailyAggregationTime) < DAILY_AGGREGATION_INTERVAL) {
            return;
        }

        // Run aggregation in background
        executor.execute(() -> {
            try {
                Log.i(TAG, "Running daily performance aggregation...");
                performanceAggregator.runDailyAggregation();

                // Run regression detection after aggregation completes
                // (delay slightly to ensure aggregation is done)
                handler.postDelayed(() -> {
                    Log.i(TAG, "Running regression detection...");
                    regressionDetector.runDetection();
                }, 5000);

                lastDailyAggregationTime = now;
            } catch (Exception e) {
                Log.e(TAG, "Error in daily aggregation", e);
            }
        });
    }

    /**
     * Check if it's time to run AI analysis and execute if needed.
     * Runs comprehensive AI analysis every INTERVAL_AI_ANALYSIS milliseconds.
     * Runs Phase-2 thermal/performance analysis every INTERVAL_PHASE2_ANALYSIS
     * milliseconds.
     */
    private void checkAndRunAIAnalysis() {
        // Guard: Check if executor is valid before submitting
        if (!isExecutorValid()) {
            Log.w(TAG, "Executor is shutdown, skipping AI analysis");
            return;
        }

        long now = System.currentTimeMillis();

        // Run Phase-2 Thermal/Performance analysis (hourly)
        if (lastPhase2AnalysisTime == 0 || (now - lastPhase2AnalysisTime) >= INTERVAL_PHASE2_ANALYSIS) {
            runPhase2Analysis(now);
        }

        // Check if enough time has passed since last general AI analysis
        if (lastAIAnalysisTime > 0 && (now - lastAIAnalysisTime) < INTERVAL_AI_ANALYSIS) {
            return; // Not time yet
        }

        // Run AI analysis in background
        executor.execute(() -> {
            try {
                Log.d(TAG, "Running periodic AI analysis...");

                // Determine analysis period
                long analysisStartTime;
                String periodLabel;

                if (lastAIAnalysisTime > 0) {
                    // Analyze since last analysis
                    analysisStartTime = lastAIAnalysisTime;
                    periodLabel = "since_last_analysis";
                } else {
                    // First analysis - use service start time or last hour
                    analysisStartTime = Math.max(serviceStartTime, now - (60 * 60 * 1000));
                    periodLabel = "initial_analysis";
                }

                // Run comprehensive AI analysis
                AIAnalysisResult result = hexNilAI.runComprehensiveAnalysis(periodLabel, analysisStartTime);

                if (result != null) {
                    // Store the analysis result
                    database.miscDao().insertAIAnalysis(result);
                    lastAIAnalysisTime = now;

                    Log.d(TAG, "AI Analysis complete: riskScore=" + result.riskScore +
                            ", issues=" + result.issueCount +
                            ", severity=" + result.severity +
                            ", recommendation=" + result.primaryRecommendation);

                    // Trigger notification if high risk detected
                    if (result.riskScore >= 75) {
                        Log.w(TAG, "HIGH RISK DETECTED by AI: " + result.primaryIssue);
                        // Could add a notification here for critical issues
                    }
                } else {
                    Log.w(TAG, "AI Analysis returned null result");
                }

            } catch (Exception e) {
                Log.e(TAG, "Error running AI analysis", e);
            }
        });
    }

    /**
     * Run Phase-2 Thermal/Performance Intelligence Analysis.
     * Identifies root causes of heating, throttling, and performance degradation.
     */
    private void runPhase2Analysis(long now) {
        // Guard: Check if executor is valid before submitting
        if (!isExecutorValid()) {
            Log.w(TAG, "Executor is shutdown, skipping Phase-2 analysis");
            return;
        }

        executor.execute(() -> {
            try {
                Log.d(TAG, "Running Phase-2 thermal/performance analysis...");

                // Determine analysis period
                long periodStart;
                if (lastPhase2AnalysisTime > 0) {
                    periodStart = lastPhase2AnalysisTime;
                } else {
                    // First analysis - last hour
                    periodStart = now - INTERVAL_PHASE2_ANALYSIS;
                }

                // Run Phase-2 analysis
                ThermalPerformanceIntelligence.Phase2Output output = thermalPerfIntelligence.runAnalysis(periodStart,
                        now);

                if (output != null && output.saveResults) {
                    // Store results (engine handles both anomaly_records and ai_analysis_results)
                    thermalPerfIntelligence.storeResults(output);

                    Log.d(TAG, "Phase-2 Analysis complete: severity=" + output.severity.getValue() +
                            ", confidence=" + output.confidence + "%" +
                            ", rootCause="
                            + (output.rootCause != null ? output.rootCause.category.getCategory() : "unknown"));

                    // Alert on critical thermal issues
                    if (output.severity == ThermalPerformanceIntelligence.Severity.CRITICAL ||
                            output.severity == ThermalPerformanceIntelligence.Severity.HIGH) {
                        Log.w(TAG, "THERMAL ALERT: " + output.severity.getValue() +
                                " - " + (output.rootCause != null ? output.rootCause.explanation : "Unknown cause"));
                    }
                } else {
                    Log.d(TAG, "Phase-2 Analysis: No actionable results to store");
                }

                lastPhase2AnalysisTime = now;

            } catch (Exception e) {
                Log.e(TAG, "Error running Phase-2 analysis", e);
            }
        });
    }

    /**
     * Force an immediate AI analysis (can be called from external sources).
     */
    public void triggerImmediateAIAnalysis() {
        // Guard: Check if executor is valid before triggering
        if (!isExecutorValid()) {
            Log.w(TAG, "Executor is shutdown, cannot trigger AI analysis");
            return;
        }
        lastAIAnalysisTime = 0; // Reset timer to force immediate analysis
        checkAndRunAIAnalysis();
    }

    /**
     * Check if executor is valid and can accept tasks.
     * 
     * @return true if executor is available and not shutdown
     */
    private boolean isExecutorValid() {
        return executor != null && !executor.isShutdown() && !executor.isTerminated();
    }

    /**
     * Collect per-app battery usage and store in database
     * Each collection creates separate timestamped records per app
     */
    private void collectAppUsage(BatterySnapshot snapshot) {
        try {
            // Collect app usage for last 5 minutes
            long periodEnd = System.currentTimeMillis();
            long periodStart = periodEnd - (5 * 60 * 1000);
            List<AppBatteryUsage> appUsages = appUsageCollector.collect(5 * 60 * 1000);

            if (appUsages != null && !appUsages.isEmpty()) {
                for (AppBatteryUsage usage : appUsages) {
                    // Set unique timestamp and period for each record
                    usage.timestamp = periodEnd;
                    usage.periodStart = periodStart;
                    usage.periodEnd = periodEnd;

                    // Calculate estimated battery usage based on foreground time
                    float hoursActive = usage.foregroundTime / 3600000.0f;

                    // Estimate battery drain based on time active
                    // Typical drain rate is ~10%/hour for active app
                    usage.batteryPercentUsed = hoursActive * 10.0f;
                    usage.drainPerHour = hoursActive > 0 ? usage.batteryPercentUsed / hoursActive : 0;

                    // Try to get app name
                    try {
                        android.content.pm.PackageManager pm = getPackageManager();
                        android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(usage.packageName, 0);
                        usage.appName = pm.getApplicationLabel(appInfo).toString();
                        usage.isSystemApp = (appInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
                    } catch (Exception e) {
                        usage.appName = usage.packageName;
                        usage.isSystemApp = false;
                    }

                    // Rate efficiency
                    if (usage.drainPerHour > 20) {
                        usage.efficiencyRating = "problematic";
                    } else if (usage.drainPerHour > 15) {
                        usage.efficiencyRating = "inefficient";
                    } else if (usage.drainPerHour > 8) {
                        usage.efficiencyRating = "normal";
                    } else {
                        usage.efficiencyRating = "efficient";
                    }

                    // Store in database
                    database.appBatteryUsageDao().insert(usage);
                }
                Log.d(TAG, "Stored " + appUsages.size() + " app usage records");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error collecting app usage", e);
        }
    }

    private void handleBatteryChanged(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);

        boolean wasCharging = isCharging;
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        // Charging state changed - capture immediate snapshot
        if (wasCharging != isCharging) {
            String context = isCharging ? "charging_start" : "charging_end";
            captureSnapshot(context);
            updateSamplingInterval();
            updateNotification();

            if (isCharging) {
                executor.execute(() -> chargingAnalyzer.startChargingSession());
            } else {
                executor.execute(() -> {
                    chargingAnalyzer.endChargingSession();
                    drainAnalyzer.startDrainTracking();
                });
            }
        }

        // Significant level change (5% or more)
        if (lastLevel >= 0 && Math.abs(level - lastLevel) >= 5) {
            captureSnapshot("level_change");
        }

        // Significant temperature change (5°C or more)
        if (lastTemperature > 0 && Math.abs(temp - lastTemperature) >= 50) {
            captureSnapshot("temp_change");
        }

        // Check for thermal threshold crossing
        boolean wasHighTemp = isHighTemp;
        isHighTemp = temp >= HIGH_TEMP_THRESHOLD;
        if (wasHighTemp != isHighTemp) {
            captureSnapshot(isHighTemp ? "thermal_high" : "thermal_normal");
            updateSamplingInterval();
        }

        // Critical temperature alert
        if (temp >= CRITICAL_TEMP_THRESHOLD) {
            Log.w(TAG, "CRITICAL TEMPERATURE: " + (temp / 10.0f) + "°C");
            safetyMonitor.checkHighTemperatureSafety(temp);
        }
    }

    private void handlePowerConnected() {
        Log.d(TAG, "Power connected");
        isCharging = true;
        stateCache.setCharging(true);
        captureSnapshot("power_connected");
        executor.execute(() -> chargingAnalyzer.startChargingSession());
        updateSamplingInterval();
        updateNotification();
    }

    private void handlePowerDisconnected() {
        Log.d(TAG, "Power disconnected");
        isCharging = false;
        isFastCharging = false;
        stateCache.setCharging(false);
        captureSnapshot("power_disconnected");
        executor.execute(() -> {
            chargingAnalyzer.endChargingSession();
            drainAnalyzer.startDrainTracking();
        });
        updateSamplingInterval();
        updateNotification();
    }

    private void handleBatteryLow() {
        Log.d(TAG, "Battery low");
        isLowBattery = true;
        captureSnapshot("battery_low");
        safetyMonitor.checkLowBatterySafety();
        updateSamplingInterval();
        updateNotification();
    }

    private void handleBatteryOkay() {
        Log.d(TAG, "Battery okay");
        isLowBattery = false;
        isCriticalBattery = false;
        updateSamplingInterval();
        updateNotification();
    }

    private void handleScreenOn() {
        Log.d(TAG, "Screen on");
        isScreenOn = true;
        captureSnapshot("screen_on");
        updateSamplingInterval();

        // Notify refresh rate monitor of screen state change
        if (refreshRateMonitor != null) {
            refreshRateMonitor.onScreenStateChanged(true);
        }
    }

    private void handleScreenOff() {
        Log.d(TAG, "Screen off");
        isScreenOn = false;
        stateCache.setScreenOn(false);
        captureSnapshot("screen_off");
        updateSamplingInterval();

        // Notify refresh rate monitor of screen state change
        if (refreshRateMonitor != null) {
            refreshRateMonitor.onScreenStateChanged(false);
        }
    }

    // =========================================================================
    // NETWORK DATA COLLECTION
    // =========================================================================

    /**
     * Collects network data (cellular, wifi, bluetooth, modem firmware) and saves
     * to database.
     * Each collector is independent - failures in one don't affect others.
     */
    private void collectNetworkData(String context) {
        try {
            // Cellular snapshot (4G/5G signal metrics)
            if (cellularCollector != null) {
                NetworkCellularSnapshot cellularSnapshot = cellularCollector.collectSnapshot(context);
                if (cellularSnapshot != null) {
                    long cellId = database.networkCellularSnapshotDao().insert(cellularSnapshot);
                    Log.d(TAG, "Cellular snapshot saved: id=" + cellId +
                            ", rsrp=" + cellularSnapshot.rsrp +
                            ", tech=" + cellularSnapshot.radioTechnology);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to collect cellular snapshot", e);
        }

        try {
            // Wi-Fi snapshot
            if (wifiCollector != null) {
                NetworkWifiSnapshot wifiSnapshot = wifiCollector.collectSnapshot(context);
                if (wifiSnapshot != null) {
                    long wifiId = database.networkWifiSnapshotDao().insert(wifiSnapshot);
                    Log.d(TAG, "Wi-Fi snapshot saved: id=" + wifiId +
                            ", rssi=" + wifiSnapshot.rssi +
                            ", std=" + wifiSnapshot.wifiStandardName);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to collect Wi-Fi snapshot", e);
        }

        try {
            // Bluetooth snapshot
            if (bluetoothCollector != null) {
                NetworkBluetoothSnapshot btSnapshot = bluetoothCollector.collectSnapshot(context);
                if (btSnapshot != null) {
                    long btId = database.networkBluetoothSnapshotDao().insert(btSnapshot);
                    Log.d(TAG, "Bluetooth snapshot saved: id=" + btId +
                            ", state=" + btSnapshot.adapterStateName +
                            ", codec=" + btSnapshot.codecType);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to collect Bluetooth snapshot", e);
        }

        try {
            // Modem firmware check (only logs changes)
            if (modemFirmwareTracker != null) {
                ModemFirmwareEvent modemEvent = modemFirmwareTracker.checkAndRecordVersion("PERIODIC");
                if (modemEvent != null) {
                    long modemId = database.modemFirmwareEventDao().insert(modemEvent);
                    Log.i(TAG, "Modem firmware event saved: id=" + modemId +
                            ", type=" + modemEvent.eventType +
                            ", ver=" + modemEvent.currentVersion);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to check modem firmware", e);
        }
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