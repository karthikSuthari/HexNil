package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AIAnalysisResult;
import com.example.hexnil.model.AnomalyRecord;
import com.example.hexnil.model.RollupMetrics;
import com.example.hexnil.model.AppDrainRank;
import com.example.hexnil.model.CalibrationData;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.UpdateComparison;
import java.util.List;

/**
 * Orchestrates AI/ML style heuristics by combining classification, trends, and recommendations.
 */
public class HexNilAI {

    private static final String TAG = "HexNilAI";
    
    private final HexNilDatabase database;
    private final IssueClassifier issueClassifier;
    private final TrendAnalyzer trendAnalyzer;
    private final RecommendationEngine recommendationEngine;
    private final MetricsRollupManager rollupManager;
    private final RiskEvaluator riskEvaluator;
    private final AppDrainRanker drainRanker;
    private final AnomalyDetector anomalyDetector;

    public HexNilAI(Context context, HexNilDatabase database) {
        this.database = database;
        this.issueClassifier = new IssueClassifier(database);
        this.trendAnalyzer = new TrendAnalyzer(database);
        this.recommendationEngine = new RecommendationEngine();
        this.rollupManager = new MetricsRollupManager(database);
        this.riskEvaluator = new RiskEvaluator();
        this.drainRanker = new AppDrainRanker(database);
        this.anomalyDetector = new AnomalyDetector(context, database);
    }

