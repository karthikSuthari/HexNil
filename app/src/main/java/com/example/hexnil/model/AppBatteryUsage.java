package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Tracks battery usage per app for drain analysis
 * Enables app-wise battery consumption comparison
 */
@Entity(tableName = "app_battery_usage")
public class AppBatteryUsage {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long timestamp;
    public long periodStart;
    public long periodEnd;
    
    // App identification
    public String packageName;
    public String appName;
    public boolean isSystemApp;
    public boolean isPreInstalled;
    public String appCategory;           // "social", "game", "utility", etc.
    
    // Usage metrics
    public long foregroundTime;          // Time in foreground (ms)
    public long backgroundTime;          // Time in background (ms)
    public float batteryPercentUsed;     // % of battery used
    public float drainPerHour;           // %/hour while active
    
    // Resource usage
    public float cpuTimeMs;              // CPU time consumed
    public long networkBytes;            // Network data used
    public int wakelockCount;            // Wakelocks acquired
    public long wakelockTime;            // Total wakelock time (ms)
    public long partialWakelockTime;     // Partial wakelock duration (ms)
    public int alarmWakeCount;           // Alarm wakeups
    public int networkWakeCount;         // Network wakeups
    public float wakelockDrainScore;     // Derived drain from wakelocks
    public int alarmCount;               // Alarms scheduled
    public int gpsRequests;              // GPS location requests
    
    // Background behavior
    public int backgroundWakes;          // Times woke device from background
    public int pushNotifications;        // Push notifications received
    public int backgroundServices;       // Background services running
    public boolean hasExcessiveBackground; // Flagged for excessive background
    
    // Comparison data
    public float categoryAverage;        // Average drain for this app category
    public float deviationFromAverage;   // How much above/below average
    public String efficiencyRating;      // "efficient", "normal", "inefficient", "problematic"
    
    // Analysis
    public String drainReasons;          // JSON array of drain contributors
    public String optimizationTips;      // Recommendations for this app
    public boolean needsRestriction;     // Should battery restriction be applied
    
    public AppBatteryUsage() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public float getTotalActiveTime() {
        return (foregroundTime + backgroundTime) / 3600000.0f; // hours
    }
    
    public boolean isProblematic() {
        return "problematic".equals(efficiencyRating) || hasExcessiveBackground;
    }
    
    public float getDrainScore() {
        // Higher score = worse battery efficiency
        float score = 0;
        score += batteryPercentUsed * 10;
        score += (wakelockTime / 60000.0f) * 0.5f;  // Wakelock minutes
        score += (partialWakelockTime / 60000.0f) * 0.3f;
        score += alarmWakeCount * 0.8f;
        score += networkWakeCount * 0.5f;
        score += backgroundWakes * 2;
        if (hasExcessiveBackground) score += 20;
        return Math.min(100, score);
    }
}
