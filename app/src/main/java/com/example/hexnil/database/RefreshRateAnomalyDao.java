package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.hexnil.model.RefreshRateAnomaly;

import java.util.List;

/**
 * Data Access Object for RefreshRateAnomaly.
 * Provides insert and query operations for refresh rate anomalies.
 */
@Dao
public interface RefreshRateAnomalyDao {

    // =========================================================================
    // Insert Operations
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(RefreshRateAnomaly anomaly);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<RefreshRateAnomaly> anomalies);

    // =========================================================================
    // Query by ID
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_anomalies WHERE id = :id")
    RefreshRateAnomaly getById(long id);

    // =========================================================================
    // Query by Session
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_anomalies WHERE linkedSessionId = :sessionId ORDER BY detectedAt DESC")
    List<RefreshRateAnomaly> getBySessionId(long sessionId);

    @Query("SELECT COUNT(*) FROM refresh_rate_anomalies WHERE linkedSessionId = :sessionId")
    int countBySessionId(long sessionId);

    // =========================================================================
    // Query by Time Range
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_anomalies WHERE detectedAt >= :startTime AND detectedAt <= :endTime ORDER BY detectedAt DESC")
    List<RefreshRateAnomaly> getInTimeRange(long startTime, long endTime);

    @Query("SELECT * FROM refresh_rate_anomalies WHERE detectedAt >= :since ORDER BY detectedAt DESC")
    List<RefreshRateAnomaly> getSince(long since);

    @Query("SELECT * FROM refresh_rate_anomalies WHERE detectedAt >= :since ORDER BY detectedAt DESC LIMIT :limit")
    List<RefreshRateAnomaly> getRecentAnomalies(long since, int limit);

    // =========================================================================
    // Query by Type
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_anomalies WHERE anomalyType = :type ORDER BY detectedAt DESC")
    List<RefreshRateAnomaly> getByType(String type);

    @Query("SELECT * FROM refresh_rate_anomalies WHERE anomalyType = :type AND detectedAt >= :since ORDER BY detectedAt DESC")
    List<RefreshRateAnomaly> getByTypeSince(String type, long since);

    @Query("SELECT COUNT(*) FROM refresh_rate_anomalies WHERE anomalyType = :type AND detectedAt >= :since")
    int countByTypeSince(String type, long since);

    // =========================================================================
    // Query by Severity
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_anomalies WHERE severity = :severity ORDER BY detectedAt DESC")
    List<RefreshRateAnomaly> getBySeverity(String severity);

    @Query("SELECT * FROM refresh_rate_anomalies WHERE severity IN ('critical', 'high') ORDER BY detectedAt DESC")
    List<RefreshRateAnomaly> getCriticalAndHigh();

    @Query("SELECT * FROM refresh_rate_anomalies WHERE severity IN ('critical', 'high') AND detectedAt >= :since ORDER BY detectedAt DESC")
    List<RefreshRateAnomaly> getCriticalAndHighSince(long since);

    // =========================================================================
    // Query by Package
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_anomalies WHERE packageName = :packageName ORDER BY detectedAt DESC")
    List<RefreshRateAnomaly> getByPackage(String packageName);

    @Query("SELECT * FROM refresh_rate_anomalies WHERE packageName = :packageName AND detectedAt >= :since ORDER BY detectedAt DESC")
    List<RefreshRateAnomaly> getByPackageSince(String packageName, long since);

    @Query("SELECT COUNT(*) FROM refresh_rate_anomalies WHERE packageName = :packageName")
    int countByPackage(String packageName);

    // =========================================================================
    // Query by Cause Category
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_anomalies WHERE causeCategory = :cause ORDER BY detectedAt DESC")
    List<RefreshRateAnomaly> getByCauseCategory(String cause);

    @Query("SELECT COUNT(*) FROM refresh_rate_anomalies WHERE causeCategory = :cause AND detectedAt >= :since")
    int countByCauseCategorySince(String cause, long since);

    // =========================================================================
    // Aggregation Queries
    // =========================================================================

    @Query("SELECT COUNT(*) FROM refresh_rate_anomalies WHERE detectedAt >= :since")
    int countSince(long since);

    @Query("SELECT COUNT(*) FROM refresh_rate_anomalies WHERE severity = 'critical' AND detectedAt >= :since")
    int countCriticalSince(long since);

    @Query("SELECT AVG(confidenceScore) FROM refresh_rate_anomalies WHERE detectedAt >= :since")
    float getAverageConfidenceSince(long since);

    // =========================================================================
    // Top Offenders Query
    // =========================================================================

    @Query("SELECT packageName, COUNT(*) as count FROM refresh_rate_anomalies " +
            "WHERE detectedAt >= :since GROUP BY packageName ORDER BY count DESC LIMIT :limit")
    List<PackageAnomalyCount> getTopOffendingPackages(long since, int limit);

    // =========================================================================
    // Count Queries
    // =========================================================================

    @Query("SELECT COUNT(*) FROM refresh_rate_anomalies")
    int getCount();

    // =========================================================================
    // Cleanup
    // =========================================================================

    @Query("DELETE FROM refresh_rate_anomalies WHERE detectedAt < :before")
    int deleteOlderThan(long before);

    @Query("DELETE FROM refresh_rate_anomalies")
    void deleteAll();

    // =========================================================================
    // Sync Support
    // =========================================================================

    @Query("SELECT * FROM refresh_rate_anomalies WHERE id > :lastId ORDER BY id ASC")
    List<RefreshRateAnomaly> getAfterIdForSync(long lastId);

    @Query("SELECT MAX(id) FROM refresh_rate_anomalies")
    Long getMaxId();

    // =========================================================================
    // Helper Class for Aggregation
    // =========================================================================

    class PackageAnomalyCount {
        public String packageName;
        public int count;
    }
}
