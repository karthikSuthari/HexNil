package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.IdleDrainSession;

import java.util.List;

@Dao
public interface IdleDrainSessionDao {
    
    @Insert
    long insert(IdleDrainSession session);
    
    @Update
    void update(IdleDrainSession session);
    
    @Delete
    void delete(IdleDrainSession session);
    
    @Query("SELECT * FROM idle_drain_sessions ORDER BY startTime DESC LIMIT 1")
    IdleDrainSession getLatest();
    
    @Query("SELECT * FROM idle_drain_sessions ORDER BY startTime DESC")
    List<IdleDrainSession> getAll();
    
    @Query("SELECT COUNT(*) FROM idle_drain_sessions")
    int getCount();
    
    @Query("SELECT * FROM idle_drain_sessions WHERE endTime = 0 LIMIT 1")
    IdleDrainSession getActiveSession();
    
    @Query("SELECT * FROM idle_drain_sessions WHERE startTime >= :startTime ORDER BY startTime DESC")
    List<IdleDrainSession> getSessionsSince(long startTime);
    
    @Query("SELECT * FROM idle_drain_sessions ORDER BY startTime DESC LIMIT :limit")
    List<IdleDrainSession> getRecentSessions(int limit);
    
    @Query("SELECT AVG(drainPerHour) FROM idle_drain_sessions WHERE startTime >= :startTime")
    float getAverageIdleDrain(long startTime);
    
    @Query("SELECT AVG(dozeEffectiveness) FROM idle_drain_sessions WHERE dozeActive = 1 AND startTime >= :startTime")
    float getAverageDozeEffectiveness(long startTime);
    
    @Query("SELECT AVG(deepSleepPercentage) FROM idle_drain_sessions WHERE startTime >= :startTime")
    float getAverageDeepSleep(long startTime);
    
    @Query("SELECT * FROM idle_drain_sessions WHERE hasLeakage = 1 AND startTime >= :startTime")
    List<IdleDrainSession> getLeakageSessions(long startTime);
    
    @Query("SELECT * FROM idle_drain_sessions WHERE benchmarkResult = 'failing' OR benchmarkResult = 'poor'")
    List<IdleDrainSession> getFailingBenchmarks();
    
    @Query("SELECT SUM(drainAmount) FROM idle_drain_sessions WHERE startTime >= :startTime")
    float getTotalIdleDrain(long startTime);
    
    @Query("SELECT SUM(endTime - startTime) FROM idle_drain_sessions WHERE startTime >= :startTime")
    long getTotalIdleTime(long startTime);
    
    @Query("DELETE FROM idle_drain_sessions WHERE startTime < :beforeTime")
    void deleteOlderThan(long beforeTime);
}
