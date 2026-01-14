package com.example.hexnil.manager;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.JsonWriter;
import android.util.Log;
import android.view.Display;

import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.validation.SnapshotValidator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OEM-Grade Battery Data Collector
 * 
 * Collects real-time battery data from BatteryManager and creates validated snapshots.
 * 
 * CRITICAL DESIGN PRINCIPLES:
 * 1. EVERY snapshot is a NEW object with fresh timestamp
 * 2. NEVER reuse or cache snapshot objects
 * 3. ALWAYS validate data before returning
 * 4. Track sequence numbers for gap detection
 * 5. Include sampling context metadata
 * 
 * This collector fixes the critical bug where snapshots were being reused
 * or contained stale/frozen data.
 */
public class BatteryDataCollector {

    private static final String TAG = "BatteryDataCollector";

    // Sequence tracking
    private static final AtomicLong sequenceCounter = new AtomicLong(0);
    private static volatile long sessionId = System.currentTimeMillis();
    
    // CPU load tracking (delta-based)
    private static long lastCpuIdle = -1;
    private static long lastCpuTotal = -1;
    
    // Previous snapshot for validation and change detection
    private volatile BatterySnapshot previousSnapshot;
    private volatile long lastCollectionTime = 0;

    private final Context context;
    private final BatteryManager batteryManager;
    private final PowerManager powerManager;

    public BatteryDataCollector(Context context) {
        this.context = context.getApplicationContext();
        this.batteryManager = (BatteryManager) this.context.getSystemService(Context.BATTERY_SERVICE);
        this.powerManager = (PowerManager) this.context.getSystemService(Context.POWER_SERVICE);
    }

    /**
     * Resets the session ID. Call when service restarts.
     */
    public void resetSession() {
        sessionId = System.currentTimeMillis();
        sequenceCounter.set(0);
        previousSnapshot = null;
        lastCollectionTime = 0;
        Log.i(TAG, "Session reset: sessionId=" + sessionId);
    }

    /**
     * Collects a new battery snapshot with full validation.
     * 
     * @param samplingContext The context triggering this collection
     *                        ("charging", "discharging", "idle", "low_battery", etc.)
     * @param configuredIntervalMs The configured sampling interval
     * @return A validated BatterySnapshot or null if collection fails
     */
    public BatterySnapshot collectSnapshot(String samplingContext, long configuredIntervalMs) {
        // CRITICAL: Always create a NEW snapshot object
        BatterySnapshot snapshot = new BatterySnapshot(samplingContext);
        
        // Populate sequence metadata
        snapshot.sequenceNumber = sequenceCounter.incrementAndGet();
        snapshot.sessionId = sessionId;
        snapshot.samplingIntervalMs = configuredIntervalMs;
        
        // Calculate time since previous snapshot
        if (previousSnapshot != null) {
            snapshot.timeSincePreviousMs = snapshot.timestamp - previousSnapshot.timestamp;
        }

        if (batteryManager == null) {
            Log.e(TAG, "BatteryManager not available");
            snapshot.validationStatus = "invalid";
            snapshot.validationNotes = "BatteryManager unavailable";
            return snapshot;
        }

        // Collect fresh data from BatteryManager
        collectBatteryManagerData(snapshot);
        
        // Collect system context
        collectSystemContext(snapshot);
        
        // Collect enhanced diagnostics
        collectEnhancedDiagnostics(snapshot);
        
        // Detect sensor changes
        detectSensorChanges(snapshot);
        
        // Validate the snapshot
        validateSnapshot(snapshot);
        
        // Update tracking state
        lastCollectionTime = snapshot.timestamp;
        previousSnapshot = snapshot;
        
        // Log for OEM diagnostics
        logSnapshotDiagnostics(snapshot);
        
        return snapshot;
    }
    
    /**
     * Legacy method for backward compatibility.
     * Uses "normal" as default sampling context.
     */
    public BatterySnapshot collectSnapshot() {
        return collectSnapshot("normal", 60000);
    }

    /**
     * Collects core battery data from BatteryManager.
     * ALL data is freshly read - no caching.
     */
    private void collectBatteryManagerData(BatterySnapshot snapshot) {
        // Read from BatteryManager API (direct sensor values)
        snapshot.level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        snapshot.chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        snapshot.currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        snapshot.currentAverage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        snapshot.energyCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);

