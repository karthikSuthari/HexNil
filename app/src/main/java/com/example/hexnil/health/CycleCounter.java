package com.example.hexnil.health;

import android.util.Log;

/**
 * OEM-Grade Cycle Counter for Battery Lifecycle Tracking
 * 
 * Implements accurate cycle counting using coulomb counting methodology.
 * Tracks both full cycles and partial cycle equivalents.
 * 
 * ============================================================================
 * CYCLE COUNTING METHODOLOGY
 * ============================================================================
 * 
 * DEFINITION OF ONE CYCLE:
 * - One full cycle = 100% depth of discharge (DoD)
 * - Equivalent to discharging from 100% to 0% and back
 * - Partial cycles accumulate: 2x 50% DoD = 1 full cycle
 * 
 * COULOMB COUNTING:
 * - Track total mAh discharged over time
 * - Cycles = TotalDischarged / DesignCapacity
 * 
 * CYCLE TYPES:
 * - Full Cycle: DoD >= 80%
 * - Deep Cycle: DoD >= 60%
 * - Standard Cycle: DoD >= 30%
 * - Top-off Cycle: DoD < 30%
 * 
 * DEGRADATION IMPACT BY CYCLE TYPE:
 * - Deep cycles (DoD > 80%) cause ~2x more wear than shallow cycles
 * - Charging to 100% causes more stress than stopping at 80%
 * - Discharging below 20% causes more stress than staying above
 * 
 * TEMPERATURE ADJUSTMENT:
 * - Cycling at high temps (>35°C) causes ~1.5x wear
 * - Cycling at low temps (<10°C) causes ~1.2x wear
 */
public class CycleCounter {

    private static final String TAG = "CycleCounter";

    // =========================================================================
    // DEPTH OF DISCHARGE THRESHOLDS
    // =========================================================================
    
    /** Full cycle depth threshold */
    public static final float DOD_FULL_CYCLE = 80.0f;
    
    /** Deep cycle threshold */
    public static final float DOD_DEEP_CYCLE = 60.0f;
    
    /** Standard cycle threshold */
    public static final float DOD_STANDARD_CYCLE = 30.0f;
    
    /** Minimum DoD to count as any cycle */
    public static final float DOD_MINIMUM = 5.0f;

    // =========================================================================
    // WEAR MULTIPLIERS
    // =========================================================================
    
    /** Wear multiplier for deep discharge (<20% SOC) */
    public static final float WEAR_DEEP_DISCHARGE = 1.3f;
    
    /** Wear multiplier for full charge (100%) */
    public static final float WEAR_FULL_CHARGE = 1.1f;
    
    /** Wear multiplier for high temp cycling */
    public static final float WEAR_HIGH_TEMP = 1.5f;
    
    /** Wear multiplier for low temp cycling */
    public static final float WEAR_LOW_TEMP = 1.2f;

    // =========================================================================
    // CYCLE STATE
    // =========================================================================
    
    private float designCapacityMah = 4000.0f;
    private long totalChargeMah = 0;
    private long totalDischargeMah = 0;
    private float partialCycleAccumulator = 0;
    private int fullCycleCount = 0;
    private int deepCycleCount = 0;
    private int topOffCycleCount = 0;
    private float wearAdjustedCycles = 0;
    
    // Current cycle tracking
    private float currentCycleStartLevel = -1;
    private float currentCycleMinLevel = 100;
    private float currentCycleMaxLevel = 0;
    private boolean isCurrentlyCharging = false;
    private float currentCycleAvgTemp = 25;
    private int tempSamples = 0;

    // =========================================================================
    // CONSTRUCTOR
    // =========================================================================
    
    public CycleCounter(float designCapacityMah) {
        this.designCapacityMah = designCapacityMah > 0 ? designCapacityMah : 4000.0f;
    }
    
    public CycleCounter() {
        this(4000.0f);
    }

    // =========================================================================
    // STATE RESTORATION
    // =========================================================================
    
    /**
     * Restore counter state from persisted values
     */
    public void restoreState(long totalChargeMah, long totalDischargeMah, 
                             float partialAccumulator, int fullCycles,
                             int deepCycles, int topOffCycles, float wearAdjusted) {
        this.totalChargeMah = totalChargeMah;
        this.totalDischargeMah = totalDischargeMah;
        this.partialCycleAccumulator = partialAccumulator;
        this.fullCycleCount = fullCycles;
        this.deepCycleCount = deepCycles;
        this.topOffCycleCount = topOffCycles;
        this.wearAdjustedCycles = wearAdjusted;
    }

