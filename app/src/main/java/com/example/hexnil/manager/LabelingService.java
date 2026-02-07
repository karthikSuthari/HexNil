package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.HumanLabel;
import com.example.hexnil.model.LabelStatistics;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager class for ground truth labeling infrastructure.
 *
 * Provides APIs for creating human/automated labels on anomaly records,
 * safety events, and thermal events.  Computes confusion-matrix-based
 * accuracy statistics (precision, recall, F1, accuracy) per anomaly type
 * and exposes a labeling queue for unlabeled anomalies.
 *
 * Core of the ML training feedback loop: without these labels, model
 * accuracy cannot be measured or improved.
 */
public class LabelingService {

    private static final String TAG = "LabelingService";

    private static final String VERDICT_TRUE_POSITIVE = "true_positive";
    private static final String VERDICT_FALSE_POSITIVE = "false_positive";
    private static final String VERDICT_TRUE_NEGATIVE = "true_negative";
    private static final String VERDICT_FALSE_NEGATIVE = "false_negative";
    private static final String VERDICT_INCONCLUSIVE = "inconclusive";

    private static final String[] ALL_ANOMALY_TYPES = {
            "app_drain", "thermal", "battery_health",
            "charging", "system", "usage_pattern", "all"
    };

    private final Context context;
    private final HexNilDatabase database;

    public LabelingService(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
    }

    // =========================================================================
    // LABELING API
    // =========================================================================

    /**
     * Create and store a HumanLabel for an anomaly record.
     *
     * @param anomalyRecordId the anomaly record to label
     * @param labelerId       anonymized labeler identity
     * @param labelerRole     role such as "oem_engineer", "field_tech", etc.
     * @param verdict         one of: "true_positive", "false_positive",
     *                        "true_negative", "false_negative", "inconclusive"
     * @param confidence      labeler confidence 0-1
     * @param notes           free-text notes
     * @return the inserted label's row id
     */
    public long labelAnomaly(long anomalyRecordId, String labelerId,
            String labelerRole, String verdict, float confidence, String notes) {
        Log.d(TAG, "labelAnomaly: anomalyId=" + anomalyRecordId
                + " verdict=" + verdict + " labeler=" + labelerId);

        HumanLabel label = HumanLabel.createForAnomaly(
                anomalyRecordId, labelerId, labelerRole, verdict);
        label.labelConfidence = confidence;
        label.notes = notes;

        long id = database.humanLabelDao().insert(label);
        Log.d(TAG, "labelAnomaly: inserted label id=" + id);
        return id;
    }

    /**
     * Create and store a HumanLabel for a safety event.
     *
     * @param safetyEventId the safety event to label
     * @param labelerId     anonymized labeler identity
     * @param labelerRole   role such as "oem_engineer", "field_tech", etc.
     * @param verdict       one of: "true_positive", "false_positive",
     *                      "true_negative", "false_negative", "inconclusive"
     * @param confidence    labeler confidence 0-1
     * @param notes         free-text notes
     * @return the inserted label's row id
     */
    public long labelSafetyEvent(long safetyEventId, String labelerId,
            String labelerRole, String verdict, float confidence, String notes) {
        Log.d(TAG, "labelSafetyEvent: eventId=" + safetyEventId
                + " verdict=" + verdict + " labeler=" + labelerId);

        HumanLabel label = HumanLabel.createForSafetyEvent(
                safetyEventId, labelerId, labelerRole, verdict);
        label.labelConfidence = confidence;
        label.notes = notes;

        long id = database.humanLabelDao().insert(label);
        Log.d(TAG, "labelSafetyEvent: inserted label id=" + id);
        return id;
    }

    /**
     * Create and store a HumanLabel for a thermal event.
     *
     * @param thermalEventId the thermal event to label
     * @param labelerId      anonymized labeler identity
     * @param labelerRole    role such as "oem_engineer", "field_tech", etc.
     * @param verdict        one of: "true_positive", "false_positive",
     *                       "true_negative", "false_negative", "inconclusive"
     * @param confidence     labeler confidence 0-1
     * @param notes          free-text notes
     * @return the inserted label's row id
     */
    public long labelThermalEvent(long thermalEventId, String labelerId,
            String labelerRole, String verdict, float confidence, String notes) {
        Log.d(TAG, "labelThermalEvent: eventId=" + thermalEventId
                + " verdict=" + verdict + " labeler=" + labelerId);

        HumanLabel label = HumanLabel.createForThermalEvent(
                thermalEventId, labelerId, labelerRole, verdict);
        label.labelConfidence = confidence;
        label.notes = notes;

        long id = database.humanLabelDao().insert(label);
        Log.d(TAG, "labelThermalEvent: inserted label id=" + id);
        return id;
    }

