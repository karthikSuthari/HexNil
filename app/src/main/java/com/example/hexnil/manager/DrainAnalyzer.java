package com.example.hexnil.manager;

import android.content.Context;
import android.net.TrafficStats;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.drain.DrainIntelligence;
import com.example.hexnil.model.AppBatteryUsage;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.DrainEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    // Network tracking for Group F
    private long networkTxBytesStart = 0;
    private long networkRxBytesStart = 0;
    
    // CPU tracking for Group F
    private int cpuSampleCount = 0;
    private long cpuUsageSum = 0;
    
    // Flag to defer database insert until first snapshot populates basic data
    private boolean needsInsert = false;

    public DrainAnalyzer(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
        this.drainIntelligence = new DrainIntelligence(context, database);
    }

    public synchronized void startDrainTracking() {
        if (activeEvent != null && activeEvent.endTime == 0)
            return;
        activeEvent = new DrainEvent();
        // DON'T insert yet - wait for first snapshot to populate basic data
        
        // Reset network tracking at drain start
        networkTxBytesStart = TrafficStats.getTotalTxBytes();
        networkRxBytesStart = TrafficStats.getTotalRxBytes();
        
        // Reset CPU tracking
        cpuSampleCount = 0;
        cpuUsageSum = 0;
        
        // Flag that we need to insert on first processSnapshot
        needsInsert = true;
    }

    public synchronized void processSnapshot(BatterySnapshot snapshot) {
        if (snapshot.isCharging) {
            // Close active drain event when charging resumes
            if (activeEvent != null) {
                activeEvent.endTime = snapshot.timestamp;
                activeEvent.endLevel = snapshot.level;
                finalizeDrain(activeEvent, snapshot);
                // Insert or update based on whether we've already saved this event
                if (needsInsert) {
                    database.drainEventDao().insert(activeEvent);
                    needsInsert = false;
                } else {
                    database.drainEventDao().update(activeEvent);
                }
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
        
        // Track CPU usage samples for avgCpuUsage calculation (Group F)
        if (snapshot.cpuLoadTotal != null && snapshot.cpuLoadTotal > 0) {
            cpuUsageSum += snapshot.cpuLoadTotal;
            cpuSampleCount++;
            activeEvent.avgCpuUsage = cpuSampleCount > 0 ? (int)(cpuUsageSum / cpuSampleCount) : 0;
        }

        // Update basic metrics
        activeEvent.endLevel = snapshot.level;
        activeEvent.endTime = snapshot.timestamp;
        activeEvent.screenOn = snapshot.isScreenOnOrFalse();
        activeEvent.foregroundApp = snapshot.foregroundApp;
        activeEvent.isCharging = false;
        activeEvent.drainAmount = Math.max(0, activeEvent.startLevel - activeEvent.endLevel);
        activeEvent.drainRatePerHour = activeEvent.getDurationHours() > 0
                ? activeEvent.drainAmount / activeEvent.getDurationHours()
                : 0;
        activeEvent.screenState = snapshot.isDozeMode ? "doze" : snapshot.isScreenOnOrFalse() ? "on" : "off";
        activeEvent.isIdle = !snapshot.isScreenOnOrFalse();

        // Classify basic drain properties
        classifyDrain(snapshot);

        // Calculate thermal metrics from snapshot
        activeEvent.avgTemperature = snapshot.temperature / 10; // Convert from tenths
        activeEvent.maxTemperature = Math.max(activeEvent.maxTemperature, snapshot.temperature / 10);
        activeEvent.wasOverheating = activeEvent.maxTemperature > 45; // 45°C threshold

        // CRITICAL FIX: Use DrainIntelligence to compute all 50+ advanced metrics
        applyDrainIntelligence(activeEvent, snapshot);

        // Insert or update the event in database
        if (needsInsert) {
            // First time - insert with populated data
            long id = database.drainEventDao().insert(activeEvent);
            activeEvent.id = id;
            needsInsert = false;
        } else {
            database.drainEventDao().update(activeEvent);
        }
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
        event.brightnessCategory = categorizeBrightness(snapshot.screenBrightness != null ? snapshot.screenBrightness : 0);

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
        // APP-LEVEL ANALYSIS (Group D) - Query app usage during drain event
        // =====================================================================
        populateAppLevelAnalysis(event);

        // =====================================================================
        // NETWORK & RESOURCE TRACKING (Group F)
        // =====================================================================
        populateNetworkStats(event);

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
        if (snapshot.isScreenOnOrFalse()) {
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
        int bright = snapshot.screenBrightness != null ? snapshot.screenBrightness : 0;
        activeEvent.displayDrainEstimate = snapshot.isScreenOnOrFalse()
                ? (bright / 255f) * activeEvent.drainRatePerHour
                : 0f;
        activeEvent.cpuDrainEstimate = (snapshot.cpuLoadTotal != null && snapshot.cpuLoadTotal > 0)
                ? (snapshot.cpuLoadTotal / 100f) * activeEvent.drainRatePerHour
                : 0f;
        activeEvent.avgBrightness = bright;
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

    // =========================================================================
    // APP-LEVEL ANALYSIS (Group D) - Comprehensive app drain attribution
    // =========================================================================

    /**
     * Populate all app-level analysis fields for DrainEvent.
     * Queries AppBatteryUsageDao to get app usage data during the drain event timeframe.
     */
    private void populateAppLevelAnalysis(DrainEvent event) {
        try {
            // Get app usage during this drain event's timeframe
            List<AppBatteryUsage> appUsages = database.appBatteryUsageDao()
                    .getUsageSince(event.startTime);

            if (appUsages == null || appUsages.isEmpty()) {
                event.totalAppsContributing = 0;
                event.systemAppDrainPct = 0;
                event.userAppDrainPct = 0;
                event.drainDominant = "unknown";
                return;
            }

            // Aggregate by package name to avoid duplicates
            Map<String, AggregatedAppUsage> aggregatedUsage = new HashMap<>();
            for (AppBatteryUsage usage : appUsages) {
                String pkg = usage.packageName;
                AggregatedAppUsage agg = aggregatedUsage.get(pkg);
                if (agg == null) {
                    agg = new AggregatedAppUsage();
                    agg.packageName = pkg;
                    agg.appName = usage.appName;
                    agg.isSystemApp = usage.isSystemApp;
                    agg.appCategory = usage.appCategory;
                    agg.efficiencyRating = usage.efficiencyRating;
                    agg.hasExcessiveBackground = usage.hasExcessiveBackground;
                    aggregatedUsage.put(pkg, agg);
                }
                agg.totalDrain += usage.batteryPercentUsed;
                agg.sampleCount++;
                // Keep worst efficiency rating
                if ("problematic".equals(usage.efficiencyRating)) {
                    agg.efficiencyRating = "problematic";
                }
                if (usage.hasExcessiveBackground) {
                    agg.hasExcessiveBackground = true;
                }
            }

            // Convert to list and sort by drain
            List<AggregatedAppUsage> sortedApps = new ArrayList<>(aggregatedUsage.values());
            Collections.sort(sortedApps, (a, b) -> Float.compare(b.totalDrain, a.totalDrain));

            // Calculate totals
            float totalDrain = 0;
            float systemDrain = 0;
            float userDrain = 0;
            int systemAppCount = 0;
            int userAppCount = 0;

            for (AggregatedAppUsage agg : sortedApps) {
                totalDrain += agg.totalDrain;
                if (agg.isSystemApp) {
                    systemDrain += agg.totalDrain;
                    systemAppCount++;
                } else {
                    userDrain += agg.totalDrain;
                    userAppCount++;
                }
            }

            // Total unique apps contributing
            event.totalAppsContributing = sortedApps.size();

            // System vs user drain percentages
            if (totalDrain > 0) {
                event.systemAppDrainPct = (systemDrain / totalDrain) * 100f;
                event.userAppDrainPct = (userDrain / totalDrain) * 100f;
                event.systemAppDrain = systemDrain;
                event.userAppDrain = userDrain;
            } else {
                event.systemAppDrainPct = 0;
                event.userAppDrainPct = 0;
            }

            // Top app info
            if (!sortedApps.isEmpty()) {
                AggregatedAppUsage topApp = sortedApps.get(0);
                event.topAppPackage = topApp.packageName;
                event.topAppName = topApp.appName;
                event.topAppDrainPct = totalDrain > 0 ? 
                        (topApp.totalDrain / totalDrain) * 100f : 0;
                event.topAppIsSystem = topApp.isSystemApp;
            }

            // Drain dominant classification
            event.drainDominant = classifyDrainDominant(event.systemAppDrainPct, event.userAppDrainPct);

            // System vs user analysis text
            event.systemVsUserAnalysis = generateSystemVsUserAnalysis(
                    event.systemAppDrainPct, event.userAppDrainPct, systemAppCount, userAppCount);

            // Unattributed drain (difference between event drain and sum of app drains)
            if (event.drainAmount > 0 && totalDrain > 0) {
                float attributedPercent = Math.min(100f, totalDrain);
                event.unattributedDrain = Math.max(0, event.drainAmount - totalDrain);
                event.unattributedDrainPct = Math.max(0, 100f - attributedPercent);
            }

            // Build JSON arrays for detailed breakdown (using aggregated data)
            event.topDrainingApps = buildTopDrainingAppsJson(sortedApps, 10);
            event.appDrainBreakdownJson = buildAppDrainBreakdownJson(sortedApps);
            event.worstOffendersJson = buildWorstOffendersJson(sortedApps);

        } catch (Exception e) {
            // Silently handle - app analysis is supplementary
            event.totalAppsContributing = 0;
        }
    }

    /**
     * Helper class for aggregating app usage by package
     */
    private static class AggregatedAppUsage {
        String packageName;
        String appName;
        boolean isSystemApp;
        String appCategory;
        String efficiencyRating;
        boolean hasExcessiveBackground;
        float totalDrain;
        int sampleCount;
    }

    /**
     * Classify drain as system-dominant, user-dominant, or balanced
     */
    private String classifyDrainDominant(float systemPct, float userPct) {
        if (systemPct > userPct + 20) return "system";
        if (userPct > systemPct + 20) return "user";
        return "balanced";
    }

    /**
     * Generate human-readable analysis of system vs user app drain
     */
    private String generateSystemVsUserAnalysis(float systemPct, float userPct, 
            int systemCount, int userCount) {
        StringBuilder analysis = new StringBuilder();
        
        if (systemPct > userPct + 30) {
            analysis.append("System apps dominate drain (")
                    .append(String.format("%.0f%%", systemPct))
                    .append("). ");
            analysis.append(systemCount).append(" system apps vs ")
                    .append(userCount).append(" user apps contributing. ");
            analysis.append("This is typically normal behavior. ");
        } else if (userPct > systemPct + 30) {
            analysis.append("User apps dominate drain (")
                    .append(String.format("%.0f%%", userPct))
                    .append("). ");
            analysis.append(userCount).append(" user apps causing majority of drain. ");
            analysis.append("Consider restricting background activity for heavy drainers. ");
        } else {
            analysis.append("Balanced drain between system (")
                    .append(String.format("%.0f%%", systemPct))
                    .append(") and user (")
                    .append(String.format("%.0f%%", userPct))
                    .append(") apps. ");
        }
        
        return analysis.toString().trim();
    }

    /**
     * Build JSON array of top draining apps (using aggregated data)
     */
    private String buildTopDrainingAppsJson(List<AggregatedAppUsage> apps, int limit) {
        try {
            JSONArray array = new JSONArray();
            int count = 0;
            
            for (AggregatedAppUsage app : apps) {
                if (count >= limit) break;
                
                JSONObject obj = new JSONObject();
                obj.put("package", app.packageName);
                obj.put("name", app.appName != null ? app.appName : app.packageName);
                obj.put("drain", app.totalDrain);
                obj.put("isSystem", app.isSystemApp);
                obj.put("efficiency", app.efficiencyRating);
                array.put(obj);
                count++;
            }
            
            return array.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Build detailed JSON breakdown of all app drain (using aggregated data)
     */
    private String buildAppDrainBreakdownJson(List<AggregatedAppUsage> apps) {
        try {
            JSONObject breakdown = new JSONObject();
            
            // Group by app category
            Map<String, Float> categoryDrain = new HashMap<>();
            Map<String, Integer> categoryCount = new HashMap<>();
            
            for (AggregatedAppUsage app : apps) {
                String category = app.appCategory != null ? app.appCategory : "other";
                categoryDrain.merge(category, app.totalDrain, Float::sum);
                categoryCount.merge(category, 1, Integer::sum);
            }
            
            JSONObject categories = new JSONObject();
            for (Map.Entry<String, Float> entry : categoryDrain.entrySet()) {
                JSONObject cat = new JSONObject();
                cat.put("drain", entry.getValue());
                cat.put("count", categoryCount.get(entry.getKey()));
                categories.put(entry.getKey(), cat);
            }
            breakdown.put("byCategory", categories);
            
            // Top 5 individual apps
            JSONArray topApps = new JSONArray();
            int count = 0;
            for (AggregatedAppUsage app : apps) {
                if (count >= 5) break;
                JSONObject obj = new JSONObject();
                obj.put("package", app.packageName);
                obj.put("drain", app.totalDrain);
                topApps.put(obj);
                count++;
            }
            breakdown.put("topApps", topApps);
            
            return breakdown.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Build JSON array of worst offending apps (using aggregated data)
     */
    private String buildWorstOffendersJson(List<AggregatedAppUsage> apps) {
        try {
            JSONArray array = new JSONArray();
            
            for (AggregatedAppUsage app : apps) {
                // Include apps with problematic efficiency or excessive background
                if ("problematic".equals(app.efficiencyRating) || 
                    app.hasExcessiveBackground ||
                    app.totalDrain > 5.0f) {
                    
                    JSONObject offender = new JSONObject();
                    offender.put("package", app.packageName);
                    offender.put("name", app.appName);
                    offender.put("drain", app.totalDrain);
                    offender.put("efficiency", app.efficiencyRating);
                    offender.put("excessiveBackground", app.hasExcessiveBackground);
                    
                    List<String> reasons = new ArrayList<>();
                    if ("problematic".equals(app.efficiencyRating)) {
                        reasons.add("Poor efficiency rating");
                    }
                    if (app.hasExcessiveBackground) {
                        reasons.add("Excessive background activity");
                    }
                    if (app.totalDrain > 5.0f) {
                        reasons.add("High battery usage (>" + String.format("%.1f", app.totalDrain) + "%)");
                    }
                    offender.put("reasons", new JSONArray(reasons));
                    
                    array.put(offender);
                    
                    if (array.length() >= 10) break; // Limit to 10 offenders
                }
            }
            
            return array.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    // =========================================================================
    // NETWORK & RESOURCE TRACKING (Group F)
    // =========================================================================

    /**
     * Populate network statistics (dataSent, dataReceived) for the drain event.
     * Calculates bytes transferred since drain tracking started.
     */
    private void populateNetworkStats(DrainEvent event) {
        try {
            // Get current total bytes
            long currentTxBytes = TrafficStats.getTotalTxBytes();
            long currentRxBytes = TrafficStats.getTotalRxBytes();
            
            // Calculate delta since drain start
            if (currentTxBytes != TrafficStats.UNSUPPORTED && networkTxBytesStart > 0) {
                event.dataSent = Math.max(0, currentTxBytes - networkTxBytesStart);
            }
            
            if (currentRxBytes != TrafficStats.UNSUPPORTED && networkRxBytesStart > 0) {
                event.dataReceived = Math.max(0, currentRxBytes - networkRxBytesStart);
            }
            
        } catch (Exception e) {
            // TrafficStats not available - leave as 0
            event.dataSent = 0;
            event.dataReceived = 0;
        }
    }
}
