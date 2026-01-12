package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Tracks battery health metrics over time for degradation analysis
 * Provides real health estimation beyond Android's basic health status
 */
@Entity(tableName = "battery_health")
public class BatteryHealth {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    
    // Capacity metrics
    public float designCapacityMah;      // Original manufacturer capacity
    public float currentCapacityMah;     // Current estimated capacity
    public float estimatedCapacityMah;   // Measured capacity from discharge window
    public float capacityFade;           // Percentage of capacity lost
    public float healthPercentage;       // Real health (currentCap/designCap * 100)
    public float capacityFadePercent;    // Duplicate explicit fade percent for reporting
    
    // Cycle estimation
    public int estimatedCycles;          // Estimated charge cycles
    public float cycleCount;             // Fractional cycle count
    public long totalChargeAccumulated;  // Total mAh charged over lifetime
    public long totalDischargeAccumulated; // Total mAh discharged
    
    // Wear indicators
    public float internalResistance;     // Estimated internal resistance (mOhm)
    public float voltageDropUnderLoad;   // Voltage drop indicator
    public float selfDischargeRate;      // %/day when idle
    public float chargingEfficiency;     // Energy in vs stored (%)
    
    // Degradation tracking
    public float degradationRate;        // % capacity loss per month
    public float fadePer100Cycles;       // % capacity loss per 100 cycles
    public float fadePerMonth;           // % capacity loss per month (smoothed)
    public String degradationTrend;      // "stable", "gradual", "accelerating"
    public float predictedEOL;           // Predicted months until 80% health
    
    // Anomaly detection
    public boolean hasAbnormalWear;      // Wear faster than expected
    public boolean hasAbnormalAging;     // Aging faster than baseline
    public boolean hasSwellingRisk;      // Indicators of swelling
    public boolean hasCalibrationIssue;  // SOC reporting issues
    public String abnormalityDetails;    // Description of issues
    public String agingNotes;            // Extra notes on aging outliers
    
    // Health score components
    public float capacityScore;          // Score based on capacity (0-100)
    public float resistanceScore;        // Score based on resistance (0-100)
    public float cycleScore;             // Score based on cycles (0-100)
    public float overallScore;           // Combined health score (0-100)
    
    // OEM data
    public String batteryModel;          // Battery model/part number
    public String manufacturer;          // Battery manufacturer
    public String manufactureDate;       // If available
    public String batchId;               // For batch tracking
    public float supplierAverageHealth;  // Aggregate health for supplier
    public float batchAverageHealth;     // Aggregate health for batch
    
    // Calibration data
    public long lastFullCharge;          // Timestamp of last 100% charge
    public long lastFullDischarge;       // Timestamp of last <5% discharge
    public boolean needsCalibration;     // Calibration recommended
    
    public BatteryHealth() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getHealthGrade() {
        if (healthPercentage >= 90) return "A";
        if (healthPercentage >= 80) return "B";
        if (healthPercentage >= 70) return "C";
        if (healthPercentage >= 60) return "D";
        return "F";
    }
    
    public boolean needsReplacement() {
        return healthPercentage < 70 || hasSwellingRisk || overallScore < 50;
    }
    
    public String getHealthStatus() {
        if (healthPercentage >= 85) return "Excellent";
        if (healthPercentage >= 75) return "Good";
        if (healthPercentage >= 65) return "Fair";
        if (healthPercentage >= 50) return "Poor";
        return "Critical";
    }
}
