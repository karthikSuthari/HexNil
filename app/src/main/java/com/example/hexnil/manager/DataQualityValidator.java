package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.database.NegativeExampleDao;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.DeviceProfile;
import com.example.hexnil.model.HumanLabel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Validates data quality for ML training datasets in the HexNil battery diagnostic app.
 *
 * Checks for completeness, consistency, and statistical properties of collected data
 * across labeled examples, negative examples, device profiles, battery snapshots,
 * and label quality metrics. Each check produces a QualityCheck result with severity
 * classification (blocker, warning, info) and the overall DataQualityReport determines
 * whether the dataset meets minimum requirements for ML model training.
 *
 * Usage:
 * <pre>
 *     DataQualityValidator validator = new DataQualityValidator(context, database);
 *     DataQualityReport report = validator.validateDataReadiness();
 *     if (report.isTrainingReady) {
 *         // Proceed with ML training
 *     } else {
 *         // Address report.blockers before training
 *     }
 * </pre>
 */
public class DataQualityValidator {

    private static final String TAG = "DataQualityValidator";

    // =========================================================================
    // THRESHOLDS
    // =========================================================================

    private static final int MIN_LABELED_DATA_COUNT = 100;
    private static final double MIN_CLASS_BALANCE_RATIO = 0.1;
    private static final double MAX_CLASS_BALANCE_RATIO = 10.0;
    private static final int MIN_CONDITION_TYPES_COVERED = 5;
    private static final int MIN_EXAMPLES_PER_CONDITION = 10;
    private static final int MIN_RELIABLE_PROFILES = 1;
    private static final int MIN_SNAPSHOTS_7_DAYS = 1000;
    private static final double MIN_LABEL_CONFIDENCE_AVG = 0.7;
    private static final int RECENT_LABELS_SAMPLE_SIZE = 100;
    private static final long SEVEN_DAYS_MS = TimeUnit.DAYS.toMillis(7);

    // =========================================================================
    // DEPENDENCIES
    // =========================================================================

    private final Context context;
    private final HexNilDatabase database;

