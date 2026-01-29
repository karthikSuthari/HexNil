package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.NetworkWifiSnapshot;

import java.util.List;

/**
 * Data Access Object for NetworkWifiSnapshot.
 * 
 * Provides CRUD operations and specialized queries for Wi-Fi network data.
 * Includes stability metrics and reconnection analysis support.
 */
@Dao
public interface NetworkWifiSnapshotDao {

    // =========================================================================
    // BASIC CRUD
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(NetworkWifiSnapshot snapshot);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<NetworkWifiSnapshot> snapshots);

    @Update
    void update(NetworkWifiSnapshot snapshot);

    @Delete
    void delete(NetworkWifiSnapshot snapshot);

    @Query("DELETE FROM network_wifi_snapshots WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM network_wifi_snapshots")
    void deleteAll();

    // =========================================================================
    // BASIC QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_wifi_snapshots WHERE id = :id")
    NetworkWifiSnapshot getById(long id);

    @Query("SELECT * FROM network_wifi_snapshots ORDER BY timestamp DESC LIMIT 1")
    NetworkWifiSnapshot getLatest();

    @Query("SELECT * FROM network_wifi_snapshots ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkWifiSnapshot> getLatest(int limit);

    @Query("SELECT COUNT(*) FROM network_wifi_snapshots")
    int getCount();

    @Query("SELECT * FROM network_wifi_snapshots ORDER BY timestamp ASC")
    List<NetworkWifiSnapshot> getAll();

    // =========================================================================
    // TIME-BASED QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_wifi_snapshots WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<NetworkWifiSnapshot> getSnapshotsSince(long startTime);

    @Query("SELECT * FROM network_wifi_snapshots WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    List<NetworkWifiSnapshot> getSnapshotsInRange(long startTime, long endTime);

    @Query("SELECT * FROM network_wifi_snapshots WHERE timestamp >= :startTime ORDER BY timestamp ASC LIMIT :limit")
    List<NetworkWifiSnapshot> getSnapshotsSinceWithLimit(long startTime, int limit);

    // =========================================================================
    // QUALITY-FILTERED QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_wifi_snapshots WHERE timestamp >= :startTime AND validationStatus != 'invalid' AND qualityScore >= :minQuality ORDER BY timestamp ASC")
    List<NetworkWifiSnapshot> getValidSnapshotsSince(long startTime, int minQuality);

    @Query("SELECT * FROM network_wifi_snapshots WHERE timestamp >= :startTime AND timestamp <= :endTime AND validationStatus != 'invalid' ORDER BY timestamp ASC")
    List<NetworkWifiSnapshot> getUsableSnapshotsInRange(long startTime, long endTime);

    @Query("SELECT * FROM network_wifi_snapshots WHERE qualityScore >= 85 AND validationStatus = 'valid' ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkWifiSnapshot> getHighQualitySnapshots(int limit);

    // =========================================================================
    // CONNECTION STATE QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_wifi_snapshots WHERE connectionState = 'CONNECTED' ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkWifiSnapshot> getConnectedSnapshots(int limit);

    @Query("SELECT * FROM network_wifi_snapshots WHERE connectionState = 'DISCONNECTED' ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkWifiSnapshot> getDisconnectedSnapshots(int limit);

    @Query("SELECT COUNT(*) FROM network_wifi_snapshots WHERE connectionState = 'CONNECTED' AND timestamp >= :startTime")
    int getConnectedCount(long startTime);

    // =========================================================================
    // FREQUENCY BAND QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_wifi_snapshots WHERE frequency >= 5000 ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkWifiSnapshot> get5GHzSnapshots(int limit);

    @Query("SELECT * FROM network_wifi_snapshots WHERE frequency >= 5925 ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkWifiSnapshot> get6GHzSnapshots(int limit);

    @Query("SELECT * FROM network_wifi_snapshots WHERE frequency < 3000 ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkWifiSnapshot> get2_4GHzSnapshots(int limit);

    @Query("SELECT COUNT(*) FROM network_wifi_snapshots WHERE frequency >= 5000 AND timestamp >= :startTime")
    int get5GHzCount(long startTime);

    // =========================================================================
    // Wi-Fi STANDARD QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_wifi_snapshots WHERE wifiStandard >= 6 ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkWifiSnapshot> getWifi6OrNewerSnapshots(int limit);

    @Query("SELECT * FROM network_wifi_snapshots WHERE wifiStandard = :standard ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkWifiSnapshot> getByWifiStandard(int standard, int limit);

    // =========================================================================
    // SIGNAL ANALYSIS QUERIES
    // =========================================================================

    @Query("SELECT AVG(rssi) FROM network_wifi_snapshots WHERE timestamp >= :startTime AND rssi IS NOT NULL AND validationStatus != 'invalid'")
    Double getAverageRssi(long startTime);

    @Query("SELECT MIN(rssi) FROM network_wifi_snapshots WHERE timestamp >= :startTime AND rssi IS NOT NULL")
    Integer getMinRssi(long startTime);

    @Query("SELECT MAX(rssi) FROM network_wifi_snapshots WHERE timestamp >= :startTime AND rssi IS NOT NULL")
    Integer getMaxRssi(long startTime);

    @Query("SELECT AVG(linkSpeedMbps) FROM network_wifi_snapshots WHERE timestamp >= :startTime AND linkSpeedMbps IS NOT NULL AND connectionState = 'CONNECTED'")
    Double getAverageLinkSpeed(long startTime);

    @Query("SELECT COUNT(*) FROM network_wifi_snapshots WHERE timestamp >= :startTime AND rssi < -80")
    int getWeakSignalCount(long startTime);

    // =========================================================================
    // STABILITY METRICS
    // =========================================================================

    @Query("SELECT SUM(reconnectCount) FROM network_wifi_snapshots WHERE timestamp >= :startTime")
    Integer getTotalReconnects(long startTime);

    @Query("SELECT * FROM network_wifi_snapshots WHERE disconnectReason IS NOT NULL ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkWifiSnapshot> getDisconnectEvents(int limit);

    @Query("SELECT AVG(dhcpLatencyMs) FROM network_wifi_snapshots WHERE dhcpLatencyMs IS NOT NULL AND timestamp >= :startTime")
    Double getAverageDhcpLatency(long startTime);

    // =========================================================================
    // NETWORK-SPECIFIC QUERIES (by hashed identifiers)
    // =========================================================================

    @Query("SELECT * FROM network_wifi_snapshots WHERE ssidHash = :ssidHash ORDER BY timestamp DESC LIMIT :limit")
    List<NetworkWifiSnapshot> getBySsidHash(String ssidHash, int limit);

    @Query("SELECT DISTINCT ssidHash FROM network_wifi_snapshots WHERE ssidHash IS NOT NULL")
    List<String> getDistinctSsidHashes();

    @Query("SELECT COUNT(DISTINCT ssidHash) FROM network_wifi_snapshots WHERE timestamp >= :startTime")
    int getUniqueNetworkCount(long startTime);

    // =========================================================================
    // SYNC SUPPORT (Incremental)
    // =========================================================================

    @Query("SELECT * FROM network_wifi_snapshots WHERE id > :lastSyncedId ORDER BY id ASC LIMIT :limit")
    List<NetworkWifiSnapshot> getSinceId(long lastSyncedId, int limit);

    @Query("SELECT MAX(id) FROM network_wifi_snapshots")
    Long getMaxId();

    @Query("SELECT MIN(timestamp) FROM network_wifi_snapshots")
    Long getEarliestTimestamp();

    @Query("SELECT MAX(timestamp) FROM network_wifi_snapshots")
    Long getLatestTimestamp();

    // =========================================================================
    // SESSION QUERIES
    // =========================================================================

    @Query("SELECT * FROM network_wifi_snapshots WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    List<NetworkWifiSnapshot> getBySessionId(long sessionId);

    // =========================================================================
    // FROZEN SIGNAL DETECTION
    // =========================================================================

    @Query("SELECT * FROM network_wifi_snapshots WHERE signalDataChanged = 0 AND timestamp >= :startTime ORDER BY timestamp ASC")
    List<NetworkWifiSnapshot> getFrozenSignalSnapshots(long startTime);

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @Query("DELETE FROM network_wifi_snapshots WHERE timestamp < :olderThan")
    int deleteOlderThan(long olderThan);

    @Query("DELETE FROM network_wifi_snapshots WHERE validationStatus = 'invalid' AND timestamp < :olderThan")
    int deleteInvalidOlderThan(long olderThan);
}
