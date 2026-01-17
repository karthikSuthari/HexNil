package com.example.hexnil.intelligence.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * App Registry - stores metadata about each tracked app.
 * Acts as a lookup table using package name as the logical key.
 * 
 * Part of the isolated App Intelligence module.
 * Does NOT reference any tables in the core HexNil database.
 */
@Entity(tableName = "ai_app_registry", indices = {
        @Index(value = { "package_name" }, unique = true),
        @Index(value = { "app_category" }),
        @Index(value = { "last_seen_at" })
})
public class AppRegistry {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // App Identification (logical key: package_name)
    @ColumnInfo(name = "package_name")
    public String packageName;

    @ColumnInfo(name = "app_name")
    public String appName;

    @ColumnInfo(name = "app_category")
    public String appCategory; // "social", "game", "streaming", "utility", etc.

    @ColumnInfo(name = "is_system_app")
    public boolean isSystemApp;

    @ColumnInfo(name = "is_preinstalled")
    public boolean isPreinstalled;

    // Tracking Metadata
    @ColumnInfo(name = "first_seen_at")
    public long firstSeenAt;

    @ColumnInfo(name = "last_seen_at")
    public long lastSeenAt;

    @ColumnInfo(name = "total_sessions")
    public int totalSessions;

    // Cached Aggregates (updated periodically)
    @ColumnInfo(name = "total_battery_used_pct")
    public float totalBatteryUsedPct;

    @ColumnInfo(name = "avg_drain_per_hour")
    public float avgDrainPerHour;

    @ColumnInfo(name = "total_foreground_ms")
    public long totalForegroundMs;

    @ColumnInfo(name = "total_background_ms")
    public long totalBackgroundMs;

    @ColumnInfo(name = "anomaly_count")
    public int anomalyCount;

    // Module Control
    @ColumnInfo(name = "tracking_enabled")
    public boolean trackingEnabled = true;

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "updated_at")
    public long updatedAt;

    public AppRegistry() {
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
        this.firstSeenAt = now;
        this.lastSeenAt = now;
        this.trackingEnabled = true;
    }

    /**
     * Create a new app registry entry for a package.
     */
    public static AppRegistry create(String packageName, String appName, String category,
            boolean isSystem, boolean isPreinstalled) {
        AppRegistry registry = new AppRegistry();
        registry.packageName = packageName;
        registry.appName = appName;
        registry.appCategory = category;
        registry.isSystemApp = isSystem;
        registry.isPreinstalled = isPreinstalled;
        return registry;
    }

    /**
     * Update the last seen timestamp and increment session count.
     */
    public void recordSession() {
        this.lastSeenAt = System.currentTimeMillis();
        this.updatedAt = this.lastSeenAt;
        this.totalSessions++;
    }

    /**
     * Update cached aggregates from session data.
     */
    public void updateAggregates(float batteryUsed, float drainRate,
            long foregroundMs, long backgroundMs) {
        this.totalBatteryUsedPct += batteryUsed;
        this.totalForegroundMs += foregroundMs;
        this.totalBackgroundMs += backgroundMs;

        // Running average for drain rate
        if (this.totalSessions > 0) {
            this.avgDrainPerHour = ((this.avgDrainPerHour * (this.totalSessions - 1)) + drainRate)
                    / this.totalSessions;
        } else {
            this.avgDrainPerHour = drainRate;
        }

        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Get total active time in hours.
     */
    public float getTotalActiveTimeHours() {
        return (totalForegroundMs + totalBackgroundMs) / 3600000.0f;
    }

    @Override
    public String toString() {
        return "AppRegistry{" +
                "packageName='" + packageName + '\'' +
                ", appName='" + appName + '\'' +
                ", category='" + appCategory + '\'' +
                ", sessions=" + totalSessions +
                ", totalDrain=" + String.format("%.1f%%", totalBatteryUsedPct) +
                '}';
    }
}
