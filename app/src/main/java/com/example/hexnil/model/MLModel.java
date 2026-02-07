package com.example.hexnil.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * ML Model Registry Entity
 *
 * Tracks machine learning models deployed on-device for battery diagnostics,
 * anomaly detection, health prediction, and OTA impact classification.
 *
 * Each record represents a versioned model artifact with its training metadata,
 * validation metrics, and deployment lifecycle timestamps.
 *
 * Model Types:
 * - isolation_forest:    Unsupervised anomaly detection on drain/thermal signals
 * - autoencoder:         Deep anomaly detection via reconstruction error
 * - xgboost:             Gradient-boosted trees for tabular classification/regression
 * - lstm:                Sequence model for time-series drain forecasting
 * - tflite_classifier:   TensorFlow Lite on-device classifier
 * - random_forest:       Ensemble classifier for OTA impact risk scoring
 *
 * Task Types:
 * - anomaly_detection:          Detect abnormal drain, thermal, or usage patterns
 * - battery_health_prediction:  Predict remaining battery health / cycle life
 * - ota_impact_classification:  Classify expected impact of pending OTA updates
 * - drain_forecasting:          Forecast future drain rate from historical trends
 */
@Entity(tableName = "ml_models", indices = {
        @Index(value = { "modelName" }),
        @Index(value = { "taskType" }),
        @Index(value = { "isActive" }),
        @Index(value = { "modelName", "modelVersion" }, unique = true)
})
public class MLModel {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // ═══════════════════════════════════════════════════════════════════════════════
    // MODEL IDENTITY
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Human-readable model name, e.g. "drain_anomaly_v2" */
    @NonNull
    public String modelName;

    /** Semantic version string, e.g. "1.2.0" */
    @NonNull
    public String modelVersion;

    /** Algorithm family: "isolation_forest", "autoencoder", "xgboost", "lstm", "tflite_classifier", "random_forest" */
    @Nullable
    public String modelType;

    /** Downstream task: "anomaly_detection", "battery_health_prediction", "ota_impact_classification", "drain_forecasting" */
    @Nullable
    public String taskType;

    // ═══════════════════════════════════════════════════════════════════════════════
    // TRAINING METADATA
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Epoch millis when training completed */
    public long trainedOn;

    /** Number of samples used during training */
    public int trainingSampleCount;

    // ═══════════════════════════════════════════════════════════════════════════════
    // VALIDATION METRICS
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Overall accuracy on held-out validation set (0-1) */
    @Nullable
    public Float validationAccuracy;

    /** Precision on positive class (0-1) */
    @Nullable
    public Float validationPrecision;

    /** Recall / sensitivity on positive class (0-1) */
    @Nullable
    public Float validationRecall;

    /** Harmonic mean of precision and recall (0-1) */
    @Nullable
    public Float validationF1;

    /** Area under ROC curve (0-1) */
    @Nullable
    public Float validationAucRoc;

    // ═══════════════════════════════════════════════════════════════════════════════
    // COMPATIBILITY & CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /** JSON array of supported device model identifiers, e.g. ["Pixel 8", "SM-S918B"] */
    @Nullable
    public String deviceModelsSupported;

    /** JSON array of feature column names consumed by this model */
    @Nullable
    public String featureListJson;

    /** JSON object of hyperparameters used during training */
    @Nullable
    public String hyperparametersJson;

    // ═══════════════════════════════════════════════════════════════════════════════
    // DEPLOYMENT LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Epoch millis when the model was deployed to production (null if never deployed) */
    @Nullable
    public Long deployedAt;

    /** Epoch millis when the model was retired (null if still eligible) */
    @Nullable
    public Long retiredAt;

    /** Whether this model is the active version for its task; 1 = active, 0 = inactive */
    public int isActive;

    // ═══════════════════════════════════════════════════════════════════════════════
    // ARTIFACT STORAGE
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Absolute or app-relative path to serialized model file */
    @Nullable
    public String modelFilePath;

    /** Size of the serialized model file in bytes */
    public long modelSizeBytes;

    // ═══════════════════════════════════════════════════════════════════════════════
    // NOTES
    // ═══════════════════════════════════════════════════════════════════════════════

    /** Free-form notes (training details, changelog, known limitations) */
    @Nullable
    public String notes;

    // ═══════════════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR
    // ═══════════════════════════════════════════════════════════════════════════════

    public MLModel() {
        this.isActive = 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Whether this model has been deployed at least once.
     */
    public boolean hasBeenDeployed() {
        return deployedAt != null;
    }

    /**
     * Whether this model is currently retired.
     */
    public boolean isRetired() {
        return retiredAt != null;
    }

    /**
     * Whether validation metrics are available for this model.
     */
    public boolean hasValidationMetrics() {
        return validationAccuracy != null || validationF1 != null || validationAucRoc != null;
    }

    @Override
    public String toString() {
        return "MLModel{" +
                "id=" + id +
                ", name='" + modelName + '\'' +
                ", version='" + modelVersion + '\'' +
                ", type='" + modelType + '\'' +
                ", task='" + taskType + '\'' +
                ", active=" + (isActive == 1) +
                ", f1=" + validationF1 +
                ", aucRoc=" + validationAucRoc +
                '}';
    }
}
