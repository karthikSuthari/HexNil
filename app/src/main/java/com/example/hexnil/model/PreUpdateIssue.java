package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Specific bugs/anomalies detected before the update.
 * Converted from anomaly_records when baseline is locked.
 */
@Entity(tableName = "pre_update_issues", foreignKeys = @ForeignKey(entity = UpdateSession.class, parentColumns = "id", childColumns = "sessionId", onDelete = ForeignKey.CASCADE), indices = {
        @Index("sessionId"),
        @Index(value = "issueUuid", unique = true),
        @Index("issueType"),
        @Index("severity")
})
public class PreUpdateIssue {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long sessionId;

    // === Issue Identity ===
    public String issueUuid;
    public long detectedAt;

    // === Classification ===
    // battery_drain, thermal, crash, anr, performance, regression, refresh_rate
    public String issueType;
    // system, app, thermal_manager, camera, bluetooth, wifi, etc.
    public String component;
    // low, medium, high, critical
    public String severity;

    // === Description ===
    public String issueTitle;
    public String issueDescription;
    public String technicalDetails; // JSON with raw data

    // === Metrics ===
    public String metricName;
    public Double baselineValue; // What it should be
    public Double actualValue; // What it actually is
    public Double deviationPercent;
    public String unit;

    // === Frequency ===
    public Integer occurrenceCount;
    public Long firstObserved;
    public Long lastObserved;

    // === Impact ===
    public Double impactScore; // 0-100 how bad this issue is
    public boolean affectsUserExperience;

    // === Evidence ===
    public String evidenceTableName; // Which table has the proof
    public String evidenceQuery; // SQL to retrieve evidence
    public Integer evidenceCount;

    // === Source App (if applicable) ===
    public String sourcePackageName;
    public String sourceAppLabel;

    public PreUpdateIssue() {
    }

    /**
     * Create from an anomaly record.
     */
    public static PreUpdateIssue fromAnomaly(long sessionId, String issueUuid,
            String type, String component, String severity,
            String title, String description) {
        PreUpdateIssue issue = new PreUpdateIssue();
        issue.sessionId = sessionId;
        issue.issueUuid = issueUuid;
        issue.detectedAt = System.currentTimeMillis();
        issue.issueType = type;
        issue.component = component;
        issue.severity = severity;
        issue.issueTitle = title;
        issue.issueDescription = description;
        issue.occurrenceCount = 1;
        issue.firstObserved = System.currentTimeMillis();
        issue.lastObserved = System.currentTimeMillis();
        issue.affectsUserExperience = true;
        return issue;
    }

    /**
     * Set metric details.
     */
    public void setMetrics(String metricName, Double baseline, Double actual, String unit) {
        this.metricName = metricName;
        this.baselineValue = baseline;
        this.actualValue = actual;
        this.unit = unit;
        if (baseline != null && actual != null && baseline != 0) {
            this.deviationPercent = ((actual - baseline) / Math.abs(baseline)) * 100;
        }
    }

    /**
     * Set frequency data.
     */
    public void setFrequency(int occurrences, long firstSeen, long lastSeen) {
        this.occurrenceCount = occurrences;
        this.firstObserved = firstSeen;
        this.lastObserved = lastSeen;
    }

    /**
     * Set evidence information.
     */
    public void setEvidence(String tableName, String query, int count) {
        this.evidenceTableName = tableName;
        this.evidenceQuery = query;
        this.evidenceCount = count;
    }

    /**
     * Set source app if this issue is app-related.
     */
    public void setSourceApp(String packageName, String appLabel) {
        this.sourcePackageName = packageName;
        this.sourceAppLabel = appLabel;
    }
}
