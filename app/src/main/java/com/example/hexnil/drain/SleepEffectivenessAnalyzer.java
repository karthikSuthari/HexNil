package com.example.hexnil.drain;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.IdleDrainSession;

import java.util.List;

/**
 * SleepEffectivenessAnalyzer - OEM-grade deep sleep and idle analysis engine.
 * 
 * Provides comprehensive analysis of device sleep behavior including:
 * - Deep sleep effectiveness measurement
 * - Doze mode analysis and optimization
 * - Wakelock impact assessment
 * - Sleep quality scoring
 * - Deep sleep vs light sleep tracking
 * - Wake reason categorization
 * - Sleep fragmentation analysis
 * - Overnight drain optimization
 * 
 * Uses statistical analysis and pattern recognition to provide
 * actionable insights for improving standby battery life.
 */
public class SleepEffectivenessAnalyzer {
    
    private static final String TAG = "SleepEffectiveness";
    
    // ====================================================================================
    // SLEEP QUALITY THRESHOLDS
    // ====================================================================================
    
    // Deep sleep time thresholds (percentage of total sleep time)
    public static final float DEEP_SLEEP_OPTIMAL = 80.0f;      // 80%+ in deep sleep = optimal
    public static final float DEEP_SLEEP_GOOD = 60.0f;         // 60%+ = good
    public static final float DEEP_SLEEP_ACCEPTABLE = 40.0f;   // 40%+ = acceptable
    public static final float DEEP_SLEEP_POOR = 20.0f;         // Below 20% = poor
    
    // Doze effectiveness thresholds
    public static final float DOZE_OPTIMAL = 90.0f;            // 90%+ doze effectiveness
    public static final float DOZE_GOOD = 75.0f;               // 75%+ = good
    public static final float DOZE_ACCEPTABLE = 50.0f;         // 50%+ = acceptable
    
    // Wakelock impact thresholds
    public static final int WAKELOCK_LOW = 3;                  // Under 3 wakelocks/hour = low impact
    public static final int WAKELOCK_MODERATE = 6;             // Under 6 = moderate
    public static final int WAKELOCK_HIGH = 12;                // Over 12 = high impact
    
    // Sleep fragmentation thresholds
    public static final int FRAGMENTATION_LOW = 2;             // Under 2 wakes/hour = low
    public static final int FRAGMENTATION_MODERATE = 5;        // Under 5 = moderate
    public static final int FRAGMENTATION_HIGH = 10;           // Over 10 = high
    
    // Overnight drain thresholds (%/hour)
    public static final float OVERNIGHT_OPTIMAL = 0.2f;        // Under 0.2%/hr = optimal
    public static final float OVERNIGHT_GOOD = 0.4f;           // Under 0.4%/hr = good
    public static final float OVERNIGHT_ACCEPTABLE = 0.8f;     // Under 0.8%/hr = acceptable
    public static final float OVERNIGHT_POOR = 1.5f;           // Over 1.5%/hr = poor
    
    // ====================================================================================
    // INSTANCE FIELDS
    // ====================================================================================
    
    private final Context context;
    private final HexNilDatabase database;
    private final PowerManager powerManager;
    
    // Sleep state tracking
    private long sleepStartTime = 0;
    private long deepSleepStartTime = 0;
    private long lightSleepStartTime = 0;
    private boolean isInSleep = false;
    private boolean isDeepSleep = false;
    
    // Cumulative tracking for current session
    private long totalDeepSleepMs = 0;
    private long totalLightSleepMs = 0;
    private long totalAwakeMs = 0;
    private int wakeCount = 0;
    private int wakelockCount = 0;
    private int alarmWakeCount = 0;
    private int networkWakeCount = 0;
    private int dozeExitCount = 0;
    
    // Historical baselines (EMA smoothed)
    private float emaDeepSleepRatio = 0.6f;        // Starting estimate 60%
    private float emaOvernightDrain = 0.5f;        // Starting estimate 0.5%/hr
    private float emaDozeEffectiveness = 70.0f;    // Starting estimate 70%
    private float emaSleepQualityScore = 70.0f;    // Starting estimate 70/100
    
    private static final float EMA_ALPHA = 0.15f;  // Smoothing factor
    
    // ====================================================================================
    // CONSTRUCTOR
    // ====================================================================================
    
    public SleepEffectivenessAnalyzer(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        loadHistoricalBaselines();
    }
    
