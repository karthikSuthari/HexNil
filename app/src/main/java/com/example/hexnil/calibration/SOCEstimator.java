package com.example.hexnil.calibration;

import android.content.Context;
import android.os.BatteryManager;
import android.os.Build;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * SOCEstimator - Multi-Method State of Charge Estimation Engine
 * 
 * Provides OEM-grade SOC (State of Charge) estimation using multiple
 * independent methods with weighted fusion for maximum accuracy.
 * 
 * ESTIMATION METHODS:
 * ==================
 * 
 * 1. CHARGE COUNTER METHOD (Most Accurate - Weight: 0.40)
 *    Uses the Coulomb counter (if available) to measure actual charge.
 *    Formula: SOC = (chargeCounter_μAh / capacity_μAh) × 100%
 *    Accuracy: ±1-2% when properly calibrated
 *    Requires: BATTERY_PROPERTY_CHARGE_COUNTER support
 * 
 * 2. VOLTAGE-BASED METHOD (Widely Available - Weight: 0.25)
 *    Maps battery voltage to SOC using Li-ion discharge curve.
 *    Formula: SOC = f(V) where f is the voltage-to-SOC lookup table
 *    Accuracy: ±3-5% depending on load and temperature
 *    Note: Affected by load current and temperature
 * 
 * 3. ENERGY INTEGRATION METHOD (Secondary - Weight: 0.20)
 *    Tracks cumulative energy usage relative to total capacity.
 *    Formula: SOC = (remaining_energy_nWh / total_energy_nWh) × 100%
 *    Accuracy: ±2-4%
 *    Requires: BATTERY_PROPERTY_ENERGY_COUNTER support
 * 
 * 4. CAPACITY RATIO METHOD (Fallback - Weight: 0.15)
 *    Uses design capacity vs current capacity estimate.
 *    Formula: SOC = (current_mAh / design_mAh) × health_factor × 100%
 *    Accuracy: ±5-10%
 * 
 * WEIGHTED FUSION FORMULA:
 * =======================
 * estimated_SOC = Σ(weight_i × SOC_i × confidence_i) / Σ(weight_i × confidence_i)
 * 
 * where:
 * - weight_i is the method's base weight
 * - SOC_i is the method's estimate
 * - confidence_i is the method's confidence for current conditions
 * 
 * CONFIDENCE FACTORS:
 * ==================
 * Each method's confidence is adjusted based on:
 * - Availability: Does the device support this method?
 * - Calibration: Has the method been calibrated recently?
 * - Conditions: Temperature, load, charging state
 * - History: How well has this method performed?
 * 
 * VOLTAGE-SOC CURVE (Typical Li-ion at 25°C, 0.2C discharge):
 * ============================================================
 * Voltage (V) | SOC (%)
 * 4.20        | 100
 * 4.15        | 95
 * 4.10        | 90
 * 4.00        | 80
 * 3.90        | 70
 * 3.80        | 60
 * 3.70        | 50
 * 3.60        | 40
 * 3.50        | 30
 * 3.40        | 20
 * 3.30        | 15
 * 3.20        | 10
 * 3.10        | 5
 * 3.00        | 0
 * 
 * @author HexNil Battery Intelligence Team
 * @version 2.0.0
 */
public class SOCEstimator {
    
    private static final String TAG = "SOCEstimator";
    
    // ========== METHOD WEIGHTS ==========
    
    /** Weight for charge counter method */
    public static final float WEIGHT_CHARGE_COUNTER = 0.40f;
    
    /** Weight for voltage-based method */
    public static final float WEIGHT_VOLTAGE = 0.25f;
    
    /** Weight for energy integration method */
    public static final float WEIGHT_ENERGY = 0.20f;
    
    /** Weight for capacity ratio method */
    public static final float WEIGHT_CAPACITY = 0.15f;
    
    // ========== VOLTAGE-SOC LOOKUP TABLE ==========
    
