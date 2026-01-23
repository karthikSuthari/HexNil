package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Daily Aggregate Entity
 * 
 * Stores aggregated daily performance metrics for trend analysis
 * and baseline calculation. One record per day per app (or device-level
 * when packageName is null).
 * 
 * Used for:
 * - 7-day and 30-day rolling baseline calculation
 * - Regression detection (comparing current vs baseline)
 * - Long-term trend analysis
 * - Device health scoring
 */
@Entity(tableName = "daily_aggregates", indices = {
        @Index(value = { "date" }),
        @Index(value = { "packageName", "date" }),
        @Index(value = { "date", "packageName" }, unique = true)
})
public class DailyAggregate {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Date in "YYYY-MM-DD" format (e.g., "2026-01-23")
     */
    public String date;

    /**
     * Package name for app-specific aggregates.
     * Null for device-level aggregate stats.
     */
    public String packageName;

    /**
     * Human-readable app name (null for device-level)
     */
    public String appLabel;

    // =========================================================================
    // Memory Aggregates
    // =========================================================================

    /**
     * Average memory usage percentage for the day
     */
    public float avgMemoryUsedPercent;

    /**
     * Maximum memory usage percentage observed
     */
    public float maxMemoryUsedPercent;

    /**
     * Minimum memory usage percentage observed
     */
    public float minMemoryUsedPercent;

    /**
     * Number of low memory events triggered
     */
    public int lowMemoryEventCount;

    /**
     * Number of memory samples collected
     */
    public int memorySampleCount;

    // =========================================================================
    // Launch Aggregates
    // =========================================================================

    /**
     * Total number of app launches
     */
    public int launchCount;

    /**
     * Number of cold launches
     */
    public int coldLaunchCount;

    /**
     * Average cold launch time in milliseconds
     */
    public float avgColdLaunchMs;

    /**
     * Average warm launch time in milliseconds
     */
    public float avgWarmLaunchMs;

    /**
     * Average hot launch time in milliseconds
     */
    public float avgHotLaunchMs;

    /**
     * Maximum launch time observed (any type)
     */
    public float maxLaunchMs;

    /**
     * Number of slow launches (exceeding type threshold)
     */
    public int slowLaunchCount;

    // =========================================================================
    // Frame/Jank Aggregates
    // =========================================================================

    /**
     * Total frames rendered
     */
    public int totalFrames;

    /**
     * Total slow frames (>16.67ms)
     */
    public int totalSlowFrames;

    /**
     * Total frozen frames (>=700ms)
     */
    public int totalFrozenFrames;

    /**
     * Average jank percentage
     */
    public float avgJankPercent;

    /**
     * Maximum jank percentage in a session
     */
    public float maxJankPercent;

    /**
     * Average FPS
     */
    public float avgFps;

    /**
     * Number of frame metric sessions
     */
    public int frameSessionCount;

    // =========================================================================
    // Crash/ANR Aggregates
    // =========================================================================

    /**
     * Total crash count
     */
    public int crashCount;

    /**
     * Number of unique crash types (different stack hashes)
     */
    public int uniqueCrashTypes;

    /**
     * Total ANR count
     */
    public int anrCount;

    /**
     * Number of unique ANR types
     */
    public int uniqueAnrTypes;

    /**
     * Whether any new crash type appeared today
     */
    public boolean hasNewCrashType;

    /**
     * Whether any new ANR type appeared today
     */
    public boolean hasNewAnrType;

    // =========================================================================
    // Thermal Aggregates
    // =========================================================================

    /**
     * Average temperature in Celsius
     */
    public float avgTemp;

    /**
     * Maximum temperature observed
     */
    public float maxTemp;

    /**
     * Minimum temperature observed
     */
    public float minTemp;

    /**
     * Average thermal headroom (0.0 to 1.0+)
     */
    public float avgThermalHeadroom;

    /**
     * Minimum thermal headroom observed
     */
    public float minThermalHeadroom;

    /**
     * Number of thermal throttle events
     */
    public int thermalThrottleEvents;

    /**
     * Number of thermal samples
     */
    public int thermalSampleCount;

    // =========================================================================
    // Battery Aggregates
    // =========================================================================

    /**
     * Total battery drain percentage for the day
     */
    public float totalBatteryDrain;

