package com.example.hexnil.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Stores detected anomalies from AI analysis with detailed descriptions.
 * Each anomaly is a separate timestamped record for historical tracking.
 */
@Entity(tableName = "anomaly_records")
public class AnomalyRecord {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    // Timestamp when anomaly was detected
    public long detectedAt;
    
    // Anomaly classification
    public String anomalyType;           // "app_drain", "thermal", "battery_health", "charging", "system", "usage_pattern"
    public String severity;              // "critical", "high", "medium", "low", "info"
    public float confidenceScore;        // 0-100%, how confident AI is about this anomaly
    
    // Source information
    public String sourceApp;             // Package name if app-related
    public String sourceAppName;         // Display name of app
    public String sourceComponent;       // Component that detected anomaly
    
    // Detailed description
    public String title;                 // Short title: "Excessive Battery Drain Detected"
    public String description;           // Detailed description of the anomaly
    public String technicalDetails;      // JSON with technical data
    
    // Metrics that triggered the anomaly
    public float expectedValue;          // What was expected
    public float actualValue;            // What was actually observed
    public float deviationPercent;       // How much it deviated (%)
    public String metricName;            // Name of the metric (e.g., "drainPerHour", "temperature")
    public String metricUnit;            // Unit (e.g., "%/hr", "°C", "mA")
    
    // Context
    public int batteryLevel;             // Battery level when detected
    public boolean wasCharging;          // Was device charging
    public boolean screenOn;             // Was screen on
    public String deviceState;           // "active", "idle", "doze", "charging"
    
    // Time period this anomaly covers
    public long periodStart;
    public long periodEnd;
    public int dataPointsAnalyzed;       // How many data points were analyzed
    
    // Recommendations
    public String recommendation;        // What user/engineer should do
    public String rootCause;             // Suspected root cause
    public String impact;                // Impact description
    
    // Status tracking
    public boolean acknowledged;         // User acknowledged this anomaly
    public boolean resolved;             // Issue has been resolved
    public long resolvedAt;              // When it was resolved
    public String resolution;            // How it was resolved
    
    // Related anomalies
    public long relatedAnomalyId;        // If this is part of a pattern
    public int occurrenceCount;          // How many times this has occurred
    public boolean isRecurring;          // Is this a recurring issue

    // ML Infrastructure
    public String deviceFingerprint;     // Device SKU fingerprint for fleet analysis
    public Long mlPredictionId;          // FK to ml_predictions if ML-detected
    @ColumnInfo(defaultValue = "heuristic")
    public String detectionMethod;       // "heuristic", "ml_model", "hybrid"
    public String modelVersion;          // Version of ML model that detected this

    public AnomalyRecord() {
        this.detectedAt = System.currentTimeMillis();
        this.acknowledged = false;
        this.resolved = false;
        this.occurrenceCount = 1;
        // Default screenOn to false - safe default when device state unknown
        this.screenOn = false;
    }
    
    /**
     * Create an app drain anomaly
     */
    public static AnomalyRecord appDrainAnomaly(String packageName, String appName, 
            float expectedDrain, float actualDrain, String description) {
        AnomalyRecord record = new AnomalyRecord();
        record.anomalyType = "app_drain";
        record.sourceApp = packageName;
        record.sourceAppName = appName;
        record.title = "Abnormal App Battery Drain: " + appName;
        record.description = description;
        record.expectedValue = expectedDrain;
        record.actualValue = actualDrain;
        record.deviationPercent = expectedDrain > 0 ? ((actualDrain - expectedDrain) / expectedDrain) * 100 : 100;
        record.metricName = "drainPerHour";
        record.metricUnit = "%/hr";
        record.severity = actualDrain > 25 ? "critical" : actualDrain > 15 ? "high" : "medium";
        record.confidenceScore = 85f;
        return record;
    }
    
    /**
     * Create a thermal anomaly
     */
    public static AnomalyRecord thermalAnomaly(float maxTemp, float avgTemp, String description) {
        AnomalyRecord record = new AnomalyRecord();
        record.anomalyType = "thermal";
        record.title = "High Temperature Detected";
        record.description = description;
        record.expectedValue = 35f; // Normal temp
        record.actualValue = maxTemp;
        record.deviationPercent = ((maxTemp - 35f) / 35f) * 100;
        record.metricName = "temperature";
        record.metricUnit = "°C";
        record.severity = maxTemp > 45 ? "critical" : maxTemp > 42 ? "high" : "medium";
        record.confidenceScore = 95f;
        return record;
    }
    
    /**
     * Create a battery health anomaly
     */
    public static AnomalyRecord healthAnomaly(float healthPercent, float degradationRate, String description) {
        AnomalyRecord record = new AnomalyRecord();
        record.anomalyType = "battery_health";
        record.title = "Battery Health Degradation";
        record.description = description;
        record.expectedValue = 100f;
        record.actualValue = healthPercent;
        record.deviationPercent = 100 - healthPercent;
        record.metricName = "healthPercentage";
        record.metricUnit = "%";
        record.severity = healthPercent < 70 ? "critical" : healthPercent < 80 ? "high" : "medium";
        record.confidenceScore = 90f;
        return record;
    }
    
    /**
     * Create a charging anomaly
     */
    public static AnomalyRecord chargingAnomaly(String issue, float efficiency, String description) {
        AnomalyRecord record = new AnomalyRecord();
        record.anomalyType = "charging";
        record.title = "Charging Anomaly: " + issue;
        record.description = description;
        record.expectedValue = 90f; // Expected efficiency
        record.actualValue = efficiency;
        record.deviationPercent = 90f - efficiency;
        record.metricName = "chargingEfficiency";
        record.metricUnit = "%";
        record.severity = efficiency < 70 ? "high" : "medium";
        record.confidenceScore = 80f;
        return record;
    }
    
    /**
     * Create a usage pattern anomaly
     */
    public static AnomalyRecord usagePatternAnomaly(String pattern, String description, float score) {
        AnomalyRecord record = new AnomalyRecord();
        record.anomalyType = "usage_pattern";
        record.title = "Unusual Usage Pattern: " + pattern;
        record.description = description;
        record.metricName = "usageScore";
        record.actualValue = score;
        record.severity = score > 75 ? "high" : score > 50 ? "medium" : "low";
        record.confidenceScore = 70f;
        return record;
    }
    
    @Override
    public String toString() {
        return "Anomaly{" +
                "type=" + anomalyType +
                ", severity=" + severity +
                ", title='" + title + '\'' +
                ", app=" + sourceAppName +
                ", deviation=" + String.format("%.1f%%", deviationPercent) +
                ", confidence=" + String.format("%.0f%%", confidenceScore) +
                '}';
    }
}