        // Read from sticky broadcast (additional details)
        // IMPORTANT: registerReceiver returns the LAST broadcast, not a cached value
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            snapshot.voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            snapshot.temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            snapshot.health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
            snapshot.status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            snapshot.plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        }

        // Derive charging state
        snapshot.isCharging = snapshot.status == BatteryManager.BATTERY_STATUS_CHARGING
            || snapshot.status == BatteryManager.BATTERY_STATUS_FULL;
        snapshot.chargingType = resolveChargingType(snapshot.plugged);
        
        // Fast charging detection with multiple heuristics
        snapshot.isFastCharging = detectFastCharging(snapshot);

        // Calculate capacity values
        snapshot.designCapacityMah = getDesignCapacity();
        snapshot.capacityMah = snapshot.chargeCounter > 0 ? snapshot.chargeCounter / 1000f : 0;
        
        if (snapshot.capacityMah > 0 && snapshot.designCapacityMah > 0) {
            snapshot.healthPercentage = (snapshot.capacityMah / snapshot.designCapacityMah) * 100f;
        }

        // Calculate charge/discharge rates (mA)
        // Note: Current sign convention varies by device
        // Positive = charging on most devices, but some report negative
        int absCurrentMa = Math.abs(snapshot.currentNow) / 1000;
        if (snapshot.isCharging) {
            snapshot.chargeRate = absCurrentMa;
            snapshot.dischargeRate = 0;
        } else {
            snapshot.chargeRate = 0;
            snapshot.dischargeRate = absCurrentMa;
        }
    }

    /**
     * Collects system context (screen, doze, etc.)
     */
    private void collectSystemContext(BatterySnapshot snapshot) {
        // Screen state - use DisplayManager for accurate reading
        snapshot.screenOn = isScreenOn();
        snapshot.screenBrightness = getScreenBrightness();
        
        // Doze mode detection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
            snapshot.isDozeMode = powerManager.isDeviceIdleMode();
        }
    }

    /**
     * Collects enhanced diagnostics for OEM analysis.
     */
    private void collectEnhancedDiagnostics(BatterySnapshot snapshot) {
        snapshot.cpuLoadTotal = readCpuLoadPercent();
        snapshot.topAppPackage = readTopAppPackage();
        snapshot.foregroundApp = snapshot.topAppPackage; // Sync for compatibility
        snapshot.gpuLoad = readGpuLoadPercent();
        snapshot.networkState = readNetworkState();
        
        // Charger fingerprinting
        readChargerFingerprint(snapshot);
        
        // Voltage/current sampling for signal analysis
        snapshot.voltageSamplesJson = collectVoltageSamples();
        snapshot.currentSamplesJson = collectCurrentSamples();
    }

    /**
     * Detects if sensor values changed from previous snapshot.
     * Critical for identifying frozen/stuck sensors.
     */
    private void detectSensorChanges(BatterySnapshot snapshot) {
        if (previousSnapshot == null) {
            snapshot.sensorDataChanged = true;
            return;
        }
        
        // Check if key sensor values changed
        boolean changed = 
            snapshot.level != previousSnapshot.level ||
            snapshot.voltage != previousSnapshot.voltage ||
            snapshot.temperature != previousSnapshot.temperature ||
            Math.abs(snapshot.currentNow - previousSnapshot.currentNow) > 1000 || // 1mA tolerance
            snapshot.chargeCounter != previousSnapshot.chargeCounter;
        
        snapshot.sensorDataChanged = changed;
        
        // Log warning if sensors appear frozen
        if (!changed && snapshot.timeSincePreviousMs > 60000) {
            Log.w(TAG, "Sensor values unchanged for " + (snapshot.timeSincePreviousMs / 1000) + 
                    " seconds - possible sensor freeze");
        }
    }

    /**
     * Validates the snapshot using SnapshotValidator.
     */
    private void validateSnapshot(BatterySnapshot snapshot) {
        SnapshotValidator.ValidationResult result = 
            SnapshotValidator.validate(snapshot, previousSnapshot);
        
        // Convert validation flags to bitmask
        int flagBits = 0;
        SnapshotValidator.ValidationFlags flags = result.flags;
        if (flags.timestampValid) flagBits |= 0x001;
        if (flags.timestampFresh) flagBits |= 0x002;
        if (flags.voltageInRange) flagBits |= 0x004;
        if (flags.temperatureInRange) flagBits |= 0x008;
        if (flags.currentInRange) flagBits |= 0x010;
        if (flags.levelValid) flagBits |= 0x020;
        if (flags.statusValid) flagBits |= 0x040;
        if (flags.notDuplicate) flagBits |= 0x080;
        if (flags.sensorResponsive) flagBits |= 0x100;
        if (flags.chargeCounterValid) flagBits |= 0x200;
        if (flags.dataConsistent) flagBits |= 0x400;
        
        snapshot.setValidationResult(
            result.isValid,
            result.qualityScore,
            flagBits,
            result.invalidReason
        );
    }

    /**
     * Logs snapshot diagnostics for OEM analysis.
     */
    private void logSnapshotDiagnostics(BatterySnapshot snapshot) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, snapshot.toString());
        }
        
        // Always log quality issues
        if (snapshot.qualityScore < 70) {
            Log.w(TAG, "Low quality snapshot: " + 
                    SnapshotValidator.getDiagnosticString(snapshot, 
                        SnapshotValidator.validate(snapshot, previousSnapshot)));
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private boolean isScreenOn() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                for (Display display : dm.getDisplays()) {
                    if (display.getState() == Display.STATE_ON) {
                        return true;
                    }
                }
            }
            return false;
        }
        // Fallback for older APIs
        if (powerManager != null) {
            return powerManager.isInteractive();
        }
        return true; // Assume on if we can't detect
    }

    private int getScreenBrightness() {
        try {
            return Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private boolean detectFastCharging(BatterySnapshot snapshot) {
        if (!snapshot.isCharging) return false;
        
        // Multiple heuristics for fast charging detection
        // 1. High current (>1.5A is typically fast charging)
        int currentMa = Math.abs(snapshot.currentNow) / 1000;
        if (currentMa > 1500) return true;
        
        // 2. USB Power Delivery or Quick Charge usually reports higher voltage
        if (snapshot.voltage > 5500) return true; // >5.5V suggests QC/PD
        
        // 3. AC charging is typically faster than USB
        if (snapshot.plugged == BatteryManager.BATTERY_PLUGGED_AC && currentMa > 1000) {
            return true;
        }
        
        return false;
    }

    private float getDesignCapacity() {
        // Try to get design capacity from system
        // This is device-specific and may not be available
        try {
            Object powerProfile = Class.forName("com.android.internal.os.PowerProfile")
                    .getConstructor(Context.class)
                    .newInstance(context);
            double capacity = (double) Class.forName("com.android.internal.os.PowerProfile")
                    .getMethod("getBatteryCapacity")
                    .invoke(powerProfile);
            return (float) capacity;
        } catch (Exception e) {
            // Return 0 if not available - will be estimated elsewhere
            return 0;
        }
    }

    private int readCpuLoadPercent() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = br.readLine();
            if (line == null || !line.startsWith("cpu")) return -1;

            String[] parts = line.trim().split("\\s+");
            if (parts.length < 8) return -1;

            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);
            long iowait = Long.parseLong(parts[5]);
            long irq = Long.parseLong(parts[6]);
            long softirq = Long.parseLong(parts[7]);
            long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0;

            long idleAll = idle + iowait;
            long total = user + nice + system + idle + iowait + irq + softirq + steal;

            int result = -1;
            if (lastCpuTotal >= 0 && lastCpuIdle >= 0) {
                long diffTotal = total - lastCpuTotal;
                long diffIdle = idleAll - lastCpuIdle;
                if (diffTotal > 0) {
                    long busy = diffTotal - diffIdle;
                    long percent = busy * 100 / diffTotal;
                    result = (int) Math.max(0, Math.min(100, percent));
                }
            }

            lastCpuTotal = total;
            lastCpuIdle = idleAll;
            return result;
        } catch (Exception e) {
            return -1;
        }
    }

    private String readTopAppPackage() {
        try {
            UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;
            
            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, 
                now - 5 * 60_000, 
                now
            );
            
            UsageStats top = null;
            if (stats != null) {
                for (UsageStats s : stats) {
                    if (top == null || s.getLastTimeUsed() > top.getLastTimeUsed()) {
                        top = s;
                    }
                }
            }
            return top != null ? top.getPackageName() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int readGpuLoadPercent() {
        // GPU load not available through public APIs
        // Would require vendor-specific implementations
        return -1;
    }

    private String readNetworkState() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "none";
            
            Network active = cm.getActiveNetwork();
            if (active == null) return "none";
            
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            if (caps == null) return "none";
            
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "mobile";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ethernet";
            return "other";
        } catch (Exception e) {
            return "none";
        }
    }

    private void readChargerFingerprint(BatterySnapshot snapshot) {
        try {
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (usbManager == null) return;
            
            Map<String, UsbDevice> deviceList = usbManager.getDeviceList();
            if (deviceList != null) {
                for (UsbDevice device : deviceList.values()) {
                    snapshot.chargerVendorId = device.getVendorId();
                    snapshot.chargerProductId = device.getProductId();
                    break; // Take first device
                }
            }
        } catch (Exception ignored) {
            // Best-effort
        }
    }

    private String collectVoltageSamples() {
        return collectSamples(() -> {
            Intent batteryIntent = context.registerReceiver(null, 
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            return batteryIntent != null ? 
                batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) : 0;
        }, 7, 15); // 7 samples, 15ms apart
    }

    private String collectCurrentSamples() {
        return collectSamples(() -> 
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            5, 20); // 5 samples, 20ms apart
    }

    private String collectSamples(IntSupplier supplier, int count, int intervalMs) {
        try {
            StringWriter sw = new StringWriter();
            JsonWriter jw = new JsonWriter(sw);
            jw.beginArray();
            
            for (int i = 0; i < count; i++) {
                jw.value(supplier.getAsInt());
                if (i < count - 1) {
                    SystemClock.sleep(intervalMs);
                }
            }
            
            jw.endArray();
            jw.close();
            return sw.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private interface IntSupplier {
        int getAsInt();
    }

    private String resolveChargingType(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "usb";
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "ac";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "wireless";
            default:
                return "unknown";
        }
    }
    
    /**
     * Gets the previous snapshot for external validation.
     */
    public BatterySnapshot getPreviousSnapshot() {
        return previousSnapshot;
    }
    
    /**
     * Gets current session ID.
     */
    public long getSessionId() {
        return sessionId;
    }
    
    /**
     * Gets current sequence number.
     */
    public long getCurrentSequence() {
        return sequenceCounter.get();
    }
}
