package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Tracks individual charging sessions for analysis
 * Used for charging behavior validation, safety checks, and optimization
 */
@Entity(tableName = "charging_sessions")
public class ChargingSession {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long startTime;
    public long endTime;
    
    // Charge levels
    public int startLevel;               // % at start
    public int endLevel;                 // % at end
    public int chargedAmount;            // % charged
    
    // Charging characteristics
    public String chargingType;          // "slow", "normal", "fast", "super_fast"
    public int plugType;                 // USB, AC, WIRELESS
    public boolean isFastCharging;
    public boolean isOriginalCharger;    // OEM vs third-party detection
    
    // Voltage & current tracking
    public float maxVoltage;             // Max voltage during session
    public float minVoltage;             // Min voltage during session
    public float avgVoltage;             // Average voltage
    public float maxCurrent;             // Max charging current (mA)
    public float minCurrent;             // Min charging current (mA)
    public float avgCurrent;             // Average current (mA)
    
    // Temperature tracking
    public float startTemperature;       // °C at start
    public float maxTemperature;         // Max °C during charging
    public float endTemperature;         // °C at end
    public float avgTemperature;         // Average temperature
    
    // Anomalies
    public int interruptionCount;        // Times charging was interrupted
    public int overCurrentEvents;        // Times current exceeded safe limits
    public int overVoltageEvents;        // Times voltage exceeded safe limits
    public int overTempEvents;           // Times temp exceeded limits
    public int currentFluctuations;      // Significant current changes
    public int voltageFluctuations;      // Significant voltage changes
    
    // Calculated metrics
    public long chargingDuration;        // Total time in ms
    public float chargingSpeed;          // %/hour
    public float energyDelivered;        // mAh delivered
    public float chargingEfficiency;     // Energy in vs battery gain

    // Curve capture
    public String voltageCurveJson;      // JSON array of {t, v}
    public String currentCurveJson;      // JSON array of {t, c}
    public int curveSampleIntervalMs;    // Approx interval between samples
    public float curveStabilityScore;    // 0-100 stability based on interruptions/fluctuations

    // Interruptions
    public String interruptionTimestamps; // JSON array of interruption timestamps
    public String interruptionDurations;  // JSON array of milliseconds between interruptions
    
    // Safety verdict
    public String safetyStatus;          // "safe", "warning", "danger"
    public String safetyNotes;           // Detailed safety observations
    
    // Charger identification
    public String chargerFingerprint;    // Unique charger signature
    public String chargerQuality;        // "oem", "certified", "third_party", "low_quality"
    
    public ChargingSession() {
        this.startTime = System.currentTimeMillis();
        this.safetyStatus = "safe";
    }
    
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
}
