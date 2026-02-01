package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.DrainEvent;

import java.util.List;

/**
 * DAO for drain event operations with comprehensive drain intelligence queries.
 * 
 * Provides queries for:
 * - Screen state analysis (on/off/doze/bright/dim)
 * - Expected vs actual drain comparison
 * - Idle drain benchmarks
 * - Deep sleep effectiveness
 * - Per-app drain attribution
 * - System vs user app comparison
 * - Drain trending and patterns
 * - Anomaly detection
 */
@Dao
public interface DrainEventDao {
    
    // ====================================================================================
    // BASIC CRUD OPERATIONS
    // ====================================================================================
    
    @Insert
    long insert(DrainEvent event);
    
    @Update
    void update(DrainEvent event);
    
    @Delete
    void delete(DrainEvent event);
    
    @Query("SELECT * FROM drain_events ORDER BY startTime DESC LIMIT 1")
    DrainEvent getLatest();
    
    @Query("SELECT * FROM drain_events ORDER BY startTime DESC")
    List<DrainEvent> getAll();
    
    @Query("SELECT COUNT(*) FROM drain_events")
    int getCount();
    
    @Query("SELECT COUNT(*) FROM drain_events WHERE startTime >= :startTime")
    int getCountSince(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE endTime = 0 LIMIT 1")
    DrainEvent getActiveEvent();
    
    @Query("SELECT * FROM drain_events WHERE startTime >= :startTime ORDER BY startTime DESC")
    List<DrainEvent> getEventsSince(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE startTime >= :startTime AND endTime <= :endTime ORDER BY startTime DESC")
    List<DrainEvent> getEventsBetween(long startTime, long endTime);
    
    @Query("SELECT * FROM drain_events ORDER BY startTime DESC LIMIT :limit")
    List<DrainEvent> getRecentEvents(int limit);
    
    @Query("DELETE FROM drain_events WHERE startTime < :beforeTime")
    void deleteOlderThan(long beforeTime);
    
    // ====================================================================================
    // SCREEN STATE ANALYSIS
    // ====================================================================================
    
    @Query("SELECT * FROM drain_events WHERE screenOn = 1 AND startTime >= :startTime")
    List<DrainEvent> getScreenOnDrainEvents(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE screenOn = 0 AND startTime >= :startTime")
    List<DrainEvent> getScreenOffDrainEvents(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE screenState = :state AND startTime >= :startTime")
    List<DrainEvent> getEventsByScreenState(String state, long startTime);
    
    @Query("SELECT * FROM drain_events WHERE screenStateDetailed = :detailedState AND startTime >= :startTime")
    List<DrainEvent> getEventsByDetailedScreenState(String detailedState, long startTime);
    
    @Query("SELECT AVG(drainRatePerHour) FROM drain_events WHERE screenOn = 1 AND startTime >= :startTime")
    Float getAverageScreenOnDrain(long startTime);
    
    @Query("SELECT AVG(drainRatePerHour) FROM drain_events WHERE screenOn = 0 AND startTime >= :startTime")
    Float getAverageScreenOffDrain(long startTime);
    
    @Query("SELECT AVG(drainRatePerHour) FROM drain_events WHERE screenState = 'doze' AND startTime >= :startTime")
    Float getAverageDozeDrain(long startTime);
    
    @Query("SELECT AVG(drainRatePerHour) FROM drain_events WHERE screenStateDetailed = :state AND startTime >= :startTime")
    Float getAverageDrainByDetailedState(String state, long startTime);
    
    @Query("SELECT screenStateGrade as grade, COUNT(*) as count FROM drain_events WHERE startTime >= :startTime GROUP BY screenStateGrade ORDER BY count DESC")
    List<GradeCount> getScreenStateGradeDistribution(long startTime);
    
    // Screen state pattern analysis
    @Query("SELECT screenStateDetailed, AVG(drainRatePerHour) as avgDrain, COUNT(*) as count " +
           "FROM drain_events WHERE startTime >= :startTime AND screenStateDetailed IS NOT NULL " +
           "GROUP BY screenStateDetailed ORDER BY avgDrain DESC")
    List<ScreenStateDrainSummary> getScreenStateDrainSummary(long startTime);
    
    // ====================================================================================
    // EXPECTED VS ACTUAL DRAIN
    // ====================================================================================
    
    @Query("SELECT AVG(drainDelta) FROM drain_events WHERE startTime >= :startTime AND drainDelta IS NOT NULL")
    Float getAverageDrainDelta(long startTime);
    
    @Query("SELECT AVG(drainEfficiency) FROM drain_events WHERE startTime >= :startTime AND drainEfficiency IS NOT NULL")
    Float getAverageDrainEfficiency(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE drainDelta > :threshold AND startTime >= :startTime ORDER BY drainDelta DESC")
    List<DrainEvent> getOverdrainEvents(float threshold, long startTime);
    
    @Query("SELECT drainEfficiencyGrade as grade, COUNT(*) as count FROM drain_events WHERE startTime >= :startTime AND drainEfficiencyGrade IS NOT NULL GROUP BY drainEfficiencyGrade")
    List<GradeCount> getDrainEfficiencyDistribution(long startTime);
    
    @Query("SELECT AVG(expectedDrainRate) FROM drain_events WHERE screenOn = 1 AND startTime >= :startTime")
    Float getExpectedScreenOnRate(long startTime);
    
    @Query("SELECT AVG(expectedDrainRate) FROM drain_events WHERE screenOn = 0 AND startTime >= :startTime")
    Float getExpectedScreenOffRate(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE drainEfficiency < :threshold AND startTime >= :startTime ORDER BY drainEfficiency ASC LIMIT :limit")
    List<DrainEvent> getLowestEfficiencyEvents(float threshold, long startTime, int limit);
    
    // ====================================================================================
    // IDLE DRAIN & BENCHMARKS
    // ====================================================================================
    
    @Query("SELECT * FROM drain_events WHERE isIdle = 1 AND startTime >= :startTime ORDER BY startTime DESC")
    List<DrainEvent> getIdleDrainEvents(long startTime);
    
    @Query("SELECT AVG(drainRatePerHour) FROM drain_events WHERE isIdle = 1 AND startTime >= :startTime")
    Float getAverageIdleDrain(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE idleDrainQuality = :quality AND startTime >= :startTime")
    List<DrainEvent> getEventsByIdleQuality(String quality, long startTime);
    
    @Query("SELECT idleBenchmarkResult as benchmark, COUNT(*) as count FROM drain_events WHERE startTime >= :startTime AND idleBenchmarkResult IS NOT NULL GROUP BY idleBenchmarkResult")
    List<BenchmarkCount> getIdleBenchmarkDistribution(long startTime);
    
    @Query("SELECT AVG(idleDrainScore) FROM drain_events WHERE isIdle = 1 AND startTime >= :startTime")
    Float getAverageIdleDrainScore(long startTime);
    
    // Benchmark analysis
    @Query("SELECT benchmarkResult as benchmark, COUNT(*) as count FROM drain_events WHERE startTime >= :startTime AND benchmarkResult IS NOT NULL GROUP BY benchmarkResult")
    List<BenchmarkCount> getBenchmarkDistribution(long startTime);
    
    @Query("SELECT AVG(benchmarkDelta) FROM drain_events WHERE screenOn = 1 AND startTime >= :startTime")
    Float getAverageBenchmarkDeltaScreenOn(long startTime);
    
    @Query("SELECT AVG(benchmarkDelta) FROM drain_events WHERE screenOn = 0 AND startTime >= :startTime")
    Float getAverageBenchmarkDeltaScreenOff(long startTime);
    
    // ====================================================================================
    // DEEP SLEEP EFFECTIVENESS
    // ====================================================================================
    
    @Query("SELECT AVG(deepSleepEffectiveness) FROM drain_events WHERE screenOn = 0 AND startTime >= :startTime AND deepSleepEffectiveness IS NOT NULL")
    Float getAverageDeepSleepEffectiveness(long startTime);
    
    @Query("SELECT AVG(deepSleepPercentage) FROM drain_events WHERE screenOn = 0 AND startTime >= :startTime AND deepSleepPercentage IS NOT NULL")
    Float getAverageDeepSleepPercentage(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE deepSleepEffectiveness < :threshold AND screenOn = 0 AND startTime >= :startTime ORDER BY deepSleepEffectiveness ASC")
    List<DrainEvent> getPoorDeepSleepEvents(float threshold, long startTime);
    
    @Query("SELECT deepSleepGrade as grade, COUNT(*) as count FROM drain_events WHERE startTime >= :startTime AND deepSleepGrade IS NOT NULL GROUP BY deepSleepGrade")
    List<GradeCount> getDeepSleepGradeDistribution(long startTime);
    
    @Query("SELECT SUM(deepSleepTimeMs) FROM drain_events WHERE startTime >= :startTime AND deepSleepTimeMs IS NOT NULL")
    Long getTotalDeepSleepTime(long startTime);
    
    @Query("SELECT SUM(lightSleepTimeMs) FROM drain_events WHERE startTime >= :startTime AND lightSleepTimeMs IS NOT NULL")
    Long getTotalLightSleepTime(long startTime);
    
    // ====================================================================================
    // COMPONENT-LEVEL DRAIN
    // ====================================================================================
    
    @Query("SELECT AVG(displayDrainPct) FROM drain_events WHERE screenOn = 1 AND startTime >= :startTime")
    Float getAverageDisplayDrainPct(long startTime);
    
    @Query("SELECT AVG(cpuDrainPct) FROM drain_events WHERE startTime >= :startTime")
    Float getAverageCpuDrainPct(long startTime);
    
    @Query("SELECT AVG(networkDrainPct) FROM drain_events WHERE startTime >= :startTime")
    Float getAverageNetworkDrainPct(long startTime);
    
    @Query("SELECT AVG(sensorDrainPct) FROM drain_events WHERE startTime >= :startTime")
    Float getAverageSensorDrainPct(long startTime);
    
    @Query("SELECT primaryDrainComponent, COUNT(*) as count FROM drain_events WHERE startTime >= :startTime AND primaryDrainComponent IS NOT NULL GROUP BY primaryDrainComponent ORDER BY count DESC")
    List<ComponentCount> getPrimaryComponentDistribution(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE primaryDrainComponent = :component AND startTime >= :startTime ORDER BY startTime DESC")
    List<DrainEvent> getEventsByPrimaryComponent(String component, long startTime);
    
    // Component trend analysis
    @Query("SELECT AVG(displayDrainPct) as display, AVG(cpuDrainPct) as cpu, AVG(networkDrainPct) as network, AVG(sensorDrainPct) as sensor " +
           "FROM drain_events WHERE startTime >= :startTime")
    ComponentDrainAverage getAverageComponentDrain(long startTime);
    
    // ====================================================================================
    // PER-APP DRAIN
    // ====================================================================================
    
    @Query("SELECT * FROM drain_events WHERE topAppPackage = :packageName AND startTime >= :startTime ORDER BY startTime DESC")
    List<DrainEvent> getEventsByTopApp(String packageName, long startTime);
    
    @Query("SELECT AVG(topAppDrainPct) FROM drain_events WHERE topAppPackage = :packageName AND startTime >= :startTime")
    Float getAverageAppDrainPct(String packageName, long startTime);
    
    @Query("SELECT topAppPackage, AVG(topAppDrainPct) as avgDrain, COUNT(*) as count " +
           "FROM drain_events WHERE startTime >= :startTime AND topAppPackage IS NOT NULL " +
           "GROUP BY topAppPackage ORDER BY avgDrain DESC LIMIT :limit")
    List<AppDrainSummary> getTopDrainingApps(long startTime, int limit);
    
    @Query("SELECT AVG(totalAppsContributing) FROM drain_events WHERE startTime >= :startTime")
    Float getAverageAppsContributing(long startTime);
    
    // ====================================================================================
    // SYSTEM VS USER APP ANALYSIS
    // ====================================================================================
    
    @Query("SELECT AVG(systemAppDrainPct) FROM drain_events WHERE startTime >= :startTime AND systemAppDrainPct IS NOT NULL")
    Float getAverageSystemAppDrainPct(long startTime);
    
    @Query("SELECT AVG(userAppDrainPct) FROM drain_events WHERE startTime >= :startTime AND userAppDrainPct IS NOT NULL")
    Float getAverageUserAppDrainPct(long startTime);
    
    @Query("SELECT AVG(unattributedDrainPct) FROM drain_events WHERE startTime >= :startTime AND unattributedDrainPct IS NOT NULL")
    Float getAverageUnattributedDrainPct(long startTime);
    
    @Query("SELECT drainDominant, COUNT(*) as count FROM drain_events WHERE startTime >= :startTime AND drainDominant IS NOT NULL GROUP BY drainDominant")
    List<DominantCount> getDrainDominantDistribution(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE drainDominant = :dominant AND startTime >= :startTime")
    List<DrainEvent> getEventsByDrainDominant(String dominant, long startTime);
    
    // ====================================================================================
    // ANOMALY DETECTION
    // ====================================================================================
    
    @Query("SELECT * FROM drain_events WHERE isAnomalous = 1 AND startTime >= :startTime")
    List<DrainEvent> getAnomalousEvents(long startTime);
    
    @Query("SELECT COUNT(*) FROM drain_events WHERE isAnomalous = 1 AND startTime >= :startTime")
    int getAnomalyCount(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE anomalySeverity = :severity AND startTime >= :startTime ORDER BY anomalyScore DESC")
    List<DrainEvent> getEventsByAnomalySeverity(String severity, long startTime);
    
    @Query("SELECT anomalySeverity, COUNT(*) as count FROM drain_events WHERE startTime >= :startTime AND isAnomalous = 1 GROUP BY anomalySeverity")
    List<SeverityCount> getAnomalySeverityDistribution(long startTime);
    
    @Query("SELECT AVG(anomalyScore) FROM drain_events WHERE isAnomalous = 1 AND startTime >= :startTime")
    Float getAverageAnomalyScore(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE isPatternAnomaly = 1 AND startTime >= :startTime ORDER BY startTime DESC")
    List<DrainEvent> getPatternAnomalies(long startTime);
    
    @Query("SELECT MAX(consecutiveAnomalies) FROM drain_events WHERE startTime >= :startTime")
    Integer getMaxConsecutiveAnomalies(long startTime);
    
    // ====================================================================================
    // DRAIN TRENDING
    // ====================================================================================
    
    @Query("SELECT AVG(drainRatePerHour) FROM drain_events WHERE startTime >= :startTime AND startTime < :endTime")
    Float getAverageDrainForPeriod(long startTime, long endTime);
    
    @Query("SELECT trendDirection, COUNT(*) as count FROM drain_events WHERE startTime >= :startTime AND trendDirection IS NOT NULL GROUP BY trendDirection")
    List<TrendCount> getTrendDistribution(long startTime);
    
    @Query("SELECT AVG(rollingAvgDrain) FROM drain_events WHERE startTime >= :startTime AND rollingAvgDrain IS NOT NULL")
    Float getAverageRollingDrain(long startTime);
    
    @Query("SELECT MAX(peakDrainRate) FROM drain_events WHERE startTime >= :startTime")
    Float getPeakDrainRate(long startTime);
    
    // ====================================================================================
    // THERMAL CORRELATION
    // ====================================================================================
    
    @Query("SELECT AVG(avgTemperature) FROM drain_events WHERE startTime >= :startTime AND avgTemperature > 0")
    Float getAverageTemperatureDuringDrain(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE wasOverheating = 1 AND startTime >= :startTime ORDER BY maxTemperature DESC")
    List<DrainEvent> getOverheatingEvents(long startTime);
    
    @Query("SELECT AVG(thermalDrainImpact) FROM drain_events WHERE startTime >= :startTime AND thermalDrainImpact > 0")
    Float getAverageThermalImpact(long startTime);
    
    // Temperature-drain correlation
    @Query("SELECT avgTemperature as temperature, AVG(drainRatePerHour) as drain FROM drain_events " +
           "WHERE startTime >= :startTime AND avgTemperature > 0 GROUP BY (avgTemperature / 5) * 5 ORDER BY temperature")
    List<TempDrainCorrelation> getTemperatureDrainCorrelation(long startTime);
    
    // ====================================================================================
    // QUALITY & OVERALL ANALYSIS
    // ====================================================================================
    
    @Query("SELECT SUM(drainAmount) FROM drain_events WHERE startTime >= :startTime")
    Integer getTotalDrain(long startTime);
    
    @Query("SELECT AVG(overallDrainScore) FROM drain_events WHERE startTime >= :startTime AND overallDrainScore IS NOT NULL")
    Float getAverageOverallScore(long startTime);
    
    @Query("SELECT overallGrade as grade, COUNT(*) as count FROM drain_events WHERE startTime >= :startTime AND overallGrade IS NOT NULL GROUP BY overallGrade")
    List<GradeCount> getOverallGradeDistribution(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE drainCategory = :category AND startTime >= :startTime")
    List<DrainEvent> getEventsByCategory(String category, long startTime);
    
    @Query("SELECT * FROM drain_events WHERE drainRatePerHour > :threshold AND startTime >= :startTime")
    List<DrainEvent> getHighDrainEvents(float threshold, long startTime);
    
    // ====================================================================================
    // COMPREHENSIVE SUMMARY QUERIES
    // ====================================================================================
    
    // Daily drain summary
    @Query("SELECT " +
           "(startTime / 86400000) * 86400000 as day, " +
           "SUM(drainAmount) as totalDrain, " +
           "AVG(drainRatePerHour) as avgDrainRate, " +
           "COUNT(*) as eventCount, " +
           "AVG(drainEfficiency) as avgEfficiency " +
           "FROM drain_events WHERE startTime >= :startTime " +
           "GROUP BY (startTime / 86400000) ORDER BY day DESC")
    List<DailyDrainSummary> getDailyDrainSummary(long startTime);
    
    // Hourly pattern analysis
    @Query("SELECT " +
           "((startTime % 86400000) / 3600000) as hour, " +
           "AVG(drainRatePerHour) as avgDrain, " +
           "COUNT(*) as count " +
           "FROM drain_events WHERE startTime >= :startTime " +
           "GROUP BY ((startTime % 86400000) / 3600000) ORDER BY hour")
    List<HourlyDrainPattern> getHourlyDrainPattern(long startTime);
    
    // ====================================================================================
    // RESULT HELPER CLASSES
    // ====================================================================================
    
    class GradeCount {
        public String grade;
        public int count;
    }
    
    class BenchmarkCount {
        public String benchmark;
        public int count;
    }
    
    class ComponentCount {
        public String primaryDrainComponent;
        public int count;
    }
    
    class SeverityCount {
        public String anomalySeverity;
        public int count;
    }
    
    class DominantCount {
        public String drainDominant;
        public int count;
    }
    
    class TrendCount {
        public String trendDirection;
        public int count;
    }
    
    class ScreenStateDrainSummary {
        public String screenStateDetailed;
        public float avgDrain;
        public int count;
    }
    
    class AppDrainSummary {
        public String topAppPackage;
        public float avgDrain;
        public int count;
    }
    
    class ComponentDrainAverage {
        public float display;
        public float cpu;
        public float network;
        public float sensor;
    }
    
    class TempDrainCorrelation {
        public int temperature;
        public float drain;
    }
    
    class DailyDrainSummary {
        public long day;
        public int totalDrain;
        public float avgDrainRate;
        public int eventCount;
        public float avgEfficiency;
    }
    
    class HourlyDrainPattern {
        public int hour;
        public float avgDrain;
        public int count;
    }
}
