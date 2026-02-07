package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.MLPrediction;

import java.util.List;

/**
 * DAO for ml_predictions — ML model prediction audit trail.
 */
@Dao
public interface MLPredictionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MLPrediction prediction);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MLPrediction> predictions);

    @Update
    void update(MLPrediction prediction);

    @Delete
    void delete(MLPrediction prediction);

    @Query("SELECT * FROM ml_predictions WHERE id = :id")
    MLPrediction getById(long id);

    @Query("SELECT * FROM ml_predictions WHERE modelId = :modelId ORDER BY predictionTimestamp DESC LIMIT :limit")
    List<MLPrediction> getByModel(long modelId, int limit);

    @Query("SELECT * FROM ml_predictions WHERE deviceFingerprint = :fingerprint ORDER BY predictionTimestamp DESC LIMIT :limit")
    List<MLPrediction> getByDevice(String fingerprint, int limit);

    @Query("SELECT * FROM ml_predictions WHERE relatedRecordId = :recordId AND relatedRecordTable = :table LIMIT 1")
    MLPrediction getForRecord(long recordId, String table);

    /** Get predictions that haven't been labeled yet */
    @Query("SELECT * FROM ml_predictions WHERE wasCorrect IS NULL ORDER BY predictionTimestamp DESC LIMIT :limit")
    List<MLPrediction> getUnlabeled(int limit);

    /** Get predictions with ground truth for model evaluation */
    @Query("SELECT * FROM ml_predictions WHERE modelId = :modelId AND wasCorrect IS NOT NULL ORDER BY predictionTimestamp DESC")
    List<MLPrediction> getLabeledByModel(long modelId);

    /** Update ground truth after labeling */
    @Query("UPDATE ml_predictions SET groundTruthLabel = :label, wasCorrect = :correct WHERE id = :id")
    void updateGroundTruth(long id, String label, int correct);

    // === Accuracy Statistics ===

    @Query("SELECT COUNT(*) FROM ml_predictions WHERE modelId = :modelId AND wasCorrect = 1")
    int getCorrectCount(long modelId);

    @Query("SELECT COUNT(*) FROM ml_predictions WHERE modelId = :modelId AND wasCorrect = 0")
    int getIncorrectCount(long modelId);

    @Query("SELECT COUNT(*) FROM ml_predictions WHERE modelId = :modelId AND wasCorrect IS NOT NULL")
    int getLabeledCount(long modelId);

    @Query("SELECT COUNT(*) FROM ml_predictions WHERE modelId = :modelId")
    int getTotalCount(long modelId);

    @Query("SELECT AVG(predictionConfidence) FROM ml_predictions WHERE modelId = :modelId AND wasCorrect = 1")
    Float getAvgConfidenceWhenCorrect(long modelId);

    @Query("SELECT AVG(predictionConfidence) FROM ml_predictions WHERE modelId = :modelId AND wasCorrect = 0")
    Float getAvgConfidenceWhenIncorrect(long modelId);

    // === Cleanup ===

    @Query("DELETE FROM ml_predictions WHERE predictionTimestamp < :beforeTime")
    void deleteOlderThan(long beforeTime);

    @Query("SELECT * FROM ml_predictions WHERE id > :lastId ORDER BY id ASC")
    List<MLPrediction> getNewRecords(long lastId);
}
