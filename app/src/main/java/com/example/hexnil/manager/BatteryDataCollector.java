package com.example.hexnil.manager;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.JsonWriter;
import android.util.Log;

import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.oem.LimitationsRegistry;
import com.example.hexnil.state.DeviceStateCache;
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
 * Collects real-time battery data from BatteryManager and creates validated
 * snapshots.
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
 *
 * THREE-STATE (v26+): Screen/network/charging from DeviceStateCache
 * (event-driven).
 * Never poll PowerManager.isInteractive, WifiManager, etc. Store null when
 * unavailable.
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
    private final TelephonyManager telephonyManager;
    private final ActivityManager activityManager;
    private final LocationManager locationManager;
    private LimitationsRegistry limitationsRegistry;
    private volatile DeviceStateCache stateCache;

    public BatteryDataCollector(Context context) {
        this.context = context.getApplicationContext();
        this.batteryManager = (BatteryManager) this.context.getSystemService(Context.BATTERY_SERVICE);
        this.powerManager = (PowerManager) this.context.getSystemService(Context.POWER_SERVICE);
        this.telephonyManager = (TelephonyManager) this.context.getSystemService(Context.TELEPHONY_SERVICE);
        this.activityManager = (ActivityManager) this.context.getSystemService(Context.ACTIVITY_SERVICE);
        this.locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * Sets the event-driven state cache. Must be set before collectSnapshot when
     * using
     * event-driven screen/network. If null, those fields will be stored as null
     * (unavailable).
     */
    public void setDeviceStateCache(DeviceStateCache cache) {
        this.stateCache = cache;
    }

    /**
     * Sets the limitations registry for nulling unsupported metrics (current,
     * energy, etc.).
     */
    public void setLimitationsRegistry(LimitationsRegistry registry) {
        this.limitationsRegistry = registry;
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
     * @param samplingContext      The context triggering this collection
     *                             ("charging", "discharging", "idle",
     *                             "low_battery", etc.)
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

        // Snapshot context metadata (explainable data)
        snapshot.snapshotTrigger = inferTrigger(samplingContext);
        snapshot.dataSource = inferDataSource(samplingContext);
        snapshot.confidence = inferConfidence(samplingContext);

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
     * Stores null for current/energy/charge when device does not support (per
     * LimitationsRegistry).
     */
    private void collectBatteryManagerData(BatterySnapshot snapshot) {
        snapshot.level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        long rawCharge = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        int rawCurrentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        int rawCurrentAvg = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        long rawEnergy = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);

        LimitationsRegistry.DeviceCapabilities caps = limitationsRegistry != null
                ? limitationsRegistry.checkDeviceCapabilities()
                : null;
        boolean hasCurrent = caps == null || caps.hasCurrentMeasurement;
        boolean hasCharge = caps == null || caps.hasChargeCounter;

        if (hasCharge && rawCharge != Long.MIN_VALUE && rawCharge != 0) {
            snapshot.chargeCounter = rawCharge;
        } else {
            snapshot.chargeCounter = null;
        }
        if (hasCurrent && rawCurrentNow != Integer.MIN_VALUE) {
            snapshot.currentNow = rawCurrentNow;
        } else {
            snapshot.currentNow = null;
        }
        if (hasCurrent && rawCurrentAvg != Integer.MIN_VALUE) {
            snapshot.currentAverage = rawCurrentAvg;
        } else {
            snapshot.currentAverage = null;
        }
        if (rawEnergy != Long.MIN_VALUE && rawEnergy != 0) {
            snapshot.energyCounter = rawEnergy;
        } else {
            snapshot.energyCounter = null;
        }

        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery != null) {
            snapshot.voltage = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
            snapshot.temperature = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            snapshot.health = battery.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
            snapshot.status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
            snapshot.plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        }

        snapshot.isCharging = snapshot.status == BatteryManager.BATTERY_STATUS_CHARGING
                || snapshot.status == BatteryManager.BATTERY_STATUS_FULL;
        snapshot.chargingType = resolveChargingType(snapshot.plugged);
        snapshot.isFastCharging = detectFastCharging(snapshot);

        snapshot.designCapacityMah = getDesignCapacity();
        snapshot.capacityMah = snapshot.chargeCounter != null && snapshot.chargeCounter > 0
                ? snapshot.chargeCounter / 1000f
                : 0;
        if (snapshot.capacityMah > 0 && snapshot.designCapacityMah > 0) {
            snapshot.healthPercentage = (snapshot.capacityMah / snapshot.designCapacityMah) * 100f;
        }

        int absCurrentMa = snapshot.currentNow != null ? Math.abs(snapshot.currentNow) / 1000 : 0;
        if (snapshot.isCharging) {
            snapshot.chargeRate = absCurrentMa;
            snapshot.dischargeRate = 0;
        } else {
            snapshot.chargeRate = 0;
            snapshot.dischargeRate = absCurrentMa;
        }
    }

    /**
     * Collects system context from DeviceStateCache (event-driven). No polling.
     * Screen/network: 1/0 from cache, null if unavailable.
     */
    private void collectSystemContext(BatterySnapshot snapshot) {
        if (stateCache != null) {
            snapshot.screenOn = stateCache.getScreenOnAsTernary();
            snapshot.wifiEnabled = stateCache.getWifiActiveAsTernary();
            snapshot.mobileDataEnabled = stateCache.getMobileActiveAsTernary();
        } else {
            snapshot.screenOn = null;
            snapshot.wifiEnabled = null;
            snapshot.mobileDataEnabled = null;
        }

        // Bluetooth
        try {
            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
            snapshot.bluetoothEnabled = (btAdapter != null && btAdapter.isEnabled()) ? 1 : 0;
        } catch (Exception e) {
            snapshot.bluetoothEnabled = null;
        }

        // GPS Toggle
        try {
            if (locationManager != null) {
                snapshot.gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? 1 : 0;
            } else {
                snapshot.gpsEnabled = null;
            }
        } catch (Exception e) {
            snapshot.gpsEnabled = null;
        }

        // Signal Strength
        try {
            if (telephonyManager != null) {
                SignalStrength ss = telephonyManager.getSignalStrength();
                if (ss != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        List<android.telephony.CellSignalStrength> signals = ss.getCellSignalStrengths();
                        if (signals != null && !signals.isEmpty()) {
                            int dbm = signals.get(0).getDbm();
                            snapshot.signalStrength = (dbm != Integer.MAX_VALUE) ? dbm : null;
                        }
                    }
                }
            }
        } catch (Exception e) {
            snapshot.signalStrength = null;
        }

        int bright = getScreenBrightness();
        snapshot.screenBrightness = (bright >= 0 && bright <= 255) ? bright : null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
            snapshot.isDozeMode = powerManager.isDeviceIdleMode();
        }
    }

    /**
     * Collects enhanced diagnostics. CPU/GPU: null when unavailable (-1). Charger
     * IDs: null for AC.
     */
    private void collectEnhancedDiagnostics(BatterySnapshot snapshot) {
        int cpu = readCpuLoadPercent();
        snapshot.cpuUsage = (cpu >= 0) ? cpu : 0;
        snapshot.cpuLoadTotal = (cpu >= 0) ? cpu : null;

        // Memory Usage
        try {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(mi);
            long usedMem = mi.totalMem - mi.availMem;
            snapshot.memoryUsage = (int) (usedMem * 100 / mi.totalMem);
        } catch (Exception e) {
            snapshot.memoryUsage = 0;
        }

        // Running Apps count
        try {
            List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
            snapshot.runningApps = (processes != null) ? processes.size() : 0;
        } catch (Exception e) {
            snapshot.runningApps = 0;
        }

        snapshot.topAppPackage = readTopAppPackage();
        snapshot.foregroundApp = snapshot.topAppPackage;
        int gpu = readGpuLoadPercent();
        snapshot.gpuLoad = (gpu >= 0) ? gpu : null;
        snapshot.networkState = readNetworkState();
        readChargerFingerprint(snapshot);
        snapshot.voltageSamplesJson = collectVoltageSamples();
        snapshot.currentSamplesJson = collectCurrentSamples();
    }

    /**
     * Detects if sensor values changed from previous snapshot.
     */
    private void detectSensorChanges(BatterySnapshot snapshot) {
        if (previousSnapshot == null) {
            snapshot.sensorDataChanged = true;
            return;
        }
        boolean changed = snapshot.level != previousSnapshot.level
                || snapshot.voltage != previousSnapshot.voltage
                || snapshot.temperature != previousSnapshot.temperature;
        if (snapshot.currentNow != null && previousSnapshot.currentNow != null) {
            changed = changed || Math.abs(snapshot.currentNow - previousSnapshot.currentNow) > 1000;
        } else if (snapshot.currentNow != null || previousSnapshot.currentNow != null) {
            changed = true;
        }
        if (!java.util.Objects.equals(snapshot.chargeCounter, previousSnapshot.chargeCounter)) {
            changed = true;
        }
        snapshot.sensorDataChanged = changed;
        if (!changed && snapshot.timeSincePreviousMs > 60000) {
            Log.w(TAG, "Sensor values unchanged for " + (snapshot.timeSincePreviousMs / 1000) + " s - possible freeze");
        }
    }

    /**
     * Validates the snapshot using SnapshotValidator.
     */
    private void validateSnapshot(BatterySnapshot snapshot) {
        SnapshotValidator.ValidationResult result = SnapshotValidator.validate(snapshot, previousSnapshot);

        // Convert validation flags to bitmask
        int flagBits = 0;
        SnapshotValidator.ValidationFlags flags = result.flags;
        if (flags.timestampValid)
            flagBits |= 0x001;
        if (flags.timestampFresh)
            flagBits |= 0x002;
        if (flags.voltageInRange)
            flagBits |= 0x004;
        if (flags.temperatureInRange)
            flagBits |= 0x008;
        if (flags.currentInRange)
            flagBits |= 0x010;
        if (flags.levelValid)
            flagBits |= 0x020;
        if (flags.statusValid)
            flagBits |= 0x040;
        if (flags.notDuplicate)
            flagBits |= 0x080;
        if (flags.sensorResponsive)
            flagBits |= 0x100;
        if (flags.chargeCounterValid)
            flagBits |= 0x200;
        if (flags.dataConsistent)
            flagBits |= 0x400;

        snapshot.setValidationResult(
                result.isValid,
                result.qualityScore,
                flagBits,
                result.invalidReason);
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

    private static String inferTrigger(String samplingContext) {
        if (samplingContext == null)
            return "PERIODIC";
        switch (samplingContext) {
            case "screen_on":
            case "screen_off":
                return samplingContext.toUpperCase();
            case "charging_start":
            case "charging_end":
            case "power_connected":
            case "power_disconnected":
            case "battery_low":
            case "level_change":
            case "temp_change":
            case "thermal_high":
            case "thermal_normal":
            case "doze_exit":
            case "service_start":
            case "service_stop":
                return samplingContext.toUpperCase();
            default:
                return "PERIODIC";
        }
    }

    private static String inferDataSource(String samplingContext) {
        if (samplingContext == null)
            return "POLLED";
        switch (samplingContext) {
            case "screen_on":
            case "screen_off":
            case "charging_start":
            case "charging_end":
            case "power_connected":
            case "power_disconnected":
            case "battery_low":
            case "doze_exit":
                return "EVENT";
            default:
                return "POLLED";
        }
    }

    private static float inferConfidence(String samplingContext) {
        if (samplingContext == null)
            return 0.8f;
        switch (samplingContext) {
            case "screen_on":
            case "screen_off":
            case "charging_start":
            case "charging_end":
            case "power_connected":
            case "power_disconnected":
            case "battery_low":
            case "doze_exit":
                return 1.0f;
            default:
                return 0.85f;
        }
    }

    private int getScreenBrightness() {
        try {
            return Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private boolean detectFastCharging(BatterySnapshot snapshot) {
        if (!snapshot.isCharging)
            return false;
        int currentMa = snapshot.currentNow != null ? Math.abs(snapshot.currentNow) / 1000 : 0;
        if (currentMa > 1500)
            return true;
        if (snapshot.voltage > 5500)
            return true;
        if (snapshot.plugged == BatteryManager.BATTERY_PLUGGED_AC && currentMa > 1000)
            return true;
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
            if (line == null || !line.startsWith("cpu"))
                return -1;

            String[] parts = line.trim().split("\\s+");
            if (parts.length < 8)
                return -1;

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
            if (usm == null)
                return null;

            long now = System.currentTimeMillis();
            List<UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    now - 5 * 60_000,
                    now);

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
            if (cm == null)
                return "none";

            Network active = cm.getActiveNetwork();
            if (active == null)
                return "none";

            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            if (caps == null)
                return "none";

            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                return "wifi";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                return "mobile";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                return "ethernet";
            return "other";
        } catch (Exception e) {
            return "none";
        }
    }

    private void readChargerFingerprint(BatterySnapshot snapshot) {
        // For AC chargers or wireless, USB device enumeration won't provide charger info
        // But many USB-C chargers (including AC with USB-C cable) expose USB device info
        if (snapshot.plugged == 0) { // Not plugged in
            snapshot.chargerVendorId = null;
            snapshot.chargerProductId = null;
            return;
        }
        
        try {
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                Log.d(TAG, "UsbManager unavailable for charger fingerprint");
                snapshot.chargerVendorId = null;
                snapshot.chargerProductId = null;
                return;
            }
            
            Map<String, UsbDevice> deviceList = usbManager.getDeviceList();
            if (deviceList != null && !deviceList.isEmpty()) {
                // Look for power delivery capable devices first
                for (UsbDevice device : deviceList.values()) {
                    // Check device class - chargers typically are class 0 or vendor-specific
                    int deviceClass = device.getDeviceClass();
                    int vendorId = device.getVendorId();
                    int productId = device.getProductId();
                    
                    // Log for debugging charger detection
                    Log.d(TAG, "USB Device found - Vendor: " + String.format("0x%04X", vendorId) +
                              ", Product: " + String.format("0x%04X", productId) +
                              ", Class: " + deviceClass +
                              ", Name: " + device.getDeviceName());
                    
                    // Use the first device with valid IDs (usually the charger)
                    if (vendorId != 0) {
                        snapshot.chargerVendorId = vendorId;
                        snapshot.chargerProductId = productId;
                        Log.d(TAG, "Charger fingerprint: " + vendorId + ":" + productId);
                        return;
                    }
                }
            } else {
                Log.d(TAG, "No USB devices enumerated for charger fingerprint");
            }
        } catch (SecurityException se) {
            Log.w(TAG, "USB permission denied for charger fingerprint");
        } catch (Exception e) {
            Log.w(TAG, "Error reading charger fingerprint: " + e.getMessage());
        }
        
        // If we couldn't get USB device info, set to null
        snapshot.chargerVendorId = null;
        snapshot.chargerProductId = null;
    }

    private String collectVoltageSamples() {
        return collectSamples(() -> {
            Intent batteryIntent = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            return batteryIntent != null ? batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) : 0;
        }, 7, 15); // 7 samples, 15ms apart
    }

    private String collectCurrentSamples() {
        return collectSamples(() -> batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
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
