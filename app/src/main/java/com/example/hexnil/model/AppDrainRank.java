package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Ranked app drain contribution for a time window.
 */
@Entity(tableName = "app_drain_ranks")
public class AppDrainRank {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String windowLabel;     // e.g., 24h, 7d
    public long windowStart;
    public long windowEnd;

    public int rank;
    public String packageName;
    public float drainScore;       // absolute score
    public float contributionPct;  // normalized % of total
    public long screenTimeMs;
    public long foregroundTimeMs;
    public long backgroundTimeMs;
}
