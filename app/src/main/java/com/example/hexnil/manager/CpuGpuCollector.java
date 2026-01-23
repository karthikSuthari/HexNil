package com.example.hexnil.manager;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * OEM-Grade CPU and GPU Collector
 * 
 * Collects real CPU and GPU metrics with device-aware fallback strategies.
 * 
 * CPU Collection:
 * - System-wide CPU % via /proc/stat (delta-based for accuracy)
 * - Per-app CPU % via /proc/[pid]/stat
 * 
 * GPU Collection (multi-vendor support):
 * - Qualcomm Adreno: /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage
 * - ARM Mali: /sys/class/misc/mali0/device/utilization
 * - PowerVR: /sys/kernel/debug/pvr/driver_stats
 * - MediaTek: /sys/kernel/debug/ged/hal/gpu_utilization
 * 
 * Returns -1 for unavailable metrics with explicit availability flag.
 */
public class CpuGpuCollector {

    private static final String TAG = "CpuGpuCollector";

    // =========================================================================
    // CPU TRACKING STATE
    // =========================================================================

    // System CPU delta tracking
    private long lastCpuUser = 0;
    private long lastCpuNice = 0;
    private long lastCpuSystem = 0;
    private long lastCpuIdle = 0;
    private long lastCpuIowait = 0;
    private long lastCpuIrq = 0;
    private long lastCpuSoftirq = 0;
    private long lastCpuSteal = 0;
    private long lastCpuTotal = 0;
    private boolean hasPreviousCpuReading = false;
    private boolean hasLoggedCpuEacces = false;

    // Per-process CPU tracking (pid -> [utime, stime, totalCpuTime])
    private final Map<Integer, long[]> pidCpuTimes = new HashMap<>();

    // =========================================================================
    // GPU TRACKING STATE
    // =========================================================================

    // GPU path detection (cached after first successful read)
    private String gpuLoadPath = null;
    private boolean gpuPathChecked = false;
    private boolean gpuAvailable = false;

    // Known GPU sysfs paths
    private static final String[] GPU_LOAD_PATHS = {
            // Qualcomm Adreno
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/devfreq/5000000.qcom,kgsl-3d0/gpu_load",

            // ARM Mali
            "/sys/class/misc/mali0/device/utilization",
            "/sys/devices/platform/mali.0/utilization",
            "/sys/kernel/gpu/gpu_utilization",

            // MediaTek
            "/sys/kernel/debug/ged/hal/gpu_utilization",
            "/proc/gpufreq/gpufreq_var_dump",

            // Samsung Exynos
            "/sys/class/devfreq/17500000.mali/cur_freq",

            // Generic devfreq
            "/sys/class/devfreq/soc:qcom,gpu-bwmon/cur_freq",
    };

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================

    public CpuGpuCollector() {
        // GPU path will be detected on first call
    }

    // =========================================================================
    // SYSTEM CPU COLLECTION
    // =========================================================================

    /**
     * Collects system-wide CPU load percentage using delta calculation.
     * 
     * @return CPU percentage (0-100) or -1 if unavailable
     */
    public int collectSystemCpuPercent() {
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = br.readLine();
            if (line == null || !line.startsWith("cpu ")) {
                return -1;
            }

            String[] parts = line.trim().split("\\s+");
            if (parts.length < 8) {
                return -1;
            }

            // Parse CPU times
            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);
            long iowait = Long.parseLong(parts[5]);
            long irq = Long.parseLong(parts[6]);
            long softirq = Long.parseLong(parts[7]);
            long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0;

            // Calculate totals
            long totalIdle = idle + iowait;
            long total = user + nice + system + idle + iowait + irq + softirq + steal;

