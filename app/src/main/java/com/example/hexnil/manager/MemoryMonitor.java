package com.example.hexnil.manager;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.MemorySnapshot;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Memory Monitor
 * 
 * Collects RAM usage snapshots using official Android APIs:
 * - ActivityManager.MemoryInfo for system memory stats
 * - ActivityManager.getRunningAppProcesses() for process count
 * 
 * Sampling frequency: Every 30-60 seconds (configured by caller)
 * 
 * Non-root compatible: Uses only public Android APIs
 */
public class MemoryMonitor {

    private static final String TAG = "MemoryMonitor";

    private final Context context;
    private final HexNilDatabase database;
    private final ActivityManager activityManager;
    private final Executor executor;

    // Thresholds for classification
    private static final float CRITICAL_MEMORY_PERCENT = 90.0f;
    private static final float WARNING_MEMORY_PERCENT = 80.0f;

    public MemoryMonitor(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Capture a memory snapshot and store it in the database.
     * This should be called periodically (every 30-60 seconds).
     * 
     * @param batteryLevel Current battery level (0-100)
     * @param temperature  Current temperature in Celsius
     * @param isCharging   Whether device is currently charging
     * @param isScreenOn   Whether screen is currently on
     */
    public void captureSnapshot(int batteryLevel, float temperature,
            boolean isCharging, boolean isScreenOn) {
        executor.execute(() -> {
            try {
                MemorySnapshot snapshot = collectMemoryData();
                if (snapshot != null) {
                    // Add context data
                    snapshot.batteryLevel = batteryLevel;
                    snapshot.temperature = temperature;
                    snapshot.isCharging = isCharging;
                    snapshot.isScreenOn = isScreenOn;

                    // Store in database
                    long id = database.memorySnapshotDao().insert(snapshot);

                    if (snapshot.isLowMemory) {
                        Log.w(TAG, "Low memory detected! " + snapshot.getFormattedStats());
                    } else {
                        Log.d(TAG, "Memory snapshot captured: " + snapshot.getFormattedStats());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error capturing memory snapshot", e);
            }
        });
    }

    /**
     * Collect memory data from system.
     * 
     * @return MemorySnapshot with current memory state, or null on error
     */
    public MemorySnapshot collectMemoryData() {
        if (activityManager == null) {
            Log.e(TAG, "ActivityManager not available");
            return null;
        }

        try {
            // Get system memory info
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memInfo);

            // Create snapshot
            MemorySnapshot snapshot = new MemorySnapshot(
                    System.currentTimeMillis(),
                    memInfo.totalMem,
                    memInfo.availMem,
                    memInfo.lowMemory,
                    memInfo.threshold);

            // Get running process count
            try {
                List<ActivityManager.RunningAppProcessInfo> processes = activityManager.getRunningAppProcesses();
                if (processes != null) {
                    snapshot.runningAppCount = processes.size();

                    // Try to find top memory app (limited on newer Android)
                    findTopMemoryApp(snapshot, processes);
                }
            } catch (Exception e) {
                Log.d(TAG, "Could not get running processes: " + e.getMessage());
                snapshot.runningAppCount = -1;
            }

            return snapshot;
        } catch (Exception e) {
            Log.e(TAG, "Error collecting memory data", e);
            return null;
        }
    }

    /**
     * Try to find the app using most memory.
     * Note: This has limited visibility on Android 11+ without special permissions.
     */
    private void findTopMemoryApp(MemorySnapshot snapshot,
            List<ActivityManager.RunningAppProcessInfo> processes) {
        if (processes == null || processes.isEmpty()) {
            return;
        }

        try {
            String topApp = null;
            long topMemory = 0;

            // Get memory for each process
            int[] pids = new int[processes.size()];
            for (int i = 0; i < processes.size(); i++) {
                pids[i] = processes.get(i).pid;
            }

            // getProcessMemoryInfo is expensive, so we sample it carefully
            android.os.Debug.MemoryInfo[] memInfos = activityManager.getProcessMemoryInfo(pids);

            if (memInfos != null) {
                for (int i = 0; i < memInfos.length && i < processes.size(); i++) {
                    if (memInfos[i] != null) {
                        // getTotalPss() returns KB
                        long pss = memInfos[i].getTotalPss() * 1024L;
                        if (pss > topMemory) {
                            topMemory = pss;
                            topApp = processes.get(i).processName;
                        }
                    }
                }
            }

            if (topApp != null && topMemory > 0) {
                snapshot.topMemoryApp = topApp;
                snapshot.topMemoryAppBytes = topMemory;
            }
        } catch (SecurityException e) {
            // Expected on newer Android without PACKAGE_USAGE_STATS permission
            Log.d(TAG, "Cannot get detailed memory info (permission restricted)");
        } catch (Exception e) {
            Log.d(TAG, "Error finding top memory app: " + e.getMessage());
        }
    }

    /**
     * Get memory statistics for a time range.
     * 
     * @param since Start time in millis
     * @return List of memory snapshots in the range
     */
    public List<MemorySnapshot> getSnapshotsSince(long since) {
        return database.memorySnapshotDao().getSince(since);
    }

    /**
     * Get low memory events in a time range.
     * 
     * @param since Start time in millis
     * @return List of low memory snapshots
     */
    public List<MemorySnapshot> getLowMemoryEventsSince(long since) {
        return database.memorySnapshotDao().getLowMemoryEventsSince(since);
    }

    /**
     * Get average memory usage since a timestamp.
     * 
     * @param since Start time in millis
     * @return Average memory usage percentage
     */
    public float getAverageMemoryUsageSince(long since) {
        return database.memorySnapshotDao().getAverageMemoryUsageSince(since);
    }

    /**
     * Get low memory event count in a time range.
     * 
     * @param start Start time in millis
     * @param end   End time in millis
     * @return Count of low memory events
     */
    public int getLowMemoryEventCount(long start, long end) {
        return database.memorySnapshotDao().getLowMemoryEventCountInRange(start, end);
    }

    /**
     * Check if system is currently in low memory state.
     * 
     * @return true if low memory
     */
    public boolean isLowMemory() {
        if (activityManager == null)
            return false;

        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);
        return memInfo.lowMemory;
    }

    /**
     * Get current memory usage percentage.
     * 
     * @return Memory usage as percentage (0-100)
     */
    public float getCurrentMemoryUsagePercent() {
        if (activityManager == null)
            return -1;

        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);

        long used = memInfo.totalMem - memInfo.availMem;
        return (float) used / memInfo.totalMem * 100f;
    }

    /**
     * Get memory pressure level.
     * 
     * @return "normal", "warning", "critical", or "low_memory"
     */
    public String getMemoryPressureLevel() {
        float usage = getCurrentMemoryUsagePercent();

        if (isLowMemory()) {
            return "low_memory";
        } else if (usage >= CRITICAL_MEMORY_PERCENT) {
            return "critical";
        } else if (usage >= WARNING_MEMORY_PERCENT) {
            return "warning";
        } else {
            return "normal";
        }
    }

    /**
     * Clean up old snapshots to prevent unbounded database growth.
     * 
     * @param olderThan Delete snapshots older than this timestamp
     * @return Number of deleted records
     */
    public int cleanupOldSnapshots(long olderThan) {
        return database.memorySnapshotDao().deleteOlderThan(olderThan);
    }

    /**
     * Get total snapshot count in database.
     * 
     * @return Total count
     */
    public int getSnapshotCount() {
        return database.memorySnapshotDao().getCount();
    }
}