    /**
     * Load historical data to calibrate baselines
     */
    private void loadHistoricalBaselines() {
        try {
            long weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L;
            
            // Load deep sleep average
            Float deepSleepAvg = database.idleDrainSessionDao().getAverageDeepSleep(weekAgo);
            if (deepSleepAvg != null && deepSleepAvg > 0) {
                emaDeepSleepRatio = deepSleepAvg / 100f;
            }
            
            // Load doze effectiveness average
            Float dozeAvg = database.idleDrainSessionDao().getAverageDozeEffectiveness(weekAgo);
            if (dozeAvg != null && dozeAvg > 0) {
                emaDozeEffectiveness = dozeAvg;
            }
            
            // Load overnight drain average
            Float drainAvg = database.idleDrainSessionDao().getAverageIdleDrain(weekAgo);
            if (drainAvg != null && drainAvg > 0) {
                emaOvernightDrain = drainAvg;
            }
            
            Log.d(TAG, String.format("Loaded baselines - DeepSleep: %.1f%%, Doze: %.1f%%, Overnight: %.2f%%/hr",
                    emaDeepSleepRatio * 100, emaDozeEffectiveness, emaOvernightDrain));
        } catch (Exception e) {
            Log.w(TAG, "Failed to load historical baselines", e);
        }
    }
    
    // ====================================================================================
    // MAIN ANALYSIS METHOD
    // ====================================================================================
    
    /**
     * Analyze sleep effectiveness for an idle drain session
     */
    public SleepAnalysisResult analyzeSession(IdleDrainSession session) {
        SleepAnalysisResult result = new SleepAnalysisResult();
        
        if (session == null) {
            result.isValid = false;
            result.notes = "Invalid session - null input";
            return result;
        }
        
        long duration = session.endTime - session.startTime;
        if (duration < 60000) { // Less than 1 minute
            result.isValid = false;
            result.notes = "Session too short for analysis";
            return result;
        }
        
        result.isValid = true;
        result.sessionId = session.id;
        result.startTime = session.startTime;
        result.endTime = session.endTime;
        result.durationHours = duration / 3600000f;
        
        // Calculate deep sleep metrics
        result.deepSleepPercentage = session.deepSleepPercentage;
        result.deepSleepTimeMs = session.deepSleepTime;
        result.lightSleepTimeMs = session.lightSleepTime;
        result.awakeTimeMs = session.awakeTime;
        result.deepSleepGrade = gradeDeepSleep(session.deepSleepPercentage);
        result.deepSleepQuality = assessDeepSleepQuality(session);
        
        // Doze mode analysis
        result.dozeActive = session.dozeActive;
        result.dozeEffectiveness = session.dozeEffectiveness;
        result.dozeEffectivenessGrade = gradeDozeEffectiveness(session.dozeEffectiveness);
        result.dozeExits = session.dozeExits;
        result.dozeExitReasons = parseDozeExitReasons(session.dozeExitReasons);
        
        // Wakelock analysis
        result.wakelockEvents = session.wakelockEvents;
        result.wakelockTimeMs = session.totalWakelockTime;
        result.wakelockImpact = assessWakelockImpact(session);
        result.wakelockImpactGrade = gradeWakelockImpact(result.wakelockImpact);
        result.topWakelockHolders = parseTopWakelockHolders(session.topWakelockHolders);
        
        // Wake reason analysis
        result.alarmWakeups = session.alarmWakeups;
        result.networkWakeups = session.networkWakeups;
        result.wakeReasonBreakdown = calculateWakeReasonBreakdown(session);
        result.primaryWakeReason = determinePrimaryWakeReason(session);
        
        // Sleep fragmentation analysis
        result.fragmentationScore = calculateFragmentationScore(session);
        result.fragmentationGrade = gradeFragmentation(result.fragmentationScore);
        result.wakesPerHour = calculateWakesPerHour(session);
        
        // Drain analysis
        result.drainPerHour = session.drainPerHour;
        result.totalDrain = session.drainAmount;
        result.drainGrade = gradeDrain(session.drainPerHour);
        result.expectedDrain = calculateExpectedDrain(session);
        result.drainEfficiency = calculateDrainEfficiency(session, result.expectedDrain);
        
        // Benchmark comparisons
        result.benchmarkResult = session.benchmarkResult;
        result.vsOptimalGap = session.drainPerHour - OVERNIGHT_OPTIMAL;
        result.vsGoodGap = session.drainPerHour - OVERNIGHT_GOOD;
        
        // Overall sleep quality score
        result.sleepQualityScore = calculateOverallSleepQuality(result);
        result.sleepQualityGrade = gradeSleepQuality(result.sleepQualityScore);
        
        // Leakage detection
        result.hasLeakage = session.hasLeakage;
        result.leakageClass = session.leakageClass;
        result.leakageSources = session.leakageSources;
        result.leakageSeverity = assessLeakageSeverity(session);
        
        // Generate recommendations
        result.recommendations = generateSleepRecommendations(result, session);
        result.notes = generateAnalysisNotes(result);
        
        // Update baselines
        updateBaselines(session, result);
        
        return result;
    }
    
    // ====================================================================================
    // DEEP SLEEP ANALYSIS
    // ====================================================================================
    
