package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Transaction;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.DrainEvent;

import java.util.List;

/**
 * Validation DAO - Specialized Queries for Data Validation
 * 
 * Provides queries specifically designed for:
 * - Integrity checking (frozen data, duplicates, gaps)
 * - Cleanup operations (retention enforcement)
 * - Health monitoring (counts, sizes, stats)
 * - Anomaly detection (outliers, impossible values)
 * 
 * IMPORTANT: This DAO focuses on validation and cleanup operations.
 * For normal data access, use entity-specific DAOs.
 * 
 * OEM Integration: Same validation queries used in production devices.
 */
@Dao
public interface ValidationDao {
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // FROZEN DATA DETECTION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Find sequences of identical snapshots (frozen sensor detection).
     * Returns snapshots where all key values match the previous snapshot.
     */
    @Query("SELECT s1.* FROM battery_snapshots s1 " +
           "INNER JOIN battery_snapshots s2 ON s1.timestamp > s2.timestamp " +
           "WHERE s1.level = s2.level " +
           "AND s1.voltage = s2.voltage " +
           "AND s1.temperature = s2.temperature " +
           "AND s1.status = s2.status " +
           "AND s1.isCharging = s2.isCharging " +
           "AND s1.timestamp - s2.timestamp < :maxGapMs " +
           "AND s1.timestamp >= :startTime " +
           "ORDER BY s1.timestamp ASC")
    List<BatterySnapshot> findFrozenSnapshots(long startTime, long maxGapMs);
    
    /**
     * Count consecutive identical snapshots.
     */
    @Query("SELECT COUNT(*) FROM battery_snapshots " +
           "WHERE level = :level AND voltage = :voltage AND temperature = :temp " +
           "AND status = :status AND timestamp >= :startTime AND timestamp <= :endTime")
    int countIdenticalSnapshots(int level, int voltage, int temp, int status, 
                                 long startTime, long endTime);
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // INCOMPLETE SESSION DETECTION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Find incomplete charging sessions (started but never ended).
     */
    @Query("SELECT * FROM charging_sessions " +
           "WHERE (endTime <= 0 OR endTime <= startTime) " +
           "ORDER BY startTime DESC")
    List<ChargingSession> findIncompleteSessions();
    
    /**
     * Find stale incomplete sessions (older than threshold).
     */
    @Query("SELECT * FROM charging_sessions " +
           "WHERE (endTime <= 0 OR endTime <= startTime) " +
           "AND startTime < :cutoffTime " +
           "ORDER BY startTime ASC")
    List<ChargingSession> findStaleSessions(long cutoffTime);
    
    /**
     * Count incomplete sessions.
     */
    @Query("SELECT COUNT(*) FROM charging_sessions " +
           "WHERE endTime <= 0 OR endTime <= startTime")
    int countIncompleteSessions();
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DATA GAP DETECTION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Find large time gaps in snapshot data.
     * Returns the snapshot AFTER each gap.
     */
    @Query("SELECT s1.* FROM battery_snapshots s1 " +
           "INNER JOIN battery_snapshots s2 ON s2.id = (" +
           "  SELECT MAX(id) FROM battery_snapshots WHERE timestamp < s1.timestamp" +
           ") " +
           "WHERE s1.timestamp - s2.timestamp > :gapThresholdMs " +
           "AND s1.timestamp >= :startTime " +
           "ORDER BY s1.timestamp ASC")
    List<BatterySnapshot> findGaps(long startTime, long gapThresholdMs);
    
    /**
     * Get timestamp of oldest snapshot.
     */
    @Query("SELECT MIN(timestamp) FROM battery_snapshots")
    Long getOldestTimestamp();
    
    /**
     * Get timestamp of newest snapshot.
     */
    @Query("SELECT MAX(timestamp) FROM battery_snapshots")
    Long getNewestTimestamp();
    
    /**
     * Calculate data coverage percentage for a time range.
     */
    @Query("SELECT COUNT(*) FROM battery_snapshots " +
           "WHERE timestamp >= :startTime AND timestamp <= :endTime")
    int getSnapshotCountInRange(long startTime, long endTime);
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DUPLICATE DETECTION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Find snapshots with duplicate timestamps.
     */
    @Query("SELECT * FROM battery_snapshots bs1 " +
           "WHERE EXISTS (" +
           "  SELECT 1 FROM battery_snapshots bs2 " +
           "  WHERE bs1.timestamp = bs2.timestamp AND bs1.id != bs2.id" +
           ") ORDER BY timestamp ASC")
    List<BatterySnapshot> findDuplicateTimestamps();
    
    /**
     * Count duplicate timestamps.
     */
    @Query("SELECT COUNT(DISTINCT timestamp) FROM battery_snapshots " +
           "WHERE timestamp IN (" +
           "  SELECT timestamp FROM battery_snapshots GROUP BY timestamp HAVING COUNT(*) > 1" +
           ")")
    int countDuplicateTimestampGroups();
    
