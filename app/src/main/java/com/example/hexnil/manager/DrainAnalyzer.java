package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.drain.DrainIntelligence;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.DrainEvent;

/**
 * OEM-Grade Drain Event Analyzer
 * 
 * Tracks discharging periods and records drain events with comprehensive
 * intelligence.
 * Integrates with DrainIntelligence engine to populate all 50+ analysis fields.
 * 
 * FIXED: Previously didn't call DrainIntelligence.analyzeDrainEvent(), leaving
 * 50+ fields as zero.
 * Now properly integrates the analysis engine for complete drain attribution.
 */
public class DrainAnalyzer {

    private static final String TAG = "DrainAnalyzer";

    private final Context context;
    private final HexNilDatabase database;
    private final DrainIntelligence drainIntelligence;

    private DrainEvent activeEvent;
    private int lastLevel = -1;
    private BatterySnapshot lastSnapshot;

    public DrainAnalyzer(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
        this.drainIntelligence = new DrainIntelligence(context, database);
    }

    public synchronized void startDrainTracking() {
        if (activeEvent != null && activeEvent.endTime == 0)
            return;
        activeEvent = new DrainEvent();
        database.drainEventDao().insert(activeEvent);
    }

    public synchronized void processSnapshot(BatterySnapshot snapshot) {
        if (snapshot.isCharging) {
            // Close active drain event when charging resumes
            if (activeEvent != null) {
                activeEvent.endTime = snapshot.timestamp;
                activeEvent.endLevel = snapshot.level;
                finalizeDrain(activeEvent, snapshot);
                database.drainEventDao().update(activeEvent);
                activeEvent = null;
            }
            lastSnapshot = snapshot;
            return;
        }

        if (activeEvent == null) {
            startDrainTracking();
        }
        if (activeEvent == null)
            return;

        if (activeEvent.startLevel == 0) {
            activeEvent.startLevel = snapshot.level;
            activeEvent.startTime = snapshot.timestamp;
        }

        // Update basic metrics
        activeEvent.endLevel = snapshot.level;
        activeEvent.endTime = snapshot.timestamp;
        activeEvent.screenOn = snapshot.screenOn;
        activeEvent.foregroundApp = snapshot.foregroundApp;
        activeEvent.isCharging = false;
        activeEvent.drainAmount = Math.max(0, activeEvent.startLevel - activeEvent.endLevel);
        activeEvent.drainRatePerHour = activeEvent.getDurationHours() > 0
                ? activeEvent.drainAmount / activeEvent.getDurationHours()
                : 0;
        activeEvent.screenState = snapshot.isDozeMode ? "doze" : snapshot.screenOn ? "on" : "off";
        activeEvent.isIdle = !snapshot.screenOn;

        // Classify basic drain properties
        classifyDrain(snapshot);

        // Calculate thermal metrics from snapshot
        activeEvent.avgTemperature = snapshot.temperature / 10; // Convert from tenths
        activeEvent.maxTemperature = Math.max(activeEvent.maxTemperature, snapshot.temperature / 10);
        activeEvent.wasOverheating = activeEvent.maxTemperature > 45; // 45°C threshold

        // CRITICAL FIX: Use DrainIntelligence to compute all 50+ advanced metrics
        applyDrainIntelligence(activeEvent, snapshot);

        database.drainEventDao().update(activeEvent);
        lastLevel = snapshot.level;
        lastSnapshot = snapshot;
    }

