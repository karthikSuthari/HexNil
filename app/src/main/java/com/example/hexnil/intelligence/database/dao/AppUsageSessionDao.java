package com.example.hexnil.intelligence.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.intelligence.database.entities.AppUsageSession;

import java.util.List;

/**
 * DAO for App Usage Session operations.
 * Part of the isolated App Intelligence module.
 */
@Dao
public interface AppUsageSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AppUsageSession session);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AppUsageSession> sessions);

    @Update
    void update(AppUsageSession session);

    @Delete
    void delete(AppUsageSession session);

    // =========================================================================
    // LOOKUP QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_usage_sessions WHERE session_id = :sessionId LIMIT 1")
    AppUsageSession getBySessionId(String sessionId);

    @Query("SELECT * FROM ai_app_usage_sessions WHERE id = :id")
    AppUsageSession getById(long id);

    @Query("SELECT * FROM ai_app_usage_sessions ORDER BY session_start DESC")
    List<AppUsageSession> getAll();

    @Query("SELECT * FROM ai_app_usage_sessions WHERE session_end IS NOT NULL ORDER BY session_start DESC")
    List<AppUsageSession> getAllCompleted();

    // =========================================================================
    // PER-APP QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_usage_sessions WHERE package_name = :packageName ORDER BY session_start DESC")
    List<AppUsageSession> getByPackageName(String packageName);

    @Query("SELECT * FROM ai_app_usage_sessions WHERE package_name = :packageName ORDER BY session_start DESC LIMIT 1")
    AppUsageSession getLatestByPackage(String packageName);

    @Query("SELECT * FROM ai_app_usage_sessions WHERE package_name = :packageName AND session_end IS NULL LIMIT 1")
    AppUsageSession getOngoingSession(String packageName);

    @Query("SELECT * FROM ai_app_usage_sessions WHERE package_name = :packageName AND session_start >= :startTime AND session_start <= :endTime ORDER BY session_start DESC")
    List<AppUsageSession> getByPackageInRange(String packageName, long startTime, long endTime);

    // =========================================================================
    // TIME-BASED QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_usage_sessions WHERE session_start >= :startTime ORDER BY session_start DESC")
    List<AppUsageSession> getSessionsSince(long startTime);

    @Query("SELECT * FROM ai_app_usage_sessions WHERE session_start >= :startTime AND session_start <= :endTime ORDER BY session_start DESC")
    List<AppUsageSession> getSessionsInRange(long startTime, long endTime);

    @Query("SELECT * FROM ai_app_usage_sessions WHERE session_end IS NULL")
    List<AppUsageSession> getOngoingSessions();

    // =========================================================================
    // CONTEXT-BASED QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_usage_sessions WHERE charging_state = :chargingState AND session_start >= :since ORDER BY session_start DESC")
    List<AppUsageSession> getSessionsByChargingState(String chargingState, long since);

    @Query("SELECT * FROM ai_app_usage_sessions WHERE network_type = :networkType AND session_start >= :since ORDER BY session_start DESC")
    List<AppUsageSession> getSessionsByNetworkType(String networkType, long since);

    @Query("SELECT * FROM ai_app_usage_sessions WHERE (battery_level_start - battery_level_end) > :drainThreshold AND session_start >= :since ORDER BY (battery_level_start - battery_level_end) DESC")
    List<AppUsageSession> getHighDrainSessions(int drainThreshold, long since);

    // =========================================================================
    // AGGREGATE QUERIES
    // =========================================================================

    @Query("SELECT SUM(foreground_time_ms) FROM ai_app_usage_sessions WHERE package_name = :packageName AND session_start >= :since")
    long getTotalForegroundTime(String packageName, long since);

    @Query("SELECT SUM(background_time_ms) FROM ai_app_usage_sessions WHERE package_name = :packageName AND session_start >= :since")
    long getTotalBackgroundTime(String packageName, long since);

    @Query("SELECT SUM(duration_ms) FROM ai_app_usage_sessions WHERE package_name = :packageName AND session_start >= :since")
    long getTotalSessionDuration(String packageName, long since);

    @Query("SELECT COUNT(*) FROM ai_app_usage_sessions WHERE package_name = :packageName AND session_start >= :since")
    int getSessionCount(String packageName, long since);

    @Query("SELECT AVG(battery_level_start - battery_level_end) FROM ai_app_usage_sessions WHERE package_name = :packageName AND battery_level_start IS NOT NULL AND battery_level_end IS NOT NULL AND session_start >= :since")
    float getAverageBatteryDrain(String packageName, long since);

    // =========================================================================
    // OVERNIGHT ANALYSIS
    // =========================================================================

    @Query("SELECT * FROM ai_app_usage_sessions WHERE session_start >= :nightStart AND session_end <= :nightEnd AND screen_on_time_ms < screen_off_time_ms ORDER BY (battery_level_start - battery_level_end) DESC")
    List<AppUsageSession> getOvernightSessions(long nightStart, long nightEnd);

    // =========================================================================
    // MAINTENANCE
    // =========================================================================

    @Query("DELETE FROM ai_app_usage_sessions WHERE session_start < :before")
    void deleteOlderThan(long before);

    @Query("UPDATE ai_app_usage_sessions SET session_end = :endTime, duration_ms = :endTime - session_start, battery_level_end = :batteryLevel WHERE session_id = :sessionId")
    void endSession(String sessionId, long endTime, int batteryLevel);
}
