package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.ModemFirmwareEvent;

import java.util.List;

/**
 * Data Access Object for ModemFirmwareEvent.
 * 
 * Provides CRUD operations and specialized queries for modem firmware tracking.
 * Supports version change detection and OTA update correlation.
 */
@Dao
public interface ModemFirmwareEventDao {

    // =========================================================================
    // BASIC CRUD
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ModemFirmwareEvent event);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<ModemFirmwareEvent> events);

    @Update
    void update(ModemFirmwareEvent event);

    @Delete
    void delete(ModemFirmwareEvent event);

    @Query("DELETE FROM modem_firmware_events WHERE id = :id")
    void deleteById(long id);

    @Query("DELETE FROM modem_firmware_events")
    void deleteAll();

    // =========================================================================
    // BASIC QUERIES
    // =========================================================================

    @Query("SELECT * FROM modem_firmware_events WHERE id = :id")
    ModemFirmwareEvent getById(long id);

    @Query("SELECT * FROM modem_firmware_events ORDER BY timestamp DESC LIMIT 1")
    ModemFirmwareEvent getLatest();

    @Query("SELECT * FROM modem_firmware_events ORDER BY timestamp DESC LIMIT :limit")
    List<ModemFirmwareEvent> getLatest(int limit);

    @Query("SELECT COUNT(*) FROM modem_firmware_events")
    int getCount();

    @Query("SELECT * FROM modem_firmware_events ORDER BY timestamp ASC")
    List<ModemFirmwareEvent> getAll();

    // =========================================================================
    // TIME-BASED QUERIES
    // =========================================================================

    @Query("SELECT * FROM modem_firmware_events WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<ModemFirmwareEvent> getEventsSince(long startTime);

    @Query("SELECT * FROM modem_firmware_events WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    List<ModemFirmwareEvent> getEventsInRange(long startTime, long endTime);

    // =========================================================================
    // EVENT TYPE QUERIES
    // =========================================================================

    @Query("SELECT * FROM modem_firmware_events WHERE eventType = :eventType ORDER BY timestamp DESC LIMIT :limit")
    List<ModemFirmwareEvent> getByEventType(String eventType, int limit);

    @Query("SELECT * FROM modem_firmware_events WHERE eventType = 'VERSION_CHANGE' ORDER BY timestamp DESC LIMIT :limit")
    List<ModemFirmwareEvent> getVersionChanges(int limit);

    @Query("SELECT * FROM modem_firmware_events WHERE eventType = 'OTA_UPDATE' ORDER BY timestamp DESC LIMIT :limit")
    List<ModemFirmwareEvent> getOtaUpdates(int limit);

    @Query("SELECT * FROM modem_firmware_events WHERE eventType = 'INITIAL_CAPTURE' ORDER BY timestamp ASC LIMIT 1")
    ModemFirmwareEvent getFirstCapture();

    @Query("SELECT COUNT(*) FROM modem_firmware_events WHERE eventType = 'VERSION_CHANGE'")
    int getVersionChangeCount();

    // =========================================================================
    // VERSION QUERIES
    // =========================================================================

    @Query("SELECT * FROM modem_firmware_events WHERE currentVersion = :version ORDER BY timestamp DESC LIMIT :limit")
    List<ModemFirmwareEvent> getByVersion(String version, int limit);

    @Query("SELECT DISTINCT currentVersion FROM modem_firmware_events WHERE currentVersion IS NOT NULL ORDER BY timestamp DESC")
    List<String> getDistinctVersions();

    @Query("SELECT * FROM modem_firmware_events WHERE isVersionChange = 1 ORDER BY timestamp DESC LIMIT :limit")
    List<ModemFirmwareEvent> getVersionChangeEvents(int limit);

    @Query("SELECT * FROM modem_firmware_events WHERE isOtaUpdate = 1 ORDER BY timestamp DESC LIMIT :limit")
    List<ModemFirmwareEvent> getOtaUpdateEvents(int limit);

    // =========================================================================
    // SECURITY PATCH QUERIES
    // =========================================================================

    @Query("SELECT * FROM modem_firmware_events WHERE securityPatch = :patch ORDER BY timestamp DESC LIMIT :limit")
    List<ModemFirmwareEvent> getBySecurityPatch(String patch, int limit);

    @Query("SELECT DISTINCT securityPatch FROM modem_firmware_events WHERE securityPatch IS NOT NULL ORDER BY securityPatch DESC")
    List<String> getDistinctSecurityPatches();

    // =========================================================================
    // ANDROID VERSION QUERIES
    // =========================================================================

    @Query("SELECT * FROM modem_firmware_events WHERE androidVersion = :version ORDER BY timestamp DESC LIMIT :limit")
    List<ModemFirmwareEvent> getByAndroidVersion(String version, int limit);

    @Query("SELECT DISTINCT androidVersion FROM modem_firmware_events WHERE androidVersion IS NOT NULL")
    List<String> getDistinctAndroidVersions();

    // =========================================================================
    // SYNC SUPPORT (Incremental)
    // =========================================================================

    @Query("SELECT * FROM modem_firmware_events WHERE id > :lastSyncedId ORDER BY id ASC LIMIT :limit")
    List<ModemFirmwareEvent> getSinceId(long lastSyncedId, int limit);

    @Query("SELECT MAX(id) FROM modem_firmware_events")
    Long getMaxId();

    @Query("SELECT MIN(timestamp) FROM modem_firmware_events")
    Long getEarliestTimestamp();

    @Query("SELECT MAX(timestamp) FROM modem_firmware_events")
    Long getLatestTimestamp();

    // =========================================================================
    // ANALYSIS SUPPORT
    // =========================================================================

    /**
     * Get the most recent event before a given timestamp.
     * Useful for comparing before/after firmware changes.
     */
    @Query("SELECT * FROM modem_firmware_events WHERE timestamp < :beforeTime ORDER BY timestamp DESC LIMIT 1")
    ModemFirmwareEvent getEventBefore(long beforeTime);

    /**
     * Get the first event after a given timestamp.
     * Useful for finding what changed after an OTA.
     */
    @Query("SELECT * FROM modem_firmware_events WHERE timestamp > :afterTime ORDER BY timestamp ASC LIMIT 1")
    ModemFirmwareEvent getEventAfter(long afterTime);

    // =========================================================================
    // CLEANUP
    // =========================================================================

    @Query("DELETE FROM modem_firmware_events WHERE timestamp < :olderThan AND eventType = 'PERIODIC_CHECK'")
    int deletePeriodicChecksOlderThan(long olderThan);
}
