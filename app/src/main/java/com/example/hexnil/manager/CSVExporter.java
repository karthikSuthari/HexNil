package com.example.hexnil.manager;

import com.example.hexnil.model.AIAnalysisResult;
import com.example.hexnil.model.ComplianceReport;

/**
 * Emits compact CSV summaries for export.
 */
public class CSVExporter {

    public String buildAnalysisCsv(AIAnalysisResult result) {
        if (result == null) return "";
        String header = "timestamp,severity,risk,primary_issue,issues_detected,issue_count,trend,overall_score";
        String row = String.format("%d,%s,%.2f,%s,%b,%d,%s,%.2f",
                result.timestamp,
                safe(result.severity),
                result.riskScore,
                safe(result.primaryIssue),
                result.issuesDetected,
                result.issueCount,
                safe(result.trend),
                result.overallBatteryScore
        );
        return header + "\n" + row;
    }

    public String buildComplianceCsv(ComplianceReport report) {
        if (report == null) return "";
        String header = "timestamp,type,safety,temp,voltage,current,grade,score,evidence_count,evidence_ids,evidence_summary";
        String row = String.format("%d,%s,%b,%b,%b,%b,%s,%.2f,%d,%s,%s",
                report.timestamp,
                safe(report.reportType),
                report.safetyCompliant,
                report.tempCompliant,
                report.voltageCompliant,
                report.currentCompliant,
                safe(report.getComplianceGrade()),
            report.safetyScore,
            report.evidenceCount,
            safe(report.evidenceIds),
            safe(report.evidenceSummary)
        );
        return header + "\n" + row;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace(",", " ");
    }
}
