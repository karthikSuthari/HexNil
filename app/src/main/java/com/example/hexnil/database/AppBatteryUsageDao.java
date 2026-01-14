package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.AppBatteryUsage;

import java.util.List;

@Dao
public interface AppBatteryUsageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AppBatteryUsage usage);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AppBatteryUsage> usages);
    
    @Update
    void update(AppBatteryUsage usage);
    
    @Delete
    void delete(AppBatteryUsage usage);
    
    @Query("SELECT * FROM app_battery_usage WHERE packageName = :packageName ORDER BY timestamp DESC LIMIT 1")
    AppBatteryUsage getLatestByPackage(String packageName);
    
    @Query("SELECT * FROM app_battery_usage WHERE timestamp >= :startTime ORDER BY batteryPercentUsed DESC")
    List<AppBatteryUsage> getUsageSince(long startTime);
    
    @Query("SELECT * FROM app_battery_usage WHERE timestamp >= :startTime ORDER BY batteryPercentUsed DESC LIMIT :limit")
    List<AppBatteryUsage> getTopDrainers(long startTime, int limit);
    
    @Query("SELECT * FROM app_battery_usage WHERE isSystemApp = 1 AND timestamp >= :startTime ORDER BY batteryPercentUsed DESC")
    List<AppBatteryUsage> getSystemAppUsage(long startTime);
    
    @Query("SELECT * FROM app_battery_usage WHERE isSystemApp = 0 AND timestamp >= :startTime ORDER BY batteryPercentUsed DESC")
    List<AppBatteryUsage> getUserAppUsage(long startTime);
    
    @Query("SELECT * FROM app_battery_usage WHERE hasExcessiveBackground = 1 AND timestamp >= :startTime")
    List<AppBatteryUsage> getExcessiveBackgroundApps(long startTime);
    
    @Query("SELECT * FROM app_battery_usage WHERE efficiencyRating = 'problematic' AND timestamp >= :startTime")
    List<AppBatteryUsage> getProblematicApps(long startTime);
    
    @Query("SELECT * FROM app_battery_usage WHERE needsRestriction = 1 AND timestamp >= :startTime")
    List<AppBatteryUsage> getAppsNeedingRestriction(long startTime);
    
    @Query("SELECT SUM(batteryPercentUsed) FROM app_battery_usage WHERE isSystemApp = 1 AND timestamp >= :startTime")
    float getSystemAppsTotalDrain(long startTime);
    
    @Query("SELECT SUM(batteryPercentUsed) FROM app_battery_usage WHERE isSystemApp = 0 AND timestamp >= :startTime")
    float getUserAppsTotalDrain(long startTime);
    
    @Query("SELECT AVG(drainPerHour) FROM app_battery_usage WHERE appCategory = :category AND timestamp >= :startTime")
    float getCategoryAverageDrain(String category, long startTime);
    
    @Query("DELETE FROM app_battery_usage WHERE timestamp < :beforeTime")
    void deleteOlderThan(long beforeTime);
    
    @Query("SELECT DISTINCT packageName FROM app_battery_usage")
    List<String> getAllTrackedPackages();
    
    // =========================================================================
    // PER-APP HISTORY QUERIES (Records with Date/Time)
    // =========================================================================
    
    /**
     * Get complete history for a specific app, ordered by timestamp
     */
    @Query("SELECT * FROM app_battery_usage WHERE packageName = :packageName ORDER BY timestamp DESC")
    List<AppBatteryUsage> getAppHistory(String packageName);
    
    /**
     * Get app history within a time range
     */
    @Query("SELECT * FROM app_battery_usage WHERE packageName = :packageName AND timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    List<AppBatteryUsage> getAppHistoryInRange(String packageName, long startTime, long endTime);
    
    /**
     * Get app history for a specific day (start of day to end of day)
     */
    @Query("SELECT * FROM app_battery_usage WHERE packageName = :packageName AND timestamp >= :dayStart AND timestamp < :dayEnd ORDER BY timestamp ASC")
    List<AppBatteryUsage> getAppHistoryForDay(String packageName, long dayStart, long dayEnd);
    
    /**
     * Get all apps' usage for a specific time range, grouped by app
     */
    @Query("SELECT * FROM app_battery_usage WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY packageName, timestamp DESC")
    List<AppBatteryUsage> getAllAppHistoryInRange(long startTime, long endTime);
    
    /**
     * Get recent records for all apps with timestamps
     */
    @Query("SELECT * FROM app_battery_usage WHERE timestamp >= :startTime ORDER BY timestamp DESC, packageName")
    List<AppBatteryUsage> getRecentRecordsWithTimestamp(long startTime);
    
    /**
     * Get all records for today
     */
    @Query("SELECT * FROM app_battery_usage WHERE timestamp >= :todayStart ORDER BY packageName, timestamp DESC")
    List<AppBatteryUsage> getTodaysRecords(long todayStart);
    
    /**
     * Get total usage stats per app (aggregated)
     */
    @Query("SELECT packageName, SUM(foregroundTime) as totalForeground, SUM(backgroundTime) as totalBackground, " +
           "SUM(batteryPercentUsed) as totalDrain, COUNT(*) as recordCount " +
           "FROM app_battery_usage WHERE timestamp >= :startTime GROUP BY packageName ORDER BY totalDrain DESC")
    List<AppUsageSummary> getAppUsageSummary(long startTime);
    
    /**
     * Helper class for usage summary
     */
    class AppUsageSummary {
        public String packageName;
        public long totalForeground;
        public long totalBackground;
        public float totalDrain;
        public int recordCount;
    }
}