    /**
     * Grade deep sleep percentage
     */
    private String gradeDeepSleep(float percentage) {
        if (percentage >= DEEP_SLEEP_OPTIMAL) return "OPTIMAL";
        if (percentage >= DEEP_SLEEP_GOOD) return "GOOD";
        if (percentage >= DEEP_SLEEP_ACCEPTABLE) return "ACCEPTABLE";
        if (percentage >= DEEP_SLEEP_POOR) return "POOR";
        return "CRITICAL";
    }
    
    /**
     * Assess deep sleep quality comprehensively
     */
    private String assessDeepSleepQuality(IdleDrainSession session) {
        int score = 0;
        
        // Deep sleep percentage (0-40 points)
        if (session.deepSleepPercentage >= DEEP_SLEEP_OPTIMAL) score += 40;
        else if (session.deepSleepPercentage >= DEEP_SLEEP_GOOD) score += 30;
        else if (session.deepSleepPercentage >= DEEP_SLEEP_ACCEPTABLE) score += 20;
        else if (session.deepSleepPercentage >= DEEP_SLEEP_POOR) score += 10;
        
        // Deep sleep success rate (0-30 points)
        if (session.deepSleepSuccessRate >= 0.8f) score += 30;
        else if (session.deepSleepSuccessRate >= 0.6f) score += 20;
        else if (session.deepSleepSuccessRate >= 0.4f) score += 10;
        
        // Doze effectiveness (0-30 points)
        if (session.dozeEffectiveness >= 80) score += 30;
        else if (session.dozeEffectiveness >= 60) score += 20;
        else if (session.dozeEffectiveness >= 40) score += 10;
        
        if (score >= 85) return "EXCELLENT";
        if (score >= 70) return "GOOD";
        if (score >= 50) return "FAIR";
        if (score >= 30) return "POOR";
        return "CRITICAL";
    }
    
    /**
     * Track deep sleep state changes in real-time
     */
    public void onSleepStateChanged(boolean isDeep, long timestamp) {
        if (isDeep && !isDeepSleep) {
            // Entering deep sleep
            if (lightSleepStartTime > 0) {
                totalLightSleepMs += timestamp - lightSleepStartTime;
                lightSleepStartTime = 0;
            }
            deepSleepStartTime = timestamp;
            isDeepSleep = true;
        } else if (!isDeep && isDeepSleep) {
            // Exiting deep sleep
            if (deepSleepStartTime > 0) {
                totalDeepSleepMs += timestamp - deepSleepStartTime;
                deepSleepStartTime = 0;
            }
            lightSleepStartTime = timestamp;
            isDeepSleep = false;
            wakeCount++;
        }
    }
    
    /**
     * Begin sleep tracking session
     */
    public void beginSleepSession(long timestamp) {
        sleepStartTime = timestamp;
        isInSleep = true;
        isDeepSleep = false;
        lightSleepStartTime = timestamp;
        totalDeepSleepMs = 0;
        totalLightSleepMs = 0;
        totalAwakeMs = 0;
        wakeCount = 0;
        wakelockCount = 0;
        alarmWakeCount = 0;
        networkWakeCount = 0;
        dozeExitCount = 0;
    }
    
    /**
     * End sleep tracking session
     */
    public SleepSessionSummary endSleepSession(long timestamp) {
        SleepSessionSummary summary = new SleepSessionSummary();
        
        // Finalize any in-progress state
        if (isDeepSleep && deepSleepStartTime > 0) {
            totalDeepSleepMs += timestamp - deepSleepStartTime;
        } else if (lightSleepStartTime > 0) {
            totalLightSleepMs += timestamp - lightSleepStartTime;
        }
        
        long totalDuration = timestamp - sleepStartTime;
        
        summary.durationMs = totalDuration;
        summary.deepSleepMs = totalDeepSleepMs;
        summary.lightSleepMs = totalLightSleepMs;
        summary.awakeMs = totalAwakeMs;
        summary.deepSleepPercentage = totalDuration > 0 
                ? (totalDeepSleepMs * 100f) / totalDuration : 0;
        summary.wakeCount = wakeCount;
        summary.wakelockCount = wakelockCount;
        summary.alarmWakeCount = alarmWakeCount;
        summary.networkWakeCount = networkWakeCount;
        summary.dozeExitCount = dozeExitCount;
        
        // Reset state
        isInSleep = false;
        isDeepSleep = false;
        sleepStartTime = 0;
        deepSleepStartTime = 0;
        lightSleepStartTime = 0;
        
        return summary;
    }
    
    // ====================================================================================
    // DOZE MODE ANALYSIS
    // ====================================================================================
    
    /**
     * Grade Doze effectiveness
     */
    private String gradeDozeEffectiveness(float effectiveness) {
        if (effectiveness >= DOZE_OPTIMAL) return "OPTIMAL";
        if (effectiveness >= DOZE_GOOD) return "GOOD";
        if (effectiveness >= DOZE_ACCEPTABLE) return "ACCEPTABLE";
        return "POOR";
    }
    