            // Calculate percentage using delta
            int result = -1;
            if (hasPreviousCpuReading) {
                long diffTotal = total - lastCpuTotal;
                long diffIdle = totalIdle - (lastCpuIdle + lastCpuIowait);

                if (diffTotal > 0) {
                    long diffBusy = diffTotal - diffIdle;
                    result = (int) Math.max(0, Math.min(100, (diffBusy * 100) / diffTotal));
                }
            } else {
                // First reading: return instantaneous load (less accurate, but useful)
                long busyTime = user + nice + system + irq + softirq + steal;
                if (total > 0) {
                    result = (int) Math.max(0, Math.min(100, (busyTime * 100) / total));
                }
            }

            // Store for next delta
            lastCpuUser = user;
            lastCpuNice = nice;
            lastCpuSystem = system;
            lastCpuIdle = idle;
            lastCpuIowait = iowait;
            lastCpuIrq = irq;
            lastCpuSoftirq = softirq;
            lastCpuSteal = steal;
            lastCpuTotal = total;
            hasPreviousCpuReading = true;

            return result;

        } catch (IOException | NumberFormatException e) {
            if (e.getMessage() != null && e.getMessage().contains("EACCES")) {
                if (!hasLoggedCpuEacces) {
                    Log.i(TAG, "Access to /proc/stat is restricted on this device");
                    hasLoggedCpuEacces = true;
                }
            } else {
                Log.w(TAG, "Failed to read /proc/stat: " + e.getMessage());
            }
            return -1;
        }
    }

    /**
     * Collects CPU usage for a specific process by PID.
     * Uses delta calculation for accurate percentage.
     * 
     * @param pid Process ID
     * @return CPU percentage (0-100) or -1 if unavailable
     */
    public int collectProcessCpuPercent(int pid) {
        if (pid <= 0)
            return -1;

        String statPath = "/proc/" + pid + "/stat";
        File statFile = new File(statPath);
        if (!statFile.exists()) {
            pidCpuTimes.remove(pid);
            return -1;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(statPath))) {
            String line = br.readLine();
            if (line == null)
                return -1;

            // Parse /proc/[pid]/stat
            // Format: pid (comm) state utime stime cutime cstime ...
            // We need fields 14 (utime) and 15 (stime)

            // Handle process name with spaces by finding last ')'
            int lastParen = line.lastIndexOf(')');
            if (lastParen < 0)
                return -1;

            String[] parts = line.substring(lastParen + 2).split("\\s+");
            if (parts.length < 13)
                return -1;

            // utime is field 14, stime is field 15 (0-indexed after state)
            long utime = Long.parseLong(parts[11]); // user time
            long stime = Long.parseLong(parts[12]); // system time
            long processTime = utime + stime;

            // Get system total CPU time for percentage calculation
            long systemTotal = lastCpuTotal;
            if (systemTotal <= 0) {
                // Force a system CPU read first
                collectSystemCpuPercent();
                systemTotal = lastCpuTotal;
            }

            // Calculate using delta
            int result = -1;
            long[] previous = pidCpuTimes.get(pid);
            if (previous != null && previous.length >= 2) {
                long prevProcessTime = previous[0];
                long prevSystemTotal = previous[1];

                long diffProcess = processTime - prevProcessTime;
                long diffSystem = systemTotal - prevSystemTotal;

                if (diffSystem > 0 && diffProcess >= 0) {
                    // Scale by number of CPUs for per-process percentage
                    int numCpus = Runtime.getRuntime().availableProcessors();
                    result = (int) Math.max(0, Math.min(100,
                            (diffProcess * 100 * numCpus) / diffSystem));
                }
            }

            // Store for next delta
            pidCpuTimes.put(pid, new long[] { processTime, systemTotal });
            return result;

        } catch (IOException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
            Log.w(TAG, "Failed to read /proc/" + pid + "/stat: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Clears tracking for a specific PID (call when process terminates).
     */
    public void clearProcessTracking(int pid) {
        pidCpuTimes.remove(pid);
    }

    /**
     * Clears all per-process CPU tracking.
     */
    public void clearAllProcessTracking() {
        pidCpuTimes.clear();
    }

    // =========================================================================
    // GPU COLLECTION
    // =========================================================================

    /**
     * Collects GPU load percentage with device-aware fallback.
     * 
     * @return GPU percentage (0-100) or -1 if device doesn't support GPU monitoring
     */
    public int collectGpuPercent() {
        // First time: detect GPU path
        if (!gpuPathChecked) {
            detectGpuPath();
        }

        if (!gpuAvailable || gpuLoadPath == null) {
            return -1;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(gpuLoadPath))) {
            String line = br.readLine();
            if (line == null)
                return -1;

            // Parse based on known formats
            return parseGpuValue(line, gpuLoadPath);

        } catch (IOException e) {
            Log.w(TAG, "Failed to read GPU load: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Detects which GPU sysfs path is available on this device.
     */
    private void detectGpuPath() {
        gpuPathChecked = true;
        gpuAvailable = false;
        gpuLoadPath = null;

        for (String path : GPU_LOAD_PATHS) {
            File file = new File(path);
            if (file.exists() && file.canRead()) {
                // Verify we can actually read a value
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line = br.readLine();
                    if (line != null && !line.isEmpty()) {
                        int value = parseGpuValue(line, path);
                        if (value >= 0) {
                            gpuLoadPath = path;
                            gpuAvailable = true;
                            Log.i(TAG, "GPU load path detected: " + path);
                            return;
                        }
                    }
                } catch (IOException ignored) {
                    // Continue trying other paths
                }
            }
        }

        Log.i(TAG, "No GPU load path available on this device");
    }

    /**
     * Parses GPU value from different format strings.
     */
    private int parseGpuValue(String line, String path) {
        try {
            line = line.trim();

            // Format: "XX%" or "XX"
            if (line.endsWith("%")) {
                return Integer.parseInt(line.replace("%", "").trim());
            }

            // Format: "busy XX%" (Qualcomm)
            if (line.contains("busy")) {
                String[] parts = line.split("\\s+");
                for (String part : parts) {
                    if (part.endsWith("%")) {
                        return Integer.parseInt(part.replace("%", ""));
                    }
                    try {
                        return Integer.parseInt(part);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            // Format: "XX YY" where XX is busy time (Mali)
            if (path.contains("utilization")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 1) {
                    int value = Integer.parseInt(parts[0]);
                    // Mali reports as 0-256 or 0-100
                    return value > 100 ? (value * 100) / 256 : value;
                }
            }

            // Format: plain integer
            int value = Integer.parseInt(line);
            return Math.max(0, Math.min(100, value));

        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Whether GPU metrics are available on this device.
     */
    public boolean isGpuAvailable() {
        if (!gpuPathChecked) {
            detectGpuPath();
        }
        return gpuAvailable;
    }

    /**
     * Gets the detected GPU sysfs path (for debugging).
     */
    public String getGpuLoadPath() {
        return gpuLoadPath;
    }

    // =========================================================================
    // COMBINED COLLECTION
    // =========================================================================

    /**
     * Result holder for combined CPU/GPU collection.
     */
    public static class CpuGpuMetrics {
        public int systemCpuPercent = -1;
        public int gpuPercent = -1;
        public boolean isGpuAvailable = false;
        public long collectionTimestamp = System.currentTimeMillis();

        @Override
        public String toString() {
            return "CpuGpuMetrics{cpu=" + systemCpuPercent +
                    "%, gpu=" + gpuPercent +
                    "%, gpuAvailable=" + isGpuAvailable + "}";
        }
    }

    /**
     * Collects both CPU and GPU metrics in one call.
     */
    public CpuGpuMetrics collectAll() {
        CpuGpuMetrics metrics = new CpuGpuMetrics();
        metrics.systemCpuPercent = collectSystemCpuPercent();
        metrics.gpuPercent = collectGpuPercent();
        metrics.isGpuAvailable = gpuAvailable;
        return metrics;
    }

    /**
     * Resets all tracking state (call on session reset).
     */
    public void reset() {
        hasPreviousCpuReading = false;
        lastCpuTotal = 0;
        pidCpuTimes.clear();
        // Don't reset GPU path detection - it's device-specific
    }
}
