package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Stores refresh rate anomalies with deterministic cause classification.
 * 
 * CRITICAL: Causes are classified using evidence-based rules only.
 * NO AI guessing. Each anomaly must have:
 * - causeCategory: The deterministic classification
 * - causeExplanation: Human-readable explanation of why
 * - evidenceMetricsJson: The metrics that led to this classification
 * 
 * Anomaly Types:
 * - REFRESH_STUCK_HIGH: Device stuck at max Hz without user activity
 * - HIGH_REFRESH_IDLE: High refresh rate during idle/home screen
 * - UNEXPECTED_REFRESH_SPIKE: Sudden jump to max Hz during low activity
 * - THERMAL_FORCED_REFRESH_DROP: Refresh dropped due to thermal throttling
 * - APP_FORCED_HIGH_REFRESH: App forcing high refresh unnecessarily
 * - SYSTEM_REFRESH_BUG: System-level refresh rate mismanagement
 */
@Entity(tableName = "refresh_rate_anomalies", indices = {
        @Index("linkedSessionId"),
        @Index("detectedAt"),
        @Index("anomalyType"),
        @Index("severity")
})
public class RefreshRateAnomaly {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // =========================================================================
    // Link to Session
    // =========================================================================

    /** ID of the RefreshRateSession this anomaly belongs to */
    public long linkedSessionId;

    /** Package name of the app (or "launcher" for home) */
    public String packageName;

    /** Display name of the app for easier identification */
    public String appName;

    /** Session type: "APP" or "HOME" */
    public String sessionType;

    // =========================================================================
    // Detection Info
    // =========================================================================

    /** Timestamp when anomaly was detected */
    public long detectedAt;

    /** Type of anomaly (see class documentation) */
    public String anomalyType;

    /** Severity: "critical", "high", "medium", "low" */
    public String severity;

    /** Confidence score 0-100 based on evidence strength */
    public float confidenceScore;

    // =========================================================================
    // Deterministic Cause Classification
    // =========================================================================

    /**
     * Cause category - MUST be one of:
     * - APP_FORCED_HIGH_REFRESH
     * - THERMAL_THROTTLING
     * - IDLE_REFRESH_WASTE
     * - SYSTEM_REFRESH_BUG
     * - USER_ACTIVITY (rare)
     */
    public String causeCategory;

    /** Human-readable explanation of why this cause was determined */
    public String causeExplanation;

    /**
     * JSON object containing the evidence metrics that led to classification.
     * Example:
     * {"refreshHz":120,"cpuUsage":5,"brightness":20,"temperature":32,"inputEvents":0}
     */
    public String evidenceMetricsJson;

    // =========================================================================
    // Context at Detection Time
    // =========================================================================

    /** Current refresh rate in Hz */
    public float refreshRateHz;

    /** CPU usage at detection time (0-100%) */
    public float cpuUsage;

    /** GPU usage at detection time (0-100%) */
    public float gpuUsage;

    /** Device temperature in Celsius */
    public float temperature;

    /** Screen brightness (0-255) */
    public float brightness;

    /** Battery current in mA */
    public float batteryCurrent;

    /** Was device charging at detection */
    public boolean wasCharging;

    /** Was screen on at detection */
    public boolean screenOn;

    // =========================================================================
    // Constructors & Factory Methods
    // =========================================================================

    public RefreshRateAnomaly() {
        this.detectedAt = System.currentTimeMillis();
        this.screenOn = true;
        // Default values for context fields that must not be NULL
        this.temperature = 0f; // 0 when not measured
        this.batteryCurrent = 0f; // 0 when not measured
    }

    /**
     * Create REFRESH_STUCK_HIGH anomaly
     * Detection: 120Hz sustained for >5min without user input
     */
    public static RefreshRateAnomaly refreshStuckHigh(long sessionId, String packageName, String appName,
            String sessionType, float hz, float cpu, float temp, String evidence) {
        RefreshRateAnomaly anomaly = new RefreshRateAnomaly();
        anomaly.linkedSessionId = sessionId;
        anomaly.packageName = packageName;
        anomaly.appName = appName;
        anomaly.sessionType = sessionType;
        anomaly.anomalyType = "REFRESH_STUCK_HIGH";
        anomaly.severity = "high";
        anomaly.confidenceScore = 90f;
        anomaly.refreshRateHz = hz;
        anomaly.cpuUsage = cpu;
        anomaly.temperature = temp;
        anomaly.evidenceMetricsJson = evidence;

        // Determine cause
        if (cpu < 20 && temp < 38) {
            anomaly.causeCategory = "APP_FORCED_HIGH_REFRESH";
            anomaly.causeExplanation = "App maintaining high refresh rate despite low CPU/GPU activity";
        } else {
            anomaly.causeCategory = "SYSTEM_REFRESH_BUG";
            anomaly.causeExplanation = "System failed to reduce refresh rate during extended inactivity";
        }
        return anomaly;
    }

