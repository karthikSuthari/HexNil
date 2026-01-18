package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * OEM-Grade Battery Health Entity
 * 
 * Comprehensive battery health tracking beyond Android's fake "GOOD" status.
 * Provides real capacity measurement, cycle counting, degradation prediction,
 * and supplier/batch quality tracking for OEM diagnostics.
 * 
 * KEY METRICS:
 * - Design vs Actual Capacity (mAh)
 * - Capacity Fade (%)
 * - Cycle Count (full + partial equivalents)
 * - Degradation Rates (per cycle, per month, per 100 cycles)
 * - Predicted End-of-Life (EOL)
 * - Abnormal Wear Detection
 * - Supplier/Batch Quality Tracking
 * 
 * CONFIDENCE MODEL:
 * - Confidence improves with more data points
 * - Minimum 7 days data for baseline
 * - Minimum 30 days for degradation trends
 * - Full cycle calibration improves accuracy
 */
@Entity(tableName = "battery_health", indices = {
        @Index(value = { "timestamp" }),
        @Index(value = { "manufacturer" }),
        @Index(value = { "batchId" }),
        @Index(value = { "healthPercentage" }),
        @Index(value = { "hasAbnormalWear" })
})
public class BatteryHealth {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;

    // Force DAO regeneration
    @Ignore
    public long schemaVersion = 2;

    // =========================================================================
    // CAPACITY METRICS
    // =========================================================================

    /** Original manufacturer-specified capacity (mAh) */
    public float designCapacityMah;

    /** Current estimated capacity from charge counter (mAh) */
    public float currentCapacityMah;

    /** Capacity measured from full discharge cycle (mAh) - most accurate */
    public float measuredCapacityMah;

    /** AI/algorithm estimated capacity when direct measurement unavailable */
    public float estimatedCapacityMah;

    /**
     * Absolute capacity lost (mAh)
     * Formula: designCapacityMah - currentCapacityMah
     */
    public float capacityLossMah;

    /**
     * Capacity fade as percentage of design capacity
     * Formula: (designCapacityMah - currentCapacityMah) / designCapacityMah * 100
     */
    public float capacityFade;

    /**
     * Real health percentage (not Android's fake status)
     * Formula: currentCapacityMah / designCapacityMah * 100
     */
    public float healthPercentage;

    /** Explicit fade percent for backward compatibility */
    public float capacityFadePercent;

    /** State of Health (SOH) - industry standard metric (0-100%) */
    public float stateOfHealth;

    // =========================================================================
    // CYCLE COUNTING (Critical for Degradation)
    // =========================================================================

    /**
     * Full equivalent cycle count (1 cycle = 100% discharge)
     * Formula: totalDischargeAccumulated / designCapacityMah
     */
    public float cycleCount;

    /** Integer cycle count for display */
    public int fullCycleCount;

    /**
     * Partial cycle accumulator (0.0 - 1.0)
     * Increments with each partial discharge, rolls over at 1.0
     */
    public float partialCycleAccumulator;

    /** Total mAh charged over lifetime (coulomb counting) */
    public long totalChargeAccumulated;

    /** Total mAh discharged over lifetime (coulomb counting) */
    public long totalDischargeAccumulated;

    /** Count of charges from <20% to >80% (deep cycles) */
    public int deepCycleCount;

    /** Count of charges from >80% to 100% (top-off cycles - more damaging) */
    public int topOffCycleCount;

    /** Days since first data point (for per-month calculations) */
    public int trackingDays;

    /** Timestamp of first health record for this battery */
    public long firstRecordTimestamp;

    // =========================================================================
    // DEGRADATION TRACKING
    // =========================================================================

    /**
     * Capacity loss per charge cycle (%)
     * Formula: capacityFadePercent / cycleCount
     * Typical: 0.02-0.05% per cycle for healthy battery
     */
    public float fadePerCycle;

    /**
     * Capacity loss per 100 cycles (%)
     * Formula: fadePerCycle * 100
     * Industry standard: <5% per 100 cycles is good
     */
    public float fadePer100Cycles;

