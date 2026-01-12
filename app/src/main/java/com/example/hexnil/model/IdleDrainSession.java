package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Tracks idle/standby battery drain for power leakage detection
 * Used to validate Doze mode effectiveness and detect background issues
 */
@Entity(tableName = "idle_drain_sessions")
public class IdleDrainSession {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long startTime;
    public long endTime;
    
    // Drain metrics
    public int startLevel;
    public int endLevel;
    public float drainAmount;            // Total % drained
    public float drainPerHour;           // %/hour
    
    // Session classification
    public String sessionType;           // "light_doze", "deep_doze", "idle", "standby"
    public boolean wasScreenOff;
    public boolean wasUnplugged;
    
    // Doze analysis
    public boolean dozeActive;
    public float dozeEffectiveness;      // How well Doze worked (0-100%)
    public int dozeExits;                // Times exited Doze
    public String dozeExitReasons;       // JSON of exit reasons
    
    // Deep sleep analysis
    public float deepSleepPercentage;    // % of time in deep sleep
    public long deepSleepTime;           // Total deep sleep (ms)
    public long lightSleepTime;          // Total light sleep (ms)
    public long awakeTime;               // Total awake time (ms)
    public float deepSleepSuccessRate;   // deepSleepTime / idleTime
    public float dozeEffectivenessScore; // 0-100 effectiveness metric
    public String leakageClass;          // NORMAL / WARNING / LEAK
    
    // Background activity
    public int wakelockEvents;           // Wakelock acquisitions
    public long totalWakelockTime;       // Total wakelock held time
    public String topWakelockHolders;    // JSON of apps holding wakelocks
    public int alarmsFired;              // Alarms during idle
    public String alarmsDetails;         // Details of alarms
    
    // Network activity
    public int networkWakes;             // Network-caused wakeups
    public long networkActivity;         // Bytes transferred
    public String networkDetails;        // What caused network activity
    
    // Sync activity
    public int syncEvents;               // Sync adapter runs
    public String syncDetails;           // Which syncs ran
    
    // Service leakage
    public int backgroundServices;       // Services running
    public String leakingServices;       // Services causing drain
    public float serviceLeakageEstimate; // Estimated drain from services

    // Doze timing & wake reasons
    public long dozeEntryTime;           // Timestamp entering Doze
    public long dozeExitTime;            // Timestamp exiting Doze
    public int alarmWakeups;             // Number of alarm-caused wakeups
    public int networkWakeups;           // Number of network-caused wakeups
    
    // Benchmark comparison
    public float expectedDrain;          // Expected drain for this duration
    public float drainDelta;             // Actual - Expected
    public String benchmarkResult;       // "excellent", "good", "fair", "poor", "failing"
    
    // Anomalies
    public boolean hasLeakage;           // Power leakage detected
    public String leakageSources;        // What's causing leakage
    public String recommendations;       // How to fix issues
    
    public IdleDrainSession() {
        this.startTime = System.currentTimeMillis();
    }
    
    public long getDurationHours() {
        if (endTime == 0) return 0;
        return (endTime - startTime) / 3600000;
    }
    
    public boolean meetsBenchmark() {
        // Good standby is < 0.5%/hour
        return drainPerHour < 0.5f;
    }
    
    public String getGrade() {
        if (drainPerHour < 0.3f) return "A+";
        if (drainPerHour < 0.5f) return "A";
        if (drainPerHour < 1.0f) return "B";
        if (drainPerHour < 2.0f) return "C";
        if (drainPerHour < 3.0f) return "D";
        return "F";
    }
}
