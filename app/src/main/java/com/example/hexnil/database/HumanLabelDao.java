package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.HumanLabel;

import java.util.List;

/**
 * DAO for human_labels — ground truth labels from engineers, technicians, and automated systems.
 * Core of the validation feedback loop.
 */
@Dao
public interface HumanLabelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(HumanLabel label);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<HumanLabel> labels);

    @Update
    void update(HumanLabel label);

    @Delete
    void delete(HumanLabel label);

    @Query("SELECT * FROM human_labels WHERE id = :id")
    HumanLabel getById(long id);

    // === By linked record ===

    @Query("SELECT * FROM human_labels WHERE anomalyRecordId = :anomalyId ORDER BY labeledAt DESC")
    List<HumanLabel> getForAnomaly(long anomalyId);

    @Query("SELECT * FROM human_labels WHERE safetyEventId = :eventId ORDER BY labeledAt DESC")
    List<HumanLabel> getForSafetyEvent(long eventId);

    @Query("SELECT * FROM human_labels WHERE thermalEventId = :eventId ORDER BY labeledAt DESC")
    List<HumanLabel> getForThermalEvent(long eventId);

    @Query("SELECT * FROM human_labels WHERE updateSessionId = :sessionId ORDER BY labeledAt DESC")
    List<HumanLabel> getForUpdateSession(long sessionId);

    // === By verdict ===

    @Query("SELECT * FROM human_labels WHERE groundTruthVerdict = :verdict ORDER BY labeledAt DESC")
    List<HumanLabel> getByVerdict(String verdict);

    @Query("SELECT * FROM human_labels WHERE groundTruthVerdict = 'true_positive' ORDER BY labeledAt DESC")
    List<HumanLabel> getTruePositives();

    @Query("SELECT * FROM human_labels WHERE groundTruthVerdict = 'false_positive' ORDER BY labeledAt DESC")
    List<HumanLabel> getFalsePositives();

    // === Time-based ===

    @Query("SELECT * FROM human_labels WHERE labeledAt >= :since ORDER BY labeledAt DESC")
    List<HumanLabel> getLabelsSince(long since);

    @Query("SELECT * FROM human_labels WHERE labeledAt >= :start AND labeledAt <= :end ORDER BY labeledAt DESC")
    List<HumanLabel> getLabelsInRange(long start, long end);

    // === Recent labels ===

    @Query("SELECT * FROM human_labels ORDER BY labeledAt DESC LIMIT :limit")
    List<HumanLabel> getRecentLabels(int limit);

    // === Statistics ===

    @Query("SELECT COUNT(*) FROM human_labels")
    int getTotalCount();

    @Query("SELECT COUNT(*) FROM human_labels WHERE labeledAt >= :since")
    int getCountSince(long since);

    @Query("SELECT COUNT(*) FROM human_labels WHERE groundTruthVerdict = :verdict")
    int getCountByVerdict(String verdict);

    @Query("SELECT COUNT(*) FROM human_labels WHERE groundTruthVerdict = 'true_positive' AND anomalyRecordId IS NOT NULL")
    int getTruePositiveAnomalyCount();

    @Query("SELECT COUNT(*) FROM human_labels WHERE groundTruthVerdict = 'false_positive' AND anomalyRecordId IS NOT NULL")
    int getFalsePositiveAnomalyCount();

    /** Get labels for anomalies of a specific type (joins with anomaly_records) */
    @Query("SELECT hl.* FROM human_labels hl " +
           "INNER JOIN anomaly_records ar ON hl.anomalyRecordId = ar.id " +
           "WHERE ar.anomalyType = :anomalyType " +
           "ORDER BY hl.labeledAt DESC")
    List<HumanLabel> getLabelsForAnomalyType(String anomalyType);

    /** Count TP/FP/TN/FN for a specific anomaly type in a time range */
    @Query("SELECT COUNT(*) FROM human_labels hl " +
           "INNER JOIN anomaly_records ar ON hl.anomalyRecordId = ar.id " +
           "WHERE ar.anomalyType = :anomalyType AND hl.groundTruthVerdict = :verdict " +
           "AND hl.labeledAt >= :start AND hl.labeledAt <= :end")
    int getCountByTypeAndVerdict(String anomalyType, String verdict, long start, long end);

    // === Labeling queue (unlabeled anomalies) ===

    @Query("SELECT ar.id FROM anomaly_records ar " +
           "LEFT JOIN human_labels hl ON ar.id = hl.anomalyRecordId " +
           "WHERE hl.id IS NULL " +
           "AND ar.detectedAt >= :since " +
           "ORDER BY ar.confidenceScore DESC " +
           "LIMIT :limit")
    List<Long> getUnlabeledAnomalyIds(long since, int limit);

    // === Cleanup ===

    @Query("DELETE FROM human_labels WHERE labeledAt < :beforeTime")
    void deleteOlderThan(long beforeTime);

    @Query("SELECT * FROM human_labels WHERE id > :lastId ORDER BY id ASC")
    List<HumanLabel> getNewRecords(long lastId);
}
