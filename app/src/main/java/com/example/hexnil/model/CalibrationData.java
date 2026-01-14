package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Tracks battery calibration data and SOC accuracy
 * 
 * Enhanced with OEM-grade calibration intelligence including:
 * - Multi-method SOC estimation tracking
 * - Drift velocity and trend analysis
 * - Calibration success criteria evaluation
 * - Confidence intervals and method agreement
 * - Voltage-SOC correlation analysis
 * - Health impact scoring
 * 
 * @see com.example.hexnil.calibration.CalibrationIntelligence
 * @see com.example.hexnil.calibration.SOCEstimator
 */
@Entity(tableName = "calibration_data")
public class CalibrationData {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    
    // ========== CURRENT STATE ==========
    public int reportedLevel;            // What Android reports
    public float estimatedActualLevel;   // Our calculated estimate
    public float levelDrift;             // Difference between reported and actual
    public boolean driftExceeded;        // Drift crosses threshold
    public boolean socMismatch;          // Estimated vs reported mismatch
    
    // ========== SOC ACCURACY TRACKING ==========
    public float socAccuracy;            // How accurate is SOC (0-100%)
    public float avgDrift;               // Average drift over time
    public float maxDrift;               // Maximum drift observed
    public String driftDirection;        // "over_reporting", "under_reporting", "stable"
    
    // ========== ADVANCED DRIFT METRICS ==========
    public float emaDrift;               // Exponential moving average of drift
    public float driftVelocity;          // Rate of drift change (%/hour)
    public float driftTrend;             // Trend direction (improving/worsening)
    public String driftSeverity;         // "none", "minor", "moderate", "significant", "severe", "critical"
    public float driftVariance;          // Variance in drift measurements
    public float driftStdDev;            // Standard deviation of drift
    
    // ========== SUDDEN DROP DETECTION ==========
    public boolean hasSuddenDrops;       // Sudden % drops detected
    public int suddenDropCount;          // Number of sudden drops
    public String suddenDrops;           // JSON of drop events
    public float avgDropSize;            // Average size of drops (%)
    public int worstDrop;                // Largest single drop (%)
    public float dropRatePerHour;        // Rate when drops occur
    public String dropPattern;           // "low_battery", "high_battery", "random", "none"
    public int peakDropHour;             // Hour of day with most drops
    
    // ========== CALIBRATION POINTS ==========
    public long lastFullCharge;          // Last time at 100%
    public long lastLowBattery;          // Last time below 10%
    public long lastDeepDischarge;       // Last time below 5%
    public int fullChargeCount;          // Times charged to 100%
    public int deepDischargeCount;       // Times discharged below 5%
    public long daysSinceFullCycle;      // Days since full 0-100% cycle
    
    // ========== CALIBRATION QUALITY ==========
    public boolean isCalibrated;         // Is battery well-calibrated
    public float calibrationQuality;     // Quality score (0-100)
    public String qualityGrade;          // "Excellent", "Good", "Fair", "Poor", "Critical"
    public long lastCalibration;         // When last properly calibrated
    public int daysSinceCalibration;
    
    // ========== CALIBRATION SUCCESS CRITERIA ==========
    public boolean avgDriftPassing;      // Drift below threshold
    public boolean noRecentDropsPassing; // No drops in 7 days
    public boolean velocityPassing;      // Drift velocity stable
    public boolean hasRecentFullCharge;  // Full charge in 30 days
    public boolean hasRecentDeepDischarge; // Deep discharge in 30 days
    public int criteriaMet;              // Number of criteria passing (0-5)
    public float calibrationScore;       // Score based on criteria
    
    // ========== ESTIMATION METHODS ==========
    public float chargeCounterEstimate;  // SOC from charge counter
    public float chargeCounterConfidence; // Confidence in charge counter
    public float voltageEstimate;        // SOC from voltage curve
    public float voltageConfidence;      // Confidence in voltage method
    public float energyEstimate;         // SOC from energy counter
    public float energyConfidence;       // Confidence in energy method
    public float capacityEstimate;       // SOC from capacity ratio
    public float capacityConfidence;     // Confidence in capacity method
    public String primaryMethod;         // Which method contributed most
    public float methodAgreement;        // How well methods agree (0-100%)
    
    // ========== CONFIDENCE INTERVALS ==========
    public float overallConfidence;      // Overall confidence in estimation
    public String confidenceGrade;       // "Very High", "High", "Moderate", "Low", "Very Low"
    public float confidenceIntervalLow;  // Lower bound of 95% CI
    public float confidenceIntervalHigh; // Upper bound of 95% CI
    public float intervalWidth;          // Width of confidence interval
    
