package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Frame Metric Entity
 * 
 * Records frame rendering performance for jank detection.
 * Uses Window.OnFrameMetricsAvailableListener (API 24+) for HexNil's own
 * frames.
 * 
 * Classifications:
 * - Normal frame: <=16.67ms (60fps)
 * - Slow frame: >16.67ms but <700ms (jank)
 * - Frozen frame: >=700ms (severe jank, ANR-like)
 */
@Entity(tableName = "frame_metrics", indices = {
        @Index(value = { "sessionStart" }),
        @Index(value = { "packageName", "sessionStart" }),
        @Index(value = { "jankPercentage" })
})
public class FrameMetric {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Session start timestamp (when frame collection started)
     */
    public long sessionStart;

    /**
     * Session end timestamp (when frame collection ended)
     */
    public long sessionEnd;

    /**
     * Package name of the app being measured.
     * For HexNil self-monitoring, this will be HexNil's package.
     */
    public String packageName;

    /**
     * Total number of frames rendered during this session
     */
    public int totalFrames;

    /**
     * Number of slow frames (>16.67ms but <700ms)
     */
    public int slowFrames;

    /**
     * Number of frozen frames (>=700ms)
     */
    public int frozenFrames;

    /**
     * Jank percentage: (slowFrames + frozenFrames) / totalFrames * 100
     */
    public float jankPercentage;

    /**
     * Average frame duration in milliseconds
     */
    public float avgFrameDurationMs;

    /**
     * Maximum frame duration in milliseconds
     */
    public float maxFrameDurationMs;

    /**
     * 50th percentile (median) frame duration in milliseconds
     */
    public float p50FrameDurationMs;

    /**
     * 95th percentile frame duration in milliseconds
     */
    public float p95FrameDurationMs;

    /**
     * 99th percentile frame duration in milliseconds
     */
    public float p99FrameDurationMs;

    /**
     * Total number of dropped frames during this session
     */
    public int droppedFrameCount;

    /**
     * Average FPS during this session
     */
    public float fps;

    /**
     * Total duration of the session in milliseconds
     */
    public long sessionDurationMs;

    /**
     * Activity or screen being measured (if known)
     */
    public String activityName;

    /**
     * Whether hardware acceleration was enabled
     */
    public boolean isHardwareAccelerated;

    /**
     * Device temperature at session end
     */
    public float temperature;

    /**
     * Memory usage percentage at session end
     */
    public float memoryUsagePercent;

    /**
     * Whether thermal throttling was active during session
     */
    public boolean wasThermalThrottling;

    // =========================================================================
    // Constructors
    // =========================================================================

    public FrameMetric() {
    }

    public FrameMetric(String packageName, long sessionStart, long sessionEnd) {
        this.packageName = packageName;
        this.sessionStart = sessionStart;
        this.sessionEnd = sessionEnd;
        this.sessionDurationMs = sessionEnd - sessionStart;
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * Calculate and set jank percentage based on frame counts
     */
    public void calculateJankPercentage() {
        if (totalFrames > 0) {
            this.jankPercentage = (float) (slowFrames + frozenFrames) / totalFrames * 100f;
        } else {
            this.jankPercentage = 0f;
        }
    }

    /**
     * Returns true if jank is above acceptable threshold (>5%)
     */
    public boolean hasExcessiveJank() {
        return jankPercentage > 5.0f;
    }

    /**
     * Returns true if there are frozen frames (severe jank)
     */
    public boolean hasFrozenFrames() {
        return frozenFrames > 0;
    }

    /**
     * Returns jank severity: "excellent", "good", "acceptable", "poor", "bad"
     */
    public String getJankGrade() {
        if (jankPercentage <= 1.0f) {
            return "excellent";
        } else if (jankPercentage <= 3.0f) {
            return "good";
        } else if (jankPercentage <= 5.0f) {
            return "acceptable";
        } else if (jankPercentage <= 10.0f) {
            return "poor";
        } else {
            return "bad";
        }
    }

    /**
     * Returns FPS grade: "smooth", "acceptable", "choppy", "unplayable"
     */
    public String getFpsGrade() {
        if (fps >= 55) {
            return "smooth";
        } else if (fps >= 45) {
            return "acceptable";
        } else if (fps >= 30) {
            return "choppy";
        } else {
            return "unplayable";
        }
    }

    /**
     * Returns estimated target FPS based on avg frame duration
     */
    public int getEstimatedTargetFps() {
        if (avgFrameDurationMs <= 8.33f) {
            return 120;
        } else if (avgFrameDurationMs <= 11.11f) {
            return 90;
        } else if (avgFrameDurationMs <= 16.67f) {
            return 60;
        } else if (avgFrameDurationMs <= 33.33f) {
            return 30;
        } else {
            return 15;
        }
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US,
                "FrameMetric{%s, frames=%d, jank=%.1f%%, fps=%.1f, grade=%s}",
                packageName, totalFrames, jankPercentage, fps, getJankGrade());
    }
}
