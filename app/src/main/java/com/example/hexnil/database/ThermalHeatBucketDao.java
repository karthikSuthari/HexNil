package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.ThermalHeatBucket;

import java.util.List;

/**
 * DAO for ThermalHeatBucket with OEM-grade heat map analysis.
 * 
 * Provides:
 * - Heat bucket distribution queries
 * - Time-of-day pattern analysis
 * - Correlation statistics
 * - OEM compliance reporting
 */
@Dao
public interface ThermalHeatBucketDao {
    
    // =====================================================================
    // Basic CRUD
    // =====================================================================
    
    @Insert
    long insert(ThermalHeatBucket bucket);

    @Update
    void update(ThermalHeatBucket bucket);

    @Query("SELECT * FROM thermal_heat_buckets WHERE startTime >= :start ORDER BY startTime ASC")
    List<ThermalHeatBucket> getBucketsSince(long start);

    @Query("SELECT * FROM thermal_heat_buckets WHERE tempBucket = :bucket ORDER BY startTime DESC LIMIT 10")
    List<ThermalHeatBucket> getRecentByBucket(String bucket);
    
    @Query("SELECT * FROM thermal_heat_buckets ORDER BY startTime DESC LIMIT 1")
    ThermalHeatBucket getLatest();
    
    // =====================================================================
    // Bucket distribution queries
    // =====================================================================
    
    @Query("SELECT tempBucket, SUM(durationMs) as totalDuration, SUM(sampleCount) as totalSamples, AVG(avgTemp) as avgTemp, MAX(maxTemp) as maxTemp FROM thermal_heat_buckets WHERE startTime >= :startTime GROUP BY tempBucket ORDER BY tempBucket")
    List<BucketDistribution> getBucketDistribution(long startTime);
    
    @Query("SELECT tempBucket, SUM(durationMs) as totalDuration FROM thermal_heat_buckets WHERE startTime >= :startTime GROUP BY tempBucket")
    List<BucketDuration> getBucketDurations(long startTime);
    
    @Query("SELECT SUM(durationMs) FROM thermal_heat_buckets WHERE tempBucket = :bucket AND startTime >= :startTime")
    long getTotalDurationInBucket(String bucket, long startTime);
    
    @Query("SELECT SUM(durationMs) FROM thermal_heat_buckets WHERE tempBucket IN ('40-45', '>45') AND startTime >= :startTime")
    long getTotalTimeInWarningZone(long startTime);
    
    @Query("SELECT SUM(durationMs) FROM thermal_heat_buckets WHERE tempBucket = '>45' AND startTime >= :startTime")
    long getTotalTimeInDangerZone(long startTime);
    
    // =====================================================================
    // Hourly pattern queries
    // =====================================================================
    
    @Query("SELECT hourBlock, AVG(avgTemp) as avgTemp, SUM(sampleCount) as totalSamples, SUM(durationMs) as totalDuration FROM thermal_heat_buckets WHERE startTime >= :startTime GROUP BY hourBlock ORDER BY hourBlock")
    List<HourlyPattern> getHourlyPatterns(long startTime);
    
    @Query("SELECT hourBlock, tempBucket, SUM(durationMs) as duration FROM thermal_heat_buckets WHERE startTime >= :startTime GROUP BY hourBlock, tempBucket ORDER BY hourBlock, tempBucket")
    List<HourlyBucketBreakdown> getHourlyBucketBreakdown(long startTime);
    
    @Query("SELECT hourBlock FROM thermal_heat_buckets WHERE startTime >= :startTime GROUP BY hourBlock ORDER BY AVG(avgTemp) DESC LIMIT 3")
    List<Integer> getHottestHours(long startTime);
    
    @Query("SELECT hourBlock FROM thermal_heat_buckets WHERE startTime >= :startTime GROUP BY hourBlock ORDER BY AVG(avgTemp) ASC LIMIT 3")
    List<Integer> getCoolestHours(long startTime);
    
    // =====================================================================
    // Charging correlation queries
    // =====================================================================
    
    @Query("SELECT tempBucket, SUM(chargingCount) as chargingSamples, SUM(sampleCount) as totalSamples, AVG(avgTempCharging) as avgTempCharging, AVG(avgTempNotCharging) as avgTempNotCharging FROM thermal_heat_buckets WHERE startTime >= :startTime GROUP BY tempBucket")
    List<BucketChargingCorrelation> getChargingCorrelationByBucket(long startTime);
    
    @Query("SELECT AVG(avgTempCharging) - AVG(avgTempNotCharging) FROM thermal_heat_buckets WHERE startTime >= :startTime AND chargingCount > 0")
    float getChargingTempDelta(long startTime);
    