    /**
     * Create HIGH_REFRESH_IDLE anomaly
     * Detection: High Hz on home screen with no touch events for >60s
     */
    public static RefreshRateAnomaly highRefreshIdle(long sessionId, float hz,
            long idleDurationMs, String evidence) {
        RefreshRateAnomaly anomaly = new RefreshRateAnomaly();
        anomaly.linkedSessionId = sessionId;
        anomaly.packageName = "launcher";
        anomaly.appName = "Home Screen";
        anomaly.sessionType = "HOME";
        anomaly.anomalyType = "HIGH_REFRESH_IDLE";
        anomaly.severity = idleDurationMs > 300000 ? "high" : "medium"; // >5min = high
        anomaly.confidenceScore = 95f;
        anomaly.refreshRateHz = hz;
        anomaly.causeCategory = "IDLE_REFRESH_WASTE";
        anomaly.causeExplanation = String.format(
                "Home screen at %.0fHz for %ds without user input - wasting battery",
                hz, idleDurationMs / 1000);
        anomaly.evidenceMetricsJson = evidence;
        return anomaly;
    }

    /**
     * Create THERMAL_FORCED_REFRESH_DROP anomaly
     * Detection: Refresh rate drops when temperature exceeds 42°C
     */
    public static RefreshRateAnomaly thermalForcedDrop(long sessionId, String packageName, String appName,
            String sessionType, float beforeHz, float afterHz, float temp, String evidence) {
        RefreshRateAnomaly anomaly = new RefreshRateAnomaly();
        anomaly.linkedSessionId = sessionId;
        anomaly.packageName = packageName;
        anomaly.appName = appName;
        anomaly.sessionType = sessionType;
        anomaly.anomalyType = "THERMAL_FORCED_REFRESH_DROP";
        anomaly.severity = temp > 45 ? "critical" : "high";
        anomaly.confidenceScore = 98f;
        anomaly.refreshRateHz = afterHz;
        anomaly.temperature = temp;
        anomaly.causeCategory = "THERMAL_THROTTLING";
        anomaly.causeExplanation = String.format(
                "Display throttled from %.0fHz to %.0fHz due to device temperature (%.1f°C)",
                beforeHz, afterHz, temp);
        anomaly.evidenceMetricsJson = evidence;
        return anomaly;
    }

    /**
     * Create APP_FORCED_HIGH_REFRESH anomaly
     * Detection: High Hz + low CPU + low brightness + low thermal
     */
    public static RefreshRateAnomaly appForcedHighRefresh(long sessionId, String packageName,
            String appName, float hz, float cpu, float brightness, String evidence) {
        RefreshRateAnomaly anomaly = new RefreshRateAnomaly();
        anomaly.linkedSessionId = sessionId;
        anomaly.packageName = packageName;
        anomaly.appName = appName;
        anomaly.sessionType = "APP";
        anomaly.anomalyType = "APP_FORCED_HIGH_REFRESH";
        anomaly.severity = "high";
        anomaly.confidenceScore = 85f;
        anomaly.refreshRateHz = hz;
        anomaly.cpuUsage = cpu;
        anomaly.brightness = brightness;
        anomaly.causeCategory = "APP_FORCED_HIGH_REFRESH";
        anomaly.causeExplanation = String.format(
                "%s requesting %.0fHz despite low activity (CPU: %.0f%%, brightness: %.0f)",
                appName, hz, cpu, brightness);
        anomaly.evidenceMetricsJson = evidence;
        return anomaly;
    }

    /**
     * Create SYSTEM_REFRESH_BUG anomaly
     * Detection: High refresh persists across multiple unrelated apps
     */
    public static RefreshRateAnomaly systemRefreshBug(long sessionId, float hz,
            int affectedAppCount, String evidence) {
        RefreshRateAnomaly anomaly = new RefreshRateAnomaly();
        anomaly.linkedSessionId = sessionId;
        anomaly.packageName = "system";
        anomaly.appName = "System";
        anomaly.sessionType = "APP";
        anomaly.anomalyType = "SYSTEM_REFRESH_BUG";
        anomaly.severity = "critical";
        anomaly.confidenceScore = 92f;
        anomaly.refreshRateHz = hz;
        anomaly.causeCategory = "SYSTEM_REFRESH_BUG";
        anomaly.causeExplanation = String.format(
                "System stuck at %.0fHz across %d different apps - possible display driver issue",
                hz, affectedAppCount);
        anomaly.evidenceMetricsJson = evidence;
        return anomaly;
    }

    /**
     * Create UNEXPECTED_REFRESH_SPIKE anomaly
     * Detection: Sudden jump to max Hz during low activity period
     */
    public static RefreshRateAnomaly unexpectedSpike(long sessionId, String packageName, String appName,
            String sessionType, float beforeHz, float afterHz, String evidence) {
        RefreshRateAnomaly anomaly = new RefreshRateAnomaly();
        anomaly.linkedSessionId = sessionId;
        anomaly.packageName = packageName;
        anomaly.appName = appName;
        anomaly.sessionType = sessionType;
        anomaly.anomalyType = "UNEXPECTED_REFRESH_SPIKE";
        anomaly.severity = "medium";
        anomaly.confidenceScore = 80f;
        anomaly.refreshRateHz = afterHz;
        anomaly.causeCategory = "APP_FORCED_HIGH_REFRESH";
        anomaly.causeExplanation = String.format(
                "Unexpected refresh spike from %.0fHz to %.0fHz without corresponding activity",
                beforeHz, afterHz);
        anomaly.evidenceMetricsJson = evidence;
        return anomaly;
    }

    @Override
    public String toString() {
        return "RefreshRateAnomaly{" +
                "type=" + anomalyType +
                ", severity=" + severity +
                ", cause=" + causeCategory +
                ", pkg=" + packageName +
                ", hz=" + String.format("%.0f", refreshRateHz) +
                ", confidence=" + String.format("%.0f%%", confidenceScore) +
                '}';
    }
}