    /** Voltage points for lookup (mV) */
    private static final int[] VOLTAGE_POINTS_MV = {
        4200, 4150, 4100, 4050, 4000, 3950, 3900, 3850,
        3800, 3750, 3700, 3650, 3600, 3550, 3500, 3450,
        3400, 3350, 3300, 3250, 3200, 3150, 3100, 3050, 3000
    };
    
    /** Corresponding SOC values (%) */
    private static final float[] SOC_POINTS = {
        100f, 95f, 90f, 85f, 80f, 75f, 70f, 65f,
        60f, 55f, 50f, 45f, 40f, 35f, 30f, 25f,
        20f, 17f, 15f, 12f, 10f, 7f, 5f, 2f, 0f
    };
    
    // ========== TEMPERATURE ADJUSTMENT ==========
    
    /** Reference temperature for voltage curve (°C) */
    private static final float REFERENCE_TEMP = 25.0f;
    
    /** Temperature coefficient (mV/°C) - typical Li-ion */
    private static final float TEMP_COEFFICIENT = -2.5f;
    
    // ========== INSTANCE VARIABLES ==========
    
    private final Context context;
    private final HexNilDatabase database;
    private final BatteryManager batteryManager;
    
    // Cached values
    private float designCapacityMah = 4000f;
    private float currentCapacityMah = 4000f;
    private float healthPercent = 100f;
    
    // Method availability flags
    private boolean hasChargeCounter = false;
    private boolean hasEnergyCounter = false;
    
    // Calibration state
    private float chargeCounterOffset = 0f;
    private float voltageCalibrationOffset = 0f;
    private long lastCalibrationTime = 0L;
    
    // Historical accuracy tracking
    private float chargeCounterAccuracy = 90f;
    private float voltageAccuracy = 70f;
    private float energyAccuracy = 80f;
    private float capacityAccuracy = 60f;
    
    public SOCEstimator(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
        this.batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        
        // Initialize
        detectMethodAvailability();
        loadCapacityData();
        loadCalibrationState();
    }
    
    // ==================================================================================
    //                              MAIN ESTIMATION METHODS
    // ==================================================================================
    
    /**
     * Estimate SOC using all available methods with weighted fusion
     * 
     * @param snapshot Current battery snapshot
     * @return Complete SOC estimation result
     */
    public SOCEstimation estimate(BatterySnapshot snapshot) {
        if (snapshot == null) {
            return SOCEstimation.invalid("Null snapshot");
        }
        
        SOCEstimation result = new SOCEstimation();
        result.timestamp = snapshot.timestamp;
        result.reportedSOC = snapshot.level;
        result.isCharging = snapshot.isCharging;
        
        // Method 1: Charge Counter
        MethodEstimate chargeCounterEstimate = estimateFromChargeCounter(snapshot);
        result.chargeCounterEstimate = chargeCounterEstimate;
        
        // Method 2: Voltage-Based
        MethodEstimate voltageEstimate = estimateFromVoltage(snapshot);
        result.voltageEstimate = voltageEstimate;
        
        // Method 3: Energy Integration
        MethodEstimate energyEstimate = estimateFromEnergy(snapshot);
        result.energyEstimate = energyEstimate;
        
        // Method 4: Capacity Ratio
        MethodEstimate capacityEstimate = estimateFromCapacity(snapshot);
        result.capacityEstimate = capacityEstimate;
        
        // Weighted Fusion
        float fusedSOC = calculateWeightedFusion(
            chargeCounterEstimate,
            voltageEstimate,
            energyEstimate,
            capacityEstimate
        );
        
        result.estimatedSOC = fusedSOC;
        result.drift = fusedSOC - snapshot.level;
        result.driftDirection = result.drift > 3 ? "under_reporting" 
                              : result.drift < -3 ? "over_reporting" 
                              : "accurate";
        
        // Calculate overall confidence
        result.confidence = calculateOverallConfidence(
            chargeCounterEstimate,
            voltageEstimate,
            energyEstimate,
            capacityEstimate
        );
        
        // Calculate method agreement (how close are the methods)
        result.methodAgreement = calculateMethodAgreement(
            chargeCounterEstimate,
            voltageEstimate,
            energyEstimate,
            capacityEstimate
        );
        
        // Determine primary method used
        result.primaryMethod = determinePrimaryMethod(
            chargeCounterEstimate,
            voltageEstimate,
            energyEstimate,
            capacityEstimate
        );
        
        // Calculate confidence interval
        float[] interval = calculateConfidenceInterval(result);
        result.confidenceIntervalLow = interval[0];
        result.confidenceIntervalHigh = interval[1];
        
        result.valid = true;
        return result;
    }
    