    // ========== CHARGING CURVE DATA ==========
    public String chargingCurve;         // JSON of voltage/% mapping while charging
    public String dischargeCurve;        // JSON of voltage/% mapping while discharging
    public float curveDeviation;         // How much curves deviate from expected
    
    // ========== VOLTAGE CORRELATION ==========
    public float voltageAt100;           // Voltage when reporting 100%
    public float voltageAt80;            // Voltage when reporting 80%
    public float voltageAt50;            // Voltage when reporting 50%
    public float voltageAt20;            // Voltage when reporting 20%
    public float voltageAt5;             // Voltage when reporting 5%
    public float voltageCorrelation;     // How well voltage matches %
    public float voltageCorrelationScore; // Correlation quality score (0-100)
    public String correlationGrade;      // "Excellent", "Good", "Fair", "Poor", "Unreliable"
    public boolean curveStretched;       // Voltage curve is stretched
    public boolean curveCompressed;      // Voltage curve is compressed
    public float voltageRangeRatio;      // Measured vs expected voltage range
    
    // ========== AI CORRECTION ==========
    public boolean aiCorrectionEnabled;
    public float aiCorrectedLevel;       // AI's suggested actual level
    public float aiConfidence;           // Confidence in AI correction
    public float correctedSoc;           // Advisory corrected SOC
    public float correctionConfidence;   // Confidence in corrected SOC
    
    // ========== RECOMMENDATIONS ==========
    public boolean needsCalibration;     // Should user calibrate
    public String calibrationUrgency;    // "none", "low", "moderate", "high", "critical"
    public String calibrationRecommendation; // How to calibrate
    public String calibrationSchedule;   // Recommended calibration schedule
    public String recommendationReason;  // Why recommendation triggered
    public long nextCalibrationDate;     // Recommended next calibration timestamp
    
    // ========== HEALTH IMPACT ==========
    public float healthImpactScore;      // How much calibration affects health reading (0-100)
    public String healthImpactLevel;     // "minimal", "low", "moderate", "high", "severe"
    public boolean affectsHealthAccuracy; // Does calibration issue affect health readings
    
    // ========== HISTORY ANALYSIS ==========
    public int sampleCount;              // Number of samples in analysis
    public float volatilityScore;        // How volatile is the calibration
    public String trendDirection;        // "improving", "stable", "worsening"
    public int underReportingCount;      // Samples where under-reporting
    public int overReportingCount;       // Samples where over-reporting
    public int stableCount;              // Samples where stable
    public String dominantPattern;       // Most common drift pattern
    
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
    
    /**
     * Get drift severity as enum-like string
     */
    public String getDriftSeverityLevel() {
        float absDrift = Math.abs(levelDrift);
        if (absDrift >= 20) return "critical";
        if (absDrift >= 12) return "severe";
        if (absDrift >= 8) return "significant";
        if (absDrift >= 5) return "moderate";
        if (absDrift >= 3) return "minor";
        return "none";
    }
    
    /**
     * Check if calibration is urgent
     */
    public boolean isUrgent() {
        return "critical".equals(calibrationUrgency) || "high".equals(calibrationUrgency);
    }
    
    /**
     * Get a human-readable summary
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Calibration: ").append(qualityGrade != null ? qualityGrade : getCalibrationStatus());
        sb.append(" (").append(String.format("%.0f", calibrationQuality)).append("%)");
        
        if (Math.abs(levelDrift) >= 3) {
            sb.append(", Drift: ").append(String.format("%.1f%%", levelDrift));
            sb.append(" (").append(driftDirection).append(")");
        }
        
        if (needsCalibration) {
            sb.append(" - ").append(calibrationUrgency != null ? calibrationUrgency.toUpperCase() : "NEEDS CALIBRATION");
        }
        
        return sb.toString();
    }
    
    /**
     * Check if estimation methods agree
     */
    public boolean hasMethodAgreement() {
        return methodAgreement >= 70f;
    }
    
    /**
     * Get confidence level description
     */
    public String getConfidenceDescription() {
        if (overallConfidence >= 90) return "Very High - measurements are highly reliable";
        if (overallConfidence >= 75) return "High - measurements are reliable";
        if (overallConfidence >= 50) return "Moderate - some uncertainty in measurements";
        if (overallConfidence >= 25) return "Low - significant uncertainty";
        return "Very Low - measurements may not be reliable";
    }
}
