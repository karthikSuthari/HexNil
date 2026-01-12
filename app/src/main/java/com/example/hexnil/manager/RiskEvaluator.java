package com.example.hexnil.manager;

import com.example.hexnil.model.RollupMetrics;

/**
 * Converts rollup metrics into severity and risk scoring.
 */
public class RiskEvaluator {

    public RollupMetrics evaluate(RollupMetrics r) {
        if (r == null) return null;
        float risk = 0f;
        risk += clamp(r.screenOnDrain - 12f, 0f, 20f) * 2f;
        risk += clamp(r.screenOffDrain - 1.5f, 0f, 10f) * 3f;
        risk += clamp(r.idleDrain - 0.7f, 0f, 5f) * 5f;
        risk += clamp(r.maxTemp - 43f, 0f, 8f) * 4f;
        risk += clamp(r.maxChargeTemp - 44f, 0f, 6f) * 4f;
        risk += clamp(25f - r.avgHealth, 0f, 25f);
        risk += r.fastChargeAbuse * 3f;
        risk += r.overDischargeEvents * 4f;
        risk += r.swellingRisk ? 10f : 0f;

        r.riskScore = clamp(risk, 0f, 100f);
        r.severity = severity(r.riskScore);
        r.topIssues = buildIssues(r);
        r.attribution = buildAttribution(r);
        return r;
    }

    private String severity(float risk) {
        if (risk >= 85) return "critical";
        if (risk >= 65) return "high";
        if (risk >= 45) return "medium";
        if (risk >= 25) return "low";
        return "info";
    }

    private String[] buildIssues(RollupMetrics r) {
        String[] issues = new String[4];
        int idx = 0;
        if (r.screenOffDrain > 1.5f) issues[idx++] = "idle_drain";
        if (r.maxTemp > 43f) issues[idx++] = "thermal";
        if (r.fastChargeAbuse > 0) issues[idx++] = "fast_charge_abuse";
        if (r.overDischargeEvents > 0) issues[idx++] = "over_discharge";
        return issues;
    }

    private String buildAttribution(RollupMetrics r) {
        StringBuilder sb = new StringBuilder();
        if (r.screenOnDrain > 15f) sb.append("app_load");
        if (r.screenOffDrain > 1.5f || r.idleDrain > 0.8f) {
            if (sb.length() > 0) sb.append('+');
            sb.append("background_wake");
        }
        if (r.maxTemp > 43f) {
            if (sb.length() > 0) sb.append('+');
            sb.append("thermal");
        }
        if (r.fastChargeAbuse > 0) {
            if (sb.length() > 0) sb.append('+');
            sb.append("charging_policy");
        }
        return sb.length() == 0 ? "normal" : sb.toString();
    }

    private float clamp(float v, float min, float max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
