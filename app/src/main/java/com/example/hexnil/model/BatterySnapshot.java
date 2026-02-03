package com.example.hexnil.model;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * OEM-Grade Battery Snapshot Entity
 * 
 * Primary time-series data structure for battery diagnostics.
 * Each snapshot represents a unique point-in-time capture of battery state.
 * 
 * CRITICAL DESIGN PRINCIPLES:
 * 1. Every snapshot MUST have a unique auto-generated ID
 * 2. Every snapshot MUST have a fresh timestamp (never reuse)
 * 3. Snapshots are IMMUTABLE after creation
 * 4. Validation metadata enables quality filtering
 * 
 * Used for: Historical analysis, trend detection, OEM validation, QA reporting
 *
 * THREE-STATE SEMANTICS (v26+):
 * - 1 = TRUE/ACTIVE/MEASURED; 0 = FALSE/INACTIVE/ZERO; null = UNAVAILABLE/RESTRICTED.
 * - Do not collapse null into 0. Use {@code isScreenOn()}, {@code getCurrentMilliampsSafe()} etc. for null-safe access.
 */
@Entity(
    tableName = "battery_snapshots",
    indices = {
        @Index(value = {"timestamp"}),
        @Index(value = {"samplingContext"}),
        @Index(value = {"isCharging", "timestamp"}),
        @Index(value = {"validationStatus"})
    }
)
public class BatterySnapshot {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    /**
     * Wall-clock timestamp when this snapshot was captured.
     * MUST be unique and monotonically increasing.
     */
    public long timestamp;
    
    /**
     * Monotonic sequence number for ordering within a session.
     * Useful for detecting gaps in data collection.
     */
    public long sequenceNumber;
    
    /**
     * Elapsed realtime (SystemClock.elapsedRealtime) for drift-free ordering.
     * Survives wall-clock changes.
     */
    public long elapsedRealtimeMs;
    
    // =====================================================================
    // CORE BATTERY METRICS (from BatteryManager)
    // =====================================================================
    
    public int level;                    // 0-100%
    public int status;                   // CHARGING, DISCHARGING, FULL, etc.
    public int health;                   // GOOD, OVERHEAT, DEAD, etc.
    public int plugged;                  // USB, AC, WIRELESS, etc.
    
    // Capacity & voltage
    public int voltage;                  // millivolts
    public int temperature;              // tenths of degree Celsius
    public Long chargeCounter;           // microampere-hours; null if unsupported
    public Integer currentNow;           // microamperes (instant); null if unsupported
    public Integer currentAverage;       // microamperes (average); null if unsupported
    public Long energyCounter;           // nanowatt-hours; null if unsupported
    
    // Calculated fields
    public float capacityMah;            // Estimated capacity in mAh
    public float designCapacityMah;      // Design capacity from manufacturer
    public float healthPercentage;       // Real battery health %
    public float chargeRate;             // mA charging rate
    public float dischargeRate;          // mA discharge rate
    
    // =====================================================================
    // DEVICE STATE CONTEXT
    // =====================================================================
    
    // Screen state (1=on, 0=off, null=unavailable)
    public Integer screenOn;
    public Integer screenBrightness;     // 0-255; null if unreadable
    
    // Connectivity state (1=active, 0=inactive, null=unavailable)
    public Integer wifiEnabled;
    public Integer mobileDataEnabled;
    public Integer bluetoothEnabled;
    public Integer gpsEnabled;
    public Integer signalStrength;       // dBm; null if unknown
    
    // System state
    public boolean isCharging;
    public boolean isFastCharging;
    public String chargingType;          // "usb", "ac", "wireless", "unknown"
    public boolean isDozeMode;
    public int cpuUsage;                 // percentage
    public int memoryUsage;              // percentage

