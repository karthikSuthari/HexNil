package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.NetworkBluetoothSnapshot;

import java.util.List;

/**
 * Data Access Object for NetworkBluetoothSnapshot.
 * 
 * Provides CRUD operations and specialized queries for Bluetooth data.
 * Includes profile-specific and audio codec analysis support.
 */
@Dao
public interface NetworkBluetoothSnapshotDao {

    // =========================================================================
    // BASIC CRUD
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(NetworkBluetoothSnapshot snapshot);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<NetworkBluetoothSnapshot> snapshots);

    @Update
    void update(NetworkBluetoothSnapshot snapshot);

    @Delete
    void delete(NetworkBluetoothSnapshot snapshot);

    @Query("DELETE FROM network_bluetooth_snapshots WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM network_bluetooth_snapshots")
    void deleteAll();

    // =========================================================================
    // BASIC QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE id = :id")
    NetworkBluetoothSnapshot getById(long id);

    @Query("SELECT * FROM network_bluetooth_snapshots ORDER BY timestamp DESC LIMIT 1")
    NetworkBluetoothSnapshot getLatest();

    @Query("SELECT * FROM network_bluetooth_snapshots ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getLatest(int limit);

    @Query("SELECT COUNT(*) FROM network_bluetooth_snapshots")
    int getCount();

    @Query("SELECT * FROM network_bluetooth_snapshots ORDER BY timestamp ASC")
    List<NetworkBluetoothSnapshot> getAll();

    // =========================================================================
    // TIME-BASED QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<NetworkBluetoothSnapshot> getSnapshotsSince(long startTime);

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    List<NetworkBluetoothSnapshot> getSnapshotsInRange(long startTime, long endTime);

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE timestamp >= :startTime ORDER BY timestamp ASC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getSnapshotsSinceWithLimit(long startTime, int limit);

    // =========================================================================
    // QUALITY-FILTERED QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE timestamp >= :startTime AND validationStatus != 'invalid' AND qualityScore >= :minQuality ORDER BY timestamp ASC")
    List<NetworkBluetoothSnapshot> getValidSnapshotsSince(long startTime, int minQuality);

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE qualityScore >= 85 AND validationStatus = 'valid' ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getHighQualitySnapshots(int limit);

    // =========================================================================
    // ADAPTER STATE QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE adapterState = 12 ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getAdapterOnSnapshots(int limit);

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE adapterState = 10 ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getAdapterOffSnapshots(int limit);

    @Query("SELECT COUNT(*) FROM network_bluetooth_snapshots WHERE adapterState = 12 AND timestamp >= :startTime")
    int getAdapterOnCount(long startTime);

    // =========================================================================
    // PROFILE-SPECIFIC QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE profileType = :profileType ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getByProfileType(String profileType, int limit);

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE profileType = 'A2DP' ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getA2DPSnapshots(int limit);

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE profileType = 'HFP' ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getHFPSnapshots(int limit);

    @Query("SELECT DISTINCT profileType FROM network_bluetooth_snapshots WHERE profileType IS NOT NULL")
    List<String> getDistinctProfileTypes();

    // =========================================================================
    // CONNECTION STATE QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE connectionState = 2 ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getConnectedSnapshots(int limit);

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE connectionState = 0 ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getDisconnectedSnapshots(int limit);

    @Query("SELECT COUNT(*) FROM network_bluetooth_snapshots WHERE connectionState = 2 AND timestamp >= :startTime")
    int getConnectedCount(long startTime);

    // =========================================================================
    // CODEC ANALYSIS (A2DP)
    // =========================================================================

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE codecType = :codecType ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getByCodecType(String codecType, int limit);

    @Query("SELECT DISTINCT codecType FROM network_bluetooth_snapshots WHERE codecType IS NOT NULL")
    List<String> getDistinctCodecTypes();

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE codecType IN ('aptX', 'aptX_HD', 'LDAC', 'LC3') ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getHighQualityCodecSnapshots(int limit);

    @Query("SELECT COUNT(*) FROM network_bluetooth_snapshots WHERE codecType = 'SBC' AND timestamp >= :startTime")
    int getSBCCodecCount(long startTime);

    // =========================================================================
    // DEVICE ANALYSIS
    // =========================================================================

    @Query("SELECT AVG(connectedDeviceCount) FROM network_bluetooth_snapshots WHERE timestamp >= :startTime AND connectedDeviceCount IS NOT NULL")
    Double getAverageConnectedDevices(long startTime);

    @Query("SELECT MAX(pairedDeviceCount) FROM network_bluetooth_snapshots WHERE timestamp >= :startTime")
    Integer getMaxPairedDevices(long startTime);

    @Query("SELECT DISTINCT remoteAddressHash FROM network_bluetooth_snapshots WHERE remoteAddressHash IS NOT NULL")
    List<String> getDistinctRemoteDeviceHashes();

    // =========================================================================
    // STABILITY METRICS
    // =========================================================================

    @Query("SELECT SUM(reconnectCount) FROM network_bluetooth_snapshots WHERE timestamp >= :startTime")
    Integer getTotalReconnects(long startTime);

    @Query("SELECT SUM(packetErrorCount) FROM network_bluetooth_snapshots WHERE timestamp >= :startTime")
    Integer getTotalPacketErrors(long startTime);

    @Query("SELECT AVG(connectionDurationMs) FROM network_bluetooth_snapshots WHERE connectionDurationMs IS NOT NULL AND timestamp >= :startTime")
    Double getAverageConnectionDuration(long startTime);

    // =========================================================================
    // SIGNAL ANALYSIS
    // =========================================================================

    @Query("SELECT AVG(rssi) FROM network_bluetooth_snapshots WHERE timestamp >= :startTime AND rssi IS NOT NULL")
    Double getAverageRssi(long startTime);

    @Query("SELECT MIN(rssi) FROM network_bluetooth_snapshots WHERE timestamp >= :startTime AND rssi IS NOT NULL")
    Integer getMinRssi(long startTime);

    // =========================================================================
    // SYNC SUPPORT (Incremental)
    // =========================================================================

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE id > :lastSyncedId ORDER BY id ASC LIMIT :limit")
    List<NetworkBluetoothSnapshot> getSinceId(long lastSyncedId, int limit);

    @Query("SELECT MAX(id) FROM network_bluetooth_snapshots")
    Long getMaxId();

    @Query("SELECT MIN(timestamp) FROM network_bluetooth_snapshots")
    Long getEarliestTimestamp();

    @Query("SELECT MAX(timestamp) FROM network_bluetooth_snapshots")
    Long getLatestTimestamp();

    // =========================================================================
    // SESSION QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_bluetooth_snapshots WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    List<NetworkBluetoothSnapshot> getBySessionId(long sessionId);

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @Query("DELETE FROM network_bluetooth_snapshots WHERE timestamp < :olderThan")
    int deleteOlderThan(long olderThan);

    @Query("DELETE FROM network_bluetooth_snapshots WHERE validationStatus = 'invalid' AND timestamp < :olderThan")
    int deleteInvalidOlderThan(long olderThan);
}