    /**
     * Label an anomaly with severity and root cause corrections.
     *
     * Use this when the AI got the anomaly detection right (or wrong) but
     * also misidentified the severity or root cause.
     *
     * @param anomalyRecordId  the anomaly record to label
     * @param labelerId        anonymized labeler identity
     * @param labelerRole      role such as "oem_engineer", "field_tech", etc.
     * @param verdict          one of: "true_positive", "false_positive",
     *                         "true_negative", "false_negative", "inconclusive"
     * @param correctedSeverity correct severity if AI was wrong, or null
     * @param correctedRootCause correct root cause if AI was wrong, or null
     * @param confidence       labeler confidence 0-1
     * @param notes            free-text notes
     * @return the inserted label's row id
     */
    public long labelWithCorrections(long anomalyRecordId, String labelerId,
            String labelerRole, String verdict, String correctedSeverity,
            String correctedRootCause, float confidence, String notes) {
        Log.d(TAG, "labelWithCorrections: anomalyId=" + anomalyRecordId
                + " verdict=" + verdict + " severity=" + correctedSeverity
                + " rootCause=" + correctedRootCause);

        HumanLabel label = HumanLabel.createForAnomaly(
                anomalyRecordId, labelerId, labelerRole, verdict);
        label.severityCorrected = correctedSeverity;
        label.rootCauseCorrected = correctedRootCause;
        label.labelConfidence = confidence;
        label.notes = notes;

        long id = database.humanLabelDao().insert(label);
        Log.d(TAG, "labelWithCorrections: inserted label id=" + id);
        return id;
    }

    /**
     * Automated label from RMA (Return Merchandise Authorization) system.
     *
     * When an RMA ticket confirms a hardware defect, this creates a label
     * with high confidence linking the anomaly to the RMA evidence.
     *
     * @param anomalyRecordId the anomaly record to label
     * @param rmaTicketId     the RMA ticket identifier
     * @param verdict         one of: "true_positive", "false_positive",
     *                        "true_negative", "false_negative", "inconclusive"
     * @return the inserted label's row id
     */
    public long autoLabelFromRMA(long anomalyRecordId, String rmaTicketId,
            String verdict) {
        Log.d(TAG, "autoLabelFromRMA: anomalyId=" + anomalyRecordId
                + " rmaTicket=" + rmaTicketId + " verdict=" + verdict);

        HumanLabel label = HumanLabel.createForAnomaly(
                anomalyRecordId, "rma_system", "rma_system", verdict);
        label.rmaTicketId = rmaTicketId;
        label.labelConfidence = 0.9f;
        label.notes = "Auto-labeled from RMA ticket: " + rmaTicketId;

        long id = database.humanLabelDao().insert(label);
        Log.d(TAG, "autoLabelFromRMA: inserted label id=" + id);
        return id;
    }

    /**
     * Automated label from lab test results.
     *
     * When a controlled lab test confirms or refutes an anomaly, this
     * creates a label with high confidence linking to the lab evidence.
     *
     * @param anomalyRecordId the anomaly record to label
     * @param labTestId       the lab test identifier
     * @param verdict         one of: "true_positive", "false_positive",
     *                        "true_negative", "false_negative", "inconclusive"
     * @return the inserted label's row id
     */
    public long autoLabelFromLabTest(long anomalyRecordId, String labTestId,
            String verdict) {
        Log.d(TAG, "autoLabelFromLabTest: anomalyId=" + anomalyRecordId
                + " labTest=" + labTestId + " verdict=" + verdict);

        HumanLabel label = HumanLabel.createForAnomaly(
                anomalyRecordId, "lab_system", "automated", verdict);
        label.labTestId = labTestId;
        label.labelConfidence = 0.95f;
        label.notes = "Auto-labeled from lab test: " + labTestId;

        long id = database.humanLabelDao().insert(label);
        Log.d(TAG, "autoLabelFromLabTest: inserted label id=" + id);
        return id;
    }

    // =========================================================================
    // LABELING QUEUE
    // =========================================================================

