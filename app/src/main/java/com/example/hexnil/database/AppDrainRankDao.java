package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.AppDrainRank;

import java.util.List;

@Dao
public interface AppDrainRankDao {
    @Insert
    void insertAll(List<AppDrainRank> ranks);

    @Query("SELECT * FROM app_drain_ranks WHERE windowLabel = :label ORDER BY rank ASC")
    List<AppDrainRank> getByWindow(String label);

    @Query("DELETE FROM app_drain_ranks WHERE windowEnd < :before")
    void deleteOlderThan(long before);
}