    /**
     * Capacity loss per month (%)
     * Formula: (currentFade - fadeAtMonthStart) / monthsElapsed
     * Typical: 0.5-1% per month normal aging
     */
    public float fadePerMonth;

    /**
     * Instantaneous degradation rate (%/month)
     * Calculated from recent trend (last 7 days)
     */
    public float degradationRate;

    /**
     * Smoothed degradation rate (exponential moving average)
     * More stable for predictions
     */
    public float smoothedDegradationRate;

    /**
     * Degradation acceleration (change in degradation rate)
     * Positive = getting worse faster
     */
    public float degradationAcceleration;

    /**
     * Degradation trend classification
     * Values: "stable", "gradual", "accelerating", "critical"
     */
    public String degradationTrend;

    /**
     * Predicted months until 80% health (EOL threshold)
     * Formula: (healthPercentage - 80) / smoothedDegradationRate
     */
    public float predictedEOL;

    /**
     * Predicted months until 70% health (replacement threshold)
     */
    public float predictedReplacementDate;

    /**
     * Confidence in EOL prediction (0-100%)
     * Improves with more data points and calibration cycles
     */
    @androidx.room.ColumnInfo(defaultValue = "0")
    public float eolConfidence = 0f;

    // =========================================================================
    // INTERNAL RESISTANCE & IMPEDANCE (Advanced Health Indicators)
    // =========================================================================

    /** Estimated internal resistance (mΩ) */
    public float internalResistance;

    /** Resistance at 25°C reference (temperature normalized) */
    public float resistance25C;

    /** Resistance increase from baseline (%) */
    public float resistanceIncrease;

    /** Voltage drop under load (mV) */
    public float voltageDropUnderLoad;

    /** Self-discharge rate when idle (%/day) */
    public float selfDischargeRate;

    /** Charging efficiency (energy stored / energy input * 100) */
    public float chargingEfficiency;

    /** Discharge efficiency under load */
    public float dischargeEfficiency;

    // =========================================================================
    // ABNORMAL WEAR DETECTION
    // =========================================================================

    /** Battery wearing faster than expected for its cycle count */
    public boolean hasAbnormalWear;

    /** Aging faster than calendar baseline */
    public boolean hasAbnormalAging;

    /** Indicators suggest possible swelling (safety risk) */
    public boolean hasSwellingRisk;

    /** SOC reporting inaccurate */
    public boolean hasCalibrationIssue;

    /** High internal resistance detected */
    public boolean hasHighResistance;

    /** Unusual capacity variance detected */
    public boolean hasCapacityInstability;

    /** Description of detected abnormalities */
    public String abnormalityDetails;

    /** Aging anomaly notes */
    public String agingNotes;

    /**
     * Wear deviation from expected baseline (%)
     * Positive = worse than expected, Negative = better
     * Formula: actualFade - expectedFadeForCycles
     */
    public float wearDeviation;

    /**
     * Abnormal wear severity (0-10 scale)
     * 0 = normal, 5 = moderate concern, 10 = critical
     */
    public int wearSeverity;

    // =========================================================================
    // HEALTH SCORES (0-100)
    // =========================================================================

    /** Score based on capacity retention */
    public float capacityScore;

    /** Score based on internal resistance */
    public float resistanceScore;

    /** Score based on cycle count vs expected life */
    public float cycleScore;

    /** Score based on degradation rate */
    public float degradationScore;

    /** Score based on thermal history */
    public float thermalScore;

    /** Combined overall health score */
    public float overallScore;

    // =========================================================================
    // SUPPLIER / BATCH QUALITY TRACKING (OEM Feature)
    // =========================================================================

    /** Battery cell model/part number */
    public String batteryModel;

    /** Cell manufacturer (e.g., "ATL", "BYD", "Samsung SDI") */
    public String manufacturer;

    /** Manufacturing date (YYYY-MM format if known) */
    public String manufactureDate;

    /** Production batch identifier for quality tracking */
    public String batchId;

    /** Average health of batteries from same supplier */
    public float supplierAverageHealth;

    /** Average health of batteries from same batch */
    public float batchAverageHealth;