    @Query("SELECT SUM(fastChargingCount) FROM thermal_heat_buckets WHERE tempBucket IN ('40-45', '>45') AND startTime >= :startTime")
    int getFastChargingHotSamples(long startTime);
    
    // =====================================================================
    // CPU/GPU correlation queries
    // =====================================================================
    
    @Query("SELECT tempBucket, SUM(highCpuCount) as highCpuSamples, AVG(avgCpuLoad) as avgCpuLoad, AVG(avgTempHighCpu) as avgTempHighCpu FROM thermal_heat_buckets WHERE startTime >= :startTime GROUP BY tempBucket")
    List<BucketCpuCorrelation> getCpuCorrelationByBucket(long startTime);
    
    @Query("SELECT tempBucket, SUM(highGpuCount) as highGpuSamples, AVG(avgGpuLoad) as avgGpuLoad, AVG(avgTempHighGpu) as avgTempHighGpu FROM thermal_heat_buckets WHERE startTime >= :startTime GROUP BY tempBucket")
    List<BucketGpuCorrelation> getGpuCorrelationByBucket(long startTime);
    
    @Query("SELECT AVG(avgTempHighCpu) - AVG(avgTemp) FROM thermal_heat_buckets WHERE startTime >= :startTime AND highCpuCount > 0")
    float getCpuTempDelta(long startTime);
    
    // =====================================================================
    // App correlation queries
    // =====================================================================
    
    @Query("SELECT topForegroundApp, SUM(sampleCount) as samples, AVG(avgTemp) as avgTemp, MAX(maxTemp) as maxTemp FROM thermal_heat_buckets WHERE topForegroundApp IS NOT NULL AND startTime >= :startTime GROUP BY topForegroundApp ORDER BY avgTemp DESC LIMIT :limit")
    List<AppBucketStats> getHottestAppsByBucket(int limit, long startTime);
    
    @Query("SELECT topForegroundApp, SUM(durationMs) as totalDuration FROM thermal_heat_buckets WHERE tempBucket IN ('40-45', '>45') AND topForegroundApp IS NOT NULL AND startTime >= :startTime GROUP BY topForegroundApp ORDER BY totalDuration DESC LIMIT :limit")
    List<AppHotDuration> getAppsWithMostHotTime(int limit, long startTime);
    
    // =====================================================================
    // Activity correlation queries
    // =====================================================================
    
    @Query("SELECT SUM(gamingCount) FROM thermal_heat_buckets WHERE tempBucket IN ('40-45', '>45') AND startTime >= :startTime")
    int getGamingHotSamples(long startTime);
    
    @Query("SELECT SUM(cameraCount) FROM thermal_heat_buckets WHERE tempBucket IN ('40-45', '>45') AND startTime >= :startTime")
    int getCameraHotSamples(long startTime);
    
    @Query("SELECT SUM(navigationCount) FROM thermal_heat_buckets WHERE tempBucket IN ('40-45', '>45') AND startTime >= :startTime")
    int getNavigationHotSamples(long startTime);
    
    @Query("SELECT SUM(screenOnCount) as screenOnSamples, SUM(sampleCount) as totalSamples FROM thermal_heat_buckets WHERE tempBucket = '>45' AND startTime >= :startTime")
    ScreenOnHotStats getScreenOnHotStats(long startTime);
    
    // =====================================================================
    // Thermal event correlation queries
    // =====================================================================
    
    @Query("SELECT tempBucket, SUM(spikeCount) as spikes, SUM(sustainedHeatCount) as sustained, SUM(warningEventCount) as warnings, SUM(criticalEventCount) as criticals FROM thermal_heat_buckets WHERE startTime >= :startTime GROUP BY tempBucket")
    List<BucketEventStats> getEventStatsByBucket(long startTime);
    
    @Query("SELECT SUM(criticalEventCount) FROM thermal_heat_buckets WHERE startTime >= :startTime")
    int getTotalCriticalEvents(long startTime);
    
    // =====================================================================
    // Battery impact queries
    // =====================================================================
    
    @Query("SELECT tempBucket, AVG(avgBatteryLevel) as avgLevel, SUM(batteryDrain) as totalDrain, AVG(degradationRisk) as avgRisk FROM thermal_heat_buckets WHERE startTime >= :startTime GROUP BY tempBucket")
    List<BucketBatteryImpact> getBatteryImpactByBucket(long startTime);
    
    @Query("SELECT SUM(batteryDrain) FROM thermal_heat_buckets WHERE tempBucket IN ('40-45', '>45') AND startTime >= :startTime")
    float getDrainInHotZone(long startTime);
    
    @Query("SELECT AVG(degradationRisk) FROM thermal_heat_buckets WHERE startTime >= :startTime")
    float getAvgDegradationRisk(long startTime);
    
