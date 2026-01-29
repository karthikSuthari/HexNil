package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.NetworkCellularSnapshot;

import java.util.List;

/**
 * Data Access Object for NetworkCellularSnapshot.
 * 
 * Provides CRUD operations and specialized queries for cellular network data.
 * Follows existing patterns from BatterySnapshotDao with sync and quality
 * support.
 */
@Dao
public interface NetworkCellularSnapshotDao {

    // =========================================================================
    // BASIC CRUD
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(NetworkCellularSnapshot snapshot);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<NetworkCellularSnapshot> snapshots);

    @Update
    void update(NetworkCellularSnapshot snapshot);

    @Delete
    void delete(NetworkCellularSnapshot snapshot);

    @Query("DELETE FROM network_cellular_snapshots WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM network_cellular_snapshots")
    void deleteAll();

    // =========================================================================
    // BASIC QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_cellular_snapshots WHERE id = :id")
    NetworkCellularSnapshot getById(long id);

    @Query("SELECT * FROM network_cellular_snapshots ORDER BY timestamp DESC LIMIT 1")
    NetworkCellularSnapshot getLatest();

    @Query("SELECT * FROM network_cellular_snapshots ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkCellularSnapshot> getLatest(int limit);

    @Query("SELECT COUNT(*) FROM network_cellular_snapshots")
    int getCount();

    @Query("SELECT * FROM network_cellular_snapshots ORDER BY timestamp ASC")
    List<NetworkCellularSnapshot> getAll();

    // =========================================================================
    // TIME-BASED QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_cellular_snapshots WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<NetworkCellularSnapshot> getSnapshotsSince(long startTime);

    @Query("SELECT * FROM network_cellular_snapshots WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    List<NetworkCellularSnapshot> getSnapshotsInRange(long startTime, long endTime);

    @Query("SELECT * FROM network_cellular_snapshots WHERE timestamp >= :startTime ORDER BY timestamp ASC LIMIT :limit")
    List<NetworkCellularSnapshot> getSnapshotsSinceWithLimit(long startTime, int limit);

    @Query("SELECT * FROM network_cellular_snapshots WHERE timestamp <= :endTime ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkCellularSnapshot> getSnapshotsBefore(long endTime, int limit);

    // =========================================================================
    // QUALITY-FILTERED QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_cellular_snapshots WHERE timestamp >= :startTime AND validationStatus != 'invalid' AND qualityScore >= :minQuality ORDER BY timestamp ASC")
    List<NetworkCellularSnapshot> getValidSnapshotsSince(long startTime, int minQuality);

    @Query("SELECT * FROM network_cellular_snapshots WHERE timestamp >= :startTime AND timestamp <= :endTime AND validationStatus != 'invalid' ORDER BY timestamp ASC")
    List<NetworkCellularSnapshot> getUsableSnapshotsInRange(long startTime, long endTime);

    @Query("SELECT * FROM network_cellular_snapshots WHERE timestamp >= :startTime AND validationStatus = 'invalid' LIMIT :limit")
    List<NetworkCellularSnapshot> getInvalidSnapshots(long startTime, int limit);

    @Query("SELECT * FROM network_cellular_snapshots WHERE qualityScore >= 85 AND validationStatus = 'valid' ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkCellularSnapshot> getHighQualitySnapshots(int limit);

    // =========================================================================
    // RADIO TECHNOLOGY QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_cellular_snapshots WHERE radioTechnology = :technology ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkCellularSnapshot> getByRadioTechnology(String technology, int limit);

    @Query("SELECT * FROM network_cellular_snapshots WHERE radioTechnology LIKE 'NR%' ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkCellularSnapshot> get5GSnapshots(int limit);

    @Query("SELECT * FROM network_cellular_snapshots WHERE radioTechnology = 'LTE' ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkCellularSnapshot> getLTESnapshots(int limit);

    @Query("SELECT DISTINCT radioTechnology FROM network_cellular_snapshots WHERE radioTechnology IS NOT NULL")
    List<String> getDistinctRadioTechnologies();

    // =========================================================================
    // SIGNAL ANALYSIS QUERIES
    // =========================================================================

    @Query("SELECT AVG(rsrp) FROM network_cellular_snapshots WHERE timestamp >= :startTime AND rsrp IS NOT NULL AND validationStatus != 'invalid'")
    Double getAverageRsrp(long startTime);

    @Query("SELECT MIN(rsrp) FROM network_cellular_snapshots WHERE timestamp >= :startTime AND rsrp IS NOT NULL AND validationStatus != 'invalid'")
    Integer getMinRsrp(long startTime);

    @Query("SELECT MAX(rsrp) FROM network_cellular_snapshots WHERE timestamp >= :startTime AND rsrp IS NOT NULL AND validationStatus != 'invalid'")
    Integer getMaxRsrp(long startTime);

    @Query("SELECT AVG(signalStrengthDbm) FROM network_cellular_snapshots WHERE timestamp >= :startTime AND signalStrengthDbm IS NOT NULL")
    Double getAverageSignalStrength(long startTime);

    @Query("SELECT COUNT(*) FROM network_cellular_snapshots WHERE timestamp >= :startTime AND rsrp < -120")
    int getPoorSignalCount(long startTime);

    // =========================================================================
    // ROAMING QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_cellular_snapshots WHERE isRoaming = 1 ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkCellularSnapshot> getRoamingSnapshots(int limit);

    @Query("SELECT COUNT(*) FROM network_cellular_snapshots WHERE isRoaming = 1 AND timestamp >= :startTime")
    int getRoamingCount(long startTime);

    // =========================================================================
    // SYNC SUPPORT (Incremental)
    // =========================================================================

    @Query("SELECT * FROM network_cellular_snapshots WHERE id > :lastSyncedId ORDER BY id ASC LIMIT :limit")
    List<NetworkCellularSnapshot> getSinceId(long lastSyncedId, int limit);

    @Query("SELECT MAX(id) FROM network_cellular_snapshots")
    Long getMaxId();

    @Query("SELECT MIN(timestamp) FROM network_cellular_snapshots")
    Long getEarliestTimestamp();

    @Query("SELECT MAX(timestamp) FROM network_cellular_snapshots")
    Long getLatestTimestamp();

    // =========================================================================
    // SESSION QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_cellular_snapshots WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    List<NetworkCellularSnapshot> getBySessionId(long sessionId);

    @Query("SELECT DISTINCT sessionId FROM network_cellular_snapshots ORDER BY sessionId DESC LIMIT :limit")
    List<Long> getRecentSessionIds(int limit);

    // =========================================================================
    // FROZEN SIGNAL DETECTION
    // =========================================================================

    @Query("SELECT * FROM network_cellular_snapshots WHERE signalDataChanged = 0 AND timestamp >= :startTime ORDER BY timestamp ASC")
    List<NetworkCellularSnapshot> getFrozenSignalSnapshots(long startTime);

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @Query("DELETE FROM network_cellular_snapshots WHERE timestamp < :olderThan")
    int deleteOlderThan(long olderThan);

    @Query("DELETE FROM network_cellular_snapshots WHERE validationStatus = 'invalid' AND timestamp < :olderThan")
    int deleteInvalidOlderThan(long olderThan);
}
