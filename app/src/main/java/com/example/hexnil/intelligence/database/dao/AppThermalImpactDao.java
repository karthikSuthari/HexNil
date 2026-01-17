package com.example.hexnil.intelligence.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.intelligence.database.entities.AppThermalImpact;

import java.util.List;

/**
 * DAO for App Thermal Impact operations.
 * Part of the isolated App Intelligence module.
 */
@Dao
public interface AppThermalImpactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(AppThermalImpact impact);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<AppThermalImpact> impacts);

    @Update
    void update(AppThermalImpact impact);

    @Delete
    void delete(AppThermalImpact impact);

    // =========================================================================
    // LOOKUP QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_thermal_impact WHERE id = :id")
    AppThermalImpact getById(long id);

    @Query("SELECT * FROM ai_app_thermal_impact WHERE session_id = :sessionId LIMIT 1")
    AppThermalImpact getBySessionId(String sessionId);

    @Query("SELECT * FROM ai_app_thermal_impact ORDER BY period_start DESC")
    List<AppThermalImpact> getAll();

    // =========================================================================
    // PER-APP QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_thermal_impact WHERE package_name = :packageName ORDER BY period_start DESC")
    List<AppThermalImpact> getByPackageName(String packageName);

    @Query("SELECT * FROM ai_app_thermal_impact WHERE package_name = :packageName ORDER BY period_start DESC LIMIT 1")
    AppThermalImpact getLatestByPackage(String packageName);

    @Query("SELECT * FROM ai_app_thermal_impact WHERE package_name = :packageName AND period_start >= :startTime AND period_end <= :endTime ORDER BY period_start DESC")
    List<AppThermalImpact> getByPackageInRange(String packageName, long startTime, long endTime);

    // =========================================================================
    // TIME-BASED QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_thermal_impact WHERE period_start >= :startTime ORDER BY max_temperature_c DESC")
    List<AppThermalImpact> getImpactsSince(long startTime);

    @Query("SELECT * FROM ai_app_thermal_impact WHERE period_start >= :startTime AND period_end <= :endTime ORDER BY max_temperature_c DESC")
    List<AppThermalImpact> getImpactsInRange(long startTime, long endTime);

    // =========================================================================
    // THERMAL SPIKE QUERIES (AI-READY)
    // =========================================================================

    @Query("SELECT * FROM ai_app_thermal_impact " +
            "WHERE period_start >= :since " +
            "AND (thermal_spike_count > 0 OR crossed_warning_threshold = 1) " +
            "ORDER BY thermal_spike_count DESC, max_temperature_c DESC")
    List<AppThermalImpact> getThermalSpikeRecords(long since);

    @Query("SELECT * FROM ai_app_thermal_impact " +
            "WHERE period_start >= :since " +
            "AND crossed_critical_threshold = 1 " +
            "ORDER BY max_temperature_c DESC")
    List<AppThermalImpact> getCriticalThermalRecords(long since);

    @Query("SELECT * FROM ai_app_thermal_impact " +
            "WHERE period_start >= :since " +
            "ORDER BY thermal_impact_score DESC LIMIT :limit")
    List<AppThermalImpact> getHighestImpactApps(long since, int limit);

    // =========================================================================
    // AGGREGATION QUERIES (AI-READY)
    // =========================================================================

    @Query("SELECT package_name, COUNT(*) as spike_count, MAX(max_temperature_c) as peak_temp, " +
            "SUM(sustained_heat_duration_ms) as total_heat_duration, AVG(thermal_impact_score) as avg_score " +
            "FROM ai_app_thermal_impact " +
            "WHERE period_start >= :since AND (thermal_spike_count > 0 OR crossed_warning_threshold = 1) " +
            "GROUP BY package_name ORDER BY spike_count DESC, peak_temp DESC LIMIT :limit")
    List<AppThermalSummary> getThermalSpikeSummary(long since, int limit);

    @Query("SELECT package_name, AVG(avg_temperature_c) as avg_temp, MAX(max_temperature_c) as peak_temp, " +
            "AVG(temperature_rise_c) as avg_rise, SUM(duration_ms) as total_duration " +
            "FROM ai_app_thermal_impact " +
            "WHERE period_start >= :since GROUP BY package_name ORDER BY avg_temp DESC LIMIT :limit")
    List<AppTemperatureSummary> getTemperatureSummary(long since, int limit);

    @Query("SELECT AVG(max_temperature_c) FROM ai_app_thermal_impact WHERE package_name = :packageName AND period_start >= :since")
    float getAverageMaxTemp(String packageName, long since);

    @Query("SELECT MAX(max_temperature_c) FROM ai_app_thermal_impact WHERE package_name = :packageName AND period_start >= :since")
    float getPeakTemp(String packageName, long since);

    @Query("SELECT AVG(thermal_impact_score) FROM ai_app_thermal_impact WHERE package_name = :packageName AND period_start >= :since")
    float getAverageThermalScore(String packageName, long since);

    // =========================================================================
    // HEAT SOURCE QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_thermal_impact WHERE primary_heat_source = :source AND period_start >= :since ORDER BY thermal_impact_score DESC")
    List<AppThermalImpact> getByHeatSource(String source, long since);

    @Query("SELECT * FROM ai_app_thermal_impact WHERE thermal_risk_level = :riskLevel AND period_start >= :since ORDER BY max_temperature_c DESC")
    List<AppThermalImpact> getByRiskLevel(String riskLevel, long since);

    // =========================================================================
    // THRESHOLD QUERIES
    // =========================================================================

    @Query("SELECT * FROM ai_app_thermal_impact WHERE max_temperature_c > :threshold AND period_start >= :since ORDER BY max_temperature_c DESC")
    List<AppThermalImpact> getHighTempRecords(float threshold, long since);

    @Query("SELECT * FROM ai_app_thermal_impact WHERE temperature_rise_c > :threshold AND period_start >= :since ORDER BY temperature_rise_c DESC")
    List<AppThermalImpact> getHighRiseRecords(float threshold, long since);

    // =========================================================================
    // MAINTENANCE
    // =========================================================================

    @Query("DELETE FROM ai_app_thermal_impact WHERE period_start < :before")
    void deleteOlderThan(long before);

    @Query("SELECT COUNT(*) FROM ai_app_thermal_impact WHERE package_name = :packageName")
    int getRecordCount(String packageName);

    @Query("SELECT COUNT(*) FROM ai_app_thermal_impact WHERE package_name = :packageName AND thermal_spike_count > 0")
    int getSpikeRecordCount(String packageName);

    // =========================================================================
    // HELPER CLASSES
    // =========================================================================

    class AppThermalSummary {
        public String package_name;
        public int spike_count;
        public float peak_temp;
        public long total_heat_duration;
        public float avg_score;
    }

    class AppTemperatureSummary {
        public String package_name;
        public float avg_temp;
        public float peak_temp;
        public float avg_rise;
        public long total_duration;
    }
}
