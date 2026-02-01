package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.BatterySnapshot;

import java.util.List;

/**
 * OEM-Grade Battery Snapshot Data Access Object
 * 
 * Provides database operations for battery snapshots with:
 * - Quality-aware queries (filter by validation status)
 * - Anomaly detection queries
 * - Duplicate detection
 * - Time-series analysis support
 * - Data retention management
 */
@Dao
public interface BatterySnapshotDao {
    
    // =========================================================================
    // BASIC CRUD OPERATIONS
    // =========================================================================
    
    @Insert
    long insert(BatterySnapshot snapshot);
    
    @Insert
    void insertAll(List<BatterySnapshot> snapshots);
    
    @Update
    void update(BatterySnapshot snapshot);
    
    @Delete
    void delete(BatterySnapshot snapshot);
    
    // =========================================================================
    // BASIC QUERIES
    // =========================================================================
    
    @Query("SELECT * FROM battery_snapshots ORDER BY timestamp DESC LIMIT 1")
    BatterySnapshot getLatest();
    
    @Query("SELECT * FROM battery_snapshots WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<BatterySnapshot> getSnapshotsSince(long startTime);
    
    @Query("SELECT * FROM battery_snapshots WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    List<BatterySnapshot> getSnapshotsInRange(long startTime, long endTime);
    
    @Query("SELECT * FROM battery_snapshots ORDER BY timestamp DESC LIMIT :limit")
    List<BatterySnapshot> getRecentSnapshots(int limit);
    
    @Query("SELECT * FROM battery_snapshots WHERE isCharging = 1 AND timestamp >= :startTime")
    List<BatterySnapshot> getChargingSnapshots(long startTime);
    
    @Query("SELECT * FROM battery_snapshots WHERE isCharging = 0 AND timestamp >= :startTime")
    List<BatterySnapshot> getDischargingSnapshots(long startTime);
    
    @Query("SELECT * FROM battery_snapshots WHERE temperature > :threshold AND timestamp >= :startTime")
    List<BatterySnapshot> getHighTempSnapshots(int threshold, long startTime);
    
    // =========================================================================
    // QUALITY-AWARE QUERIES (OEM-Grade)
    // =========================================================================
    
    /**
     * Get only valid, high-quality snapshots for reliable analysis.
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE validationStatus = 'valid' AND qualityScore >= :minQuality " +
           "AND timestamp >= :startTime ORDER BY timestamp ASC")
    List<BatterySnapshot> getValidSnapshotsSince(long startTime, int minQuality);
    
    /**
     * Get snapshots that passed validation for a time range.
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE validationStatus IN ('valid', 'warning') " +
           "AND timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY timestamp ASC")
    List<BatterySnapshot> getUsableSnapshotsInRange(long startTime, long endTime);
    
    /**
     * Get snapshots with specific sampling context.
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE samplingContext = :context AND timestamp >= :startTime " +
           "ORDER BY timestamp ASC")
    List<BatterySnapshot> getSnapshotsByContext(String context, long startTime);
    
    /**
     * Get snapshots from a specific session.
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE sessionId = :sessionId ORDER BY sequenceNumber ASC")
    List<BatterySnapshot> getSnapshotsBySession(long sessionId);
    
    // =========================================================================
    // ANOMALY & DUPLICATE DETECTION
    // =========================================================================
    
    /**
     * Find snapshots with frozen/unresponsive sensors.
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE sensorDataChanged = 0 AND timestamp >= :startTime " +
           "ORDER BY timestamp DESC")
    List<BatterySnapshot> getFrozenSensorSnapshots(long startTime);
    
    /**
     * Find low-quality snapshots for diagnostics.
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE qualityScore < :threshold AND timestamp >= :startTime " +
           "ORDER BY qualityScore ASC LIMIT :limit")
    List<BatterySnapshot> getLowQualitySnapshots(int threshold, long startTime, int limit);
    
    /**
     * Find invalid snapshots for debugging.
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE validationStatus = 'invalid' AND timestamp >= :startTime " +
           "ORDER BY timestamp DESC LIMIT :limit")
    List<BatterySnapshot> getInvalidSnapshots(long startTime, int limit);
    
    /**
     * Count snapshots with duplicate timestamps (should always be 0).
     */
    @Query("SELECT COUNT(*) FROM battery_snapshots bs1 " +
           "WHERE EXISTS (SELECT 1 FROM battery_snapshots bs2 " +
           "WHERE bs1.timestamp = bs2.timestamp AND bs1.id != bs2.id)")
    int countDuplicateTimestamps();
    
    /**
     * Find potential duplicate snapshots (same sensor fingerprint within time window).
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE timestamp >= :startTime " +
           "AND level = :level AND voltage = :voltage AND temperature = :temp " +
           "ORDER BY timestamp ASC")
    List<BatterySnapshot> findPotentialDuplicates(long startTime, int level, int voltage, int temp);
    
    /**
     * Get snapshot sequence gaps (missing sequence numbers).
     */
    @Query("SELECT MAX(sequenceNumber) - MIN(sequenceNumber) + 1 - COUNT(*) AS gaps " +
           "FROM battery_snapshots WHERE sessionId = :sessionId")
    int getSequenceGapCount(long sessionId);
    
    // =========================================================================
    // AGGREGATION & STATISTICS
    // =========================================================================
    
    @Query("SELECT AVG(level) FROM battery_snapshots WHERE timestamp >= :startTime")
    float getAverageLevel(long startTime);
    
    @Query("SELECT AVG(temperature) FROM battery_snapshots WHERE timestamp >= :startTime")
    float getAverageTemperature(long startTime);
    
    @Query("SELECT MAX(temperature) FROM battery_snapshots WHERE timestamp >= :startTime")
    float getMaxTemperature(long startTime);
    
    @Query("SELECT MIN(temperature) FROM battery_snapshots WHERE timestamp >= :startTime")
    float getMinTemperature(long startTime);
    
    @Query("SELECT AVG(qualityScore) FROM battery_snapshots " +
           "WHERE timestamp >= :startTime AND qualityScore >= 0")
    float getAverageQualityScore(long startTime);
    
    @Query("SELECT COUNT(*) FROM battery_snapshots")
    int getTotalCount();
    
    @Query("SELECT COUNT(*) FROM battery_snapshots")
    int getCount();
    
    @Query("SELECT COUNT(*) FROM battery_snapshots WHERE timestamp >= :startTime")
    int getCountSince(long startTime);
    
    @Query("SELECT COUNT(*) FROM battery_snapshots WHERE timestamp BETWEEN :startTime AND :endTime")
    int getCountInRange(long startTime, long endTime);
    
    @Query("SELECT COUNT(*) FROM battery_snapshots " +
           "WHERE validationStatus = 'valid' AND timestamp >= :startTime")
    int getValidCountSince(long startTime);
    
    @Query("SELECT COUNT(*) FROM battery_snapshots " +
           "WHERE validationStatus = 'invalid' AND timestamp >= :startTime")
    int getInvalidCountSince(long startTime);
    
    // =========================================================================
    // SCREEN STATE QUERIES
    // =========================================================================
    
    @Query("SELECT * FROM battery_snapshots WHERE screenOn = 1 AND timestamp >= :startTime")
    List<BatterySnapshot> getScreenOnSnapshots(long startTime);
    
    @Query("SELECT * FROM battery_snapshots WHERE screenOn = 0 AND timestamp >= :startTime")
    List<BatterySnapshot> getScreenOffSnapshots(long startTime);
    
    // =========================================================================
    // DATA RETENTION & CLEANUP
    // =========================================================================
    
    @Query("DELETE FROM battery_snapshots WHERE timestamp < :beforeTime")
    void deleteOlderThan(long beforeTime);
    
    @Query("DELETE FROM battery_snapshots WHERE validationStatus = 'invalid' AND timestamp < :beforeTime")
    void deleteInvalidOlderThan(long beforeTime);
    
    /**
     * Keep only the last N snapshots.
     */
    @Query("DELETE FROM battery_snapshots WHERE id NOT IN " +
           "(SELECT id FROM battery_snapshots ORDER BY timestamp DESC LIMIT :keepCount)")
    void pruneToCount(int keepCount);
    
    /**
     * Delete low-quality snapshots older than threshold to save space.
     */
    @Query("DELETE FROM battery_snapshots " +
           "WHERE qualityScore < :qualityThreshold AND timestamp < :beforeTime")
    void deleteLowQualityOlderThan(int qualityThreshold, long beforeTime);
    
    // =========================================================================
    // TIME-SERIES ANALYSIS SUPPORT
    // =========================================================================
    
    /**
     * Get level changes over time for drain/charge analysis.
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE timestamp >= :startTime AND validationStatus != 'invalid' " +
           "ORDER BY timestamp ASC")
    List<BatterySnapshot> getTimeSeriesForAnalysis(long startTime);
    
    /**
     * Get distinct sessions within a time range.
     */
    @Query("SELECT DISTINCT sessionId FROM battery_snapshots " +
           "WHERE timestamp >= :startTime ORDER BY sessionId ASC")
    List<Long> getSessionIds(long startTime);
    
    /**
     * Get first and last snapshot for a session (for quick session summary).
     */
    @Query("SELECT * FROM battery_snapshots " +
           "WHERE sessionId = :sessionId " +
           "AND (sequenceNumber = (SELECT MIN(sequenceNumber) FROM battery_snapshots WHERE sessionId = :sessionId) " +
           "OR sequenceNumber = (SELECT MAX(sequenceNumber) FROM battery_snapshots WHERE sessionId = :sessionId))")
    List<BatterySnapshot> getSessionBoundaries(long sessionId);
    
    /**
     * Get sampling rate statistics for a time range.
     */
    @Query("SELECT AVG(timeSincePreviousMs) FROM battery_snapshots " +
           "WHERE timestamp >= :startTime AND timeSincePreviousMs > 0")
    float getAverageSamplingInterval(long startTime);
}
