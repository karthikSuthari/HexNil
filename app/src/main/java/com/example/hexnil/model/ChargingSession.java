package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * Tracks individual charging sessions for analysis.
 * Used for charging behavior validation, safety checks, and optimization.
 * 
 * OEM-grade charging session tracking with:
 * - Multi-phase charging analysis (CC/CV phases)
 * - Overnight charging detection
 * - Power quality metrics
 * - Battery degradation impact tracking
 * - Charger fingerprinting and classification
 * - Safety scoring and compliance checking
 */
@Entity(tableName = "charging_sessions")
public class ChargingSession {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long startTime;
    public long endTime;
    
    // ========================================================================
    // CHARGE LEVELS
    // ========================================================================
    
    public int startLevel;               // % at start
    public int endLevel;                 // % at end
    public int chargedAmount;            // % charged
    public int peakLevel;                // Highest level reached
    
    // ========================================================================
    // CHARGING CHARACTERISTICS
    // ========================================================================
    
    public String chargingType;          // "trickle", "slow", "normal", "fast", "super_fast", "ultra_fast"
    public int plugType;                 // USB, AC, WIRELESS
    public boolean isFastCharging;
    public boolean isOriginalCharger;    // OEM vs third-party detection
    public String chargingProtocol;      // "usb_pd", "qc3", "vooc", etc.
    
    // ========================================================================
    // VOLTAGE & CURRENT TRACKING
    // ========================================================================
    
    public float maxVoltage;             // Max voltage during session
    public float minVoltage;             // Min voltage during session
    public float avgVoltage;             // Average voltage
    public float maxCurrent;             // Max charging current (mA)
    public float minCurrent;             // Min charging current (mA)
    public float avgCurrent;             // Average current (mA)
    
    // ========================================================================
    // TEMPERATURE TRACKING
    // ========================================================================
    
    public float startTemperature;       // °C at start
    public float maxTemperature;         // Max °C during charging
    public float endTemperature;         // °C at end
    public float avgTemperature;         // Average temperature
    public float temperatureRise;        // Max temp - start temp
    
    // ========================================================================
    // ANOMALIES & EVENTS
    // ========================================================================
    
    public int interruptionCount;        // Times charging was interrupted
    public int overCurrentEvents;        // Times current exceeded safe limits
    public int overVoltageEvents;        // Times voltage exceeded safe limits
    public int overTempEvents;           // Times temp exceeded limits
    public int currentFluctuations;      // Significant current changes
    public int voltageFluctuations;      // Significant voltage changes
    
    // ========================================================================
    // CALCULATED METRICS
    // ========================================================================
    
    public long chargingDuration;        // Total time in ms
    public float chargingSpeed;          // %/hour
    public float energyDelivered;        // mAh delivered
    public float chargingEfficiency;     // Energy in vs battery gain (0-100%)
    public float powerWatts;             // Average power in Watts
    
    // ========================================================================
    // CHARGING PHASES (CC/CV Analysis)
    // ========================================================================
    
    public long ccPhaseDuration;         // Constant Current phase duration (ms)
    public long cvPhaseDuration;         // Constant Voltage phase duration (ms)
    public int ccPhaseEndLevel;          // Battery % when CV phase starts
    public float ccPhaseAvgCurrent;      // Average current during CC phase
    public float cvPhaseAvgCurrent;      // Average current during CV phase
    public float ccToCvTransitionVoltage;// Voltage at CC/CV transition
    public String currentPhase;          // "cc", "cv", "trickle", "complete"
    
    // ========================================================================
    // OVERNIGHT CHARGING DETECTION
    // ========================================================================
    
    public boolean isOvernightCharging;  // Duration > 6 hours
    public long timeAtFullCharge;        // Time spent at 100% (ms)
    public float percentAtFullCharge;    // % of session at 100%
    public boolean reachedFullCharge;    // Did session hit 100%?
    public int fullChargeHoldCount;      // Times held at 100%
    
    // ========================================================================
    // CURVE CAPTURE
    // ========================================================================
    
    public String voltageCurveJson;      // JSON array of {t, v}
    public String currentCurveJson;      // JSON array of {t, c}
    public String temperatureCurveJson;  // JSON array of {t, temp}
    public int curveSampleIntervalMs;    // Approx interval between samples
    public float curveStabilityScore;    // 0-100 stability based on interruptions/fluctuations
    public int curveSampleCount;         // Number of samples captured
    
    // ========================================================================
    // INTERRUPTIONS
    // ========================================================================
    
    public String interruptionTimestamps; // JSON array of interruption timestamps
    public String interruptionDurations;  // JSON array of milliseconds between interruptions
    public long longestInterruption;      // Longest single interruption (ms)
    public long totalInterruptionTime;    // Sum of all interruption durations
    
