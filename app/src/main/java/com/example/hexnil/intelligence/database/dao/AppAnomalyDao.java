package com.example.hexnil.intelligence.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.intelligence.database.entities.AppAnomaly;

import java.util.List;

/**
 * DAO for App Anomaly operations.
 * Part of the isolated App Intelligence module.
 * Does NOT interact with the core anomaly_records table.
 */
@Dao
public interface AppAnomalyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AppAnomaly anomaly);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AppAnomaly> anomalies);

    @Update
    void update(AppAnomaly anomaly);

    @Delete
    void delete(AppAnomaly anomaly);

    // =========================================================================
    // LOOKUP QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_anomalies WHERE id = :id")
    AppAnomaly getById(long id);

    @Query("SELECT * FROM ai_app_anomalies WHERE session_id = :sessionId")
    List<AppAnomaly> getBySessionId(String sessionId);

    @Query("SELECT * FROM ai_app_anomalies ORDER BY detected_at DESC")
    List<AppAnomaly> getAll();

    @Query("SELECT * FROM ai_app_anomalies ORDER BY detected_at DESC LIMIT :limit")
    List<AppAnomaly> getRecent(int limit);

    // =========================================================================
    // PER-APP QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_anomalies WHERE package_name = :packageName ORDER BY detected_at DESC")
    List<AppAnomaly> getByPackageName(String packageName);

    @Query("SELECT * FROM ai_app_anomalies WHERE package_name = :packageName ORDER BY detected_at DESC LIMIT 1")
    AppAnomaly getLatestByPackage(String packageName);

    @Query("SELECT * FROM ai_app_anomalies WHERE package_name = :packageName AND detected_at >= :since ORDER BY detected_at DESC")
    List<AppAnomaly> getByPackageSince(String packageName, long since);

    @Query("SELECT COUNT(*) FROM ai_app_anomalies WHERE package_name = :packageName AND detected_at >= :since")
    int getAnomalyCountByPackage(String packageName, long since);

    // =========================================================================
    // TYPE-BASED QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_anomalies WHERE anomaly_type = :type ORDER BY detected_at DESC")
    List<AppAnomaly> getByType(String type);

    @Query("SELECT * FROM ai_app_anomalies WHERE anomaly_type = :type AND detected_at >= :since ORDER BY detected_at DESC")
    List<AppAnomaly> getByTypeSince(String type, long since);

    @Query("SELECT * FROM ai_app_anomalies WHERE package_name = :packageName AND anomaly_type = :type ORDER BY detected_at DESC")
    List<AppAnomaly> getByPackageAndType(String packageName, String type);

    // =========================================================================
    // SEVERITY QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_anomalies WHERE severity = :severity ORDER BY detected_at DESC")
    List<AppAnomaly> getBySeverity(String severity);

    @Query("SELECT * FROM ai_app_anomalies WHERE severity IN ('critical', 'high') ORDER BY detected_at DESC")
    List<AppAnomaly> getHighPriority();

    @Query("SELECT * FROM ai_app_anomalies WHERE severity IN ('critical', 'high') AND resolved = 0 ORDER BY detected_at DESC")
    List<AppAnomaly> getUnresolvedHighPriority();

    @Query("SELECT * FROM ai_app_anomalies WHERE severity = 'critical' AND resolved = 0 ORDER BY detected_at DESC")
    List<AppAnomaly> getCriticalUnresolved();

    // =========================================================================
    // STATUS QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_anomalies WHERE resolved = 0 ORDER BY detected_at DESC")
    List<AppAnomaly> getUnresolved();

    @Query("SELECT * FROM ai_app_anomalies WHERE resolved = 1 ORDER BY resolved_at DESC")
    List<AppAnomaly> getResolved();

    @Query("SELECT * FROM ai_app_anomalies WHERE acknowledged = 0 ORDER BY detected_at DESC")
    List<AppAnomaly> getUnacknowledged();

    @Query("SELECT * FROM ai_app_anomalies WHERE package_name = :packageName AND resolved = 0 ORDER BY detected_at DESC")
    List<AppAnomaly> getUnresolvedByPackage(String packageName);

    // =========================================================================
    // TIME-BASED QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_anomalies WHERE detected_at >= :since ORDER BY detected_at DESC")
    List<AppAnomaly> getAnomaliesSince(long since);

    @Query("SELECT * FROM ai_app_anomalies WHERE detected_at >= :startTime AND detected_at <= :endTime ORDER BY detected_at DESC")
    List<AppAnomaly> getAnomaliesInRange(long startTime, long endTime);

    // =========================================================================
    // AGGREGATION QUERIES (AI-READY)
    // =========================================================================

    @Query("SELECT package_name, COUNT(*) as anomaly_count, " +
            "SUM(CASE WHEN severity = 'critical' THEN 1 ELSE 0 END) as critical_count, " +
            "SUM(CASE WHEN severity = 'high' THEN 1 ELSE 0 END) as high_count, " +
            "AVG(confidence_score) as avg_confidence " +
            "FROM ai_app_anomalies WHERE detected_at >= :since " +
            "GROUP BY package_name ORDER BY anomaly_count DESC LIMIT :limit")
    List<AppAnomalySummary> getAnomalySummary(long since, int limit);

    @Query("SELECT anomaly_type, COUNT(*) as count, " +
            "AVG(confidence_score) as avg_confidence, " +
            "SUM(CASE WHEN resolved = 0 THEN 1 ELSE 0 END) as unresolved_count " +
            "FROM ai_app_anomalies WHERE detected_at >= :since " +
            "GROUP BY anomaly_type ORDER BY count DESC")
    List<AnomalyTypeSummary> getTypeSummary(long since);

    @Query("SELECT COUNT(*) FROM ai_app_anomalies WHERE detected_at >= :since")
    int getTotalAnomalyCount(long since);

    @Query("SELECT COUNT(*) FROM ai_app_anomalies WHERE detected_at >= :since AND resolved = 0")
    int getUnresolvedCount(long since);

    @Query("SELECT COUNT(*) FROM ai_app_anomalies WHERE detected_at >= :since AND severity IN ('critical', 'high')")
    int getHighPriorityCount(long since);

    // =========================================================================
    // PATTERN DETECTION (AI-READY)
    // =========================================================================

    @Query("SELECT package_name FROM ai_app_anomalies " +
            "WHERE detected_at >= :since " +
            "GROUP BY package_name " +
            "HAVING COUNT(*) >= :threshold " +
            "ORDER BY COUNT(*) DESC")
    List<String> getRecurringAnomalyApps(long since, int threshold);

    @Query("SELECT * FROM ai_app_anomalies " +
            "WHERE package_name = :packageName " +
            "AND anomaly_type = :type " +
            "AND detected_at >= :since " +
            "ORDER BY detected_at DESC")
    List<AppAnomaly> getRecurringAnomalies(String packageName, String type, long since);

    // =========================================================================
    // STATUS UPDATES
    // =========================================================================

    @Query("UPDATE ai_app_anomalies SET acknowledged = 1 WHERE id = :id")
    void acknowledge(long id);

    @Query("UPDATE ai_app_anomalies SET resolved = 1, resolved_at = :resolvedAt WHERE id = :id")
    void resolve(long id, long resolvedAt);

    @Query("UPDATE ai_app_anomalies SET acknowledged = 1 WHERE package_name = :packageName AND resolved = 0")
    void acknowledgeAllByPackage(String packageName);

    @Query("UPDATE ai_app_anomalies SET resolved = 1, resolved_at = :resolvedAt WHERE package_name = :packageName AND resolved = 0")
    void resolveAllByPackage(String packageName, long resolvedAt);

    // =========================================================================
    // MAINTENANCE
    // =========================================================================

    @Query("DELETE FROM ai_app_anomalies WHERE detected_at < :before")
    void deleteOlderThan(long before);

    @Query("DELETE FROM ai_app_anomalies WHERE resolved = 1 AND resolved_at < :before")
    void deleteOldResolved(long before);

    // =========================================================================
    // HELPER CLASSES
    // =========================================================================

    class AppAnomalySummary {
        public String package_name;
        public int anomaly_count;
        public int critical_count;
        public int high_count;
        public float avg_confidence;
    }

    class AnomalyTypeSummary {
        public String anomaly_type;
        public int count;
        public float avg_confidence;
        public int unresolved_count;
    }
}
