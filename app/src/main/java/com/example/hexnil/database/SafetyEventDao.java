package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.SafetyEvent;

import java.util.List;

@Dao
public interface SafetyEventDao {
    
    @Insert
    long insert(SafetyEvent event);
    
    @Update
    void update(SafetyEvent event);
    
    @Delete
    void delete(SafetyEvent event);
    
    @Query("SELECT * FROM safety_events ORDER BY timestamp DESC LIMIT 1")
    SafetyEvent getLatest();
    
    @Query("SELECT * FROM safety_events ORDER BY timestamp DESC")
    List<SafetyEvent> getAll();
    
    @Query("SELECT COUNT(*) FROM safety_events")
    int getCount();
    
    @Query("SELECT * FROM safety_events WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    List<SafetyEvent> getEventsSince(long startTime);
    
    @Query("SELECT * FROM safety_events ORDER BY timestamp DESC LIMIT :limit")
    List<SafetyEvent> getRecentEvents(int limit);
    
    @Query("SELECT * FROM safety_events WHERE severity = 'critical' OR severity = 'emergency'")
    List<SafetyEvent> getCriticalEvents();
    
    @Query("SELECT * FROM safety_events WHERE eventType = :type AND timestamp >= :startTime")
    List<SafetyEvent> getEventsByType(String type, long startTime);
    
    @Query("SELECT * FROM safety_events WHERE exceedsRegulatory = 1")
    List<SafetyEvent> getRegulatoryViolations();
    
    @Query("SELECT * FROM safety_events WHERE requiresReport = 1")
    List<SafetyEvent> getReportableEvents();
    
    @Query("SELECT COUNT(*) FROM safety_events WHERE severity = 'critical' OR severity = 'emergency'")
    int getCriticalEventCount();
    
    @Query("SELECT COUNT(*) FROM safety_events WHERE eventType = 'over_temp' AND timestamp >= :startTime")
    int getOverTempEventCount(long startTime);
    
    @Query("SELECT COUNT(*) FROM safety_events WHERE eventType = 'over_voltage' AND timestamp >= :startTime")
    int getOverVoltageEventCount(long startTime);
    
    @Query("SELECT COUNT(*) FROM safety_events WHERE eventType = 'over_current' AND timestamp >= :startTime")
    int getOverCurrentEventCount(long startTime);
    
    @Query("SELECT COUNT(*) FROM safety_events WHERE abuseType IS NOT NULL AND timestamp >= :startTime")
    int getAbuseEventCount(long startTime);
    
    @Query("SELECT * FROM safety_events WHERE swellingRiskScore > 0.5")
    List<SafetyEvent> getSwellingRiskEvents();
    
    @Query("DELETE FROM safety_events WHERE timestamp < :beforeTime")
    void deleteOlderThan(long beforeTime);
}
