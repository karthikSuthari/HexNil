package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Regression Event Entity
 * 
 * Records detected performance regressions with full evidence chain.
 * This is the cornerstone of the regression detection system.
 * 
 * Regression Types:
 * - launch_time: App launch time degraded >20% vs baseline
 * - jank: Frame jank increased >15% vs baseline
 * - memory: Sustained low-memory pressure increase
 * - crash: New or increased crash frequency
 * - anr: Any new ANR after app/system update
 * - thermal: Sustained temperature increase >3°C
 * 
 * Triggers:
 * - app_update: Detected after app version change
 * - system_update: Detected after OS version change
 * - long_term_decay: Gradual degradation over 30+ days
 */
@Entity(tableName = "regression_events", indices = {
        @Index(value = { "detectedAt" }),
        @Index(value = { "packageName", "detectedAt" }),
        @Index(value = { "regressionType" }),
        @Index(value = { "isResolved" }),
        @Index(value = { "severity" })
})
public class RegressionEvent {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Timestamp when regression was detected
     */
    public long detectedAt;

    /**
     * Estimated timestamp when regression likely started
     */
    public long regressionStartTime;

    /**
     * Package name affected (null for system-level regressions)
     */
    public String packageName;

    /**
     * Human-readable app name
     */
    public String appLabel;

    /**
     * Type of regression:
     * "launch_time", "jank", "memory", "crash", "anr", "thermal"
     */
    public String regressionType;

    // =========================================================================
    // Measurements
    // =========================================================================

    /**
     * Baseline value before regression (e.g., avg launch time)
     */
    public float baselineValue;

    /**
     * Current value showing regression
     */
    public float currentValue;

    /**
     * Percentage change from baseline ((current - baseline) / baseline * 100)
     */
    public float deltaPercent;

    /**
     * Threshold that was exceeded to trigger detection
     * (e.g., 20 for 20% launch time threshold)
     */
    public float threshold;

    /**
     * Unit of measurement (e.g., "ms", "%", "°C", "count")
     */
    public String unit;

    // =========================================================================
    // Context
    // =========================================================================

    /**
     * What triggered the regression:
     * "app_update", "system_update", "long_term_decay", "unknown"
     */
    public String triggerEvent;

    /**
     * App version before (if app update triggered)
     */
    public String previousVersion;

    /**
     * App version after (current version)
     */
    public String currentVersion;

    /**
     * Previous system version (if system update triggered)
     */
    public String previousSystemVersion;

    /**
     * Current system version at detection
     */
    public String systemVersion;

    /**
     * Number of days the regression has persisted
     */
    public int persistenceDays;

    // =========================================================================
    // Evidence Chain
    // =========================================================================

    /**
     * Name of the table containing evidence data
     */
    public String evidenceTableName;

    /**
     * SQL query to retrieve evidence (for audit/verification)
     */
    public String evidenceQuery;

    /**
     * Number of data points supporting this regression
     */
    public int evidenceCount;

    /**
     * Date range of evidence (start timestamp)
     */
    public long evidenceStartTime;

    /**
     * Date range of evidence (end timestamp)
     */
    public long evidenceEndTime;

    /**
     * JSON summary of key evidence data points
     * Format: [{"timestamp":..., "value":...}, ...]
     */
    public String evidenceDataJson;

    // =========================================================================
    // AI Analysis
    // =========================================================================

    /**
     * Confidence score (0.0 to 1.0) that this is a real regression
     */
    public float confidenceScore;

    /**
     * Severity: "low", "medium", "high", "critical"
     */
    public String severity;

    /**
     * Natural language explanation of what degraded
     */
    public String explanation;

    /**
     * Root cause analysis (if determinable)
     */
    public String rootCause;

    /**
     * Trend: "worsening", "stable", "improving"
     */
    public String trend;

    /**
     * Recommended action for user
     */
    public String userRecommendation;

