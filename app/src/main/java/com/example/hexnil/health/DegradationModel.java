package com.example.hexnil.health;

import android.util.Log;

/**
 * OEM-Grade Battery Degradation Model
 * 
 * Provides mathematical models and formulas for battery degradation prediction.
 * Based on lithium-ion battery aging research and OEM validation data.
 * 
 * ============================================================================
 * KEY FORMULAS
 * ============================================================================
 * 
 * 1. STATE OF HEALTH (SOH):
 *    SOH = (CurrentCapacity / DesignCapacity) × 100%
 * 
 * 2. CAPACITY FADE:
 *    CapacityFade = 100% - SOH
 *    CapacityFade = (DesignCapacity - CurrentCapacity) / DesignCapacity × 100%
 * 
 * 3. CYCLE COUNT (Equivalent Full Cycles):
 *    CycleCount = TotalDischargeAccumulated / DesignCapacity
 *    (One cycle = discharge from 100% to 0%)
 * 
 * 4. DEGRADATION PER CYCLE:
 *    FadePerCycle = CapacityFade / CycleCount
 *    Typical healthy: 0.02-0.05% per cycle
 * 
 * 5. DEGRADATION PER 100 CYCLES:
 *    FadePer100Cycles = FadePerCycle × 100
 *    Industry standard threshold: <5% per 100 cycles
 * 
 * 6. DEGRADATION PER MONTH:
 *    FadePerMonth = ΔCapacityFade / ΔMonths
 *    Calendar aging typical: 0.5-1.5% per month
 * 
 * 7. PREDICTED END-OF-LIFE (EOL):
 *    MonthsToEOL = (CurrentSOH - EOLThreshold) / DegradationRate
 *    EOLThreshold = 80% (industry standard)
 * 
 * 8. INTERNAL RESISTANCE ESTIMATION:
 *    R = ΔV / ΔI (from voltage drop under load)
 *    R_normalized = R × (298K / T_actual) (temperature compensation)
 * 
 * ============================================================================
 * THRESHOLDS & FLAGS
 * ============================================================================
 * 
 * CAPACITY:
 * - Excellent: SOH ≥ 90%
 * - Good: SOH ≥ 80%
 * - Fair: SOH ≥ 70%
 * - Poor: SOH ≥ 60%
 * - Critical: SOH < 60%
 * - EOL Threshold: SOH = 80%
 * - Replacement: SOH = 70%
 * 
 * DEGRADATION RATE:
 * - Normal: <1% per month, <3% per 100 cycles
 * - Elevated: 1-2% per month, 3-5% per 100 cycles
 * - High: 2-3% per month, 5-8% per 100 cycles
 * - Critical: >3% per month, >8% per 100 cycles
 * 
 * CYCLE LIFE:
 * - Premium cells: 800-1000 cycles to 80%
 * - Standard cells: 500-800 cycles to 80%
 * - Budget cells: 300-500 cycles to 80%
 * 
 * ABNORMAL WEAR FLAGS:
 * - Capacity loss > 20% in first 100 cycles
 * - FadePerCycle > 0.1%
 * - FadePer100Cycles > 8%
 * - FadePerMonth > 3%
 * - SOH drops >5% in one week
 * 
 * SWELLING RISK FLAGS:
 * - Temperature >45°C during discharge
 * - Rapid capacity loss + high temperature
 * - Voltage instability at low SOC
 */
public class DegradationModel {

    private static final String TAG = "DegradationModel";

    // =========================================================================
    // THRESHOLDS - SOH (State of Health)
    // =========================================================================
    
    /** Excellent health threshold */
    public static final float SOH_EXCELLENT = 90.0f;
    
    /** Good health threshold */
    public static final float SOH_GOOD = 80.0f;
    
    /** Fair health threshold */
    public static final float SOH_FAIR = 70.0f;
    
    /** Poor health threshold */
    public static final float SOH_POOR = 60.0f;
    
    /** End-of-life threshold (industry standard) */
    public static final float SOH_EOL_THRESHOLD = 80.0f;
    
    /** Replacement recommended threshold */
    public static final float SOH_REPLACEMENT_THRESHOLD = 70.0f;
    
    /** Critical - immediate replacement needed */
    public static final float SOH_CRITICAL = 60.0f;

