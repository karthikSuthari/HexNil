package com.example.hexnil.intelligence.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * App Anomaly - independent anomaly records for the App Intelligence module.
 * Does NOT reference the core `anomaly_records` table.
 * 
 * Part of the isolated App Intelligence module.
 * All anomaly attribution and tracking is self-contained.
 */
@Entity(tableName = "ai_app_anomalies", indices = {
        @Index(value = { "package_name" }),
        @Index(value = { "detected_at" }),
        @Index(value = { "anomaly_type" }),
        @Index(value = { "severity" }),
        @Index(value = { "session_id" }),
        @Index(value = { "resolved", "detected_at" })
})
public class AppAnomaly {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // Correlation (no FKs!)
    @ColumnInfo(name = "package_name")
    public String packageName;

    @ColumnInfo(name = "session_id")
    public String sessionId;

    // Detection Time
    @ColumnInfo(name = "detected_at")
    public long detectedAt;

    // Anomaly Classification
    @ColumnInfo(name = "anomaly_type")
    public String anomalyType; // "excessive_drain", "thermal_spike", "background_abuse",
                               // "wakelock_abuse", "network_abuse", "overnight_drain"

    @ColumnInfo(name = "severity")
    public String severity; // "info", "low", "medium", "high", "critical"

    // Attribution
    @ColumnInfo(name = "cause_category")
    public String causeCategory; // "app_behavior", "system", "hardware", "unknown"

    @ColumnInfo(name = "cause_reason")
    public String causeReason; // Human-readable explanation

    // Metrics
    @ColumnInfo(name = "expected_value")
    public Float expectedValue;

    @ColumnInfo(name = "actual_value")
    public Float actualValue;

    @ColumnInfo(name = "deviation_percent")
    public Float deviationPercent;

    @ColumnInfo(name = "metric_name")
    public String metricName;

    @ColumnInfo(name = "metric_unit")
    public String metricUnit;

    // Confidence
    @ColumnInfo(name = "confidence_score")
    public float confidenceScore; // 0-100

    @ColumnInfo(name = "detection_method")
    public String detectionMethod; // "threshold", "statistical", "ml_model", "heuristic"

    // Context Snapshot
    @ColumnInfo(name = "battery_level")
    public Integer batteryLevel;

    @ColumnInfo(name = "was_charging")
    public Boolean wasCharging;

    @ColumnInfo(name = "screen_on")
    public Boolean screenOn;

    @ColumnInfo(name = "network_type")
    public String networkType;

    // Time Period Analyzed
    @ColumnInfo(name = "period_start")
    public Long periodStart;

    @ColumnInfo(name = "period_end")
    public Long periodEnd;

    @ColumnInfo(name = "data_points_analyzed")
    public Integer dataPointsAnalyzed;

    // Recommendations (AI-Ready)
    @ColumnInfo(name = "recommendation")
    public String recommendation;

    @ColumnInfo(name = "impact_description")
    public String impactDescription;

    @ColumnInfo(name = "root_cause_hypothesis")
    public String rootCauseHypothesis;

    // Status
    @ColumnInfo(name = "acknowledged")
    public boolean acknowledged;

    @ColumnInfo(name = "resolved")
    public boolean resolved;

    @ColumnInfo(name = "resolved_at")
    public Long resolvedAt;

    // Audit
    @ColumnInfo(name = "created_at")
    public long createdAt;

    public AppAnomaly() {
        this.detectedAt = System.currentTimeMillis();
        this.createdAt = this.detectedAt;
        this.acknowledged = false;
        this.resolved = false;
    }

    // =========================================================================
    // FACTORY METHODS FOR COMMON ANOMALY TYPES
    // =========================================================================

    /**
     * Create an excessive drain anomaly.
     */
    public static AppAnomaly excessiveDrain(String packageName, String sessionId,
            float expectedDrain, float actualDrain,
            String reason) {
        AppAnomaly anomaly = new AppAnomaly();
        anomaly.packageName = packageName;
        anomaly.sessionId = sessionId;
        anomaly.anomalyType = "excessive_drain";
        anomaly.causeCategory = "app_behavior";
        anomaly.causeReason = reason;
        anomaly.expectedValue = expectedDrain;
        anomaly.actualValue = actualDrain;
        anomaly.deviationPercent = expectedDrain > 0 ? ((actualDrain - expectedDrain) / expectedDrain) * 100 : 100f;
        anomaly.metricName = "drain_rate_per_hour";
        anomaly.metricUnit = "%/hr";
        anomaly.detectionMethod = "threshold";

        // Set severity based on deviation
        if (anomaly.deviationPercent >= 200 || actualDrain > 20) {
            anomaly.severity = "critical";
            anomaly.confidenceScore = 95f;
        } else if (anomaly.deviationPercent >= 100 || actualDrain > 15) {
            anomaly.severity = "high";
            anomaly.confidenceScore = 85f;
        } else if (anomaly.deviationPercent >= 50) {
            anomaly.severity = "medium";
            anomaly.confidenceScore = 75f;
        } else {
            anomaly.severity = "low";
            anomaly.confidenceScore = 65f;
        }

        return anomaly;
    }

