package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.RegressionEvent;

import java.util.List;

/**
 * DAO for RegressionEvent entity.
 * Provides CRUD operations and regression tracking queries.
 */
@Dao
public interface RegressionEventDao {

    // =========================================================================
    // Insert/Update Operations
    // =========================================================================

    @Insert
    long insert(RegressionEvent event);

    @Insert
    void insertAll(List<RegressionEvent> events);

    @Update
    void update(RegressionEvent event);

    // =========================================================================
    // Query Operations
    // =========================================================================

    @Query("SELECT * FROM regression_events ORDER BY detectedAt DESC LIMIT 1")
    RegressionEvent getLatest();

    @Query("SELECT * FROM regression_events WHERE id = :id")
    RegressionEvent getById(long id);

    @Query("SELECT * FROM regression_events WHERE detectedAt >= :since ORDER BY detectedAt DESC")
    List<RegressionEvent> getSince(long since);

    @Query("SELECT * FROM regression_events ORDER BY detectedAt DESC LIMIT :limit")
    List<RegressionEvent> getRecent(int limit);

    // =========================================================================
    // Active (Unresolved) Regressions
    // =========================================================================

    @Query("SELECT * FROM regression_events WHERE isResolved = 0 ORDER BY detectedAt DESC")
    List<RegressionEvent> getActiveRegressions();

    @Query("SELECT * FROM regression_events WHERE isResolved = 0 AND packageName = :packageName ORDER BY detectedAt DESC")
    List<RegressionEvent> getActiveByPackage(String packageName);

    @Query("SELECT COUNT(*) FROM regression_events WHERE isResolved = 0")
    int getActiveCount();

    @Query("SELECT COUNT(*) FROM regression_events WHERE isResolved = 0 AND packageName = :packageName")
    int getActiveCountByPackage(String packageName);

    // =========================================================================
    // Regression Type Queries
    // =========================================================================

    @Query("SELECT * FROM regression_events WHERE regressionType = :type AND isResolved = 0 ORDER BY detectedAt DESC")
    List<RegressionEvent> getActiveByType(String type);

    @Query("SELECT * FROM regression_events WHERE regressionType = :type ORDER BY detectedAt DESC")
    List<RegressionEvent> getByType(String type);

    @Query("SELECT COUNT(*) FROM regression_events WHERE regressionType = :type AND isResolved = 0")
    int getActiveCountByType(String type);

    // =========================================================================
    // Severity Queries
    // =========================================================================

    @Query("SELECT * FROM regression_events WHERE severity IN ('high', 'critical') AND isResolved = 0 ORDER BY detectedAt DESC")
    List<RegressionEvent> getHighSeverityActive();

    @Query("SELECT * FROM regression_events WHERE severity = :severity AND isResolved = 0 ORDER BY detectedAt DESC")
    List<RegressionEvent> getActiveBySeverity(String severity);

    @Query("SELECT COUNT(*) FROM regression_events WHERE severity IN ('high', 'critical') AND isResolved = 0")
    int getHighSeverityActiveCount();

    // =========================================================================
    // Package-Specific Queries
    // =========================================================================

    @Query("SELECT * FROM regression_events WHERE packageName = :packageName ORDER BY detectedAt DESC")
    List<RegressionEvent> getByPackage(String packageName);

    @Query("SELECT * FROM regression_events WHERE packageName = :packageName AND detectedAt >= :since ORDER BY detectedAt DESC")
    List<RegressionEvent> getByPackageSince(String packageName, long since);

    // =========================================================================
    // Trigger-Based Queries
    // =========================================================================

    @Query("SELECT * FROM regression_events WHERE triggerEvent = :triggerEvent AND isResolved = 0 ORDER BY detectedAt DESC")
    List<RegressionEvent> getActiveByTrigger(String triggerEvent);

    @Query("SELECT * FROM regression_events WHERE triggerEvent = 'app_update' AND detectedAt >= :since ORDER BY detectedAt DESC")
    List<RegressionEvent> getAppUpdateRegressions(long since);

    @Query("SELECT * FROM regression_events WHERE triggerEvent = 'system_update' AND detectedAt >= :since ORDER BY detectedAt DESC")
    List<RegressionEvent> getSystemUpdateRegressions(long since);

    @Query("SELECT * FROM regression_events WHERE triggerEvent = 'long_term_decay' AND isResolved = 0 ORDER BY detectedAt DESC")
    List<RegressionEvent> getLongTermDecayRegressions();

    // =========================================================================
    // Resolution Queries
    // =========================================================================

    @Query("SELECT * FROM regression_events WHERE isResolved = 1 ORDER BY resolvedAt DESC LIMIT :limit")
    List<RegressionEvent> getRecentlyResolved(int limit);

    @Query("UPDATE regression_events SET isResolved = 1, resolvedAt = :resolvedAt, resolutionMethod = :method, resolvedValue = :value WHERE id = :id")
    void markResolved(long id, long resolvedAt, String method, float value);

    @Query("UPDATE regression_events SET userNotified = 1 WHERE id = :id")
    void markNotified(long id);

    @Query("UPDATE regression_events SET userDismissed = 1 WHERE id = :id")
    void markDismissed(long id);

    // =========================================================================
    // User Attention Queries
    // =========================================================================

    @Query("SELECT * FROM regression_events WHERE isResolved = 0 AND userDismissed = 0 AND severity IN ('high', 'critical') ORDER BY detectedAt DESC")
    List<RegressionEvent> getRequiringAttention();

    @Query("SELECT * FROM regression_events WHERE isResolved = 0 AND userNotified = 0 ORDER BY detectedAt DESC")
    List<RegressionEvent> getUnnotified();

    // =========================================================================
    // Confidence Queries
    // =========================================================================

    @Query("SELECT * FROM regression_events WHERE confidenceScore >= 0.8 AND isResolved = 0 ORDER BY detectedAt DESC")
    List<RegressionEvent> getHighConfidenceActive();

    @Query("SELECT AVG(confidenceScore) FROM regression_events WHERE detectedAt >= :since")
    float getAverageConfidence(long since);

    // =========================================================================
    // Deduplication
    // =========================================================================

    @Query("SELECT EXISTS(SELECT 1 FROM regression_events WHERE packageName = :packageName AND regressionType = :type AND isResolved = 0 LIMIT 1)")
    boolean hasActiveRegression(String packageName, String type);

    // =========================================================================
    // Distinct Queries
    // =========================================================================

    @Query("SELECT DISTINCT packageName FROM regression_events WHERE isResolved = 0 AND packageName IS NOT NULL")
    List<String> getDistinctActivePackages();

    @Query("SELECT DISTINCT regressionType FROM regression_events WHERE isResolved = 0")
    List<String> getDistinctActiveTypes();

    // =========================================================================
    // Cleanup Operations
    // =========================================================================

    @Query("DELETE FROM regression_events WHERE detectedAt < :before AND isResolved = 1")
    int deleteResolvedOlderThan(long before);

    @Query("DELETE FROM regression_events")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM regression_events")
    int getCount();
}
