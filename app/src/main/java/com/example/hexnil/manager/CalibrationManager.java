package com.example.hexnil.manager;

import android.content.Context;
import java.util.List;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.CalibrationData;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;

/**
 * Maintains calibration records to detect SOC drift and guide recalibration steps.
 * 
 * Tracks:
 * - Drift between reported and actual SOC
 * - Sudden drops in battery level
 * - Voltage at different SOC points for calibration curves
 * - Multiple SOC estimation methods for cross-validation
 */
public class CalibrationManager {

    private static final String TAG = "CalibrationManager";

    private final HexNilDatabase database;
    private int lastLevel = -1;
    private long lastTimestamp = 0L;
    private long lastPersistTimestamp = 0L;
    private int suddenDropCount = 0;
    private int sampleCount = 0;
    
    // EMA tracking for drift
    private float emaDrift = 0f;
    private float driftVariance = 0f;
    private float previousDrift = 0f;
    private static final float EMA_ALPHA = 0.2f;
    
    // Voltage checkpoints tracking
    private int voltageAt100 = 0;
    private int voltageAt80 = 0;
    private int voltageAt50 = 0;
    private int voltageAt20 = 0;
    private int voltageAt5 = 0;

    public CalibrationManager(Context context, HexNilDatabase database) {
        this.database = database;
    }

    public void recordCalibrationPoint(int reportedLevel, float estimatedActualLevel) {
        CalibrationData data = new CalibrationData();
        data.reportedLevel = reportedLevel;
        data.estimatedActualLevel = estimatedActualLevel;
        data.levelDrift = estimatedActualLevel - reportedLevel;
        data.driftExceeded = Math.abs(data.levelDrift) > 8f;
        data.socMismatch = Math.abs(data.levelDrift) > 5f;
        data.socAccuracy = 100 - Math.abs(data.levelDrift);
        data.driftDirection = data.levelDrift > 0 ? "under_reporting" : data.levelDrift < 0 ? "over_reporting" : "stable";
        data.needsCalibration = Math.abs(data.levelDrift) > 10;
        data.calibrationQuality = Math.max(0, 100 - Math.abs(data.levelDrift) * 2);
        data.correctedSoc = clamp(reportedLevel + data.levelDrift, 0f, 110f);
        data.correctionConfidence = Math.max(0, Math.min(100, data.socAccuracy));
        data.recommendationReason = data.needsCalibration ? "Drift exceeded threshold" : "Tracking";
        database.miscDao().insertCalibration(data);
    }

