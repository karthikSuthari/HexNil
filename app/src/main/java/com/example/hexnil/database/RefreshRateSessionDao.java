package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.RefreshRateSession;

import java.util.List;

/**
 * Data Access Object for RefreshRateSession.
 * Provides CRUD operations and queries for refresh rate session data.
 */
@Dao
public interface RefreshRateSessionDao {

    // =========================================================================
    // Insert Operations
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(RefreshRateSession session);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RefreshRateSession> sessions);

    // =========================================================================
    // Update Operations
    // =========================================================================

    @Update
    void update(RefreshRateSession session);

    // =========================================================================
    // Query by ID
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_sessions WHERE id = :id")
    RefreshRateSession getById(long id);

    @Query("SELECT * FROM refresh_rate_sessions WHERE sessionId = :sessionId")
    RefreshRateSession getBySessionId(String sessionId);

    // =========================================================================
    // Query by Time Range
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_sessions WHERE startTime >= :startTime AND startTime <= :endTime ORDER BY startTime DESC")
    List<RefreshRateSession> getInTimeRange(long startTime, long endTime);

    @Query("SELECT * FROM refresh_rate_sessions WHERE startTime >= :since ORDER BY startTime DESC")
    List<RefreshRateSession> getSince(long since);

    @Query("SELECT * FROM refresh_rate_sessions WHERE startTime >= :since ORDER BY startTime DESC LIMIT :limit")
    List<RefreshRateSession> getRecentSessions(long since, int limit);

    // =========================================================================
    // Query by Package
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_sessions WHERE packageName = :packageName ORDER BY startTime DESC")
    List<RefreshRateSession> getByPackage(String packageName);

    @Query("SELECT * FROM refresh_rate_sessions WHERE packageName = :packageName AND startTime >= :since ORDER BY startTime DESC")
    List<RefreshRateSession> getByPackageSince(String packageName, long since);

    // =========================================================================
    // Query by Session Type
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_sessions WHERE sessionType = :sessionType ORDER BY startTime DESC")
    List<RefreshRateSession> getBySessionType(String sessionType);

    @Query("SELECT * FROM refresh_rate_sessions WHERE sessionType = 'HOME' AND startTime >= :since ORDER BY startTime DESC")
    List<RefreshRateSession> getHomeSessionsSince(long since);

    @Query("SELECT * FROM refresh_rate_sessions WHERE sessionType = 'APP' AND startTime >= :since ORDER BY startTime DESC")
    List<RefreshRateSession> getAppSessionsSince(long since);

    // =========================================================================
    // Query by Anomaly Status
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_sessions WHERE hasStrongAnomaly = 1 ORDER BY startTime DESC")
    List<RefreshRateSession> getSessionsWithAnomalies();

    @Query("SELECT * FROM refresh_rate_sessions WHERE hasStrongAnomaly = 1 AND startTime >= :since ORDER BY startTime DESC")
    List<RefreshRateSession> getSessionsWithAnomaliesSince(long since);

    @Query("SELECT COUNT(*) FROM refresh_rate_sessions WHERE hasStrongAnomaly = 1 AND startTime >= :since")
    int countAnomalousSessions(long since);

    // =========================================================================
    // Aggregation Queries
    // =========================================================================

    @Query("SELECT AVG(avgRefreshRateHz) FROM refresh_rate_sessions WHERE startTime >= :since")
    float getAverageRefreshRateSince(long since);

    @Query("SELECT SUM(timeAtHighHzMs) FROM refresh_rate_sessions WHERE startTime >= :since")
    long getTotalHighHzTimeSince(long since);

    @Query("SELECT SUM(timeAtLowHzMs) FROM refresh_rate_sessions WHERE startTime >= :since")
    long getTotalLowHzTimeSince(long since);

    @Query("SELECT SUM(estimatedBatteryCost) FROM refresh_rate_sessions WHERE startTime >= :since")
    float getTotalBatteryCostSince(long since);

    @Query("SELECT AVG(avgRefreshRateHz) FROM refresh_rate_sessions WHERE packageName = :packageName AND startTime >= :since")
    float getAverageRefreshRateForAppSince(String packageName, long since);

    // =========================================================================
    // For OTA Comparison
    // =========================================================================

    @Query("SELECT AVG(avgRefreshRateHz) FROM refresh_rate_sessions WHERE startTime >= :start AND startTime <= :end")
    float getAverageRefreshRateInWindow(long start, long end);

    @Query("SELECT SUM(timeAtHighHzMs) FROM refresh_rate_sessions WHERE startTime >= :start AND startTime <= :end")
    long getTotalHighHzTimeInWindow(long start, long end);

    @Query("SELECT SUM(estimatedBatteryCost) FROM refresh_rate_sessions WHERE startTime >= :start AND startTime <= :end")
    float getTotalBatteryCostInWindow(long start, long end);

    // =========================================================================
    // Count Queries
    // =========================================================================

    @Query("SELECT COUNT(*) FROM refresh_rate_sessions")
    int getCount();

    @Query("SELECT COUNT(*) FROM refresh_rate_sessions WHERE startTime >= :since")
    int getCountSince(long since);

    @Query("SELECT COUNT(*) FROM refresh_rate_sessions WHERE packageName = :packageName")
    int getCountByPackage(String packageName);

    // =========================================================================
    // Cleanup
    // =========================================================================

    @Query("DELETE FROM refresh_rate_sessions WHERE startTime < :before")
    int deleteOlderThan(long before);

    @Query("DELETE FROM refresh_rate_sessions")
    void deleteAll();

    // =========================================================================
    // Sync Support
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_sessions WHERE id > :lastId ORDER BY id ASC")
    List<RefreshRateSession> getAfterIdForSync(long lastId);

    @Query("SELECT MAX(id) FROM refresh_rate_sessions")
    Long getMaxId();
}
