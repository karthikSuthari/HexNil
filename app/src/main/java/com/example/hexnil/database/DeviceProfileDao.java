package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.DeviceProfile;

import java.util.List;

/**
 * DAO for device_profiles — per-device-model hardware specs and fleet statistics.
 */
@Dao
public interface DeviceProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(DeviceProfile profile);

    @Update
    void update(DeviceProfile profile);

    @Delete
    void delete(DeviceProfile profile);

    @Query("SELECT * FROM device_profiles WHERE id = :id")
    DeviceProfile getById(long id);

    @Query("SELECT * FROM device_profiles WHERE deviceFingerprint = :fingerprint")
    DeviceProfile getByFingerprint(String fingerprint);

    @Query("SELECT * FROM device_profiles WHERE manufacturer = :manufacturer AND model = :model LIMIT 1")
    DeviceProfile getByManufacturerAndModel(String manufacturer, String model);

    @Query("SELECT * FROM device_profiles WHERE soc = :soc ORDER BY deviceCount DESC")
    List<DeviceProfile> getBySoc(String soc);

    @Query("SELECT * FROM device_profiles WHERE manufacturer = :manufacturer ORDER BY deviceCount DESC")
    List<DeviceProfile> getByManufacturer(String manufacturer);

    @Query("SELECT * FROM device_profiles ORDER BY deviceCount DESC")
    List<DeviceProfile> getAll();

    @Query("SELECT * FROM device_profiles ORDER BY deviceCount DESC LIMIT :limit")
    List<DeviceProfile> getTopDevices(int limit);

    @Query("SELECT COUNT(*) FROM device_profiles")
    int getCount();

    @Query("SELECT COUNT(*) FROM device_profiles WHERE idleDrainMean IS NOT NULL")
    int getCountWithBaselines();

    @Query("SELECT SUM(deviceCount) FROM device_profiles")
    int getTotalDeviceCount();

    @Query("DELETE FROM device_profiles WHERE lastUpdated < :beforeTime AND deviceCount <= 1")
    void deleteStale(long beforeTime);

    @Query("SELECT * FROM device_profiles WHERE id > :lastId ORDER BY id ASC")
    List<DeviceProfile> getNewRecords(long lastId);
}
