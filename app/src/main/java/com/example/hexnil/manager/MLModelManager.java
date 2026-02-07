package com.example.hexnil.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.MLModel;
import com.example.hexnil.model.MLPrediction;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages the full ML model lifecycle for HexNil battery diagnostics:
 * registration, deployment, inference recording, evaluation, promotion, and retirement.
 *
 * Each model progresses through a lifecycle:
 *   registered -> deployed (active) -> retired
 *
 * Only one version of a given model name may be active at a time.
 * Predictions are recorded against the active model and can be evaluated
 * once ground-truth labels are applied, enabling closed-loop model improvement.
 */
public class MLModelManager {

    private static final String TAG = "MLModelManager";

    private final Context context;
    private final HexNilDatabase database;

    public MLModelManager(Context context, HexNilDatabase database) {
        this.context = context;
        this.database = database;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MODEL LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Register a new ML model in the registry.
     * The model starts in an inactive state until explicitly deployed.
     *
     * @param name               human-readable model name (e.g. "drain_anomaly_v2")
     * @param version            semantic version string (e.g. "1.2.0")
     * @param modelType          algorithm family (e.g. "isolation_forest", "xgboost")
     * @param taskType           downstream task (e.g. "anomaly_detection")
     * @param trainingSampleCount number of samples used during training
     * @param featureListJson    JSON array of feature column names
     * @return the newly registered MLModel with its auto-generated ID populated
     */
    public MLModel registerModel(String name, String version, String modelType,
                                  String taskType, int trainingSampleCount,
                                  String featureListJson) {
        MLModel model = new MLModel();
        model.modelName = name;
        model.modelVersion = version;
        model.modelType = modelType;
        model.taskType = taskType;
        model.trainedOn = System.currentTimeMillis();
        model.trainingSampleCount = trainingSampleCount;
        model.featureListJson = featureListJson;
        model.isActive = 0;

        long id = database.mlModelDao().insert(model);
        model.id = id;

        Log.d(TAG, "Registered model: " + name + " v" + version
                + " (type=" + modelType + ", task=" + taskType + ", id=" + id + ")");
        return model;
    }

    /**
     * Deploy a model, making it the active version for its model name.
     * All other versions with the same name are deactivated first.
     *
     * @param modelId the ID of the model to deploy
     * @return the deployed model, or null if the model was not found
     */
    @Nullable
    public MLModel deployModel(long modelId) {
        MLModel model = database.mlModelDao().getById(modelId);
        if (model == null) {
            Log.w(TAG, "deployModel: model not found for id=" + modelId);
            return null;
        }

        long now = System.currentTimeMillis();

        // Deactivate all other versions of this model name
        database.mlModelDao().deactivateAllVersions(model.modelName, now);

        // Activate the target model
        model.isActive = 1;
        model.deployedAt = now;
        model.retiredAt = null;
        database.mlModelDao().update(model);

        Log.d(TAG, "Deployed model: " + model.modelName + " v" + model.modelVersion
                + " (id=" + modelId + ")");
        return model;
    }

    /**
     * Retire a model, marking it as inactive.
     *
     * @param modelId the ID of the model to retire
     */
    public void retireModel(long modelId) {
        MLModel model = database.mlModelDao().getById(modelId);
        if (model == null) {
            Log.w(TAG, "retireModel: model not found for id=" + modelId);
            return;
        }

        model.isActive = 0;
        model.retiredAt = System.currentTimeMillis();
        database.mlModelDao().update(model);

        Log.d(TAG, "Retired model: " + model.modelName + " v" + model.modelVersion
                + " (id=" + modelId + ")");
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MODEL LOOKUP
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Get the currently active model for a given model name.
     *
     * @param modelName the model name to look up
     * @return the active model, or null if none is active
     */
    @Nullable
    public MLModel getActiveModel(String modelName) {
        return database.mlModelDao().getActiveModel(modelName);
    }

    /**
     * Get the latest active model for a given task type.
     * If multiple models serve the same task, the most recently deployed one is returned.
     *
     * @param taskType the task type (e.g. "anomaly_detection")
     * @return the latest active model for the task, or null if none
     */
    @Nullable
    public MLModel getActiveModelForTask(String taskType) {
        List<MLModel> models = database.mlModelDao().getActiveModelsForTask(taskType);
        if (models == null || models.isEmpty()) {
            return null;
        }
        // getActiveModelsForTask is ordered by deployedAt DESC, so first is latest
        return models.get(0);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // PREDICTION RECORDING
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Record a prediction made by an ML model.
     *
     * @param modelId            the model that produced the prediction
     * @param predictedClass     model output class (e.g. "anomaly", "normal")
     * @param confidence         prediction confidence, 0-1
     * @param deviceFingerprint  unique device identifier
     * @param relatedRecordId    ID of the source record
     * @param relatedRecordTable table that the source record belongs to
     * @param inputFeaturesJson  JSON-serialized feature vector fed to the model
     * @param inferenceTimeMs    time taken for model inference in milliseconds
     * @return the recorded prediction with its auto-generated ID, or null if model not found
     */
    @Nullable
    public MLPrediction recordPrediction(long modelId, String predictedClass,
                                          float confidence, String deviceFingerprint,
                                          long relatedRecordId, String relatedRecordTable,
                                          String inputFeaturesJson, long inferenceTimeMs) {
        MLModel model = database.mlModelDao().getById(modelId);
        if (model == null) {
            Log.w(TAG, "recordPrediction: model not found for id=" + modelId);
            return null;
        }

        MLPrediction prediction = MLPrediction.create(
                modelId,
                model.modelVersion,
                predictedClass,
                confidence,
                deviceFingerprint,
                relatedRecordId,
                relatedRecordTable
        );
        prediction.inputFeaturesJson = inputFeaturesJson;
        prediction.inferenceTimeMs = inferenceTimeMs;

        long id = database.mlPredictionDao().insert(prediction);
        prediction.id = id;

        Log.d(TAG, "Recorded prediction: model=" + model.modelName
                + " v" + model.modelVersion + ", class=" + predictedClass
                + ", confidence=" + String.format("%.2f", confidence)
                + ", inferenceMs=" + inferenceTimeMs);
        return prediction;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // MODEL EVALUATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Evaluate a model's accuracy by computing metrics from labeled predictions.
     *
     * @param modelId the model to evaluate
     * @return evaluation results, or null if the model was not found
     */
    @Nullable
    public ModelEvaluation evaluateModel(long modelId) {
        MLModel model = database.mlModelDao().getById(modelId);
        if (model == null) {
            Log.w(TAG, "evaluateModel: model not found for id=" + modelId);
            return null;
        }

        int totalPredictions = database.mlPredictionDao().getTotalCount(modelId);
        int labeledPredictions = database.mlPredictionDao().getLabeledCount(modelId);
        int correctPredictions = database.mlPredictionDao().getCorrectCount(modelId);
        int incorrectPredictions = database.mlPredictionDao().getIncorrectCount(modelId);

        Float avgConfidenceCorrect = database.mlPredictionDao().getAvgConfidenceWhenCorrect(modelId);
        Float avgConfidenceIncorrect = database.mlPredictionDao().getAvgConfidenceWhenIncorrect(modelId);

        float accuracy = 0f;
        float precision = 0f;
        float recall = 0f;
        float f1Score = 0f;

        if (labeledPredictions > 0) {
            accuracy = (float) correctPredictions / labeledPredictions;

            // For binary classification:
            // precision = TP / (TP + FP) -> correct / (correct + incorrect) when treating
            // "correct" as true positives in a simplified binary view
            // recall = TP / (TP + FN) -> in a simplified view, same as accuracy when
            // we only have correct/incorrect labels
            //
            // With the available data (wasCorrect = 1 or 0), we approximate:
            //   precision = correctPredictions / labeledPredictions
            //   recall = correctPredictions / labeledPredictions
            // This is an approximation; for multi-class, per-class metrics would be needed
            precision = labeledPredictions > 0
                    ? (float) correctPredictions / labeledPredictions : 0f;
            recall = labeledPredictions > 0
                    ? (float) correctPredictions / labeledPredictions : 0f;

            if (precision + recall > 0) {
                f1Score = 2f * (precision * recall) / (precision + recall);
            }
        }

        // Calibration gap: difference between average confidence and actual accuracy
        float calibrationGap = 0f;
        if (avgConfidenceCorrect != null && labeledPredictions > 0) {
            // Weighted average confidence across all labeled predictions
            float weightedConfidence;
            if (avgConfidenceIncorrect != null && incorrectPredictions > 0) {
                weightedConfidence = (avgConfidenceCorrect * correctPredictions
                        + avgConfidenceIncorrect * incorrectPredictions) / labeledPredictions;
            } else {
                weightedConfidence = avgConfidenceCorrect;
            }
            calibrationGap = Math.abs(weightedConfidence - accuracy);
        }

        ModelEvaluation evaluation = new ModelEvaluation();
        evaluation.modelId = modelId;
        evaluation.modelName = model.modelName;
        evaluation.modelVersion = model.modelVersion;
        evaluation.totalPredictions = totalPredictions;
        evaluation.labeledPredictions = labeledPredictions;
        evaluation.correctPredictions = correctPredictions;
        evaluation.accuracy = accuracy;
        evaluation.precision = precision;
        evaluation.recall = recall;
        evaluation.f1Score = f1Score;
        evaluation.avgConfidenceCorrect = avgConfidenceCorrect;
        evaluation.avgConfidenceIncorrect = avgConfidenceIncorrect;
        evaluation.calibrationGap = calibrationGap;
        evaluation.evaluatedAt = System.currentTimeMillis();

        Log.d(TAG, "Evaluated model " + model.modelName + " v" + model.modelVersion
                + ": accuracy=" + String.format("%.3f", accuracy)
                + ", f1=" + String.format("%.3f", f1Score)
                + ", labeled=" + labeledPredictions + "/" + totalPredictions
                + ", calibrationGap=" + String.format("%.3f", calibrationGap));
        return evaluation;
    }

    /**
     * Get performance summary for a model including prediction counts,
     * accuracy percentage, confidence analysis, and calibration gap.
     *
     * @param modelId the model to query
     * @return performance summary, or null if the model was not found
     */
    @Nullable
    public ModelPerformance getModelPerformance(long modelId) {
        MLModel model = database.mlModelDao().getById(modelId);
        if (model == null) {
            Log.w(TAG, "getModelPerformance: model not found for id=" + modelId);
            return null;
        }

        int totalPredictions = database.mlPredictionDao().getTotalCount(modelId);
        int labeledCount = database.mlPredictionDao().getLabeledCount(modelId);
        int correctCount = database.mlPredictionDao().getCorrectCount(modelId);
        int incorrectCount = database.mlPredictionDao().getIncorrectCount(modelId);

        Float avgConfidenceCorrect = database.mlPredictionDao().getAvgConfidenceWhenCorrect(modelId);
        Float avgConfidenceIncorrect = database.mlPredictionDao().getAvgConfidenceWhenIncorrect(modelId);

        float accuracyPercent = labeledCount > 0
                ? ((float) correctCount / labeledCount) * 100f : 0f;

        // Calibration gap: difference between model's average confidence and its actual accuracy
        Float calibrationGap = null;
        if (labeledCount > 0) {
            float actualAccuracy = (float) correctCount / labeledCount;
            float weightedConfidence = 0f;
            boolean hasConfidence = false;

            if (avgConfidenceCorrect != null && correctCount > 0) {
                weightedConfidence += avgConfidenceCorrect * correctCount;
                hasConfidence = true;
            }
            if (avgConfidenceIncorrect != null && incorrectCount > 0) {
                weightedConfidence += avgConfidenceIncorrect * incorrectCount;
                hasConfidence = true;
            }

            if (hasConfidence) {
                weightedConfidence /= labeledCount;
                calibrationGap = Math.abs(weightedConfidence - actualAccuracy);
            }
        }

        ModelPerformance performance = new ModelPerformance();
        performance.modelId = modelId;
        performance.totalPredictions = totalPredictions;
        performance.labeledCount = labeledCount;
        performance.correctCount = correctCount;
        performance.incorrectCount = incorrectCount;
        performance.accuracyPercent = accuracyPercent;
        performance.avgConfidenceCorrect = avgConfidenceCorrect;
        performance.avgConfidenceIncorrect = avgConfidenceIncorrect;
        performance.calibrationGap = calibrationGap;

        return performance;
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // AUTO-PROMOTION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Automatically promote the best-performing model for a given task type.
     *
     * All models registered for the task type are evaluated. The model with the
     * highest F1 score (or highest accuracy if no F1 is available) is deployed.
     * A model must have at least one labeled prediction to be eligible.
     *
     * @param taskType the task type to promote for (e.g. "anomaly_detection")
     * @return the promoted model, or null if no eligible model was found
     */
    @Nullable
    public MLModel promoteBestModel(String taskType) {
        List<MLModel> allModels = database.mlModelDao().getAll();
        if (allModels == null || allModels.isEmpty()) {
            Log.d(TAG, "promoteBestModel: no models found");
            return null;
        }

        MLModel bestModel = null;
        float bestScore = -1f;

        for (MLModel model : allModels) {
            // Only consider models for the requested task type
            if (!taskType.equals(model.taskType)) {
                continue;
            }

            // Skip retired models (unless they have no retirement timestamp,
            // meaning they are just inactive but not permanently retired)
            if (model.isRetired()) {
                continue;
            }

            // Prefer validation F1 from training if available
            float score = -1f;
            if (model.validationF1 != null && model.validationF1 > 0) {
                score = model.validationF1;
            } else if (model.validationAccuracy != null && model.validationAccuracy > 0) {
                score = model.validationAccuracy;
            } else {
                // Fall back to runtime evaluation
                ModelEvaluation evaluation = evaluateModel(model.id);
                if (evaluation != null && evaluation.labeledPredictions > 0) {
                    score = evaluation.f1Score > 0 ? evaluation.f1Score : evaluation.accuracy;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestModel = model;
            }
        }

        if (bestModel == null) {
            Log.d(TAG, "promoteBestModel: no eligible model found for task=" + taskType);
            return null;
        }

        Log.d(TAG, "promoteBestModel: promoting " + bestModel.modelName
                + " v" + bestModel.modelVersion + " with score=" + String.format("%.3f", bestScore)
                + " for task=" + taskType);
        return deployModel(bestModel.id);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Delete predictions older than the specified retention period.
     *
     * @param retentionDays number of days to retain predictions
     */
    public void cleanupOldPredictions(int retentionDays) {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
        database.mlPredictionDao().deleteOlderThan(cutoff);
        Log.d(TAG, "Cleaned up predictions older than " + retentionDays + " days");
    }

    /**
     * Delete retired models older than the specified retention period.
     *
     * @param retentionDays number of days to retain retired models
     */
    public void cleanupRetiredModels(int retentionDays) {
        long cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(retentionDays);
        database.mlModelDao().deleteOldRetired(cutoff);
        Log.d(TAG, "Cleaned up retired models older than " + retentionDays + " days");
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Comprehensive evaluation results for a model based on labeled predictions.
     *
     * Includes classification metrics (accuracy, precision, recall, F1),
     * confidence analysis, and calibration gap measurement.
     */
    public static class ModelEvaluation {

        /** ID of the evaluated model */
        public long modelId;

        /** Human-readable model name */
        public String modelName;

        /** Model version string */
        public String modelVersion;

        /** Total number of predictions made by this model */
        public int totalPredictions;

        /** Number of predictions that have been labeled with ground truth */
        public int labeledPredictions;

        /** Number of predictions that matched ground truth */
        public int correctPredictions;

        /** Fraction of labeled predictions that were correct (0-1) */
        public float accuracy;

        /** Precision: fraction of positive predictions that are correct (0-1) */
        public float precision;

        /** Recall: fraction of actual positives correctly predicted (0-1) */
        public float recall;

        /** F1 score: harmonic mean of precision and recall (0-1) */
        public float f1Score;

        /** Average confidence score on correct predictions, null if no correct predictions */
        @Nullable
        public Float avgConfidenceCorrect;

        /** Average confidence score on incorrect predictions, null if no incorrect predictions */
        @Nullable
        public Float avgConfidenceIncorrect;

        /** Absolute difference between average confidence and actual accuracy */
        public float calibrationGap;

        /** Epoch millis when this evaluation was computed */
        public long evaluatedAt;

        @Override
        public String toString() {
            return "ModelEvaluation{" +
                    "model='" + modelName + " v" + modelVersion + '\'' +
                    ", accuracy=" + String.format("%.3f", accuracy) +
                    ", precision=" + String.format("%.3f", precision) +
                    ", recall=" + String.format("%.3f", recall) +
                    ", f1=" + String.format("%.3f", f1Score) +
                    ", labeled=" + labeledPredictions + "/" + totalPredictions +
                    ", calibrationGap=" + String.format("%.3f", calibrationGap) +
                    '}';
        }
    }

    /**
     * Summary of a model's prediction performance including counts,
     * accuracy percentage, confidence statistics, and calibration gap.
     */
    public static class ModelPerformance {

        /** ID of the model */
        public long modelId;

        /** Total number of predictions made */
        public int totalPredictions;

        /** Number of predictions that have been labeled */
        public int labeledCount;

        /** Number of correct predictions */
        public int correctCount;

        /** Number of incorrect predictions */
        public int incorrectCount;

        /** Accuracy as a percentage (0-100) */
        public float accuracyPercent;

        /** Average confidence when predictions were correct, null if none */
        @Nullable
        public Float avgConfidenceCorrect;

        /** Average confidence when predictions were incorrect, null if none */
        @Nullable
        public Float avgConfidenceIncorrect;

        /** Calibration gap: difference between model confidence and actual accuracy, null if insufficient data */
        @Nullable
        public Float calibrationGap;

        @Override
        public String toString() {
            return "ModelPerformance{" +
                    "modelId=" + modelId +
                    ", total=" + totalPredictions +
                    ", labeled=" + labeledCount +
                    ", correct=" + correctCount +
                    ", incorrect=" + incorrectCount +
                    ", accuracy=" + String.format("%.1f%%", accuracyPercent) +
                    ", calibrationGap=" + calibrationGap +
                    '}';
        }
    }
}
