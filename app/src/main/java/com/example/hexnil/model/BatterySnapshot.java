package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Primary battery snapshot entity - captures point-in-time battery state
 * Used for historical analysis, trend detection, and reporting
 */
@Entity(tableName = "battery_snapshots")
public class BatterySnapshot {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    
    // Basic battery info
    public int level;                    // 0-100%
    public int status;                   // CHARGING, DISCHARGING, FULL, etc.
    public int health;                   // GOOD, OVERHEAT, DEAD, etc.
    public int plugged;                  // USB, AC, WIRELESS, etc.
    
    // Capacity & voltage
    public int voltage;                  // millivolts
    public int temperature;              // tenths of degree Celsius
    public long chargeCounter;           // microampere-hours (actual charge)
    public int currentNow;               // microamperes (instant current)
    public int currentAverage;           // microamperes (average current)
    public long energyCounter;           // nanowatt-hours
    
    // Calculated fields
    public float capacityMah;            // Estimated capacity in mAh
    public float designCapacityMah;      // Design capacity from manufacturer
    public float healthPercentage;       // Real battery health %
    public float chargeRate;             // mA charging rate
    public float dischargeRate;          // mA discharge rate
    
    // Screen state
    public boolean screenOn;
    public int screenBrightness;         // 0-255
    
    // Connectivity state
    public boolean wifiEnabled;
    public boolean mobileDataEnabled;
    public boolean bluetoothEnabled;
    public boolean gpsEnabled;
    public int signalStrength;           // dBm
    
    // System state
    public boolean isCharging;
    public boolean isFastCharging;
    public String chargingType;          // "slow", "normal", "fast", "super_fast"
    public boolean isDozeMode;
    public int cpuUsage;                 // percentage
    public int memoryUsage;              // percentage

    // New diagnostics
    public int cpuLoadTotal;             // total CPU load %
    public String topAppPackage;         // top package at sampling time
    public int gpuLoad;                  // GPU load % if available, else -1
    public String networkState;          // wifi/mobile/none
    public int chargerVendorId;          // USB vendor id if available
    public int chargerProductId;         // USB product id if available
    public String voltageSamplesJson;    // JSON array of voltage samples
    public String currentSamplesJson;    // JSON array of current samples
    
    // App context
    public String foregroundApp;
    public int runningApps;
    
    public BatterySnapshot() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public float getTemperatureCelsius() {
        return temperature / 10.0f;
    }
    
    public float getVoltageVolts() {
        return voltage / 1000.0f;
    }
    
    public float getCurrentAmps() {
        return currentNow / 1000000.0f;
    }
}
