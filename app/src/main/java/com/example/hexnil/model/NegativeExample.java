package com.example.hexnil.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Negative example entity for ML training pipeline.
 *
 * Stores known-normal telemetry snapshots captured during legitimate high-drain
 * conditions (gaming, fast-charging, benchmarks, navigation, etc.).
 * These records teach the anomaly-detection model what normal behaviour looks
 * like under heavy load so it avoids false-positive alerts.
 *
 * Condition types:
 *   "high_cpu_sustained", "thermal_spike", "fast_charging",
 *   "gaming_session", "benchmark", "low_battery", "navigation",
 *   "camera_recording", "video_call"
 */
@Entity(tableName = "negative_examples", indices = {
        @Index(value = {"conditionType"}),
        @Index(value = {"deviceFingerprint"}),
        @Index(value = {"timestamp"}),
        @Index(value = {"foregroundApp"})
})
public class NegativeExample {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // ── Classification ──────────────────────────────────────────────────────────

    /** Condition that makes this snapshot normal. NOT NULL. */
    @ColumnInfo(defaultValue = "")
    public String conditionType;             // "high_cpu_sustained", "thermal_spike", etc.

    /** Unique device identifier (model + build fingerprint). */
    public String deviceFingerprint;

    /** Epoch millis when the snapshot was captured. */
    public long timestamp;

    /** Duration of the observed condition in milliseconds. */
    public long durationMs;

    // ── Telemetry ───────────────────────────────────────────────────────────────

    /** Full telemetry snapshot serialised as JSON. */
    public String metricsJson;

    // ── Explanation ─────────────────────────────────────────────────────────────

    /** Human-readable reason why this is considered normal. NOT NULL. */
    @ColumnInfo(defaultValue = "")
    public String expectedBehavior;

    /** Legitimate root-cause activity that produced the drain / thermal spike. */
    public String normalCauseRootCause;

    /** Detected activity label: "benchmark_app", "gaming", "video_recording", etc. */
    public String detectedActivity;

    // ── Foreground app context ──────────────────────────────────────────────────

    /** Package name of the foreground app during capture. */
    public String foregroundApp;

    /** User-visible display name of the foreground app. */
    public String foregroundAppName;

    // ── Device state at capture ─────────────────────────────────────────────────

    /** Battery level (0-100) at the time of capture. */
    public int batteryLevel;

    /** Device temperature in °C. */
    public float temperature;

    /** CPU usage percentage (0-100). */
    public int cpuUsage;

    /** Battery drain rate in %/hr during this period. */
    public float drainRate;

    // ── Labelling & training ────────────────────────────────────────────────────

    /** Boolean-like flag: 1 = labelled as normal behaviour. */
    @ColumnInfo(defaultValue = "1")
    public int labeledAsNormal = 1;

    /** Who / what applied the label: "system_auto", "engineer", "user". */
    public String labeledBy;

    /** Boolean-like flag: 1 = already consumed by a training run. */
    @ColumnInfo(defaultValue = "0")
    public int usedInTraining = 0;

    /** Version tag of the training set this example was included in. Nullable. */
    public String trainingSetVersion;

    // ── Constructors ────────────────────────────────────────────────────────────

    public NegativeExample() {
        this.timestamp = System.currentTimeMillis();
        this.labeledAsNormal = 1;
        this.usedInTraining = 0;
    }

    // ── Factory ─────────────────────────────────────────────────────────────────

    /**
     * Convenience factory for the most common creation path.
     *
     * @param conditionType     one of the known condition-type constants
     * @param deviceFingerprint device model + build fingerprint
     * @param expectedBehavior  why this snapshot is normal
     * @param activity          detected activity label
     * @param foregroundApp     package name of the foreground app
     * @return a new {@link NegativeExample} pre-populated with defaults
     */
    public static NegativeExample create(String conditionType,
                                         String deviceFingerprint,
                                         String expectedBehavior,
                                         String activity,
                                         String foregroundApp) {
        NegativeExample example = new NegativeExample();
        example.conditionType = conditionType;
        example.deviceFingerprint = deviceFingerprint;
        example.expectedBehavior = expectedBehavior;
        example.detectedActivity = activity;
        example.foregroundApp = foregroundApp;
        example.labeledBy = "system_auto";
        return example;
    }

    @Override
    public String toString() {
        return "NegativeExample{" +
                "id=" + id +
                ", conditionType='" + conditionType + '\'' +
                ", detectedActivity='" + detectedActivity + '\'' +
                ", foregroundApp='" + foregroundApp + '\'' +
                ", batteryLevel=" + batteryLevel +
                ", temperature=" + temperature +
                ", cpuUsage=" + cpuUsage +
                ", drainRate=" + drainRate +
                ", labeledAsNormal=" + labeledAsNormal +
                ", labeledBy='" + labeledBy + '\'' +
                '}';
    }
}