    public void processSnapshot(BatterySnapshot snapshot) {
        if (snapshot == null) return;

        if (lastTimestamp > 0 && lastLevel > 0) {
            int deltaLevel = lastLevel - snapshot.level;
            long deltaMs = Math.max(1, snapshot.timestamp - lastTimestamp);
            float deltaMinutes = deltaMs / 60000f;
            if (deltaLevel >= 5 && deltaMinutes <= 15f) {
                suddenDropCount += 1;
            }
        }
        
        // Track voltage checkpoints
        updateVoltageCheckpoints(snapshot);

        float designCapacity = resolveDesignCapacity();
        float estimatedActualLevel = estimateActualLevel(snapshot, designCapacity);
        float levelDrift = estimatedActualLevel - snapshot.level;
        
        // Update EMA drift
        if (emaDrift == 0f) {
            emaDrift = levelDrift;
        } else {
            emaDrift = EMA_ALPHA * levelDrift + (1 - EMA_ALPHA) * emaDrift;
        }
        
        // Update drift variance
        float driftDelta = levelDrift - emaDrift;
        driftVariance = EMA_ALPHA * (driftDelta * driftDelta) + (1 - EMA_ALPHA) * driftVariance;
        
        // Calculate drift velocity
        float driftVelocity = 0f;
        if (lastTimestamp > 0) {
            float hoursDelta = (snapshot.timestamp - lastTimestamp) / (1000f * 3600f);
            if (hoursDelta > 0) {
                driftVelocity = (levelDrift - previousDrift) / hoursDelta;
            }
        }
        previousDrift = levelDrift;

        boolean shouldPersist = (snapshot.timestamp - lastPersistTimestamp) > 30 * 60 * 1000
                || Math.abs(levelDrift) > 8
                || suddenDropCount > 0;

        if (shouldPersist) {
            CalibrationData latest = database.miscDao().getLatestCalibration();
            CalibrationData data = new CalibrationData();
            data.reportedLevel = snapshot.level;
            data.estimatedActualLevel = estimatedActualLevel;
            data.levelDrift = levelDrift;
            data.avgDrift = latest != null ? (float) ((latest.avgDrift + levelDrift) / 2.0) : levelDrift;
            data.maxDrift = latest != null ? Math.max(latest.maxDrift, Math.abs(levelDrift)) : Math.abs(levelDrift);
            data.driftDirection = levelDrift > 0 ? "under_reporting" : levelDrift < 0 ? "over_reporting" : "stable";
            data.driftExceeded = Math.abs(levelDrift) > 8f;
            data.socMismatch = Math.abs(levelDrift) > 5f;
            
            // New drift analysis fields
            data.driftVelocity = driftVelocity;
            data.emaDrift = emaDrift;
            data.driftVariance = driftVariance;
            data.driftTrend = determineDriftTrend(driftVelocity, emaDrift, latest);
            data.driftSeverity = determineDriftSeverity(levelDrift, emaDrift, driftVariance);
            
            // Sudden drop tracking
            data.hasSuddenDrops = suddenDropCount > 0;
            data.suddenDropCount = latest != null ? latest.suddenDropCount + suddenDropCount : suddenDropCount;
            data.avgDropSize = calculateAvgDropSize(latest, lastLevel - snapshot.level, suddenDropCount);
            data.worstDrop = latest != null ? Math.max(latest.worstDrop, Math.abs(lastLevel - snapshot.level)) 
                    : (suddenDropCount > 0 ? Math.abs(lastLevel - snapshot.level) : 0);
            
            // Cycle integrity
            data.lastFullCharge = snapshot.level >= 99 ? snapshot.timestamp : (latest != null ? latest.lastFullCharge : 0);
            data.lastLowBattery = snapshot.level <= 15 ? snapshot.timestamp : (latest != null ? latest.lastLowBattery : 0);
            data.lastDeepDischarge = snapshot.level <= 5 ? snapshot.timestamp : (latest != null ? latest.lastDeepDischarge : 0);
            data.fullChargeCount = latest != null ? latest.fullChargeCount + (snapshot.level >= 99 && !snapshot.isCharging ? 1 : 0) : (snapshot.level >= 99 && !snapshot.isCharging ? 1 : 0);
            data.deepDischargeCount = latest != null ? latest.deepDischargeCount + (snapshot.level <= 5 ? 1 : 0) : (snapshot.level <= 5 ? 1 : 0);
            data.daysSinceFullCycle = calculateDaysSinceFullCycle(data.lastFullCharge, data.lastDeepDischarge, snapshot.timestamp);
            
            // SOC estimation methods
            populateSocEstimationMethods(data, snapshot, designCapacity);
            
            // Voltage checkpoints
            data.voltageAt100 = voltageAt100;
            data.voltageAt80 = voltageAt80;
            data.voltageAt50 = voltageAt50;
            data.voltageAt20 = voltageAt20;
            data.voltageAt5 = voltageAt5;
            populateVoltageCorrelation(data, latest);
            
            // AI correction (placeholder for actual AI model)
            populateAiCorrection(data, snapshot, levelDrift, designCapacity);
            
            // Calibration quality and recommendations
            data.calibrationQuality = clamp(100f - (Math.abs(levelDrift) * 2f) - (suddenDropCount * 5f), 0f, 100f);
            data.isCalibrated = data.calibrationQuality >= 70 && !data.hasSuddenDrops;
            data.needsCalibration = Math.abs(levelDrift) > 8 || data.hasSuddenDrops;
            data.calibrationUrgency = determineCalibrationUrgency(data);
            data.calibrationRecommendation = buildCalibrationRecommendation(data);
            data.calibrationSchedule = data.needsCalibration ? "Run calibration this week" : "Recheck monthly";
            data.daysSinceCalibration = latest != null ? (int) ((snapshot.timestamp - latest.lastCalibration) / (24 * 60 * 60 * 1000)) : 0;
            data.lastCalibration = data.needsCalibration ? snapshot.timestamp : (latest != null ? latest.lastCalibration : snapshot.timestamp);
            data.healthImpactScore = Math.min(100f, Math.abs(levelDrift) * 0.5f + suddenDropCount * 2f);
            data.socAccuracy = clamp(100f - Math.abs(levelDrift), 0f, 100f);
            sampleCount++;
            data.correctedSoc = clamp(snapshot.level + levelDrift, 0f, 110f);
            data.correctionConfidence = computeConfidence(latest, data, sampleCount);
            data.recommendationReason = buildRecommendationReason(data);

            database.miscDao().insertCalibration(data);
            lastPersistTimestamp = snapshot.timestamp;
            suddenDropCount = 0;
        }

        lastLevel = snapshot.level;
        lastTimestamp = snapshot.timestamp;
    }
    