    /**
     * Average drain rate percent per hour
     */
    public float avgDrainRatePerHour;

    /**
     * Screen-on time in minutes
     */
    public int screenOnMinutes;

    // =========================================================================
    // Rolling Baselines (for regression detection)
    // =========================================================================

    /**
     * 7-day rolling average cold launch time
     */
    public float baseline7DayLaunchMs;

    /**
     * 7-day rolling average jank percentage
     */
    public float baseline7DayJankPercent;

    /**
     * 7-day rolling average crash count
     */
    public float baseline7DayCrashCount;

    /**
     * 7-day rolling average temperature
     */
    public float baseline7DayTemp;

    /**
     * 30-day rolling average cold launch time
     */
    public float baseline30DayLaunchMs;

    /**
     * 30-day rolling average jank percentage
     */
    public float baseline30DayJankPercent;

    /**
     * 30-day rolling average crash count
     */
    public float baseline30DayCrashCount;

    /**
     * 30-day rolling average temperature
     */
    public float baseline30DayTemp;

    // =========================================================================
    // Health Score
    // =========================================================================

    /**
     * Computed daily health score (0-100)
     */
    public float healthScore;

    /**
     * Health grade: "A", "B", "C", "D", "F"
     */
    public String healthGrade;

    // =========================================================================
    // Metadata
    // =========================================================================

    /**
     * Timestamp when this aggregate was created
     */
    public long createdAt;

    /**
     * Timestamp when this aggregate was last updated
     */
    public long updatedAt;

    /**
     * App version (for app-specific aggregates)
     */
    public String appVersion;

    /**
     * System version
     */
    public String systemVersion;

    // =========================================================================
    // Constructors
    // =========================================================================

    public DailyAggregate() {
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public DailyAggregate(String date, String packageName) {
        this();
        this.date = date;
        this.packageName = packageName;
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * Returns true if this is a device-level aggregate
     */
    public boolean isDeviceLevel() {
        return packageName == null || packageName.isEmpty();
    }

    /**
     * Calculates health score based on all metrics
     */
    public void calculateHealthScore() {
        float score = 100f;

        // Deduct for crashes (up to 30 points)
        score -= Math.min(30, crashCount * 10);

        // Deduct for ANRs (up to 25 points)
        score -= Math.min(25, anrCount * 12.5f);

        // Deduct for jank (up to 15 points)
        if (avgJankPercent > 5) {
            score -= Math.min(15, (avgJankPercent - 5) * 3);
        }

        // Deduct for thermal issues (up to 15 points)
        score -= Math.min(15, thermalThrottleEvents * 5);

        // Deduct for slow launches (up to 10 points)
        if (avgColdLaunchMs > 3000) {
            score -= Math.min(10, (avgColdLaunchMs - 3000) / 500);
        }

        // Deduct for low memory (up to 5 points)
        score -= Math.min(5, lowMemoryEventCount * 2.5f);

        this.healthScore = Math.max(0, score);
        this.healthGrade = computeGrade(this.healthScore);
    }

    private String computeGrade(float score) {
        if (score >= 90)
            return "A";
        if (score >= 80)
            return "B";
        if (score >= 70)
            return "C";
        if (score >= 60)
            return "D";
        return "F";
    }

    /**
     * Returns true if any regression is detected vs 7-day baseline
     */
    public boolean hasRegressionVsBaseline() {
        // Launch time regression >20%
        if (baseline7DayLaunchMs > 0 && avgColdLaunchMs > baseline7DayLaunchMs * 1.2f) {
            return true;
        }
        // Jank regression >15%
        if (baseline7DayJankPercent > 0 && avgJankPercent > baseline7DayJankPercent * 1.15f) {
            return true;
        }
        // Crash increase >50%
        if (baseline7DayCrashCount > 0 && crashCount > baseline7DayCrashCount * 1.5f) {
            return true;
        }
        // Thermal increase >3°C
        if (baseline7DayTemp > 0 && avgTemp > baseline7DayTemp + 3) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US,
                "DailyAggregate{date=%s, pkg=%s, health=%.0f (%s), crashes=%d, anrs=%d}",
                date, packageName, healthScore, healthGrade, crashCount, anrCount);
    }
}
