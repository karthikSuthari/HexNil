package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Records thermal events for battery heating intelligence
 * Correlates temperature with system activity for root cause analysis
 */
@Entity(tableName = "thermal_events")
public class ThermalEvent {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    public long duration;                // How long event lasted (ms)
    
    // Temperature data
    public float batteryTemp;            // Battery temperature °C
    public float cpuTemp;                // CPU temperature °C (if available)
    public float skinTemp;               // Skin/surface temperature °C (if available)
    public float ambientTemp;            // Ambient temperature estimate °C
    
    // Event classification
    public String eventType;             // "spike", "sustained_high", "threshold_violation"
    public String severity;              // "normal", "warning", "critical"
    public float peakTemperature;        // Highest temp during event
    public float temperatureRise;        // Delta from baseline
    
    // Activity correlation
    public String triggerActivity;       // What caused the event
    public String foregroundApp;         // App running during event
    public int cpuUsage;                 // CPU % during event
    public int gpuUsage;                 // GPU % during event (if available)
    public boolean isCharging;           // Was device charging
    public boolean screenOn;             // Was screen on
    public boolean isGaming;             // Gaming activity detected
    public boolean isCamera;             // Camera was active
    public boolean isNavigation;         // GPS/Navigation active
    
    // Network activity
    public boolean heavyNetworkActivity;
    public String networkType;           // "wifi", "5g", "4g", etc.
    
    // Battery state during event
    public int batteryLevel;
    public float currentDraw;            // Current draw in mA
    
    // Thermal zone data (for OEM analysis)
    public String thermalZones;          // JSON of all thermal zone temps
    
    // Resolution
    public String resolution;            // How event was resolved
    public long cooldownTime;            // Time to return to normal
    
    public ThermalEvent() {
        this.timestamp = System.currentTimeMillis();
        this.severity = "normal";
    }
    
    public boolean isCritical() {
        return "critical".equals(severity);
    }
    
    public boolean isChargeRelated() {
        return isCharging && peakTemperature > 40;
    }
    
    public boolean isLoadRelated() {
        return cpuUsage > 70 || isGaming || isCamera;
    }
}
