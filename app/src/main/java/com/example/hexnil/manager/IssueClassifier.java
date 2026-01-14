package com.example.hexnil.manager;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AIAnalysisResult;

/**
 * Performs lightweight heuristic classification of battery issues using stored metrics.
 */
public class IssueClassifier {

    public static class Result {
        public boolean issuesDetected;
        public int issueCount;
        public String primaryIssue;
        public String issueCategory;
        public String severity;
        public float riskScore;
        public String detectedIssues;
        public String rootCause;
    }

    private final HexNilDatabase database;

    public IssueClassifier(HexNilDatabase database) {
        this.database = database;
    }

    public Result classify(long startTime) {
        Result result = new Result();

        Float avgScreenOnDrainObj = database.drainEventDao().getAverageScreenOnDrain(startTime);
        Float avgScreenOffDrainObj = database.drainEventDao().getAverageScreenOffDrain(startTime);
        Float maxPeakTempObj = database.thermalEventDao().getMaxPeakTemperature(startTime);
        
        // Handle null values with defaults
        float avgScreenOnDrain = avgScreenOnDrainObj != null ? avgScreenOnDrainObj : 0f;
        float avgScreenOffDrain = avgScreenOffDrainObj != null ? avgScreenOffDrainObj : 0f;
        float maxPeakTemp = maxPeakTempObj != null ? maxPeakTempObj : 0f;
        
        int criticalSafety = database.safetyEventDao().getCriticalEventCount();
        int abnormalWear = database.batteryHealthDao().getAbnormalWearRecords().size();

        boolean highDrain = avgScreenOnDrain > 18 || avgScreenOffDrain > 3.5f;
        boolean thermalRisk = maxPeakTemp > 43;
        boolean safetyRisk = criticalSafety > 0;
        boolean healthRisk = abnormalWear > 0;

        StringBuilder issues = new StringBuilder("[");
        int count = 0;
        if (highDrain) { issues.append("\"high_drain\""); count++; }
        if (thermalRisk) { if (count>0) issues.append(','); issues.append("\"thermal\""); count++; }
        if (safetyRisk) { if (count>0) issues.append(','); issues.append("\"safety\""); count++; }
        if (healthRisk) { if (count>0) issues.append(','); issues.append("\"health\""); count++; }
        issues.append(']');

        result.issueCount = count;
        result.issuesDetected = count > 0;
        result.detectedIssues = issues.toString();
        result.severity = deriveSeverity(highDrain, thermalRisk, safetyRisk, healthRisk);
        result.riskScore = computeRiskScore(highDrain, thermalRisk, safetyRisk, healthRisk, avgScreenOnDrain, maxPeakTemp);
        result.issueCategory = pickCategory(thermalRisk, safetyRisk, healthRisk);
        result.primaryIssue = pickPrimary(result);
        result.rootCause = deriveRootCause(result.issueCategory, avgScreenOnDrain, maxPeakTemp);

        return result;
    }

    private String deriveSeverity(boolean highDrain, boolean thermalRisk, boolean safetyRisk, boolean healthRisk) {
        if (safetyRisk) return "critical";
        if (thermalRisk) return "high";
        if (healthRisk) return "high";
        if (highDrain) return "medium";
        return "low";
    }

    private float computeRiskScore(boolean highDrain, boolean thermalRisk, boolean safetyRisk, boolean healthRisk,
                                   float avgScreenOnDrain, float maxPeakTemp) {
        float score = 0;
        if (safetyRisk) score += 40;
        if (thermalRisk) score += 30;
        if (healthRisk) score += 20;
        if (highDrain) score += 20;
        score += Math.min(20, Math.max(0, (avgScreenOnDrain - 15))); // scale drain
        score += Math.min(20, Math.max(0, (maxPeakTemp - 40))); // scale temp
        return Math.min(100, score);
    }

    private String pickCategory(boolean thermalRisk, boolean safetyRisk, boolean healthRisk) {
        if (safetyRisk || thermalRisk) return "hardware";
        if (healthRisk) return "firmware";
        return "app";
    }

    private String pickPrimary(Result r) {
        if ("hardware".equals(r.issueCategory)) {
            if ("critical".equals(r.severity)) return "safety_risk";
            return "thermal_instability";
        }
        if ("firmware".equals(r.issueCategory)) return "accelerated_wear";
        if ("app".equals(r.issueCategory)) return "excessive_drain";
        return "none";
    }

    private String deriveRootCause(String category, float avgScreenOnDrain, float maxPeakTemp) {
        switch (category) {
            case "hardware":
                return maxPeakTemp > 45 ? "potential cooling or charging fault" : "thermal stress during load";
            case "firmware":
                return "battery reporting drift or calibration needed";
            case "app":
                return avgScreenOnDrain > 20 ? "foreground or background app drain" : "background services";
            default:
                return "no_issue";
        }
    }
}