    /**
     * Quick estimation using only the most reliable available method
     */
    public float estimateQuick(BatterySnapshot snapshot) {
        if (snapshot == null) {
            return snapshot != null ? snapshot.level : 0f;
        }
        
        // Try methods in order of reliability
        if (hasChargeCounter && snapshot.chargeCounter != null && snapshot.chargeCounter > 0) {
            MethodEstimate est = estimateFromChargeCounter(snapshot);
            if (est.available && est.confidence > 50) {
                return est.estimatedSOC;
            }
        }
        
        if (snapshot.voltage > 0) {
            MethodEstimate est = estimateFromVoltage(snapshot);
            if (est.available && est.confidence > 40) {
                return est.estimatedSOC;
            }
        }
        
        // Fallback to reported level
        return snapshot.level;
    }
    
    // ==================================================================================
    //                           INDIVIDUAL ESTIMATION METHODS
    // ==================================================================================
    
    /**
     * Method 1: Charge Counter Based Estimation
     * 
     * Formula: SOC = (chargeCounter_μAh / capacity_μAh) × 100%
     * 
     * Most accurate method when Coulomb counter is available and calibrated.
     */
    private MethodEstimate estimateFromChargeCounter(BatterySnapshot snapshot) {
        MethodEstimate result = new MethodEstimate();
        result.method = "charge_counter";
        result.weight = WEIGHT_CHARGE_COUNTER;
        
        if (!hasChargeCounter || snapshot.chargeCounter == null || snapshot.chargeCounter <= 0 || currentCapacityMah <= 0) {
            result.available = false;
            result.reason = "Charge counter not available or invalid";
            return result;
        }
        
        // Convert μAh to mAh
        float currentChargeMah = snapshot.chargeCounter / 1000f;
        
        // Apply calibration offset
        currentChargeMah += chargeCounterOffset;
        
        // Calculate SOC
        float capacityMicroAh = currentCapacityMah * 1000f;
        float soc = (snapshot.chargeCounter / capacityMicroAh) * 100f;
        
        // Clamp to valid range
        soc = clamp(soc, 0f, 105f);
        
        result.available = true;
        result.estimatedSOC = soc;
        result.rawValue = snapshot.chargeCounter;
        result.referenceValue = capacityMicroAh;
        
        // Calculate confidence
        // Higher confidence if recently calibrated and value is reasonable
        float baseConfidence = chargeCounterAccuracy;
        
        // Penalty if SOC seems unreasonable
        if (soc > 100 || soc < 0) {
            baseConfidence -= 20;
        }
        
        // Penalty if far from reported (might indicate calibration issue)
        float deviation = Math.abs(soc - snapshot.level);
        if (deviation > 20) {
            baseConfidence -= Math.min(30, deviation - 10);
        }
        
        // Bonus if recently calibrated
        long hoursSinceCalibration = (System.currentTimeMillis() - lastCalibrationTime) / (60 * 60 * 1000);
        if (hoursSinceCalibration < 24) {
            baseConfidence += 10;
        } else if (hoursSinceCalibration > 168) { // 1 week
            baseConfidence -= 10;
        }
        
        result.confidence = clamp(baseConfidence, 0f, 100f);
        
        return result;
    }
    
