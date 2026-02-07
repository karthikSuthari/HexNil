package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.PreUpdateSnapshot;

import java.util.List;

/**
 * DAO for PreUpdateSnapshot table operations.
 */
@Dao
public interface PreUpdateSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(PreUpdateSnapshot snapshot);

    @Update
    void update(PreUpdateSnapshot snapshot);

    @Delete
    void delete(PreUpdateSnapshot snapshot);

    @Query("SELECT * FROM pre_update_snapshots WHERE id = :id")
    PreUpdateSnapshot getById(long id);

    @Query("SELECT * FROM pre_update_snapshots WHERE sessionId = :sessionId ORDER BY capturedAt DESC LIMIT 1")
    PreUpdateSnapshot getBySessionId(long sessionId);

    @Query("SELECT * FROM pre_update_snapshots ORDER BY capturedAt DESC LIMIT :limit")
    List<PreUpdateSnapshot> getRecent(int limit);

    @Query("SELECT COUNT(*) FROM pre_update_snapshots")
    int getCount();

    @Query("SELECT COUNT(*) FROM pre_update_snapshots WHERE sessionId = :sessionId")
    int getCountBySession(long sessionId);

    @Query("DELETE FROM pre_update_snapshots WHERE sessionId = :sessionId")
    void deleteBySession(long sessionId);

    @Query("SELECT * FROM pre_update_snapshots WHERE id > :lastId ORDER BY id ASC")
    List<PreUpdateSnapshot> getNewRecords(long lastId);
}
