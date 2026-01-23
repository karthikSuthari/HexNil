package com.example.hexnil.manager;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * OEM-Grade System Load Collector
 * 
 * Collects system load metrics from /proc/loadavg and detects thermal
 * throttling.
 * 
 * Metrics Collected:
 * - Load averages (1m / 5m / 15m) from /proc/loadavg
 * - Runnable process count
 * - CPU frequency monitoring for throttle detection
 * - Thermal throttling status (Android 10+ via ThermalManager or sysfs)
 */
public class SystemLoadCollector {

    private static final String TAG = "SystemLoadCollector";

    // Thermal throttle detection paths
    private static final String[] THERMAL_THROTTLE_PATHS = {
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq",
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq",
            "/sys/class/thermal/thermal_zone0/temp",
    };

    // CPU frequency paths
    private static final String CPU_CUR_FREQ_PATH = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq";
    private static final String CPU_MAX_FREQ_PATH = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq";
    private static final String CPU_POLICY_MAX_PATH = "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq";

    private final Context context;
    private final PowerManager powerManager;

    // Baseline max frequency for throttle detection
    private long baselineMaxFreqHz = 0;
    private boolean baselineDetected = false;
    private boolean hasLoggedEacces = false;

    // =========================================================================
    // SYSTEM LOAD DATA STRUCTURE
    // =========================================================================

    /**
     * System load metrics result.
     */
    public static class SystemLoad {
        /** 1-minute load average */
        public float load1m = 0;

        /** 5-minute load average */
        public float load5m = 0;

        /** 15-minute load average */
        public float load15m = 0;

        /** Currently runnable/running processes */
        public int runnableProcesses = 0;

        /** Total processes in system */
        public int totalProcesses = 0;

        /** Last created PID */
        public int lastPid = 0;

        /** CPU thermal throttling detected */
        public boolean isThrottled = false;

        /** Throttle severity (0=none, 1=light, 2=moderate, 3=severe) */
        public int throttleLevel = 0;

        /** Current maximum allowed CPU frequency in Hz (0 if unreadable) */
        public long cpuMaxFreqHz = 0;

        /** Current CPU frequency in Hz (0 if unreadable) */
        public long cpuCurrentFreqHz = 0;

        /** Percentage of max frequency available (throttle indicator) */
        public int freqPercentOfMax = 100;

        /** Collection timestamp */
        public long timestamp = System.currentTimeMillis();

        @Override
        public String toString() {
            return "SystemLoad{" +
                    "load=" + load1m + "/" + load5m + "/" + load15m +
                    ", procs=" + runnableProcesses + "/" + totalProcesses +
                    ", throttled=" + isThrottled + " (level=" + throttleLevel + ")" +
                    ", freq=" + freqPercentOfMax + "% of max}";
        }
    }

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public SystemLoadCollector(Context context) {
        this.context = context.getApplicationContext();
        this.powerManager = (PowerManager) this.context.getSystemService(Context.POWER_SERVICE);
        detectBaselineFrequency();
    }

    // =========================================================================
    // MAIN COLLECTION METHOD
    // =========================================================================

    /**
     * Collect current system load metrics.
     * 
     * @return SystemLoad with all available metrics
     */
    public SystemLoad collect() {
        SystemLoad result = new SystemLoad();

        // Collect load averages from /proc/loadavg
        collectLoadAverages(result);

        // Collect CPU frequency info
        collectCpuFrequency(result);

        // Detect throttling
        detectThrottling(result);

        return result;
    }

    // =========================================================================
    // LOAD AVERAGES
    // =========================================================================