    /**
     * Analyze Doze mode performance
     */
    public DozeAnalysisResult analyzeDozePerformance(IdleDrainSession session) {
        DozeAnalysisResult result = new DozeAnalysisResult();
        
        result.dozeActive = session.dozeActive;
        result.effectiveness = session.dozeEffectiveness;
        result.effectivenessGrade = gradeDozeEffectiveness(session.dozeEffectiveness);
        
        // Calculate doze entry time
        if (session.dozeEntryTime > 0 && session.startTime > 0) {
            result.entryDelayMs = session.dozeEntryTime - session.startTime;
        }
        
        // Calculate doze duration
        if (session.dozeEntryTime > 0) {
            long exitTime = session.dozeExitTime > 0 ? session.dozeExitTime : session.endTime;
            result.dozeDurationMs = exitTime - session.dozeEntryTime;
        }
        
        result.exitCount = session.dozeExits;
        result.exitReasons = parseDozeExitReasons(session.dozeExitReasons);
        
        // Assess doze quality
        if (session.dozeEffectiveness >= 80 && session.dozeExits <= 2) {
            result.quality = "EXCELLENT";
        } else if (session.dozeEffectiveness >= 60 && session.dozeExits <= 5) {
            result.quality = "GOOD";
        } else if (session.dozeEffectiveness >= 40) {
            result.quality = "FAIR";
        } else {
            result.quality = "POOR";
        }
        
        // Generate doze-specific recommendations
        result.recommendations = generateDozeRecommendations(session, result);
        
        return result;
    }
    
    /**
     * Parse Doze exit reasons JSON
     */
    private String[] parseDozeExitReasons(String json) {
        if (json == null || json.isEmpty()) {
            return new String[0];
        }
        
        // Simple parsing - in production would use JSON library
        try {
            String cleaned = json.replace("[", "").replace("]", "").replace("\"", "");
            if (cleaned.isEmpty()) return new String[0];
            return cleaned.split(",");
        } catch (Exception e) {
            return new String[0];
        }
    }
    
    /**
     * Generate Doze-specific recommendations
     */
    private String generateDozeRecommendations(IdleDrainSession session, DozeAnalysisResult result) {
        StringBuilder recs = new StringBuilder();
        
        if (!session.dozeActive) {
            recs.append("• Doze mode was not active - check for wakelocks preventing sleep\n");
        }
        
        if (result.entryDelayMs > 30 * 60 * 1000) { // > 30 minutes
            recs.append("• Doze entry delayed - screen timeout or app activity may be interfering\n");
        }
        
        if (session.dozeExits > 5) {
            recs.append("• Frequent Doze exits detected - review alarm and sync settings\n");
        }
        
        if (session.dozeEffectiveness < 50) {
            recs.append("• Enable Adaptive Battery to improve Doze effectiveness\n");
            recs.append("• Review apps with background restrictions\n");
        }
        
        return recs.length() > 0 ? recs.toString().trim() : "Doze mode operating normally";
    }
    
    // ====================================================================================
    // WAKELOCK ANALYSIS
    // ====================================================================================
    
    /**
     * Assess wakelock impact (0-100 scale)
     */
    private float assessWakelockImpact(IdleDrainSession session) {
        if (session.wakelockEvents == 0) return 0;
        
        float durationHours = (session.endTime - session.startTime) / 3600000f;
        if (durationHours == 0) return 0;
        
        float wakelocksPerHour = session.wakelockEvents / durationHours;
        float wakelockTimeRatio = session.totalWakelockTime / (float)(session.endTime - session.startTime);
        
        // Impact score based on frequency and duration
        float frequencyImpact = Math.min(50, wakelocksPerHour * 4);
        float durationImpact = Math.min(50, wakelockTimeRatio * 100);
        
        return Math.min(100, frequencyImpact + durationImpact);
    }
    
    /**
     * Grade wakelock impact
     */
    private String gradeWakelockImpact(float impact) {
        if (impact < 20) return "LOW";
        if (impact < 40) return "MODERATE";
        if (impact < 60) return "HIGH";
        return "SEVERE";
    }
    
    /**
     * Parse top wakelock holders JSON
     */
    private String[] parseTopWakelockHolders(String json) {
        if (json == null || json.isEmpty()) {
            return new String[0];
        }
        
        try {
            String cleaned = json.replace("[", "").replace("]", "").replace("\"", "");
            if (cleaned.isEmpty()) return new String[0];
            return cleaned.split(",");
        } catch (Exception e) {
            return new String[0];
        }
    }
    
    /**
     * Record a wakelock event
     */
    public void onWakelockAcquired(String tag, String packageName) {
        wakelockCount++;
        Log.d(TAG, "Wakelock acquired: " + tag + " by " + packageName);
    }
    