    // =========================================================================
    // THRESHOLDS - DEGRADATION RATE
    // =========================================================================
    
    /** Normal fade per cycle (%) */
    public static final float FADE_PER_CYCLE_NORMAL = 0.05f;
    
    /** Elevated fade per cycle (%) - flag raised */
    public static final float FADE_PER_CYCLE_ELEVATED = 0.08f;
    
    /** High fade per cycle (%) - abnormal wear */
    public static final float FADE_PER_CYCLE_HIGH = 0.10f;
    
    /** Critical fade per cycle (%) - severe issue */
    public static final float FADE_PER_CYCLE_CRITICAL = 0.15f;
    
    /** Normal fade per 100 cycles (%) */
    public static final float FADE_PER_100_CYCLES_NORMAL = 5.0f;
    
    /** Elevated fade per 100 cycles (%) */
    public static final float FADE_PER_100_CYCLES_ELEVATED = 8.0f;
    
    /** High fade per 100 cycles (%) */
    public static final float FADE_PER_100_CYCLES_HIGH = 10.0f;
    
    /** Normal fade per month (%) - calendar aging */
    public static final float FADE_PER_MONTH_NORMAL = 1.0f;
    
    /** Elevated fade per month (%) */
    public static final float FADE_PER_MONTH_ELEVATED = 2.0f;
    
    /** High fade per month (%) */
    public static final float FADE_PER_MONTH_HIGH = 3.0f;

    // =========================================================================
    // THRESHOLDS - CYCLE LIFE EXPECTATIONS
    // =========================================================================
    
    /** Expected cycles to 80% SOH - premium cells */
    public static final int CYCLES_TO_EOL_PREMIUM = 1000;
    
    /** Expected cycles to 80% SOH - standard cells */
    public static final int CYCLES_TO_EOL_STANDARD = 500;
    
    /** Expected cycles to 80% SOH - budget cells */
    public static final int CYCLES_TO_EOL_BUDGET = 300;
    
    /** Default expected cycles (conservative) */
    public static final int CYCLES_TO_EOL_DEFAULT = 500;

    // =========================================================================
    // THRESHOLDS - TEMPERATURE
    // =========================================================================
    
    /** Optimal charging temperature (°C) */
    public static final float TEMP_OPTIMAL_MIN = 20.0f;
    public static final float TEMP_OPTIMAL_MAX = 25.0f;
    
    /** Safe operating range (°C) */
    public static final float TEMP_SAFE_MIN = 0.0f;
    public static final float TEMP_SAFE_MAX = 40.0f;
    
    /** Accelerated degradation threshold (°C) */
    public static final float TEMP_ACCELERATED_DEGRADATION = 35.0f;
    
    /** Swelling risk threshold (°C) */
    public static final float TEMP_SWELLING_RISK = 45.0f;
    
    /** Critical temperature (°C) */
    public static final float TEMP_CRITICAL = 50.0f;

    // =========================================================================
    // THRESHOLDS - INTERNAL RESISTANCE
    // =========================================================================
    
    /** Normal internal resistance for fresh cell (mΩ) */
    public static final float RESISTANCE_NORMAL = 50.0f;
    
    /** Elevated resistance indicating wear (mΩ) */
    public static final float RESISTANCE_ELEVATED = 80.0f;
    
    /** High resistance - significant degradation (mΩ) */
    public static final float RESISTANCE_HIGH = 120.0f;
    
    /** Maximum acceptable resistance increase (%) */
    public static final float RESISTANCE_INCREASE_MAX = 50.0f;

    // =========================================================================
    // CONFIDENCE PARAMETERS
    // =========================================================================
    
    /** Minimum days of data for baseline confidence */
    public static final int MIN_DAYS_FOR_BASELINE = 7;
    
    /** Days of data for moderate confidence */
    public static final int DAYS_FOR_MODERATE_CONFIDENCE = 14;
    
    /** Days of data for high confidence */
    public static final int DAYS_FOR_HIGH_CONFIDENCE = 30;
    
    /** Minimum snapshots per day for reliable data */
    public static final int MIN_SNAPSHOTS_PER_DAY = 24;
    
