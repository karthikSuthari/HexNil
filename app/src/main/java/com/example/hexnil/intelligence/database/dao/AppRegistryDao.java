package com.example.hexnil.intelligence.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.intelligence.database.entities.AppRegistry;

import java.util.List;

/**
 * DAO for App Registry operations.
 * Part of the isolated App Intelligence module.
 */
@Dao
public interface AppRegistryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AppRegistry registry);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AppRegistry> registries);

    @Update
    void update(AppRegistry registry);

    @Delete
    void delete(AppRegistry registry);

    // =========================================================================
    // LOOKUP QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_registry WHERE package_name = :packageName LIMIT 1")
    AppRegistry getByPackageName(String packageName);

    @Query("SELECT * FROM ai_app_registry WHERE id = :id")
    AppRegistry getById(long id);

    @Query("SELECT * FROM ai_app_registry ORDER BY last_seen_at DESC")
    List<AppRegistry> getAll();

    @Query("SELECT * FROM ai_app_registry WHERE tracking_enabled = 1 ORDER BY last_seen_at DESC")
    List<AppRegistry> getAllEnabled();

    // =========================================================================
    // CATEGORY QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_registry WHERE app_category = :category ORDER BY total_battery_used_pct DESC")
    List<AppRegistry> getByCategory(String category);

    @Query("SELECT * FROM ai_app_registry WHERE is_system_app = 1 ORDER BY total_battery_used_pct DESC")
    List<AppRegistry> getSystemApps();

    @Query("SELECT * FROM ai_app_registry WHERE is_system_app = 0 ORDER BY total_battery_used_pct DESC")
    List<AppRegistry> getUserApps();

    // =========================================================================
    // RANKING QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_registry ORDER BY total_battery_used_pct DESC LIMIT :limit")
    List<AppRegistry> getTopDrainApps(int limit);

    @Query("SELECT * FROM ai_app_registry ORDER BY avg_drain_per_hour DESC LIMIT :limit")
    List<AppRegistry> getHighestDrainRateApps(int limit);

    @Query("SELECT * FROM ai_app_registry ORDER BY anomaly_count DESC LIMIT :limit")
    List<AppRegistry> getMostProblematicApps(int limit);

    @Query("SELECT * FROM ai_app_registry ORDER BY total_sessions DESC LIMIT :limit")
    List<AppRegistry> getMostUsedApps(int limit);

    // =========================================================================
    // AGGREGATE QUERIES
    // =========================================================================

    @Query("SELECT SUM(total_battery_used_pct) FROM ai_app_registry WHERE is_system_app = 1")
    float getSystemAppsTotalDrain();

    @Query("SELECT SUM(total_battery_used_pct) FROM ai_app_registry WHERE is_system_app = 0")
    float getUserAppsTotalDrain();

    @Query("SELECT COUNT(*) FROM ai_app_registry")
    int getTotalAppCount();

    @Query("SELECT COUNT(*) FROM ai_app_registry WHERE anomaly_count > 0")
    int getProblematicAppCount();

    @Query("SELECT AVG(avg_drain_per_hour) FROM ai_app_registry WHERE app_category = :category")
    float getCategoryAverageDrainRate(String category);

    // =========================================================================
    // ACTIVITY QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_registry WHERE last_seen_at >= :since ORDER BY last_seen_at DESC")
    List<AppRegistry> getRecentlyActiveApps(long since);

    @Query("SELECT * FROM ai_app_registry WHERE last_seen_at < :before ORDER BY last_seen_at DESC")
    List<AppRegistry> getInactiveApps(long before);

    // =========================================================================
    // MAINTENANCE
    // =========================================================================

    @Query("UPDATE ai_app_registry SET tracking_enabled = :enabled WHERE package_name = :packageName")
    void setTrackingEnabled(String packageName, boolean enabled);

    @Query("UPDATE ai_app_registry SET anomaly_count = anomaly_count + 1, updated_at = :timestamp WHERE package_name = :packageName")
    void incrementAnomalyCount(String packageName, long timestamp);

    @Query("DELETE FROM ai_app_registry WHERE last_seen_at < :before AND total_sessions = 0")
    void deleteInactiveApps(long before);

    @Query("SELECT DISTINCT package_name FROM ai_app_registry")
    List<String> getAllPackageNames();

    @Query("SELECT EXISTS(SELECT 1 FROM ai_app_registry WHERE package_name = :packageName)")
    boolean exists(String packageName);
}