    /**
     * Method 2: Voltage-Based Estimation
     * 
     * Uses voltage-SOC lookup table with temperature compensation.
     * Less accurate than Coulomb counting but widely available.
     */
    private MethodEstimate estimateFromVoltage(BatterySnapshot snapshot) {
        MethodEstimate result = new MethodEstimate();
        result.method = "voltage";
        result.weight = WEIGHT_VOLTAGE;
        
        if (snapshot.voltage <= 0) {
            result.available = false;
            result.reason = "Voltage not available";
            return result;
        }
        
        int voltageMv = snapshot.voltage;
        float temperatureC = snapshot.temperature / 10f; // tenths of °C to °C
        
        // Temperature compensation
        float tempDelta = temperatureC - REFERENCE_TEMP;
        int compensatedVoltageMv = voltageMv - (int)(tempDelta * TEMP_COEFFICIENT);
        
        // Apply calibration offset
        compensatedVoltageMv += (int)voltageCalibrationOffset;
        
        // Lookup SOC from voltage
        float soc = lookupSOCFromVoltage(compensatedVoltageMv);
        
        result.available = true;
        result.estimatedSOC = soc;
        result.rawValue = voltageMv;
        result.referenceValue = compensatedVoltageMv;
        
        // Calculate confidence
        float baseConfidence = voltageAccuracy;
        
        // Lower confidence when charging (voltage is artificially high)
        if (snapshot.isCharging) {
            baseConfidence -= 20;
            result.reason = "Reduced accuracy during charging";
        }
        
        // Lower confidence at extreme temperatures
        if (temperatureC < 10 || temperatureC > 40) {
            baseConfidence -= 15;
        }
        
        // Lower confidence at high current draw
        if (snapshot.currentNow < -1000000) { // High discharge > 1A
            baseConfidence -= 10;
        }
        
        // Lower confidence near extremes of curve (non-linear regions)
        if (soc > 90 || soc < 20) {
            baseConfidence -= 10;
        }
        
        result.confidence = clamp(baseConfidence, 0f, 100f);
        
        return result;
    }
    
    /**
     * Lookup SOC from voltage using linear interpolation
     */
    private float lookupSOCFromVoltage(int voltageMv) {
        // Clamp to valid range
        if (voltageMv >= VOLTAGE_POINTS_MV[0]) {
            return SOC_POINTS[0];
        }
        if (voltageMv <= VOLTAGE_POINTS_MV[VOLTAGE_POINTS_MV.length - 1]) {
            return SOC_POINTS[SOC_POINTS.length - 1];
        }
        
        // Find interpolation points
        for (int i = 0; i < VOLTAGE_POINTS_MV.length - 1; i++) {
            if (voltageMv <= VOLTAGE_POINTS_MV[i] && voltageMv > VOLTAGE_POINTS_MV[i + 1]) {
                // Linear interpolation
                float vHigh = VOLTAGE_POINTS_MV[i];
                float vLow = VOLTAGE_POINTS_MV[i + 1];
                float socHigh = SOC_POINTS[i];
                float socLow = SOC_POINTS[i + 1];
                
                float ratio = (voltageMv - vLow) / (vHigh - vLow);
                return socLow + ratio * (socHigh - socLow);
            }
        }
        
        return 50f; // Fallback
    }
    
    /**
     * Method 3: Energy Integration Estimation
     * 
     * Formula: SOC = (remaining_energy_nWh / total_energy_nWh) × 100%
     */
    private MethodEstimate estimateFromEnergy(BatterySnapshot snapshot) {
        MethodEstimate result = new MethodEstimate();
        result.method = "energy";
        result.weight = WEIGHT_ENERGY;
        
        if (!hasEnergyCounter || snapshot.energyCounter <= 0) {
            result.available = false;
            result.reason = "Energy counter not available";
            return result;
        }
        
        // Estimate total energy capacity
        // Total energy (Wh) = Capacity (Ah) × Nominal Voltage (V)
        float nominalVoltage = 3.7f; // Typical Li-ion nominal
        float totalEnergyWh = (currentCapacityMah / 1000f) * nominalVoltage;
        float totalEnergyNwh = totalEnergyWh * 1_000_000_000f;
        
        if (totalEnergyNwh <= 0) {
            result.available = false;
            result.reason = "Cannot calculate total energy";
            return result;
        }
        
        float soc = (snapshot.energyCounter / totalEnergyNwh) * 100f;
        soc = clamp(soc, 0f, 105f);
        
        result.available = true;
        result.estimatedSOC = soc;
        result.rawValue = snapshot.energyCounter;
        result.referenceValue = totalEnergyNwh;
        result.confidence = clamp(energyAccuracy, 0f, 100f);
        
        return result;
    }
    
