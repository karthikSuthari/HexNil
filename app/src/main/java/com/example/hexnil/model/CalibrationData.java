package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Tracks battery calibration data and SOC accuracy
 * Detects percentage drift and sudden drop issues
 */
@Entity(tableName = "calibration_data")
public class CalibrationData {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    
    // Current state
    public int reportedLevel;            // What Android reports
    public float estimatedActualLevel;   // Our calculated estimate
    public float levelDrift;             // Difference between reported and actual
    public boolean driftExceeded;        // Drift crosses threshold
    public boolean socMismatch;          // Estimated vs reported mismatch
    
    // SOC accuracy tracking
    public float socAccuracy;            // How accurate is SOC (0-100%)
    public float avgDrift;               // Average drift over time
    public float maxDrift;               // Maximum drift observed
    public String driftDirection;        // "over_reporting", "under_reporting", "stable"
    
    // Sudden drop detection
    public boolean hasSuddenDrops;       // Sudden % drops detected
    public int suddenDropCount;          // Number of sudden drops
    public String suddenDrops;           // JSON of drop events
    public float avgDropSize;            // Average size of drops (%)
    public int worstDrop;                // Largest single drop (%)
    
    // Calibration points
    public long lastFullCharge;          // Last time at 100%
    public long lastLowBattery;          // Last time below 10%
    public long lastDeepDischarge;       // Last time below 5%
    public int fullChargeCount;          // Times charged to 100%
    public int deepDischargeCount;       // Times discharged below 5%
    
    // Calibration quality
    public boolean isCalibrated;         // Is battery well-calibrated
    public float calibrationQuality;     // Quality score (0-100)
    public long lastCalibration;         // When last properly calibrated
    public int daysSinceCalibration;
    
    // Charging curve data
    public String chargingCurve;         // JSON of voltage/% mapping while charging
    public String dischargeCurve;        // JSON of voltage/% mapping while discharging
    public float curveDeviation;         // How much curves deviate from expected
    
    // Voltage correlation
    public float voltageAt100;           // Voltage when reporting 100%
    public float voltageAt50;            // Voltage when reporting 50%
    public float voltageAt20;            // Voltage when reporting 20%
    public float voltageAt5;             // Voltage when reporting 5%
    public float voltageCorrelation;     // How well voltage matches %
    
    // AI correction
    public boolean aiCorrectionEnabled;
    public float aiCorrectedLevel;       // AI's suggested actual level
    public float aiConfidence;           // Confidence in AI correction
    public float correctedSoc;           // Advisory corrected SOC
    public float correctionConfidence;   // Confidence in corrected SOC
    
    // Recommendations
    public boolean needsCalibration;     // Should user calibrate
    public String calibrationRecommendation; // How to calibrate
    public String calibrationSchedule;   // Recommended calibration schedule
    public String recommendationReason;  // Why recommendation triggered
    
    // Health impact
    public float healthImpactScore;      // How much calibration affects health reading
    
    public CalibrationData() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public boolean hasSignificantDrift() {
        return Math.abs(levelDrift) > 10; // More than 10% drift is significant
    }
    
    public String getCalibrationStatus() {
        if (calibrationQuality >= 90) return "Excellent";
        if (calibrationQuality >= 75) return "Good";
        if (calibrationQuality >= 50) return "Fair";
        if (calibrationQuality >= 25) return "Poor";
        return "Critical";
    }
    
    public boolean isReliable() {
        return calibrationQuality >= 70 && !hasSuddenDrops;
    }
}