    // ====================================================================================
    // WAKE REASON ANALYSIS
    // ====================================================================================
    
    /**
     * Calculate wake reason breakdown
     */
    private WakeReasonBreakdown calculateWakeReasonBreakdown(IdleDrainSession session) {
        WakeReasonBreakdown breakdown = new WakeReasonBreakdown();
        
        int totalWakes = session.alarmWakeups + session.networkWakeups + 
                         session.dozeExits + session.wakelockEvents;
        
        if (totalWakes == 0) {
            breakdown.alarmPct = 0;
            breakdown.networkPct = 0;
            breakdown.dozePct = 0;
            breakdown.wakelockPct = 0;
            breakdown.otherPct = 0;
            return breakdown;
        }
        
        breakdown.alarmPct = (session.alarmWakeups * 100f) / totalWakes;
        breakdown.networkPct = (session.networkWakeups * 100f) / totalWakes;
        breakdown.dozePct = (session.dozeExits * 100f) / totalWakes;
        breakdown.wakelockPct = (session.wakelockEvents * 100f) / totalWakes;
        breakdown.otherPct = 100 - breakdown.alarmPct - breakdown.networkPct - 
                            breakdown.dozePct - breakdown.wakelockPct;
        
        return breakdown;
    }
    
    /**
     * Determine primary wake reason
     */
    private String determinePrimaryWakeReason(IdleDrainSession session) {
        int maxWakes = 0;
        String primary = "UNKNOWN";
        
        if (session.alarmWakeups > maxWakes) {
            maxWakes = session.alarmWakeups;
            primary = "ALARM";
        }
        if (session.networkWakeups > maxWakes) {
            maxWakes = session.networkWakeups;
            primary = "NETWORK";
        }
        if (session.dozeExits > maxWakes) {
            maxWakes = session.dozeExits;
            primary = "DOZE_EXIT";
        }
        if (session.wakelockEvents > maxWakes) {
            maxWakes = session.wakelockEvents;
            primary = "WAKELOCK";
        }
        
        return primary;
    }
    
    /**
     * Record an alarm wake
     */
    public void onAlarmWake() {
        alarmWakeCount++;
    }
    
    /**
     * Record a network wake
     */
    public void onNetworkWake() {
        networkWakeCount++;
    }
    
    /**
     * Record Doze exit
     */
    public void onDozeExit() {
        dozeExitCount++;
    }
    
    // ====================================================================================
    // SLEEP FRAGMENTATION ANALYSIS
    // ====================================================================================
    
    /**
     * Calculate sleep fragmentation score (0-100, lower is better)
     */
    private float calculateFragmentationScore(IdleDrainSession session) {
        float durationHours = (session.endTime - session.startTime) / 3600000f;
        if (durationHours == 0) return 0;
        
        // Total interruptions
        int totalWakes = session.alarmWakeups + session.networkWakeups + 
                         session.dozeExits + Math.min(session.wakelockEvents, 10);
        
        float wakesPerHour = totalWakes / durationHours;
        
        // Fragmentation based on frequency
        float fragmentation = Math.min(100, wakesPerHour * 10);
        
        // Adjust for deep sleep percentage (higher deep sleep reduces fragmentation impact)
        if (session.deepSleepPercentage > 60) {
            fragmentation *= 0.8f;
        } else if (session.deepSleepPercentage < 30) {
            fragmentation *= 1.2f;
        }
        
        return Math.min(100, Math.max(0, fragmentation));
    }
    
    /**
     * Grade sleep fragmentation
     */
    private String gradeFragmentation(float score) {
        if (score < 20) return "MINIMAL";
        if (score < 40) return "LOW";
        if (score < 60) return "MODERATE";
        if (score < 80) return "HIGH";
        return "SEVERE";
    }
    
    /**
     * Calculate wakes per hour
     */
    private float calculateWakesPerHour(IdleDrainSession session) {
        float durationHours = (session.endTime - session.startTime) / 3600000f;
        if (durationHours == 0) return 0;
        
        int totalWakes = session.alarmWakeups + session.networkWakeups + session.dozeExits;
        return totalWakes / durationHours;
    }
    
    // ====================================================================================
    // DRAIN ANALYSIS
    // ====================================================================================
    
    /**
     * Grade overnight/idle drain
     */
    private String gradeDrain(float drainPerHour) {
        if (drainPerHour < OVERNIGHT_OPTIMAL) return "OPTIMAL";
        if (drainPerHour < OVERNIGHT_GOOD) return "GOOD";
        if (drainPerHour < OVERNIGHT_ACCEPTABLE) return "ACCEPTABLE";
        if (drainPerHour < OVERNIGHT_POOR) return "POOR";
        return "CRITICAL";
    }
    
