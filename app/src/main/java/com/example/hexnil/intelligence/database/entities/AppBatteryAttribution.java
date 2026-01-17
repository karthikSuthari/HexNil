package com.example.hexnil.intelligence.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * App Battery Attribution - attributes battery usage to specific apps within
 * time windows.
 * 
 * Part of the isolated App Intelligence module.
 * Does NOT reference any tables in the core HexNil database.
 */
@Entity(tableName = "ai_app_battery_attribution", indices = {
        @Index(value = { "package_name" }),
        @Index(value = { "period_start", "period_end" }),
        @Index(value = { "battery_percent_used" }),
        @Index(value = { "session_id" })
})
public class AppBatteryAttribution {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // Correlation (no FKs!)
    @ColumnInfo(name = "package_name")
    public String packageName;

    @ColumnInfo(name = "session_id")
    public String sessionId; // Optional link to usage session

    // Time Window
    @ColumnInfo(name = "period_start")
    public long periodStart;

    @ColumnInfo(name = "period_end")
    public long periodEnd;

    // Battery Metrics
    @ColumnInfo(name = "battery_percent_used")
    public float batteryPercentUsed;

    @ColumnInfo(name = "drain_rate_per_hour")
    public float drainRatePerHour;

    // Resource Attribution
    @ColumnInfo(name = "cpu_time_ms")
    public long cpuTimeMs;

    @ColumnInfo(name = "network_bytes")
    public long networkBytes;

    @ColumnInfo(name = "wakelock_time_ms")
    public long wakelockTimeMs;

    @ColumnInfo(name = "partial_wakelock_ms")
    public long partialWakelockMs;

    @ColumnInfo(name = "alarm_wakeups")
    public int alarmWakeups;

    @ColumnInfo(name = "network_wakeups")
    public int networkWakeups;

    @ColumnInfo(name = "gps_requests")
    public int gpsRequests;

    // Context at Time of Measurement
    @ColumnInfo(name = "charging_state")
    public String chargingState;

    @ColumnInfo(name = "network_type")
    public String networkType;

    @ColumnInfo(name = "screen_state")
    public String screenState; // "on", "off", "doze"

    // Analysis Scores (computed locally)
    @ColumnInfo(name = "efficiency_score")
    public float efficiencyScore; // 0-100, higher = better

    @ColumnInfo(name = "drain_score")
    public float drainScore; // 0-100, higher = worse

    @ColumnInfo(name = "background_abuse_score")
    public float backgroundAbuseScore; // 0-100

    // Comparison Context
    @ColumnInfo(name = "category_avg_drain")
    public Float categoryAvgDrain;

    @ColumnInfo(name = "deviation_from_avg")
    public Float deviationFromAvg;

    // Notes
    @ColumnInfo(name = "drain_reasons_json")
    public String drainReasonsJson;

    // Audit
    @ColumnInfo(name = "created_at")
    public long createdAt;

    public AppBatteryAttribution() {
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * Create a battery attribution record for an app.
     */
    public static AppBatteryAttribution create(String packageName, String sessionId,
            long periodStart, long periodEnd,
            float batteryUsed, float drainRate) {
        AppBatteryAttribution attr = new AppBatteryAttribution();
        attr.packageName = packageName;
        attr.sessionId = sessionId;
        attr.periodStart = periodStart;
        attr.periodEnd = periodEnd;
        attr.batteryPercentUsed = batteryUsed;
        attr.drainRatePerHour = drainRate;
        return attr;
    }

    /**
     * Set resource usage metrics.
     */
    public void setResourceUsage(long cpuMs, long networkBytes, long wakelockMs,
            int alarmWakes, int networkWakes, int gps) {
        this.cpuTimeMs = cpuMs;
        this.networkBytes = networkBytes;
        this.wakelockTimeMs = wakelockMs;
        this.alarmWakeups = alarmWakes;
        this.networkWakeups = networkWakes;
        this.gpsRequests = gps;
    }

    /**
     * Set context at time of measurement.
     */
    public void setContext(String chargingState, String networkType, String screenState) {
        this.chargingState = chargingState;
        this.networkType = networkType;
        this.screenState = screenState;
    }

    /**
     * Calculate and set efficiency and drain scores.
     */
    public void calculateScores() {
        // Drain score: higher = worse battery efficiency
        float score = 0;
        score += batteryPercentUsed * 10;
        score += (wakelockTimeMs / 60000.0f) * 0.5f; // Wakelock minutes
        score += (partialWakelockMs / 60000.0f) * 0.3f;
        score += alarmWakeups * 0.8f;
        score += networkWakeups * 0.5f;
        this.drainScore = Math.min(100, score);

        // Efficiency score: inverse of drain score
        this.efficiencyScore = Math.max(0, 100 - this.drainScore);

        // Background abuse score based on wakelocks and wakeups
        float bgScore = 0;
        bgScore += (wakelockTimeMs / 300000.0f) * 20; // 5 min wakelock = 20 points
        bgScore += alarmWakeups * 5;
        bgScore += networkWakeups * 3;
        this.backgroundAbuseScore = Math.min(100, bgScore);
    }

    /**
     * Get period duration in hours.
     */
    public float getPeriodDurationHours() {
        return (periodEnd - periodStart) / 3600000.0f;
    }

    /**
     * Check if this is a high drain record.
     */
    public boolean isHighDrain() {
        return batteryPercentUsed > 5.0f || drainRatePerHour > 10.0f;
    }

    @Override
    public String toString() {
        return "AppBatteryAttribution{" +
                "packageName='" + packageName + '\'' +
                ", batteryUsed=" + String.format("%.1f%%", batteryPercentUsed) +
                ", drainRate=" + String.format("%.1f%%/hr", drainRatePerHour) +
                ", drainScore=" + String.format("%.0f", drainScore) +
                '}';
    }
}
