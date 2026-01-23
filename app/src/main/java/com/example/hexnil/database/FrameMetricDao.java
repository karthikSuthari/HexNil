package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.FrameMetric;

import java.util.List;

/**
 * DAO for FrameMetric entity.
 * Provides CRUD operations and jank analysis queries.
 */
@Dao
public interface FrameMetricDao {

    // =========================================================================
    // Insert Operations
    // =========================================================================

    @Insert
    long insert(FrameMetric metric);

    @Insert
    void insertAll(List<FrameMetric> metrics);

    // =========================================================================
    // Query Operations
    // =========================================================================

    @Query("SELECT * FROM frame_metrics ORDER BY sessionStart DESC LIMIT 1")
    FrameMetric getLatest();

    @Query("SELECT * FROM frame_metrics WHERE id = :id")
    FrameMetric getById(long id);

    @Query("SELECT * FROM frame_metrics WHERE sessionStart >= :since ORDER BY sessionStart DESC")
    List<FrameMetric> getSince(long since);

    @Query("SELECT * FROM frame_metrics WHERE sessionStart BETWEEN :start AND :end ORDER BY sessionStart ASC")
    List<FrameMetric> getInRange(long start, long end);

    @Query("SELECT * FROM frame_metrics ORDER BY sessionStart DESC LIMIT :limit")
    List<FrameMetric> getRecent(int limit);

    // =========================================================================
    // Package-Specific Queries
    // =========================================================================

    @Query("SELECT * FROM frame_metrics WHERE packageName = :packageName ORDER BY sessionStart DESC")
    List<FrameMetric> getByPackage(String packageName);

    @Query("SELECT * FROM frame_metrics WHERE packageName = :packageName AND sessionStart >= :since ORDER BY sessionStart DESC")
    List<FrameMetric> getByPackageSince(String packageName, long since);

    // =========================================================================
    // Jank Analysis Queries
    // =========================================================================

    @Query("SELECT AVG(jankPercentage) FROM frame_metrics WHERE sessionStart >= :since")
    float getAverageJankSince(long since);

    @Query("SELECT MAX(jankPercentage) FROM frame_metrics WHERE sessionStart >= :since")
    float getMaxJankSince(long since);

    @Query("SELECT AVG(jankPercentage) FROM frame_metrics WHERE packageName = :packageName AND sessionStart >= :since")
    float getAverageJankByPackage(String packageName, long since);

    @Query("SELECT AVG(jankPercentage) FROM frame_metrics WHERE packageName = :packageName AND sessionStart BETWEEN :start AND :end")
    float getAverageJankByPackageInRange(String packageName, long start, long end);

    @Query("SELECT * FROM frame_metrics WHERE jankPercentage > 5 AND sessionStart >= :since ORDER BY jankPercentage DESC")
    List<FrameMetric> getExcessiveJankSessions(long since);

    @Query("SELECT * FROM frame_metrics WHERE frozenFrames > 0 AND sessionStart >= :since ORDER BY frozenFrames DESC")
    List<FrameMetric> getFrozenFrameSessions(long since);

    // =========================================================================
    // Aggregation Queries
    // =========================================================================

    @Query("SELECT SUM(totalFrames) FROM frame_metrics WHERE sessionStart >= :since")
    int getTotalFramesSince(long since);

    @Query("SELECT SUM(slowFrames) FROM frame_metrics WHERE sessionStart >= :since")
    int getTotalSlowFramesSince(long since);

    @Query("SELECT SUM(frozenFrames) FROM frame_metrics WHERE sessionStart >= :since")
    int getTotalFrozenFramesSince(long since);

    @Query("SELECT SUM(totalFrames) FROM frame_metrics WHERE sessionStart BETWEEN :start AND :end")
    int getTotalFramesInRange(long start, long end);

    @Query("SELECT SUM(slowFrames) FROM frame_metrics WHERE sessionStart BETWEEN :start AND :end")
    int getTotalSlowFramesInRange(long start, long end);

    @Query("SELECT SUM(frozenFrames) FROM frame_metrics WHERE sessionStart BETWEEN :start AND :end")
    int getTotalFrozenFramesInRange(long start, long end);

    @Query("SELECT AVG(fps) FROM frame_metrics WHERE sessionStart >= :since")
    float getAverageFpsSince(long since);

    @Query("SELECT COUNT(*) FROM frame_metrics WHERE sessionStart >= :since")
    int getSessionCountSince(long since);

    @Query("SELECT COUNT(*) FROM frame_metrics WHERE sessionStart BETWEEN :start AND :end")
    int getSessionCountInRange(long start, long end);

    // =========================================================================
    // Distinct Packages
    // =========================================================================

    @Query("SELECT DISTINCT packageName FROM frame_metrics WHERE sessionStart >= :since")
    List<String> getDistinctPackages(long since);

    // =========================================================================
    // Cleanup Operations
    // =========================================================================

    @Query("DELETE FROM frame_metrics WHERE sessionStart < :before")
    int deleteOlderThan(long before);

    @Query("DELETE FROM frame_metrics")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM frame_metrics")
    int getCount();
}
