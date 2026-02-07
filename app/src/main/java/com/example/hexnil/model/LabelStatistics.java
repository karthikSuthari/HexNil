package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Tracks AI/heuristic model performance over time using ground truth labels.
 * Each row represents accuracy metrics for a specific anomaly type within a time period.
 *
 * Enables precision/recall/F1 tracking and threshold calibration validation.
 */
@Entity(tableName = "label_statistics", indices = {
        @Index("anomalyType"),
        @Index("computedAt")
})
public class LabelStatistics {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long computedAt;
    public long periodStart;
    public long periodEnd;

    /** Which anomaly type these stats cover (e.g., "app_drain", "thermal", or "all") */
    public String anomalyType;

    // === Confusion Matrix ===
    public int truePositives;
    public int falsePositives;
    public int trueNegatives;
    public int falseNegatives;

    // === Derived Metrics (computed via calculateMetrics()) ===
    public Float precision;             // TP / (TP + FP)
    public Float recall;                // TP / (TP + FN)
    public Float f1Score;               // 2 * (P * R) / (P + R)
    public Float accuracy;             // (TP + TN) / total
    public int totalLabeled;

    // === Versioning ===
    public String analysisEngineVersion;
    public String thresholdSetVersion;
    public String modelVersion;         // Null until ML models deployed

    public LabelStatistics() {
        this.computedAt = System.currentTimeMillis();
    }

    /**
     * Computes precision, recall, F1, and accuracy from the confusion matrix counts.
     * Call this after setting TP/FP/TN/FN values.
     */
    @Ignore
    public void calculateMetrics() {
        totalLabeled = truePositives + falsePositives + trueNegatives + falseNegatives;

        int ppv_denom = truePositives + falsePositives;
        precision = ppv_denom > 0 ? (float) truePositives / ppv_denom : null;

        int tpr_denom = truePositives + falseNegatives;
        recall = tpr_denom > 0 ? (float) truePositives / tpr_denom : null;

        if (precision != null && recall != null && (precision + recall) > 0) {
            f1Score = 2.0f * (precision * recall) / (precision + recall);
        } else {
            f1Score = null;
        }

        accuracy = totalLabeled > 0
                ? (float) (truePositives + trueNegatives) / totalLabeled
                : null;
    }

    @Override
    public String toString() {
        return "LabelStatistics{" +
                "type=" + anomalyType +
                ", P=" + (precision != null ? String.format("%.1f%%", precision * 100) : "n/a") +
                ", R=" + (recall != null ? String.format("%.1f%%", recall * 100) : "n/a") +
                ", F1=" + (f1Score != null ? String.format("%.1f%%", f1Score * 100) : "n/a") +
                ", total=" + totalLabeled +
                '}';
    }
}