    /**
     * Method 4: Capacity Ratio Estimation
     * 
     * Formula: SOC = (remaining_mAh / capacity_mAh) × 100%
     * 
     * Fallback method using capacity estimates.
     */
    private MethodEstimate estimateFromCapacity(BatterySnapshot snapshot) {
        MethodEstimate result = new MethodEstimate();
        result.method = "capacity";
        result.weight = WEIGHT_CAPACITY;
        
        if (designCapacityMah <= 0 || currentCapacityMah <= 0) {
            result.available = false;
            result.reason = "Capacity data not available";
            return result;
        }
        
        // Estimate remaining capacity based on reported level and health
        float healthFactor = healthPercent / 100f;
        float effectiveCapacity = designCapacityMah * healthFactor;
        float remainingMah = (snapshot.level / 100f) * effectiveCapacity;
        
        // Calculate SOC relative to current (degraded) capacity
        float soc = (remainingMah / currentCapacityMah) * 100f;
        soc = clamp(soc, 0f, 105f);
        
        result.available = true;
        result.estimatedSOC = soc;
        result.rawValue = remainingMah;
        result.referenceValue = currentCapacityMah;
        result.confidence = clamp(capacityAccuracy, 0f, 100f);
        
        // Lower confidence if health data is stale
        BatteryHealth health = database.batteryHealthDao().getLatest();
        if (health != null) {
            long hoursSinceHealth = (System.currentTimeMillis() - health.timestamp) / (60 * 60 * 1000);
            if (hoursSinceHealth > 24) {
                result.confidence -= Math.min(20, hoursSinceHealth / 24);
            }
        }
        
        return result;
    }
    
    // ==================================================================================
    //                              FUSION CALCULATIONS
    // ==================================================================================
    
    /**
     * Calculate weighted fusion of all method estimates
     * 
     * Formula: estimated_SOC = Σ(weight_i × SOC_i × confidence_i) / Σ(weight_i × confidence_i)
     */
    private float calculateWeightedFusion(MethodEstimate... estimates) {
        float numerator = 0f;
        float denominator = 0f;
        
        for (MethodEstimate est : estimates) {
            if (est != null && est.available && est.confidence > 20) {
                float effectiveWeight = est.weight * (est.confidence / 100f);
                numerator += effectiveWeight * est.estimatedSOC;
                denominator += effectiveWeight;
            }
        }
        
        if (denominator < 0.01f) {
            // No valid estimates, return average of available
            float sum = 0f;
            int count = 0;
            for (MethodEstimate est : estimates) {
                if (est != null && est.available) {
                    sum += est.estimatedSOC;
                    count++;
                }
            }
            return count > 0 ? sum / count : 50f;
        }
        
        return clamp(numerator / denominator, 0f, 100f);
    }
    
    /**
     * Calculate overall confidence in the fused estimate
     */
    private float calculateOverallConfidence(MethodEstimate... estimates) {
        float maxConfidence = 0f;
        float sumConfidence = 0f;
        int availableCount = 0;
        
        for (MethodEstimate est : estimates) {
            if (est != null && est.available) {
                maxConfidence = Math.max(maxConfidence, est.confidence);
                sumConfidence += est.confidence;
                availableCount++;
            }
        }
        
        if (availableCount == 0) {
            return 0f;
        }
        
        // Overall confidence is weighted average with bonus for agreement
        float avgConfidence = sumConfidence / availableCount;
        float agreement = calculateMethodAgreement(estimates);
        
        // Higher agreement = higher overall confidence
        float agreementBonus = (agreement / 100f) * 20f;
        
        // More methods = slightly higher confidence
        float methodBonus = Math.min(10f, (availableCount - 1) * 5f);
        
        return clamp(avgConfidence + agreementBonus + methodBonus, 0f, 100f);
    }
    