    /**
     * Returns a list of anomaly record IDs that have not yet been labeled.
     *
     * Useful for building a labeling queue UI or batch-processing workflow.
     * Results are ordered by the anomaly's confidence score (highest first)
     * so the most impactful anomalies are labeled first.
     *
     * @param limit maximum number of IDs to return
     * @return list of unlabeled anomaly record IDs
     */
    public List<Long> getUnlabeledAnomalyQueue(int limit) {
        Log.d(TAG, "getUnlabeledAnomalyQueue: limit=" + limit);
        List<Long> ids = database.humanLabelDao().getUnlabeledAnomalyIds(0L, limit);
        Log.d(TAG, "getUnlabeledAnomalyQueue: found " + ids.size() + " unlabeled anomalies");
        return ids;
    }

    // =========================================================================
    // LABELING PROGRESS
    // =========================================================================

    /**
     * Computes and returns a snapshot of overall labeling progress.
     *
     * Queries the database for total anomaly count, labeled count, and
     * per-verdict breakdowns.  The returned object includes a labeling
     * percentage for progress tracking dashboards.
     *
     * @return a {@link LabelingProgress} with current counts
     */
    public LabelingProgress getLabelingProgress() {
        Log.d(TAG, "getLabelingProgress: computing progress snapshot");

        LabelingProgress progress = new LabelingProgress();

        progress.labeledCount = database.humanLabelDao().getTotalCount();

        progress.truePositiveCount = database.humanLabelDao()
                .getCountByVerdict(VERDICT_TRUE_POSITIVE);
        progress.falsePositiveCount = database.humanLabelDao()
                .getCountByVerdict(VERDICT_FALSE_POSITIVE);
        progress.trueNegativeCount = database.humanLabelDao()
                .getCountByVerdict(VERDICT_TRUE_NEGATIVE);
        progress.falseNegativeCount = database.humanLabelDao()
                .getCountByVerdict(VERDICT_FALSE_NEGATIVE);
        progress.inconclusiveCount = database.humanLabelDao()
                .getCountByVerdict(VERDICT_INCONCLUSIVE);

        int unlabeledCount = database.humanLabelDao()
                .getUnlabeledAnomalyIds(0L, Integer.MAX_VALUE).size();
        progress.unlabeledCount = unlabeledCount;

        progress.totalAnomalies = progress.labeledCount + progress.unlabeledCount;

        if (progress.totalAnomalies > 0) {
            progress.labelingPercentage =
                    (float) progress.labeledCount / progress.totalAnomalies * 100.0f;
        } else {
            progress.labelingPercentage = 0.0f;
        }

        Log.d(TAG, "getLabelingProgress: total=" + progress.totalAnomalies
                + " labeled=" + progress.labeledCount
                + " unlabeled=" + progress.unlabeledCount
                + " pct=" + String.format("%.1f%%", progress.labelingPercentage));

        return progress;
    }

    // =========================================================================
    // STATISTICS COMPUTATION
    // =========================================================================