    /** This battery's deviation from supplier average */
    public float supplierDeviation;

    /** This battery's deviation from batch average */
    public float batchDeviation;

    /** Supplier quality tier (A/B/C/D/F) */
    public String supplierQualityTier;

    /** Batch quality flag */
    public boolean batchQualityAlert;

    // =========================================================================
    // CALIBRATION & DATA QUALITY
    // =========================================================================

    /** Timestamp of last 100% charge */
    public long lastFullCharge;

    /** Timestamp of last <5% discharge (calibration point) */
    public long lastFullDischarge;

    /** Has completed at least one full cycle for calibration */
    public boolean hasCalibrationCycle;

    /** Calibration recommended flag */
    public boolean needsCalibration;

    /** Number of data points used for this health record */
    public int dataPointCount;

    /**
     * Overall confidence in health metrics (0-100%)
     * 
     * Confidence factors:
     * - Data age (more days = higher confidence, up to 30 days)
     * - Calibration cycles (full cycles improve confidence)
     * - Data density (more snapshots = better)
     * - Measurement consistency (low variance = better)
     */
    public float overallConfidence;

    /**
     * Confidence in capacity measurement specifically
     */
    public float capacityConfidence;

    /**
     * Confidence in cycle count accuracy
     */
    public float cycleConfidence;

    /**
     * Confidence in degradation rate
     */
    public float degradationConfidence;

    /** Minimum data requirement met for reliable analysis */
    public boolean meetsMinimumDataRequirement;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    public BatteryHealth() {
        this.timestamp = System.currentTimeMillis();
        this.degradationTrend = "unknown";
        this.overallConfidence = 0;
        this.eolConfidence = 0; // Explicit init for NOT NULL constraint
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Returns letter grade for health (A-F)
     */
    @Ignore
    public String getHealthGrade() {
        if (healthPercentage >= 90)
            return "A";
        if (healthPercentage >= 80)
            return "B";
        if (healthPercentage >= 70)
            return "C";
        if (healthPercentage >= 60)
            return "D";
        return "F";
    }

    /**
     * Checks if battery needs replacement
     */
    @Ignore
    public boolean needsReplacement() {
        return healthPercentage < 70 || hasSwellingRisk || overallScore < 50 || wearSeverity >= 8;
    }

    /**
     * Human-readable health status
     */
    @Ignore
    public String getHealthStatus() {
        if (healthPercentage >= 90)
            return "Excellent";
        if (healthPercentage >= 80)
            return "Good";
        if (healthPercentage >= 70)
            return "Fair";
        if (healthPercentage >= 60)
            return "Poor";
        return "Critical";
    }

    /**
     * Checks if we have enough data for reliable predictions
     */
    @Ignore
    public boolean hasReliableData() {
        return overallConfidence >= 60 && meetsMinimumDataRequirement;
    }

    /**
     * Gets EOL prediction with confidence qualifier
     */
    @Ignore
    public String getEolPredictionString() {
        if (eolConfidence < 30) {
            return "Insufficient data for prediction";
        } else if (eolConfidence < 60) {
            return String.format("~%.0f months (low confidence)", predictedEOL);
        } else if (eolConfidence < 80) {
            return String.format("~%.0f months (moderate confidence)", predictedEOL);
        } else {
            return String.format("%.0f months (high confidence)", predictedEOL);
        }
    }

    /**
     * Creates a summary string for logging/diagnostics
     */
    @Override
    public String toString() {
        return "BatteryHealth{" +
                "id=" + id +
                ", health=" + String.format("%.1f%%", healthPercentage) +
                ", cycles=" + String.format("%.1f", cycleCount) +
                ", fade=" + String.format("%.2f%%", capacityFade) +
                ", fadePerCycle=" + String.format("%.4f%%", fadePerCycle) +
                ", eol=" + String.format("%.0f months", predictedEOL) +
                ", confidence=" + String.format("%.0f%%", overallConfidence) +
                ", abnormal=" + hasAbnormalWear +
                ", trend=" + degradationTrend +
                '}';
    }
}