    /** Full calibration cycle bonus to confidence */
    public static final float CALIBRATION_CONFIDENCE_BONUS = 15.0f;

    // =========================================================================
    // CORE CALCULATION METHODS
    // =========================================================================

    /**
     * Calculate State of Health (SOH)
     * 
     * Formula: SOH = (CurrentCapacity / DesignCapacity) × 100%
     * 
     * @param currentCapacityMah Current battery capacity
     * @param designCapacityMah Original design capacity
     * @return SOH percentage (0-100+, can exceed 100 if measurement error)
     */
    public static float calculateSOH(float currentCapacityMah, float designCapacityMah) {
        if (designCapacityMah <= 0) {
            Log.w(TAG, "Invalid design capacity: " + designCapacityMah);
            return 0;
        }
        float soh = (currentCapacityMah / designCapacityMah) * 100.0f;
        // Allow slight over 100% due to measurement variance, but cap at 105%
        return Math.max(0, Math.min(soh, 105.0f));
    }

    /**
     * Calculate capacity fade percentage
     * 
     * Formula: Fade = 100% - SOH = (Design - Current) / Design × 100%
     */
    public static float calculateCapacityFade(float currentCapacityMah, float designCapacityMah) {
        float soh = calculateSOH(currentCapacityMah, designCapacityMah);
        return Math.max(0, 100.0f - soh);
    }

    /**
     * Calculate equivalent full cycle count
     * 
     * Formula: Cycles = TotalDischarge / DesignCapacity
     * 
     * One full cycle = 100% depth of discharge (DoD)
     * Partial cycles accumulate (e.g., 2x 50% DoD = 1 cycle)
     */
    public static float calculateCycleCount(long totalDischargeMah, float designCapacityMah) {
        if (designCapacityMah <= 0) return 0;
        return totalDischargeMah / designCapacityMah;
    }

    /**
     * Calculate degradation per cycle
     * 
     * Formula: FadePerCycle = TotalFade / CycleCount
     */
    public static float calculateFadePerCycle(float capacityFade, float cycleCount) {
        if (cycleCount <= 0.1f) return 0; // Need at least 0.1 cycles
        return capacityFade / cycleCount;
    }

    /**
     * Calculate degradation per 100 cycles
     * 
     * Formula: FadePer100Cycles = FadePerCycle × 100
     */
    public static float calculateFadePer100Cycles(float fadePerCycle) {
        return fadePerCycle * 100.0f;
    }

    /**
     * Calculate degradation per month (calendar aging)
     * 
     * Formula: FadePerMonth = (CurrentFade - InitialFade) / MonthsElapsed
     * 
     * @param currentFade Current capacity fade (%)
     * @param initialFade Fade at start of tracking (%)
     * @param daysElapsed Days since tracking started
     */
    public static float calculateFadePerMonth(float currentFade, float initialFade, int daysElapsed) {
        if (daysElapsed < 7) return 0; // Need at least a week
        float monthsElapsed = daysElapsed / 30.0f;
        if (monthsElapsed < 0.1f) return 0;
        return (currentFade - initialFade) / monthsElapsed;
    }

    /**
     * Calculate instantaneous degradation rate from recent trend
     * 
     * @param recentFadePoints Array of {timestamp_ms, fade%} pairs
     * @return Degradation rate in %/month
     */
    public static float calculateInstantaneousDegradationRate(float[][] recentFadePoints) {
        if (recentFadePoints == null || recentFadePoints.length < 2) return 0;
        
        // Linear regression on recent points
        int n = recentFadePoints.length;
        float sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        float baseTime = recentFadePoints[0][0];
        
        for (float[] point : recentFadePoints) {
            float x = (point[0] - baseTime) / (30.0f * 24 * 60 * 60 * 1000); // Convert to months
            float y = point[1]; // Fade %
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        float denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 0.0001f) return 0;
        
        // Slope = degradation rate in %/month
        return (n * sumXY - sumX * sumY) / denominator;
    }

    /**
     * Calculate exponential moving average for smoothed degradation rate
     * 
     * @param currentRate Current degradation rate
     * @param previousSmoothed Previous smoothed value
     * @param alpha Smoothing factor (0-1, higher = more weight to current)
     */
    public static float calculateSmoothedRate(float currentRate, float previousSmoothed, float alpha) {
        if (previousSmoothed == 0) return currentRate;
        return alpha * currentRate + (1 - alpha) * previousSmoothed;
    }