    /**
     * Track voltage at key SOC checkpoints
     */
    private void updateVoltageCheckpoints(BatterySnapshot snapshot) {
        if (snapshot.voltage <= 0 || snapshot.isCharging) return;
        
        int level = snapshot.level;
        int voltage = snapshot.voltage;
        
        // Update voltage checkpoints when within ±2% of target
        if (level >= 98 && level <= 100) {
            voltageAt100 = voltage;
        } else if (level >= 78 && level <= 82) {
            voltageAt80 = voltage;
        } else if (level >= 48 && level <= 52) {
            voltageAt50 = voltage;
        } else if (level >= 18 && level <= 22) {
            voltageAt20 = voltage;
        } else if (level >= 3 && level <= 7) {
            voltageAt5 = voltage;
        }
    }
    
    /**
     * Determine drift trend based on velocity and history
     * Returns: -1.0 = decreasing/improving, 0.0 = stable, 1.0 = increasing/worsening
     */
    private float determineDriftTrend(float velocity, float ema, CalibrationData latest) {
        if (Math.abs(velocity) < 0.1f && Math.abs(ema) < 3f) {
            return 0.0f; // stable
        } else if (velocity > 0.5f) {
            return Math.min(1.0f, velocity); // increasing
        } else if (velocity < -0.5f) {
            return Math.max(-1.0f, velocity); // decreasing
        } else if (latest != null && Math.abs(ema) > Math.abs(latest.emaDrift)) {
            return 0.5f; // worsening
        }
        return 0.0f;
    }
    
    /**
     * Determine drift severity classification
     */
    private String determineDriftSeverity(float drift, float ema, float variance) {
        float combinedDrift = Math.max(Math.abs(drift), Math.abs(ema));
        float varianceFactor = (float) Math.sqrt(variance);
        
        if (combinedDrift > 15f || varianceFactor > 5f) {
            return "severe";
        } else if (combinedDrift > 10f || varianceFactor > 3f) {
            return "moderate";
        } else if (combinedDrift > 5f) {
            return "mild";
        }
        return "none";
    }
    
    /**
     * Calculate average drop size
     */
    private float calculateAvgDropSize(CalibrationData latest, int currentDrop, int newDropCount) {
        if (latest == null || latest.suddenDropCount == 0) {
            return newDropCount > 0 ? Math.abs(currentDrop) : 0f;
        }
        
        float totalDrops = latest.avgDropSize * latest.suddenDropCount;
        if (newDropCount > 0) {
            totalDrops += Math.abs(currentDrop);
        }
        int totalCount = latest.suddenDropCount + newDropCount;
        return totalCount > 0 ? totalDrops / totalCount : 0f;
    }
    
    /**
     * Calculate days since last full calibration cycle
     */
    private int calculateDaysSinceFullCycle(long lastFullCharge, long lastDeepDischarge, long now) {
        if (lastFullCharge == 0 || lastDeepDischarge == 0) {
            return 0;
        }
        // Full cycle requires both full charge AND deep discharge
        long lastCycleTime = Math.min(lastFullCharge, lastDeepDischarge);
        return (int) ((now - lastCycleTime) / (24 * 60 * 60 * 1000));
    }
    
