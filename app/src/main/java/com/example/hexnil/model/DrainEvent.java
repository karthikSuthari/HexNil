package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Records battery drain events for root-cause analysis
 * Tracks app-wise and system-wise battery consumption
 * 
 * Enhanced with drain intelligence including:
 * - Screen state analysis (on/off/doze/bright/dim)
 * - Expected vs actual drain modeling
 * - Deep sleep effectiveness tracking
 * - Component-level drain attribution
 * - Per-app drain profiling
 * - System vs user app comparison
 * - Benchmark grading
 */
@Entity(tableName = "drain_events")
public class DrainEvent {
    
    @PrimaryKey(autoGenerate = true)
    public long id;
    
    public long startTime;
    public long endTime;
    
    // Drain metrics
    public int startLevel;               // Battery % at start
    public int endLevel;                 // Battery % at end
    public int drainAmount;              // % drained
    public float drainRatePerHour;       // %/hour
    
    // Context
    public boolean screenOn;
    public String screenState;           // "on", "off", "doze"
    public boolean isCharging;
    public boolean isIdle;
    
    // Drain classification
    public String drainType;             // "normal", "heavy", "idle", "standby"
    public String drainCategory;         // "app", "system", "network", "display", "unknown"
    public String primaryCause;          // Main drain cause
    
    // App attribution
    public String topDrainingApps;       // JSON array of top draining apps
    public String foregroundApp;         // Primary foreground app during period
    public int wakelockCount;            // Number of wakelocks held
    public String activeWakelocks;       // JSON of active wakelocks
    
    // Network drain
    public float networkDrainEstimate;   // Estimated % from network
    public String networkType;           // "5g", "4g", "wifi", "none"
    public long dataSent;                // Bytes sent
    public long dataReceived;            // Bytes received
    
    // Display drain
    public float displayDrainEstimate;   // Estimated % from display
    public int avgBrightness;            // Average brightness level
    public long screenOnTime;            // Screen on time in ms
    
    // CPU drain
    public float cpuDrainEstimate;       // Estimated % from CPU
    public int avgCpuUsage;              // Average CPU usage %
    
    // GPS drain
    public float gpsDrainEstimate;       // Estimated % from GPS
    public boolean gpsActive;
    
    // Analysis results
    public String analysisNotes;         // AI analysis notes
    public String recommendations;       // Recommendations for optimization
    
    // Anomaly flags
    public boolean isAnomalous;          // Higher than expected drain
    public float expectedDrain;          // What drain should have been
    public float anomalyScore;           // How anomalous (0-1)
    
    // ====================================================================================
    // DRAIN INTELLIGENCE - Enhanced screen state analysis
    // ====================================================================================
    
    // Extended screen state classification
    public String screenStateDetailed;        // "screen_on_bright", "screen_on_medium", "screen_on_dim", "screen_off", "doze"
    public String screenStateGrade;           // "A+", "A", "B", "C", "D", "F"
    public int brightnessCategory;            // 0=dim, 1=medium, 2=bright, 3=max
    
    // ====================================================================================
    // EXPECTED VS ACTUAL DRAIN MODEL
    // ====================================================================================
    
    // Expected drain calculation
    public float expectedDrainRate;           // Expected %/hr based on device state
    public float drainDelta;                  // Actual - Expected
    public float drainEfficiency;             // (Expected / Actual) * 100
    public String drainEfficiencyGrade;       // "EXCELLENT", "GOOD", "AVERAGE", "POOR"
    
    // Baseline comparison
    public float baselineScreenOnRate;        // EMA baseline for screen-on drain
    public float baselineScreenOffRate;       // EMA baseline for screen-off drain
    public float baselineDozeDrainRate;       // EMA baseline for doze drain
    public float vsBaselineDelta;             // Comparison to personal baseline
    
    // ====================================================================================
    // BENCHMARK COMPARISON
    // ====================================================================================
    
    // Industry benchmark grading
    public String benchmarkResult;            // "EXCELLENT", "GOOD", "AVERAGE", "POOR", "CRITICAL"
    public float benchmarkDelta;              // Delta from benchmark
    public float benchmarkDrainRate;          // Benchmark drain rate for this state
    public String benchmarkNotes;             // Explanation of benchmark comparison
    
    // ====================================================================================
    // DEEP SLEEP EFFECTIVENESS
    // ====================================================================================
    
    // Sleep analysis
    public float deepSleepEffectiveness;      // 0-100 effectiveness score
    public String deepSleepGrade;             // "EXCELLENT", "GOOD", "FAIR", "POOR"
    public boolean isDeepSleepActive;         // Was in deep sleep/doze
    public float deepSleepPercentage;         // % of time in deep sleep
    public long deepSleepTimeMs;              // Time in deep sleep (ms)
    public long lightSleepTimeMs;             // Time in light sleep (ms)
    
    // Idle drain quality
    public String idleDrainQuality;           // "OPTIMAL", "GOOD", "ACCEPTABLE", "SUBOPTIMAL"
    public String idleBenchmarkResult;        // Benchmark result for idle drain
    public float idleDrainScore;              // 0-100 score for idle drain
    
    // ====================================================================================
    // COMPONENT-LEVEL DRAIN ATTRIBUTION
    // ====================================================================================
    
    // Component percentage breakdown
    public float displayDrainPct;             // % of drain from display
    public float cpuDrainPct;                 // % of drain from CPU
    public float networkDrainPct;             // % of drain from network
    public float sensorDrainPct;              // % of drain from sensors
    public float otherDrainPct;               // % of drain from other sources
    public String primaryDrainComponent;      // "display", "cpu", "network", "sensors", "system"
    
