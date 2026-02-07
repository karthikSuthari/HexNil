package com.example.hexnil.model;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Stores ML model prediction results for audit, accuracy tracking, and analysis.
 *
 * Each row represents a single inference run by an on-device or server-side model.
 * Ground-truth labels and correctness flags are filled in retrospectively once
 * the prediction can be verified, enabling closed-loop model evaluation.
 *
 * Indices are chosen for the most common query patterns:
 *   - filter by model, time range, device, source table, or labeling status.
 */
@Entity(
    tableName = "ml_predictions",
    indices = {
        @Index(value = {"modelId"}),
        @Index(value = {"predictionTimestamp"}),
        @Index(value = {"deviceFingerprint"}),
        @Index(value = {"relatedRecordTable"}),
        @Index(value = {"wasCorrect"})
    }
)
public class MLPrediction {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Foreign key to the ml_models table. */
    public long modelId;

    /** Wall-clock time when inference was performed. */
    public long predictionTimestamp;

    /** JSON-serialized feature vector that was fed to the model. */
    public String inputFeaturesJson;

    /** Model output class: "anomaly", "normal", "regression", etc. */
    public String predictedClass;

    /** Confidence score for the predicted class, range 0-1. */
    public float predictionConfidence;

    /** Predicted severity level: "critical", "high", "medium", "low". Null if not applicable. */
    @Nullable
    public String predictedSeverity;

    /** Ground-truth label assigned after human or automated review. Null until labeled. */
    @Nullable
    public String groundTruthLabel;

    /** 1 if the prediction matched ground truth, 0 if wrong, null if not yet labeled. */
    @Nullable
    public Integer wasCorrect;

    /** Unique device fingerprint for fleet-level analysis. */
    public String deviceFingerprint;

    /** ID of the related record (anomaly, event, session, etc.). */
    public long relatedRecordId;

    /** Table name that {@link #relatedRecordId} refers to. */
    public String relatedRecordTable;

    /** Time taken for model inference in milliseconds. */
    public long inferenceTimeMs;

    /** Denormalized model version string for fast queries without joining ml_models. */
    public String modelVersion;

    public MLPrediction() {
        this.predictionTimestamp = System.currentTimeMillis();
    }

    /**
     * Factory method for creating a new prediction record with required fields populated.
     *
     * @param modelId            foreign key to the ml_models table
     * @param modelVersion       denormalized version string
     * @param predictedClass     model output class
     * @param confidence         prediction confidence, 0-1
     * @param deviceFingerprint  unique device identifier
     * @param relatedRecordId    ID of the source record
     * @param relatedRecordTable table that the source record belongs to
     * @return a new MLPrediction with timestamp set to now
     */
    public static MLPrediction create(long modelId, String modelVersion,
                                      String predictedClass, float confidence,
                                      String deviceFingerprint,
                                      long relatedRecordId, String relatedRecordTable) {
        MLPrediction prediction = new MLPrediction();
        prediction.modelId = modelId;
        prediction.modelVersion = modelVersion;
        prediction.predictedClass = predictedClass;
        prediction.predictionConfidence = confidence;
        prediction.deviceFingerprint = deviceFingerprint;
        prediction.relatedRecordId = relatedRecordId;
        prediction.relatedRecordTable = relatedRecordTable;
        return prediction;
    }

    @Override
    public String toString() {
        return "MLPrediction{" +
                "id=" + id +
                ", modelId=" + modelId +
                ", predictedClass='" + predictedClass + '\'' +
                ", confidence=" + String.format("%.2f", predictionConfidence) +
                ", severity=" + predictedSeverity +
                ", wasCorrect=" + wasCorrect +
                ", table=" + relatedRecordTable +
                ", recordId=" + relatedRecordId +
                '}';
    }
}