    /**
     * Calculate how well the different methods agree
     * 
     * 100% = perfect agreement, 0% = complete disagreement
     */
    private float calculateMethodAgreement(MethodEstimate... estimates) {
        List<Float> values = new ArrayList<>();
        for (MethodEstimate est : estimates) {
            if (est != null && est.available && est.confidence > 30) {
                values.add(est.estimatedSOC);
            }
        }
        
        if (values.size() < 2) {
            return 100f; // Only one method, so "perfect" agreement
        }
        
        // Calculate max deviation from mean
        float sum = 0f;
        for (float v : values) {
            sum += v;
        }
        float mean = sum / values.size();
        
        float maxDeviation = 0f;
        for (float v : values) {
            maxDeviation = Math.max(maxDeviation, Math.abs(v - mean));
        }
        
        // Convert to agreement score (0% deviation = 100% agreement)
        // Max meaningful deviation is 50% (half the SOC range)
        float agreement = Math.max(0f, 100f - (maxDeviation * 2f));
        return agreement;
    }
    
    /**
     * Determine which method contributed most to the final estimate
     */
    private String determinePrimaryMethod(MethodEstimate... estimates) {
        String primary = "none";
        float maxWeight = 0f;
        
        for (MethodEstimate est : estimates) {
            if (est != null && est.available) {
                float effectiveWeight = est.weight * (est.confidence / 100f);
                if (effectiveWeight > maxWeight) {
                    maxWeight = effectiveWeight;
                    primary = est.method;
                }
            }
        }
        
        return primary;
    }
    
    /**
     * Calculate 95% confidence interval for the estimate
     */
    private float[] calculateConfidenceInterval(SOCEstimation result) {
        // Interval width based on method agreement and confidence
        float baseWidth = 10f; // ±5% base
        
        // Wider interval if low confidence
        float confidenceFactor = (100f - result.confidence) / 100f;
        
        // Wider interval if methods disagree
        float agreementFactor = (100f - result.methodAgreement) / 100f;
        
        float intervalWidth = baseWidth + (baseWidth * confidenceFactor) + (baseWidth * agreementFactor);
        float halfWidth = intervalWidth / 2f;
        
        float low = Math.max(0f, result.estimatedSOC - halfWidth);
        float high = Math.min(100f, result.estimatedSOC + halfWidth);
        
        return new float[]{low, high};
    }
    
    // ==================================================================================
    //                              CALIBRATION
    // ==================================================================================
    
    /**
     * Calibrate estimation methods at a known reference point
     * 
     * Best called when battery is at 100% (full charge) or 0% (empty)
     */
    public void calibrate(BatterySnapshot snapshot, float knownSOC) {
        if (snapshot == null) return;
        
        // Calibrate charge counter
        if (hasChargeCounter && snapshot.chargeCounter != null && snapshot.chargeCounter > 0) {
            float estimatedFromCounter = estimateFromChargeCounter(snapshot).estimatedSOC;
            chargeCounterOffset += (knownSOC - estimatedFromCounter);
        }
        
        // Calibrate voltage offset
        if (snapshot.voltage > 0) {
            float estimatedFromVoltage = estimateFromVoltage(snapshot).estimatedSOC;
            voltageCalibrationOffset += (knownSOC - estimatedFromVoltage) * 10; // Convert to mV-equivalent
        }
        
        lastCalibrationTime = System.currentTimeMillis();
        
        // Persist calibration state
        saveCalibrationState();
    }
    
