package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Records thermal events for battery heating intelligence
 * Correlates temperature with system activity for root cause analysis
 * 
 * OEM-Grade thermal monitoring with:
 * - Spike detection and sustained heat tracking
 * - Multi-factor correlation (charging, CPU, GPU, app)
 * - OEM threshold compliance checking
 * - Battery degradation risk assessment
 */
@Entity(tableName = "thermal_events",
        indices = {
            @Index(value = "timestamp"),
            @Index(value = "severity"),
            @Index(value = "eventType"),
            @Index(value = "isCharging"),
            @Index(value = "foregroundApp")
        })
public class ThermalEvent {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    public long duration;                // How long event lasted (ms)
    
    // =====================================================================
    // Temperature data
    // =====================================================================
    public float batteryTemp;            // Battery temperature °C
    public float cpuTemp;                // CPU temperature °C (if available)
    public float skinTemp;               // Skin/surface temperature °C (if available)
    public float ambientTemp;            // Ambient temperature estimate °C
    
    // =====================================================================
    // Event classification
    // =====================================================================
    public String eventType;             // "spike", "sustained_high", "threshold_violation"
    public String severity;              // "normal", "warning", "critical", "danger", "emergency"
    public float peakTemperature;        // Highest temp during event
    public float temperatureRise;        // Delta from baseline
    
    // =====================================================================
    // Spike detection (NEW)
    // =====================================================================
    public boolean isSpike;              // Was this a temperature spike?
    public float spikeRateOfChange;      // °C per minute during spike
    public float spikeMagnitude;         // Total temp rise during spike
    public long spikeStartTime;          // When spike started
    public float spikeStartTemp;         // Temperature at spike start
    public String spikeCause;            // Identified cause of spike
    
    // =====================================================================
    // Sustained heat detection (NEW)
    // =====================================================================
    public boolean isSustainedHeat;      // Is this sustained elevated temperature?
    public String sustainedState;        // "none", "building", "warning", "high", "critical"
    public long sustainedDuration;       // Duration above threshold
    public float sustainedAvgTemp;       // Average temp during sustained period
    public float sustainedThreshold;     // Threshold being exceeded
    
    // =====================================================================
    // Activity correlation
    // =====================================================================
    public String triggerActivity;       // What caused the event
    public String foregroundApp;         // App running during event
    public int cpuUsage;                 // CPU % during event
    public int gpuUsage;                 // GPU % during event (if available)
    public boolean isCharging;           // Was device charging
    public boolean screenOn;             // Was screen on
    public boolean isGaming;             // Gaming activity detected
    public boolean isCamera;             // Camera was active
    public boolean isNavigation;         // GPS/Navigation active
    
    // =====================================================================
    // Correlation analysis (NEW)
    // =====================================================================
    public String primaryCause;          // "charging", "cpu", "gpu", "app", "ambient", "combined"
    public float causeConfidence;        // 0-1 confidence in cause identification
    public float chargingContribution;   // 0-100 % contribution from charging
    public float cpuContribution;        // 0-100 % contribution from CPU
    public float gpuContribution;        // 0-100 % contribution from GPU
    public float appContribution;        // 0-100 % contribution from foreground app
    public String correlationNotes;      // Additional correlation analysis notes
    
    // =====================================================================
    // Charging context (NEW)
    // =====================================================================
    public float chargeCurrent;          // Charging current during event (mA)
    public float chargeVoltage;          // Charging voltage during event (mV)
    public String chargingType;          // "ac", "usb", "wireless", "fast"
    public boolean wasFastCharging;      // Was fast charging active?
    public float chargerPower;           // Charger power in watts
    
    // =====================================================================
    // Network activity
    // =====================================================================
    public boolean heavyNetworkActivity;
    public String networkType;           // "wifi", "5g", "4g", etc.
    
    // =====================================================================
    // Battery state during event
    // =====================================================================
    public int batteryLevel;
    public float currentDraw;            // Current draw in mA
    
