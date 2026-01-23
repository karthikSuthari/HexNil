package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.MemorySnapshot;

import java.util.List;

/**
 * DAO for MemorySnapshot entity.
 * Provides CRUD operations and analysis queries for RAM usage tracking.
 */
@Dao
public interface MemorySnapshotDao {

    // =========================================================================
    // Insert Operations
    // =========================================================================

    @Insert
    long insert(MemorySnapshot snapshot);

    @Insert
    void insertAll(List<MemorySnapshot> snapshots);

    // =========================================================================
    // Query Operations
    // =========================================================================

    @Query("SELECT * FROM memory_snapshots ORDER BY timestamp DESC LIMIT 1")
    MemorySnapshot getLatest();

    @Query("SELECT * FROM memory_snapshots WHERE id = :id")
    MemorySnapshot getById(long id);

    @Query("SELECT * FROM memory_snapshots WHERE timestamp >= :since ORDER BY timestamp DESC")
    List<MemorySnapshot> getSince(long since);

    @Query("SELECT * FROM memory_snapshots WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    List<MemorySnapshot> getInRange(long start, long end);

    @Query("SELECT * FROM memory_snapshots ORDER BY timestamp DESC LIMIT :limit")
    List<MemorySnapshot> getRecent(int limit);

    // =========================================================================
    // Low Memory Analysis
    // =========================================================================

    @Query("SELECT * FROM memory_snapshots WHERE isLowMemory = 1 AND timestamp >= :since ORDER BY timestamp DESC")
    List<MemorySnapshot> getLowMemoryEventsSince(long since);

    @Query("SELECT COUNT(*) FROM memory_snapshots WHERE isLowMemory = 1 AND timestamp >= :since")
    int getLowMemoryEventCount(long since);

    @Query("SELECT COUNT(*) FROM memory_snapshots WHERE isLowMemory = 1 AND timestamp BETWEEN :start AND :end")
    int getLowMemoryEventCountInRange(long start, long end);

    // =========================================================================
    // Aggregation Queries
    // =========================================================================

    @Query("SELECT AVG(usedMemoryPercent) FROM memory_snapshots WHERE timestamp >= :since")
    float getAverageMemoryUsageSince(long since);

    @Query("SELECT MAX(usedMemoryPercent) FROM memory_snapshots WHERE timestamp >= :since")
    float getMaxMemoryUsageSince(long since);

    @Query("SELECT MIN(usedMemoryPercent) FROM memory_snapshots WHERE timestamp >= :since")
    float getMinMemoryUsageSince(long since);

    @Query("SELECT AVG(usedMemoryPercent) FROM memory_snapshots WHERE timestamp BETWEEN :start AND :end")
    float getAverageMemoryUsageInRange(long start, long end);

    @Query("SELECT MAX(usedMemoryPercent) FROM memory_snapshots WHERE timestamp BETWEEN :start AND :end")
    float getMaxMemoryUsageInRange(long start, long end);

    @Query("SELECT COUNT(*) FROM memory_snapshots WHERE timestamp >= :since")
    int getSampleCountSince(long since);

    // =========================================================================
    // Critical Memory Queries
    // =========================================================================

    @Query("SELECT * FROM memory_snapshots WHERE usedMemoryPercent > 90 AND timestamp >= :since ORDER BY timestamp DESC")
    List<MemorySnapshot> getCriticalMemoryEvents(long since);

    @Query("SELECT * FROM memory_snapshots WHERE usedMemoryPercent > 80 AND timestamp >= :since ORDER BY timestamp DESC")
    List<MemorySnapshot> getWarningMemoryEvents(long since);

    // =========================================================================
    // Cleanup Operations
    // =========================================================================

    @Query("DELETE FROM memory_snapshots WHERE timestamp < :before")
    int deleteOlderThan(long before);

    @Query("DELETE FROM memory_snapshots")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM memory_snapshots")
    int getCount();
}