    /**
     * Calculate expected drain based on session characteristics
     */
    private float calculateExpectedDrain(IdleDrainSession session) {
        float baseDrain = OVERNIGHT_GOOD; // Start with "good" baseline
        
        // Adjust for doze effectiveness
        if (session.dozeActive && session.dozeEffectiveness > 70) {
            baseDrain *= 0.7f; // Good doze reduces expected drain
        } else if (!session.dozeActive) {
            baseDrain *= 1.5f; // No doze increases expected drain
        }
        
        // Adjust for wakelock activity
        if (session.wakelockEvents > 10) {
            baseDrain *= 1.3f;
        }
        
        // Adjust for wake frequency
        float durationHours = (session.endTime - session.startTime) / 3600000f;
        if (durationHours > 0) {
            float wakesPerHour = (session.alarmWakeups + session.networkWakeups) / durationHours;
            if (wakesPerHour > 5) {
                baseDrain *= 1.2f;
            }
        }
        
        return baseDrain * durationHours;
    }
    
    /**
     * Calculate drain efficiency percentage
     */
    private float calculateDrainEfficiency(IdleDrainSession session, float expectedDrain) {
        if (expectedDrain == 0) return 100;
        if (session.drainAmount == 0) return 100;
        
        // Efficiency = (expected / actual) * 100
        return Math.min(150, (expectedDrain / session.drainAmount) * 100);
    }
    
    // ====================================================================================
    // LEAKAGE ASSESSMENT
    // ====================================================================================
    
    /**
     * Assess leakage severity
     */
    private String assessLeakageSeverity(IdleDrainSession session) {
        if (!session.hasLeakage) return "NONE";
        
        if ("LEAK".equals(session.leakageClass)) {
            return session.drainPerHour > 3.0f ? "CRITICAL" : "SEVERE";
        } else if ("WARNING".equals(session.leakageClass)) {
            return "MODERATE";
        }
        return "MILD";
    }
    
    // ====================================================================================
    // OVERALL SLEEP QUALITY
    // ====================================================================================
    
    /**
     * Calculate overall sleep quality score (0-100)
     */
    private float calculateOverallSleepQuality(SleepAnalysisResult result) {
        float score = 0;
        float totalWeight = 0;
        
        // Deep sleep (weight: 25)
        float deepSleepScore = Math.min(100, (result.deepSleepPercentage / DEEP_SLEEP_OPTIMAL) * 100);
        score += deepSleepScore * 0.25f;
        totalWeight += 0.25f;
        
        // Doze effectiveness (weight: 20)
        float dozeScore = Math.min(100, (result.dozeEffectiveness / DOZE_OPTIMAL) * 100);
        score += dozeScore * 0.20f;
        totalWeight += 0.20f;
        
        // Drain efficiency (weight: 25)
        float drainScore = Math.min(100, result.drainEfficiency);
        score += drainScore * 0.25f;
        totalWeight += 0.25f;
        
        // Low fragmentation (weight: 15)
        float fragmentScore = Math.max(0, 100 - result.fragmentationScore);
        score += fragmentScore * 0.15f;
        totalWeight += 0.15f;
        
        // Low wakelock impact (weight: 15)
        float wakelockScore = Math.max(0, 100 - result.wakelockImpact);
        score += wakelockScore * 0.15f;
        totalWeight += 0.15f;
        
        return totalWeight > 0 ? score / totalWeight : 0;
    }
    
    /**
     * Grade sleep quality score
     */
    private String gradeSleepQuality(float score) {
        if (score >= 90) return "A+";
        if (score >= 80) return "A";
        if (score >= 70) return "B";
        if (score >= 60) return "C";
        if (score >= 50) return "D";
        return "F";
    }
    
    // ====================================================================================
    // RECOMMENDATIONS
    // ====================================================================================
    
