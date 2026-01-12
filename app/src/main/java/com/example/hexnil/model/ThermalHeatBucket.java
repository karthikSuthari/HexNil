package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Aggregated thermal heat buckets by temp range and time-of-day.
 */
@Entity(tableName = "thermal_heat_buckets")
public class ThermalHeatBucket {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long startTime;
    public long endTime;
    public String tempBucket;      // <35, 35-40, 40-45, >45
    public int hourBlock;          // 0-23 or 3-hour block index

    public int sampleCount;        // number of samples
    public long durationMs;        // cumulative duration in this bucket

    // Correlations
    public int chargingCount;      // samples taken while charging
    public String topForegroundApp;// last seen or top app

    public float maxTemp;
    public float avgTemp;
}
