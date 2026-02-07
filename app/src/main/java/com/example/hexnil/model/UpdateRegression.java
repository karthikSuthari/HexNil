package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * New problems introduced by the update that didn't exist before.
 * These are issues that appear in post-update data but not in pre-update data.
 */
@Entity(tableName = "update_regressions", foreignKeys = @ForeignKey(entity = UpdateSession.class, parentColumns = "id", childColumns = "sessionId", onDelete = ForeignKey.CASCADE), indices = {
        @Index("sessionId"),
        @Index(value = "regressionUuid", unique = true),
        @Index("regressionType"),
        @Index("severity")
})
public class UpdateRegression {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long sessionId;

    // === Identity ===
    public String regressionUuid;
    public long detectedAt;

    // === Classification ===
    // new_crash, performance_drop, battery_regression, thermal_regression,
    // new_anr, stability_issue
    public String regressionType;
    public String component;
    // low, medium, high, critical
    public String severity;

    // === Description ===
    public String title;
    public String description;
    public String technicalDetails; // JSON

    // === Metrics ===
    public String metricName;
    public Double preUpdateBaseline;
    public Double postUpdateValue;
    public Double deltaPercent;
    public String unit;

    // === Evidence ===
    public Long firstOccurrence;
    public Integer occurrenceCount;
    public String evidenceJson;

    // === Impact ===
    public Double impactScore; // 0-100
    public boolean userNoticeable;

    // === Status ===
    public boolean acknowledged;
    public boolean userReported;

    public UpdateRegression() {
    }

    /**
     * Create a new regression detected after update.
     */
    public static UpdateRegression create(long sessionId, String uuid, String type,
            String component, String severity,
            String title, String description) {
        UpdateRegression regression = new UpdateRegression();
        regression.sessionId = sessionId;
        regression.regressionUuid = uuid;
        regression.detectedAt = System.currentTimeMillis();
        regression.regressionType = type;
        regression.component = component;
        regression.severity = severity;
        regression.title = title;
        regression.description = description;
        regression.firstOccurrence = System.currentTimeMillis();
        regression.occurrenceCount = 1;
        regression.acknowledged = false;
        regression.userReported = false;
        return regression;
    }

    /**
     * Set metric comparison data.
     */
    public void setMetrics(String metricName, Double baseline, Double current, String unit) {
        this.metricName = metricName;
        this.preUpdateBaseline = baseline;
        this.postUpdateValue = current;
        this.unit = unit;

        if (baseline != null && current != null && baseline != 0) {
            this.deltaPercent = ((current - baseline) / Math.abs(baseline)) * 100;
        }
    }

    /**
     * Set occurrence data.
     */
    public void setOccurrences(long first, int count) {
        this.firstOccurrence = first;
        this.occurrenceCount = count;
    }

    /**
     * Calculate impact score based on severity and metrics.
     */
    public void calculateImpact() {
        double baseScore = 50.0;

        // Adjust by severity
        switch (severity != null ? severity : "medium") {
            case "critical":
                baseScore = 90;
                break;
            case "high":
                baseScore = 75;
                break;
            case "medium":
                baseScore = 50;
                break;
            case "low":
                baseScore = 25;
                break;
        }

        // Adjust by occurrence count
        if (occurrenceCount != null && occurrenceCount > 5) {
            baseScore = Math.min(100, baseScore + 10);
        }

        // Adjust by delta
        if (deltaPercent != null && Math.abs(deltaPercent) > 50) {
            baseScore = Math.min(100, baseScore + 10);
        }

        this.impactScore = baseScore;
        this.userNoticeable = baseScore >= 50 || (occurrenceCount != null && occurrenceCount > 3);
    }

    /**
     * Set evidence JSON.
     */
    public void setEvidence(String json) {
        this.evidenceJson = json;
    }
}