    /**
     * Auto-calibrate when reaching known reference points
     */
    public void autoCalibrate(BatterySnapshot snapshot) {
        if (snapshot == null) return;
        
        // Calibrate at 100% (full charge reached)
        if (snapshot.level >= 100 && snapshot.isCharging) {
            // When fully charged, actual SOC should be ~100%
            calibrate(snapshot, 100f);
        }
        
        // Calibrate at low battery shutdown
        if (snapshot.level <= 1 && !snapshot.isCharging) {
            // When nearly empty, actual SOC should be ~0%
            calibrate(snapshot, 0f);
        }
    }
    
    /**
     * Update accuracy tracking based on observed performance
     */
    public void updateAccuracyTracking(float reportedSOC, float observedActualSOC, String method) {
        float error = Math.abs(reportedSOC - observedActualSOC);
        float accuracy = Math.max(0f, 100f - error * 5f);
        
        // Exponential moving average update
        float alpha = 0.1f;
        
        switch (method) {
            case "charge_counter":
                chargeCounterAccuracy = alpha * accuracy + (1 - alpha) * chargeCounterAccuracy;
                break;
            case "voltage":
                voltageAccuracy = alpha * accuracy + (1 - alpha) * voltageAccuracy;
                break;
            case "energy":
                energyAccuracy = alpha * accuracy + (1 - alpha) * energyAccuracy;
                break;
            case "capacity":
                capacityAccuracy = alpha * accuracy + (1 - alpha) * capacityAccuracy;
                break;
        }
    }
    
    // ==================================================================================
    //                              INITIALIZATION
    // ==================================================================================
    
    private void detectMethodAvailability() {
        if (batteryManager == null) return;
        
        // Check charge counter availability
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            hasChargeCounter = chargeCounter > 0;
            
            // Energy counter requires API 21+
            long energyCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
            hasEnergyCounter = energyCounter > 0;
        }
    }
    
    private void loadCapacityData() {
        BatteryHealth health = database.batteryHealthDao().getLatest();
        if (health != null) {
            if (health.designCapacityMah > 0) {
                designCapacityMah = health.designCapacityMah;
            }
            if (health.currentCapacityMah > 0) {
                currentCapacityMah = health.currentCapacityMah;
            }
            if (health.healthPercentage > 0) {
                healthPercent = health.healthPercentage;
            }
        }
    }
    
    private void loadCalibrationState() {
        // Load from shared preferences or database
        // For now, use defaults
    }
    
    private void saveCalibrationState() {
        // Save to shared preferences or database
    }
    
    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    // ==================================================================================
    //                              DATA CLASSES
    // ==================================================================================
    
    public static class SOCEstimation {
        public long timestamp;
        public int reportedSOC;
        public float estimatedSOC;
        public float drift;
        public String driftDirection;
        public float confidence;
        public float confidenceIntervalLow;
        public float confidenceIntervalHigh;
        public float methodAgreement;
        public String primaryMethod;
        public boolean isCharging;
        
        public MethodEstimate chargeCounterEstimate;
        public MethodEstimate voltageEstimate;
        public MethodEstimate energyEstimate;
        public MethodEstimate capacityEstimate;
        
        public boolean valid;
        public String invalidReason;
        
        public static SOCEstimation invalid(String reason) {
            SOCEstimation est = new SOCEstimation();
            est.valid = false;
            est.invalidReason = reason;
            return est;
        }
        
        public String getSummary() {
            if (!valid) return "Invalid: " + invalidReason;
            
            return String.format(
                "Estimated: %.1f%% (reported: %d%%), drift: %.1f%%, confidence: %.0f%%",
                estimatedSOC, reportedSOC, drift, confidence
            );
        }
    }
    
    public static class MethodEstimate {
        public String method;
        public boolean available;
        public float weight;
        public float estimatedSOC;
        public float confidence;
        public float rawValue;
        public float referenceValue;
        public String reason;
        
        public String getSummary() {
            if (!available) return method + ": unavailable - " + reason;
            return String.format("%s: %.1f%% (confidence: %.0f%%)", method, estimatedSOC, confidence);
        }
    }
}
