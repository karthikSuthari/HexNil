package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.ComplianceReport;

/**
 * Produces ComplianceReport records using recent telemetry summaries.
 */
public class ComplianceReportGenerator {

    private static final long WEEK_MS = 7 * 24 * 60 * 60 * 1000L;

    private final HexNilDatabase database;
    private final PDFReportBuilder pdfReportBuilder;
    private final CSVExporter csvExporter;

    public ComplianceReportGenerator(Context context, HexNilDatabase database) {
        this.database = database;
        this.pdfReportBuilder = new PDFReportBuilder();
        this.csvExporter = new CSVExporter();
    }

    public ComplianceReport generateWeeklyCompliance() {
        long end = System.currentTimeMillis();
        long start = end - WEEK_MS;
        ComplianceMatrixBuilder matrixBuilder = new ComplianceMatrixBuilder(database);
        ComplianceReport report = matrixBuilder.buildMatrix(start, end);
        report.reportType = "comprehensive";
        report.reportVersion = "1.1";
        report.generatedBy = "HexNil";
        report.periodStart = start;
        report.periodEnd = end;

        report.tempViolationDetails = "critical thermal events: " + report.tempViolationCount;
        report.executiveSummary = report.reviewStatus.equals("approved")
            ? "Device meets compliance thresholds"
            : "Compliance risks detected";

        database.miscDao().insertComplianceReport(report);
        return report;
    }

    public byte[] buildWeeklyCompliancePdf() {
        ComplianceReport report = generateWeeklyCompliance();
        return pdfReportBuilder.buildCompliancePdf(report);
    }

    public String buildWeeklyComplianceCsv() {
        ComplianceReport report = generateWeeklyCompliance();
        return csvExporter.buildComplianceCsv(report);
    }
}
