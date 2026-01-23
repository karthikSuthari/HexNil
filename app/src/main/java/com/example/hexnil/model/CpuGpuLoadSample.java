package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * OEM-Grade CPU/GPU Load Sample Entity
 * 
 * Captures continuous CPU/GPU usage, system load, and stress metrics.
 * Designed for high-frequency sampling (10-15 second intervals) to enable
 * accurate stress detection and per-app load attribution.
 * 
 * Key Design Principles:
 * 1. Every sample has a unique auto-generated ID
 * 2. Full app attribution (package, name, pid, uid, system flag)
 * 3. GPU availability explicitly tracked (not all devices support)
 * 4. Stress score is calculated, not guessed
 * 5. Validation status for data quality tracking
 */
@Entity(tableName = "cpu_gpu_load_samples", indices = {
        @Index(value = { "timestamp" }),
        @Index(value = { "packageName" }),
        @Index(value = { "stressCategory" }),
        @Index(value = { "sourceType", "timestamp" })
})
public class CpuGpuLoadSample {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // =========================================================================
    // TIMING
    // =========================================================================

    /** Wall-clock timestamp when this sample was captured */
    public long timestamp;

    // =========================================================================
    // APP ATTRIBUTION
    // =========================================================================

    /** Display name of the app (human readable) */
    public String appName;

    /** Full package name (e.g., com.example.app) */
    public String packageName;

    /** Process ID (0 for system-wide samples) */
    public int pid;

    /** User ID */
    public int uid;

    /** Whether this is a system app */
    public boolean isSystemApp;

    // =========================================================================
    // CPU METRICS
    // =========================================================================

    /** System-wide CPU usage percentage (0-100, -1 if unavailable) */
    public int systemCpuPercent;

    /** Per-app CPU usage percentage (0-100, -1 for system-wide samples) */
    public int appCpuPercent;

    // =========================================================================
    // GPU METRICS
    // =========================================================================

    /** GPU load percentage (0-100, -1 if unavailable on device) */
    public int gpuPercent;

    /** Whether GPU metrics are reliable on this device */
    public boolean isGpuAvailable;

    // =========================================================================
    // SYSTEM LOAD (from /proc/loadavg)
    // =========================================================================

    /** 1-minute load average */
    public float load1m;

    /** 5-minute load average */
    public float load5m;

    /** 15-minute load average */
    public float load15m;

    /** Number of currently runnable processes */
    public int runnableProcesses;

    // =========================================================================
    // CONTEXT (at sample time)
    // =========================================================================

    /** Battery/CPU temperature in Celsius */
    public float temperature;

    /** Battery level percentage (0-100) */
    public int batteryLevel;

    /** Current drain rate in %/hour */
    public float drainRatePerHour;

    /** Whether CPU thermal throttling is active */
    public boolean isThrottled;

    /** Throttle severity (0=none, 1=light, 2=moderate, 3=severe) */
    public int throttleLevel;

    /** System thermal status (PowerManager.THERMAL_STATUS_X) */
    public int thermalStatus;

    // =========================================================================
    // STRESS ANALYSIS (calculated, not guessed)
    // =========================================================================

    /** Calculated stress score (0-100) from sliding window analysis */
    public float stressScore;

    /** Stress category: LOW, MEDIUM, HIGH, CRITICAL */
    public String stressCategory;

    /** Human-readable explanation of stress contributors */
    public String stressExplanation;

    // =========================================================================
    // SAMPLE METADATA
    // =========================================================================

    /** Sample source type: "foreground", "background", "system" */
    public String sourceType;

    /** Validation status: "valid", "warning", "invalid", "unchecked" */
    public String validationStatus;

    /** Reason for validation failure or warning */
    public String validationNotes;

    /** Whether the app was actively contributing to load when sampled */
    public boolean isActive;

    /**
     * Inferred load category (LOW, MODERATE, HIGH) when raw metrics are restricted
     */
    public String inferredLoad;

    /** Confidence level of the inference (LOW, MODERATE, HIGH) */
    public String confidenceLevel;

    /**
     * Method used for detection: "Direct hardware counters" or "Indirect behavioral
     * inference"
     */
    public String detectionMethod;

