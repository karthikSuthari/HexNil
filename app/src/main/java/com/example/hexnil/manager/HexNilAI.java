package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AIAnalysisResult;
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

    private final HexNilDatabase database;
    private final IssueClassifier issueClassifier;
    private final TrendAnalyzer trendAnalyzer;
    private final RecommendationEngine recommendationEngine;
    private final MetricsRollupManager rollupManager;
    private final RiskEvaluator riskEvaluator;
    private final AppDrainRanker drainRanker;

    public HexNilAI(Context context, HexNilDatabase database) {
        this.database = database;
        this.issueClassifier = new IssueClassifier(database);
        this.trendAnalyzer = new TrendAnalyzer(database);
        this.recommendationEngine = new RecommendationEngine();
        this.rollupManager = new MetricsRollupManager(database);
        this.riskEvaluator = new RiskEvaluator();
        this.drainRanker = new AppDrainRanker(database);
    }

    public AIAnalysisResult runComprehensiveAnalysis(String periodLabel, long startTime) {
        long analysisStart = System.currentTimeMillis();
        long now = System.currentTimeMillis();

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

        database.miscDao().insertAIAnalysis(result);
        return result;
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
