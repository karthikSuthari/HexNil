package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.AnomalyRecord;

import java.util.List;

/**
 * DAO for anomaly records - stores all detected anomalies with detailed descriptions
 */
@Dao
public interface AnomalyRecordDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AnomalyRecord anomaly);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AnomalyRecord> anomalies);
    
    @Update
    void update(AnomalyRecord anomaly);
    
    @Delete
    void delete(AnomalyRecord anomaly);
    
    // =========================================================================
    // BASIC QUERIES
    // =========================================================================
    
    @Query("SELECT * FROM anomaly_records ORDER BY detectedAt DESC")
    List<AnomalyRecord> getAllAnomalies();
    
    @Query("SELECT * FROM anomaly_records WHERE detectedAt >= :startTime ORDER BY detectedAt DESC")
    List<AnomalyRecord> getAnomaliesSince(long startTime);
    
    @Query("SELECT * FROM anomaly_records ORDER BY detectedAt DESC LIMIT :limit")
    List<AnomalyRecord> getRecentAnomalies(int limit);
    
    @Query("SELECT * FROM anomaly_records WHERE id = :id")
    AnomalyRecord getById(long id);
    
    // =========================================================================
    // BY TYPE QUERIES
    // =========================================================================
    
    @Query("SELECT * FROM anomaly_records WHERE anomalyType = :type ORDER BY detectedAt DESC")
    List<AnomalyRecord> getAnomaliesByType(String type);
    
    @Query("SELECT * FROM anomaly_records WHERE anomalyType = 'app_drain' ORDER BY detectedAt DESC")
    List<AnomalyRecord> getAppDrainAnomalies();
    
    @Query("SELECT * FROM anomaly_records WHERE anomalyType = 'thermal' ORDER BY detectedAt DESC")
    List<AnomalyRecord> getThermalAnomalies();
    
    @Query("SELECT * FROM anomaly_records WHERE anomalyType = 'battery_health' ORDER BY detectedAt DESC")
    List<AnomalyRecord> getHealthAnomalies();
    
    @Query("SELECT * FROM anomaly_records WHERE anomalyType = 'charging' ORDER BY detectedAt DESC")
    List<AnomalyRecord> getChargingAnomalies();
    
    @Query("SELECT * FROM anomaly_records WHERE anomalyType = 'usage_pattern' ORDER BY detectedAt DESC")
    List<AnomalyRecord> getUsagePatternAnomalies();
    
    // =========================================================================
    // BY SEVERITY QUERIES
    // =========================================================================
    
    @Query("SELECT * FROM anomaly_records WHERE severity = :severity ORDER BY detectedAt DESC")
    List<AnomalyRecord> getAnomaliesBySeverity(String severity);
    
    @Query("SELECT * FROM anomaly_records WHERE severity = 'critical' ORDER BY detectedAt DESC")
    List<AnomalyRecord> getCriticalAnomalies();
    
    @Query("SELECT * FROM anomaly_records WHERE severity IN ('critical', 'high') ORDER BY detectedAt DESC")
    List<AnomalyRecord> getHighPriorityAnomalies();
    
    @Query("SELECT * FROM anomaly_records WHERE severity IN ('critical', 'high') AND resolved = 0 ORDER BY detectedAt DESC")
    List<AnomalyRecord> getUnresolvedHighPriority();
    
    // =========================================================================
    // BY APP QUERIES
    // =========================================================================
    
    @Query("SELECT * FROM anomaly_records WHERE sourceApp = :packageName ORDER BY detectedAt DESC")
    List<AnomalyRecord> getAnomaliesForApp(String packageName);
    
    @Query("SELECT * FROM anomaly_records WHERE sourceApp = :packageName AND detectedAt >= :startTime ORDER BY detectedAt DESC")
    List<AnomalyRecord> getAppAnomaliesSince(String packageName, long startTime);
    
    @Query("SELECT DISTINCT sourceApp FROM anomaly_records WHERE sourceApp IS NOT NULL ORDER BY sourceApp")
    List<String> getAppsWithAnomalies();
    
    @Query("SELECT sourceApp, COUNT(*) as count FROM anomaly_records WHERE sourceApp IS NOT NULL GROUP BY sourceApp ORDER BY count DESC")
    List<AppAnomalyCount> getAnomalyCountPerApp();
    
    // =========================================================================
    // STATUS QUERIES
    // =========================================================================
    
    @Query("SELECT * FROM anomaly_records WHERE resolved = 0 ORDER BY detectedAt DESC")
    List<AnomalyRecord> getUnresolvedAnomalies();
    
    @Query("SELECT * FROM anomaly_records WHERE acknowledged = 0 ORDER BY detectedAt DESC")
    List<AnomalyRecord> getUnacknowledgedAnomalies();
    
    @Query("SELECT * FROM anomaly_records WHERE isRecurring = 1 ORDER BY occurrenceCount DESC")
    List<AnomalyRecord> getRecurringAnomalies();
    
    @Query("UPDATE anomaly_records SET acknowledged = 1 WHERE id = :id")
    void acknowledgeAnomaly(long id);
    
    @Query("UPDATE anomaly_records SET resolved = 1, resolvedAt = :resolvedAt, resolution = :resolution WHERE id = :id")
    void resolveAnomaly(long id, long resolvedAt, String resolution);
    
    // =========================================================================
    // TIME-BASED QUERIES
    // =========================================================================
    
    @Query("SELECT * FROM anomaly_records WHERE detectedAt >= :startTime AND detectedAt <= :endTime ORDER BY detectedAt DESC")
    List<AnomalyRecord> getAnomaliesInRange(long startTime, long endTime);
    
    @Query("SELECT * FROM anomaly_records WHERE detectedAt >= :todayStart ORDER BY detectedAt DESC")
    List<AnomalyRecord> getTodaysAnomalies(long todayStart);
    
    // =========================================================================
    // STATISTICS
    // =========================================================================
    
    @Query("SELECT COUNT(*) FROM anomaly_records")
    int getTotalCount();
    
    @Query("SELECT COUNT(*) FROM anomaly_records WHERE detectedAt >= :startTime")
    int getCountSince(long startTime);
    
    @Query("SELECT COUNT(*) FROM anomaly_records WHERE severity = 'critical'")
    int getCriticalCount();
    
    @Query("SELECT COUNT(*) FROM anomaly_records WHERE resolved = 0")
    int getUnresolvedCount();
    
    @Query("SELECT anomalyType, COUNT(*) as count FROM anomaly_records GROUP BY anomalyType ORDER BY count DESC")
    List<AnomalyTypeCount> getCountByType();
    
    // =========================================================================
    // CLEANUP
    // =========================================================================
    
    @Query("DELETE FROM anomaly_records WHERE detectedAt < :beforeTime")
    void deleteOlderThan(long beforeTime);
    
    @Query("DELETE FROM anomaly_records WHERE resolved = 1 AND resolvedAt < :beforeTime")
    void deleteOldResolved(long beforeTime);
    
    // =========================================================================
    // DUPLICATE DETECTION
    // =========================================================================
    
    @Query("SELECT * FROM anomaly_records WHERE anomalyType = :type AND sourceApp = :packageName AND detectedAt >= :since ORDER BY detectedAt DESC LIMIT 1")
    AnomalyRecord findSimilarRecent(String type, String packageName, long since);
    
    @Query("SELECT * FROM anomaly_records WHERE anomalyType = :type AND metricName = :metric AND detectedAt >= :since ORDER BY detectedAt DESC LIMIT 1")
    AnomalyRecord findSimilarByMetric(String type, String metric, long since);
    
    // =========================================================================
    // HELPER CLASSES
    // =========================================================================
    
    class AppAnomalyCount {
        public String sourceApp;
        public int count;
    }
    
    class AnomalyTypeCount {
        public String anomalyType;
        public int count;
    }
}
