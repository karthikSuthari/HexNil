package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.BatteryHealth;

import java.util.List;

@Dao
public interface BatteryHealthDao {
    
    @Insert
    long insert(BatteryHealth health);
    
    @Update
    void update(BatteryHealth health);
    
    @Delete
    void delete(BatteryHealth health);
    
    @Query("SELECT * FROM battery_health ORDER BY timestamp DESC LIMIT 1")
    BatteryHealth getLatest();
    
    @Query("SELECT * FROM battery_health WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<BatteryHealth> getHealthHistorySince(long startTime);
    
    @Query("SELECT * FROM battery_health ORDER BY timestamp DESC LIMIT :limit")
    List<BatteryHealth> getRecentHealthRecords(int limit);
    
    @Query("SELECT AVG(healthPercentage) FROM battery_health WHERE timestamp >= :startTime")
    float getAverageHealth(long startTime);
    
    @Query("SELECT MIN(healthPercentage) FROM battery_health WHERE timestamp >= :startTime")
    float getMinHealth(long startTime);
    
    @Query("SELECT * FROM battery_health WHERE hasAbnormalWear = 1")
    List<BatteryHealth> getAbnormalWearRecords();
    
    @Query("SELECT * FROM battery_health WHERE hasSwellingRisk = 1")
    List<BatteryHealth> getSwellingRiskRecords();
    
    @Query("SELECT * FROM battery_health WHERE needsCalibration = 1 ORDER BY timestamp DESC LIMIT 1")
    BatteryHealth getCalibrationNeededRecord();
    
    @Query("SELECT AVG(degradationRate) FROM battery_health WHERE timestamp >= :startTime")
    float getAverageDegradationRate(long startTime);
    
    @Query("SELECT AVG(estimatedCycles) FROM battery_health ORDER BY timestamp DESC LIMIT 10")
    float getRecentAverageCycles();

    @Query("SELECT AVG(healthPercentage) FROM battery_health WHERE manufacturer = :supplier")
    float getAverageHealthBySupplier(String supplier);

    @Query("SELECT AVG(healthPercentage) FROM battery_health WHERE batchId = :batch")
    float getAverageHealthByBatch(String batch);
    
    @Query("DELETE FROM battery_health WHERE timestamp < :beforeTime")
    void deleteOlderThan(long beforeTime);
    
    @Query("SELECT COUNT(*) FROM battery_health")
    int getTotalRecords();
}
