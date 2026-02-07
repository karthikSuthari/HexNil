package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.PostUpdateVerification;

import java.util.List;

/**
 * DAO for PostUpdateVerification table operations.
 */
@Dao
public interface PostUpdateVerificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PostUpdateVerification verification);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<PostUpdateVerification> verifications);

    @Update
    void update(PostUpdateVerification verification);

    @Delete
    void delete(PostUpdateVerification verification);

    @Query("SELECT * FROM post_update_verifications WHERE id = :id")
    PostUpdateVerification getById(long id);

    @Query("SELECT * FROM post_update_verifications WHERE sessionId = :sessionId ORDER BY verifiedAt DESC")
    List<PostUpdateVerification> getBySessionId(long sessionId);

    @Query("SELECT * FROM post_update_verifications WHERE preUpdateIssueId = :issueId")
    PostUpdateVerification getByIssueId(long issueId);

    @Query("SELECT * FROM post_update_verifications WHERE sessionId = :sessionId AND verificationStatus = :status")
    List<PostUpdateVerification> getBySessionAndStatus(long sessionId, String status);

    @Query("SELECT COUNT(*) FROM post_update_verifications WHERE sessionId = :sessionId")
    int getCountBySession(long sessionId);

    @Query("SELECT COUNT(*) FROM post_update_verifications WHERE sessionId = :sessionId AND verificationStatus = 'fixed'")
    int getFixedCountBySession(long sessionId);

    @Query("SELECT COUNT(*) FROM post_update_verifications WHERE sessionId = :sessionId AND verificationStatus = 'improved'")
    int getImprovedCountBySession(long sessionId);

    @Query("SELECT COUNT(*) FROM post_update_verifications WHERE sessionId = :sessionId AND verificationStatus = 'unchanged'")
    int getUnchangedCountBySession(long sessionId);

    @Query("SELECT COUNT(*) FROM post_update_verifications WHERE sessionId = :sessionId AND verificationStatus = 'worsened'")
    int getWorsenedCountBySession(long sessionId);

    @Query("DELETE FROM post_update_verifications WHERE sessionId = :sessionId")
    void deleteBySession(long sessionId);

    @Query("SELECT * FROM post_update_verifications WHERE id > :lastId ORDER BY id ASC")
    List<PostUpdateVerification> getNewRecords(long lastId);
}
