package com.example.hexnil.intelligence.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.intelligence.database.entities.AppBatteryAttribution;

import java.util.List;

/**
 * DAO for App Battery Attribution operations.
 * Part of the isolated App Intelligence module.
 */
@Dao
public interface AppBatteryAttributionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AppBatteryAttribution attribution);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AppBatteryAttribution> attributions);

    @Update
    void update(AppBatteryAttribution attribution);

    @Delete
    void delete(AppBatteryAttribution attribution);

    // =========================================================================
    // LOOKUP QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_battery_attribution WHERE id = :id")
    AppBatteryAttribution getById(long id);

    @Query("SELECT * FROM ai_app_battery_attribution WHERE session_id = :sessionId LIMIT 1")
    AppBatteryAttribution getBySessionId(String sessionId);

    @Query("SELECT * FROM ai_app_battery_attribution ORDER BY period_start DESC")
    List<AppBatteryAttribution> getAll();

    // =========================================================================
    // PER-APP QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_battery_attribution WHERE package_name = :packageName ORDER BY period_start DESC")
    List<AppBatteryAttribution> getByPackageName(String packageName);

    @Query("SELECT * FROM ai_app_battery_attribution WHERE package_name = :packageName ORDER BY period_start DESC LIMIT 1")
    AppBatteryAttribution getLatestByPackage(String packageName);

    @Query("SELECT * FROM ai_app_battery_attribution WHERE package_name = :packageName AND period_start >= :startTime AND period_end <= :endTime ORDER BY period_start DESC")
    List<AppBatteryAttribution> getByPackageInRange(String packageName, long startTime, long endTime);

    // =========================================================================
    // TIME-BASED QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_battery_attribution WHERE period_start >= :startTime ORDER BY battery_percent_used DESC")
    List<AppBatteryAttribution> getAttributionsSince(long startTime);

    @Query("SELECT * FROM ai_app_battery_attribution WHERE period_start >= :startTime AND period_end <= :endTime ORDER BY battery_percent_used DESC")
    List<AppBatteryAttribution> getAttributionsInRange(long startTime, long endTime);

    // =========================================================================
    // RANKING QUERIES (AI-READY)
    // =========================================================================

    @Query("SELECT * FROM ai_app_battery_attribution WHERE period_start >= :since ORDER BY battery_percent_used DESC LIMIT :limit")
    List<AppBatteryAttribution> getTopDrainers(long since, int limit);

    @Query("SELECT * FROM ai_app_battery_attribution WHERE period_start >= :since ORDER BY drain_rate_per_hour DESC LIMIT :limit")
    List<AppBatteryAttribution> getHighestDrainRates(long since, int limit);

    @Query("SELECT * FROM ai_app_battery_attribution WHERE period_start >= :since ORDER BY drain_score DESC LIMIT :limit")
    List<AppBatteryAttribution> getWorstEfficiency(long since, int limit);

    @Query("SELECT * FROM ai_app_battery_attribution WHERE period_start >= :since ORDER BY background_abuse_score DESC LIMIT :limit")
    List<AppBatteryAttribution> getBackgroundAbusers(long since, int limit);

    // =========================================================================
    // AGGREGATION QUERIES (AI-READY)
    // =========================================================================

    @Query("SELECT package_name, SUM(battery_percent_used) as total_drain, AVG(drain_rate_per_hour) as avg_rate, COUNT(*) as records "
            +
            "FROM ai_app_battery_attribution WHERE period_start >= :since GROUP BY package_name ORDER BY total_drain DESC LIMIT :limit")
    List<AppDrainSummary> getAppDrainSummary(long since, int limit);

    @Query("SELECT SUM(battery_percent_used) FROM ai_app_battery_attribution WHERE package_name = :packageName AND period_start >= :since")
    float getTotalDrainByPackage(String packageName, long since);

    @Query("SELECT AVG(drain_rate_per_hour) FROM ai_app_battery_attribution WHERE package_name = :packageName AND period_start >= :since")
    float getAverageDrainRateByPackage(String packageName, long since);

    @Query("SELECT AVG(efficiency_score) FROM ai_app_battery_attribution WHERE package_name = :packageName AND period_start >= :since")
    float getAverageEfficiencyByPackage(String packageName, long since);

    // =========================================================================
    // OVERNIGHT DRAIN ANALYSIS (AI-READY)
    // =========================================================================

    @Query("SELECT * FROM ai_app_battery_attribution " +
            "WHERE period_start >= :overnightStart AND period_end <= :overnightEnd " +
            "AND screen_state = 'off' " +
            "ORDER BY battery_percent_used DESC")
    List<AppBatteryAttribution> getOvernightDrainers(long overnightStart, long overnightEnd);

    @Query("SELECT package_name, SUM(battery_percent_used) as total_drain, AVG(drain_rate_per_hour) as avg_rate, SUM(period_end - period_start) as total_background_ms "
            +
            "FROM ai_app_battery_attribution " +
            "WHERE period_start >= :overnightStart AND period_end <= :overnightEnd AND screen_state = 'off' " +
            "GROUP BY package_name ORDER BY total_drain DESC LIMIT :limit")
    List<OvernightDrainSummary> getOvernightDrainSummary(long overnightStart, long overnightEnd, int limit);

    // =========================================================================
    // CONTEXT-BASED QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_battery_attribution WHERE charging_state = :chargingState AND period_start >= :since ORDER BY battery_percent_used DESC")
    List<AppBatteryAttribution> getByChargingState(String chargingState, long since);

    @Query("SELECT * FROM ai_app_battery_attribution WHERE network_type = :networkType AND period_start >= :since ORDER BY battery_percent_used DESC")
    List<AppBatteryAttribution> getByNetworkType(String networkType, long since);

    @Query("SELECT * FROM ai_app_battery_attribution WHERE screen_state = :screenState AND period_start >= :since ORDER BY battery_percent_used DESC")
    List<AppBatteryAttribution> getByScreenState(String screenState, long since);

    // =========================================================================
    // THRESHOLD QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_battery_attribution WHERE battery_percent_used > :threshold AND period_start >= :since ORDER BY battery_percent_used DESC")
    List<AppBatteryAttribution> getHighDrainRecords(float threshold, long since);

    @Query("SELECT * FROM ai_app_battery_attribution WHERE drain_score > :threshold AND period_start >= :since ORDER BY drain_score DESC")
    List<AppBatteryAttribution> getProblematicRecords(float threshold, long since);

    // =========================================================================
    // MAINTENANCE
    // =========================================================================

    @Query("DELETE FROM ai_app_battery_attribution WHERE period_start < :before")
    void deleteOlderThan(long before);

    @Query("SELECT COUNT(*) FROM ai_app_battery_attribution WHERE package_name = :packageName")
    int getRecordCount(String packageName);

    // =========================================================================
    // HELPER CLASSES
    // =========================================================================

    class AppDrainSummary {
        public String package_name;
        public float total_drain;
        public float avg_rate;
        public int records;
    }

    class OvernightDrainSummary {
        public String package_name;
        public float total_drain;
        public float avg_rate;
        public long total_background_ms;
    }
}
