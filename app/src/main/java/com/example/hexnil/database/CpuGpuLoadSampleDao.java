package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.hexnil.model.CpuGpuLoadSample;

import java.util.List;

/**
 * DAO for CPU/GPU Load Samples
 * 
 * Provides database operations for continuous CPU/GPU/stress sampling.
 * Optimized for high-frequency inserts and time-range queries.
 */
@Dao
public interface CpuGpuLoadSampleDao {

    // =========================================================================
    // INSERT OPERATIONS
    // =========================================================================

    /**
     * Insert a single sample.
     * 
     * @param sample The sample to insert
     * @return The row ID of the inserted sample
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CpuGpuLoadSample sample);

    /**
     * Insert multiple samples in batch.
     * 
     * @param samples List of samples to insert
     * @return List of row IDs
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<CpuGpuLoadSample> samples);

    // =========================================================================
    // QUERY BY TIME
    // =========================================================================

    /**
     * Get all samples since a timestamp, newest first.
     */
    @Query("SELECT * FROM cpu_gpu_load_samples WHERE timestamp > :since ORDER BY timestamp DESC")
    List<CpuGpuLoadSample> getSamplesSince(long since);

    /**
     * Get samples within a time range.
     */
    @Query("SELECT * FROM cpu_gpu_load_samples WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    List<CpuGpuLoadSample> getSamplesInRange(long start, long end);

    /**
     * Get the most recent samples.
     */
    @Query("SELECT * FROM cpu_gpu_load_samples ORDER BY id DESC LIMIT :limit")
    List<CpuGpuLoadSample> getRecentSamples(int limit);

    /**
     * Get samples since a given ID (for sync).
     */
    @Query("SELECT * FROM cpu_gpu_load_samples WHERE id > :lastId ORDER BY id ASC LIMIT :limit")
    List<CpuGpuLoadSample> getSamplesSinceId(long lastId, int limit);

    // =========================================================================
    // QUERY BY PACKAGE
    // =========================================================================

    /**
     * Get samples for a specific package since a timestamp.
     */
    @Query("SELECT * FROM cpu_gpu_load_samples WHERE packageName = :pkg AND timestamp > :since ORDER BY timestamp DESC")
    List<CpuGpuLoadSample> getSamplesForPackage(String pkg, long since);

    /**
     * Get unique packages with samples in a time range.
     */
    @Query("SELECT DISTINCT packageName FROM cpu_gpu_load_samples WHERE packageName IS NOT NULL AND timestamp > :since")
    List<String> getActivePackages(long since);

    // =========================================================================
    // QUERY BY STRESS
    // =========================================================================

    /**
     * Get high stress samples (HIGH or CRITICAL).
     */
    @Query("SELECT * FROM cpu_gpu_load_samples WHERE stressCategory IN ('HIGH', 'CRITICAL') AND timestamp > :since ORDER BY timestamp DESC")
    List<CpuGpuLoadSample> getStressSamples(long since);

    /**
     * Get samples by specific stress category.
     */
    @Query("SELECT * FROM cpu_gpu_load_samples WHERE stressCategory = :category AND timestamp > :since ORDER BY timestamp DESC")
    List<CpuGpuLoadSample> getSamplesByStressCategory(String category, long since);

    /**
     * Count samples by stress category in a time range.
     */
    @Query("SELECT COUNT(*) FROM cpu_gpu_load_samples WHERE stressCategory = :category AND timestamp BETWEEN :start AND :end")
    int countByStressCategory(String category, long start, long end);

    // =========================================================================
    // AGGREGATION QUERIES
    // =========================================================================

    /**
     * Get average CPU for a time period.
     */
    @Query("SELECT AVG(systemCpuPercent) FROM cpu_gpu_load_samples WHERE systemCpuPercent >= 0 AND timestamp BETWEEN :start AND :end")
    float getAvgCpuForPeriod(long start, long end);

    /**
     * Get average GPU for a time period (only valid readings).
     */
    @Query("SELECT AVG(gpuPercent) FROM cpu_gpu_load_samples WHERE gpuPercent >= 0 AND timestamp BETWEEN :start AND :end")
    float getAvgGpuForPeriod(long start, long end);

    /**
     * Get average stress score for a time period.
     */
    @Query("SELECT AVG(stressScore) FROM cpu_gpu_load_samples WHERE timestamp BETWEEN :start AND :end")
    float getAvgStressForPeriod(long start, long end);

    /**
     * Get max stress score for a time period.
     */
    @Query("SELECT MAX(stressScore) FROM cpu_gpu_load_samples WHERE timestamp BETWEEN :start AND :end")
    float getMaxStressForPeriod(long start, long end);

    /**
     * Get average CPU for a specific package.
     */
    @Query("SELECT AVG(appCpuPercent) FROM cpu_gpu_load_samples WHERE packageName = :pkg AND appCpuPercent >= 0 AND timestamp BETWEEN :start AND :end")
    float getAvgCpuForPackage(String pkg, long start, long end);

    /**
     * Get peak temperature for a time period.
     */
    @Query("SELECT MAX(temperature) FROM cpu_gpu_load_samples WHERE timestamp BETWEEN :start AND :end")
    float getPeakTempForPeriod(long start, long end);

    /**
     * Count throttle events in a time period.
     */
    @Query("SELECT COUNT(*) FROM cpu_gpu_load_samples WHERE isThrottled = 1 AND timestamp BETWEEN :start AND :end")
    int countThrottleEvents(long start, long end);

    // =========================================================================
    // VALIDATION QUERIES
    // =========================================================================

    /**
     * Get samples with validation issues.
     */
    @Query("SELECT * FROM cpu_gpu_load_samples WHERE validationStatus = 'invalid' AND timestamp > :since ORDER BY timestamp DESC")
    List<CpuGpuLoadSample> getInvalidSamples(long since);

    /**
     * Count valid samples in a time period.
     */
    @Query("SELECT COUNT(*) FROM cpu_gpu_load_samples WHERE validationStatus = 'valid' AND timestamp BETWEEN :start AND :end")
    int countValidSamples(long start, long end);

    // =========================================================================
    // CLEANUP & MAINTENANCE
    // =========================================================================

    /**
     * Delete samples older than a timestamp.
     */
    @Query("DELETE FROM cpu_gpu_load_samples WHERE timestamp < :before")
    int deleteOlderThan(long before);

    /**
     * Delete all samples for a specific package.
     */
    @Query("DELETE FROM cpu_gpu_load_samples WHERE packageName = :pkg")
    int deleteForPackage(String pkg);

    /**
     * Get total sample count.
     */
    @Query("SELECT COUNT(*) FROM cpu_gpu_load_samples")
    int getCount();

    /**
     * Get sample count since timestamp.
     */
    @Query("SELECT COUNT(*) FROM cpu_gpu_load_samples WHERE timestamp > :since")
    int getCountSince(long since);

    /**
     * Get the latest sample timestamp.
     */
    @Query("SELECT MAX(timestamp) FROM cpu_gpu_load_samples")
    Long getLatestTimestamp();

    /**
     * Get the latest sample ID (for sync watermark).
     */
    @Query("SELECT MAX(id) FROM cpu_gpu_load_samples")
    Long getLatestId();
}
