package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.ChargingSession;

import java.util.List;

@Dao
public interface ChargingSessionDao {
    
    @Insert
    long insert(ChargingSession session);
    
    @Update
    void update(ChargingSession session);
    
    @Delete
    void delete(ChargingSession session);
    
    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC LIMIT 1")
    ChargingSession getLatest();
    
    @Query("SELECT * FROM charging_sessions WHERE endTime = 0 LIMIT 1")
    ChargingSession getActiveSession();
    
    @Query("SELECT * FROM charging_sessions WHERE startTime >= :startTime ORDER BY startTime DESC")
    List<ChargingSession> getSessionsSince(long startTime);
    
    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC LIMIT :limit")
    List<ChargingSession> getRecentSessions(int limit);
    
    @Query("SELECT * FROM charging_sessions WHERE safetyStatus = 'danger' OR safetyStatus = 'warning'")
    List<ChargingSession> getUnsafeSessions();
    
    @Query("SELECT * FROM charging_sessions WHERE isFastCharging = 1 AND startTime >= :startTime")
    List<ChargingSession> getFastChargingSessions(long startTime);
    
    @Query("SELECT AVG(chargingSpeed) FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime")
    float getAverageChargingSpeed(long startTime);
    
    @Query("SELECT AVG(maxTemperature) FROM charging_sessions WHERE endTime > 0 AND startTime >= :startTime")
    float getAverageChargingTemp(long startTime);
    
    @Query("SELECT COUNT(*) FROM charging_sessions WHERE overCurrentEvents > 0")
    int getOverCurrentSessionCount();
    
    @Query("SELECT COUNT(*) FROM charging_sessions WHERE overVoltageEvents > 0")
    int getOverVoltageSessionCount();
    
    @Query("SELECT COUNT(*) FROM charging_sessions WHERE overTempEvents > 0")
    int getOverTempSessionCount();
    
    @Query("SELECT SUM(chargedAmount) FROM charging_sessions WHERE startTime >= :startTime")
    int getTotalChargedAmount(long startTime);
    
    @Query("SELECT * FROM charging_sessions WHERE chargerQuality = :quality")
    List<ChargingSession> getSessionsByChargerQuality(String quality);
    
    @Query("DELETE FROM charging_sessions WHERE startTime < :beforeTime")
    void deleteOlderThan(long beforeTime);
}
