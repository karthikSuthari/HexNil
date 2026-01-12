package com.example.hexnil.manager;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.ComplianceEvidence;
import com.example.hexnil.model.ComplianceReport;
import com.example.hexnil.model.SafetyEvent;
import com.example.hexnil.model.ThermalEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds pass/fail/attention compliance matrices from existing safety/thermal/charging data.
 */
public class ComplianceMatrixBuilder {

    private static final float OVER_CURRENT_WARN_MA = 2500f;
    private static final float OVER_CURRENT_CRIT_MA = 3000f;
    private static final float OVER_VOLTAGE_WARN_MV = 4450f;
    private static final float OVER_TEMP_WARN_C = 43f;
    private static final float OVER_TEMP_CRIT_C = 46f;
    private static final float THERMAL_CRIT_C = 45f;

    private static final String STATUS_PASS = "PASS";
    private static final String STATUS_ATTENTION = "ATTENTION";
    private static final String STATUS_FAIL = "FAIL";

    private static final String[] CORE_RULES = new String[] {
            "safety.over_current",
            "safety.over_voltage",
            "safety.over_temp",
            "safety.over_discharge",
            "thermal.critical_temp",
            "charging.over_current",
            "charging.over_voltage",
            "charging.over_temp"
    };

    private final HexNilDatabase database;

    public ComplianceMatrixBuilder(HexNilDatabase database) {
        this.database = database;
    }

    public ComplianceReport buildMatrix(long start, long end) {
        List<SafetyEvent> safetyEvents = database.safetyEventDao().getEventsSince(start);
        List<ThermalEvent> thermals = database.thermalEventDao().getEventsSince(start);
        List<ChargingSession> chargingSessions = database.chargingSessionDao().getSessionsSince(start);

        List<ComplianceEvidence> evidence = new ArrayList<>();
        Map<String, List<ComplianceEvidence>> byRule = new HashMap<>();

        addSafetyEvidence(start, end, safetyEvents, evidence, byRule);
        addThermalEvidence(start, end, thermals, evidence, byRule);
        addChargingEvidence(start, end, chargingSessions, evidence, byRule);

        List<Long> evidenceIds = persistEvidence(evidence);
        Map<String, String> ruleStatuses = computeRuleStatuses(byRule);

        ComplianceReport report = new ComplianceReport();
        report.timestamp = end;
        report.periodStart = start;
        report.periodEnd = end;
        report.reportType = "compliance_matrix";

        report.evidenceCount = evidenceIds.size();
        report.evidenceIds = toIdJson(evidenceIds);
        report.evidenceSummary = toEvidenceJson(evidence);

        report.safetyViolationCount = countViolations(byRule, "safety.");
        report.safetyCompliant = !hasStatus(ruleStatuses, "safety.", STATUS_FAIL) && !hasStatus(ruleStatuses, "safety.", STATUS_ATTENTION);
        report.tempViolationCount = countViolations(byRule, "thermal.") + countViolations(byRule, "charging.over_temp");
        report.tempCompliant = !hasStatus(ruleStatuses, "thermal.", STATUS_FAIL) && !hasStatus(ruleStatuses, "charging.over_temp", STATUS_FAIL) && !hasStatus(ruleStatuses, "thermal.", STATUS_ATTENTION);
        report.voltageViolationCount = countViolations(byRule, "safety.over_voltage") + countViolations(byRule, "charging.over_voltage");
        report.voltageCompliant = !hasStatus(ruleStatuses, "safety.over_voltage", STATUS_FAIL) && !hasStatus(ruleStatuses, "charging.over_voltage", STATUS_FAIL)
            && !hasStatus(ruleStatuses, "safety.over_voltage", STATUS_ATTENTION) && !hasStatus(ruleStatuses, "charging.over_voltage", STATUS_ATTENTION);
        report.currentViolationCount = countViolations(byRule, "safety.over_current") + countViolations(byRule, "charging.over_current");
        report.currentCompliant = !hasStatus(ruleStatuses, "safety.over_current", STATUS_FAIL) && !hasStatus(ruleStatuses, "charging.over_current", STATUS_FAIL)
            && !hasStatus(ruleStatuses, "safety.over_current", STATUS_ATTENTION) && !hasStatus(ruleStatuses, "charging.over_current", STATUS_ATTENTION);

        report.safetyScore = computeScore(ruleStatuses, evidence);
        report.complianceMatrix = matrix(ruleStatuses, byRule);
        report.certificationReadiness = computeReadiness(ruleStatuses, evidence);
        String overall = overallStatus(ruleStatuses);
        report.reviewStatus = STATUS_FAIL.equals(overall) ? "rejected" : STATUS_ATTENTION.equals(overall) ? "attention" : "approved";
        report.reviewNotes = report.reviewStatus.equals("approved") ? "PASS: all rules backed by evidence" : "See evidence list for corrective actions";
        report.executiveSummary = buildSummary(overall, report);
        return report;
    }