    // =========================================================================
    // CORE CYCLE TRACKING
    // =========================================================================

    /**
     * Update cycle counter with new battery level reading
     * 
     * @param batteryLevel Current battery level (0-100)
     * @param isCharging Whether the device is currently charging
     * @param temperatureC Current battery temperature
     * @param energyDeltaMah Energy change since last reading (positive = charged, negative = discharged)
     * @return CycleUpdateResult with updated counts
     */
    public CycleUpdateResult update(float batteryLevel, boolean isCharging, 
                                     float temperatureC, float energyDeltaMah) {
        CycleUpdateResult result = new CycleUpdateResult();
        
        // Track temperature for this cycle
        updateCycleTemperature(temperatureC);
        
        // Track charge/discharge energy
        if (energyDeltaMah > 0) {
            totalChargeMah += (long) energyDeltaMah;
        } else if (energyDeltaMah < 0) {
            totalDischargeMah += (long) Math.abs(energyDeltaMah);
        }
        
        // Detect charging state change
        boolean stateChanged = (isCharging != isCurrentlyCharging);
        
        if (stateChanged) {
            if (isCharging) {
                // Started charging - end of discharge phase
                result = completeDischargeCycle(batteryLevel);
            } else {
                // Started discharging - end of charge phase
                result = completeChargeCycle(batteryLevel);
            }
        }
        
        // Update tracking
        isCurrentlyCharging = isCharging;
        
        if (isCharging) {
            currentCycleMaxLevel = Math.max(currentCycleMaxLevel, batteryLevel);
        } else {
            currentCycleMinLevel = Math.min(currentCycleMinLevel, batteryLevel);
        }
        
        // Update result with current totals
        result.totalCycles = getEquivalentCycleCount();
        result.fullCycles = fullCycleCount;
        result.deepCycles = deepCycleCount;
        result.topOffCycles = topOffCycleCount;
        result.partialAccumulator = partialCycleAccumulator;
        result.wearAdjustedCycles = wearAdjustedCycles;
        
        return result;
    }

    /**
     * Complete a discharge cycle (user plugged in)
     */
    private CycleUpdateResult completeDischargeCycle(float currentLevel) {
        CycleUpdateResult result = new CycleUpdateResult();
        
        if (currentCycleStartLevel < 0) {
            // No valid start level, just initialize
            currentCycleStartLevel = currentLevel;
            resetCycleTracking(currentLevel);
            return result;
        }
        
        // Calculate depth of discharge
        float depthOfDischarge = currentCycleStartLevel - currentCycleMinLevel;
        
        if (depthOfDischarge < DOD_MINIMUM) {
            // Too small to count
            resetCycleTracking(currentLevel);
            return result;
        }
        
        // Calculate wear-adjusted DoD
        float wearMultiplier = calculateWearMultiplier(currentCycleMinLevel, currentCycleMaxLevel, 
                                                        currentCycleAvgTemp);
        float wearAdjustedDoD = depthOfDischarge * wearMultiplier;
        
        // Accumulate partial cycle
        float cycleEquivalent = depthOfDischarge / 100.0f;
        partialCycleAccumulator += cycleEquivalent;
        
        // Accumulate wear-adjusted cycles
        wearAdjustedCycles += (wearAdjustedDoD / 100.0f);
        
        // Categorize cycle type
        if (depthOfDischarge >= DOD_FULL_CYCLE) {
            fullCycleCount++;
            deepCycleCount++;
            result.cycleType = CycleType.FULL;
        } else if (depthOfDischarge >= DOD_DEEP_CYCLE) {
            deepCycleCount++;
            result.cycleType = CycleType.DEEP;
        } else if (depthOfDischarge >= DOD_STANDARD_CYCLE) {
            result.cycleType = CycleType.STANDARD;
        } else {
            topOffCycleCount++;
            result.cycleType = CycleType.TOPOFF;
        }
        
        // Roll over accumulator if >= 1 full cycle equivalent
        if (partialCycleAccumulator >= 1.0f) {
            int rollovers = (int) partialCycleAccumulator;
            fullCycleCount += rollovers;
            partialCycleAccumulator -= rollovers;
        }
        
        result.depthOfDischarge = depthOfDischarge;
        result.wearMultiplier = wearMultiplier;
        result.cycleCompleted = true;
        
        Log.d(TAG, String.format("Discharge cycle: DoD=%.1f%%, Type=%s, Wear=%.2fx", 
                depthOfDischarge, result.cycleType, wearMultiplier));
        
        // Reset for next cycle
        resetCycleTracking(currentLevel);
        
        return result;
    }

