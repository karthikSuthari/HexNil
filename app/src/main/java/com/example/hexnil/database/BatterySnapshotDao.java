package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.BatterySnapshot;

import java.util.List;

@Dao
public interface BatterySnapshotDao {
    
    @Insert
    long insert(BatterySnapshot snapshot);
    
    @Insert
    void insertAll(List<BatterySnapshot> snapshots);
    
    @Update
    void update(BatterySnapshot snapshot);
    
    @Delete
    void delete(BatterySnapshot snapshot);
    
    @Query("SELECT * FROM battery_snapshots ORDER BY timestamp DESC LIMIT 1")
    BatterySnapshot getLatest();
    
    @Query("SELECT * FROM battery_snapshots WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    List<BatterySnapshot> getSnapshotsSince(long startTime);
    
    @Query("SELECT * FROM battery_snapshots WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    List<BatterySnapshot> getSnapshotsInRange(long startTime, long endTime);
    
    @Query("SELECT * FROM battery_snapshots ORDER BY timestamp DESC LIMIT :limit")
    List<BatterySnapshot> getRecentSnapshots(int limit);
    
    @Query("SELECT * FROM battery_snapshots WHERE isCharging = 1 AND timestamp >= :startTime")
    List<BatterySnapshot> getChargingSnapshots(long startTime);
    
    @Query("SELECT * FROM battery_snapshots WHERE isCharging = 0 AND timestamp >= :startTime")
    List<BatterySnapshot> getDischargingSnapshots(long startTime);
    
    @Query("SELECT * FROM battery_snapshots WHERE temperature > :threshold AND timestamp >= :startTime")
    List<BatterySnapshot> getHighTempSnapshots(int threshold, long startTime);
    
    @Query("SELECT AVG(level) FROM battery_snapshots WHERE timestamp >= :startTime")
    float getAverageLevel(long startTime);
    
    @Query("SELECT AVG(temperature) FROM battery_snapshots WHERE timestamp >= :startTime")
    float getAverageTemperature(long startTime);
    
    @Query("SELECT MAX(temperature) FROM battery_snapshots WHERE timestamp >= :startTime")
    float getMaxTemperature(long startTime);
    
    @Query("SELECT COUNT(*) FROM battery_snapshots")
    int getTotalCount();
    
    @Query("SELECT COUNT(*) FROM battery_snapshots WHERE timestamp >= :startTime")
    int getCountSince(long startTime);
    
    @Query("DELETE FROM battery_snapshots WHERE timestamp < :beforeTime")
    void deleteOlderThan(long beforeTime);
    
    @Query("SELECT * FROM battery_snapshots WHERE screenOn = 1 AND timestamp >= :startTime")
    List<BatterySnapshot> getScreenOnSnapshots(long startTime);
    
    @Query("SELECT * FROM battery_snapshots WHERE screenOn = 0 AND timestamp >= :startTime")
    List<BatterySnapshot> getScreenOffSnapshots(long startTime);
}
