package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.ThermalEvent;

import java.util.List;

@Dao
public interface ThermalEventDao {
    
    @Insert
    long insert(ThermalEvent event);
    
    @Update
    void update(ThermalEvent event);
    
    @Delete
    void delete(ThermalEvent event);
    
    @Query("SELECT * FROM thermal_events ORDER BY timestamp DESC LIMIT 1")
    ThermalEvent getLatest();
    
    @Query("SELECT * FROM thermal_events WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    List<ThermalEvent> getEventsSince(long startTime);
    
    @Query("SELECT * FROM thermal_events ORDER BY timestamp DESC LIMIT :limit")
    List<ThermalEvent> getRecentEvents(int limit);
    
    @Query("SELECT * FROM thermal_events WHERE severity = 'critical' AND timestamp >= :startTime")
    List<ThermalEvent> getCriticalEvents(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE eventType = :type AND timestamp >= :startTime")
    List<ThermalEvent> getEventsByType(String type, long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE isCharging = 1 AND timestamp >= :startTime")
    List<ThermalEvent> getChargingThermalEvents(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE isGaming = 1 AND timestamp >= :startTime")
    List<ThermalEvent> getGamingThermalEvents(long startTime);
    
    @Query("SELECT MAX(peakTemperature) FROM thermal_events WHERE timestamp >= :startTime")
    float getMaxPeakTemperature(long startTime);
    
    @Query("SELECT AVG(peakTemperature) FROM thermal_events WHERE timestamp >= :startTime")
    float getAveragePeakTemperature(long startTime);
    
    @Query("SELECT COUNT(*) FROM thermal_events WHERE severity = 'critical'")
    int getCriticalEventCount();
    
    @Query("SELECT COUNT(*) FROM thermal_events WHERE timestamp >= :startTime")
    int getEventCount(long startTime);
    
    @Query("SELECT * FROM thermal_events WHERE foregroundApp = :packageName AND timestamp >= :startTime")
    List<ThermalEvent> getEventsByApp(String packageName, long startTime);
    
    @Query("DELETE FROM thermal_events WHERE timestamp < :beforeTime")
    void deleteOlderThan(long beforeTime);
}
