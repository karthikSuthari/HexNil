package com.example.hexnil.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Records battery safety events and protection triggers
 * Critical for OEM safety compliance and abuse detection
 */
@Entity(tableName = "safety_events")
public class SafetyEvent {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    
    // Event classification
    public String eventType;             // "over_temp", "over_voltage", "over_current", 
                                         // "over_discharge", "swelling_risk", "abuse"
    public String severity;              // "warning", "critical", "emergency"
    public boolean wasProtectionTriggered; // Did system protection kick in
    
    // Trigger values
    public float triggerValue;           // Value that triggered event
    public float thresholdValue;         // Threshold that was exceeded
    public String measurementUnit;       // "°C", "mV", "mA", "%"
    
    // Context
    public int batteryLevel;
    public boolean wasCharging;
    public String chargingType;
    public String activity;              // What user was doing
    
    // Temperature events
    public float batteryTemperature;
    public float ambientTemperature;
    public boolean wasThermalShutdown;
    
    // Voltage events
    public float voltage;
    public float expectedVoltage;
    
    // Current events
    public float current;
    public float expectedCurrent;
    
    // Over-discharge
    public float lowestVoltage;
    public boolean wasEmergencyShutdown;
    
    // Abuse indicators
    public String abuseType;             // "rapid_charging", "deep_discharge", 
                                         // "high_temp_charging", "physical"
    public int abuseCount;               // Times this abuse type occurred
    public float abuseSeverity;          // How severe (0-1)
    
    // Swelling indicators
    public float voltageAnomaly;         // Unusual voltage behavior
    public float capacityAnomaly;        // Unusual capacity
    public float resistanceAnomaly;      // Unusual resistance
    public float swellingRiskScore;      // Combined risk score
    
    // Resolution
    public String protectionAction;      // What protection did
    public long resolutionTime;          // How long to resolve
    public boolean wasResolved;
    
    // Compliance data
    public boolean exceedsRegulatory;    // Exceeds regulatory limits
    public String regulatoryStandard;    // Which standard (IEC, UL, etc.)
    public boolean requiresReport;       // Should be reported
    
    // OEM data
    public String deviceModel;
    public String firmwareVersion;
    public String batteryPartNumber;

    // ML Infrastructure
    public String deviceFingerprint;     // Device SKU fingerprint for fleet analysis
    public Long mlPredictionId;          // FK to ml_predictions if ML-detected
    @ColumnInfo(defaultValue = "heuristic")
    public String detectionMethod;       // "heuristic", "ml_model", "hybrid"

    public SafetyEvent() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public boolean isCritical() {
        return "critical".equals(severity) || "emergency".equals(severity);
    }
    
    public boolean requiresImmediateAction() {
        return isCritical() || wasThermalShutdown || wasEmergencyShutdown;
    }
    
    public String getRiskLevel() {
        if ("emergency".equals(severity)) return "EXTREME";
        if ("critical".equals(severity)) return "HIGH";
        if ("warning".equals(severity)) return "MEDIUM";
        return "LOW";
    }
}