    // Component breakdown JSON
    public String componentBreakdownJson;     // JSON of component drain attribution
    
    // ====================================================================================
    // PER-APP DRAIN PROFILING
    // ====================================================================================
    
    // App drain analysis
    public int totalAppsContributing;         // Number of apps contributing to drain
    public float topAppDrainPct;              // % of drain from top app
    public String topAppPackage;              // Package name of top draining app
    public String topAppName;                 // Display name of top draining app
    public boolean topAppIsSystem;            // Is top app a system app
    public float topAppEfficiency;            // Efficiency rating of top app
    
    // App drain breakdown JSON
    public String appDrainBreakdownJson;      // JSON of per-app drain breakdown
    public String worstOffendersJson;         // JSON of worst offending apps
    
    // ====================================================================================
    // SYSTEM VS USER APP COMPARISON
    // ====================================================================================
    
    // System vs user drain
    public float systemAppDrain;              // Total drain from system apps (%)
    public float userAppDrain;                // Total drain from user apps (%)
    public float systemAppDrainPct;           // % of total from system apps
    public float userAppDrainPct;             // % of total from user apps
    public float unattributedDrain;           // Drain not attributed to any app
    public float unattributedDrainPct;        // % of drain unattributed
    
    // System vs user analysis
    public String systemVsUserAnalysis;       // Analysis notes
    public String drainDominant;              // "system", "user", "balanced"
    
    // ====================================================================================
    // ANOMALY DETECTION ENHANCED
    // ====================================================================================
    
    // Enhanced anomaly detection
    public String anomalySeverity;            // "NORMAL", "MILD", "MODERATE", "SEVERE", "CRITICAL"
    public int consecutiveAnomalies;          // Count of consecutive anomalous events
    public String anomalyReason;              // Explanation of why anomalous
    public boolean isPatternAnomaly;          // Part of anomalous pattern
    
    // ====================================================================================
    // DRAIN TREND TRACKING
    // ====================================================================================
    
    // Trend information
    public String trendDirection;             // "IMPROVING", "STABLE", "WORSENING"
    public float trendChangePercent;          // % change in trend
    public float rollingAvgDrain;             // Rolling average drain rate
    public float peakDrainRate;               // Peak drain rate during event
    
    // ====================================================================================
    // THERMAL CORRELATION
    // ====================================================================================
    
    // Thermal impact
    public int avgTemperature;                // Average temperature during event
    public int maxTemperature;                // Maximum temperature during event
    public float thermalDrainImpact;          // Estimated thermal impact on drain
    public boolean wasOverheating;            // Temperature exceeded safe threshold
    
    // ====================================================================================
    // QUALITY METRICS
    // ====================================================================================
    
    // Overall quality
    public float overallDrainScore;           // 0-100 overall drain quality score
    public String overallGrade;               // "A+", "A", "B", "C", "D", "F"
    public String qualitySummary;             // Brief summary of drain quality
    
    public DrainEvent() {
        this.startTime = System.currentTimeMillis();
    }
    
    public long getDurationMinutes() {
        if (endTime == 0) return 0;
        return (endTime - startTime) / 60000;
    }
    
    public float getDurationHours() {
        return getDurationMinutes() / 60.0f;
    }
    
    public boolean isExcessiveDrain() {
        if (screenOn) {
            return drainRatePerHour > 20; // >20%/hr with screen on is high
        } else {
            return drainRatePerHour > 3;  // >3%/hr idle is high
        }
    }
    
    /**
     * Get component with highest drain
     */
    public String getTopDrainComponent() {
        if (primaryDrainComponent != null) return primaryDrainComponent;
        
        float max = displayDrainPct;
        String top = "display";
        
        if (cpuDrainPct > max) {
            max = cpuDrainPct;
            top = "cpu";
        }
        if (networkDrainPct > max) {
            max = networkDrainPct;
            top = "network";
        }
        if (sensorDrainPct > max) {
            max = sensorDrainPct;
            top = "sensors";
        }
        
        return top;
    }
    
    /**
     * Check if this is an idle drain event
     */
    public boolean isIdleEvent() {
        return !screenOn && !isCharging && 
               ("screen_off".equals(screenState) || "doze".equals(screenState));
    }
    
    /**
     * Check if drain is within expected parameters
     */
    public boolean isWithinExpected() {
        if (expectedDrain <= 0) return true;
        return drainAmount <= expectedDrain * 1.2f; // Within 20% of expected
    }
    
    /**
     * Get efficiency percentage (100 = exactly as expected)
     */
    public float getEfficiencyPercentage() {
        if (drainEfficiency > 0) return drainEfficiency;
        if (drainAmount <= 0) return 100;
        if (expectedDrain <= 0) return 100;
        return (expectedDrain / drainAmount) * 100;
    }
    
    /**
     * Check if deep sleep was effective
     */
    public boolean hasEffectiveDeepSleep() {
        return deepSleepEffectiveness >= 60 || "EXCELLENT".equals(deepSleepGrade) || "GOOD".equals(deepSleepGrade);
    }
    
    /**
     * Get dominant drain source (system vs user apps)
     */
    public String getDominantSource() {
        if (drainDominant != null) return drainDominant;
        if (systemAppDrainPct > userAppDrainPct + 20) return "system";
        if (userAppDrainPct > systemAppDrainPct + 20) return "user";
        return "balanced";
    }
}