    /**
     * Calculate predicted End-of-Life (months until 80% SOH)
     * 
     * Formula: MonthsToEOL = (CurrentSOH - 80%) / DegradationRate
     * 
     * @param currentSOH Current state of health (%)
     * @param degradationRatePerMonth Degradation rate (%/month)
     * @param eolThreshold EOL threshold (default 80%)
     * @return Predicted months until EOL, or Float.MAX_VALUE if not degrading
     */
    public static float calculatePredictedEOL(float currentSOH, float degradationRatePerMonth, 
                                               float eolThreshold) {
        if (currentSOH <= eolThreshold) return 0; // Already at or past EOL
        if (degradationRatePerMonth <= 0.01f) return 120; // Essentially not degrading, cap at 10 years
        
        float monthsRemaining = (currentSOH - eolThreshold) / degradationRatePerMonth;
        return Math.min(monthsRemaining, 120); // Cap at 10 years
    }

    /**
     * Calculate predicted EOL with default 80% threshold
     */
    public static float calculatePredictedEOL(float currentSOH, float degradationRatePerMonth) {
        return calculatePredictedEOL(currentSOH, degradationRatePerMonth, SOH_EOL_THRESHOLD);
    }

    // =========================================================================
    // INTERNAL RESISTANCE ESTIMATION
    // =========================================================================

    /**
     * Estimate internal resistance from voltage drop under load
     * 
     * Formula: R = ΔV / ΔI
     * 
     * @param voltageDropMv Voltage drop when load applied (mV)
     * @param currentChangeMa Current change (mA)
     * @return Estimated resistance (mΩ)
     */
    public static float estimateInternalResistance(float voltageDropMv, float currentChangeMa) {
        if (currentChangeMa <= 0) return 0;
        return (voltageDropMv / currentChangeMa) * 1000; // mΩ
    }

    /**
     * Normalize resistance to 25°C reference temperature
     * 
     * Resistance increases ~0.5% per °C below 25°C
     * 
     * @param resistance Measured resistance (mΩ)
     * @param temperatureC Measurement temperature (°C)
     * @return Normalized resistance at 25°C (mΩ)
     */
    public static float normalizeResistanceTo25C(float resistance, float temperatureC) {
        float tempDiff = temperatureC - 25.0f;
        float tempFactor = 1.0f + (tempDiff * 0.005f); // 0.5% per degree
        return resistance / tempFactor;
    }

    /**
     * Calculate resistance increase from baseline
     * 
     * @param currentResistance Current resistance (mΩ)
     * @param baselineResistance Baseline/new battery resistance (mΩ)
     * @return Increase percentage
     */
    public static float calculateResistanceIncrease(float currentResistance, float baselineResistance) {
        if (baselineResistance <= 0) return 0;
        return ((currentResistance - baselineResistance) / baselineResistance) * 100.0f;
    }

    // =========================================================================
    // ABNORMAL WEAR DETECTION
    // =========================================================================