    /**
     * Create a thermal spike anomaly.
     */
    public static AppAnomaly thermalSpike(String packageName, String sessionId,
            float maxTemp, float avgTemp,
            String reason) {
        AppAnomaly anomaly = new AppAnomaly();
        anomaly.packageName = packageName;
        anomaly.sessionId = sessionId;
        anomaly.anomalyType = "thermal_spike";
        anomaly.causeCategory = "app_behavior";
        anomaly.causeReason = reason;
        anomaly.expectedValue = 35f;
        anomaly.actualValue = maxTemp;
        anomaly.deviationPercent = ((maxTemp - 35f) / 35f) * 100;
        anomaly.metricName = "max_temperature";
        anomaly.metricUnit = "°C";
        anomaly.detectionMethod = "threshold";

        if (maxTemp >= 45) {
            anomaly.severity = "critical";
            anomaly.confidenceScore = 95f;
        } else if (maxTemp >= 42) {
            anomaly.severity = "high";
            anomaly.confidenceScore = 90f;
        } else if (maxTemp >= 40) {
            anomaly.severity = "medium";
            anomaly.confidenceScore = 80f;
        } else {
            anomaly.severity = "low";
            anomaly.confidenceScore = 70f;
        }

        return anomaly;
    }

    /**
     * Create a background abuse anomaly.
     */
    public static AppAnomaly backgroundAbuse(String packageName, String sessionId,
            long wakelockMs, int alarmWakes,
            String reason) {
        AppAnomaly anomaly = new AppAnomaly();
        anomaly.packageName = packageName;
        anomaly.sessionId = sessionId;
        anomaly.anomalyType = "background_abuse";
        anomaly.causeCategory = "app_behavior";
        anomaly.causeReason = reason;
        anomaly.metricName = "wakelock_time";
        anomaly.metricUnit = "ms";
        anomaly.actualValue = (float) wakelockMs;
        anomaly.expectedValue = 60000f; // 1 minute expected max
        anomaly.deviationPercent = ((wakelockMs - 60000f) / 60000f) * 100;
        anomaly.detectionMethod = "heuristic";

        float severity = (wakelockMs / 300000f) + (alarmWakes * 0.5f); // 5 min + alarms
        if (severity >= 5) {
            anomaly.severity = "critical";
            anomaly.confidenceScore = 90f;
        } else if (severity >= 3) {
            anomaly.severity = "high";
            anomaly.confidenceScore = 85f;
        } else if (severity >= 1) {
            anomaly.severity = "medium";
            anomaly.confidenceScore = 75f;
        } else {
            anomaly.severity = "low";
            anomaly.confidenceScore = 65f;
        }

        return anomaly;
    }

    /**
     * Create an overnight drain anomaly.
     */
    public static AppAnomaly overnightDrain(String packageName, String sessionId,
            float drainPercent, long durationMs,
            String reason) {
        AppAnomaly anomaly = new AppAnomaly();
        anomaly.packageName = packageName;
        anomaly.sessionId = sessionId;
        anomaly.anomalyType = "overnight_drain";
        anomaly.causeCategory = "app_behavior";
        anomaly.causeReason = reason;

        float hours = durationMs / 3600000f;
        float drainPerHour = drainPercent / hours;

        anomaly.actualValue = drainPerHour;
        anomaly.expectedValue = 0.5f; // Expected <0.5%/hr overnight
        anomaly.deviationPercent = ((drainPerHour - 0.5f) / 0.5f) * 100;
        anomaly.metricName = "overnight_drain_rate";
        anomaly.metricUnit = "%/hr";
        anomaly.detectionMethod = "threshold";

        if (drainPerHour >= 3) {
            anomaly.severity = "critical";
            anomaly.confidenceScore = 90f;
        } else if (drainPerHour >= 2) {
            anomaly.severity = "high";
            anomaly.confidenceScore = 85f;
        } else if (drainPerHour >= 1) {
            anomaly.severity = "medium";
            anomaly.confidenceScore = 75f;
        } else {
            anomaly.severity = "low";
            anomaly.confidenceScore = 65f;
        }

        return anomaly;
    }

    /**
     * Set context at time of detection.
     */
    public void setContext(int batteryLevel, boolean charging, boolean screenOn, String network) {
        this.batteryLevel = batteryLevel;
        this.wasCharging = charging;
        this.screenOn = screenOn;
        this.networkType = network;
    }

    /**
     * Set analysis period.
     */
    public void setPeriod(long start, long end, int dataPoints) {
        this.periodStart = start;
        this.periodEnd = end;
        this.dataPointsAnalyzed = dataPoints;
    }

    /**
     * Set recommendations for this anomaly.
     */
    public void setRecommendations(String recommendation, String impact, String rootCause) {
        this.recommendation = recommendation;
        this.impactDescription = impact;
        this.rootCauseHypothesis = rootCause;
    }

    /**
     * Mark as resolved.
     */
    public void resolve() {
        this.resolved = true;
        this.resolvedAt = System.currentTimeMillis();
    }

    /**
     * Check if this is a high-priority anomaly.
     */
    public boolean isHighPriority() {
        return "critical".equals(severity) || "high".equals(severity);
    }

    @Override
    public String toString() {
        return "AppAnomaly{" +
                "package='" + packageName + '\'' +
                ", type='" + anomalyType + '\'' +
                ", severity='" + severity + '\'' +
                ", confidence=" + String.format("%.0f%%", confidenceScore) +
                ", cause='" + causeReason + '\'' +
                '}';
    }
}