    /**
     * Generate comprehensive sleep recommendations
     */
    private String generateSleepRecommendations(SleepAnalysisResult result, IdleDrainSession session) {
        StringBuilder recs = new StringBuilder();
        
        // Deep sleep recommendations
        if (result.deepSleepPercentage < DEEP_SLEEP_ACCEPTABLE) {
            recs.append("• Deep sleep is below optimal - check for background activity\n");
            if (session.wakelockEvents > 5) {
                recs.append("• Frequent wakelocks detected - review battery-intensive apps\n");
            }
        }
        
        // Doze recommendations
        if (!session.dozeActive) {
            recs.append("• Doze mode not entering - ensure screen is off and device is stationary\n");
        } else if (session.dozeEffectiveness < DOZE_ACCEPTABLE) {
            recs.append("• Doze effectiveness is low - enable Adaptive Battery\n");
        }
        
        // Wake recommendations
        if (session.alarmWakeups > 5) {
            recs.append("• Frequent alarm wakes - review app alarm permissions\n");
        }
        if (session.networkWakeups > 5) {
            recs.append("• Frequent network wakes - reduce sync frequency for non-critical apps\n");
        }
        
        // Wakelock recommendations
        if (result.wakelockImpact > 40) {
            recs.append("• High wakelock impact - identify and restrict wakelocks-heavy apps\n");
            String[] holders = result.topWakelockHolders;
            if (holders != null && holders.length > 0) {
                recs.append("• Top offenders: ").append(String.join(", ", holders)).append("\n");
            }
        }
        
        // Fragmentation recommendations
        if (result.fragmentationScore > 50) {
            recs.append("• Sleep is fragmented - consider Do Not Disturb mode overnight\n");
        }
        
        // Drain recommendations
        if (result.drainPerHour > OVERNIGHT_ACCEPTABLE) {
            recs.append("• Overnight drain is higher than optimal\n");
            if (!session.hasLeakage) {
                recs.append("• Enable Battery Saver mode overnight\n");
            }
        }
        
        // Leakage recommendations
        if (session.hasLeakage) {
            recs.append("• Power leakage detected!\n");
            if (session.leakageSources != null && !session.leakageSources.isEmpty()) {
                recs.append("• Sources: ").append(session.leakageSources).append("\n");
            }
            recs.append("• Restart device if issue persists\n");
        }
        
        return recs.length() > 0 ? recs.toString().trim() : "Sleep efficiency is optimal";
    }
    
    /**
     * Generate analysis notes
     */
    private String generateAnalysisNotes(SleepAnalysisResult result) {
        return String.format(
            "Duration: %.1fh | Deep: %.0f%% (%s) | Doze: %.0f%% (%s) | Drain: %.2f%%/hr (%s) | Quality: %.0f/100 (%s)",
            result.durationHours,
            result.deepSleepPercentage,
            result.deepSleepGrade,
            result.dozeEffectiveness,
            result.dozeEffectivenessGrade,
            result.drainPerHour,
            result.drainGrade,
            result.sleepQualityScore,
            result.sleepQualityGrade
        );
    }
    
    // ====================================================================================
    // BASELINE UPDATES
    // ====================================================================================
    
    /**
     * Update historical baselines using EMA
     */
    private void updateBaselines(IdleDrainSession session, SleepAnalysisResult result) {
        if (!result.isValid || result.durationHours < 1) return; // Skip short sessions
        
        // Update deep sleep baseline
        emaDeepSleepRatio = emaDeepSleepRatio * (1 - EMA_ALPHA) + 
                           (result.deepSleepPercentage / 100f) * EMA_ALPHA;
        
        // Update overnight drain baseline
        if (session.drainPerHour < 5) { // Ignore extreme outliers
            emaOvernightDrain = emaOvernightDrain * (1 - EMA_ALPHA) + 
                               session.drainPerHour * EMA_ALPHA;
        }
        
        // Update doze effectiveness baseline
        if (session.dozeActive) {
            emaDozeEffectiveness = emaDozeEffectiveness * (1 - EMA_ALPHA) + 
                                  session.dozeEffectiveness * EMA_ALPHA;
        }
        
        // Update sleep quality baseline
        emaSleepQualityScore = emaSleepQualityScore * (1 - EMA_ALPHA) + 
                              result.sleepQualityScore * EMA_ALPHA;
    }
    
    // ====================================================================================
    // HISTORICAL ANALYSIS
    // ====================================================================================
    
