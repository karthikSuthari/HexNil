package com.example.hexnil.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Ground truth labels from engineers, field technicians, RMA systems, or automated validation.
 * Links to anomaly_records, safety_events, regression_events, thermal_events, or update_sessions
 * to provide verified outcomes for measuring AI/heuristic accuracy.
 *
 * Core of the feedback loop: without these labels, precision and recall cannot be measured.
 */
@Entity(tableName = "human_labels", indices = {
        @Index("anomalyRecordId"),
        @Index("safetyEventId"),
        @Index("thermalEventId"),
        @Index("updateSessionId"),
        @Index("groundTruthVerdict"),
        @Index("labelerId"),
        @Index("labeledAt")
})
public class HumanLabel {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // === FK references (nullable — only one should be set per label) ===
    public Long anomalyRecordId;
    public Long safetyEventId;
    public Long regressionEventId;
    public Long thermalEventId;
    public Long updateSessionId;

    // === Labeler Identity ===
    @NonNull
    public String labelerId;            // Anonymized engineer/system ID

    /** "oem_engineer", "field_tech", "rma_system", "automated", "user" */
    public String labelerRole;

    // === Ground Truth ===
    /** "true_positive", "false_positive", "true_negative", "false_negative", "inconclusive" */
    @NonNull
    public String groundTruthVerdict;

    /** If AI got severity wrong, what's the correct severity? */
    public String severityCorrected;

    /** If AI misidentified root cause, what's the correct root cause? */
    public String rootCauseCorrected;

    // === External References ===
    public String rmaTicketId;
    public String labTestId;
    public String userReportId;

    // === Metadata ===
    public long labeledAt;
    public float labelConfidence;       // 0-1, how confident the labeler is
    public String notes;

    public HumanLabel() {
        this.labelerId = "";
        this.groundTruthVerdict = "";
        this.labeledAt = System.currentTimeMillis();
        this.labelConfidence = 1.0f;
    }

    /**
     * Create a label for an anomaly record.
     */
    public static HumanLabel createForAnomaly(long anomalyRecordId, String labelerId,
            String labelerRole, String verdict) {
        HumanLabel label = new HumanLabel();
        label.anomalyRecordId = anomalyRecordId;
        label.labelerId = labelerId;
        label.labelerRole = labelerRole;
        label.groundTruthVerdict = verdict;
        return label;
    }

    /**
     * Create a label for a safety event.
     */
    public static HumanLabel createForSafetyEvent(long safetyEventId, String labelerId,
            String labelerRole, String verdict) {
        HumanLabel label = new HumanLabel();
        label.safetyEventId = safetyEventId;
        label.labelerId = labelerId;
        label.labelerRole = labelerRole;
        label.groundTruthVerdict = verdict;
        return label;
    }

    /**
     * Create a label for a thermal event.
     */
    public static HumanLabel createForThermalEvent(long thermalEventId, String labelerId,
            String labelerRole, String verdict) {
        HumanLabel label = new HumanLabel();
        label.thermalEventId = thermalEventId;
        label.labelerId = labelerId;
        label.labelerRole = labelerRole;
        label.groundTruthVerdict = verdict;
        return label;
    }

    public boolean isTruePositive() {
        return "true_positive".equals(groundTruthVerdict);
    }

    public boolean isFalsePositive() {
        return "false_positive".equals(groundTruthVerdict);
    }

    @Override
    public String toString() {
        return "HumanLabel{" +
                "id=" + id +
                ", verdict=" + groundTruthVerdict +
                ", labeler=" + labelerId +
                ", role=" + labelerRole +
                ", confidence=" + String.format("%.0f%%", labelConfidence * 100) +
                '}';
    }
}
