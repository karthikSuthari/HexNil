package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.AppLaunchMetric;

import java.util.List;

/**
 * DAO for AppLaunchMetric entity.
 * Provides CRUD operations and analysis queries for app launch performance
 * tracking.
 */
@Dao
public interface AppLaunchMetricDao {

    // =========================================================================
    // Insert Operations
    // =========================================================================

    @Insert
    long insert(AppLaunchMetric metric);

    @Insert
    void insertAll(List<AppLaunchMetric> metrics);

    // =========================================================================
    // Query Operations
    // =========================================================================

    @Query("SELECT * FROM app_launch_metrics ORDER BY timestamp DESC LIMIT 1")
    AppLaunchMetric getLatest();

    @Query("SELECT * FROM app_launch_metrics WHERE id = :id")
    AppLaunchMetric getById(long id);

    @Query("SELECT * FROM app_launch_metrics WHERE timestamp >= :since ORDER BY timestamp DESC")
    List<AppLaunchMetric> getSince(long since);

    @Query("SELECT * FROM app_launch_metrics WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    List<AppLaunchMetric> getInRange(long start, long end);

    @Query("SELECT * FROM app_launch_metrics ORDER BY timestamp DESC LIMIT :limit")
    List<AppLaunchMetric> getRecent(int limit);

    // =========================================================================
    // Package-Specific Queries
    // =========================================================================

    @Query("SELECT * FROM app_launch_metrics WHERE packageName = :packageName ORDER BY timestamp DESC")
    List<AppLaunchMetric> getByPackage(String packageName);

    @Query("SELECT * FROM app_launch_metrics WHERE packageName = :packageName AND timestamp >= :since ORDER BY timestamp DESC")
    List<AppLaunchMetric> getByPackageSince(String packageName, long since);

    @Query("SELECT * FROM app_launch_metrics WHERE packageName = :packageName AND timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    List<AppLaunchMetric> getByPackageInRange(String packageName, long start, long end);

    // =========================================================================
    // Launch Type Queries
    // =========================================================================

    @Query("SELECT * FROM app_launch_metrics WHERE launchType = :launchType AND timestamp >= :since ORDER BY timestamp DESC")
    List<AppLaunchMetric> getByTypeSince(String launchType, long since);

    @Query("SELECT * FROM app_launch_metrics WHERE packageName = :packageName AND launchType = :launchType AND timestamp >= :since")
    List<AppLaunchMetric> getByPackageAndType(String packageName, String launchType, long since);

    // =========================================================================
    // Aggregation Queries
    // =========================================================================

    @Query("SELECT AVG(launchDurationMs) FROM app_launch_metrics WHERE launchType = 'cold' AND timestamp >= :since")
    float getAverageColdLaunchSince(long since);

    @Query("SELECT AVG(launchDurationMs) FROM app_launch_metrics WHERE launchType = 'warm' AND timestamp >= :since")
    float getAverageWarmLaunchSince(long since);

    @Query("SELECT AVG(launchDurationMs) FROM app_launch_metrics WHERE launchType = 'hot' AND timestamp >= :since")
    float getAverageHotLaunchSince(long since);

    @Query("SELECT AVG(launchDurationMs) FROM app_launch_metrics WHERE packageName = :packageName AND launchType = 'cold' AND timestamp >= :since")
    float getAverageColdLaunchByPackage(String packageName, long since);

    @Query("SELECT AVG(launchDurationMs) FROM app_launch_metrics WHERE packageName = :packageName AND launchType = 'cold' AND timestamp BETWEEN :start AND :end")
    float getAverageColdLaunchByPackageInRange(String packageName, long start, long end);
    
    @Query("SELECT AVG(launchDurationMs) FROM app_launch_metrics WHERE packageName = :packageName AND launchType = 'warm' AND timestamp BETWEEN :start AND :end")
    float getAverageWarmLaunchByPackageInRange(String packageName, long start, long end);
    
    @Query("SELECT AVG(launchDurationMs) FROM app_launch_metrics WHERE packageName = :packageName AND launchType = 'hot' AND timestamp BETWEEN :start AND :end")
    float getAverageHotLaunchByPackageInRange(String packageName, long start, long end);

    @Query("SELECT MAX(launchDurationMs) FROM app_launch_metrics WHERE timestamp >= :since")
    float getMaxLaunchTimeSince(long since);

    @Query("SELECT MAX(launchDurationMs) FROM app_launch_metrics WHERE packageName = :packageName AND timestamp >= :since")
    float getMaxLaunchTimeByPackage(String packageName, long since);

    @Query("SELECT COUNT(*) FROM app_launch_metrics WHERE timestamp >= :since")
    int getLaunchCountSince(long since);

    @Query("SELECT COUNT(*) FROM app_launch_metrics WHERE packageName = :packageName AND timestamp >= :since")
    int getLaunchCountByPackage(String packageName, long since);

    @Query("SELECT COUNT(*) FROM app_launch_metrics WHERE launchType = 'cold' AND timestamp >= :since")
    int getColdLaunchCountSince(long since);

    @Query("SELECT COUNT(*) FROM app_launch_metrics WHERE packageName = :packageName AND launchType = 'cold' AND timestamp BETWEEN :start AND :end")
    int getColdLaunchCountByPackageInRange(String packageName, long start, long end);

    // =========================================================================
    // Slow Launch Analysis
    // =========================================================================

    @Query("SELECT * FROM app_launch_metrics WHERE launchType = 'cold' AND launchDurationMs > 5000 AND timestamp >= :since")
    List<AppLaunchMetric> getSlowColdLaunches(long since);

    @Query("SELECT * FROM app_launch_metrics WHERE launchType = 'warm' AND launchDurationMs > 2000 AND timestamp >= :since")
    List<AppLaunchMetric> getSlowWarmLaunches(long since);

    @Query("SELECT COUNT(*) FROM app_launch_metrics WHERE " +
            "((launchType = 'cold' AND launchDurationMs > 5000) OR " +
            "(launchType = 'warm' AND launchDurationMs > 2000) OR " +
            "(launchType = 'hot' AND launchDurationMs > 1000)) AND timestamp >= :since")
    int getSlowLaunchCount(long since);
    
    @Query("SELECT COUNT(*) FROM app_launch_metrics WHERE packageName = :packageName AND " +
            "((launchType = 'cold' AND launchDurationMs > 5000) OR " +
            "(launchType = 'warm' AND launchDurationMs > 2000) OR " +
            "(launchType = 'hot' AND launchDurationMs > 1000)) AND timestamp >= :since")
    int getSlowLaunchCountByPackage(String packageName, long since);

    // =========================================================================
    // Distinct Packages
    // =========================================================================

    @Query("SELECT DISTINCT packageName FROM app_launch_metrics WHERE timestamp >= :since")
    List<String> getDistinctPackages(long since);

    // =========================================================================
    // Cleanup Operations
    // =========================================================================

    @Query("DELETE FROM app_launch_metrics WHERE timestamp < :before")
    int deleteOlderThan(long before);

    @Query("DELETE FROM app_launch_metrics")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM app_launch_metrics")
    int getCount();
}