    /**
     * Recommended action for developers/engineers
     */
    public String engineerRecommendation;

    // =========================================================================
    // Resolution Status
    // =========================================================================

    /**
     * Whether this regression has been resolved
     */
    public boolean isResolved;

    /**
     * Timestamp when resolved (0 if not resolved)
     */
    public long resolvedAt;

    /**
     * How it was resolved (e.g., "app_update", "system_update",
     * "cleared_data", "manual_dismiss")
     */
    public String resolutionMethod;

    /**
     * Value after resolution (to confirm fix)
     */
    public float resolvedValue;

    /**
     * Whether user has been notified
     */
    public boolean userNotified;

    /**
     * Whether user dismissed the notification
     */
    public boolean userDismissed;

    // =========================================================================
    // Constructors
    // =========================================================================

    public RegressionEvent() {
        this.detectedAt = System.currentTimeMillis();
    }

    public RegressionEvent(String packageName, String regressionType,
            float baselineValue, float currentValue, float threshold) {
        this();
        this.packageName = packageName;
        this.regressionType = regressionType;
        this.baselineValue = baselineValue;
        this.currentValue = currentValue;
        this.threshold = threshold;
        calculateDelta();
        calculateSeverity();
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * Calculate percentage delta from baseline
     */
    public void calculateDelta() {
        if (baselineValue > 0) {
            this.deltaPercent = (currentValue - baselineValue) / baselineValue * 100f;
        } else {
            this.deltaPercent = 0;
        }
    }

    /**
     * Calculate severity based on delta and type
     */
    public void calculateSeverity() {
        // ANR regressions are always high/critical
        if ("anr".equals(regressionType)) {
            this.severity = "critical";
            return;
        }

        // Crash regressions are high
        if ("crash".equals(regressionType)) {
            this.severity = deltaPercent > 100 ? "critical" : "high";
            return;
        }

        // Other types based on delta magnitude
        float absDelta = Math.abs(deltaPercent);
        if (absDelta > 50) {
            this.severity = "critical";
        } else if (absDelta > 30) {
            this.severity = "high";
        } else if (absDelta > 15) {
            this.severity = "medium";
        } else {
            this.severity = "low";
        }
    }

    /**
     * Returns true if this is an app-level regression
     */
    public boolean isAppLevel() {
        return packageName != null && !packageName.isEmpty();
    }

    /**
     * Returns true if this was caused by an update
     */
    public boolean isUpdateRelated() {
        return "app_update".equals(triggerEvent) || "system_update".equals(triggerEvent);
    }

    /**
     * Returns true if high confidence (>0.8)
     */
    public boolean isHighConfidence() {
        return confidenceScore >= 0.8f;
    }

    /**
     * Returns true if requires user attention
     */
    public boolean requiresAttention() {
        return !isResolved && !userDismissed &&
                ("high".equals(severity) || "critical".equals(severity));
    }

    /**
     * Generate human-readable summary
     */
    public String getSummary() {
        String direction = deltaPercent > 0 ? "increased" : "decreased";
        return String.format(java.util.Locale.US,
                "%s %s by %.1f%% (from %.1f to %.1f %s)",
                getRegressionTypeLabel(),
                direction,
                Math.abs(deltaPercent),
                baselineValue,
                currentValue,
                unit != null ? unit : "");
    }

    /**
     * Get human-readable regression type label
     */
    public String getRegressionTypeLabel() {
        switch (regressionType) {
            case "launch_time":
                return "App launch time";
            case "jank":
                return "Frame drops";
            case "memory":
                return "Memory pressure";
            case "crash":
                return "Crash frequency";
            case "anr":
                return "ANR occurrences";
            case "thermal":
                return "Temperature";
            default:
                return regressionType;
        }
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US,
                "RegressionEvent{%s, type=%s, delta=%.1f%%, severity=%s, resolved=%b}",
                packageName, regressionType, deltaPercent, severity, isResolved);
    }
}
