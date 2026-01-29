package com.example.hexnil.manager;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.ModemFirmwareEvent;

/**
 * OEM-Grade Modem Firmware Tracker
 * 
 * Tracks modem/baseband firmware versions and detects changes for regression
 * analysis.
 * Critical for correlating radio performance with OTA updates.
 * 
 * DETECTION SOURCES:
 * - APP_START: Initial capture when app starts
 * - BOOT_COMPLETED: After device boot
 * - PERIODIC: Regular scheduled checks
 * - MANUAL: User-triggered check
 * 
 * DATA INTEGRITY PRINCIPLES:
 * - Store BOTH previous and current versions for diff analysis
 * - Always timestamp with System.currentTimeMillis()
 * - Log all version changes for auditability
 */
public class ModemFirmwareTracker {
    private static final String TAG = "ModemFirmwareTracker";

    private final Context context;
    private final HexNilDatabase database;
    private ModemFirmwareEvent lastKnownEvent = null;
    private long sessionId = 0;

    public ModemFirmwareTracker(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
        this.sessionId = System.currentTimeMillis();

        // Load last known event from database
        loadLastKnownEvent();
    }

    /**
     * Load the most recent firmware event from database.
     */
    private void loadLastKnownEvent() {
        try {
            lastKnownEvent = database.modemFirmwareEventDao().getLatest();
            if (lastKnownEvent != null) {
                Log.d(TAG, "Loaded last known version: " + lastKnownEvent.currentVersion);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load last known event", e);
        }
    }

    /**
     * Check current firmware version and record if changed.
     * 
     * @param detectionSource Source of this check (APP_START, BOOT_COMPLETED, etc.)
     * @return ModemFirmwareEvent if recorded, null if unchanged
     */
    @Nullable
    public ModemFirmwareEvent checkAndRecordVersion(@NonNull String detectionSource) {
        ModemFirmwareEvent event = new ModemFirmwareEvent();
        event.sessionId = sessionId;
        event.detectionSource = detectionSource;
        event.populateFromBuild();

        // Compare with last known
        if (lastKnownEvent != null) {
            event.compareWith(lastKnownEvent);
        } else {
            // First capture ever
            event.eventType = "INITIAL_CAPTURE";
            event.isVersionChange = false;
            event.isOtaUpdate = false;
        }

        // Decide if we should record this event
        boolean shouldRecord = false;

        if (lastKnownEvent == null) {
            // Always record first capture
            shouldRecord = true;
            Log.i(TAG, "Recording initial modem version: " + event.currentVersion);
        } else if (event.isVersionChange != null && event.isVersionChange) {
            // Modem firmware changed
            shouldRecord = true;
            Log.w(TAG, "Modem firmware changed: " + lastKnownEvent.currentVersion +
                    " -> " + event.currentVersion);
        } else if (event.isOtaUpdate != null && event.isOtaUpdate) {
            // OTA update detected (security patch or build changed)
            shouldRecord = true;
            Log.i(TAG, "OTA update detected: " + event.securityPatch);
        } else if ("APP_START".equals(detectionSource) || "BOOT_COMPLETED".equals(detectionSource)) {
            // Record on app start/boot for baseline
            shouldRecord = true;
        }

        if (shouldRecord) {
            recordEvent(event);
            return event;
        }

        return null;
    }

    /**
     * Record a firmware event to the database.
     */
    private void recordEvent(ModemFirmwareEvent event) {
        try {
            long id = database.modemFirmwareEventDao().insert(event);
            event.id = id;
            lastKnownEvent = event;
            Log.d(TAG, "Recorded firmware event: " + event.eventType + ", id=" + id);
        } catch (Exception e) {
            Log.e(TAG, "Failed to record firmware event", e);
        }
    }

    /**
     * Get current modem firmware version.
     * 
     * @return Current version string, or null if unavailable
     */
    @Nullable
    public String getCurrentVersion() {
        try {
            return Build.getRadioVersion();
        } catch (Exception e) {
            Log.w(TAG, "Failed to get radio version", e);
            return null;
        }
    }

    /**
     * Get last known firmware event.
     */
    @Nullable
    public ModemFirmwareEvent getLastKnownEvent() {
        return lastKnownEvent;
    }

    /**
     * Check if firmware has changed since last check.
     */
    public boolean hasVersionChanged() {
        String currentVersion = getCurrentVersion();
        if (currentVersion == null || lastKnownEvent == null) {
            return false;
        }
        return !currentVersion.equals(lastKnownEvent.currentVersion);
    }

    /**
     * Get firmware version history.
     * 
     * @param limit Maximum number of events to return
     * @return List of firmware events
     */
    public java.util.List<ModemFirmwareEvent> getVersionHistory(int limit) {
        try {
            return database.modemFirmwareEventDao().getLatest(limit);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get version history", e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Get firmware changes only (excluding periodic checks).
     * 
     * @param limit Maximum number of events to return
     * @return List of version change events
     */
    public java.util.List<ModemFirmwareEvent> getVersionChanges(int limit) {
        try {
            return database.modemFirmwareEventDao().getVersionChanges(limit);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get version changes", e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Create a snapshot of current firmware state for diagnostic purposes.
     */
    @NonNull
    public ModemFirmwareEvent createDiagnosticSnapshot() {
        ModemFirmwareEvent event = new ModemFirmwareEvent("PERIODIC_CHECK");
        event.sessionId = sessionId;
        event.detectionSource = "DIAGNOSTIC";
        event.populateFromBuild();

        if (lastKnownEvent != null) {
            event.compareWith(lastKnownEvent);
        }

        return event;
    }

    /**
     * Get system information summary for debugging.
     */
    @NonNull
    public String getSystemInfoSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Modem: ").append(getCurrentVersion()).append("\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE);
        sb.append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Security: ").append(Build.VERSION.SECURITY_PATCH).append("\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Build: ").append(Build.DISPLAY);
        return sb.toString();
    }

    /**
     * Reset session.
     */
    public void resetSession() {
        sessionId = System.currentTimeMillis();
    }

    /**
     * Get current session ID.
     */
    public long getSessionId() {
        return sessionId;
    }
}
