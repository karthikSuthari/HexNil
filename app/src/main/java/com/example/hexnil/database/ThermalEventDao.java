package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.ThermalEvent;

import java.util.List;

/**
 * DAO for ThermalEvent with OEM-grade thermal analysis queries.
 * 
 * Provides:
 * - Spike and sustained heat detection queries
 * - Correlation analysis queries
 * - OEM compliance reporting
 * - Degradation risk assessment
 */
@Dao
public interface ThermalEventDao {
    
    // =====================================================================
    // Basic CRUD
    // =====================================================================
    
    @Insert
    long insert(ThermalEvent event);
    
    @Update
    void update(ThermalEvent event);
    
    @Delete
    void delete(ThermalEvent event);
    
    @Query("SELECT * FROM thermal_events ORDER BY timestamp DESC LIMIT 1")
    ThermalEvent getLatest();
    
    @Query("SELECT * FROM thermal_events ORDER BY timestamp DESC")
    List<ThermalEvent> getAll();
    
    @Query("SELECT COUNT(*) FROM thermal_events")
    int getCount();
    
    @Query("SELECT * FROM thermal_events WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getEventsSince(long startTime);
    
    @Query("SELECT * FROM thermal_events ORDER BY timestamp DESC LIMIT :limit")
    List<ThermalEvent> getRecentEvents(int limit);
    
    // =====================================================================
    // Severity-based queries
    // =====================================================================
    
    @Query("SELECT * FROM thermal_events WHERE severity = 'critical' AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getCriticalEvents(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE severity = 'danger' AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getDangerEvents(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE severity = 'emergency' AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getEmergencyEvents(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE severity IN ('critical', 'danger', 'emergency') AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getHighSeverityEvents(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE eventType = :type AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getEventsByType(String type, long startTime);
    
    // =====================================================================
    // Spike detection queries
    // =====================================================================
    
    @Query("SELECT * FROM thermal_events WHERE isSpike = 1 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getSpikeEvents(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE isSpike = 1 AND spikeMagnitude >= :minMagnitude AND timestamp >= :startTime ORDER BY spikeMagnitude DESC")
    List<ThermalEvent> getSignificantSpikes(float minMagnitude, long startTime);
    
    @Query("SELECT AVG(spikeMagnitude) FROM thermal_events WHERE isSpike = 1 AND timestamp >= :startTime")
    float getAverageSpikeMagnitude(long startTime);
    
    @Query("SELECT MAX(spikeRateOfChange) FROM thermal_events WHERE isSpike = 1 AND timestamp >= :startTime")
    float getMaxSpikeRate(long startTime);
    
    @Query("SELECT COUNT(*) FROM thermal_events WHERE isSpike = 1 AND timestamp >= :startTime")
    int getSpikeCount(long startTime);
    
    @Query("SELECT spikeCause, COUNT(*) as count FROM thermal_events WHERE isSpike = 1 AND timestamp >= :startTime GROUP BY spikeCause ORDER BY count DESC")
    List<SpikeCauseCount> getSpikeCauseDistribution(long startTime);
    
    // =====================================================================
    // Sustained heat queries
    // =====================================================================
    
    @Query("SELECT * FROM thermal_events WHERE isSustainedHeat = 1 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getSustainedHeatEvents(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE sustainedState = :state AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getEventsBySustainedState(String state, long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE isSustainedHeat = 1 AND sustainedDuration >= :minDuration AND timestamp >= :startTime ORDER BY sustainedDuration DESC")
    List<ThermalEvent> getLongSustainedHeatEvents(long minDuration, long startTime);
    
    @Query("SELECT SUM(sustainedDuration) FROM thermal_events WHERE isSustainedHeat = 1 AND timestamp >= :startTime")
    long getTotalSustainedHeatDuration(long startTime);
    
    @Query("SELECT AVG(sustainedAvgTemp) FROM thermal_events WHERE isSustainedHeat = 1 AND timestamp >= :startTime")
    float getAvgSustainedHeatTemp(long startTime);
    
    // =====================================================================
    // Charging correlation queries
    // =====================================================================
    
    @Query("SELECT * FROM thermal_events WHERE isCharging = 1 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getChargingThermalEvents(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE isCharging = 1 AND wasFastCharging = 1 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getFastChargingThermalEvents(long startTime);
    
    @Query("SELECT AVG(peakTemperature) FROM thermal_events WHERE isCharging = 1 AND timestamp >= :startTime")
    float getAvgTempWhileCharging(long startTime);
    
    @Query("SELECT AVG(peakTemperature) FROM thermal_events WHERE isCharging = 0 AND timestamp >= :startTime")
    float getAvgTempWhileNotCharging(long startTime);
    
    @Query("SELECT AVG(chargeCurrent) FROM thermal_events WHERE isCharging = 1 AND peakTemperature >= :tempThreshold AND timestamp >= :startTime")
    float getAvgCurrentWhenHot(float tempThreshold, long startTime);
    
    @Query("SELECT chargingType, AVG(peakTemperature) as avgTemp, COUNT(*) as count FROM thermal_events WHERE isCharging = 1 AND timestamp >= :startTime GROUP BY chargingType ORDER BY avgTemp DESC")
    List<ChargingTypeThermal> getThermalByChargingType(long startTime);
    
    // =====================================================================
    // CPU/GPU correlation queries
    // =====================================================================
    
    @Query("SELECT * FROM thermal_events WHERE cpuUsage >= :threshold AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getHighCpuThermalEvents(int threshold, long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE gpuUsage >= :threshold AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getHighGpuThermalEvents(int threshold, long startTime);
    
    @Query("SELECT AVG(peakTemperature) FROM thermal_events WHERE cpuUsage >= :threshold AND timestamp >= :startTime")
    float getAvgTempHighCpu(int threshold, long startTime);
    
    @Query("SELECT AVG(peakTemperature) FROM thermal_events WHERE cpuUsage < :threshold AND timestamp >= :startTime")
    float getAvgTempLowCpu(int threshold, long startTime);
    
    @Query("SELECT AVG(peakTemperature) FROM thermal_events WHERE gpuUsage >= :threshold AND timestamp >= :startTime")
    float getAvgTempHighGpu(int threshold, long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE isGaming = 1 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getGamingThermalEvents(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE isCamera = 1 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getCameraThermalEvents(long startTime);
    
    // =====================================================================
    // App correlation queries
    // =====================================================================
    
    @Query("SELECT * FROM thermal_events WHERE foregroundApp = :packageName AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getEventsByApp(String packageName, long startTime);
    
    @Query("SELECT foregroundApp, AVG(peakTemperature) as avgTemp, MAX(peakTemperature) as maxTemp, COUNT(*) as eventCount FROM thermal_events WHERE foregroundApp IS NOT NULL AND timestamp >= :startTime GROUP BY foregroundApp ORDER BY avgTemp DESC LIMIT :limit")
    List<AppThermalStats> getHottestApps(int limit, long startTime);
    
    @Query("SELECT foregroundApp, COUNT(*) as eventCount FROM thermal_events WHERE severity IN ('critical', 'danger') AND foregroundApp IS NOT NULL AND timestamp >= :startTime GROUP BY foregroundApp ORDER BY eventCount DESC LIMIT :limit")
    List<AppEventCount> getAppsWithMostCriticalEvents(int limit, long startTime);
    
    // =====================================================================
    // Primary cause queries
    // =====================================================================
    
    @Query("SELECT primaryCause, COUNT(*) as count FROM thermal_events WHERE timestamp >= :startTime GROUP BY primaryCause ORDER BY count DESC")
    List<PrimaryCauseCount> getPrimaryCauseDistribution(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE primaryCause = :cause AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getEventsByPrimaryCause(String cause, long startTime);
    
    @Query("SELECT AVG(causeConfidence) FROM thermal_events WHERE primaryCause = :cause AND timestamp >= :startTime")
    float getAvgConfidenceForCause(String cause, long startTime);
    
    // =====================================================================
    // OEM compliance queries
    // =====================================================================
    
    @Query("SELECT * FROM thermal_events WHERE passesOEMCompliance = 0 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getOEMComplianceFailures(long startTime);
    
    @Query("SELECT COUNT(*) FROM thermal_events WHERE passesOEMCompliance = 0 AND timestamp >= :startTime")
    int getOEMComplianceFailureCount(long startTime);
    
    @Query("SELECT COUNT(*) * 100.0 / (SELECT COUNT(*) FROM thermal_events WHERE timestamp >= :startTime) FROM thermal_events WHERE passesOEMCompliance = 1 AND timestamp >= :startTime")
    float getOEMComplianceRate(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE requiresEmergencyAction = 1 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getEmergencyActionEvents(long startTime);
    
    @Query("SELECT thresholdLevel, COUNT(*) as count, AVG(duration) as avgDuration FROM thermal_events WHERE timestamp >= :startTime GROUP BY thresholdLevel")
    List<ThresholdLevelStats> getThresholdLevelDistribution(long startTime);
    
    // =====================================================================
    // Degradation risk queries
    // =====================================================================
    
    @Query("SELECT * FROM thermal_events WHERE degradationRisk >= :threshold AND timestamp >= :startTime ORDER BY degradationRisk DESC")
    List<ThermalEvent> getHighDegradationRiskEvents(float threshold, long startTime);
    
    @Query("SELECT AVG(degradationRisk) FROM thermal_events WHERE timestamp >= :startTime")
    float getAvgDegradationRisk(long startTime);
    
    @Query("SELECT SUM(estimatedCycleImpact) FROM thermal_events WHERE timestamp >= :startTime")
    float getTotalCycleImpact(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE degradationRisk >= 0.5 AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getSignificantDegradationEvents(long startTime);
    
    // =====================================================================
    // Temperature statistics
    // =====================================================================
    
    @Query("SELECT MAX(peakTemperature) FROM thermal_events WHERE timestamp >= :startTime")
    float getMaxPeakTemperature(long startTime);
    
    @Query("SELECT AVG(peakTemperature) FROM thermal_events WHERE timestamp >= :startTime")
    float getAveragePeakTemperature(long startTime);
    
    @Query("SELECT MIN(batteryTemp) FROM thermal_events WHERE timestamp >= :startTime")
    float getMinTemperature(long startTime);
    
    @Query("SELECT AVG(temperatureRise) FROM thermal_events WHERE timestamp >= :startTime")
    float getAvgTemperatureRise(long startTime);
    
    // =====================================================================
    // Thermal zone queries
    // =====================================================================
    
    @Query("SELECT thermalZone, COUNT(*) as count, AVG(duration) as avgDuration, AVG(peakTemperature) as avgTemp FROM thermal_events WHERE timestamp >= :startTime GROUP BY thermalZone")
    List<ThermalZoneStats> getThermalZoneDistribution(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE thermalZone = :zone AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getEventsByThermalZone(String zone, long startTime);
    
    // =====================================================================
    // Trend queries
    // =====================================================================
    
    @Query("SELECT thermalTrend, COUNT(*) as count FROM thermal_events WHERE timestamp >= :startTime GROUP BY thermalTrend ORDER BY count DESC")
    List<TrendCount> getThermalTrendDistribution(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE thermalTrend = 'spiking' AND timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getSpikingTrendEvents(long startTime);
    
    // =====================================================================
    // Thermal score queries
    // =====================================================================
    
    @Query("SELECT AVG(thermalScore) FROM thermal_events WHERE timestamp >= :startTime")
    float getAvgThermalScore(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE thermalScore < :threshold AND timestamp >= :startTime ORDER BY thermalScore ASC")
    List<ThermalEvent> getLowScoreEvents(int threshold, long startTime);
    
    // =====================================================================
    // Count queries
    // =====================================================================
    
    @Query("SELECT COUNT(*) FROM thermal_events WHERE severity = 'critical'")
    int getCriticalEventCount();
    
    @Query("SELECT COUNT(*) FROM thermal_events WHERE timestamp >= :startTime")
    int getEventCount(long startTime);
    
    @Query("SELECT COUNT(*) FROM thermal_events WHERE isCharging = 1 AND timestamp >= :startTime")
    int getChargingEventCount(long startTime);
    
    // =====================================================================
    // Time-based analysis
    // =====================================================================
    
    @Query("SELECT strftime('%H', datetime(timestamp/1000, 'unixepoch')) as hour, COUNT(*) as count, AVG(peakTemperature) as avgTemp FROM thermal_events WHERE timestamp >= :startTime GROUP BY hour ORDER BY hour")
    List<HourlyThermalStats> getHourlyDistribution(long startTime);
    
    // =====================================================================
    // Summary queries
    // =====================================================================
    
    @Query("SELECT COUNT(*) as totalEvents, AVG(peakTemperature) as avgPeakTemp, MAX(peakTemperature) as maxPeakTemp, AVG(duration) as avgDuration, SUM(CASE WHEN severity IN ('critical', 'danger', 'emergency') THEN 1 ELSE 0 END) as criticalCount, AVG(thermalScore) as avgScore, AVG(degradationRisk) as avgDegradationRisk FROM thermal_events WHERE timestamp >= :startTime")
    ThermalSummary getThermalSummary(long startTime);
    
    // =====================================================================
    // Cleanup
    // =====================================================================
    
    @Query("DELETE FROM thermal_events WHERE timestamp < :beforeTime")
    void deleteOlderThan(long beforeTime);
    
    // =====================================================================
    // Result Classes
    // =====================================================================
    
    class SpikeCauseCount {
        public String spikeCause;
        public int count;
    }
    
    class ChargingTypeThermal {
        public String chargingType;
        public float avgTemp;
        public int count;
    }
    
    class AppThermalStats {
        public String foregroundApp;
        public float avgTemp;
        public float maxTemp;
        public int eventCount;
    }
    
    class AppEventCount {
        public String foregroundApp;
        public int eventCount;
    }
    
    class PrimaryCauseCount {
        public String primaryCause;
        public int count;
    }
    
    class ThresholdLevelStats {
        public String thresholdLevel;
        public int count;
        public float avgDuration;
    }
    
    class ThermalZoneStats {
        public String thermalZone;
        public int count;
        public float avgDuration;
        public float avgTemp;
    }
    
    class TrendCount {
        public String thermalTrend;
        public int count;
    }
    
    class HourlyThermalStats {
        public String hour;
        public int count;
        public float avgTemp;
    }
    
    class ThermalSummary {
        public int totalEvents;
        public float avgPeakTemp;
        public float maxPeakTemp;
        public float avgDuration;
        public int criticalCount;
        public float avgScore;
        public float avgDegradationRisk;
    }
}