    private void addSafetyEvidence(long start, long end, List<SafetyEvent> safetyEvents,
                                   List<ComplianceEvidence> evidence,
                                   Map<String, List<ComplianceEvidence>> byRule) {
        for (SafetyEvent e : safetyEvents) {
            if (e.timestamp < start || e.timestamp > end) continue;
            ComplianceEvidence ev = new ComplianceEvidence();
            ev.ruleId = "safety." + (e.eventType == null ? "unknown" : e.eventType);
            ev.timestamp = e.timestamp;
            ev.metricValue = resolveSafetyMetric(e);
            ev.thresholdValue = resolveSafetyThreshold(e);
            ev.sourceModule = "safety_monitor";
            ev.severity = e.severity == null ? "warning" : e.severity;
            ev.category = "safety";
            ev.sourceEventId = e.id;
            ev.notes = e.protectionAction;
            addEvidence(evidence, byRule, ev);
        }
    }

    private void addThermalEvidence(long start, long end, List<ThermalEvent> thermals,
                                    List<ComplianceEvidence> evidence,
                                    Map<String, List<ComplianceEvidence>> byRule) {
        for (ThermalEvent t : thermals) {
            if (t.timestamp < start || t.timestamp > end) continue;
            boolean isViolation = "critical".equals(t.severity) || "threshold_violation".equals(t.eventType) || t.peakTemperature >= THERMAL_CRIT_C;
            if (!isViolation) continue;
            ComplianceEvidence ev = new ComplianceEvidence();
            ev.ruleId = "thermal.critical_temp";
            ev.timestamp = t.timestamp;
            ev.metricValue = t.peakTemperature > 0 ? t.peakTemperature : t.batteryTemp;
            ev.thresholdValue = THERMAL_CRIT_C;
            ev.sourceModule = "thermal_monitor";
            ev.severity = t.severity == null ? "warning" : t.severity;
            ev.category = "thermal";
            ev.sourceEventId = t.id;
            ev.notes = t.triggerActivity;
            addEvidence(evidence, byRule, ev);
        }
    }

    private void addChargingEvidence(long start, long end, List<ChargingSession> sessions,
                                     List<ComplianceEvidence> evidence,
                                     Map<String, List<ComplianceEvidence>> byRule) {
        for (ChargingSession cs : sessions) {
            long ts = cs.endTime > 0 ? cs.endTime : cs.startTime;
            if (ts < start || ts > end) continue;
            if (cs.overCurrentEvents > 0 || cs.maxCurrent > OVER_CURRENT_WARN_MA) {
                ComplianceEvidence ev = new ComplianceEvidence();
                ev.ruleId = "charging.over_current";
                ev.timestamp = ts;
                ev.metricValue = cs.maxCurrent;
                ev.thresholdValue = OVER_CURRENT_WARN_MA;
                ev.sourceModule = "charging_analyzer";
                ev.severity = cs.maxCurrent >= OVER_CURRENT_CRIT_MA ? "critical" : "warning";
                ev.category = "charging";
                ev.sourceEventId = cs.id;
                ev.notes = cs.safetyNotes;
                addEvidence(evidence, byRule, ev);
            }
            if (cs.overVoltageEvents > 0 || cs.maxVoltage > OVER_VOLTAGE_WARN_MV) {
                ComplianceEvidence ev = new ComplianceEvidence();
                ev.ruleId = "charging.over_voltage";
                ev.timestamp = ts;
                ev.metricValue = cs.maxVoltage;
                ev.thresholdValue = OVER_VOLTAGE_WARN_MV;
                ev.sourceModule = "charging_analyzer";
                ev.severity = cs.maxVoltage > OVER_VOLTAGE_WARN_MV + 50 ? "critical" : "warning";
                ev.category = "charging";
                ev.sourceEventId = cs.id;
                ev.notes = cs.safetyNotes;
                addEvidence(evidence, byRule, ev);
            }
            if (cs.overTempEvents > 0 || cs.maxTemperature > OVER_TEMP_WARN_C) {
                ComplianceEvidence ev = new ComplianceEvidence();
                ev.ruleId = "charging.over_temp";
                ev.timestamp = ts;
                ev.metricValue = cs.maxTemperature;
                ev.thresholdValue = OVER_TEMP_WARN_C;
                ev.sourceModule = "charging_analyzer";
                ev.severity = cs.maxTemperature >= OVER_TEMP_CRIT_C ? "critical" : "warning";
                ev.category = "charging";
                ev.sourceEventId = cs.id;
                ev.notes = cs.safetyNotes;
                addEvidence(evidence, byRule, ev);
            }
        }
    }

