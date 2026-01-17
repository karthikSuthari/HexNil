package com.example.hexnil.intelligence.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/**
 * App Usage Session - tracks individual usage sessions with full contextual
 * state.
 * 
 * Part of the isolated App Intelligence module.
 * Does NOT reference any tables in the core HexNil database.
 */
@Entity(tableName = "ai_app_usage_sessions", indices = {
        @Index(value = { "package_name" }),
        @Index(value = { "session_start" }),
        @Index(value = { "session_id" }, unique = true)
})
public class AppUsageSession {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // Session Identification
    @ColumnInfo(name = "session_id")
    public String sessionId;

    @ColumnInfo(name = "package_name")
    public String packageName;

    // Time Bounds
    @ColumnInfo(name = "session_start")
    public long sessionStart;

    @ColumnInfo(name = "session_end")
    public Long sessionEnd; // NULL if ongoing

    @ColumnInfo(name = "duration_ms")
    public long durationMs;

    // Screen State
    @ColumnInfo(name = "foreground_time_ms")
    public long foregroundTimeMs;

    @ColumnInfo(name = "background_time_ms")
    public long backgroundTimeMs;

    @ColumnInfo(name = "screen_on_time_ms")
    public long screenOnTimeMs;

    @ColumnInfo(name = "screen_off_time_ms")
    public long screenOffTimeMs;

    // Device Context
    @ColumnInfo(name = "network_type")
    public String networkType; // "wifi", "mobile_4g", "mobile_5g", "none"

    @ColumnInfo(name = "charging_state")
    public String chargingState; // "charging_ac", "charging_usb", "discharging"

    @ColumnInfo(name = "battery_level_start")
    public Integer batteryLevelStart;

    @ColumnInfo(name = "battery_level_end")
    public Integer batteryLevelEnd;

    // Power State
    @ColumnInfo(name = "doze_mode_active")
    public boolean dozeModeActive;

    @ColumnInfo(name = "power_save_mode")
    public boolean powerSaveMode;

    // Audit
    @ColumnInfo(name = "created_at")
    public long createdAt;

    public AppUsageSession() {
        this.sessionId = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.sessionStart = this.createdAt;
    }

    /**
     * Start a new usage session for an app.
     */
    public static AppUsageSession start(String packageName, int batteryLevel,
            String networkType, String chargingState) {
        AppUsageSession session = new AppUsageSession();
        session.packageName = packageName;
        session.batteryLevelStart = batteryLevel;
        session.networkType = networkType;
        session.chargingState = chargingState;
        return session;
    }

    /**
     * End the session and calculate duration.
     */
    public void end(int batteryLevelEnd) {
        this.sessionEnd = System.currentTimeMillis();
        this.batteryLevelEnd = batteryLevelEnd;
        this.durationMs = this.sessionEnd - this.sessionStart;
    }

    /**
     * Update screen time metrics.
     */
    public void updateScreenTime(long foregroundMs, long backgroundMs,
            long screenOnMs, long screenOffMs) {
        this.foregroundTimeMs = foregroundMs;
        this.backgroundTimeMs = backgroundMs;
        this.screenOnTimeMs = screenOnMs;
        this.screenOffTimeMs = screenOffMs;
    }

    /**
     * Check if session is still ongoing.
     */
    public boolean isOngoing() {
        return sessionEnd == null;
    }

    /**
     * Get battery drain during this session.
     */
    public int getBatteryDrain() {
        if (batteryLevelStart != null && batteryLevelEnd != null) {
            return batteryLevelStart - batteryLevelEnd;
        }
        return 0;
    }

    /**
     * Get session duration in hours.
     */
    public float getDurationHours() {
        return durationMs / 3600000.0f;
    }

    @Override
    public String toString() {
        return "AppUsageSession{" +
                "sessionId='" + sessionId + '\'' +
                ", packageName='" + packageName + '\'' +
                ", duration=" + String.format("%.1f min", durationMs / 60000.0f) +
                ", drain=" + getBatteryDrain() + "%" +
                ", ongoing=" + isOngoing() +
                '}';
    }
}
