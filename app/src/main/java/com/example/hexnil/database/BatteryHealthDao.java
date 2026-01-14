package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.BatteryHealth;

import java.util.List;

/**
 * OEM-Grade Battery Health DAO
 * 
 * Provides comprehensive queries for:
 * - Health history and trends
 * - Degradation analysis
 * - Supplier/batch quality tracking
 * - Anomaly detection
 * - OEM reporting
 */
@Dao
public interface BatteryHealthDao {
    
    // =========================================================================
    // BASIC CRUD
    // =========================================================================
    
    @Insert
    long insert(BatteryHealth health);
    
    @Update
    void update(BatteryHealth health);
    
    @Delete
    void delete(BatteryHealth health);
    
    // =========================================================================
    // LATEST & RECENT QUERIES
    // =========================================================================
    
    @Query("SELECT * FROM battery_health ORDER BY timestamp DESC LIMIT 1")
    BatteryHealth getLatest();
    
    @Query("SELECT * FROM battery_health ORDER BY timestamp DESC")
    List<BatteryHealth> getAll();
    
    @Query("SELECT COUNT(*) FROM battery_health")
    int getCount();
    
    @Query("SELECT * FROM battery_health WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<BatteryHealth> getHealthHistorySince(long startTime);
    
    @Query("SELECT * FROM battery_health ORDER BY timestamp DESC LIMIT :limit")
    List<BatteryHealth> getRecentHealthRecords(int limit);
    
    @Query("SELECT * FROM battery_health ORDER BY timestamp ASC LIMIT 1")
    BatteryHealth getFirst();
    
    // =========================================================================
    // HEALTH STATISTICS
    // =========================================================================
    
    @Query("SELECT AVG(healthPercentage) FROM battery_health WHERE timestamp >= :startTime")
    float getAverageHealth(long startTime);
    
    @Query("SELECT MIN(healthPercentage) FROM battery_health WHERE timestamp >= :startTime")
    float getMinHealth(long startTime);
    
    @Query("SELECT MAX(healthPercentage) FROM battery_health WHERE timestamp >= :startTime")
    float getMaxHealth(long startTime);
    
    @Query("SELECT AVG(degradationRate) FROM battery_health WHERE timestamp >= :startTime")
    float getAverageDegradationRate(long startTime);
    
    @Query("SELECT AVG(cycleCount) FROM battery_health ORDER BY timestamp DESC LIMIT 10")
    float getRecentAverageCycles();
    
    // =========================================================================
    // DEGRADATION TREND ANALYSIS
    // =========================================================================
    
    /** Get degradation rate trend (last N records) */
    @Query("SELECT degradationRate FROM battery_health ORDER BY timestamp DESC LIMIT :count")
    List<Float> getRecentDegradationRates(int count);
    
    /** Get capacity fade progression */
    @Query("SELECT capacityFade FROM battery_health WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<Float> getCapacityFadeTrend(long startTime);
    
    /** Get health percentage progression */
    @Query("SELECT healthPercentage FROM battery_health WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<Float> getHealthTrend(long startTime);
    
    /** Get cycle count progression */
    @Query("SELECT cycleCount FROM battery_health WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<Float> getCycleTrend(long startTime);
    
    /** Check if degradation is accelerating (current rate > previous average) */
    @Query("SELECT AVG(degradationRate) FROM battery_health " +
           "WHERE timestamp < :currentTimestamp AND timestamp >= :windowStart")
    float getPreviousPeriodAverageDegradationRate(long currentTimestamp, long windowStart);
    
    // =========================================================================
    // ABNORMAL WEAR DETECTION
    // =========================================================================
    
    @Query("SELECT * FROM battery_health WHERE hasAbnormalWear = 1 ORDER BY timestamp DESC")
    List<BatteryHealth> getAbnormalWearRecords();
    
    @Query("SELECT * FROM battery_health WHERE hasSwellingRisk = 1 ORDER BY timestamp DESC")
    List<BatteryHealth> getSwellingRiskRecords();
    
    @Query("SELECT * FROM battery_health WHERE hasAbnormalAging = 1 ORDER BY timestamp DESC")
    List<BatteryHealth> getAbnormalAgingRecords();
    
    @Query("SELECT * FROM battery_health WHERE hasHighResistance = 1 ORDER BY timestamp DESC")
    List<BatteryHealth> getHighResistanceRecords();
    
    @Query("SELECT * FROM battery_health WHERE hasCapacityInstability = 1 ORDER BY timestamp DESC")
    List<BatteryHealth> getCapacityInstabilityRecords();
    
    @Query("SELECT * FROM battery_health WHERE wearSeverity >= :minSeverity ORDER BY wearSeverity DESC")
    List<BatteryHealth> getRecordsBySeverity(int minSeverity);
    
    @Query("SELECT COUNT(*) FROM battery_health WHERE hasAbnormalWear = 1")
    int getAbnormalWearCount();
    
    @Query("SELECT MAX(wearSeverity) FROM battery_health")
    int getMaxWearSeverity();
    
    // =========================================================================
    // CALIBRATION QUERIES
    // =========================================================================
    
    @Query("SELECT * FROM battery_health WHERE needsCalibration = 1 ORDER BY timestamp DESC LIMIT 1")
    BatteryHealth getCalibrationNeededRecord();
    
    @Query("SELECT * FROM battery_health WHERE hasCalibrationCycle = 1 ORDER BY timestamp DESC LIMIT 1")
    BatteryHealth getLastCalibratedRecord();
    
    @Query("SELECT COUNT(*) FROM battery_health WHERE hasCalibrationCycle = 1")
    int getCalibrationCycleCount();
    
    // =========================================================================
    // SUPPLIER/BATCH QUALITY TRACKING
    // =========================================================================
    
    @Query("SELECT AVG(healthPercentage) FROM battery_health WHERE manufacturer = :supplier")
    float getAverageHealthBySupplier(String supplier);
    
    @Query("SELECT AVG(healthPercentage) FROM battery_health WHERE batchId = :batch")
    float getAverageHealthByBatch(String batch);
    
    @Query("SELECT DISTINCT manufacturer FROM battery_health WHERE manufacturer IS NOT NULL")
    List<String> getAllSuppliers();
    
    @Query("SELECT DISTINCT batchId FROM battery_health WHERE batchId IS NOT NULL")
    List<String> getAllBatches();
    
    @Query("SELECT AVG(degradationRate) FROM battery_health WHERE manufacturer = :supplier")
    float getAverageDegradationBySupplier(String supplier);
    
    @Query("SELECT AVG(degradationRate) FROM battery_health WHERE batchId = :batch")
    float getAverageDegradationByBatch(String batch);
    
    @Query("SELECT COUNT(*) FROM battery_health WHERE manufacturer = :supplier AND hasAbnormalWear = 1")
    int getAbnormalWearCountBySupplier(String supplier);
    
    @Query("SELECT COUNT(*) FROM battery_health WHERE batchId = :batch AND hasAbnormalWear = 1")
    int getAbnormalWearCountByBatch(String batch);
    
    @Query("SELECT COUNT(*) FROM battery_health WHERE manufacturer = :supplier")
    int getTotalRecordsBySupplier(String supplier);
    
    @Query("SELECT COUNT(*) FROM battery_health WHERE batchId = :batch")
    int getTotalRecordsByBatch(String batch);
    
    /** Get supplier quality tier distribution */
    @Query("SELECT supplierQualityTier, COUNT(*) as count FROM battery_health " +
           "WHERE supplierQualityTier IS NOT NULL GROUP BY supplierQualityTier")
    List<QualityTierCount> getSupplierQualityDistribution();
    
    /** Get batches with quality alerts */
    @Query("SELECT DISTINCT batchId FROM battery_health WHERE batchQualityAlert = 1")
    List<String> getBatchesWithQualityAlerts();
    
    // =========================================================================
    // CONFIDENCE & DATA QUALITY
    // =========================================================================
    
    @Query("SELECT AVG(overallConfidence) FROM battery_health ORDER BY timestamp DESC LIMIT :count")
    float getAverageConfidence(int count);
    
    @Query("SELECT * FROM battery_health WHERE overallConfidence >= :minConfidence " +
           "ORDER BY timestamp DESC LIMIT :limit")
    List<BatteryHealth> getHighConfidenceRecords(float minConfidence, int limit);
    
    @Query("SELECT * FROM battery_health WHERE meetsMinimumDataRequirement = 1 " +
           "ORDER BY timestamp DESC LIMIT 1")
    BatteryHealth getLatestReliableRecord();
    
    // =========================================================================
    // SCORE QUERIES
    // =========================================================================
    
    @Query("SELECT AVG(overallScore) FROM battery_health WHERE timestamp >= :startTime")
    float getAverageOverallScore(long startTime);
    
    @Query("SELECT AVG(capacityScore) FROM battery_health WHERE timestamp >= :startTime")
    float getAverageCapacityScore(long startTime);
    
    @Query("SELECT AVG(cycleScore) FROM battery_health WHERE timestamp >= :startTime")
    float getAverageCycleScore(long startTime);
    
    @Query("SELECT AVG(thermalScore) FROM battery_health WHERE timestamp >= :startTime")
    float getAverageThermalScore(long startTime);
    
    // =========================================================================
    // EOL PREDICTION
    // =========================================================================
    
    @Query("SELECT AVG(predictedEOL) FROM battery_health WHERE eolConfidence >= :minConfidence " +
           "ORDER BY timestamp DESC LIMIT :count")
    float getAveragePredictedEOL(float minConfidence, int count);
    
    @Query("SELECT * FROM battery_health WHERE predictedEOL <= :monthsThreshold " +
           "AND eolConfidence >= :minConfidence ORDER BY predictedEOL ASC")
    List<BatteryHealth> getBatteriesNearingEOL(float monthsThreshold, float minConfidence);
    
    // =========================================================================
    // MAINTENANCE & CLEANUP
    // =========================================================================
    
    @Query("DELETE FROM battery_health WHERE timestamp < :beforeTime")
    void deleteOlderThan(long beforeTime);
    
    @Query("SELECT COUNT(*) FROM battery_health")
    int getTotalRecords();
    
    @Query("DELETE FROM battery_health WHERE id NOT IN " +
           "(SELECT id FROM battery_health ORDER BY timestamp DESC LIMIT :keepCount)")
    void pruneOldRecords(int keepCount);
    
    // =========================================================================
    // OEM REPORTING QUERIES
    // =========================================================================
    
    /** Summary for OEM dashboard */
    @Query("SELECT COUNT(*) as total, " +
           "AVG(healthPercentage) as avgHealth, " +
           "AVG(cycleCount) as avgCycles, " +
           "AVG(degradationRate) as avgDegradation, " +
           "SUM(CASE WHEN hasAbnormalWear = 1 THEN 1 ELSE 0 END) as abnormalCount, " +
           "SUM(CASE WHEN hasSwellingRisk = 1 THEN 1 ELSE 0 END) as swellingRiskCount " +
           "FROM battery_health WHERE timestamp >= :startTime")
    HealthSummary getHealthSummary(long startTime);
    
    // =========================================================================
    // RESULT CLASSES FOR COMPLEX QUERIES
    // =========================================================================
    
    class QualityTierCount {
        public String supplierQualityTier;
        public int count;
    }
    
    class HealthSummary {
        public int total;
        public float avgHealth;
        public float avgCycles;
        public float avgDegradation;
        public int abnormalCount;
        public int swellingRiskCount;
    }
}
