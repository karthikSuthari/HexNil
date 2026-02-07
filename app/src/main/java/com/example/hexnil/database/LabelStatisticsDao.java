package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.LabelStatistics;

import java.util.List;

/**
 * DAO for label_statistics — tracks AI/heuristic accuracy metrics over time.
 */
@Dao
public interface LabelStatisticsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LabelStatistics stats);

    @Update
    void update(LabelStatistics stats);

    @Delete
    void delete(LabelStatistics stats);

    @Query("SELECT * FROM label_statistics ORDER BY computedAt DESC LIMIT 1")
    LabelStatistics getLatest();

    @Query("SELECT * FROM label_statistics WHERE anomalyType = :type ORDER BY computedAt DESC LIMIT 1")
    LabelStatistics getLatestForType(String type);

    @Query("SELECT * FROM label_statistics WHERE anomalyType = :type ORDER BY computedAt DESC")
    List<LabelStatistics> getHistoryForType(String type);

    @Query("SELECT * FROM label_statistics ORDER BY computedAt DESC LIMIT :limit")
    List<LabelStatistics> getRecent(int limit);

    @Query("SELECT * FROM label_statistics WHERE computedAt >= :since ORDER BY computedAt DESC")
    List<LabelStatistics> getSince(long since);

    @Query("SELECT DISTINCT anomalyType FROM label_statistics")
    List<String> getTrackedAnomalyTypes();

    @Query("DELETE FROM label_statistics WHERE computedAt < :beforeTime")
    void deleteOlderThan(long beforeTime);
}