    private void addEvidence(List<ComplianceEvidence> sink, Map<String, List<ComplianceEvidence>> byRule, ComplianceEvidence ev) {
        sink.add(ev);
        List<ComplianceEvidence> list = byRule.get(ev.ruleId);
        if (list == null) {
            list = new ArrayList<>();
            byRule.put(ev.ruleId, list);
        }
        list.add(ev);
    }

    private List<Long> persistEvidence(List<ComplianceEvidence> evidence) {
        List<Long> ids = new ArrayList<>();
        for (ComplianceEvidence ev : evidence) {
            long id = database.complianceEvidenceDao().insert(ev);
            ev.id = id;
            ids.add(id);
        }
        return ids;
    }

    private Map<String, String> computeRuleStatuses(Map<String, List<ComplianceEvidence>> byRule) {
        Map<String, String> statuses = new HashMap<>();
        for (String rule : CORE_RULES) {
            statuses.put(rule, STATUS_PASS);
        }
        for (Map.Entry<String, List<ComplianceEvidence>> entry : byRule.entrySet()) {
            String status = STATUS_PASS;
            for (ComplianceEvidence ev : entry.getValue()) {
                status = maxStatus(status, statusForSeverity(ev.severity));
            }
            statuses.put(entry.getKey(), status);
        }
        return statuses;
    }

