package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.NegativeExample;

import java.util.List;

/**
 * DAO for negative_examples — labeled "normal but high-stress" scenarios for ML training.
 */
@Dao
public interface NegativeExampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(NegativeExample example);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<NegativeExample> examples);

    @Update
    void update(NegativeExample example);

    @Delete
    void delete(NegativeExample example);

    @Query("SELECT * FROM negative_examples WHERE id = :id")
    NegativeExample getById(long id);

    @Query("SELECT * FROM negative_examples ORDER BY timestamp DESC")
    List<NegativeExample> getAll();

    @Query("SELECT * FROM negative_examples WHERE conditionType = :type ORDER BY timestamp DESC")
    List<NegativeExample> getByConditionType(String type);

    @Query("SELECT * FROM negative_examples WHERE deviceFingerprint = :fingerprint ORDER BY timestamp DESC")
    List<NegativeExample> getByDevice(String fingerprint);

    @Query("SELECT * FROM negative_examples WHERE foregroundApp = :packageName ORDER BY timestamp DESC")
    List<NegativeExample> getByApp(String packageName);

    @Query("SELECT * FROM negative_examples WHERE labeledAsNormal = 1 AND usedInTraining = 0 ORDER BY timestamp DESC")
    List<NegativeExample> getUnusedTrainingExamples();

    @Query("SELECT * FROM negative_examples WHERE timestamp >= :since ORDER BY timestamp DESC")
    List<NegativeExample> getSince(long since);

    /** Check if a similar negative example exists (for dedup) */
    @Query("SELECT * FROM negative_examples WHERE conditionType = :type AND foregroundApp = :app AND timestamp >= :since LIMIT 1")
    NegativeExample findSimilarRecent(String type, String app, long since);

    // === Statistics ===

    @Query("SELECT COUNT(*) FROM negative_examples")
    int getTotalCount();

    @Query("SELECT COUNT(*) FROM negative_examples WHERE labeledAsNormal = 1")
    int getNormalCount();

    @Query("SELECT COUNT(DISTINCT deviceFingerprint) FROM negative_examples WHERE deviceFingerprint IS NOT NULL")
    int getDistinctDeviceCount();

    @Query("SELECT conditionType, COUNT(*) as count FROM negative_examples GROUP BY conditionType ORDER BY count DESC")
    List<ConditionTypeCount> getCountByConditionType();

    @Query("UPDATE negative_examples SET usedInTraining = 1, trainingSetVersion = :version WHERE id IN (:ids)")
    void markAsUsedInTraining(List<Long> ids, String version);

    // === Cleanup ===

    @Query("DELETE FROM negative_examples WHERE timestamp < :beforeTime")
    void deleteOlderThan(long beforeTime);

    @Query("SELECT * FROM negative_examples WHERE id > :lastId ORDER BY id ASC")
    List<NegativeExample> getNewRecords(long lastId);

    class ConditionTypeCount {
        public String conditionType;
        public int count;
    }
}