    /**
     * Populate SOC estimation methods for cross-validation
     */
    private void populateSocEstimationMethods(CalibrationData data, BatterySnapshot snapshot, float designCapacity) {
        // Method 1: Charge counter estimate
        if (snapshot.chargeCounter != null && snapshot.chargeCounter > 0 && designCapacity > 0) {
            float chargeCounterMah = snapshot.chargeCounter / 1000f;
            data.chargeCounterEstimate = clamp((chargeCounterMah / designCapacity) * 100f, 0f, 100f);
        }
        
        // Method 2: Voltage estimate (from voltage curve)
        if (snapshot.voltage > 0) {
            data.voltageEstimate = estimateSocFromVoltage(snapshot.voltage);
        }
        
        // Method 3: Energy estimate
        if (snapshot.energyCounter != null && snapshot.energyCounter > 0 && designCapacity > 0) {
            // Energy counter is in nWh, need nominal voltage to convert
            float nominalVoltage = 3.7f; // Average Li-ion voltage
            float energyWh = snapshot.energyCounter / 1_000_000_000f;
            float estimatedMah = (energyWh * 1000f) / nominalVoltage;
            data.energyEstimate = clamp((estimatedMah / designCapacity) * 100f, 0f, 100f);
        }
        
        // Method 4: Capacity estimate (reported level with drift correction)
        data.capacityEstimate = clamp(snapshot.level + data.levelDrift, 0f, 100f);
        
        // Calculate method agreement
        data.methodAgreement = calculateMethodAgreement(data);
        
        // Select primary method
        data.primaryMethod = selectPrimaryMethod(data);
    }
    
    /**
     * Estimate SOC from voltage using typical Li-ion discharge curve
     */
    private float estimateSocFromVoltage(int voltageMv) {
        // Typical Li-ion discharge curve (approximate)
        // 4200mV = 100%, 4000mV = 80%, 3700mV = 50%, 3500mV = 20%, 3300mV = 5%
        if (voltageMv >= 4200) return 100f;
        if (voltageMv >= 4000) return 80f + (voltageMv - 4000) * 0.1f;
        if (voltageMv >= 3700) return 50f + (voltageMv - 3700) * 0.1f;
        if (voltageMv >= 3500) return 20f + (voltageMv - 3500) * 0.15f;
        if (voltageMv >= 3300) return 5f + (voltageMv - 3300) * 0.075f;
        return Math.max(0f, (voltageMv - 3000) * 0.0167f);
    }
    
    /**
     * Calculate agreement between estimation methods (0-1)
     */
    private float calculateMethodAgreement(CalibrationData data) {
        float sum = 0;
        int count = 0;
        
        float[] estimates = new float[4];
        if (data.chargeCounterEstimate > 0) estimates[count++] = data.chargeCounterEstimate;
        if (data.voltageEstimate > 0) estimates[count++] = data.voltageEstimate;
        if (data.energyEstimate > 0) estimates[count++] = data.energyEstimate;
        if (data.capacityEstimate > 0) estimates[count++] = data.capacityEstimate;
        
        if (count < 2) return 0f;
        
        // Calculate mean
        for (int i = 0; i < count; i++) sum += estimates[i];
        float mean = sum / count;
        
        // Calculate variance
        float variance = 0;
        for (int i = 0; i < count; i++) {
            float diff = estimates[i] - mean;
            variance += diff * diff;
        }
        variance /= count;
        
        // Convert to agreement score (lower variance = higher agreement)
        // Max agreement when variance < 4 (std dev < 2%)
        float stdDev = (float) Math.sqrt(variance);
        return clamp(1f - (stdDev / 10f), 0f, 1f);
    }
    
    /**
     * Select the most reliable estimation method
     */
    private String selectPrimaryMethod(CalibrationData data) {
        // Priority: charge_counter > voltage > energy > capacity
        if (data.chargeCounterEstimate > 0) return "charge_counter";
        if (data.voltageEstimate > 0) return "voltage";
        if (data.energyEstimate > 0) return "energy";
        return "capacity";
    }
    
    /**
     * Populate voltage correlation analysis
     */
    private void populateVoltageCorrelation(CalibrationData data, CalibrationData latest) {
        // Calculate expected voltage range (typical Li-ion: 3300-4200mV)
        float expectedRange = 900f; // 4200 - 3300
        
        if (data.voltageAt100 > 0 && data.voltageAt5 > 0) {
            float actualRange = data.voltageAt100 - data.voltageAt5;
            data.voltageRangeRatio = actualRange / expectedRange;
            
            // Detect curve stretching/compression
            data.curveStretched = data.voltageRangeRatio > 1.1f;
            data.curveCompressed = data.voltageRangeRatio < 0.9f;
            
            // Calculate correlation with expected curve
            float correlation = 1f - Math.abs(data.voltageRangeRatio - 1f);
            data.voltageCorrelation = clamp(correlation, 0f, 1f);
        } else if (latest != null) {
            // Carry forward from previous
            data.voltageRangeRatio = latest.voltageRangeRatio;
            data.voltageCorrelation = latest.voltageCorrelation;
            data.curveStretched = latest.curveStretched;
            data.curveCompressed = latest.curveCompressed;
        }
    }
    
