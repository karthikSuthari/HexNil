package com.example.hexnil.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Stores per-device statistical baselines for key health metrics.
 * Each row captures the distribution (p50/p95/p99/mean/stdDev) for one
 * metric on one device fingerprint, enabling threshold-based anomaly
 * detection that adapts to the specific hardware profile.
 */
@Entity(tableName = "device_baseline_thresholds", indices = {
        @Index(value = {"deviceFingerprint", "metricName"}, unique = true),
        @Index("deviceFingerprint")
})
public class DeviceBaselineThreshold {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // === Device Identity ===
    @NonNull
    public String deviceFingerprint;

    // === Metric Identity ===
    // e.g. "idleDrainRate", "activeDrainRate", "batteryTemp", "cpuTemp",
    //      "coldLaunchMs", "jankPercent", "crashesPerDay"
    @NonNull
    public String metricName;

    // === Distribution Thresholds ===
    public Float p50;       // Median — typical value
    public Float p95;       // 95th percentile — warning threshold
    public Float p99;       // 99th percentile — critical threshold

    // === Summary Statistics ===
    public Float mean;
    public Float stdDev;

    // === Sample Metadata ===
    public int sampleSize;          // How many devices contributed
    public float confidenceLevel;   // 0-1, statistical confidence

    // === Timestamps ===
    public long lastUpdated;

    public DeviceBaselineThreshold() {
        this.sampleSize = 0;
        this.confidenceLevel = 0.0f;
    }

    /**
     * Create a baseline threshold entry for a device metric.
     */
    public static DeviceBaselineThreshold create(String fingerprint, String metricName,
            float p50, float p95, float p99,
            float mean, float stdDev, int sampleSize) {
        DeviceBaselineThreshold threshold = new DeviceBaselineThreshold();
        threshold.deviceFingerprint = fingerprint;
        threshold.metricName = metricName;
        threshold.p50 = p50;
        threshold.p95 = p95;
        threshold.p99 = p99;
        threshold.mean = mean;
        threshold.stdDev = stdDev;
        threshold.sampleSize = sampleSize;
        threshold.confidenceLevel = Math.min(1.0f, sampleSize / 100.0f);
        threshold.lastUpdated = System.currentTimeMillis();
        return threshold;
    }

    @Override
    public String toString() {
        return "DeviceBaselineThreshold{" +
                "fingerprint=" + deviceFingerprint +
                ", metric=" + metricName +
                ", p50=" + p50 +
                ", p95=" + p95 +
                ", p99=" + p99 +
                ", mean=" + mean +
                ", stdDev=" + stdDev +
                ", samples=" + sampleSize +
                ", confidence=" + String.format("%.2f", confidenceLevel) +
                '}';
    }
}
