package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * App Launch Metric Entity
 * 
 * Records app launch performance including cold, warm, and hot launch times.
 * Uses lifecycle timestamps and UsageStatsManager data for measurement.
 * 
 * Launch Types:
 * - Cold: App not in memory, full startup required
 * - Warm: App in memory but activity recreated
 * - Hot: App in memory and activity resumed
 */
@Entity(tableName = "app_launch_metrics", indices = {
        @Index(value = { "timestamp" }),
        @Index(value = { "packageName", "timestamp" }),
        @Index(value = { "launchType", "timestamp" })
})
public class AppLaunchMetric {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Timestamp when this launch occurred (System.currentTimeMillis())
     */
    public long timestamp;

    /**
     * Package name of the launched app
     */
    public String packageName;

    /**
     * Human-readable app label/name
     */
    public String appLabel;

    /**
     * Launch type: "cold", "warm", or "hot"
     */
    public String launchType;

    /**
     * Total launch duration in milliseconds.
     * Measured from intent dispatch to first draw completion.
     */
    public long launchDurationMs;

    /**
     * Time to initial display (TTID) in milliseconds.
     * Time until the app shows its first frame.
     */
    public long ttidMs;

    /**
     * Time to full display (TTFD) in milliseconds.
     * Time until app reports fully drawn (if reported by app).
     * May be 0 if app doesn't report this.
     */
    public long ttfdMs;

    /**
     * Battery level before launch (0-100)
     */
    public int beforeLevel;

    /**
     * Battery level after launch (0-100)
     */
    public int afterLevel;

    /**
     * Temperature before launch in Celsius
     */
    public float beforeTemp;

    /**
     * Temperature after launch in Celsius
     */
    public float afterTemp;

    /**
     * True if this is the first launch of this app today
     */
    public boolean isFirstLaunchToday;

    /**
     * Context in which the app was launched.
     * Values: "foreground", "notification", "widget", "shortcut", "assistant",
     * "unknown"
     */
    public String sessionContext;

    /**
     * App version code at launch time
     */
    public int appVersionCode;

    /**
     * App version name at launch time
     */
    public String appVersionName;

    /**
     * Device memory usage percentage at launch time
     */
    public float memoryUsagePercent;

    /**
     * Whether device was charging at launch time
     */
    public boolean isCharging;

    /**
     * Thermal headroom at launch time (0.0 to 1.0+)
     * Lower values indicate device is near thermal throttling.
     */
    public float thermalHeadroom;

    // =========================================================================
    // Constructors
    // =========================================================================

    public AppLaunchMetric() {
    }

    public AppLaunchMetric(String packageName, String launchType, long launchDurationMs) {
        this.timestamp = System.currentTimeMillis();
        this.packageName = packageName;
        this.launchType = launchType;
        this.launchDurationMs = launchDurationMs;
        this.ttidMs = launchDurationMs; // Default TTID to launch duration
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * Returns true if this is a cold launch
     */
    public boolean isColdLaunch() {
        return "cold".equalsIgnoreCase(launchType);
    }

    /**
     * Returns true if this is a warm launch
     */
    public boolean isWarmLaunch() {
        return "warm".equalsIgnoreCase(launchType);
    }

    /**
     * Returns true if this is a hot launch
     */
    public boolean isHotLaunch() {
        return "hot".equalsIgnoreCase(launchType);
    }

    /**
     * Returns true if launch time exceeds threshold for its type.
     * Cold launch: >5000ms is slow
     * Warm launch: >2000ms is slow
     * Hot launch: >1000ms is slow
     */
    public boolean isSlowLaunch() {
        if (isColdLaunch()) {
            return launchDurationMs > 5000;
        } else if (isWarmLaunch()) {
            return launchDurationMs > 2000;
        } else {
            return launchDurationMs > 1000;
        }
    }

    /**
     * Returns battery drain during launch
     */
    public int getBatteryDrain() {
        return beforeLevel - afterLevel;
    }

    /**
     * Returns temperature increase during launch
     */
    public float getTempIncrease() {
        return afterTemp - beforeTemp;
    }

    /**
     * Returns launch speed classification: "fast", "normal", "slow", "very_slow"
     */
    public String getLaunchSpeedGrade() {
        long threshold = isColdLaunch() ? 2000 : (isWarmLaunch() ? 1000 : 500);

        if (launchDurationMs <= threshold) {
            return "fast";
        } else if (launchDurationMs <= threshold * 2) {
            return "normal";
        } else if (launchDurationMs <= threshold * 4) {
            return "slow";
        } else {
            return "very_slow";
        }
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US,
                "AppLaunchMetric{%s, type=%s, duration=%dms, grade=%s}",
                packageName, launchType, launchDurationMs, getLaunchSpeedGrade());
    }
}