    // =====================================================================
    // OEM threshold tracking (NEW)
    // =====================================================================
    public String thresholdLevel;        // "optimal", "normal", "elevated", "warning", "high", "critical", "danger"
    public float nextThreshold;          // Next threshold above current temp
    public float headroom;               // Degrees until next threshold
    public boolean passesOEMCompliance;  // Does this event pass OEM thermal requirements?
    public boolean requiresAction;       // Does this require user/system action?
    public boolean requiresEmergencyAction;  // Emergency action required?
    
    // =====================================================================
    // Degradation risk (NEW)
    // =====================================================================
    public float degradationRisk;        // 0-1 risk of battery degradation
    public float estimatedCycleImpact;   // Estimated impact on battery cycles
    public String degradationNotes;      // Notes about degradation risk
    
    // =====================================================================
    // Thermal zone data (for OEM analysis)
    // =====================================================================
    public String thermalZones;          // JSON of all thermal zone temps
    
    // =====================================================================
    // Thermal score (NEW)
    // =====================================================================
    public int thermalScore;             // 0-100 overall thermal health score
    public String thermalTrend;          // "cooling_fast", "cooling", "stable", "warming", "warming_fast", "spiking"
    public String thermalZone;           // "<35", "35-40", "40-45", ">45"
    
    // =====================================================================
    // Resolution
    // =====================================================================
    public String resolution;            // How event was resolved
    public long cooldownTime;            // Time to return to normal
    public float cooldownRate;           // °C per minute during cooldown
    
    // =====================================================================
    // Sample tracking (NEW)
    // =====================================================================
    public int sampleCount;              // Number of samples during event
    public String temperatureSamplesJson;// JSON array of temperature samples

    // ML Infrastructure
    public String deviceFingerprint;     // Device SKU fingerprint for fleet analysis
    public Integer isNormalForActivity;  // Null=unknown, 1=normal for detected activity, 0=abnormal
    public Long mlPredictionId;          // FK to ml_predictions if ML-detected

    public ThermalEvent() {
        this.timestamp = System.currentTimeMillis();
        this.severity = "normal";
        this.eventType = "spike";
        this.sustainedState = "none";
        this.primaryCause = "unknown";
        this.thresholdLevel = "normal";
        this.thermalTrend = "stable";
        this.thermalZone = "<35";
        // Default values for context fields that must not be NULL
        this.skinTemp = 0f; // 0 when not measured (many devices don't expose)
        this.screenOn = false; // Safe default
        this.isNavigation = false; // Safe default
    }
    
    public boolean isCritical() {
        return "critical".equals(severity) || "danger".equals(severity) || "emergency".equals(severity);
    }
    
    public boolean isChargeRelated() {
        return isCharging && peakTemperature > 40;
    }
    
    public boolean isLoadRelated() {
        return cpuUsage > 70 || isGaming || isCamera;
    }
    
    /**
     * Check if this event requires immediate attention.
     */
    public boolean requiresImmediateAttention() {
        return requiresEmergencyAction || "danger".equals(severity) || "emergency".equals(severity);
    }
    
    /**
     * Check if this event poses significant degradation risk.
     */
    public boolean hasHighDegradationRisk() {
        return degradationRisk > 0.5f || (isSustainedHeat && "critical".equals(sustainedState));
    }
    
    /**
     * Get the thermal zone for this event's peak temperature.
     */
    public String getThermalZoneForPeak() {
        if (peakTemperature < 35) return "<35";
        if (peakTemperature < 40) return "35-40";
        if (peakTemperature < 45) return "40-45";
        return ">45";
    }
    
    /**
     * Get severity level as numeric value (0-4).
     */
    public int getSeverityLevel() {
        if ("emergency".equals(severity)) return 4;
        if ("danger".equals(severity)) return 3;
        if ("critical".equals(severity)) return 2;
        if ("warning".equals(severity)) return 1;
        return 0;
    }
}