    /**
     * Complete a charge cycle (user unplugged)
     */
    private CycleUpdateResult completeChargeCycle(float currentLevel) {
        CycleUpdateResult result = new CycleUpdateResult();
        
        // Start tracking discharge from current level
        currentCycleStartLevel = currentLevel;
        currentCycleMinLevel = currentLevel;
        currentCycleMaxLevel = currentLevel;
        
        // Check if charged to 100%
        if (currentLevel >= 99) {
            result.chargedToFull = true;
        }
        
        return result;
    }

    /**
     * Calculate wear multiplier based on cycle conditions
     */
    private float calculateWearMultiplier(float minLevel, float maxLevel, float avgTemp) {
        float multiplier = 1.0f;
        
        // Deep discharge wear
        if (minLevel < 20) {
            multiplier *= WEAR_DEEP_DISCHARGE;
        }
        
        // Full charge wear
        if (maxLevel >= 99) {
            multiplier *= WEAR_FULL_CHARGE;
        }
        
        // Temperature wear
        if (avgTemp > 35) {
            multiplier *= WEAR_HIGH_TEMP;
        } else if (avgTemp < 10) {
            multiplier *= WEAR_LOW_TEMP;
        }
        
        return multiplier;
    }

    /**
     * Update running average temperature for current cycle
     */
    private void updateCycleTemperature(float temperatureC) {
        tempSamples++;
        currentCycleAvgTemp = currentCycleAvgTemp + 
            (temperatureC - currentCycleAvgTemp) / tempSamples;
    }

    /**
     * Reset tracking for a new cycle
     */
    private void resetCycleTracking(float currentLevel) {
        currentCycleStartLevel = currentLevel;
        currentCycleMinLevel = currentLevel;
        currentCycleMaxLevel = currentLevel;
        currentCycleAvgTemp = 25;
        tempSamples = 0;
    }

    // =========================================================================
    // COULOMB COUNTING
    // =========================================================================

    /**
     * Update with direct coulomb measurement
     * 
     * @param chargeCounterUah BATTERY_PROPERTY_CHARGE_COUNTER value (μAh)
     */
    public void updateFromChargeCounter(long chargeCounterUah) {
        // This provides accumulated charge data from the fuel gauge
        // Can be used to calibrate our tracking
        long measuredMah = chargeCounterUah / 1000;
        
        // If significantly different from our tracking, may need calibration
        Log.d(TAG, "Charge counter reading: " + measuredMah + " mAh");
    }