    /**
     * Populate AI correction fields
     */
    private void populateAiCorrection(CalibrationData data, BatterySnapshot snapshot, 
                                       float drift, float designCapacity) {
        // AI correction is enabled when we have sufficient confidence
        data.aiCorrectionEnabled = data.correctionConfidence >= 60f && 
                                    Math.abs(drift) <= 15f;
        
        if (data.aiCorrectionEnabled) {
            // Corrected level uses EMA drift for stability
            data.aiCorrectedLevel = (int) clamp(snapshot.level + emaDrift, 0f, 100f);
            data.aiConfidence = data.correctionConfidence / 100f;
        } else {
            data.aiCorrectedLevel = snapshot.level;
            data.aiConfidence = 0f;
        }
    }
    
    /**
     * Determine calibration urgency
     */
    private String determineCalibrationUrgency(CalibrationData data) {
        if (Math.abs(data.levelDrift) > 15f || "severe".equals(data.driftSeverity)) {
            return "high";
        } else if (Math.abs(data.levelDrift) > 10f || data.hasSuddenDrops) {
            return "medium";
        } else if (data.needsCalibration) {
            return "low";
        }
        return "none";
    }
    
    /**
     * Build detailed calibration recommendation
     */
    private String buildCalibrationRecommendation(CalibrationData data) {
        if (!data.needsCalibration) {
            return "Battery calibration is accurate. Continue normal usage.";
        }
        
        StringBuilder sb = new StringBuilder();
        
        if ("high".equals(data.calibrationUrgency)) {
            sb.append("URGENT: ");
        }
        
        if (data.hasSuddenDrops) {
            sb.append("Sudden battery drops detected. ");
        }
        
        if (Math.abs(data.levelDrift) > 10f) {
            sb.append("Significant SOC drift detected. ");
        }
        
        sb.append("Recommended: Discharge battery to 5% or lower, then charge uninterrupted to 100%.");
        
        if (data.daysSinceFullCycle > 30) {
            sb.append(" Last full cycle was ").append(data.daysSinceFullCycle).append(" days ago.");
        }
        
        return sb.toString();
    }

    private float resolveDesignCapacity() {
        BatteryHealth latest = database.batteryHealthDao().getLatest();
        if (latest != null && latest.designCapacityMah > 0) {
            return latest.designCapacityMah;
        }
        return 4000f;
    }

    private float estimateActualLevel(BatterySnapshot snapshot, float designCapacityMah) {
        if (snapshot.chargeCounter != null && snapshot.chargeCounter > 0 && designCapacityMah > 0) {
            float currentMah = snapshot.chargeCounter / 1000f;
            return clamp((currentMah / designCapacityMah) * 100f, 0f, 110f);
        }
        return snapshot.level;
    }

    private float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private float computeConfidence(CalibrationData latest, CalibrationData current, int samples) {
        float recency = 30f;
        float driftPenalty = Math.min(40f, Math.abs(current.levelDrift) * 2f);
        float dropPenalty = current.hasSuddenDrops ? Math.min(20f, current.suddenDropCount * 5f) : 0f;
        float sampleScore = Math.min(30f, samples * 3f);
        float trendBonus = latest != null && Math.abs(latest.levelDrift) < 5f ? 10f : 0f;
        return clamp(recency + sampleScore + trendBonus - driftPenalty - dropPenalty, 0f, 100f);
    }

    private String buildRecommendationReason(CalibrationData data) {
        StringBuilder sb = new StringBuilder();
        if (data.driftExceeded) sb.append("Drift>8% ");
        if (data.hasSuddenDrops) sb.append("Sudden drops ");
        if (data.socMismatch) sb.append("SOC mismatch");
        String reason = sb.toString().trim();
        return reason.isEmpty() ? "Monitoring" : reason;
    }

    public CalibrationData getLatestCalibration() {
        return database.miscDao().getLatestCalibration();
    }
}
