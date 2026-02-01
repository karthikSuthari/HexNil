package com.example.hexnil.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.hexnil.model.DailyAggregate;

import java.util.List;

/**
 * DAO for DailyAggregate entity.
 * Provides CRUD operations and baseline calculation queries.
 */
@Dao
public interface DailyAggregateDao {

    // =========================================================================
    // Insert/Update Operations
    // =========================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertOrReplace(DailyAggregate aggregate);

    @Insert
    void insertAll(List<DailyAggregate> aggregates);

    @Update
    void update(DailyAggregate aggregate);

    // =========================================================================
    // Query Operations
    // =========================================================================

    @Query("SELECT * FROM daily_aggregates WHERE date = :date AND packageName IS NULL")
    DailyAggregate getDeviceLevelByDate(String date);

    @Query("SELECT * FROM daily_aggregates WHERE date = :date AND packageName = :packageName")
    DailyAggregate getByDateAndPackage(String date, String packageName);

    @Query("SELECT * FROM daily_aggregates WHERE id = :id")
    DailyAggregate getById(long id);

    @Query("SELECT * FROM daily_aggregates WHERE packageName IS NULL ORDER BY date DESC LIMIT :limit")
    List<DailyAggregate> getRecentDeviceLevel(int limit);

    @Query("SELECT * FROM daily_aggregates WHERE packageName = :packageName ORDER BY date DESC LIMIT :limit")
    List<DailyAggregate> getRecentByPackage(String packageName, int limit);

    @Query("SELECT * FROM daily_aggregates ORDER BY date DESC LIMIT 1")
    DailyAggregate getLatest();

    // =========================================================================
    // Date Range Queries
    // =========================================================================

    @Query("SELECT * FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName IS NULL ORDER BY date ASC")
    List<DailyAggregate> getDeviceLevelInRange(String startDate, String endDate);

    @Query("SELECT * FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName = :packageName ORDER BY date ASC")
    List<DailyAggregate> getByPackageInRange(String packageName, String startDate, String endDate);

    @Query("SELECT * FROM daily_aggregates WHERE date >= :startDate AND packageName IS NULL ORDER BY date ASC")
    List<DailyAggregate> getDeviceLevelSince(String startDate);

    @Query("SELECT * FROM daily_aggregates WHERE date >= :startDate AND packageName = :packageName ORDER BY date ASC")
    List<DailyAggregate> getByPackageSince(String packageName, String startDate);

    // =========================================================================
    // Baseline Calculation Queries (7-day rolling average)
    // =========================================================================

    @Query("SELECT AVG(avgColdLaunchMs) FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName = :packageName")
    float get7DayAvgLaunchMs(String packageName, String startDate, String endDate);

    @Query("SELECT AVG(avgJankPercent) FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName = :packageName")
    float get7DayAvgJankPercent(String packageName, String startDate, String endDate);

    @Query("SELECT AVG(crashCount) FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName = :packageName")
    float get7DayAvgCrashCount(String packageName, String startDate, String endDate);

    @Query("SELECT AVG(avgTemp) FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName IS NULL")
    float get7DayAvgTemp(String startDate, String endDate);

    // =========================================================================
    // Baseline Calculation Queries (30-day rolling average)
    // =========================================================================

    @Query("SELECT AVG(avgColdLaunchMs) FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName = :packageName")
    float get30DayAvgLaunchMs(String packageName, String startDate, String endDate);

    @Query("SELECT AVG(avgJankPercent) FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName = :packageName")
    float get30DayAvgJankPercent(String packageName, String startDate, String endDate);

    @Query("SELECT AVG(avgTemp) FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName IS NULL")
    float get30DayAvgTemp(String startDate, String endDate);

    // =========================================================================
    // Device-Level Aggregation
    // =========================================================================

    @Query("SELECT AVG(avgColdLaunchMs) FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName IS NULL")
    float getDeviceAvgLaunchInRange(String startDate, String endDate);

    @Query("SELECT SUM(crashCount) FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName IS NULL")
    int getDeviceTotalCrashesInRange(String startDate, String endDate);

    @Query("SELECT SUM(anrCount) FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName IS NULL")
    int getDeviceTotalAnrsInRange(String startDate, String endDate);

    // =========================================================================
    // Health Score Queries
    // =========================================================================

    @Query("SELECT AVG(healthScore) FROM daily_aggregates WHERE date BETWEEN :startDate AND :endDate AND packageName IS NULL")
    float getAverageHealthScore(String startDate, String endDate);

    @Query("SELECT * FROM daily_aggregates WHERE healthScore < 70 AND packageName IS NULL ORDER BY date DESC")
    List<DailyAggregate> getLowHealthDays();

    @Query("SELECT * FROM daily_aggregates WHERE healthGrade = 'F' AND packageName IS NULL ORDER BY date DESC")
    List<DailyAggregate> getFailingDays();

    // =========================================================================
    // Trend Detection
    // =========================================================================

    @Query("SELECT * FROM daily_aggregates WHERE hasNewCrashType = 1 AND date >= :startDate ORDER BY date DESC")
    List<DailyAggregate> getDaysWithNewCrashes(String startDate);

    @Query("SELECT * FROM daily_aggregates WHERE hasNewAnrType = 1 AND date >= :startDate ORDER BY date DESC")
    List<DailyAggregate> getDaysWithNewAnrs(String startDate);

    // =========================================================================
    // Distinct Packages
    // =========================================================================

    @Query("SELECT DISTINCT packageName FROM daily_aggregates WHERE packageName IS NOT NULL")
    List<String> getDistinctPackages();

    @Query("SELECT DISTINCT date FROM daily_aggregates ORDER BY date DESC")
    List<String> getDistinctDates();

    // =========================================================================
    // Cleanup Operations
    // =========================================================================

    @Query("DELETE FROM daily_aggregates WHERE date < :beforeDate")
    int deleteOlderThan(String beforeDate);

    @Query("DELETE FROM daily_aggregates")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM daily_aggregates")
    int getCount();

    @Query("SELECT COUNT(*) FROM daily_aggregates WHERE createdAt >= :since")
    int getAggregateCountSince(long since);

    @Query("SELECT COUNT(DISTINCT date) FROM daily_aggregates")
    int getDistinctDateCount();
}
