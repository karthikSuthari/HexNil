package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.AnrEvent;

import java.util.List;

/**
 * DAO for AnrEvent entity.
 * Provides CRUD operations and ANR analysis queries.
 */
@Dao
public interface AnrEventDao {

    // =========================================================================
    // Insert Operations
    // =========================================================================

    @Insert
    long insert(AnrEvent event);

    @Insert
    void insertAll(List<AnrEvent> events);

    // =========================================================================
    // Query Operations
    // =========================================================================

    @Query("SELECT * FROM anr_events ORDER BY timestamp DESC LIMIT 1")
    AnrEvent getLatest();

    @Query("SELECT * FROM anr_events WHERE id = :id")
    AnrEvent getById(long id);

    @Query("SELECT * FROM anr_events WHERE timestamp >= :since ORDER BY timestamp DESC")
    List<AnrEvent> getSince(long since);

    @Query("SELECT * FROM anr_events WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    List<AnrEvent> getInRange(long start, long end);

    @Query("SELECT * FROM anr_events ORDER BY timestamp DESC LIMIT :limit")
    List<AnrEvent> getRecent(int limit);

    // =========================================================================
    // Package-Specific Queries
    // =========================================================================

    @Query("SELECT * FROM anr_events WHERE packageName = :packageName ORDER BY timestamp DESC")
    List<AnrEvent> getByPackage(String packageName);

    @Query("SELECT * FROM anr_events WHERE packageName = :packageName AND timestamp >= :since ORDER BY timestamp DESC")
    List<AnrEvent> getByPackageSince(String packageName, long since);

    @Query("SELECT * FROM anr_events WHERE packageName = :packageName AND timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    List<AnrEvent> getByPackageInRange(String packageName, long start, long end);

    // =========================================================================
    // ANR Type Queries
    // =========================================================================

    @Query("SELECT * FROM anr_events WHERE anrType = :anrType AND timestamp >= :since ORDER BY timestamp DESC")
    List<AnrEvent> getByTypeSince(String anrType, long since);

    @Query("SELECT * FROM anr_events WHERE anrType = 'input_dispatching' AND timestamp >= :since ORDER BY timestamp DESC")
    List<AnrEvent> getInputAnrsSince(long since);

    // =========================================================================
    // Count Queries
    // =========================================================================

    @Query("SELECT COUNT(*) FROM anr_events WHERE timestamp >= :since")
    int getCountSince(long since);

    @Query("SELECT COUNT(*) FROM anr_events WHERE packageName = :packageName AND timestamp >= :since")
    int getCountByPackage(String packageName, long since);

    @Query("SELECT COUNT(*) FROM anr_events WHERE timestamp BETWEEN :start AND :end")
    int getCountInRange(long start, long end);

    @Query("SELECT COUNT(*) FROM anr_events WHERE packageName = :packageName AND timestamp BETWEEN :start AND :end")
    int getCountByPackageInRange(String packageName, long start, long end);

    // =========================================================================
    // Unique ANR Analysis
    // =========================================================================

    @Query("SELECT COUNT(DISTINCT traceHash) FROM anr_events WHERE timestamp >= :since")
    int getUniqueAnrTypeCount(long since);

    @Query("SELECT COUNT(DISTINCT traceHash) FROM anr_events WHERE packageName = :packageName AND timestamp >= :since")
    int getUniqueAnrTypeCountByPackage(String packageName, long since);

    @Query("SELECT * FROM anr_events WHERE isNewAnrType = 1 AND timestamp >= :since ORDER BY timestamp DESC")
    List<AnrEvent> getNewAnrTypes(long since);

    @Query("SELECT DISTINCT traceHash FROM anr_events WHERE packageName = :packageName")
    List<String> getDistinctAnrHashesByPackage(String packageName);

    // =========================================================================
    // Deduplication Check
    // =========================================================================

    @Query("SELECT EXISTS(SELECT 1 FROM anr_events WHERE traceHash = :hash AND packageName = :packageName LIMIT 1)")
    boolean existsByHash(String hash, String packageName);

    @Query("SELECT COUNT(*) FROM anr_events WHERE traceHash = :hash")
    int getCountByHash(String hash);

    // =========================================================================
    // Version-Based Check (for regression after update)
    // =========================================================================

    @Query("SELECT * FROM anr_events WHERE packageName = :packageName AND appVersionCode > :previousVersionCode ORDER BY timestamp ASC LIMIT 1")
    AnrEvent getFirstAnrAfterVersion(String packageName, int previousVersionCode);

    @Query("SELECT COUNT(*) FROM anr_events WHERE packageName = :packageName AND appVersionCode = :versionCode")
    int getCountByVersion(String packageName, int versionCode);

    // =========================================================================
    // Distinct Packages
    // =========================================================================

    @Query("SELECT DISTINCT packageName FROM anr_events WHERE timestamp >= :since")
    List<String> getDistinctPackages(long since);

    // =========================================================================
    // Cleanup Operations
    // =========================================================================

    @Query("DELETE FROM anr_events WHERE timestamp < :before")
    int deleteOlderThan(long before);

    @Query("DELETE FROM anr_events")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM anr_events")
    int getCount();
}