    /**
     * Computes label statistics (confusion matrix + derived metrics) for a
     * specific anomaly type within a time period.
     *
     * Queries the DAO for per-verdict counts filtered by anomaly type and
     * time range, then creates a {@link LabelStatistics} entry with the
     * confusion matrix values.  Calls {@link LabelStatistics#calculateMetrics()}
     * to derive precision, recall, F1, and accuracy.  The result is persisted
     * to the database and returned.
     *
     * @param anomalyType the anomaly type (e.g. "app_drain", "thermal", "all")
     * @param periodStart start of the measurement period (epoch ms)
     * @param periodEnd   end of the measurement period (epoch ms)
     * @return the computed and persisted {@link LabelStatistics}
     */
    public LabelStatistics computeLabelStatistics(String anomalyType,
            long periodStart, long periodEnd) {
        Log.d(TAG, "computeLabelStatistics: type=" + anomalyType
                + " start=" + periodStart + " end=" + periodEnd);

        int tp;
        int fp;
        int tn;
        int fn;

        if ("all".equals(anomalyType)) {
            List<HumanLabel> labelsInRange = database.humanLabelDao()
                    .getLabelsInRange(periodStart, periodEnd);

            tp = 0;
            fp = 0;
            tn = 0;
            fn = 0;

            for (HumanLabel label : labelsInRange) {
                switch (label.groundTruthVerdict) {
                    case VERDICT_TRUE_POSITIVE:
                        tp++;
                        break;
                    case VERDICT_FALSE_POSITIVE:
                        fp++;
                        break;
                    case VERDICT_TRUE_NEGATIVE:
                        tn++;
                        break;
                    case VERDICT_FALSE_NEGATIVE:
                        fn++;
                        break;
                    default:
                        break;
                }
            }
        } else {
            tp = database.humanLabelDao().getCountByTypeAndVerdict(
                    anomalyType, VERDICT_TRUE_POSITIVE, periodStart, periodEnd);
            fp = database.humanLabelDao().getCountByTypeAndVerdict(
                    anomalyType, VERDICT_FALSE_POSITIVE, periodStart, periodEnd);
            tn = database.humanLabelDao().getCountByTypeAndVerdict(
                    anomalyType, VERDICT_TRUE_NEGATIVE, periodStart, periodEnd);
            fn = database.humanLabelDao().getCountByTypeAndVerdict(
                    anomalyType, VERDICT_FALSE_NEGATIVE, periodStart, periodEnd);
        }

        LabelStatistics stats = new LabelStatistics();
        stats.periodStart = periodStart;
        stats.periodEnd = periodEnd;
        stats.anomalyType = anomalyType;
        stats.truePositives = tp;
        stats.falsePositives = fp;
        stats.trueNegatives = tn;
        stats.falseNegatives = fn;
        stats.calculateMetrics();

        long id = database.labelStatisticsDao().insert(stats);
        stats.id = id;

        Log.d(TAG, "computeLabelStatistics: " + stats);
        return stats;
    }

    /**
     * Computes label statistics for all standard anomaly types plus "all".
     *
     * Iterates over: "app_drain", "thermal", "battery_health", "charging",
     * "system", "usage_pattern", and "all".  Each type gets its own
     * {@link LabelStatistics} row persisted to the database.
     *
     * @param periodStart start of the measurement period (epoch ms)
     * @param periodEnd   end of the measurement period (epoch ms)
     * @return list of computed {@link LabelStatistics} for all types
     */
    public List<LabelStatistics> computeAllStatistics(long periodStart,
            long periodEnd) {
        Log.d(TAG, "computeAllStatistics: start=" + periodStart
                + " end=" + periodEnd);

        List<LabelStatistics> results = new ArrayList<>();

        for (String type : ALL_ANOMALY_TYPES) {
            LabelStatistics stats = computeLabelStatistics(
                    type, periodStart, periodEnd);
            results.add(stats);
        }

        Log.d(TAG, "computeAllStatistics: computed " + results.size()
                + " statistics entries");
        return results;
    }

    /**
     * Returns the most recent {@link LabelStatistics} for a given anomaly type.
     *
     * @param anomalyType the anomaly type to query
     * @return the latest statistics, or null if none exist
     */
    public LabelStatistics getLatestStatistics(String anomalyType) {
        Log.d(TAG, "getLatestStatistics: type=" + anomalyType);
        LabelStatistics stats = database.labelStatisticsDao()
                .getLatestForType(anomalyType);
        if (stats != null) {
            Log.d(TAG, "getLatestStatistics: found " + stats);
        } else {
            Log.d(TAG, "getLatestStatistics: no statistics found for type="
                    + anomalyType);
        }
        return stats;
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    /**
     * Snapshot of overall labeling progress across all anomaly records.
     *
     * Provides counts for each verdict category and a computed labeling
     * percentage for progress dashboards.
     */
    public static class LabelingProgress {
        public int totalAnomalies;
        public int labeledCount;
        public int unlabeledCount;
        public int truePositiveCount;
        public int falsePositiveCount;
        public int trueNegativeCount;
        public int falseNegativeCount;
        public int inconclusiveCount;
        public float labelingPercentage;

        @Override
        public String toString() {
            return "LabelingProgress{"
                    + "total=" + totalAnomalies
                    + ", labeled=" + labeledCount
                    + ", unlabeled=" + unlabeledCount
                    + ", TP=" + truePositiveCount
                    + ", FP=" + falsePositiveCount
                    + ", TN=" + trueNegativeCount
                    + ", FN=" + falseNegativeCount
                    + ", inconclusive=" + inconclusiveCount
                    + ", pct=" + String.format("%.1f%%", labelingPercentage)
                    + '}';
        }
    }
}
