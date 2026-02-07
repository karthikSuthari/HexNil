package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.UpdateSession;

import java.util.List;

/**
 * DAO for UpdateSession table operations.
 */
@Dao
public interface UpdateSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(UpdateSession session);

    @Update
    void update(UpdateSession session);

    @Delete
    void delete(UpdateSession session);

    @Query("SELECT * FROM update_sessions WHERE id = :id")
    UpdateSession getById(long id);

    @Query("SELECT * FROM update_sessions WHERE sessionUuid = :uuid")
    UpdateSession getByUuid(String uuid);

    @Query("SELECT * FROM update_sessions WHERE status = 'pending' ORDER BY detectedAt DESC LIMIT 1")
    UpdateSession getPendingSession();

    @Query("SELECT * FROM update_sessions WHERE status = 'baseline_locked' ORDER BY detectedAt DESC LIMIT 1")
    UpdateSession getBaselineLockedSession();

    @Query("SELECT * FROM update_sessions WHERE status IN ('pending', 'baseline_locked', 'installed_monitoring', 'analyzing') ORDER BY detectedAt DESC LIMIT 1")
    UpdateSession getActiveSession();

    @Query("SELECT * FROM update_sessions WHERE status = 'installed_monitoring' ORDER BY updateInstalledAt DESC LIMIT 1")
    UpdateSession getMonitoringSession();

    @Query("SELECT * FROM update_sessions ORDER BY detectedAt DESC LIMIT :limit")
    List<UpdateSession> getRecent(int limit);

    @Query("SELECT * FROM update_sessions WHERE status = 'verified' ORDER BY verificationCompletedAt DESC LIMIT :limit")
    List<UpdateSession> getVerifiedSessions(int limit);

    @Query("SELECT COUNT(*) FROM update_sessions")
    int getCount();

    @Query("SELECT COUNT(*) FROM update_sessions WHERE status = :status")
    int getCountByStatus(String status);

    @Query("SELECT * FROM update_sessions WHERE previousBuildFingerprint = :fingerprint ORDER BY detectedAt DESC LIMIT 1")
    UpdateSession getByPreviousFingerprint(String fingerprint);

    @Query("DELETE FROM update_sessions WHERE status = 'pending' AND detectedAt < :cutoffTime")
    void deleteOldPendingSessions(long cutoffTime);

    @Query("SELECT * FROM update_sessions WHERE id > :lastId ORDER BY id ASC")
    List<UpdateSession> getNewRecords(long lastId);
}