    /**
     * Apply DrainIntelligence analysis to populate all advanced fields.
     * This is the critical integration that was previously missing.
     */
    private void applyDrainIntelligence(DrainEvent event, BatterySnapshot snapshot) {
        DrainIntelligence.DrainAnalysisResult result = drainIntelligence.analyzeDrainEvent(event, snapshot);

        if (result == null || !result.isValid) {
            return;
        }

        // =====================================================================
        // SCREEN STATE ANALYSIS
        // =====================================================================
        event.screenStateDetailed = result.screenState;
        event.screenStateGrade = result.screenStateGrade;
        event.brightnessCategory = categorizeBrightness(snapshot.screenBrightness);

        // =====================================================================
        // EXPECTED VS ACTUAL DRAIN MODEL
        // =====================================================================
        event.expectedDrainRate = result.expectedDrainRate;
        event.expectedDrain = result.expectedDrain;
        event.drainDelta = result.drainDelta;
        event.drainEfficiency = result.drainEfficiency;
        event.drainEfficiencyGrade = gradeDrainEfficiency(result.drainEfficiency);

        // Update baselines from historical averages
        event.baselineScreenOnRate = drainIntelligence.getBaselineScreenOnRate();
        event.baselineScreenOffRate = drainIntelligence.getBaselineScreenOffRate();
        event.baselineDozeDrainRate = drainIntelligence.getBaselineDozeDrainRate();
        event.vsBaselineDelta = event.drainRatePerHour - getApplicableBaseline(event);

        // =====================================================================
        // BENCHMARK COMPARISON
        // =====================================================================
        event.benchmarkResult = result.benchmarkResult;
        event.benchmarkDelta = result.benchmarkDelta;
        event.benchmarkDrainRate = drainIntelligence.getBenchmarkDrainRate(result.screenState);
        event.benchmarkNotes = generateBenchmarkNotes(result);

        // =====================================================================
        // DEEP SLEEP EFFECTIVENESS
        // =====================================================================
        event.deepSleepEffectiveness = result.deepSleepEffectiveness;
        event.deepSleepGrade = result.deepSleepGrade;
        event.isDeepSleepActive = result.isDeepSleepActive;
        event.deepSleepPercentage = drainIntelligence.getDeepSleepPercentage();
        event.deepSleepTimeMs = drainIntelligence.getDeepSleepTimeMs();
        event.lightSleepTimeMs = drainIntelligence.getLightSleepTimeMs();

        // Idle drain quality
        event.idleDrainQuality = result.idleDrainQuality;
        event.idleBenchmarkResult = result.idleBenchmarkResult;
        event.idleDrainScore = calculateIdleDrainScore(result);

        // =====================================================================
        // COMPONENT-LEVEL DRAIN ATTRIBUTION
        // =====================================================================
        event.displayDrainPct = result.displayDrainPct;
        event.cpuDrainPct = result.cpuDrainPct;
        event.networkDrainPct = result.networkDrainPct;
        event.sensorDrainPct = result.sensorDrainPct;
        event.otherDrainPct = result.otherDrainPct;
        event.primaryDrainComponent = result.primaryDrainComponent;
        event.componentBreakdownJson = buildComponentBreakdownJson(result);

        // =====================================================================
        // PER-APP DRAIN PROFILING
        // =====================================================================
        event.topAppPackage = snapshot.topAppPackage;
        event.topAppName = snapshot.foregroundApp;
        event.topAppIsSystem = isSystemApp(snapshot.topAppPackage);
        event.topAppEfficiency = calculateAppEfficiency(snapshot.topAppPackage, event.drainRatePerHour);

        // =====================================================================
        // ANOMALY DETECTION ENHANCED
        // =====================================================================
        event.isAnomalous = result.isAnomalous;
        event.anomalyScore = result.anomalyScore;
        event.anomalySeverity = result.anomalySeverity;
        event.consecutiveAnomalies = drainIntelligence.getConsecutiveAnomalies();
        event.anomalyReason = result.isAnomalous ? generateAnomalyReason(result) : null;
        event.isPatternAnomaly = drainIntelligence.isPatternAnomaly();

        // =====================================================================
        // DRAIN TREND TRACKING
        // =====================================================================
        event.trendDirection = drainIntelligence.getTrendDirection();
        event.trendChangePercent = drainIntelligence.getTrendChangePercent();
        event.rollingAvgDrain = drainIntelligence.getRollingAverageDrain();
        event.peakDrainRate = Math.max(event.peakDrainRate, event.drainRatePerHour);

        // =====================================================================
        // THERMAL CORRELATION
        // =====================================================================
        event.thermalDrainImpact = calculateThermalDrainImpact(event);

        // =====================================================================
        // QUALITY METRICS
        // =====================================================================
        event.overallDrainScore = calculateOverallDrainScore(result, event);
        event.overallGrade = gradeOverallScore(event.overallDrainScore);
        event.qualitySummary = generateQualitySummary(event, result);

        // Analysis output
        event.analysisNotes = result.analysisNotes;
        event.recommendations = result.recommendations;
    }