    private String matrix(Map<String, String> statuses, Map<String, List<ComplianceEvidence>> byRule) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        List<String> ordered = new ArrayList<>(Arrays.asList(CORE_RULES));
        for (String key : statuses.keySet()) {
            if (!ordered.contains(key)) ordered.add(key);
        }
        for (String rule : ordered) {
            String status = statuses.get(rule);
            if (status == null) continue;
            if (!first) sb.append(',');
            List<ComplianceEvidence> evs = byRule.get(rule);
            sb.append('{')
              .append("\"rule\":\"").append(rule).append("\"")
              .append(",\"status\":\"").append(status).append("\"")
                            .append(",\"count\":").append(evs == null ? 0 : evs.size())
                            .append(",\"evidence\":").append(toEvidenceIdJson(evs))
              .append('}');
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private float computeScore(Map<String, String> ruleStatuses, List<ComplianceEvidence> evidence) {
        float score = 100f;
        for (String status : ruleStatuses.values()) {
            if (STATUS_FAIL.equals(status)) score -= 10f;
            else if (STATUS_ATTENTION.equals(status)) score -= 5f;
        }
        score -= Math.min(20f, evidence.size() * 1.5f);
        return Math.max(0f, score);
    }

    private float computeReadiness(Map<String, String> ruleStatuses, List<ComplianceEvidence> evidence) {
        float readiness = 100f;
        for (String status : ruleStatuses.values()) {
            if (STATUS_FAIL.equals(status)) readiness -= 12f;
            else if (STATUS_ATTENTION.equals(status)) readiness -= 6f;
        }
        readiness -= Math.min(15f, evidence.size());
        return Math.max(0f, readiness);
    }

    private String overallStatus(Map<String, String> ruleStatuses) {
        boolean hasAttention = false;
        for (String status : ruleStatuses.values()) {
            if (STATUS_FAIL.equals(status)) return STATUS_FAIL;
            if (STATUS_ATTENTION.equals(status)) hasAttention = true;
        }
        return hasAttention ? STATUS_ATTENTION : STATUS_PASS;
    }

    private String buildSummary(String overall, ComplianceReport report) {
        if (STATUS_FAIL.equals(overall)) {
            return "FAIL: certification blocked; evidence=" + report.evidenceCount;
        }
        if (STATUS_ATTENTION.equals(overall)) {
            return "ATTENTION: issues require review; evidence=" + report.evidenceCount;
        }
        return "PASS: all rules met with evidence=" + report.evidenceCount;
    }

    private int countViolations(Map<String, List<ComplianceEvidence>> byRule, String scope) {
        int count = 0;
        for (Map.Entry<String, List<ComplianceEvidence>> entry : byRule.entrySet()) {
            if (matchesScope(entry.getKey(), scope)) {
                count += entry.getValue().size();
            }
        }
        return count;
    }

    private boolean hasStatus(Map<String, String> statuses, String scope, String status) {
        for (Map.Entry<String, String> entry : statuses.entrySet()) {
            if (matchesScope(entry.getKey(), scope) && status.equals(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unused") // Utility method for multi-status checking
    boolean hasStatus(Map<String, String> statuses, String scope, String statusA, String statusB) {
        return hasStatus(statuses, scope, statusA) || hasStatus(statuses, scope, statusB);
    }

    private boolean matchesScope(String rule, String scope) {
        return rule.equals(scope) || rule.startsWith(scope);
    }

    private float resolveSafetyMetric(SafetyEvent e) {
        if (e.triggerValue > 0) return e.triggerValue;
        if ("over_current".equals(e.eventType) && e.current > 0) return e.current;
        if ("over_voltage".equals(e.eventType) && e.voltage > 0) return e.voltage;
        if ("over_temp".equals(e.eventType) && e.batteryTemperature > 0) return e.batteryTemperature;
        if ("over_discharge".equals(e.eventType) && e.lowestVoltage > 0) return e.lowestVoltage;
        return 0f;
    }

    private float resolveSafetyThreshold(SafetyEvent e) {
        if (e.thresholdValue > 0) return e.thresholdValue;
        if ("over_current".equals(e.eventType)) return OVER_CURRENT_WARN_MA;
        if ("over_voltage".equals(e.eventType)) return OVER_VOLTAGE_WARN_MV;
        if ("over_temp".equals(e.eventType)) return OVER_TEMP_WARN_C;
        if ("over_discharge".equals(e.eventType)) return 10f;
        return 0f;
    }

    private String statusForSeverity(String severity) {
        if (severity == null) return STATUS_ATTENTION;
        if ("critical".equalsIgnoreCase(severity) || "emergency".equalsIgnoreCase(severity)) return STATUS_FAIL;
        if ("warning".equalsIgnoreCase(severity)) return STATUS_ATTENTION;
        return STATUS_ATTENTION;
    }

    private String maxStatus(String a, String b) {
        int rankA = rank(a);
        int rankB = rank(b);
        return rankA >= rankB ? a : b;
    }

    private int rank(String status) {
        if (STATUS_FAIL.equals(status)) return 2;
        if (STATUS_ATTENTION.equals(status)) return 1;
        return 0;
    }

    private String toEvidenceIdJson(List<ComplianceEvidence> evs) {
        if (evs == null || evs.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ComplianceEvidence ev : evs) {
            if (!first) sb.append(',');
            sb.append(ev.id);
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private String toIdJson(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Long id : ids) {
            if (!first) sb.append(',');
            sb.append(id);
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private String toEvidenceJson(List<ComplianceEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ComplianceEvidence ev : evidence) {
            if (!first) sb.append(',');
            sb.append('{')
              .append("\"id\":").append(ev.id)
              .append(",\"rule\":\"").append(ev.ruleId).append("\"")
              .append(",\"ts\":").append(ev.timestamp)
              .append(",\"metric\":").append(ev.metricValue)
              .append(",\"threshold\":").append(ev.thresholdValue)
              .append(",\"severity\":\"").append(ev.severity).append("\"")
              .append(",\"source\":\"").append(ev.sourceModule).append("\"")
              .append('}');
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }
}