    /**
     * Reads load averages from /proc/loadavg.
     * Format: "0.50 0.25 0.15 1/234 12345"
     * load1m load5m load15m running/total lastPid
     */
    private void collectLoadAverages(SystemLoad result) {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/loadavg"))) {
            String line = br.readLine();
            if (line == null)
                return;

            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 5) {
                result.load1m = Float.parseFloat(parts[0]);
                result.load5m = Float.parseFloat(parts[1]);
                result.load15m = Float.parseFloat(parts[2]);

                // Parse "running/total"
                String[] procParts = parts[3].split("/");
                if (procParts.length >= 2) {
                    result.runnableProcesses = Integer.parseInt(procParts[0]);
                    result.totalProcesses = Integer.parseInt(procParts[1]);
                }

                result.lastPid = Integer.parseInt(parts[4]);
            }
        } catch (IOException | NumberFormatException e) {
            if (e.getMessage() != null && e.getMessage().contains("EACCES")) {
                if (!hasLoggedEacces) {
                    Log.i(TAG, "Access to /proc/loadavg is restricted on this device");
                    hasLoggedEacces = true;
                }
            } else {
                Log.w(TAG, "Failed to read /proc/loadavg: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // CPU FREQUENCY
    // =========================================================================

    /**
     * Detects baseline max frequency for throttle comparison.
     */
    private void detectBaselineFrequency() {
        // Try to read the absolute max frequency
        baselineMaxFreqHz = readFrequencyFile(CPU_MAX_FREQ_PATH);
        if (baselineMaxFreqHz <= 0) {
            baselineMaxFreqHz = readFrequencyFile(CPU_POLICY_MAX_PATH);
        }

        baselineDetected = baselineMaxFreqHz > 0;
        if (baselineDetected) {
            Log.i(TAG, "Baseline CPU max freq: " + (baselineMaxFreqHz / 1000) + " MHz");
        }
    }

    /**
     * Collects current CPU frequency information.
     */
    private void collectCpuFrequency(SystemLoad result) {
        // Current frequency
        result.cpuCurrentFreqHz = readFrequencyFile(CPU_CUR_FREQ_PATH);

        // Current max allowed frequency (may be throttled)
        result.cpuMaxFreqHz = readFrequencyFile(CPU_MAX_FREQ_PATH);
        if (result.cpuMaxFreqHz <= 0) {
            result.cpuMaxFreqHz = readFrequencyFile(CPU_POLICY_MAX_PATH);
        }

        // Calculate percentage of baseline max
        if (baselineMaxFreqHz > 0 && result.cpuCurrentFreqHz > 0) {
            result.freqPercentOfMax = (int) ((result.cpuCurrentFreqHz * 100) / baselineMaxFreqHz);
            result.freqPercentOfMax = Math.max(0, Math.min(100, result.freqPercentOfMax));
        }
    }

    /**
     * Reads frequency value from sysfs (values are in kHz, we return Hz).
     */
    private long readFrequencyFile(String path) {
        File file = new File(path);
        if (!file.exists() || !file.canRead()) {
            return 0;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line = br.readLine();
            if (line != null) {
                // sysfs CPU freq is in kHz, convert to Hz
                return Long.parseLong(line.trim()) * 1000;
            }
        } catch (IOException | NumberFormatException e) {
            // Ignore
        }
        return 0;
    }

    // =========================================================================
    // THROTTLE DETECTION
    // =========================================================================

    /**
     * Detects if CPU is being thermally throttled.
     */
    private void detectThrottling(SystemLoad result) {
        result.isThrottled = false;
        result.throttleLevel = 0;

        // Method 1: Frequency-based detection
        if (baselineMaxFreqHz > 0 && result.cpuMaxFreqHz > 0) {
            // Compare current max to baseline max
            float freqRatio = (float) result.cpuMaxFreqHz / baselineMaxFreqHz;

            if (freqRatio < 0.5f) {
                result.isThrottled = true;
                result.throttleLevel = 3; // Severe: <50% of max
            } else if (freqRatio < 0.7f) {
                result.isThrottled = true;
                result.throttleLevel = 2; // Moderate: 50-70% of max
            } else if (freqRatio < 0.9f) {
                result.isThrottled = true;
                result.throttleLevel = 1; // Light: 70-90% of max
            }
        }

        // Method 2: Check thermal framework (Android Q+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // Use PowerManager thermal status if available
                int thermalStatus = powerManager.getCurrentThermalStatus();

                switch (thermalStatus) {
                    case PowerManager.THERMAL_STATUS_SEVERE:
                    case PowerManager.THERMAL_STATUS_CRITICAL:
                    case PowerManager.THERMAL_STATUS_EMERGENCY:
                    case PowerManager.THERMAL_STATUS_SHUTDOWN:
                        result.isThrottled = true;
                        result.throttleLevel = Math.max(result.throttleLevel, 3);
                        break;
                    case PowerManager.THERMAL_STATUS_MODERATE:
                        result.isThrottled = true;
                        result.throttleLevel = Math.max(result.throttleLevel, 2);
                        break;
                    case PowerManager.THERMAL_STATUS_LIGHT:
                        result.isThrottled = true;
                        result.throttleLevel = Math.max(result.throttleLevel, 1);
                        break;
                }
            } catch (Exception e) {
                // Thermal API not available
            }
        }
    }

    /**
     * Quick check if thermal throttling is currently active.
     */
    public boolean isThrottling() {
        SystemLoad load = collect();
        return load.isThrottled;
    }

    /**
     * Gets current throttle level (0-3).
     */
    public int getThrottleLevel() {
        SystemLoad load = collect();
        return load.throttleLevel;
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Checks if load averages are elevated.
     * 
     * @param threshold Load average threshold (e.g., 4.0 for quad-core device)
     * @return true if 1-minute load exceeds threshold
     */
    public boolean isLoadElevated(float threshold) {
        SystemLoad load = collect();
        return load.load1m > threshold;
    }

    /**
     * Gets load average normalized by CPU count.
     * Values > 1.0 indicate system is overloaded.
     * 
     * @return Normalized load (0.0 - N where 1.0 = all CPUs fully utilized)
     */
    public float getNormalizedLoad() {
        SystemLoad load = collect();
        int cpuCount = Runtime.getRuntime().availableProcessors();
        return cpuCount > 0 ? load.load1m / cpuCount : load.load1m;
    }

    /**
     * Gets the baseline max frequency detected at startup.
     */
    public long getBaselineMaxFreqHz() {
        return baselineMaxFreqHz;
    }
}
