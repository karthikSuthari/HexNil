package com.example.hexnil.manager;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates remediation steps based on issue classification and trend insights.
 */
public class RecommendationEngine {

    public static class Recommendation {
        public String recommendationsJson;
        public String primaryRecommendation;
        public String userActions;
        public String engineerActions;
    }

    public Recommendation buildRecommendations(IssueClassifier.Result classification, TrendAnalyzer.Trend trend) {
        List<String> recs = new ArrayList<>();
        List<String> user = new ArrayList<>();
        List<String> eng = new ArrayList<>();

        if (classification == null) classification = new IssueClassifier.Result();
        if (trend == null) trend = new TrendAnalyzer.Trend();

        if ("hardware".equals(classification.issueCategory)) {
            recs.add("Reduce thermal load, verify charger quality, and inspect battery health");
            eng.add("Review charging current limits and thermal throttling policies");
            user.add("Avoid fast charging in hot environments; remove case during charge");
        } else if ("firmware".equals(classification.issueCategory)) {
            recs.add("Run a full calibration cycle and collect updated health metrics");
            eng.add("Validate fuel gauge calibration tables and SOC smoothing");
            user.add("Charge to 100% then discharge to 5% once to recalibrate");
        } else {
            recs.add("Identify and restrict top draining apps and background services");
            user.add("Limit background activity for problematic apps");
        }

        if (trend.isDegrading) {
            recs.add("Trend indicates degradation; schedule follow-up analysis in 24h");
            eng.add("Compare pre/post update metrics and check regression dashboards");
        }

        if (classification.riskScore > 70) {
            user.add("Stop heavy usage until temperature and drain stabilize");
            eng.add("Collect thermal traces and charging session logs for lab review");
        }

        Recommendation recommendation = new Recommendation();
        recommendation.recommendationsJson = toJsonArray(recs);
        recommendation.primaryRecommendation = recs.isEmpty() ? "monitor" : recs.get(0);
        recommendation.userActions = toJsonArray(user);
        recommendation.engineerActions = toJsonArray(eng);
        return recommendation;
    }

    private String toJsonArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(values.get(i)).append('"');
        }
        sb.append(']');
        return sb.toString();
    }
}