    /**
     * Analyze sleep trends over time
     */
    public SleepTrendAnalysis analyzeSleepTrends(long startTime, long endTime) {
        SleepTrendAnalysis trend = new SleepTrendAnalysis();
        trend.startTime = startTime;
        trend.endTime = endTime;
        
        try {
            List<IdleDrainSession> sessions = database.idleDrainSessionDao().getSessionsSince(startTime);
            
            if (sessions == null || sessions.isEmpty()) {
                trend.hasData = false;
                return trend;
            }
            
            trend.hasData = true;
            trend.sessionCount = sessions.size();
            
            float totalDeepSleep = 0;
            float totalDozeEff = 0;
            float totalDrain = 0;
            float totalQuality = 0;
            int validDoze = 0;
            int leakageCount = 0;
            
            for (IdleDrainSession session : sessions) {
                totalDeepSleep += session.deepSleepPercentage;
                totalDrain += session.drainPerHour;
                totalQuality += session.dozeEffectivenessScore;
                
                if (session.dozeActive) {
                    totalDozeEff += session.dozeEffectiveness;
                    validDoze++;
                }
                
                if (session.hasLeakage) {
                    leakageCount++;
                }
            }
            
            int count = sessions.size();
            trend.avgDeepSleepPct = totalDeepSleep / count;
            trend.avgDozeEffectiveness = validDoze > 0 ? totalDozeEff / validDoze : 0;
            trend.avgDrainPerHour = totalDrain / count;
            trend.avgQualityScore = totalQuality / count;
            trend.leakageIncidents = leakageCount;
            trend.leakageRate = (leakageCount * 100f) / count;
            
            // Determine trend direction
            if (sessions.size() >= 4) {
                int half = sessions.size() / 2;
                float firstHalfQuality = 0;
                float secondHalfQuality = 0;
                
                for (int i = 0; i < half; i++) {
                    firstHalfQuality += sessions.get(i).dozeEffectivenessScore;
                }
                for (int i = half; i < sessions.size(); i++) {
                    secondHalfQuality += sessions.get(i).dozeEffectivenessScore;
                }
                
                firstHalfQuality /= half;
                secondHalfQuality /= (sessions.size() - half);
                
                float change = secondHalfQuality - firstHalfQuality;
                trend.trendDirection = change > 5 ? "IMPROVING" : change < -5 ? "DECLINING" : "STABLE";
                trend.trendChangePct = firstHalfQuality > 0 ? (change / firstHalfQuality) * 100 : 0;
            } else {
                trend.trendDirection = "INSUFFICIENT_DATA";
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to analyze sleep trends", e);
            trend.hasData = false;
        }
        
        return trend;
    }
    
    // ====================================================================================
    // GETTERS FOR BASELINES
    // ====================================================================================
    
    public float getEmaDeepSleepRatio() { return emaDeepSleepRatio; }
    public float getEmaOvernightDrain() { return emaOvernightDrain; }
    public float getEmaDozeEffectiveness() { return emaDozeEffectiveness; }
    public float getEmaSleepQualityScore() { return emaSleepQualityScore; }
    
    // ====================================================================================
    // RESULT CLASSES
    // ====================================================================================
    
    /**
     * Complete sleep analysis result
     */
    public static class SleepAnalysisResult {
        public boolean isValid;
        public long sessionId;
        public long startTime;
        public long endTime;
        public float durationHours;
        
        // Deep sleep metrics
        public float deepSleepPercentage;
        public long deepSleepTimeMs;
        public long lightSleepTimeMs;
        public long awakeTimeMs;
        public String deepSleepGrade;
        public String deepSleepQuality;
        
        // Doze analysis
        public boolean dozeActive;
        public float dozeEffectiveness;
        public String dozeEffectivenessGrade;
        public int dozeExits;
        public String[] dozeExitReasons;
        
        // Wakelock analysis
        public int wakelockEvents;
        public long wakelockTimeMs;
        public float wakelockImpact;
        public String wakelockImpactGrade;
        public String[] topWakelockHolders;
        
        // Wake reasons
        public int alarmWakeups;
        public int networkWakeups;
        public WakeReasonBreakdown wakeReasonBreakdown;
        public String primaryWakeReason;
        
        // Fragmentation
        public float fragmentationScore;
        public String fragmentationGrade;
        public float wakesPerHour;
        
        // Drain
        public float drainPerHour;
        public float totalDrain;
        public String drainGrade;
        public float expectedDrain;
        public float drainEfficiency;
        
        // Benchmark
        public String benchmarkResult;
        public float vsOptimalGap;
        public float vsGoodGap;
        
        // Overall quality
        public float sleepQualityScore;
        public String sleepQualityGrade;
        
        // Leakage
        public boolean hasLeakage;
        public String leakageClass;
        public String leakageSources;
        public String leakageSeverity;
        
        // Output
        public String recommendations;
        public String notes;
    }
    
    /**
     * Wake reason breakdown
     */
    public static class WakeReasonBreakdown {
        public float alarmPct;
        public float networkPct;
        public float dozePct;
        public float wakelockPct;
        public float otherPct;
    }
    
    /**
     * Doze analysis result
     */
    public static class DozeAnalysisResult {
        public boolean dozeActive;
        public float effectiveness;
        public String effectivenessGrade;
        public long entryDelayMs;
        public long dozeDurationMs;
        public int exitCount;
        public String[] exitReasons;
        public String quality;
        public String recommendations;
    }
    
    /**
     * Sleep session summary
     */
    public static class SleepSessionSummary {
        public long durationMs;
        public long deepSleepMs;
        public long lightSleepMs;
        public long awakeMs;
        public float deepSleepPercentage;
        public int wakeCount;
        public int wakelockCount;
        public int alarmWakeCount;
        public int networkWakeCount;
        public int dozeExitCount;
    }
    
    /**
     * Sleep trend analysis
     */
    public static class SleepTrendAnalysis {
        public long startTime;
        public long endTime;
        public boolean hasData;
        
        public int sessionCount;
        public float avgDeepSleepPct;
        public float avgDozeEffectiveness;
        public float avgDrainPerHour;
        public float avgQualityScore;
        public int leakageIncidents;
        public float leakageRate;
        
        public String trendDirection;
        public float trendChangePct;
    }
}
