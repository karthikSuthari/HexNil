package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Aggregated thermal heat buckets by temp range and time-of-day.
 * 
 * OEM-Grade heat map with:
 * - Temperature bucket distribution (<35, 35-40, 40-45, >45)
 * - Time-of-day patterns (hourly blocks)
 * - Multi-factor correlation tracking
 * - Battery impact assessment
 */
@Entity(tableName = "thermal_heat_buckets",
        indices = {
            @Index(value = "startTime"),
            @Index(value = "tempBucket"),
            @Index(value = "hourBlock")
        })
public class ThermalHeatBucket {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long startTime;
    public long endTime;
    public String tempBucket;      // <35, 35-40, 40-45, >45
    public int hourBlock;          // 0-23 or 3-hour block index

    public int sampleCount;        // number of samples
    public long durationMs;        // cumulative duration in this bucket

    // =====================================================================
    // Temperature statistics
    // =====================================================================
    public float maxTemp;
    public float avgTemp;
    public float minTemp;          // NEW: Minimum temperature in bucket
    public float tempVariance;     // NEW: Temperature variance
    
    // =====================================================================
    // Correlations - Charging
    // =====================================================================
    public int chargingCount;      // samples taken while charging
    public float chargingPercent;  // NEW: % of samples while charging
    public float avgTempCharging;  // NEW: Average temp while charging
    public float avgTempNotCharging;// NEW: Average temp while not charging
    public float avgChargeCurrent; // NEW: Average charge current
    public int fastChargingCount;  // NEW: Samples during fast charging
    
    // =====================================================================
    // Correlations - CPU/GPU
    // =====================================================================
    public int highCpuCount;       // NEW: Samples with CPU >60%
    public int highGpuCount;       // NEW: Samples with GPU >60%
    public float avgCpuLoad;       // NEW: Average CPU load
    public float avgGpuLoad;       // NEW: Average GPU load
    public float avgTempHighCpu;   // NEW: Avg temp when CPU high
    public float avgTempHighGpu;   // NEW: Avg temp when GPU high
    
    // =====================================================================
    // Correlations - App
    // =====================================================================
    public String topForegroundApp;// last seen or top app
    public int screenOnCount;      // NEW: Samples with screen on
    public float screenOnPercent;  // NEW: % of time screen was on
    public String appTempContributionJson; // NEW: JSON map of app -> avg temp
    
    // =====================================================================
    // Context
    // =====================================================================
    public int gamingCount;        // NEW: Samples during gaming
    public int cameraCount;        // NEW: Samples during camera use
    public int navigationCount;    // NEW: Samples during navigation
    public String networkType;     // NEW: Dominant network type
    
    // =====================================================================
    // Thermal events
    // =====================================================================
    public int spikeCount;         // NEW: Number of spikes in this bucket
    public int sustainedHeatCount; // NEW: Sustained heat events
    public int warningEventCount;  // NEW: Warning-level events
    public int criticalEventCount; // NEW: Critical-level events
    
    // =====================================================================
    // Battery impact
    // =====================================================================
    public float avgBatteryLevel;  // NEW: Average battery level
    public float batteryDrain;     // NEW: Total drain during bucket (%)
    public float degradationRisk;  // NEW: Estimated degradation risk 0-1
    
    // =====================================================================
    // OEM Metrics
    // =====================================================================
    public float thermalScore;     // NEW: 0-100 thermal health score
    public boolean exceedsOEMLimit;// NEW: Exceeded OEM thermal limit
    public String oemComplianceStatus; // NEW: "pass", "warning", "fail"
    
    public ThermalHeatBucket() {
        this.minTemp = Float.MAX_VALUE;
        this.oemComplianceStatus = "pass";
    }
    
    /**
     * Get the thermal risk level for this bucket.
     */
    public String getThermalRiskLevel() {
        if (">45".equals(tempBucket)) return "critical";
        if ("40-45".equals(tempBucket)) return "warning";
        if ("35-40".equals(tempBucket)) return "elevated";
        return "normal";
    }
    
    /**
     * Check if this bucket indicates thermal stress.
     */
    public boolean indicatesThermalStress() {
        return "40-45".equals(tempBucket) || ">45".equals(tempBucket) ||
               criticalEventCount > 0 || degradationRisk > 0.3f;
    }
    
    /**
     * Calculate the charging heat contribution.
     */
    public float getChargingHeatContribution() {
        if (sampleCount == 0 || chargingCount == 0) return 0;
        float tempDiff = avgTempCharging - avgTempNotCharging;
        return Math.max(0, tempDiff);
    }
    
    /**
     * Calculate CPU heat contribution.
     */
    public float getCpuHeatContribution() {
        if (sampleCount == 0 || highCpuCount == 0) return 0;
        float baseline = avgTemp;
        return Math.max(0, avgTempHighCpu - baseline);
    }
}