    // Enhanced diagnostics (null when blocked/unavailable)
    public Integer cpuLoadTotal;         // total CPU load %
    public String topAppPackage;         // top package at sampling time
    public Integer gpuLoad;              // GPU load %; null if unavailable
    public String networkState;          // wifi/mobile/none
    public Integer chargerVendorId;      // USB vendor id; null for AC/wireless
    public Integer chargerProductId;     // USB product id; null for AC/wireless
    public String voltageSamplesJson;    // JSON array of voltage samples
    public String currentSamplesJson;    // JSON array of current samples
    
    // App context
    public String foregroundApp;
    public int runningApps;
    
    // =====================================================================
    // SAMPLING & VALIDATION METADATA (OEM-Grade)
    // =====================================================================
    
    /**
     * Context in which this snapshot was captured.
     * Determines expected sampling interval and validation rules.
     * Values: "charging", "discharging", "idle", "low_battery", "thermal_event",
     *         "charging_start", "charging_end", "level_change", "manual"
     */
    public String samplingContext;
    
    /**
     * Configured sampling interval at capture time (milliseconds).
     * Used for validating data density.
     */
    public long samplingIntervalMs;
    
    /**
     * Time elapsed since previous snapshot (milliseconds).
     * -1 if this is the first snapshot in a session.
     */
    public long timeSincePreviousMs;
    
    /**
     * Validation status from SnapshotValidator.
     * Values: "valid", "warning", "invalid", "unchecked"
     */
    public String validationStatus;
    
    /**
     * Quality score from validation (0-100).
     * 100 = perfect, <70 = questionable, <50 = unreliable
     */
    public int qualityScore;
    
    /**
     * Validation flags encoded as bitmask for efficient querying.
     * Bit 0: timestamp_valid
     * Bit 1: timestamp_fresh  
     * Bit 2: voltage_in_range
     * Bit 3: temperature_in_range
     * Bit 4: current_in_range
     * Bit 5: level_valid
     * Bit 6: status_valid
     * Bit 7: not_duplicate
     * Bit 8: sensor_responsive
     * Bit 9: charge_counter_valid
     * Bit 10: data_consistent
     */
    public int validationFlags;
    
    /**
     * Reason for validation failure or warning (if any).
     */
    public String validationNotes;
    
    /**
     * Indicates if sensor values changed from previous snapshot.
     * Used to detect frozen/stuck sensors.
     */
    public boolean sensorDataChanged;
    
    /**
     * Session ID for grouping related snapshots.
     * Changes when service restarts or significant gap detected.
     */
    public long sessionId;
    
    /** FOREGROUND | BACKGROUND | PERIODIC | CHARGING | SCREEN_ON | SCREEN_OFF | etc. */
    public String snapshotTrigger;
    /** EVENT | POLLED | INFERRED */
    public String dataSource;
    /** 0.0–1.0; 1.0 = high confidence */
    public Float confidence;
    
    // =====================================================================
    // CONSTRUCTORS
    // =====================================================================
    
    /**
     * Default constructor - creates snapshot with fresh timestamp.
     * ALWAYS generates new timestamp to ensure uniqueness.
     */
    public BatterySnapshot() {
        this.timestamp = System.currentTimeMillis();
        this.elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime();
        this.validationStatus = "unchecked";
        this.qualityScore = -1;
        this.validationFlags = 0;
        this.sensorDataChanged = true;
        this.timeSincePreviousMs = -1;
        // Default values for fields that must not be NULL
        // energyCounter stays null (three-state: unavailable on many devices)
        this.chargeRate = 0f; // 0 when not charging
        this.screenOn = null; // Three-state: null = unavailable (retained)
        this.chargingType = "unknown"; // Safe default
        // chargerVendorId/chargerProductId stay null (only valid for USB)
    }
    
    /**
     * Creates a snapshot with specified sampling context.
     */
    @Ignore
    public BatterySnapshot(String samplingContext) {
        this();
        this.samplingContext = samplingContext;
    }
    