    public DataQualityValidator(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    // =========================================================================
    // MAIN VALIDATION
    // =========================================================================

    /**
     * Runs all data quality checks and returns a comprehensive DataQualityReport.
     *
     * This is the primary entry point for validating whether collected data meets
     * the minimum requirements for ML model training. Each check is run independently,
     * and the report aggregates results into an overall readiness assessment.
     *
     * @return DataQualityReport with all check results, overall score, and blocking issues
     */
    public DataQualityReport validateDataReadiness() {
        Log.d(TAG, "validateDataReadiness: starting all quality checks");

        List<QualityCheck> checks = new ArrayList<>();

        try {
            checks.add(checkLabeledDataVolume());
        } catch (Exception e) {
            Log.e(TAG, "validateDataReadiness: checkLabeledDataVolume failed", e);
            checks.add(createErrorCheck("labeled_data_volume",
                    "Failed to check labeled data volume: " + e.getMessage()));
        }

        try {
            checks.add(checkClassBalance());
        } catch (Exception e) {
            Log.e(TAG, "validateDataReadiness: checkClassBalance failed", e);
            checks.add(createErrorCheck("class_balance",
                    "Failed to check class balance: " + e.getMessage()));
        }

        try {
            checks.add(checkNegativeExampleCoverage());
        } catch (Exception e) {
            Log.e(TAG, "validateDataReadiness: checkNegativeExampleCoverage failed", e);
            checks.add(createErrorCheck("negative_example_coverage",
                    "Failed to check negative example coverage: " + e.getMessage()));
        }

        try {
            checks.add(checkDeviceProfileCoverage());
        } catch (Exception e) {
            Log.e(TAG, "validateDataReadiness: checkDeviceProfileCoverage failed", e);
            checks.add(createErrorCheck("device_profile_coverage",
                    "Failed to check device profile coverage: " + e.getMessage()));
        }

        try {
            checks.add(checkSnapshotDensity());
        } catch (Exception e) {
            Log.e(TAG, "validateDataReadiness: checkSnapshotDensity failed", e);
            checks.add(createErrorCheck("snapshot_density",
                    "Failed to check snapshot density: " + e.getMessage()));
        }

        try {
            checks.add(checkLabelQuality());
        } catch (Exception e) {
            Log.e(TAG, "validateDataReadiness: checkLabelQuality failed", e);
            checks.add(createErrorCheck("label_quality",
                    "Failed to check label quality: " + e.getMessage()));
        }

        // Build report
        int passedCount = 0;
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (QualityCheck check : checks) {
            if (check.passed) {
                passedCount++;
            } else if ("blocker".equals(check.severity)) {
                blockers.add(check.name + ": " + check.message);
            } else if ("warning".equals(check.severity)) {
                warnings.add(check.name + ": " + check.message);
            }
        }

        int overallScore = checks.isEmpty() ? 0
                : (int) Math.round((passedCount * 100.0) / checks.size());
        boolean isTrainingReady = blockers.isEmpty() && overallScore >= 70;

        DataQualityReport report = new DataQualityReport();
        report.checks = checks;
        report.overallScore = overallScore;
        report.isTrainingReady = isTrainingReady;
        report.blockers = blockers;
        report.warnings = warnings;
        report.timestamp = System.currentTimeMillis();

        Log.d(TAG, "validateDataReadiness: complete - score=" + overallScore
                + " ready=" + isTrainingReady
                + " blockers=" + blockers.size()
                + " warnings=" + warnings.size());

        return report;
    }

    // =========================================================================
    // INDIVIDUAL QUALITY CHECKS
    // =========================================================================

    /**
     * Checks whether enough labeled data exists for training.
     *
     * Requires at least {@value #MIN_LABELED_DATA_COUNT} human-verified labels across
     * all anomaly types. This is the foundational requirement -- without sufficient
     * ground truth labels, no supervised model can be trained.
     *
     * @return QualityCheck with labeled data volume assessment
     */
    public QualityCheck checkLabeledDataVolume() {
        int totalCount = database.humanLabelDao().getTotalCount();
        boolean passed = totalCount >= MIN_LABELED_DATA_COUNT;

        QualityCheck check = new QualityCheck();
        check.name = "labeled_data_volume";
        check.passed = passed;
        check.actualValue = totalCount;
        check.threshold = MIN_LABELED_DATA_COUNT;
        check.severity = "blocker";

        if (passed) {
            check.message = "Sufficient labeled data: " + totalCount
                    + " labels (minimum " + MIN_LABELED_DATA_COUNT + ")";
        } else {
            check.message = "Insufficient labeled data: " + totalCount
                    + " labels, need at least " + MIN_LABELED_DATA_COUNT;
        }

        Log.d(TAG, "checkLabeledDataVolume: count=" + totalCount
                + " passed=" + passed);
        return check;
    }

    /**
     * Checks whether the TP/FP class distribution is balanced enough for training.
     *
     * If one class dominates by more than 10:1, the model will be biased.
     * The ratio is computed as TP / FP, and must be between 0.1 and 10.0.
     * If either TP or FP count is zero, the check fails since both classes
     * must be represented.
     *
     * @return QualityCheck with class balance assessment
     */
    public QualityCheck checkClassBalance() {
        int tpCount = database.humanLabelDao().getCountByVerdict("true_positive");
        int fpCount = database.humanLabelDao().getCountByVerdict("false_positive");

        QualityCheck check = new QualityCheck();
        check.name = "class_balance";
        check.severity = "blocker";

        if (tpCount == 0 && fpCount == 0) {
            check.passed = false;
            check.actualValue = 0;
            check.threshold = MIN_CLASS_BALANCE_RATIO;
            check.message = "No TP or FP labels found. Both classes must be represented.";
            Log.d(TAG, "checkClassBalance: no labels - failed");
            return check;
        }

        if (tpCount == 0 || fpCount == 0) {
            check.passed = false;
            check.actualValue = tpCount == 0 ? 0 : Double.MAX_VALUE;
            check.threshold = MAX_CLASS_BALANCE_RATIO;
            check.message = "Only one class represented: TP=" + tpCount
                    + " FP=" + fpCount + ". Both classes required.";
            Log.d(TAG, "checkClassBalance: single class - TP=" + tpCount
                    + " FP=" + fpCount + " failed");
            return check;
        }

        double ratio = (double) tpCount / fpCount;
        boolean passed = ratio >= MIN_CLASS_BALANCE_RATIO && ratio <= MAX_CLASS_BALANCE_RATIO;

        check.passed = passed;
        check.actualValue = ratio;
        check.threshold = MAX_CLASS_BALANCE_RATIO;

        if (passed) {
            check.message = String.format(
                    "Class balance OK: TP=%d FP=%d ratio=%.2f (acceptable range %.1f-%.1f)",
                    tpCount, fpCount, ratio, MIN_CLASS_BALANCE_RATIO, MAX_CLASS_BALANCE_RATIO);
        } else {
            check.message = String.format(
                    "Class imbalance detected: TP=%d FP=%d ratio=%.2f (must be between %.1f and %.1f)",
                    tpCount, fpCount, ratio, MIN_CLASS_BALANCE_RATIO, MAX_CLASS_BALANCE_RATIO);
        }

        Log.d(TAG, "checkClassBalance: TP=" + tpCount + " FP=" + fpCount
                + " ratio=" + String.format("%.2f", ratio) + " passed=" + passed);
        return check;
    }

    /**
     * Checks whether negative example coverage spans enough condition types.
     *
     * ML models need to learn from a diverse set of "normal but high-stress" scenarios.
     * At least {@value #MIN_CONDITION_TYPES_COVERED} distinct condition types (e.g., gaming,
     * navigation, fast charging, video call, benchmark) must each have at least
     * {@value #MIN_EXAMPLES_PER_CONDITION} examples to provide adequate negative coverage.
     *
     * @return QualityCheck with negative example coverage assessment
     */
    public QualityCheck checkNegativeExampleCoverage() {
        List<NegativeExampleDao.ConditionTypeCount> typeCounts =
                database.negativeExampleDao().getCountByConditionType();

        int adequatelyCovertedTypes = 0;
        int totalExamples = 0;

        for (NegativeExampleDao.ConditionTypeCount tc : typeCounts) {
            totalExamples += tc.count;
            if (tc.count >= MIN_EXAMPLES_PER_CONDITION) {
                adequatelyCovertedTypes++;
            }
        }

        boolean passed = adequatelyCovertedTypes >= MIN_CONDITION_TYPES_COVERED;

        QualityCheck check = new QualityCheck();
        check.name = "negative_example_coverage";
        check.passed = passed;
        check.actualValue = adequatelyCovertedTypes;
        check.threshold = MIN_CONDITION_TYPES_COVERED;
        check.severity = "blocker";

        if (passed) {
            check.message = adequatelyCovertedTypes + " condition types have " +
                    MIN_EXAMPLES_PER_CONDITION + "+ examples each (minimum "
                    + MIN_CONDITION_TYPES_COVERED + " types). Total examples: " + totalExamples;
        } else {
            check.message = "Only " + adequatelyCovertedTypes + " condition types have "
                    + MIN_EXAMPLES_PER_CONDITION + "+ examples. Need at least "
                    + MIN_CONDITION_TYPES_COVERED + " types. Total examples: " + totalExamples;
        }

        Log.d(TAG, "checkNegativeExampleCoverage: coveredTypes=" + adequatelyCovertedTypes
                + " totalExamples=" + totalExamples + " passed=" + passed);
        return check;
    }

    /**
     * Checks whether at least one device profile has reliable baseline data.
     *
     * Device-specific baselines are critical for anomaly detection accuracy.
     * A profile is considered reliable when {@link DeviceProfile#hasReliableBaselines()}
     * returns true (requires deviceCount >= 10 and non-null drain baselines).
     *
     * @return QualityCheck with device profile coverage assessment
     */
    public QualityCheck checkDeviceProfileCoverage() {
        List<DeviceProfile> allProfiles = database.deviceProfileDao().getAll();
        int reliableCount = 0;

        for (DeviceProfile profile : allProfiles) {
            if (profile.hasReliableBaselines()) {
                reliableCount++;
            }
        }

        boolean passed = reliableCount >= MIN_RELIABLE_PROFILES;

        QualityCheck check = new QualityCheck();
        check.name = "device_profile_coverage";
        check.passed = passed;
        check.actualValue = reliableCount;
        check.threshold = MIN_RELIABLE_PROFILES;
        check.severity = "blocker";

        if (passed) {
            check.message = reliableCount + " device profile(s) with reliable baselines"
                    + " out of " + allProfiles.size() + " total (minimum "
                    + MIN_RELIABLE_PROFILES + ")";
        } else {
            check.message = "No device profiles with reliable baselines found. "
                    + allProfiles.size() + " total profiles, but none meet reliability criteria "
                    + "(need deviceCount >= 10 with drain baselines). "
                    + "Need at least " + MIN_RELIABLE_PROFILES + " reliable profile(s).";
        }

        Log.d(TAG, "checkDeviceProfileCoverage: total=" + allProfiles.size()
                + " reliable=" + reliableCount + " passed=" + passed);
        return check;
    }

    /**
     * Checks whether enough battery snapshots have been collected in the last 7 days.
     *
     * Adequate snapshot density ensures the training data captures the full range
     * of device states. Requires at least {@value #MIN_SNAPSHOTS_7_DAYS} snapshots
     * in the trailing 7-day window.
     *
     * @return QualityCheck with snapshot density assessment
     */
    public QualityCheck checkSnapshotDensity() {
        long sevenDaysAgo = System.currentTimeMillis() - SEVEN_DAYS_MS;
        List<BatterySnapshot> recentSnapshots =
                database.batterySnapshotDao().getSnapshotsSince(sevenDaysAgo);
        int snapshotCount = recentSnapshots != null ? recentSnapshots.size() : 0;

        boolean passed = snapshotCount >= MIN_SNAPSHOTS_7_DAYS;

        QualityCheck check = new QualityCheck();
        check.name = "snapshot_density";
        check.passed = passed;
        check.actualValue = snapshotCount;
        check.threshold = MIN_SNAPSHOTS_7_DAYS;
        check.severity = "warning";

        if (passed) {
            check.message = snapshotCount + " snapshots in last 7 days"
                    + " (minimum " + MIN_SNAPSHOTS_7_DAYS + ")";
        } else {
            check.message = "Only " + snapshotCount + " snapshots in last 7 days."
                    + " Need at least " + MIN_SNAPSHOTS_7_DAYS + " for adequate density.";
        }

        Log.d(TAG, "checkSnapshotDensity: count=" + snapshotCount
                + " passed=" + passed);
        return check;
    }

    /**
     * Checks the average confidence of recent human labels.
     *
     * Low-confidence labels degrade model training quality. The average labelConfidence
     * across the most recent {@value #RECENT_LABELS_SAMPLE_SIZE} labels must be at
     * least {@value #MIN_LABEL_CONFIDENCE_AVG}.
     *
     * @return QualityCheck with label quality assessment
     */
    public QualityCheck checkLabelQuality() {
        List<HumanLabel> recentLabels =
                database.humanLabelDao().getRecentLabels(RECENT_LABELS_SAMPLE_SIZE);

        QualityCheck check = new QualityCheck();
        check.name = "label_quality";
        check.severity = "warning";

        if (recentLabels == null || recentLabels.isEmpty()) {
            check.passed = false;
            check.actualValue = 0;
            check.threshold = MIN_LABEL_CONFIDENCE_AVG;
            check.message = "No labels available to assess label quality.";
            Log.d(TAG, "checkLabelQuality: no labels available");
            return check;
        }

        double totalConfidence = 0;
        for (HumanLabel label : recentLabels) {
            totalConfidence += label.labelConfidence;
        }
        double avgConfidence = totalConfidence / recentLabels.size();

        boolean passed = avgConfidence >= MIN_LABEL_CONFIDENCE_AVG;

        check.passed = passed;
        check.actualValue = avgConfidence;
        check.threshold = MIN_LABEL_CONFIDENCE_AVG;

        if (passed) {
            check.message = String.format(
                    "Label quality OK: average confidence %.2f across %d recent labels"
                            + " (minimum %.1f)",
                    avgConfidence, recentLabels.size(), MIN_LABEL_CONFIDENCE_AVG);
        } else {
            check.message = String.format(
                    "Low label quality: average confidence %.2f across %d recent labels."
                            + " Need at least %.1f average confidence.",
                    avgConfidence, recentLabels.size(), MIN_LABEL_CONFIDENCE_AVG);
        }

        Log.d(TAG, "checkLabelQuality: avgConfidence="
                + String.format("%.2f", avgConfidence)
                + " sampleSize=" + recentLabels.size()
                + " passed=" + passed);
        return check;
    }

    // =========================================================================
    // AGGREGATE ASSESSMENTS
    // =========================================================================

    /**
     * Runs all quality checks and returns a readiness score from 0 to 100.
     *
     * Each quality check is weighted equally. The score is the percentage of
     * checks that passed, scaled to the 0-100 range.
     *
     * @return integer readiness score where 100 means all checks pass
     */
    public int getReadinessScore() {
        List<QualityCheck> checks = new ArrayList<>();
        checks.add(checkLabeledDataVolume());
        checks.add(checkClassBalance());
        checks.add(checkNegativeExampleCoverage());
        checks.add(checkDeviceProfileCoverage());
        checks.add(checkSnapshotDensity());
        checks.add(checkLabelQuality());

        if (checks.isEmpty()) {
            return 0;
        }

        int passedCount = 0;
        for (QualityCheck check : checks) {
            if (check.passed) {
                passedCount++;
            }
        }

        int score = (int) Math.round((passedCount * 100.0) / checks.size());
        Log.d(TAG, "getReadinessScore: " + passedCount + "/" + checks.size()
                + " checks passed = " + score);
        return score;
    }

    /**
     * Returns all failed quality checks that are classified as blockers.
     *
     * Blocker-severity failures must be resolved before ML training can proceed.
     * This provides a focused list of exactly what needs to be fixed.
     *
     * @return list of failed QualityCheck objects with "blocker" severity
     */
    public List<QualityCheck> getBlockingIssues() {
        List<QualityCheck> allChecks = new ArrayList<>();
        allChecks.add(checkLabeledDataVolume());
        allChecks.add(checkClassBalance());
        allChecks.add(checkNegativeExampleCoverage());
        allChecks.add(checkDeviceProfileCoverage());
        allChecks.add(checkSnapshotDensity());
        allChecks.add(checkLabelQuality());

        List<QualityCheck> blockers = new ArrayList<>();
        for (QualityCheck check : allChecks) {
            if (!check.passed && "blocker".equals(check.severity)) {
                blockers.add(check);
            }
        }

        Log.d(TAG, "getBlockingIssues: " + blockers.size() + " blocker(s) found");
        return blockers;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    /**
     * Creates an error QualityCheck when a check fails due to an exception.
     */
    private QualityCheck createErrorCheck(String name, String errorMessage) {
        QualityCheck check = new QualityCheck();
        check.name = name;
        check.passed = false;
        check.actualValue = -1;
        check.threshold = -1;
        check.message = errorMessage;
        check.severity = "blocker";
        return check;
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    /**
     * Result of a single data quality check.
     *
     * Each check has a name, pass/fail status, the actual measured value,
     * the threshold used for comparison, a human-readable message, and a
     * severity classification that determines whether the failure blocks
     * ML training or is merely advisory.
     */
    public static class QualityCheck {

        /** Identifier for this check (e.g., "labeled_data_volume", "class_balance") */
        public String name;

        /** Whether this check passed its threshold criteria */
        public boolean passed;

        /** The actual measured value for this check */
        public double actualValue;

        /** The threshold this value was compared against */
        public double threshold;

        /** Human-readable description of the check result */
        public String message;

        /**
         * Severity of a failure:
         * <ul>
         *   <li>"blocker" - must be resolved before ML training can proceed</li>
         *   <li>"warning" - should be addressed but does not prevent training</li>
         *   <li>"info" - informational, no action required</li>
         * </ul>
         */
        public String severity;

        public QualityCheck() {
            this.name = "";
            this.passed = false;
            this.actualValue = 0;
            this.threshold = 0;
            this.message = "";
            this.severity = "info";
        }

        @Override
        public String toString() {
            return "QualityCheck{" +
                    "name='" + name + '\'' +
                    ", passed=" + passed +
                    ", actual=" + actualValue +
                    ", threshold=" + threshold +
                    ", severity='" + severity + '\'' +
                    ", message='" + message + '\'' +
                    '}';
        }
    }

    /**
     * Comprehensive data quality report aggregating all individual quality checks.
     *
     * Provides an overall readiness assessment for ML training, including a numeric
     * score (0-100), a boolean training-ready flag, and categorized lists of
     * blockers and warnings that need attention.
     */
    public static class DataQualityReport {

        /** All individual quality check results */
        public List<QualityCheck> checks;

        /** Overall readiness score from 0 (no checks pass) to 100 (all pass) */
        public int overallScore;

        /**
         * Whether the dataset is ready for ML training.
         * True only when there are no blockers and the overall score >= 70.
         */
        public boolean isTrainingReady;

        /** List of blocker-severity failure messages that prevent training */
        public List<String> blockers;

        /** List of warning-severity messages that should be addressed */
        public List<String> warnings;

        /** Timestamp when this report was generated (epoch milliseconds) */
        public long timestamp;

        public DataQualityReport() {
            this.checks = new ArrayList<>();
            this.overallScore = 0;
            this.isTrainingReady = false;
            this.blockers = new ArrayList<>();
            this.warnings = new ArrayList<>();
            this.timestamp = System.currentTimeMillis();
        }

        /**
         * Returns the number of checks that passed.
         */
        public int getPassedCount() {
            int count = 0;
            for (QualityCheck check : checks) {
                if (check.passed) {
                    count++;
                }
            }
            return count;
        }

        /**
         * Returns the total number of checks run.
         */
        public int getTotalChecks() {
            return checks.size();
        }

        @Override
        public String toString() {
            return "DataQualityReport{" +
                    "score=" + overallScore +
                    ", ready=" + isTrainingReady +
                    ", checks=" + getPassedCount() + "/" + getTotalChecks() +
                    ", blockers=" + blockers.size() +
                    ", warnings=" + warnings.size() +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}