    // ========================================================================
    // SAFETY SCORING
    // ========================================================================
    
    public String safetyStatus;          // "safe", "warning", "danger"
    public String safetyNotes;           // Detailed safety observations
    public float safetyScore;            // 0-100 overall safety score
    public float temperatureScore;       // 0-100 temperature safety
    public float voltageScore;           // 0-100 voltage safety
    public float currentScore;           // 0-100 current safety
    public float stabilityScore;         // 0-100 stability score
    public boolean passedOEMCompliance;  // Passed OEM safety standards
    public String complianceNotes;       // OEM compliance details
    
    // ========================================================================
    // CHARGER IDENTIFICATION
    // ========================================================================
    
    public String chargerFingerprint;    // Unique charger signature (vendorId:productId)
    public String chargerQuality;        // "oem", "certified", "compatible", "questionable", "poor", "dangerous"
    public int chargerVendorId;          // USB vendor ID
    public int chargerProductId;         // USB product ID
    public String chargerClassification; // Full classification string
    public float chargerPowerCapacity;   // Estimated max power (W)
    
    // ========================================================================
    // BATTERY IMPACT
    // ========================================================================
    
    public float degradationImpact;      // Estimated % capacity loss from this session
    public float cycleEquivalent;        // Equivalent full cycle count
    public float stressLevel;            // 0-100 battery stress score
    public String degradationRiskLevel;  // "low", "moderate", "high"
    public String degradationNotes;      // Impact analysis notes
    
    // ========================================================================
    // SESSION CONTEXT
    // ========================================================================
    
    public String sessionContext;        // "manual", "scheduled", "overnight", "top_up"
    public boolean screenOnDuringCharge; // Was screen on during any part
    public String topAppDuringCharge;    // Most used app during charging
    public int samplesCollected;         // Total snapshots during session
    
    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================
    
    public ChargingSession() {
        this.startTime = System.currentTimeMillis();
        this.safetyStatus = "safe";
        this.currentPhase = "cc";
        this.chargerQuality = "unknown";
        this.degradationRiskLevel = "low";
        this.sessionContext = "manual";
    }
    
    // ========================================================================
    // HELPER METHODS
    // ========================================================================
    
    public long getDurationMinutes() {
        if (endTime == 0) return 0;
        return (endTime - startTime) / 60000;
    }
    
    public boolean isComplete() {
        return endTime > 0;
    }
    
    public float getChargingRate() {
        if (getDurationMinutes() == 0) return 0;
        return (chargedAmount * 60.0f) / getDurationMinutes();
    }
    
    /**
     * Check if this is an overnight charging session (>6 hours).
     */
    public boolean checkIsOvernightCharging() {
        long duration = endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime;
        return duration > 6 * 60 * 60 * 1000; // 6 hours
    }
    
    /**
     * Get estimated time to full charge in minutes.
     */
    @Ignore
    public int getEstimatedTimeToFull() {
        if (chargingSpeed <= 0 || endLevel >= 100) return 0;
        int remaining = 100 - endLevel;
        // Account for slowdown above 80%
        if (endLevel < 80) {
            float belowEighty = Math.min(80 - endLevel, remaining);
            float aboveEighty = remaining - belowEighty;
            float hours = (belowEighty / chargingSpeed) + (aboveEighty / (chargingSpeed * 0.6f));
            return (int) (hours * 60);
        }
        return (int) ((remaining / (chargingSpeed * 0.6f)) * 60);
    }
    
    /**
     * Get charging efficiency category.
     */
    @Ignore
    public String getEfficiencyCategory() {
        if (chargingEfficiency >= 90) return "excellent";
        if (chargingEfficiency >= 80) return "good";
        if (chargingEfficiency >= 70) return "acceptable";
        if (chargingEfficiency >= 60) return "poor";
        return "very_poor";
    }
    
    /**
     * Check if session had any safety events.
     */
    @Ignore
    public boolean hadSafetyEvents() {
        return overCurrentEvents > 0 || overVoltageEvents > 0 || overTempEvents > 0;
    }
    
    /**
     * Get total safety event count.
     */
    @Ignore
    public int getTotalSafetyEvents() {
        return overCurrentEvents + overVoltageEvents + overTempEvents;
    }
    
    /**
     * Check if CC phase is complete (transitioned to CV).
     */
    @Ignore
    public boolean hasCCPhaseCompleted() {
        return ccPhaseDuration > 0 && (cvPhaseDuration > 0 || "cv".equals(currentPhase));
    }
    
    /**
     * Get CC phase percentage of total duration.
     */
    @Ignore
    public float getCCPhasePercent() {
        if (chargingDuration == 0) return 0;
        return (ccPhaseDuration * 100f) / chargingDuration;
    }
}