    // =====================================================================
    // OEM compliance queries
    // =====================================================================
    
    @Query("SELECT * FROM thermal_heat_buckets WHERE exceedsOEMLimit = 1 AND startTime >= :startTime ORDER BY startTime DESC")
    List<ThermalHeatBucket> getOEMLimitExceededBuckets(long startTime);
    
    @Query("SELECT oemComplianceStatus, COUNT(*) as count FROM thermal_heat_buckets WHERE startTime >= :startTime GROUP BY oemComplianceStatus")
    List<OEMComplianceCount> getOEMComplianceDistribution(long startTime);
    
    @Query("SELECT AVG(thermalScore) FROM thermal_heat_buckets WHERE startTime >= :startTime")
    float getAvgThermalScore(long startTime);
    
    @Query("SELECT * FROM thermal_heat_buckets WHERE thermalScore < :threshold AND startTime >= :startTime ORDER BY thermalScore ASC")
    List<ThermalHeatBucket> getLowScoreBuckets(float threshold, long startTime);
    
    // =====================================================================
    // Temperature statistics
    // =====================================================================
    
    @Query("SELECT MAX(maxTemp) FROM thermal_heat_buckets WHERE startTime >= :startTime")
    float getOverallMaxTemp(long startTime);
    
    @Query("SELECT MIN(minTemp) FROM thermal_heat_buckets WHERE startTime >= :startTime")
    float getOverallMinTemp(long startTime);
    
    @Query("SELECT AVG(avgTemp) FROM thermal_heat_buckets WHERE startTime >= :startTime")
    float getOverallAvgTemp(long startTime);
    
    @Query("SELECT SUM(sampleCount) FROM thermal_heat_buckets WHERE startTime >= :startTime")
    int getTotalSampleCount(long startTime);
    
    @Query("SELECT SUM(durationMs) FROM thermal_heat_buckets WHERE startTime >= :startTime")
    long getTotalTrackedDuration(long startTime);
    
    // =====================================================================
    // Summary queries
    // =====================================================================
    
    @Query("SELECT SUM(durationMs) as totalDuration, SUM(sampleCount) as totalSamples, AVG(avgTemp) as avgTemp, MAX(maxTemp) as maxTemp, AVG(thermalScore) as avgScore, AVG(degradationRisk) as avgRisk, SUM(criticalEventCount) as criticalEvents FROM thermal_heat_buckets WHERE startTime >= :startTime")
    BucketSummary getBucketSummary(long startTime);
    
    // =====================================================================
    // Cleanup
    // =====================================================================

    @Query("DELETE FROM thermal_heat_buckets WHERE endTime < :before")
    void deleteOlderThan(long before);
    
    // =====================================================================
    // Result Classes
    // =====================================================================
    
    class BucketDistribution {
        public String tempBucket;
        public long totalDuration;
        public int totalSamples;
        public float avgTemp;
        public float maxTemp;
    }
    
    class BucketDuration {
        public String tempBucket;
        public long totalDuration;
    }
    
    class HourlyPattern {
        public int hourBlock;
        public float avgTemp;
        public int totalSamples;
        public long totalDuration;
    }
    
    class HourlyBucketBreakdown {
        public int hourBlock;
        public String tempBucket;
        public long duration;
    }
    
    class BucketChargingCorrelation {
        public String tempBucket;
        public int chargingSamples;
        public int totalSamples;
        public float avgTempCharging;
        public float avgTempNotCharging;
    }
    
    class BucketCpuCorrelation {
        public String tempBucket;
        public int highCpuSamples;
        public float avgCpuLoad;
        public float avgTempHighCpu;
    }
    
    class BucketGpuCorrelation {
        public String tempBucket;
        public int highGpuSamples;
        public float avgGpuLoad;
        public float avgTempHighGpu;
    }
    
    class AppBucketStats {
        public String topForegroundApp;
        public int samples;
        public float avgTemp;
        public float maxTemp;
    }
    
    class AppHotDuration {
        public String topForegroundApp;
        public long totalDuration;
    }
    
    class ScreenOnHotStats {
        public int screenOnSamples;
        public int totalSamples;
    }
    
    class BucketEventStats {
        public String tempBucket;
        public int spikes;
        public int sustained;
        public int warnings;
        public int criticals;
    }
    
    class BucketBatteryImpact {
        public String tempBucket;
        public float avgLevel;
        public float totalDrain;
        public float avgRisk;
    }
    
    class OEMComplianceCount {
        public String oemComplianceStatus;
        public int count;
    }
    
    class BucketSummary {
        public long totalDuration;
        public int totalSamples;
        public float avgTemp;
        public float maxTemp;
        public float avgScore;
        public float avgRisk;
        public int criticalEvents;
    }
}
