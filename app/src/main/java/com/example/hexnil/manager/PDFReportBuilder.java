package com.example.hexnil.manager;

import com.example.hexnil.model.AIAnalysisResult;
import com.example.hexnil.model.ComplianceReport;

import java.nio.charset.StandardCharsets;

/**
 * Produces simple PDF-ready payloads. Placeholder uses plain text bytes so a PDF library can be wired later.
 */
public class PDFReportBuilder {

    public byte[] buildAnalysisPdf(AIAnalysisResult result) {
        if (result == null) return new byte[0];
        StringBuilder sb = new StringBuilder();
        sb.append("HexNil Battery Analysis\n");
        sb.append("Severity: ").append(result.severity).append("\n");
        sb.append("Risk: ").append(result.riskScore).append("\n");
        sb.append("Primary issue: ").append(result.primaryIssue).append("\n");
        sb.append("Recommendations: ").append(result.recommendations).append("\n");
        sb.append("Executive summary: ").append(result.executiveSummary).append("\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] buildCompliancePdf(ComplianceReport report) {
        if (report == null) return new byte[0];
        StringBuilder sb = new StringBuilder();
        sb.append("HexNil Compliance Report\n");
        sb.append("Type: ").append(report.reportType).append("\n");
        sb.append("Safety compliant: ").append(report.safetyCompliant).append("\n");
        sb.append("Temp compliant: ").append(report.tempCompliant).append("\n");
        sb.append("Voltage compliant: ").append(report.voltageCompliant).append("\n");
        sb.append("Current compliant: ").append(report.currentCompliant).append("\n");
        sb.append("Compliance grade: ").append(report.getComplianceGrade()).append("\n");
        sb.append("Evidence count: ").append(report.evidenceCount).append("\n");
        sb.append("Evidence ids: ").append(report.evidenceIds).append("\n");
        sb.append("Evidence summary: ").append(report.evidenceSummary).append("\n");
        sb.append("Executive summary: ").append(report.executiveSummary).append("\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
