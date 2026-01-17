package com.example.hexnil.intelligence.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * App Thermal Impact - tracks thermal contribution of individual apps.
 * 
 * Part of the isolated App Intelligence module.
 * Does NOT reference any tables in the core HexNil database.
 */
@Entity(tableName = "ai_app_thermal_impact", indices = {
        @Index(value = { "package_name" }),
        @Index(value = { "period_start" }),
        @Index(value = { "max_temperature_c" }),
        @Index(value = { "session_id" })
})
public class AppThermalImpact {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // Correlation (no FKs!)
    @ColumnInfo(name = "package_name")
    public String packageName;

    @ColumnInfo(name = "session_id")
    public String sessionId;

    // Time Window
    @ColumnInfo(name = "period_start")
    public long periodStart;

    @ColumnInfo(name = "period_end")
    public long periodEnd;

    @ColumnInfo(name = "duration_ms")
    public long durationMs;

    // Temperature Metrics
    @ColumnInfo(name = "avg_temperature_c")
    public float avgTemperatureC;

    @ColumnInfo(name = "max_temperature_c")
    public float maxTemperatureC;

    @ColumnInfo(name = "min_temperature_c")
    public float minTemperatureC = 100f; // Start high for min tracking

    @ColumnInfo(name = "temperature_rise_c")
    public float temperatureRiseC; // Rise during app usage

    // Load Context
    @ColumnInfo(name = "avg_cpu_load")
    public int avgCpuLoad;

    @ColumnInfo(name = "max_cpu_load")
    public int maxCpuLoad;

    @ColumnInfo(name = "avg_gpu_load")
    public int avgGpuLoad;

    // Thermal Events During Session
    @ColumnInfo(name = "thermal_spike_count")
    public int thermalSpikeCount;

    @ColumnInfo(name = "sustained_heat_duration_ms")
    public long sustainedHeatDurationMs;

    @ColumnInfo(name = "crossed_warning_threshold")
    public boolean crossedWarningThreshold;

    @ColumnInfo(name = "crossed_critical_threshold")
    public boolean crossedCriticalThreshold;

    // Analysis
    @ColumnInfo(name = "thermal_impact_score")
    public float thermalImpactScore; // 0-100, higher = worse

    @ColumnInfo(name = "thermal_risk_level")
    public String thermalRiskLevel; // "low", "moderate", "high", "critical"

    @ColumnInfo(name = "primary_heat_source")
    public String primaryHeatSource; // "cpu", "gpu", "network", "combined"

    // Audit
    @ColumnInfo(name = "created_at")
    public long createdAt;

    // Thresholds for classification
    public static final float WARNING_TEMP = 40.0f;
    public static final float CRITICAL_TEMP = 45.0f;

    public AppThermalImpact() {
        this.createdAt = System.currentTimeMillis();
        this.minTemperatureC = 100f;
    }

    /**
     * Create a thermal impact record for an app session.
     */
    public static AppThermalImpact create(String packageName, String sessionId,
            long periodStart, long periodEnd) {
        AppThermalImpact impact = new AppThermalImpact();
        impact.packageName = packageName;
        impact.sessionId = sessionId;
        impact.periodStart = periodStart;
        impact.periodEnd = periodEnd;
        impact.durationMs = periodEnd - periodStart;
        return impact;
    }

    /**
     * Update temperature metrics from a sample.
     */
    public void updateTemperature(float tempC) {
        this.maxTemperatureC = Math.max(this.maxTemperatureC, tempC);
        this.minTemperatureC = Math.min(this.minTemperatureC, tempC);

        if (tempC >= WARNING_TEMP) {
            this.crossedWarningThreshold = true;
        }
        if (tempC >= CRITICAL_TEMP) {
            this.crossedCriticalThreshold = true;
        }
    }

    /**
     * Set final temperature statistics.
     */
    public void setTemperatureStats(float avgTemp, float maxTemp, float minTemp, float rise) {
        this.avgTemperatureC = avgTemp;
        this.maxTemperatureC = maxTemp;
        this.minTemperatureC = minTemp;
        this.temperatureRiseC = rise;
    }

    /**
     * Set CPU/GPU load metrics.
     */
    public void setLoadMetrics(int avgCpu, int maxCpu, int avgGpu) {
        this.avgCpuLoad = avgCpu;
        this.maxCpuLoad = maxCpu;
        this.avgGpuLoad = avgGpu;

        // Determine primary heat source
        if (avgGpu > avgCpu && avgGpu > 50) {
            this.primaryHeatSource = "gpu";
        } else if (avgCpu > 50) {
            this.primaryHeatSource = "cpu";
        } else if (avgCpu > 30 && avgGpu > 30) {
            this.primaryHeatSource = "combined";
        } else {
            this.primaryHeatSource = "unknown";
        }
    }

    /**
     * Calculate thermal impact score and risk level.
     */
    public void calculateThermalScore() {
        float score = 0;

        // Temperature contribution (0-50 points)
        if (maxTemperatureC > 35) {
            score += Math.min(50, (maxTemperatureC - 35) * 5);
        }

        // Temperature rise contribution (0-20 points)
        score += Math.min(20, temperatureRiseC * 4);

        // Spike contribution (0-15 points)
        score += Math.min(15, thermalSpikeCount * 5);

        // Duration at high temp (0-15 points)
        float sustainedMinutes = sustainedHeatDurationMs / 60000.0f;
        score += Math.min(15, sustainedMinutes * 3);

        this.thermalImpactScore = Math.min(100, score);

        // Set risk level
        if (this.thermalImpactScore >= 75 || crossedCriticalThreshold) {
            this.thermalRiskLevel = "critical";
        } else if (this.thermalImpactScore >= 50 || crossedWarningThreshold) {
            this.thermalRiskLevel = "high";
        } else if (this.thermalImpactScore >= 25) {
            this.thermalRiskLevel = "moderate";
        } else {
            this.thermalRiskLevel = "low";
        }
    }

    /**
     * Check if this session had significant thermal impact.
     */
    public boolean hasSignificantImpact() {
        return maxTemperatureC > 40 || thermalSpikeCount > 0 ||
                "high".equals(thermalRiskLevel) || "critical".equals(thermalRiskLevel);
    }

    @Override
    public String toString() {
        return "AppThermalImpact{" +
                "packageName='" + packageName + '\'' +
                ", maxTemp=" + String.format("%.1f°C", maxTemperatureC) +
                ", rise=" + String.format("%.1f°C", temperatureRiseC) +
                ", score=" + String.format("%.0f", thermalImpactScore) +
                ", risk='" + thermalRiskLevel + '\'' +
                '}';
    }
}
