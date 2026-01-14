package com.example.hexnil.validation;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OEM-Grade Snapshot Quality Analyzer
 * 
 * Provides comprehensive quality metrics and diagnostics for battery snapshots:
 * - Data quality scoring across multiple dimensions
 * - Sampling rate compliance analysis
 * - Sensor reliability assessment
 * - Time-series continuity validation
 * - OEM compliance reporting
 * 
 * Used by QA teams and firmware engineers to validate battery monitoring quality.
 */
public class SnapshotQualityAnalyzer {

    private static final String TAG = "SnapshotQualityAnalyzer";

    private final Context context;
    private final HexNilDatabase database;

    public SnapshotQualityAnalyzer(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    // =========================================================================
    // QUALITY REPORT STRUCTURES
    // =========================================================================

    /**
     * Comprehensive quality report for a time period.
     */
    public static class QualityReport {
        public long reportTimestamp;
        public long periodStartTime;
        public long periodEndTime;
        public long periodDurationMs;
        
        // Counts
        public int totalSnapshots;
        public int validSnapshots;
        public int warningSnapshots;
        public int invalidSnapshots;
        public int uncheckedSnapshots;
        
        // Quality metrics (0-100)
        public float overallQualityScore;
        public float dataCompletenessScore;
        public float samplingRegularityScore;
        public float sensorReliabilityScore;
        public float validationPassRate;
        
        // Sampling analysis
        public float avgSamplingIntervalMs;
        public float expectedSamplingIntervalMs;
        public float samplingVarianceMs;
        public int missedSamplesEstimate;
        public int sequenceGaps;
        
        // Sensor health
        public int frozenSensorCount;
        public int duplicateTimestampCount;
        public int outOfRangeCount;
        
        // Context breakdown
        public int chargingSnapshots;
        public int dischargingSnapshots;
        public int idleSnapshots;
        public int thermalEventSnapshots;
        public int lowBatterySnapshots;
        
        // Issues
        public List<QualityIssue> issues = new ArrayList<>();
        
        // OEM compliance
        public boolean meetsOemStandards;
        public String complianceNotes;
        
        @Override
        public String toString() {
            return "QualityReport{" +
                    "period=" + TimeUnit.MILLISECONDS.toHours(periodDurationMs) + "h" +
                    ", total=" + totalSnapshots +
                    ", valid=" + validSnapshots +
                    ", overallScore=" + String.format("%.1f", overallQualityScore) +
                    ", passRate=" + String.format("%.1f%%", validationPassRate) +
                    ", avgInterval=" + String.format("%.0fms", avgSamplingIntervalMs) +
                    ", gaps=" + sequenceGaps +
                    ", frozen=" + frozenSensorCount +
                    ", meetsOEM=" + meetsOemStandards +
                    '}';
        }
    }

    /**
     * Individual quality issue for diagnostics.
     */
    public static class QualityIssue {
        public String severity;      // "critical", "warning", "info"
        public String category;      // "sampling", "sensor", "validation", "continuity"
        public String description;
        public long firstOccurrence;
        public int occurrenceCount;
        public String recommendation;
        
        public QualityIssue(String severity, String category, String description) {
            this.severity = severity;
            this.category = category;
            this.description = description;
            this.firstOccurrence = System.currentTimeMillis();
            this.occurrenceCount = 1;
        }
    }

    // =========================================================================
    // ANALYSIS METHODS
    // =========================================================================

    /**
     * Generate a comprehensive quality report for the last N hours.
     */
    public QualityReport analyzeQuality(int hours) {
        long endTime = System.currentTimeMillis();
        long startTime = endTime - TimeUnit.HOURS.toMillis(hours);
        return analyzeQualityInRange(startTime, endTime);
    }

    /**
     * Generate a comprehensive quality report for a specific time range.
     */
    public QualityReport analyzeQualityInRange(long startTime, long endTime) {
        QualityReport report = new QualityReport();
        report.reportTimestamp = System.currentTimeMillis();
        report.periodStartTime = startTime;
        report.periodEndTime = endTime;
        report.periodDurationMs = endTime - startTime;

        try {
            // Get all snapshots in range
            List<BatterySnapshot> snapshots = database.batterySnapshotDao()
                    .getSnapshotsInRange(startTime, endTime);

            if (snapshots == null || snapshots.isEmpty()) {
                report.overallQualityScore = 0;
                report.issues.add(new QualityIssue("critical", "sampling", 
                        "No snapshots found in time range"));
                report.meetsOemStandards = false;
                report.complianceNotes = "No data available for analysis";
                return report;
            }

            report.totalSnapshots = snapshots.size();

            // Count by validation status
            countByValidationStatus(snapshots, report);

            // Calculate quality scores
            calculateQualityScores(snapshots, report);

            // Analyze sampling regularity
            analyzeSamplingRegularity(snapshots, report);

            // Analyze sensor reliability
            analyzeSensorReliability(snapshots, report);

            // Analyze context distribution
            analyzeContextDistribution(snapshots, report);

            // Check for issues
            identifyIssues(snapshots, report);

            // Calculate overall score
            calculateOverallScore(report);

            // OEM compliance check
            checkOemCompliance(report);

            Log.i(TAG, "Quality analysis complete: " + report);

        } catch (Exception e) {
            Log.e(TAG, "Error analyzing quality", e);
            report.overallQualityScore = -1;
            report.issues.add(new QualityIssue("critical", "system", 
                    "Analysis error: " + e.getMessage()));
        }

        return report;
    }

    private void countByValidationStatus(List<BatterySnapshot> snapshots, QualityReport report) {
        for (BatterySnapshot s : snapshots) {
            if (s.validationStatus == null) {
                report.uncheckedSnapshots++;
            } else {
                switch (s.validationStatus) {
                    case "valid":
                        report.validSnapshots++;
                        break;
                    case "warning":
                        report.warningSnapshots++;
                        break;
                    case "invalid":
                        report.invalidSnapshots++;
                        break;
                    default:
                        report.uncheckedSnapshots++;
                        break;
                }
            }
        }
        
        // Calculate pass rate
        int checkedCount = report.totalSnapshots - report.uncheckedSnapshots;
        if (checkedCount > 0) {
            report.validationPassRate = (report.validSnapshots * 100.0f) / checkedCount;
        }
    }

    private void calculateQualityScores(List<BatterySnapshot> snapshots, QualityReport report) {
        // Data completeness: how many snapshots have quality scores assigned
        int withQuality = 0;
        float totalQuality = 0;
        
        for (BatterySnapshot s : snapshots) {
            if (s.qualityScore >= 0) {
                withQuality++;
                totalQuality += s.qualityScore;
            }
        }
        
        if (withQuality > 0) {
            report.dataCompletenessScore = (withQuality * 100.0f) / report.totalSnapshots;
            // Average quality of scored snapshots
            float avgQuality = totalQuality / withQuality;
            report.sensorReliabilityScore = avgQuality;
        } else {
            report.dataCompletenessScore = 0;
            report.sensorReliabilityScore = 50; // Unknown
        }
    }

    private void analyzeSamplingRegularity(List<BatterySnapshot> snapshots, QualityReport report) {
        if (snapshots.size() < 2) {
            report.samplingRegularityScore = 100; // Single snapshot is "regular"
            return;
        }

        // Calculate actual intervals between snapshots
        List<Long> intervals = new ArrayList<>();
        long prevTimestamp = snapshots.get(0).timestamp;
        long expectedInterval = snapshots.get(0).samplingIntervalMs;
        
        if (expectedInterval <= 0) {
            expectedInterval = 60000; // Default 1 minute
        }
        report.expectedSamplingIntervalMs = expectedInterval;

        for (int i = 1; i < snapshots.size(); i++) {
            long current = snapshots.get(i).timestamp;
            long interval = current - prevTimestamp;
            
            if (interval > 0) {
                intervals.add(interval);
            }
            prevTimestamp = current;
        }

        if (intervals.isEmpty()) {
            report.samplingRegularityScore = 0;
            return;
        }

        // Calculate average and variance
        long sum = 0;
        for (long interval : intervals) {
            sum += interval;
        }
        report.avgSamplingIntervalMs = sum / (float) intervals.size();

        // Variance calculation
        float varianceSum = 0;
        for (long interval : intervals) {
            float diff = interval - report.avgSamplingIntervalMs;
            varianceSum += diff * diff;
        }
        report.samplingVarianceMs = (float) Math.sqrt(varianceSum / intervals.size());

        // Estimate missed samples
        long totalExpectedSamples = report.periodDurationMs / (long) expectedInterval;
        report.missedSamplesEstimate = Math.max(0, (int) (totalExpectedSamples - report.totalSnapshots));

        // Calculate regularity score
        // Lower variance = higher score
        // Target: variance should be less than 20% of expected interval
        float maxAcceptableVariance = expectedInterval * 0.2f;
        if (report.samplingVarianceMs <= maxAcceptableVariance) {
            report.samplingRegularityScore = 100;
        } else {
            float excessVariance = report.samplingVarianceMs - maxAcceptableVariance;
            float penalty = Math.min(50, excessVariance / expectedInterval * 100);
            report.samplingRegularityScore = Math.max(0, 100 - penalty);
        }

        // Additional penalty for missed samples
        if (report.missedSamplesEstimate > 0) {
            float missedPenalty = Math.min(30, report.missedSamplesEstimate * 2);
            report.samplingRegularityScore = Math.max(0, report.samplingRegularityScore - missedPenalty);
        }
    }

    private void analyzeSensorReliability(List<BatterySnapshot> snapshots, QualityReport report) {
        int frozenCount = 0;
        int duplicateTs = 0;
        int outOfRange = 0;
        
        BatterySnapshot prev = null;
        for (BatterySnapshot s : snapshots) {
            // Check for frozen sensors
            if (!s.sensorDataChanged && prev != null) {
                frozenCount++;
            }
            
            // Check for duplicate timestamps
            if (prev != null && s.timestamp == prev.timestamp) {
                duplicateTs++;
            }
            
            // Check for out of range values
            if (s.voltage < 2500 || s.voltage > 4500 ||
                s.temperature < -200 || s.temperature > 800 ||
                s.level < 0 || s.level > 100) {
                outOfRange++;
            }
            
            prev = s;
        }

        report.frozenSensorCount = frozenCount;
        report.duplicateTimestampCount = duplicateTs;
        report.outOfRangeCount = outOfRange;

        // Count sequence gaps
        if (!snapshots.isEmpty()) {
            long sessionId = snapshots.get(0).sessionId;
            report.sequenceGaps = database.batterySnapshotDao().getSequenceGapCount(sessionId);
        }
    }

    private void analyzeContextDistribution(List<BatterySnapshot> snapshots, QualityReport report) {
        for (BatterySnapshot s : snapshots) {
            if (s.samplingContext == null) continue;
            
            switch (s.samplingContext) {
                case "charging":
                case "fast_charging":
                case "charging_start":
                case "charging_end":
                case "power_connected":
                case "power_disconnected":
                    report.chargingSnapshots++;
                    break;
                case "discharging":
                case "normal":
                    report.dischargingSnapshots++;
                    break;
                case "idle":
                case "screen_off":
                case "doze_exit":
                    report.idleSnapshots++;
                    break;
                case "thermal_event":
                case "thermal_high":
                case "thermal_normal":
                case "temp_change":
                    report.thermalEventSnapshots++;
                    break;
                case "low_battery":
                case "critical_battery":
                case "battery_low":
                    report.lowBatterySnapshots++;
                    break;
            }
        }
    }

    private void identifyIssues(List<BatterySnapshot> snapshots, QualityReport report) {
        // Critical: Too many invalid snapshots
        if (report.invalidSnapshots > report.totalSnapshots * 0.1) {
            report.issues.add(new QualityIssue("critical", "validation",
                    "High invalid snapshot rate: " + 
                    String.format("%.1f%%", (report.invalidSnapshots * 100.0f / report.totalSnapshots))));
        }

        // Critical: Duplicate timestamps
        if (report.duplicateTimestampCount > 0) {
            QualityIssue issue = new QualityIssue("critical", "sampling",
                    "Duplicate timestamps detected - snapshots may be reused");
            issue.occurrenceCount = report.duplicateTimestampCount;
            issue.recommendation = "Check BatteryDataCollector for snapshot reuse bugs";
            report.issues.add(issue);
        }

        // Warning: Frozen sensors
        if (report.frozenSensorCount > report.totalSnapshots * 0.05) {
            QualityIssue issue = new QualityIssue("warning", "sensor",
                    "Frozen sensor readings detected");
            issue.occurrenceCount = report.frozenSensorCount;
            issue.recommendation = "Investigate BatteryManager polling or device sleep behavior";
            report.issues.add(issue);
        }

        // Warning: High sampling variance
        if (report.samplingVarianceMs > report.expectedSamplingIntervalMs * 0.5) {
            report.issues.add(new QualityIssue("warning", "sampling",
                    "High sampling interval variance: " + 
                    String.format("%.0fms (expected %.0fms)", 
                            report.samplingVarianceMs, report.expectedSamplingIntervalMs * 0.2)));
        }

        // Warning: Many missed samples
        if (report.missedSamplesEstimate > report.totalSnapshots * 0.2) {
            report.issues.add(new QualityIssue("warning", "continuity",
                    "Estimated missed samples: " + report.missedSamplesEstimate));
        }

        // Warning: Sequence gaps
        if (report.sequenceGaps > 10) {
            report.issues.add(new QualityIssue("warning", "continuity",
                    "Sequence gaps detected: " + report.sequenceGaps));
        }

        // Info: Out of range values
        if (report.outOfRangeCount > 0) {
            QualityIssue issue = new QualityIssue("info", "sensor",
                    "Out-of-range sensor values");
            issue.occurrenceCount = report.outOfRangeCount;
            report.issues.add(issue);
        }

        // Info: Low completeness
        if (report.dataCompletenessScore < 90) {
            report.issues.add(new QualityIssue("info", "validation",
                    "Some snapshots missing quality scores"));
        }
    }

    private void calculateOverallScore(QualityReport report) {
        // Weighted average of subscores
        float validationWeight = 0.30f;
        float regularityWeight = 0.25f;
        float reliabilityWeight = 0.25f;
        float completenessWeight = 0.20f;

        report.overallQualityScore = 
            report.validationPassRate * validationWeight +
            report.samplingRegularityScore * regularityWeight +
            report.sensorReliabilityScore * reliabilityWeight +
            report.dataCompletenessScore * completenessWeight;

        // Penalty for critical issues
        int criticalCount = 0;
        for (QualityIssue issue : report.issues) {
            if ("critical".equals(issue.severity)) {
                criticalCount++;
            }
        }
        report.overallQualityScore = Math.max(0, report.overallQualityScore - criticalCount * 15);
    }

    private void checkOemCompliance(QualityReport report) {
        // OEM standards for battery monitoring quality
        // These thresholds represent production-grade requirements
        
        boolean passValidation = report.validationPassRate >= 90;
        boolean passRegularity = report.samplingRegularityScore >= 70;
        boolean passReliability = report.sensorReliabilityScore >= 75;
        boolean noCritical = report.duplicateTimestampCount == 0;
        boolean acceptableFrozen = report.frozenSensorCount <= report.totalSnapshots * 0.02;

        report.meetsOemStandards = passValidation && passRegularity && 
                                   passReliability && noCritical && acceptableFrozen;

        StringBuilder notes = new StringBuilder();
        if (!passValidation) {
            notes.append("Validation pass rate below 90%. ");
        }
        if (!passRegularity) {
            notes.append("Sampling regularity below 70%. ");
        }
        if (!passReliability) {
            notes.append("Sensor reliability below 75%. ");
        }
        if (!noCritical) {
            notes.append("Duplicate timestamps detected. ");
        }
        if (!acceptableFrozen) {
            notes.append("Frozen sensor rate above 2%. ");
        }

        if (notes.length() == 0) {
            report.complianceNotes = "All OEM quality standards met";
        } else {
            report.complianceNotes = notes.toString().trim();
        }
    }

    // =========================================================================
    // QUICK HEALTH CHECKS
    // =========================================================================

    /**
     * Quick check for data quality issues in the last hour.
     * Returns true if no critical issues found.
     */
    public boolean quickHealthCheck() {
        QualityReport report = analyzeQuality(1);
        
        for (QualityIssue issue : report.issues) {
            if ("critical".equals(issue.severity)) {
                Log.w(TAG, "Health check failed: " + issue.description);
                return false;
            }
        }
        
        return report.validationPassRate >= 80 && report.duplicateTimestampCount == 0;
    }

    /**
     * Get the current snapshot rate (snapshots per minute).
     */
    public float getCurrentSnapshotRate() {
        long oneHourAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1);
        int count = database.batterySnapshotDao().getCountSince(oneHourAgo);
        return count / 60.0f; // per minute
    }

    /**
     * Get count of issues in the last N hours.
     */
    public int getRecentIssueCount(int hours) {
        QualityReport report = analyzeQuality(hours);
        return report.issues.size();
    }
}
