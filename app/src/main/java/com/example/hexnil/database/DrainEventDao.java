package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.DrainEvent;

import java.util.List;

@Dao
public interface DrainEventDao {
    
    @Insert
    long insert(DrainEvent event);
    
    @Update
    void update(DrainEvent event);
    
    @Delete
    void delete(DrainEvent event);
    
    @Query("SELECT * FROM drain_events ORDER BY startTime DESC LIMIT 1")
    DrainEvent getLatest();
    
    @Query("SELECT * FROM drain_events WHERE endTime = 0 LIMIT 1")
    DrainEvent getActiveEvent();
    
    @Query("SELECT * FROM drain_events WHERE startTime >= :startTime ORDER BY startTime DESC")
    List<DrainEvent> getEventsSince(long startTime);
    
    @Query("SELECT * FROM drain_events ORDER BY startTime DESC LIMIT :limit")
    List<DrainEvent> getRecentEvents(int limit);
    
    @Query("SELECT * FROM drain_events WHERE isAnomalous = 1 AND startTime >= :startTime")
    List<DrainEvent> getAnomalousEvents(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE screenOn = 1 AND startTime >= :startTime")
    List<DrainEvent> getScreenOnDrainEvents(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE screenOn = 0 AND startTime >= :startTime")
    List<DrainEvent> getScreenOffDrainEvents(long startTime);
    
    @Query("SELECT AVG(drainRatePerHour) FROM drain_events WHERE screenOn = 1 AND startTime >= :startTime")
    float getAverageScreenOnDrain(long startTime);
    
    @Query("SELECT AVG(drainRatePerHour) FROM drain_events WHERE screenOn = 0 AND startTime >= :startTime")
    float getAverageScreenOffDrain(long startTime);
    
    @Query("SELECT SUM(drainAmount) FROM drain_events WHERE startTime >= :startTime")
    int getTotalDrain(long startTime);
    
    @Query("SELECT * FROM drain_events WHERE drainCategory = :category AND startTime >= :startTime")
    List<DrainEvent> getEventsByCategory(String category, long startTime);
    
    @Query("SELECT * FROM drain_events WHERE drainRatePerHour > :threshold AND startTime >= :startTime")
    List<DrainEvent> getHighDrainEvents(float threshold, long startTime);
    
    @Query("DELETE FROM drain_events WHERE startTime < :beforeTime")
    void deleteOlderThan(long beforeTime);
}
