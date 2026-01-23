package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Memory Snapshot Entity
 * 
 * Captures point-in-time RAM usage from ActivityManager.MemoryInfo.
 * Sampled every 30-60 seconds for memory pressure detection and trend analysis.
 * 
 * All values are from official Android APIs:
 * - ActivityManager.MemoryInfo.totalMem
 * - ActivityManager.MemoryInfo.availMem
 * - ActivityManager.MemoryInfo.lowMemory
 * - ActivityManager.MemoryInfo.threshold
 */
@Entity(tableName = "memory_snapshots", indices = {
        @Index(value = { "timestamp" }),
        @Index(value = { "isLowMemory", "timestamp" })
})
public class MemorySnapshot {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Timestamp when this snapshot was captured (System.currentTimeMillis())
     */
    public long timestamp;

    /**
     * Total memory available to the device in bytes.
     * From ActivityManager.MemoryInfo.totalMem
     */
    public long totalMemoryBytes;

    /**
     * Available memory on the device in bytes.
     * From ActivityManager.MemoryInfo.availMem
     */
    public long availableMemoryBytes;

    /**
     * Used memory in bytes (computed: totalMemoryBytes - availableMemoryBytes)
     */
    public long usedMemoryBytes;

    /**
     * Percentage of memory currently in use (0-100)
     */
    public float usedMemoryPercent;

    /**
     * True if the system considers itself in a low memory situation.
     * From ActivityManager.MemoryInfo.lowMemory
     */
    public boolean isLowMemory;

    /**
     * The threshold of availMem at which the system will kill background processes.
     * From ActivityManager.MemoryInfo.threshold
     */
    public long threshold;

    /**
     * Number of running app processes at snapshot time.
     * From ActivityManager.getRunningAppProcesses() size.
     */
    public int runningAppCount;

    /**
     * Package name of the app using the most memory (if determinable).
     * May be null if permission not granted or unavailable.
     */
    public String topMemoryApp;

    /**
     * Memory used by the top memory app in bytes.
     * Set to 0 if topMemoryApp is null.
     */
    public long topMemoryAppBytes;

    /**
     * Device battery level at snapshot time (0-100).
     * For correlation analysis.
     */
    public int batteryLevel;

    /**
     * Device temperature at snapshot time (Celsius).
     * For thermal-memory correlation.
     */
    public float temperature;

    /**
     * Whether device was charging at snapshot time.
     */
    public boolean isCharging;

    /**
     * Whether screen was on at snapshot time.
     */
    public boolean isScreenOn;

    // =========================================================================
    // Constructors
    // =========================================================================

    public MemorySnapshot() {
    }

    public MemorySnapshot(long timestamp, long totalMemoryBytes, long availableMemoryBytes,
            boolean isLowMemory, long threshold) {
        this.timestamp = timestamp;
        this.totalMemoryBytes = totalMemoryBytes;
        this.availableMemoryBytes = availableMemoryBytes;
        this.usedMemoryBytes = totalMemoryBytes - availableMemoryBytes;
        this.usedMemoryPercent = totalMemoryBytes > 0
                ? (float) usedMemoryBytes / totalMemoryBytes * 100f
                : 0f;
        this.isLowMemory = isLowMemory;
        this.threshold = threshold;
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * Returns memory stats in human-readable format (GB/MB).
     */
    public String getFormattedStats() {
        return String.format(java.util.Locale.US,
                "Used: %.1f GB / %.1f GB (%.1f%%) - %s",
                usedMemoryBytes / 1024.0 / 1024.0 / 1024.0,
                totalMemoryBytes / 1024.0 / 1024.0 / 1024.0,
                usedMemoryPercent,
                isLowMemory ? "LOW MEMORY" : "Normal");
    }

    /**
     * Returns available memory in MB for quick reference.
     */
    public float getAvailableMemoryMB() {
        return availableMemoryBytes / 1024f / 1024f;
    }

    /**
     * Returns used memory in MB.
     */
    public float getUsedMemoryMB() {
        return usedMemoryBytes / 1024f / 1024f;
    }

    /**
     * Returns true if memory usage is above critical threshold (>90%).
     */
    public boolean isCritical() {
        return usedMemoryPercent > 90f || isLowMemory;
    }

    /**
     * Returns true if memory usage is concerning (>80%).
     */
    public boolean isWarning() {
        return usedMemoryPercent > 80f;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US,
                "MemorySnapshot{id=%d, used=%.1f%%, lowMem=%b, apps=%d}",
                id, usedMemoryPercent, isLowMemory, runningAppCount);
    }
}
