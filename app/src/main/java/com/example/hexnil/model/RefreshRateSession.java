package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Stores ONE row per refresh rate session.
 * A session is created for each foreground app or home screen period.
 * 
 * Session boundaries:
 * - START: Foreground app changes, screen turns ON, app moves
 * background→foreground
 * - END: Foreground app changes, screen turns OFF, app goes
 * foreground→background
 * 
 * CRITICAL: Raw continuous samples are NEVER persisted - only aggregated
 * metrics.
 */
@Entity(tableName = "refresh_rate_sessions", indices = {
        @Index("startTime"),
        @Index("packageName"),
        @Index("sessionType"),
        @Index("hasStrongAnomaly")
})
public class RefreshRateSession {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // =========================================================================
    // Session Identification
    // =========================================================================

    /** UUID for this session */
    public String sessionId;

    /** Session type: "APP" or "HOME" */
    public String sessionType;

    /** Package name (e.g., com.instagram.android) or "launcher" for home screen */
    public String packageName;

    /** Display name of the app */
    public String appName;

    // =========================================================================
    // Timing
    // =========================================================================

    /** Session start timestamp in milliseconds */
    public long startTime;

    /** Session end timestamp in milliseconds */
    public long endTime;

    /** Total session duration in milliseconds */
    public long durationMs;

    // =========================================================================
    // Refresh Rate Metrics (Aggregated from in-memory samples)
    // =========================================================================

    /** Refresh rate at session start (Hz) */
    public float startRefreshRateHz;

    /** Refresh rate at session end (Hz) */
    public float endRefreshRateHz;

    /** Average refresh rate across session (Hz) */
    public float avgRefreshRateHz;

    /** Minimum refresh rate observed (Hz) */
    public float minRefreshRateHz;

    /** Maximum refresh rate observed (Hz) */
    public float maxRefreshRateHz;

    /** Time spent at high refresh rate (90Hz+) in milliseconds */
    public long timeAtHighHzMs;

    /** Time spent at low refresh rate (<=60Hz) in milliseconds */
    public long timeAtLowHzMs;

    /** Whether device has adaptive display enabled */
    public boolean isAdaptiveDisplay;

    // =========================================================================
    // Context During Session
    // =========================================================================

    /** Average screen brightness (0-255) */
    public float avgBrightness;

    /** Average CPU usage (0-100%) */
    public float avgCpuUsage;

    /** Average GPU usage (0-100%) */
    public float avgGpuUsage;

    /** Average battery current in mA */
    public float avgBatteryCurrent;

    /** Average temperature in Celsius */
    public float avgTemperature;

    /** Estimated battery cost due to high refresh rate (mAh) */
    public float estimatedBatteryCost;

    // =========================================================================
    // Anomaly Summary
    // =========================================================================

    /** Number of anomalies detected during this session */
    public int anomalyCount;

    /** Whether any strong/critical anomaly was detected */
    public boolean hasStrongAnomaly;

    // =========================================================================
    // Constructors & Factory Methods
    // =========================================================================

    public RefreshRateSession() {
        this.startTime = System.currentTimeMillis();
        this.sessionId = java.util.UUID.randomUUID().toString();
        this.anomalyCount = 0;
        this.hasStrongAnomaly = false;
        this.minRefreshRateHz = Float.MAX_VALUE;
        this.maxRefreshRateHz = 0f;
    }

    /**
     * Create an app session
     */
    public static RefreshRateSession appSession(String packageName, String appName) {
        RefreshRateSession session = new RefreshRateSession();
        // User Request: sessionType should store the actual App Name
        session.sessionType = appName != null ? appName : "APP";
        session.packageName = packageName;
        session.appName = appName;
        return session;
    }

    /**
     * Create a home screen / launcher session
     */
    public static RefreshRateSession homeSession() {
        RefreshRateSession session = new RefreshRateSession();
        session.sessionType = "Homescreen"; // User Request: Use "Homescreen" instead of "HOME"
        session.packageName = "launcher";
        session.appName = "Home Screen";
        return session;
    }

    /**
     * End the session and calculate final metrics
     */
    public void endSession() {
        this.endTime = System.currentTimeMillis();
        this.durationMs = this.endTime - this.startTime;
    }

    /**
     * Calculate percentage of time at high refresh rate
     */
    public float getHighHzPercentage() {
        if (durationMs <= 0)
            return 0f;
        return (timeAtHighHzMs * 100f) / durationMs;
    }

    /**
     * Check if session has significant high refresh usage
     */
    public boolean hasSignificantHighRefresh() {
        return getHighHzPercentage() > 50f || timeAtHighHzMs > 60000; // >50% or >1min
    }

    @Override
    public String toString() {
        return "RefreshRateSession{" +
                "type=" + sessionType +
                ", pkg=" + packageName +
                ", duration=" + (durationMs / 1000) + "s" +
                ", avgHz=" + String.format("%.1f", avgRefreshRateHz) +
                ", highHz=" + (timeAtHighHzMs / 1000) + "s" +
                ", anomalies=" + anomalyCount +
                '}';
    }
}
