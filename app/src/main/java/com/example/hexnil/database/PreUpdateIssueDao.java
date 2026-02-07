package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.PreUpdateIssue;

import java.util.List;

/**
 * DAO for PreUpdateIssue table operations.
 */
@Dao
public interface PreUpdateIssueDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(PreUpdateIssue issue);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    List<Long> insertAll(List<PreUpdateIssue> issues);

    @Update
    void update(PreUpdateIssue issue);

    @Delete
    void delete(PreUpdateIssue issue);

    @Query("SELECT * FROM pre_update_issues WHERE id = :id")
    PreUpdateIssue getById(long id);

    @Query("SELECT * FROM pre_update_issues WHERE issueUuid = :uuid")
    PreUpdateIssue getByUuid(String uuid);

    @Query("SELECT * FROM pre_update_issues WHERE sessionId = :sessionId ORDER BY severity DESC, impactScore DESC")
    List<PreUpdateIssue> getBySessionId(long sessionId);

    @Query("SELECT * FROM pre_update_issues WHERE sessionId = :sessionId AND severity = :severity")
    List<PreUpdateIssue> getBySessionAndSeverity(long sessionId, String severity);

    @Query("SELECT * FROM pre_update_issues WHERE sessionId = :sessionId AND issueType = :type")
    List<PreUpdateIssue> getBySessionAndType(long sessionId, String type);

    @Query("SELECT COUNT(*) FROM pre_update_issues WHERE sessionId = :sessionId")
    int getCountBySession(long sessionId);

    @Query("SELECT COUNT(*) FROM pre_update_issues WHERE sessionId = :sessionId AND severity IN ('high', 'critical')")
    int getHighSeverityCountBySession(long sessionId);

    @Query("DELETE FROM pre_update_issues WHERE sessionId = :sessionId")
    void deleteBySession(long sessionId);

    @Query("SELECT * FROM pre_update_issues WHERE id > :lastId ORDER BY id ASC")
    List<PreUpdateIssue> getNewRecords(long lastId);
}
