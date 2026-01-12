package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.ThermalHeatBucket;

import java.util.List;

@Dao
public interface ThermalHeatBucketDao {
    @Insert
    long insert(ThermalHeatBucket bucket);

    @Update
    void update(ThermalHeatBucket bucket);

    @Query("SELECT * FROM thermal_heat_buckets WHERE startTime >= :start ORDER BY startTime ASC")
    List<ThermalHeatBucket> getBucketsSince(long start);

    @Query("SELECT * FROM thermal_heat_buckets WHERE tempBucket = :bucket ORDER BY startTime DESC LIMIT 10")
    List<ThermalHeatBucket> getRecentByBucket(String bucket);

    @Query("DELETE FROM thermal_heat_buckets WHERE endTime < :before")
    void deleteOlderThan(long before);
}
