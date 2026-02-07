package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.ComparisonStatistics;

import java.util.List;

/**
 * DAO for comparison_statistics — statistical significance test results for OTA comparisons.
 */
@Dao
public interface ComparisonStatisticsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ComparisonStatistics stats);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ComparisonStatistics> stats);

    @Update
    void update(ComparisonStatistics stats);

    @Delete
    void delete(ComparisonStatistics stats);

    @Query("SELECT * FROM comparison_statistics WHERE id = :id")
    ComparisonStatistics getById(long id);

    @Query("SELECT * FROM comparison_statistics WHERE verificationId = :verificationId ORDER BY metricName")
    List<ComparisonStatistics> getByVerificationId(long verificationId);

    @Query("SELECT * FROM comparison_statistics WHERE sessionId = :sessionId ORDER BY metricName")
    List<ComparisonStatistics> getBySessionId(long sessionId);

    @Query("SELECT * FROM comparison_statistics WHERE sessionId = :sessionId AND statisticallySignificant = 1 ORDER BY pValue ASC")
    List<ComparisonStatistics> getSignificantBySession(long sessionId);

    @Query("SELECT * FROM comparison_statistics WHERE sessionId = :sessionId AND practicallySignificant = 1 ORDER BY cohenD DESC")
    List<ComparisonStatistics> getPracticallySignificantBySession(long sessionId);

    @Query("SELECT COUNT(*) FROM comparison_statistics WHERE sessionId = :sessionId AND statisticallySignificant = 1")
    int getSignificantCount(long sessionId);

    @Query("DELETE FROM comparison_statistics WHERE sessionId = :sessionId")
    void deleteBySession(long sessionId);
}