    /**
     * Check if wear is abnormal based on capacity fade vs cycles
     * 
     * Expected fade = cycles × expectedFadePerCycle
     * Abnormal if actual > expected × threshold
     */
    public static AbnormalWearResult checkAbnormalWear(float capacityFade, float cycleCount,
                                                        float fadePerMonth, float soh) {
        AbnormalWearResult result = new AbnormalWearResult();
        
        // Calculate expected fade for this cycle count
        // Using standard expectation of 20% fade in 500 cycles = 0.04% per cycle
        float expectedFadePerCycle = 0.04f;
        float expectedFade = cycleCount * expectedFadePerCycle;
        
        // Deviation from expected
        result.wearDeviation = capacityFade - expectedFade;
        
        // Check various abnormal conditions
        float fadePerCycle = cycleCount > 0.1f ? capacityFade / cycleCount : 0;
        
        // Flag 1: High fade per cycle
        if (fadePerCycle > FADE_PER_CYCLE_HIGH) {
            result.hasAbnormalWear = true;
            result.flags.add("High fade per cycle: " + String.format("%.3f%%", fadePerCycle));
            result.severity += 3;
        } else if (fadePerCycle > FADE_PER_CYCLE_ELEVATED) {
            result.flags.add("Elevated fade per cycle: " + String.format("%.3f%%", fadePerCycle));
            result.severity += 1;
        }
        
        // Flag 2: High calendar aging
        if (fadePerMonth > FADE_PER_MONTH_HIGH) {
            result.hasAbnormalWear = true;
            result.hasAbnormalAging = true;
            result.flags.add("High calendar aging: " + String.format("%.2f%%/month", fadePerMonth));
            result.severity += 3;
        } else if (fadePerMonth > FADE_PER_MONTH_ELEVATED) {
            result.flags.add("Elevated calendar aging: " + String.format("%.2f%%/month", fadePerMonth));
            result.severity += 1;
        }
        
        // Flag 3: Excessive early wear (>20% fade in first 100 cycles)
        if (cycleCount > 10 && cycleCount <= 100 && capacityFade > 20) {
            result.hasAbnormalWear = true;
            result.flags.add("Excessive early wear: " + String.format("%.1f%% fade in %.0f cycles", 
                    capacityFade, cycleCount));
            result.severity += 4;
        }
        
        // Flag 4: Large deviation from expected
        if (result.wearDeviation > 10) {
            result.hasAbnormalWear = true;
            result.flags.add("Large wear deviation: " + String.format("+%.1f%% vs expected", 
                    result.wearDeviation));
            result.severity += 2;
        }
        
        // Flag 5: SOH already at critical level
        if (soh < SOH_CRITICAL) {
            result.flags.add("Critical SOH level: " + String.format("%.1f%%", soh));
            result.severity += 3;
        }
        
        // Cap severity at 10
        result.severity = Math.min(10, result.severity);
        
        return result;
    }

    /**
     * Check for swelling risk indicators
     */
    public static boolean checkSwellingRisk(float temperatureC, float voltageDropMv, 
                                             boolean isCharging, float soh) {
        // High temp during discharge is a warning sign
        if (temperatureC > TEMP_SWELLING_RISK && !isCharging) {
            return true;
        }
        
        // Combination of rapid degradation + high temp
        if (soh < 70 && temperatureC > TEMP_ACCELERATED_DEGRADATION) {
            return true;
        }
        
        // Large voltage instability at low SOC
        if (voltageDropMv > 200 && soh < 80) {
            return true;
        }
        
        return false;
    }

    // =========================================================================
    // CONFIDENCE CALCULATION
    // =========================================================================

    /**
     * Calculate overall confidence in health metrics
     * 
     * Factors:
     * - Data age (more days = higher, up to 30 days)
     * - Data density (snapshots per day)
     * - Calibration cycles completed
     * - Measurement consistency
     */
    public static ConfidenceResult calculateConfidence(int trackingDays, int snapshotCount,
                                                        boolean hasCalibrationCycle,
                                                        float measurementVariance) {
        ConfidenceResult result = new ConfidenceResult();
        
        // Factor 1: Data age (0-30 points)
        float ageFactor = Math.min(30, trackingDays) / 30.0f * 30;
        result.ageContribution = ageFactor;
        
        // Factor 2: Data density (0-30 points)
        float snapshotsPerDay = trackingDays > 0 ? snapshotCount / (float) trackingDays : 0;
        float densityFactor = Math.min(1, snapshotsPerDay / MIN_SNAPSHOTS_PER_DAY) * 30;
        result.densityContribution = densityFactor;
        
        // Factor 3: Calibration (0-15 points)
        float calibrationFactor = hasCalibrationCycle ? CALIBRATION_CONFIDENCE_BONUS : 0;
        result.calibrationContribution = calibrationFactor;
        
        // Factor 4: Consistency (0-25 points)
        // Lower variance = higher confidence
        // Typical variance should be <5%
        float consistencyFactor = Math.max(0, 25 - (measurementVariance * 5));
        result.consistencyContribution = consistencyFactor;
        
        // Total confidence
        result.overallConfidence = ageFactor + densityFactor + calibrationFactor + consistencyFactor;
        result.overallConfidence = Math.min(100, result.overallConfidence);
        
        // Specific confidence levels
        result.capacityConfidence = result.overallConfidence * (hasCalibrationCycle ? 1.0f : 0.8f);
        result.cycleConfidence = result.overallConfidence * 0.9f; // Cycle counting is reliable
        result.degradationConfidence = trackingDays >= DAYS_FOR_HIGH_CONFIDENCE 
                ? result.overallConfidence : result.overallConfidence * 0.6f;
        
        // Meets minimum requirement
        result.meetsMinimumRequirement = trackingDays >= MIN_DAYS_FOR_BASELINE 
                && snapshotsPerDay >= MIN_SNAPSHOTS_PER_DAY * 0.5f;
        
        return result;
    }