    // =====================================================================
    // UTILITY METHODS
    // =====================================================================
    
    public float getTemperatureCelsius() {
        return temperature / 10.0f;
    }
    
    public float getVoltageVolts() {
        return voltage / 1000.0f;
    }
    
    /** Current in A; 0 if unavailable (null). Prefer {@code currentNow != null} checks. */
    public float getCurrentAmps() {
        return currentNow != null ? currentNow / 1000000.0f : 0f;
    }
    
    /** Current in mA; 0 if unavailable (null). Use only for display; never treat 0 as "measured zero" without checking null. */
    public float getCurrentMilliamps() {
        return currentNow != null ? currentNow / 1000.0f : 0f;
    }
    
    /** True if screen known on; false if off; null if unavailable. */
    @Nullable
    public Boolean isScreenOn() {
        if (screenOn == null) return null;
        return screenOn != 0;
    }
    
    /** True if wifi known active; false if inactive; null if unavailable. */
    @Nullable
    public Boolean isWifiEnabled() {
        if (wifiEnabled == null) return null;
        return wifiEnabled != 0;
    }
    
    /** True if mobile data known active; false if inactive; null if unavailable. */
    @Nullable
    public Boolean isMobileDataEnabled() {
        if (mobileDataEnabled == null) return null;
        return mobileDataEnabled != 0;
    }

    /**
     * Legacy helper: true iff screen known on; false when off or null.
     * Use only where boolean is required (e.g. downstream APIs). Prefer isScreenOn() and explicit null handling.
     */
    @Ignore
    public boolean isScreenOnOrFalse() {
        return Boolean.TRUE.equals(isScreenOn());
    }
    
    /**
     * Checks if this snapshot passes minimum quality threshold.
     */
    @Ignore
    public boolean isUsable() {
        return !"invalid".equals(validationStatus) && qualityScore >= 50;
    }
    
    /**
     * Checks if this snapshot has high quality data.
     */
    @Ignore
    public boolean isHighQuality() {
        return "valid".equals(validationStatus) && qualityScore >= 85;
    }
    
    /**
     * Sets validation result from SnapshotValidator.
     */
    @Ignore
    public void setValidationResult(boolean valid, int score, int flags, String notes) {
        this.validationStatus = valid ? (score >= 85 ? "valid" : "warning") : "invalid";
        this.qualityScore = score;
        this.validationFlags = flags;
        this.validationNotes = notes;
    }
    
    /**
     * Creates a fingerprint for duplicate detection.
     * Two snapshots with identical fingerprints are likely duplicates.
     */
    @Ignore
    public String getFingerprint() {
        return level + ":" + voltage + ":" + temperature + ":" +
               (currentNow != null ? currentNow : "n") + ":" +
               (chargeCounter != null ? chargeCounter : "n") + ":" + status;
    }
    
    /**
     * Checks if sensor values match another snapshot (potential duplicate/frozen data).
     */
    @Ignore
    public boolean hasSameReadingsAs(BatterySnapshot other) {
        if (other == null) return false;
        return this.level == other.level &&
               this.voltage == other.voltage &&
               this.temperature == other.temperature &&
               java.util.Objects.equals(this.currentNow, other.currentNow) &&
               java.util.Objects.equals(this.chargeCounter, other.chargeCounter);
    }
    
    @Override
    public String toString() {
        return "BatterySnapshot{" +
                "id=" + id +
                ", ts=" + timestamp +
                ", seq=" + sequenceNumber +
                ", level=" + level + "%" +
                ", voltage=" + voltage + "mV" +
                ", temp=" + getTemperatureCelsius() + "°C" +
                ", current=" + (currentNow != null ? getCurrentMilliamps() + "mA" : "null") +
                ", charging=" + isCharging +
                ", screenOn=" + screenOn +
                ", context=" + samplingContext +
                ", quality=" + qualityScore +
                ", valid=" + validationStatus +
                '}';
    }
}
