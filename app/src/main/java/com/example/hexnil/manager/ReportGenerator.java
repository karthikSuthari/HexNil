package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AIAnalysisResult;

/**
 * Coordinates report creation using HexNilAI and builders/exporters.
 */
public class ReportGenerator {

    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    @SuppressWarnings("FieldCanBeLocal") // Reserved for extended report generation
    private final HexNilDatabase database;
    private final HexNilAI hexNilAI;
    private final PDFReportBuilder pdfReportBuilder;
    private final CSVExporter csvExporter;

    public ReportGenerator(Context context, HexNilDatabase database) {
        this.database = database;
        this.hexNilAI = new HexNilAI(context, database);
        this.pdfReportBuilder = new PDFReportBuilder();
        this.csvExporter = new CSVExporter();
    }

    public AIAnalysisResult runDailyAnalysis() {
        long start = System.currentTimeMillis() - DAY_MS;
        return hexNilAI.runComprehensiveAnalysis("24h", start);
    }

    public byte[] buildDailyPdfReport() {
        AIAnalysisResult result = runDailyAnalysis();
        return pdfReportBuilder.buildAnalysisPdf(result);
    }

    public String buildDailyCsvSummary() {
        AIAnalysisResult result = runDailyAnalysis();
        return csvExporter.buildAnalysisCsv(result);
    }
}