    // =========================================================================
    // HEALTH SCORE CALCULATION
    // =========================================================================

    /**
     * Calculate component health scores (0-100)
     */
    public static HealthScores calculateHealthScores(float soh, float cycleCount, 
                                                      float resistanceIncrease,
                                                      float degradationRate,
                                                      float avgTemperatureC) {
        HealthScores scores = new HealthScores();
        
        // Capacity score: direct SOH mapping
        scores.capacityScore = Math.max(0, Math.min(100, soh));
        
        // Cycle score: 100 at 0 cycles, degrades toward expected EOL
        // Assume 500 cycles = 0 score
        scores.cycleScore = Math.max(0, 100 - (cycleCount / CYCLES_TO_EOL_DEFAULT * 100));
        
        // Resistance score: 100 at baseline, degrades with increase
        scores.resistanceScore = Math.max(0, 100 - (resistanceIncrease * 2));
        
        // Degradation score: based on rate vs normal
        if (degradationRate <= FADE_PER_MONTH_NORMAL) {
            scores.degradationScore = 100;
        } else if (degradationRate <= FADE_PER_MONTH_ELEVATED) {
            scores.degradationScore = 80 - ((degradationRate - FADE_PER_MONTH_NORMAL) * 20);
        } else if (degradationRate <= FADE_PER_MONTH_HIGH) {
            scores.degradationScore = 50 - ((degradationRate - FADE_PER_MONTH_ELEVATED) * 20);
        } else {
            scores.degradationScore = Math.max(0, 20 - ((degradationRate - FADE_PER_MONTH_HIGH) * 10));
        }
        
        // Thermal score: based on average operating temperature
        if (avgTemperatureC >= TEMP_OPTIMAL_MIN && avgTemperatureC <= TEMP_OPTIMAL_MAX) {
            scores.thermalScore = 100;
        } else if (avgTemperatureC <= TEMP_SAFE_MAX) {
            float deviation = Math.abs(avgTemperatureC - 22.5f); // Mid-optimal
            scores.thermalScore = Math.max(60, 100 - (deviation * 2));
        } else {
            scores.thermalScore = Math.max(0, 60 - ((avgTemperatureC - TEMP_SAFE_MAX) * 5));
        }
        
        // Overall score: weighted average
        scores.overallScore = 
            scores.capacityScore * 0.40f +
            scores.cycleScore * 0.20f +
            scores.resistanceScore * 0.10f +
            scores.degradationScore * 0.20f +
            scores.thermalScore * 0.10f;
        
        return scores;
    }

    // =========================================================================
    // RESULT CLASSES
    // =========================================================================

    public static class AbnormalWearResult {
        public boolean hasAbnormalWear = false;
        public boolean hasAbnormalAging = false;
        public float wearDeviation = 0;
        public int severity = 0; // 0-10
        public java.util.List<String> flags = new java.util.ArrayList<>();
        
        public String getDetails() {
            if (flags.isEmpty()) return "Normal wear";
            return String.join("; ", flags);
        }
    }

    public static class ConfidenceResult {
        public float overallConfidence = 0;
        public float capacityConfidence = 0;
        public float cycleConfidence = 0;
        public float degradationConfidence = 0;
        public float ageContribution = 0;
        public float densityContribution = 0;
        public float calibrationContribution = 0;
        public float consistencyContribution = 0;
        public boolean meetsMinimumRequirement = false;
    }

    public static class HealthScores {
        public float capacityScore = 0;
        public float cycleScore = 0;
        public float resistanceScore = 0;
        public float degradationScore = 0;
        public float thermalScore = 0;
        public float overallScore = 0;
    }
}