    /**
     * Calculate energy delta from level change
     * 
     * @param previousLevel Previous battery level (0-100)
     * @param currentLevel Current battery level (0-100)
     * @return Energy change in mAh (positive = charged, negative = discharged)
     */
    public float calculateEnergyDelta(float previousLevel, float currentLevel) {
        float levelDelta = currentLevel - previousLevel;
        // Convert percentage to mAh based on design capacity
        return (levelDelta / 100.0f) * designCapacityMah;
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    /**
     * Get equivalent full cycle count
     * Formula: TotalDischarge / DesignCapacity
     */
    public float getEquivalentCycleCount() {
        if (designCapacityMah <= 0) return 0;
        return totalDischargeMah / designCapacityMah;
    }

    /**
     * Get discrete full cycle count
     */
    public int getFullCycleCount() {
        return fullCycleCount;
    }

    /**
     * Get deep cycle count (DoD >= 60%)
     */
    public int getDeepCycleCount() {
        return deepCycleCount;
    }

    /**
     * Get top-off cycle count (DoD < 30%)
     */
    public int getTopOffCycleCount() {
        return topOffCycleCount;
    }

    /**
     * Get partial cycle accumulator (0.0 - 0.99)
     */
    public float getPartialCycleAccumulator() {
        return partialCycleAccumulator;
    }

    /**
     * Get wear-adjusted cycle count
     */
    public float getWearAdjustedCycles() {
        return wearAdjustedCycles;
    }

    /**
     * Get total energy charged (mAh)
     */
    public long getTotalChargeMah() {
        return totalChargeMah;
    }

    /**
     * Get total energy discharged (mAh)
     */
    public long getTotalDischargeMah() {
        return totalDischargeMah;
    }

    /**
     * Get design capacity
     */
    public float getDesignCapacityMah() {
        return designCapacityMah;
    }

    /**
     * Set design capacity (for calibration)
     */
    public void setDesignCapacityMah(float capacityMah) {
        if (capacityMah > 0) {
            this.designCapacityMah = capacityMah;
        }
    }

    /**
     * Calculate cycling health ratio
     * Compares wear-adjusted cycles to raw cycles
     * >1.0 means more wear than expected, <1.0 means less wear
     */
    public float getCyclingHealthRatio() {
        float rawCycles = getEquivalentCycleCount();
        if (rawCycles < 0.1f) return 1.0f; // Not enough data
        return wearAdjustedCycles / rawCycles;
    }

    // =========================================================================
    // CYCLE STATISTICS
    // =========================================================================

    /**
     * Get comprehensive cycle statistics
     */
    public CycleStatistics getStatistics() {
        CycleStatistics stats = new CycleStatistics();
        
        stats.equivalentCycles = getEquivalentCycleCount();
        stats.fullCycles = fullCycleCount;
        stats.deepCycles = deepCycleCount;
        stats.topOffCycles = topOffCycleCount;
        stats.partialAccumulator = partialCycleAccumulator;
        stats.wearAdjustedCycles = wearAdjustedCycles;
        stats.totalChargeMah = totalChargeMah;
        stats.totalDischargeMah = totalDischargeMah;
        stats.cyclingHealthRatio = getCyclingHealthRatio();
        
        // Calculate average DoD
        int totalCycleEvents = fullCycleCount + deepCycleCount + topOffCycleCount;
        if (totalCycleEvents > 0) {
            stats.avgDepthOfDischarge = (stats.equivalentCycles / totalCycleEvents) * 100;
        }
        
        // Calculate deep cycle ratio (indicator of usage pattern)
        if (stats.equivalentCycles > 0.1f) {
            stats.deepCycleRatio = (float) deepCycleCount / stats.equivalentCycles;
        }
        
        return stats;
    }

    /**
     * Get human-readable summary
     */
    public String getSummary() {
        CycleStatistics stats = getStatistics();
        return String.format(
            "Cycles: %.1f (Full: %d, Deep: %d, TopOff: %d)\n" +
            "Wear-adjusted: %.1f (Health ratio: %.2f)\n" +
            "Total: %.0f mAh charged, %.0f mAh discharged",
            stats.equivalentCycles, stats.fullCycles, stats.deepCycles, stats.topOffCycles,
            stats.wearAdjustedCycles, stats.cyclingHealthRatio,
            (float) stats.totalChargeMah, (float) stats.totalDischargeMah
        );
    }

    // =========================================================================
    // DATA CLASSES
    // =========================================================================

    public enum CycleType {
        NONE,
        TOPOFF,     // DoD < 30%
        STANDARD,   // DoD 30-60%
        DEEP,       // DoD 60-80%
        FULL        // DoD >= 80%
    }

    public static class CycleUpdateResult {
        public boolean cycleCompleted = false;
        public boolean chargedToFull = false;
        public CycleType cycleType = CycleType.NONE;
        public float depthOfDischarge = 0;
        public float wearMultiplier = 1.0f;
        public float totalCycles = 0;
        public int fullCycles = 0;
        public int deepCycles = 0;
        public int topOffCycles = 0;
        public float partialAccumulator = 0;
        public float wearAdjustedCycles = 0;
    }

    public static class CycleStatistics {
        public float equivalentCycles = 0;
        public int fullCycles = 0;
        public int deepCycles = 0;
        public int topOffCycles = 0;
        public float partialAccumulator = 0;
        public float wearAdjustedCycles = 0;
        public long totalChargeMah = 0;
        public long totalDischargeMah = 0;
        public float cyclingHealthRatio = 1.0f;
        public float avgDepthOfDischarge = 0;
        public float deepCycleRatio = 0;
    }
}
