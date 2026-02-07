package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Frozen baseline state captured immediately when an update is detected.
 * Contains aggregated metrics and known issues from before the update.
 */
@Entity(tableName = "pre_update_snapshots", foreignKeys = @ForeignKey(entity = UpdateSession.class, parentColumns = "id", childColumns = "sessionId", onDelete = ForeignKey.CASCADE), indices = {
        @Index("sessionId"),
        @Index("capturedAt")
})
public class PreUpdateSnapshot {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long sessionId;
    public long capturedAt;

    // === System State ===
    public String buildFingerprint;
    public String androidVersion;
    public String securityPatch;
    public String modemVersion;

    // === Battery Baseline ===
    public Double avgDrainRatePerHour;
    public Double idleDrainRatePerHour;
    public Double batteryHealthPercent;
    public Integer cycleCount;
    public Double avgChargingTemp;

    // === Performance Baseline ===
    public Double avgColdLaunchMs;
    public Double avgJankPercent;
    public Double avgFps;
    public Double avgMemoryUsedPercent;

    // === Stability Baseline ===
    public Double crashesPerDay;
    public Double anrsPerDay;
    public Double thermalEventsPerDay;

    // === Thermal Baseline ===
    public Double avgOperatingTemp;
    public Double maxTempRecorded;
    public Long timeAbove40C; // milliseconds
    public Long timeAbove45C; // milliseconds

    // === Known Issues Summary ===
    public String knownIssuesJson; // JSON array of detected issues
    public Integer anomalyCountTotal;
    public Integer highSeverityAnomalies;

    // === Data Quality ===
    public Double dataQualityScore; // 0-1
    public Integer observationDays;
    public Integer snapshotCount;

    public PreUpdateSnapshot() {
    }

    /**
     * Create a snapshot from aggregated data.
     */
    public static PreUpdateSnapshot create(long sessionId, String buildFingerprint,
            String androidVersion, String securityPatch,
            String modemVersion) {
        PreUpdateSnapshot snapshot = new PreUpdateSnapshot();
        snapshot.sessionId = sessionId;
        snapshot.capturedAt = System.currentTimeMillis();
        snapshot.buildFingerprint = buildFingerprint;
        snapshot.androidVersion = androidVersion;
        snapshot.securityPatch = securityPatch;
        snapshot.modemVersion = modemVersion;
        return snapshot;
    }

    /**
     * Set battery baseline metrics.
     */
    public void setBatteryBaseline(Double avgDrain, Double idleDrain, Double health,
            Integer cycles, Double chargingTemp) {
        this.avgDrainRatePerHour = avgDrain;
        this.idleDrainRatePerHour = idleDrain;
        this.batteryHealthPercent = health;
        this.cycleCount = cycles;
        this.avgChargingTemp = chargingTemp;
    }

    /**
     * Set performance baseline metrics.
     */
    public void setPerformanceBaseline(Double coldLaunch, Double jank, Double fps, Double memory) {
        this.avgColdLaunchMs = coldLaunch;
        this.avgJankPercent = jank;
        this.avgFps = fps;
        this.avgMemoryUsedPercent = memory;
    }

    /**
     * Set stability baseline metrics.
     */
    public void setStabilityBaseline(Double crashes, Double anrs, Double thermalEvents) {
        this.crashesPerDay = crashes;
        this.anrsPerDay = anrs;
        this.thermalEventsPerDay = thermalEvents;
    }

    /**
     * Set thermal baseline metrics.
     */
    public void setThermalBaseline(Double avgTemp, Double maxTemp, Long above40, Long above45) {
        this.avgOperatingTemp = avgTemp;
        this.maxTempRecorded = maxTemp;
        this.timeAbove40C = above40;
        this.timeAbove45C = above45;
    }

    /**
     * Set known issues summary.
     */
    public void setIssuesSummary(String issuesJson, int totalAnomalies, int highSeverity) {
        this.knownIssuesJson = issuesJson;
        this.anomalyCountTotal = totalAnomalies;
        this.highSeverityAnomalies = highSeverity;
    }

    /**
     * Set data quality metrics.
     */
    public void setDataQuality(double qualityScore, int days, int snapshots) {
        this.dataQualityScore = qualityScore;
        this.observationDays = days;
        this.snapshotCount = snapshots;
    }
}
