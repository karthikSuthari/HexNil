package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.MLModel;

import java.util.List;

/**
 * DAO for ml_models — ML model lifecycle management.
 */
@Dao
public interface MLModelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MLModel model);

    @Update
    void update(MLModel model);

    @Delete
    void delete(MLModel model);

    @Query("SELECT * FROM ml_models WHERE id = :id")
    MLModel getById(long id);

    @Query("SELECT * FROM ml_models WHERE modelName = :name AND isActive = 1 LIMIT 1")
    MLModel getActiveModel(String name);

    @Query("SELECT * FROM ml_models WHERE modelName = :name AND modelVersion = :version LIMIT 1")
    MLModel getByNameAndVersion(String name, String version);

    @Query("SELECT * FROM ml_models WHERE taskType = :taskType AND isActive = 1 ORDER BY deployedAt DESC")
    List<MLModel> getActiveModelsForTask(String taskType);

    @Query("SELECT * FROM ml_models WHERE isActive = 1 ORDER BY deployedAt DESC")
    List<MLModel> getAllActiveModels();

    @Query("SELECT * FROM ml_models ORDER BY trainedOn DESC")
    List<MLModel> getAll();

    @Query("SELECT * FROM ml_models WHERE modelName = :name ORDER BY trainedOn DESC")
    List<MLModel> getVersionHistory(String name);

    @Query("SELECT COUNT(*) FROM ml_models")
    int getCount();

    @Query("SELECT COUNT(*) FROM ml_models WHERE isActive = 1")
    int getActiveCount();

    /** Deactivate all versions of a model (before activating a new one) */
    @Query("UPDATE ml_models SET isActive = 0, retiredAt = :retiredAt WHERE modelName = :name AND isActive = 1")
    void deactivateAllVersions(String name, long retiredAt);

    @Query("DELETE FROM ml_models WHERE retiredAt IS NOT NULL AND retiredAt < :beforeTime")
    void deleteOldRetired(long beforeTime);
}
