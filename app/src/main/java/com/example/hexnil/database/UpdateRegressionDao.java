package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.UpdateRegression;

import java.util.List;

/**
 * DAO for UpdateRegression table operations.
 */
@Dao
public interface UpdateRegressionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(UpdateRegression regression);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    List<Long> insertAll(List<UpdateRegression> regressions);

    @Update
    void update(UpdateRegression regression);

    @Delete
    void delete(UpdateRegression regression);

    @Query("SELECT * FROM update_regressions WHERE id = :id")
    UpdateRegression getById(long id);

    @Query("SELECT * FROM update_regressions WHERE regressionUuid = :uuid")
    UpdateRegression getByUuid(String uuid);

    @Query("SELECT * FROM update_regressions WHERE sessionId = :sessionId ORDER BY severity DESC, impactScore DESC")
    List<UpdateRegression> getBySessionId(long sessionId);

    @Query("SELECT * FROM update_regressions WHERE sessionId = :sessionId AND severity = :severity")
    List<UpdateRegression> getBySessionAndSeverity(long sessionId, String severity);

    @Query("SELECT * FROM update_regressions WHERE sessionId = :sessionId AND regressionType = :type")
    List<UpdateRegression> getBySessionAndType(long sessionId, String type);

    @Query("SELECT COUNT(*) FROM update_regressions WHERE sessionId = :sessionId")
    int getCountBySession(long sessionId);

    @Query("SELECT COUNT(*) FROM update_regressions WHERE sessionId = :sessionId AND severity IN ('high', 'critical')")
    int getHighSeverityCountBySession(long sessionId);

    @Query("SELECT COUNT(*) FROM update_regressions WHERE sessionId = :sessionId AND userNoticeable = 1")
    int getUserNoticeableCountBySession(long sessionId);

    @Query("UPDATE update_regressions SET acknowledged = 1 WHERE id = :id")
    void acknowledge(long id);

    @Query("UPDATE update_regressions SET userReported = 1 WHERE id = :id")
    void markAsReported(long id);

    @Query("DELETE FROM update_regressions WHERE sessionId = :sessionId")
    void deleteBySession(long sessionId);

    @Query("SELECT * FROM update_regressions WHERE id > :lastId ORDER BY id ASC")
    List<UpdateRegression> getNewRecords(long lastId);
}
