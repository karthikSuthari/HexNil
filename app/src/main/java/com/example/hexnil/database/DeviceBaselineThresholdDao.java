package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.DeviceBaselineThreshold;

import java.util.List;

/**
 * DAO for device_baseline_thresholds — per-device-model per-metric statistical baselines.
 * Replaces universal hardcoded thresholds with data-driven thresholds.
 */
@Dao
public interface DeviceBaselineThresholdDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(DeviceBaselineThreshold threshold);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<DeviceBaselineThreshold> thresholds);

    @Update
    void update(DeviceBaselineThreshold threshold);

    @Delete
    void delete(DeviceBaselineThreshold threshold);

    @Query("SELECT * FROM device_baseline_thresholds WHERE id = :id")
    DeviceBaselineThreshold getById(long id);

    /** Primary lookup: get threshold for a specific device and metric */
    @Query("SELECT * FROM device_baseline_thresholds WHERE deviceFingerprint = :fingerprint AND metricName = :metricName LIMIT 1")
    DeviceBaselineThreshold getThreshold(String fingerprint, String metricName);

    /** Get all thresholds for a device */
    @Query("SELECT * FROM device_baseline_thresholds WHERE deviceFingerprint = :fingerprint ORDER BY metricName")
    List<DeviceBaselineThreshold> getAllForDevice(String fingerprint);

    /** Get thresholds for a metric across all devices (for fleet analysis) */
    @Query("SELECT * FROM device_baseline_thresholds WHERE metricName = :metricName ORDER BY sampleSize DESC")
    List<DeviceBaselineThreshold> getAllForMetric(String metricName);

    /** Get only high-confidence thresholds (sampleSize >= threshold) */
    @Query("SELECT * FROM device_baseline_thresholds WHERE deviceFingerprint = :fingerprint AND sampleSize >= :minSampleSize ORDER BY metricName")
    List<DeviceBaselineThreshold> getReliableThresholds(String fingerprint, int minSampleSize);

    @Query("SELECT COUNT(*) FROM device_baseline_thresholds WHERE deviceFingerprint = :fingerprint")
    int getCountForDevice(String fingerprint);

    @Query("SELECT COUNT(DISTINCT deviceFingerprint) FROM device_baseline_thresholds")
    int getDistinctDeviceCount();

    @Query("DELETE FROM device_baseline_thresholds WHERE deviceFingerprint = :fingerprint")
    void deleteAllForDevice(String fingerprint);

    @Query("DELETE FROM device_baseline_thresholds WHERE lastUpdated < :beforeTime")
    void deleteStale(long beforeTime);

    @Query("SELECT * FROM device_baseline_thresholds WHERE id > :lastId ORDER BY id ASC")
    List<DeviceBaselineThreshold> getNewRecords(long lastId);
}
