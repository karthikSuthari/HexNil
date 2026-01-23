package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.CrashEvent;

import java.util.List;

/**
 * DAO for CrashEvent entity.
 * Provides CRUD operations and crash analysis queries.
 */
@Dao
public interface CrashEventDao {

    // =========================================================================
    // Insert Operations
    // =========================================================================

    @Insert
    long insert(CrashEvent event);

    @Insert
    void insertAll(List<CrashEvent> events);

    // =========================================================================
    // Query Operations
    // =========================================================================

    @Query("SELECT * FROM crash_events ORDER BY timestamp DESC LIMIT 1")
    CrashEvent getLatest();

    @Query("SELECT * FROM crash_events WHERE id = :id")
    CrashEvent getById(long id);

    @Query("SELECT * FROM crash_events WHERE timestamp >= :since ORDER BY timestamp DESC")
    List<CrashEvent> getSince(long since);

    @Query("SELECT * FROM crash_events WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    List<CrashEvent> getInRange(long start, long end);

    @Query("SELECT * FROM crash_events ORDER BY timestamp DESC LIMIT :limit")
    List<CrashEvent> getRecent(int limit);

    // =========================================================================
    // Package-Specific Queries
    // =========================================================================

    @Query("SELECT * FROM crash_events WHERE packageName = :packageName ORDER BY timestamp DESC")
    List<CrashEvent> getByPackage(String packageName);

    @Query("SELECT * FROM crash_events WHERE packageName = :packageName AND timestamp >= :since ORDER BY timestamp DESC")
    List<CrashEvent> getByPackageSince(String packageName, long since);

    @Query("SELECT * FROM crash_events WHERE packageName = :packageName AND timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    List<CrashEvent> getByPackageInRange(String packageName, long start, long end);

    // =========================================================================
    // Crash Type Queries
    // =========================================================================

    @Query("SELECT * FROM crash_events WHERE crashType = :crashType AND timestamp >= :since ORDER BY timestamp DESC")
    List<CrashEvent> getByTypeSince(String crashType, long since);

    @Query("SELECT * FROM crash_events WHERE crashType = 'native_crash' AND timestamp >= :since ORDER BY timestamp DESC")
    List<CrashEvent> getNativeCrashesSince(long since);

    // =========================================================================
    // Count Queries
    // =========================================================================

    @Query("SELECT COUNT(*) FROM crash_events WHERE timestamp >= :since")
    int getCountSince(long since);

    @Query("SELECT COUNT(*) FROM crash_events WHERE packageName = :packageName AND timestamp >= :since")
    int getCountByPackage(String packageName, long since);

    @Query("SELECT COUNT(*) FROM crash_events WHERE timestamp BETWEEN :start AND :end")
    int getCountInRange(long start, long end);

    @Query("SELECT COUNT(*) FROM crash_events WHERE packageName = :packageName AND timestamp BETWEEN :start AND :end")
    int getCountByPackageInRange(String packageName, long start, long end);

    // =========================================================================
    // Unique Crash Analysis
    // =========================================================================

    @Query("SELECT COUNT(DISTINCT stackTraceHash) FROM crash_events WHERE timestamp >= :since")
    int getUniqueCrashTypeCount(long since);

    @Query("SELECT COUNT(DISTINCT stackTraceHash) FROM crash_events WHERE packageName = :packageName AND timestamp >= :since")
    int getUniqueCrashTypeCountByPackage(String packageName, long since);

    @Query("SELECT * FROM crash_events WHERE isNewCrashType = 1 AND timestamp >= :since ORDER BY timestamp DESC")
    List<CrashEvent> getNewCrashTypes(long since);

    @Query("SELECT DISTINCT stackTraceHash FROM crash_events WHERE packageName = :packageName")
    List<String> getDistinctCrashHashesByPackage(String packageName);

    // =========================================================================
    // Deduplication Check
    // =========================================================================

    @Query("SELECT EXISTS(SELECT 1 FROM crash_events WHERE stackTraceHash = :hash AND packageName = :packageName LIMIT 1)")
    boolean existsByHash(String hash, String packageName);

    @Query("SELECT COUNT(*) FROM crash_events WHERE stackTraceHash = :hash")
    int getCountByHash(String hash);

    // =========================================================================
    // Distinct Packages
    // =========================================================================

    @Query("SELECT DISTINCT packageName FROM crash_events WHERE timestamp >= :since")
    List<String> getDistinctPackages(long since);

    // =========================================================================
    // Cleanup Operations
    // =========================================================================

    @Query("DELETE FROM crash_events WHERE timestamp < :before")
    int deleteOlderThan(long before);

    @Query("DELETE FROM crash_events")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM crash_events")
    int getCount();
}