    /** Semicolon-separated list of observed signals that led to the inference */
    public String observedSignals;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /**
     * Default constructor - creates sample with fresh timestamp.
     */
    public CpuGpuLoadSample() {
        this.timestamp = System.currentTimeMillis();
        this.validationStatus = "unchecked";
        this.stressCategory = "LOW";
        this.stressScore = 0;
        this.isGpuAvailable = true;
        this.isActive = true;
        this.sourceType = "system";
        this.gpuPercent = -1;
        this.detectionMethod = "Direct hardware counters";
        this.inferredLoad = "LOW";
        this.confidenceLevel = "LOW";
    }

    /**
     * Creates a system-wide sample (no specific app).
     */
    @Ignore
    public static CpuGpuLoadSample createSystemSample(
            int systemCpu, int gpu, boolean gpuAvailable,
            float load1m, float load5m, float load15m,
            float temperature, int batteryLevel) {

        CpuGpuLoadSample sample = new CpuGpuLoadSample();
        sample.systemCpuPercent = systemCpu;
        sample.appCpuPercent = -1;
        sample.gpuPercent = gpu;
        sample.isGpuAvailable = gpuAvailable;
        sample.load1m = load1m;
        sample.load5m = load5m;
        sample.load15m = load15m;
        sample.temperature = temperature;
        sample.batteryLevel = batteryLevel;
        sample.sourceType = "system";
        return sample;
    }

    /**
     * Creates a per-app sample.
     */
    @Ignore
    public static CpuGpuLoadSample createAppSample(
            String packageName, String appName, int pid, int uid, boolean isSystemApp,
            int appCpu, int systemCpu, int gpu, boolean gpuAvailable,
            float temperature, int batteryLevel, String sourceType) {

        CpuGpuLoadSample sample = new CpuGpuLoadSample();
        sample.packageName = packageName;
        sample.appName = appName;
        sample.pid = pid;
        sample.uid = uid;
        sample.isSystemApp = isSystemApp;
        sample.appCpuPercent = appCpu;
        sample.systemCpuPercent = systemCpu;
        sample.gpuPercent = gpu;
        sample.isGpuAvailable = gpuAvailable;
        sample.temperature = temperature;
        sample.batteryLevel = batteryLevel;
        sample.sourceType = sourceType;
        return sample;
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Checks if this is a system-wide sample (no specific app).
     */
    @Ignore
    public boolean isSystemWideSample() {
        return packageName == null || packageName.isEmpty() || pid <= 0;
    }

    /**
     * Gets effective CPU percentage (app or system).
     */
    @Ignore
    public int getEffectiveCpuPercent() {
        return appCpuPercent >= 0 ? appCpuPercent : systemCpuPercent;
    }

    /**
     * Checks if stress level is concerning (HIGH or CRITICAL).
     */
    @Ignore
    public boolean isStressElevated() {
        return "HIGH".equals(stressCategory) || "CRITICAL".equals(stressCategory);
    }

    /**
     * Checks if data passed validation.
     */
    @Ignore
    public boolean isValid() {
        return !"invalid".equals(validationStatus);
    }

    /**
     * Sets validation result.
     */
    @Ignore
    public void setValidationResult(boolean valid, String notes) {
        this.validationStatus = valid ? "valid" : "invalid";
        this.validationNotes = notes;
    }

    /**
     * Sets stress analysis result with inference data.
     */
    @Ignore
    public void setStressResult(float score, String category, String explanation,
            String inferredLoad, String confidence, String method, String signals) {
        this.stressScore = score;
        this.stressCategory = category;
        this.stressExplanation = explanation;
        this.inferredLoad = inferredLoad;
        this.confidenceLevel = confidence;
        this.detectionMethod = method;
        this.observedSignals = signals;
    }

    @Ignore
    public void setStressResult(float score, String category, String explanation) {
        this.stressScore = score;
        this.stressCategory = category;
        this.stressExplanation = explanation;
    }

    @Override
    public String toString() {
        return "CpuGpuLoadSample{" +
                "id=" + id +
                ", ts=" + timestamp +
                ", pkg=" + packageName +
                ", cpu=" + systemCpuPercent + "%" +
                ", appCpu=" + appCpuPercent + "%" +
                ", gpu=" + gpuPercent + "%" +
                ", load=" + load1m +
                ", temp=" + temperature + "°C" +
                ", stress=" + stressScore + " (" + stressCategory + ")" +
                ", valid=" + validationStatus +
                '}';
    }
}