    /**
     * Find snapshots with duplicate hash fingerprints (should be 0).
     * Uses the fingerprint pattern: level:voltage:temperature combination.
     */
    @Query("SELECT * FROM battery_snapshots bs1 " +
           "WHERE EXISTS (" +
           "  SELECT 1 FROM battery_snapshots bs2 " +
           "  WHERE bs1.level = bs2.level " +
           "  AND bs1.voltage = bs2.voltage " +
           "  AND bs1.temperature = bs2.temperature " +
           "  AND ABS(bs1.timestamp - bs2.timestamp) < 5000 " +  // Within 5 seconds
           "  AND bs1.id != bs2.id" +
           ") ORDER BY timestamp ASC")
    List<BatterySnapshot> findPotentialDuplicateSnapshots();
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // ANOMALY DETECTION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Find snapshots with invalid battery levels.
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE level < 0 OR level > 100 " +
           "ORDER BY timestamp DESC LIMIT :limit")
    List<BatterySnapshot> findInvalidLevelSnapshots(int limit);
    
    /**
     * Find snapshots with extreme temperatures.
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE temperature < 0 OR temperature > 800 " +  // < 0°C or > 80°C
           "ORDER BY timestamp DESC LIMIT :limit")
    List<BatterySnapshot> findExtremeTemperatureSnapshots(int limit);
    
    /**
     * Find snapshots with future timestamps.
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE timestamp > :currentTime " +
           "ORDER BY timestamp DESC")
    List<BatterySnapshot> findFutureTimestamps(long currentTime);
    
    /**
     * Find snapshots with impossible level jumps (>5% change in 1 minute).
     */
    @Query("SELECT s1.* FROM battery_snapshots s1 " +
           "INNER JOIN battery_snapshots s2 ON s2.id = (" +
           "  SELECT MAX(id) FROM battery_snapshots WHERE timestamp < s1.timestamp" +
           ") " +
           "WHERE ABS(s1.level - s2.level) > 5 " +
           "AND s1.timestamp - s2.timestamp < 60000 " +  // Within 1 minute
           "AND s1.timestamp >= :startTime " +
           "ORDER BY s1.timestamp DESC LIMIT :limit")
    List<BatterySnapshot> findImpossibleLevelJumps(long startTime, int limit);
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // TABLE STATISTICS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Get battery snapshot count.
     */
    @Query("SELECT COUNT(*) FROM battery_snapshots")
    int getSnapshotCount();
    
    /**
     * Get charging session count.
     */
    @Query("SELECT COUNT(*) FROM charging_sessions")
    int getSessionCount();
    
    /**
     * Get drain event count.
     */
    @Query("SELECT COUNT(*) FROM drain_events")
    int getDrainEventCount();
    
    /**
     * Get thermal event count.
     */
    @Query("SELECT COUNT(*) FROM thermal_events")
    int getThermalEventCount();
    
    /**
     * Get battery health record count.
     */
    @Query("SELECT COUNT(*) FROM battery_health")
    int getBatteryHealthCount();
    
    /**
     * Get AI analysis result count.
     */
    @Query("SELECT COUNT(*) FROM ai_analysis_results")
    int getAIAnalysisCount();
    
    /**
     * Get calibration data record count.
     */
    @Query("SELECT COUNT(*) FROM calibration_data")
    int getCalibrationDataCount();
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // CLEANUP OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Delete snapshots older than cutoff.
     */
    @Query("DELETE FROM battery_snapshots WHERE timestamp < :cutoffTime")
    int deleteSnapshotsOlderThan(long cutoffTime);
    
    /**
     * Delete charging sessions older than cutoff.
     */
    @Query("DELETE FROM charging_sessions WHERE startTime < :cutoffTime")
    int deleteSessionsOlderThan(long cutoffTime);
    
    /**
     * Delete drain events older than cutoff.
     */
    @Query("DELETE FROM drain_events WHERE startTime < :cutoffTime")
    int deleteDrainEventsOlderThan(long cutoffTime);
    
    /**
     * Delete thermal events older than cutoff.
     */
    @Query("DELETE FROM thermal_events WHERE timestamp < :cutoffTime")
    int deleteThermalEventsOlderThan(long cutoffTime);
    
    /**
     * Delete battery health records older than cutoff.
     */
    @Query("DELETE FROM battery_health WHERE timestamp < :cutoffTime")
    int deleteHealthRecordsOlderThan(long cutoffTime);
    
    /**
     * Delete AI analysis results older than cutoff.
     */
    @Query("DELETE FROM ai_analysis_results WHERE timestamp < :cutoffTime")
    int deleteAIResultsOlderThan(long cutoffTime);
    
    /**
     * Delete calibration data older than cutoff.
     */
    @Query("DELETE FROM calibration_data WHERE timestamp < :cutoffTime")
    int deleteCalibrationDataOlderThan(long cutoffTime);
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // COUNT-BASED CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Delete oldest snapshots to maintain count limit.
     */
    @Query("DELETE FROM battery_snapshots WHERE id IN (" +
           "  SELECT id FROM battery_snapshots " +
           "  ORDER BY timestamp ASC LIMIT :deleteCount" +
           ")")
    int deleteOldestSnapshots(int deleteCount);
    
    /**
     * Delete oldest sessions to maintain count limit.
     */
    @Query("DELETE FROM charging_sessions WHERE id IN (" +
           "  SELECT id FROM charging_sessions " +
           "  ORDER BY startTime ASC LIMIT :deleteCount" +
           ")")
    int deleteOldestSessions(int deleteCount);
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // DUPLICATE CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Delete duplicate timestamp snapshots (keep newest id).
     */
    @Query("DELETE FROM battery_snapshots WHERE id NOT IN (" +
           "  SELECT MAX(id) FROM battery_snapshots GROUP BY timestamp" +
           ")")
    int deleteDuplicateTimestampSnapshots();
    
    /**
     * Delete snapshots with future timestamps.
     */
    @Query("DELETE FROM battery_snapshots WHERE timestamp > :currentTime + 300000")  // 5 min tolerance
    int deleteFutureSnapshots(long currentTime);
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // STALE SESSION CLEANUP
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Auto-close stale sessions by setting endTime to last known snapshot time.
     */
    @Query("UPDATE charging_sessions SET " +
           "endTime = (SELECT MAX(timestamp) FROM battery_snapshots WHERE isCharging = 1 AND timestamp >= charging_sessions.startTime AND timestamp < :cutoffTime), " +
           "endLevel = (SELECT level FROM battery_snapshots WHERE isCharging = 1 AND timestamp = (SELECT MAX(timestamp) FROM battery_snapshots WHERE isCharging = 1 AND timestamp >= charging_sessions.startTime AND timestamp < :cutoffTime)) " +
           "WHERE (endTime <= 0 OR endTime <= startTime) " +
           "AND startTime < :cutoffTime")
    int autoCloseStaleSessionsBeforeUpdate(long cutoffTime);
    
    /**
     * Delete sessions that couldn't be auto-closed (no snapshots found).
     */
    @Query("DELETE FROM charging_sessions " +
           "WHERE (endTime <= 0 OR endTime <= startTime) " +
           "AND startTime < :cutoffTime")
    int deleteUnrecoverableStaleSessions(long cutoffTime);
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // INTEGRITY VERIFICATION
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Quick integrity check - returns 1 if integrity_check passes.
     */
    @Query("SELECT COUNT(*) FROM battery_snapshots WHERE 1=1")
    int verifySnapshotTableReadable();
    
    /**
     * Verify foreign key integrity (if applicable).
     */
    @Query("SELECT COUNT(*) FROM charging_sessions WHERE 1=1")
    int verifySessionTableReadable();
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // RAW QUERY SUPPORT (For dynamic cleanup)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Execute raw query for advanced cleanup operations.
     * USE WITH CAUTION - for admin/maintenance only.
     */
    @RawQuery
    int executeRawCleanup(SupportSQLiteQuery query);
    
    // ═══════════════════════════════════════════════════════════════════════════════
    // AGGREGATION QUERIES (For retention aggregation before delete)
    // ═══════════════════════════════════════════════════════════════════════════════
    
    /**
     * Get hourly averages for aggregation before cleanup.
     */
    @Query("SELECT " +
           "(timestamp / 3600000 * 3600000) AS hourTimestamp, " +
           "AVG(level) AS avgLevel, " +
           "AVG(voltage) AS avgVoltage, " +
           "AVG(temperature) AS avgTemperature, " +
           "MAX(temperature) AS maxTemperature, " +
           "MIN(level) AS minLevel, " +
           "MAX(level) AS maxLevel, " +
           "COUNT(*) AS sampleCount " +
           "FROM battery_snapshots " +
           "WHERE timestamp >= :startTime AND timestamp < :endTime " +
           "GROUP BY timestamp / 3600000")
    List<HourlyAggregate> getHourlyAggregates(long startTime, long endTime);
    
    /**
     * Hourly aggregate result class.
     */
    class HourlyAggregate {
        public long hourTimestamp;
        public float avgLevel;
        public float avgVoltage;
        public float avgTemperature;
        public float maxTemperature;
        public int minLevel;
        public int maxLevel;
        public int sampleCount;
    }
    
    /**
     * Get daily summary for long-term storage.
     */
    @Query("SELECT " +
           "(timestamp / 86400000 * 86400000) AS dayTimestamp, " +
           "MIN(level) AS minLevel, " +
           "MAX(level) AS maxLevel, " +
           "AVG(level) AS avgLevel, " +
           "MAX(temperature) AS maxTemperature, " +
           "AVG(temperature) AS avgTemperature, " +
           "SUM(CASE WHEN isCharging = 1 THEN 1 ELSE 0 END) AS chargingSampleCount, " +
           "COUNT(*) AS totalSampleCount " +
           "FROM battery_snapshots " +
           "WHERE timestamp >= :startTime AND timestamp < :endTime " +
           "GROUP BY timestamp / 86400000")
    List<DailySummary> getDailySummaries(long startTime, long endTime);
    
    /**
     * Daily summary result class.
     */
    class DailySummary {
        public long dayTimestamp;
        public int minLevel;
        public int maxLevel;
        public float avgLevel;
        public float maxTemperature;
        public float avgTemperature;
        public int chargingSampleCount;
        public int totalSampleCount;
    }
}