    private void finalizeDrain(DrainEvent event, BatterySnapshot snapshot) {
        event.drainAmount = Math.max(0, event.startLevel - event.endLevel);
        event.drainRatePerHour = event.getDurationHours() > 0
                ? event.drainAmount / event.getDurationHours()
                : 0;
        event.isAnomalous = event.isExcessiveDrain();

        // Final intelligence pass
        if (snapshot != null) {
            applyDrainIntelligence(event, snapshot);
        }
    }

    private void classifyDrain(BatterySnapshot snapshot) {
        if (snapshot.screenOn) {
            activeEvent.drainType = activeEvent.drainRatePerHour > 18 ? "heavy" : "normal";
            activeEvent.drainCategory = "display";
        } else if (snapshot.isDozeMode) {
            activeEvent.drainType = activeEvent.drainRatePerHour > 1.0f ? "idle" : "standby";
            activeEvent.drainCategory = "idle";
        } else {
            activeEvent.drainType = activeEvent.drainRatePerHour > 1.5f ? "heavy" : "normal";
            activeEvent.drainCategory = "system";
        }

        activeEvent.primaryCause = snapshot.topAppPackage != null ? snapshot.topAppPackage : snapshot.foregroundApp;
        activeEvent.networkType = snapshot.networkState;
        activeEvent.networkDrainEstimate = "mobile".equals(snapshot.networkState) ? activeEvent.drainRatePerHour * 0.4f
                : activeEvent.drainRatePerHour * 0.2f;
        activeEvent.displayDrainEstimate = snapshot.screenOn
                ? (snapshot.screenBrightness / 255f) * activeEvent.drainRatePerHour
                : 0f;
        activeEvent.cpuDrainEstimate = snapshot.cpuLoadTotal > 0
                ? (snapshot.cpuLoadTotal / 100f) * activeEvent.drainRatePerHour
                : 0f;
        activeEvent.avgBrightness = snapshot.screenBrightness;
        activeEvent.screenOnTime = activeEvent.screenOn
                ? Math.max(0, snapshot.timestamp - activeEvent.startTime)
                : activeEvent.screenOnTime;
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private int categorizeBrightness(int brightness) {
        if (brightness < 50)
            return 0; // dim
        if (brightness < 128)
            return 1; // medium
        if (brightness < 200)
            return 2; // bright
        return 3; // max
    }

    private String gradeDrainEfficiency(float efficiency) {
        if (efficiency >= 90)
            return "EXCELLENT";
        if (efficiency >= 75)
            return "GOOD";
        if (efficiency >= 50)
            return "AVERAGE";
        return "POOR";
    }

    private float getApplicableBaseline(DrainEvent event) {
        if ("on".equals(event.screenState))
            return event.baselineScreenOnRate;
        if ("doze".equals(event.screenState))
            return event.baselineDozeDrainRate;
        return event.baselineScreenOffRate;
    }

    private String generateBenchmarkNotes(DrainIntelligence.DrainAnalysisResult result) {
        if ("EXCELLENT".equals(result.benchmarkResult)) {
            return "Drain rate is excellent - better than industry average";
        } else if ("GOOD".equals(result.benchmarkResult)) {
            return "Drain rate is within normal parameters";
        } else if ("AVERAGE".equals(result.benchmarkResult)) {
            return "Drain rate is average - room for optimization";
        } else if ("POOR".equals(result.benchmarkResult)) {
            return "Drain rate is above average - consider reducing usage";
        }
        return "Drain rate is critical - immediate action recommended";
    }

    private float calculateIdleDrainScore(DrainIntelligence.DrainAnalysisResult result) {
        // Score from 0-100 based on idle drain quality
        if ("OPTIMAL".equals(result.idleDrainQuality))
            return 100f;
        if ("GOOD".equals(result.idleDrainQuality))
            return 80f;
        if ("ACCEPTABLE".equals(result.idleDrainQuality))
            return 60f;
        return 40f;
    }

    private String buildComponentBreakdownJson(DrainIntelligence.DrainAnalysisResult result) {
        return String.format(
                "{\"display\":%.1f,\"cpu\":%.1f,\"network\":%.1f,\"sensors\":%.1f,\"other\":%.1f}",
                result.displayDrainPct, result.cpuDrainPct, result.networkDrainPct,
                result.sensorDrainPct, result.otherDrainPct);
    }

    private boolean isSystemApp(String packageName) {
        if (packageName == null)
            return false;
        return packageName.startsWith("com.android.")
                || packageName.startsWith("com.google.")
                || packageName.startsWith("android.");
    }

    private float calculateAppEfficiency(String packageName, float drainRate) {
        // Placeholder - would need app-specific benchmarks
        return drainRate < 10 ? 90f : (drainRate < 20 ? 70f : 50f);
    }

    private String generateAnomalyReason(DrainIntelligence.DrainAnalysisResult result) {
        StringBuilder reason = new StringBuilder();
        if (result.drainDelta > 5) {
            reason.append("Drain ").append(String.format("%.1f%%", result.drainDelta))
                    .append(" above expected. ");
        }
        if ("POOR".equals(result.benchmarkResult) || "CRITICAL".equals(result.benchmarkResult)) {
            reason.append("Performance below industry benchmark. ");
        }
        if (result.deepSleepEffectiveness < 50 && result.isDeepSleepActive) {
            reason.append("Low deep sleep effectiveness (")
                    .append(String.format("%.0f%%", result.deepSleepEffectiveness))
                    .append("). ");
        }
        return reason.length() > 0 ? reason.toString().trim() : "Abnormal drain pattern detected";
    }

    private float calculateThermalDrainImpact(DrainEvent event) {
        // Thermal impact increases drain by ~10% for every 10°C above 35
        float baseTemp = 35f;
        if (event.avgTemperature > baseTemp) {
            float tempDelta = event.avgTemperature - baseTemp;
            return (tempDelta / 10f) * 10f; // 10% per 10°C
        }
        return 0f;
    }

    private float calculateOverallDrainScore(DrainIntelligence.DrainAnalysisResult result,
            DrainEvent event) {
        float score = 100f;

        // Penalize based on various factors
        score -= result.anomalyScore * 30; // Up to -30 for high anomaly
        score -= Math.max(0, result.drainDelta) * 2; // -2 per % above expected
        if (event.wasOverheating)
            score -= 20; // -20 for overheating
        if (event.deepSleepEffectiveness < 50 && event.isDeepSleepActive) {
            score -= 10; // -10 for poor deep sleep
        }

        return Math.max(0, Math.min(100, score));
    }

    private String gradeOverallScore(float score) {
        if (score >= 95)
            return "A+";
        if (score >= 90)
            return "A";
        if (score >= 80)
            return "B";
        if (score >= 70)
            return "C";
        if (score >= 60)
            return "D";
        return "F";
    }

    private String generateQualitySummary(DrainEvent event,
            DrainIntelligence.DrainAnalysisResult result) {
        String grade = event.overallGrade != null ? event.overallGrade : gradeOverallScore(event.overallDrainScore);
        return String.format("Grade %s: %.1f%%/hr drain (%s). %s",
                grade, event.drainRatePerHour, result.benchmarkResult,
                event.isAnomalous ? "Anomaly detected." : "Within normal range.");
    }
}