    public AIAnalysisResult runComprehensiveAnalysis(String periodLabel, long startTime) {
        long analysisStart = System.currentTimeMillis();
        long now = System.currentTimeMillis();
        
        // Run anomaly detection and store findings
        try {
            List<AnomalyRecord> anomalies = anomalyDetector.detectAnomalies(startTime, now);
            if (anomalies != null && !anomalies.isEmpty()) {
                Log.i(TAG, "AI detected " + anomalies.size() + " anomalies during analysis");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during anomaly detection", e);
        }

        RollupMetrics rollup = riskEvaluator.evaluate(
            rollupManager.rollup(periodLabel, startTime, now)
        );

        List<AppDrainRank> topApps = drainRanker.rank(periodLabel, startTime, now);
        Attribution attribution = computeAttribution(topApps);

        UpdateComparison updateVerdict = database.miscDao().getLatestUpdateComparison();
        CalibrationData calibration = database.miscDao().getLatestCalibration();
        BatteryHealth health = database.batteryHealthDao().getLatest();

        IssueClassifier.Result classification = issueClassifier.classify(startTime);
        TrendAnalyzer.Trend trend = trendAnalyzer.analyze(now);
        RecommendationEngine.Recommendation rec = recommendationEngine.buildRecommendations(classification, trend);

        AIAnalysisResult result = new AIAnalysisResult();
        result.analysisStartTime = analysisStart;
        result.analysisEndTime = System.currentTimeMillis();
        result.analysisType = "comprehensive";
        result.analysisPeriod = periodLabel;
        result.dataPointsAnalyzed = database.batterySnapshotDao().getCountSince(startTime);

        // =========================================================================
        // AI READINESS - Anti-Hallucination Gate
        // =========================================================================
        int snapshotsAvailable = result.dataPointsAnalyzed;
        int snapshotsRequired = 10;
        long observationHoursAvailable = (now - startTime) / (1000 * 3600);
        long observationHoursRequired = 4;
        int chargingSessions = database.chargingSessionDao().getCountSince(startTime);
        int drainEvents = database.drainEventDao().getCountSince(startTime);
        
        result.snapshotsAvailable = snapshotsAvailable;
        result.snapshotsRequired = snapshotsRequired;
        result.observationHoursAvailable = observationHoursAvailable;
        result.observationHoursRequired = observationHoursRequired;
        result.chargingSessionsAvailable = chargingSessions;
        result.drainEventsAvailable = drainEvents;
        
        // Calculate readiness score
        float snapshotScore = Math.min(1f, (float) snapshotsAvailable / snapshotsRequired);
        float hoursScore = Math.min(1f, (float) observationHoursAvailable / observationHoursRequired);
        float dataScore = Math.min(1f, (chargingSessions + drainEvents) / 5f);
        result.aiReadinessScore = (snapshotScore + hoursScore + dataScore) / 3f;
        
        result.aiWasAllowedToRun = snapshotsAvailable >= snapshotsRequired && 
                                    observationHoursAvailable >= observationHoursRequired;
        
        if (!result.aiWasAllowedToRun) {
            StringBuilder reason = new StringBuilder();
            if (snapshotsAvailable < snapshotsRequired) {
                reason.append("Insufficient snapshots (").append(snapshotsAvailable)
                      .append("/").append(snapshotsRequired).append("). ");
            }
            if (observationHoursAvailable < observationHoursRequired) {
                reason.append("Insufficient observation time (").append(observationHoursAvailable)
                      .append("/").append(observationHoursRequired).append("h). ");
            }
            result.aiBlockedReason = reason.toString().trim();
        }
        
        result.aiReadinessDetails = buildReadinessJson(snapshotsAvailable, snapshotsRequired, 
                observationHoursAvailable, observationHoursRequired, chargingSessions, drainEvents);

        // =========================================================================
        // DATA QUALITY METRICS
        // =========================================================================
        float completeness = calculateDataCompleteness(startTime, now);
        float consistency = calculateDataConsistency(startTime, now);
        float coverage = calculateDataCoverage(startTime, now);
        
        result.dataCompletenessScore = completeness;
        result.dataConsistencyScore = consistency;
        result.dataCoverageScore = coverage;
        result.dataQualityGrade = gradeFromScore((completeness + consistency + coverage) / 3f);

        // =========================================================================
        // DETERMINISTIC RESULTS - Exact calculations
        // =========================================================================
        DeterministicMetrics deterministic = calculateDeterministicMetrics(startTime, now);
        result.tempMinCelsius = deterministic.tempMin;
        result.tempMaxCelsius = deterministic.tempMax;
        result.tempMeanCelsius = deterministic.tempMean;
        result.tempStdDevCelsius = deterministic.tempStdDev;
        result.voltageMinMv = deterministic.voltageMin;
        result.voltageMaxMv = deterministic.voltageMax;
        result.voltageMeanMv = deterministic.voltageMean;
        result.drainRateMeanPerHour = deterministic.drainRateMean;
        result.drainRateMaxPerHour = deterministic.drainRateMax;
        result.drainRateStdDev = deterministic.drainRateStdDev;
        result.equivalentChargeCycles = deterministic.equivalentCycles;
        result.fullDischargeCount = deterministic.fullDischargeCount;
        result.screenOnTimeMs = deterministic.screenOnTimeMs;
        result.chargingTimeMs = deterministic.chargingTimeMs;
        result.highTempTimeMs = deterministic.highTempTimeMs;
        result.deterministicResults = buildDeterministicJson(deterministic);

        // =========================================================================
        // AI INFERENCES (only if allowed to run)
        // =========================================================================
        if (result.aiWasAllowedToRun) {
            // Health pattern
            result.healthPatternInference = inferHealthPattern(health, deterministic);
            result.healthPatternConfidence = health != null ? Math.min(0.9f, health.overallConfidence / 100f) : 0.5f;
            
            // Usage pattern
            UsagePattern usage = inferUsagePattern(deterministic, rollup);
            result.usagePatternInference = usage.inference;
            result.usagePatternConfidence = usage.confidence;
            result.usagePatternCategory = usage.category;
            
            // Thermal stress
            ThermalStress thermal = inferThermalStress(deterministic);
            result.thermalStressInference = thermal.inference;
            result.thermalStressConfidence = thermal.confidence;
            result.thermalStressLevel = thermal.level;
            
            // Drain anomalies - check based on idle drain being higher than expected
            result.drainAnomaliesDetected = rollup.idleDrain > 2.0f || rollup.wakelockDrain > rollup.screenOnDrain;
            
            result.includesAIContent = true;
            result.aiInterpretations = buildAiInterpretationsJson(result);
        }

        result.issuesDetected = classification.issuesDetected;
        result.issueCount = classification.issueCount;
        result.detectedIssues = classification.detectedIssues != null ? classification.detectedIssues : toIssueJson(rollup.topIssues);
        result.primaryIssue = choosePrimaryIssue(classification, rollup, attribution, updateVerdict, calibration);
        if (topApps != null && !topApps.isEmpty()) {
            result.contributingFactors = toAppJson(topApps);
        }
        result.issueCategory = classification.issueCategory != null ? classification.issueCategory : attribution.category;
        result.issueConfidence = 0.75f;
        result.rootCause = classification.rootCause != null ? classification.rootCause : rollup.attribution;
        
        // Enhanced root cause analysis
        result.rootCauseCode = mapToRootCauseCode(result.rootCause);
        result.rootCauseConfidence = result.aiWasAllowedToRun ? 0.7f : 0.4f;
        result.rootCauseConfidenceLevel = result.rootCauseConfidence >= 0.7f ? "HIGH" : 
                                           result.rootCauseConfidence >= 0.5f ? "MODERATE" : "LOW";

        float combinedRisk = combineRisk(rollup, updateVerdict, calibration, health);
        result.riskScore = combinedRisk;
        result.severity = severityFromRisk(combinedRisk);
        result.requiresAction = combinedRisk >= 45;
        result.urgency = combinedRisk >= 75 ? "high" : combinedRisk >= 45 ? "medium" : "low";

        result.trend = trend.trendLabel;
        result.trendSlope = trend.drainTrend;
        result.trendPrediction = trend.isDegrading ? "expect further degradation" : "stable";

        result.recommendations = rec.recommendationsJson;
        result.primaryRecommendation = rec.primaryRecommendation;
        result.engineerActions = rec.engineerActions;
        result.userActions = rec.userActions;

        result.overallBatteryScore = Math.max(0, 100 - result.riskScore);
        result.healthScore = rollup.avgHealth > 0 ? rollup.avgHealth : result.overallBatteryScore;
        result.performanceScore = 100 - Math.max(0, trend.drainTrend * 5);
        result.safetyScore = rollup.maxTemp > 44f || rollup.fastChargeAbuse > 0 ? 55 : 85;
        result.reliabilityScore = trend.isDegrading ? 60 : 85;

        result.rootCauseExplanation = buildRootCauseExplanation(rollup, attribution, updateVerdict, calibration, health);
        result.contributingFactors = buildContributingFactors(rollup, attribution, topApps, updateVerdict, calibration, health);
        RecommendationPayload recPayload = buildRecommendationsPayload(rollup, attribution, updateVerdict, calibration, health, rec);
        result.recommendations = recPayload.recommendationsJson;
        result.primaryRecommendation = recPayload.primaryRecommendation;
        result.engineerActions = recPayload.engineerActions;
        result.userActions = recPayload.userActions;

        result.executiveSummary = buildExecutiveSummary(result, rollup, updateVerdict, calibration, health, attribution);
        result.technicalSummary = buildTechnicalSummary(result, trend, rollup, updateVerdict, calibration, health, attribution);
        result.oemReportData = recPayload.oemReportData;
        
        // Confidence metrics
        result.overallConfidenceScore = result.aiReadinessScore;
        result.overallConfidenceLevel = result.overallConfidenceScore >= 0.8f ? "HIGH" : 
                                         result.overallConfidenceScore >= 0.6f ? "MODERATE" : "LOW";
        result.evidencePointCount = snapshotsAvailable + chargingSessions + drainEvents;

        database.miscDao().insertAIAnalysis(result);
        return result;
    }
    
    // =========================================================================
    // AI READINESS HELPERS
    // =========================================================================
    
    private String buildReadinessJson(int snapshots, int snapshotsReq, long hours, long hoursReq, int charging, int drain) {
        return String.format("{\"snapshots\":{\"available\":%d,\"required\":%d},\"hours\":{\"available\":%d,\"required\":%d},\"sessions\":{\"charging\":%d,\"drain\":%d}}",
                snapshots, snapshotsReq, hours, hoursReq, charging, drain);
    }
    
    private float calculateDataCompleteness(long startTime, long endTime) {
        int snapshots = database.batterySnapshotDao().getCountSince(startTime);
        long expectedSnapshots = (endTime - startTime) / (5 * 60 * 1000); // Every 5 min
        return Math.min(1f, (float) snapshots / Math.max(1, expectedSnapshots));
    }
    
    private float calculateDataConsistency(long startTime, long endTime) {
        // Check for gaps in data
        // For now, use a simple heuristic
        return 0.85f; // Placeholder - could analyze timestamp gaps
    }
    
    private float calculateDataCoverage(long startTime, long endTime) {
        int charging = database.chargingSessionDao().getCountSince(startTime);
        int drain = database.drainEventDao().getCountSince(startTime);
        int idle = database.idleDrainSessionDao().getCountSince(startTime);
        return Math.min(1f, (charging + drain + idle) / 10f);
    }
    
    private String gradeFromScore(float score) {
        if (score >= 0.9f) return "A";
        if (score >= 0.8f) return "B";
        if (score >= 0.7f) return "C";
        if (score >= 0.6f) return "D";
        return "F";
    }
    
    // =========================================================================
    // DETERMINISTIC CALCULATIONS
    // =========================================================================
    
    private DeterministicMetrics calculateDeterministicMetrics(long startTime, long endTime) {
        DeterministicMetrics m = new DeterministicMetrics();
        
        try {
            List<com.example.hexnil.model.BatterySnapshot> snapshots = 
                    database.batterySnapshotDao().getSnapshotsSince(startTime);
            
            if (snapshots == null || snapshots.isEmpty()) return m;
            
            float tempSum = 0, tempSumSq = 0;
            int voltageSum = 0;
            int tempMin = Integer.MAX_VALUE, tempMax = Integer.MIN_VALUE;
            int voltageMin = Integer.MAX_VALUE, voltageMax = Integer.MIN_VALUE;
            
            for (com.example.hexnil.model.BatterySnapshot s : snapshots) {
                float tempC = s.temperature / 10f;
                tempSum += tempC;
                tempSumSq += tempC * tempC;
                tempMin = Math.min(tempMin, s.temperature);
                tempMax = Math.max(tempMax, s.temperature);
                
                voltageSum += s.voltage;
                voltageMin = Math.min(voltageMin, s.voltage);
                voltageMax = Math.max(voltageMax, s.voltage);
                
                if (tempC > 40) m.highTempTimeMs += 300000; // 5 min per snapshot
                if (s.isCharging) m.chargingTimeMs += 300000;
                else m.screenOnTimeMs += 300000; // Approximation
            }
            
            int n = snapshots.size();
            m.tempMean = tempSum / n;
            m.tempMin = tempMin / 10f;
            m.tempMax = tempMax / 10f;
            float variance = (tempSumSq / n) - (m.tempMean * m.tempMean);
            m.tempStdDev = (float) Math.sqrt(Math.max(0, variance));
            
            m.voltageMean = voltageSum / n;
            m.voltageMin = voltageMin;
            m.voltageMax = voltageMax;
            
            // Calculate drain rate
            if (n >= 2) {
                com.example.hexnil.model.BatterySnapshot first = snapshots.get(0);
                com.example.hexnil.model.BatterySnapshot last = snapshots.get(n - 1);
                float hours = (last.timestamp - first.timestamp) / (1000f * 3600f);
                if (hours > 0) {
                    m.drainRateMean = (first.level - last.level) / hours;
                }
            }
            
            // Get cycle data from health
            BatteryHealth health = database.batteryHealthDao().getLatest();
            if (health != null) {
                m.equivalentCycles = health.cycleCount;
                m.fullDischargeCount = health.deepCycleCount;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error calculating deterministic metrics", e);
        }
        
        return m;
    }
    
    private String buildDeterministicJson(DeterministicMetrics m) {
        return String.format("{\"temp\":{\"min\":%.1f,\"max\":%.1f,\"mean\":%.1f,\"stdDev\":%.2f},\"voltage\":{\"min\":%d,\"max\":%d,\"mean\":%d},\"drain\":{\"mean\":%.2f,\"max\":%.2f},\"cycles\":%.1f}",
                m.tempMin, m.tempMax, m.tempMean, m.tempStdDev,
                m.voltageMin, m.voltageMax, m.voltageMean,
                m.drainRateMean, m.drainRateMax, m.equivalentCycles);
    }
    
    // =========================================================================
    // AI INFERENCES
    // =========================================================================
    
    private String inferHealthPattern(BatteryHealth health, DeterministicMetrics m) {
        if (health == null) return "Insufficient health data";
        
        if (health.healthPercentage >= 90) {
            return "Excellent battery health. Capacity retention is optimal.";
        } else if (health.healthPercentage >= 80) {
            return "Good battery health. Minor capacity degradation within normal range.";
        } else if (health.healthPercentage >= 70) {
            return "Moderate capacity loss detected. Consider monitoring charging habits.";
        } else {
            return "Significant capacity degradation. Battery replacement may be needed.";
        }
    }
    
    private UsagePattern inferUsagePattern(DeterministicMetrics m, RollupMetrics rollup) {
        UsagePattern p = new UsagePattern();
        
        float drainRate = m.drainRateMean;
        float screenOnHours = m.screenOnTimeMs / (1000f * 3600f);
        
        if (drainRate > 10f || screenOnHours > 8f) {
            p.category = "power_user";
            p.inference = "High usage pattern detected. Extended screen time and intensive app usage.";
            p.confidence = 0.8f;
        } else if (drainRate > 5f || screenOnHours > 4f) {
            p.category = "heavy_user";
            p.inference = "Above average usage. Multiple apps active throughout the day.";
            p.confidence = 0.75f;
        } else if (drainRate > 2f) {
            p.category = "moderate_user";
            p.inference = "Moderate usage pattern. Balanced screen time and background activity.";
            p.confidence = 0.8f;
        } else {
            p.category = "light_user";
            p.inference = "Light usage pattern. Minimal active usage, mostly idle.";
            p.confidence = 0.85f;
        }
        
        return p;
    }
    
    private ThermalStress inferThermalStress(DeterministicMetrics m) {
        ThermalStress t = new ThermalStress();
        
        if (m.tempMax > 45f || m.tempMean > 38f) {
            t.level = "CRITICAL";
            t.inference = "Critical thermal stress detected. Sustained high temperatures may damage battery.";
            t.confidence = 0.9f;
        } else if (m.tempMax > 40f || m.tempMean > 35f) {
            t.level = "HIGH";
            t.inference = "Elevated thermal stress. Device running warmer than optimal.";
            t.confidence = 0.85f;
        } else if (m.tempMax > 35f || m.tempMean > 30f) {
            t.level = "MODERATE";
            t.inference = "Moderate thermal conditions. Within acceptable range.";
            t.confidence = 0.8f;
        } else {
            t.level = "LOW";
            t.inference = "Optimal thermal conditions. No thermal stress detected.";
            t.confidence = 0.9f;
        }
        
        return t;
    }
    
    private String buildAiInterpretationsJson(AIAnalysisResult r) {
        return String.format("{\"health\":{\"inference\":\"%s\",\"confidence\":%.2f},\"usage\":{\"inference\":\"%s\",\"category\":\"%s\",\"confidence\":%.2f},\"thermal\":{\"level\":\"%s\",\"inference\":\"%s\",\"confidence\":%.2f}}",
                escape(r.healthPatternInference != null ? r.healthPatternInference : ""), r.healthPatternConfidence,
                escape(r.usagePatternInference != null ? r.usagePatternInference : ""), r.usagePatternCategory != null ? r.usagePatternCategory : "", r.usagePatternConfidence,
                r.thermalStressLevel != null ? r.thermalStressLevel : "", escape(r.thermalStressInference != null ? r.thermalStressInference : ""), r.thermalStressConfidence);
    }
    
    private String mapToRootCauseCode(String rootCause) {
        if (rootCause == null) return "UNKNOWN";
        String lower = rootCause.toLowerCase();
        if (lower.contains("thermal") || lower.contains("temp")) return "THERMAL_DEGRADATION";
        if (lower.contains("drain") || lower.contains("wakelock")) return "EXCESSIVE_DRAIN";
        if (lower.contains("network")) return "NETWORK_ACTIVITY";
        if (lower.contains("calibrat")) return "CALIBRATION_ISSUE";
        if (lower.contains("aging") || lower.contains("health")) return "BATTERY_AGING";
        if (lower.contains("update") || lower.contains("ota")) return "SOFTWARE_REGRESSION";
        return "MIXED_FACTORS";
    }
    
    // Helper classes
    private static class DeterministicMetrics {
        float tempMin, tempMax, tempMean, tempStdDev;
        int voltageMin, voltageMax, voltageMean;
        float drainRateMean, drainRateMax, drainRateStdDev;
        float equivalentCycles;
        int fullDischargeCount;
        long screenOnTimeMs, chargingTimeMs, highTempTimeMs;
    }
    
    private static class UsagePattern {
        String inference;
        String category;
        float confidence;
    }
    
    private static class ThermalStress {
        String inference;
        String level;
        float confidence;
    }

    private String toIssueJson(String[] issues) {
        if (issues == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String issue : issues) {
            if (issue == null) continue;
            if (!first) sb.append(',');
            sb.append('"').append(issue).append('"');
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private String toAppJson(List<AppDrainRank> ranks) {
        if (ranks == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (AppDrainRank r : ranks) {
            if (!first) sb.append(',');
            sb.append('{')
              .append("\"pkg\":\"").append(r.packageName).append("\"")
              .append(",\"rank\":").append(r.rank)
              .append(",\"contrib\":").append(r.contributionPct)
              .append('}');
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private String buildExecutiveSummary(AIAnalysisResult result, RollupMetrics rollup, UpdateComparison updateVerdict,
                                         CalibrationData calibration, BatteryHealth health, Attribution attribution) {
        String updateNote = updateVerdict != null ? ("update " + updateVerdict.overallImpact + " (conf " + String.format("%.0f", updateVerdict.confidenceLevel) + "%)") : "no recent OTA verdict";
        String calibNote = calibration != null && calibration.needsCalibration ? "calibration recommended" : "calibration ok";
        String agingNote = health != null ? String.format("health %.0f%%, cycles %.0f", health.healthPercentage, health.cycleCount) : "health n/a";
        return "Risk=" + String.format("%.0f", result.riskScore) + " (" + result.severity + ") | "
                + "Drain so/idle=" + String.format("%.1f/%.1f", rollup.screenOnDrain, rollup.idleDrain) + " | "
                + "Top attribution: " + attribution.summary + " | " + updateNote + " | " + calibNote + " | " + agingNote;
    }

    private String buildTechnicalSummary(AIAnalysisResult result, TrendAnalyzer.Trend trend, RollupMetrics rollup,
                                         UpdateComparison updateVerdict, CalibrationData calibration, BatteryHealth health,
                                         Attribution attribution) {
        StringBuilder sb = new StringBuilder();
        sb.append("risk=").append(String.format("%.1f", result.riskScore))
          .append(" wakelockDrain=").append(String.format("%.2f", rollup.wakelockDrain))
          .append(" networkDrain=").append(String.format("%.2f", rollup.networkDrain))
          .append(" systemShare=").append(String.format("%.1f", attribution.systemPct))
          .append(" userShare=").append(String.format("%.1f", attribution.userPct))
          .append(" trend=").append(trend.trendLabel)
          .append(" drainTrend=").append(String.format("%.2f", trend.drainTrend));
        if (updateVerdict != null) {
            sb.append(" OTA=").append(updateVerdict.overallImpact)
              .append(" dDrain=").append(String.format("%.2f", updateVerdict.drainRateDelta))
              .append(" idle=").append(String.format("%.2f", updateVerdict.idleDrainDelta));
        }
        if (calibration != null) {
            sb.append(" calibDrift=").append(String.format("%.1f", calibration.levelDrift))
              .append(" correctedSOC=").append(String.format("%.1f", calibration.correctedSoc))
              .append(" conf=").append(String.format("%.0f", calibration.correctionConfidence));
        }
        if (health != null) {
            sb.append(" health=").append(String.format("%.0f", health.healthPercentage))
              .append(" fade100c=").append(String.format("%.1f", health.fadePer100Cycles))
              .append(" cycles=").append(String.format("%.0f", health.cycleCount));
        }
        return sb.toString();
    }

    private float combineRisk(RollupMetrics rollup, UpdateComparison updateVerdict,
                              CalibrationData calibration, BatteryHealth health) {
        float risk = rollup != null ? rollup.riskScore : 0f;
        if (updateVerdict != null && "degraded".equalsIgnoreCase(updateVerdict.overallImpact)) {
            risk += 15f + clamp(Math.abs(updateVerdict.drainRateDelta) * 2f, 0f, 15f);
        }
        if (calibration != null) {
            if (calibration.driftExceeded) risk += 12f;
            if (calibration.hasSuddenDrops) risk += 8f;
            if (calibration.needsCalibration) risk += 5f;
        }
        if (health != null) {
            if (health.healthPercentage < 80f) risk += 10f;
            if (health.fadePer100Cycles > 5f) risk += 8f;
            if (health.cycleCount > 500) risk += 5f;
            if (health.hasAbnormalAging) risk += 5f;
        }
        return clamp(risk, 0f, 100f);
    }

    private String severityFromRisk(float risk) {
        if (risk >= 80) return "critical";
        if (risk >= 60) return "high";
        if (risk >= 40) return "medium";
        if (risk >= 20) return "low";
        return "info";
    }

    private String buildRootCauseExplanation(RollupMetrics rollup, Attribution attribution,
                                             UpdateComparison updateVerdict, CalibrationData calibration,
                                             BatteryHealth health) {
        StringBuilder sb = new StringBuilder();
        sb.append("Drain drivers: wakelock ").append(String.format("%.2f", rollup.wakelockDrain))
          .append("%/h, network ").append(String.format("%.2f", rollup.networkDrain))
          .append("%/h; system vs user drain ").append(String.format("%.1f", attribution.systemPct))
          .append("/%").append(String.format("%.1f", attribution.userPct)).append(".");
        if (updateVerdict != null) {
            sb.append(" OTA verdict: ").append(updateVerdict.overallImpact)
              .append(" (drain Δ=").append(String.format("%.2f", updateVerdict.drainRateDelta))
              .append("%/h, idle Δ=").append(String.format("%.2f", updateVerdict.idleDrainDelta))
              .append(", conf ").append(String.format("%.0f", updateVerdict.confidenceLevel)).append("%).");
        }
        if (calibration != null) {
            sb.append(" Calibration: drift ").append(String.format("%.1f", calibration.levelDrift))
              .append("%, corrected SOC ").append(String.format("%.1f", calibration.correctedSoc))
              .append(" (conf ").append(String.format("%.0f", calibration.correctionConfidence)).append("%).");
        }
        if (health != null) {
            sb.append(" Aging: health ").append(String.format("%.0f", health.healthPercentage))
              .append("%, fade/100c ").append(String.format("%.1f", health.fadePer100Cycles))
              .append(", cycles ").append(String.format("%.0f", health.cycleCount)).append(".");
        }
        return sb.toString();
    }

    private String buildContributingFactors(RollupMetrics rollup, Attribution attribution, List<AppDrainRank> topApps,
                                            UpdateComparison updateVerdict, CalibrationData calibration, BatteryHealth health) {
        StringBuilder sb = new StringBuilder();
        sb.append('{')
          .append("\"wakelockDrain\":").append(formatFloat(rollup.wakelockDrain)).append(',')
          .append("\"networkDrain\":").append(formatFloat(rollup.networkDrain)).append(',')
          .append("\"systemShare\":").append(formatFloat(attribution.systemPct)).append(',')
          .append("\"userShare\":").append(formatFloat(attribution.userPct)).append(',')
          .append("\"topApps\":").append(toAppJson(topApps));
        if (updateVerdict != null) {
            sb.append(',').append("\"updateImpact\":{\"verdict\":\"")
              .append(updateVerdict.overallImpact).append("\",\"drainDelta\":")
              .append(formatFloat(updateVerdict.drainRateDelta)).append(",\"idleDelta\":")
              .append(formatFloat(updateVerdict.idleDrainDelta)).append(",\"confidence\":")
              .append(formatFloat(updateVerdict.confidenceLevel)).append('}');
        }
        if (calibration != null) {
            sb.append(',').append("\"calibration\":{\"drift\":")
              .append(formatFloat(calibration.levelDrift)).append(",\"correctedSoc\":")
              .append(formatFloat(calibration.correctedSoc)).append(",\"confidence\":")
              .append(formatFloat(calibration.correctionConfidence)).append("}");
        }
        if (health != null) {
            sb.append(',').append("\"aging\":{\"health\":")
              .append(formatFloat(health.healthPercentage)).append(",\"fadePer100c\":")
              .append(formatFloat(health.fadePer100Cycles)).append(",\"cycles\":")
              .append(formatFloat(health.cycleCount)).append("}");
        }
        sb.append('}');
        return sb.toString();
    }

    private RecommendationPayload buildRecommendationsPayload(RollupMetrics rollup, Attribution attribution,
                                                              UpdateComparison updateVerdict, CalibrationData calibration,
                                                              BatteryHealth health, RecommendationEngine.Recommendation rec) {
        StringBuilder user = new StringBuilder();
        StringBuilder engineer = new StringBuilder();

        if (rollup.wakelockDrain > 0.5f) {
            user.append("Reduce apps holding wakelocks; disable aggressive background sync. ");
            engineer.append("Audit wakelock-heavy services; add job batching. ");
        }
        if (rollup.networkDrain > 0.5f) {
            user.append("Limit background data for top drain apps. ");
            engineer.append("Check radio paging/scheduling; optimize push heartbeat. ");
        }
        if (attribution.userPct > attribution.systemPct) {
            user.append("Uninstall/limit top user apps contributing ")
                .append(String.format("%.1f", attribution.userPct)).append("% drain. ");
        } else {
            engineer.append("System services contribute ").append(String.format("%.1f", attribution.systemPct))
                .append("% drain; profile system components. ");
        }
        if (updateVerdict != null && "degraded".equalsIgnoreCase(updateVerdict.overallImpact)) {
            engineer.append("OTA regression: drain Δ=")
                    .append(String.format("%.2f", updateVerdict.drainRateDelta)).append("%/h; patch or rollback. ");
        }
        if (calibration != null && calibration.needsCalibration) {
            user.append("Run calibration: discharge to 5% then charge to 100% (drift ")
                .append(String.format("%.1f", calibration.levelDrift)).append("%). ");
        }
        if (health != null && health.healthPercentage < 80f) {
            user.append("Battery aging noted (health ")
                .append(String.format("%.0f", health.healthPercentage)).append("%); avoid deep cycles. ");
            engineer.append("Monitor aging/cycle drift; fade/100c=")
                .append(String.format("%.1f", health.fadePer100Cycles)).append(". ");
        }

        // Merge with existing generic recs
        if (rec != null) {
            if (rec.userActions != null) user.append(rec.userActions).append(' ');
            if (rec.engineerActions != null) engineer.append(rec.engineerActions).append(' ');
        }

        String primary = user.length() > 0 ? user.toString().trim() : engineer.toString().trim();
        String recommendationsJson = toRecommendationsJson(user.toString().trim(), engineer.toString().trim());
        String oemData = buildOemReportData(rollup, attribution, updateVerdict, calibration, health);

        RecommendationPayload payload = new RecommendationPayload();
        payload.primaryRecommendation = primary;
        payload.userActions = user.toString().trim();
        payload.engineerActions = engineer.toString().trim();
        payload.recommendationsJson = recommendationsJson;
        payload.oemReportData = oemData;
        return payload;
    }

    private String toRecommendationsJson(String userActions, String engineerActions) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        if (!userActions.isEmpty()) {
            sb.append("{\"audience\":\"user\",\"action\":\"").append(escape(userActions)).append("\"}");
            first = false;
        }
        if (!engineerActions.isEmpty()) {
            if (!first) sb.append(',');
            sb.append("{\"audience\":\"engineer\",\"action\":\"").append(escape(engineerActions)).append("\"}");
        }
        sb.append(']');
        return sb.toString();
    }

    private String buildOemReportData(RollupMetrics rollup, Attribution attribution,
                                      UpdateComparison updateVerdict, CalibrationData calibration,
                                      BatteryHealth health) {
        StringBuilder sb = new StringBuilder();
        sb.append('{')
          .append("\"drain\":{\"screenOn\":").append(formatFloat(rollup.screenOnDrain))
          .append(",\"idle\":").append(formatFloat(rollup.idleDrain))
          .append(",\"wakelock\":").append(formatFloat(rollup.wakelockDrain))
          .append(",\"network\":").append(formatFloat(rollup.networkDrain)).append("}")
          .append(',').append("\"attribution\":{\"system\":").append(formatFloat(attribution.systemPct))
          .append(",\"user\":").append(formatFloat(attribution.userPct)).append("}");
        if (updateVerdict != null) {
            sb.append(',').append("\"update\":{\"verdict\":\"")
              .append(updateVerdict.overallImpact).append("\",\"drainDelta\":")
              .append(formatFloat(updateVerdict.drainRateDelta)).append(",\"confidence\":")
              .append(formatFloat(updateVerdict.confidenceLevel)).append("}");
        }
        if (calibration != null) {
            sb.append(',').append("\"calibration\":{\"drift\":")
              .append(formatFloat(calibration.levelDrift)).append(",\"corrected\":")
              .append(formatFloat(calibration.correctedSoc)).append(",\"conf\":")
              .append(formatFloat(calibration.correctionConfidence)).append("}");
        }
        if (health != null) {
            sb.append(',').append("\"aging\":{\"health\":")
              .append(formatFloat(health.healthPercentage)).append(",\"fadePer100c\":")
              .append(formatFloat(health.fadePer100Cycles)).append(",\"cycles\":")
              .append(formatFloat(health.cycleCount)).append("}");
        }
        sb.append('}');
        return sb.toString();
    }

    private Attribution computeAttribution(List<AppDrainRank> topApps) {
        Attribution a = new Attribution();
        if (topApps == null || topApps.isEmpty()) {
            a.summary = "no apps";
            a.category = "firmware";
            return a;
        }
        float system = 0f;
        float user = 0f;
        for (AppDrainRank r : topApps) {
            boolean sys = isSystemApp(r.packageName);
            if (sys) system += r.contributionPct;
            else user += r.contributionPct;
            if (a.topApp == null) {
                a.topApp = r.packageName;
                a.topAppPct = r.contributionPct;
            }
        }
        float total = Math.max(1f, system + user);
        a.systemPct = (system / total) * 100f;
        a.userPct = (user / total) * 100f;
        a.summary = user >= system ? "user apps" : "system services";
        a.category = user >= system ? "app" : "system";
        return a;
    }

    private boolean isSystemApp(String pkg) {
        if (pkg == null) return false;
        return pkg.startsWith("com.android") || pkg.startsWith("com.google.android")
                || pkg.startsWith("com.qualcomm") || pkg.startsWith("com.samsung")
                || pkg.startsWith("miui") || pkg.startsWith("com.huawei");
    }

    private String choosePrimaryIssue(IssueClassifier.Result classification, RollupMetrics rollup,
                                      Attribution attribution, UpdateComparison updateVerdict,
                                      CalibrationData calibration) {
        if (classification.primaryIssue != null) return classification.primaryIssue;
        if (updateVerdict != null && "degraded".equalsIgnoreCase(updateVerdict.overallImpact)) {
            return "OTA regression";
        }
        if (calibration != null && calibration.needsCalibration) {
            return "Calibration drift";
        }
        if (rollup.wakelockDrain > 0.5f) return "Wakelock drain";
        if (rollup.networkDrain > 0.5f) return "Network drain";
        return attribution.summary;
    }

    private float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private String formatFloat(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return "0";
        return String.format("%.3f", value);
    }

    private String escape(String s) {
        return s.replace("\"", "'");
    }

    private static class Attribution {
        float systemPct;
        float userPct;
        String summary;
        String category;
        String topApp;
        float topAppPct;
    }

    private static class RecommendationPayload {
        String primaryRecommendation;
        String recommendationsJson;
        String engineerActions;
        String userActions;
        String oemReportData;
    }
}
