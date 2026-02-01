package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * OEM-Grade Modem Firmware Event Entity
 * 
 * Tracks modem/baseband firmware versions and changes for regression analysis.
 * Critical for correlating radio performance with OTA updates.
 * 
 * CRITICAL DESIGN PRINCIPLES:
 * 1. Every event MUST have a unique auto-generated ID
 * 2. INITIAL_CAPTURE on first observation, VERSION_CHANGE on update detection
 * 3. Stores BOTH previous and current versions for diff analysis
 * 4. NULL means data unavailable, NEVER fabricate values
 *
 * Data Sources:
 * - Build.getRadioVersion()
 * - Build.VERSION information
 * - Build.FINGERPRINT
 */
@Entity(tableName = "modem_firmware_events", indices = {
        @Index(value = { "timestamp" }),
        @Index(value = { "eventType" }),
        @Index(value = { "currentVersion" })
})
public class ModemFirmwareEvent {

    @PrimaryKey(autoGenerate = true)
    public long id;

    // =========================================================================
    // TIMING
    // =========================================================================

    /** Wall-clock timestamp when this event was captured (UTC millis). */
    public long timestamp;

    /** Elapsed realtime for drift-free ordering. */
    public long elapsedRealtimeMs;

    // =========================================================================
    // FIRMWARE VERSIONS
    // =========================================================================

    /**
     * Previous modem firmware version.
     * null on INITIAL_CAPTURE, populated on VERSION_CHANGE.
     */
    public String previousVersion;

    /**
     * Current modem firmware version from Build.getRadioVersion().
     * May be null on some custom ROMs.
     */
    public String currentVersion;

    /** Previous baseband version if different from firmware version. */
    public String previousBasebandVersion;

    /** Current baseband version. */
    public String currentBasebandVersion;

    // =========================================================================
    // EVENT CLASSIFICATION
    // =========================================================================

    /**
     * Event type.
     * Values: INITIAL_CAPTURE, VERSION_CHANGE, PERIODIC_CHECK, OTA_UPDATE
     */
    public String eventType;

    /**
     * Source of version change detection.
     * Values: APP_START, PERIODIC, BOOT_COMPLETED, MANUAL
     */
    public String detectionSource;

    // =========================================================================
    // SYSTEM CONTEXT
    // =========================================================================

    /** Android version (Build.VERSION.RELEASE). */
    public String androidVersion;

    /** Android SDK level (Build.VERSION.SDK_INT). */
    public Integer androidSdkLevel;

    /** Security patch level (Build.VERSION.SECURITY_PATCH). */
    public String securityPatch;

    /** Build fingerprint (Build.FINGERPRINT). */
    public String buildFingerprint;

    /** Build number (Build.DISPLAY). */
    public String buildNumber;

    /** Device manufacturer (Build.MANUFACTURER). */
    public String manufacturer;

    /** Device model (Build.MODEL). */
    public String model;

    /** Device product (Build.PRODUCT). */
    public String product;

    // =========================================================================
    // BOOTLOADER & HARDWARE
    // =========================================================================

    /** Bootloader version (Build.BOOTLOADER). */
    public String bootloaderVersion;

    /** Hardware revision (Build.HARDWARE). */
    public String hardware;

    /** Board name (Build.BOARD). */
    public String board;

    // =========================================================================
    // CHANGE ANALYSIS
    // =========================================================================

    /**
     * Whether this represents a modem firmware version change.
     * true only when previousVersion != currentVersion and both are non-null.
     */
    public Boolean isVersionChange;

    /**
     * Whether this appears to be an OTA update (security patch or build changed).
     */
    public Boolean isOtaUpdate;

    /** Time since last recorded event in milliseconds. */
    public Long timeSinceLastEventMs;

    // =========================================================================
    // METADATA
    // =========================================================================

    /** Session ID for grouping related events. */
    public long sessionId;

    /** Notes about this event. */
    public String notes;

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================

    /** Default constructor - creates event with fresh timestamp. */
    public ModemFirmwareEvent() {
        this.timestamp = System.currentTimeMillis();
        this.elapsedRealtimeMs = android.os.SystemClock.elapsedRealtime();
        this.eventType = "INITIAL_CAPTURE";
        this.isVersionChange = false;
        this.isOtaUpdate = false;
    }

    /** Creates an event with specified type. */
    @Ignore
    public ModemFirmwareEvent(String eventType) {
        this();
        this.eventType = eventType;
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Check if modem firmware has changed.
     */
    @Ignore
    public boolean hasFirmwareChanged() {
        return previousVersion != null &&
                currentVersion != null &&
                !previousVersion.equals(currentVersion);
    }

    /**
     * Get a short version identifier (first 20 chars).
     */
    @Ignore
    public String getShortVersion() {
        if (currentVersion == null)
            return "NOT_AVAILABLE";
        return currentVersion.length() > 20 ? currentVersion.substring(0, 20) + "..." : currentVersion;
    }

    /**
     * Populate from Build class constants.
     */
    @Ignore
    public void populateFromBuild() {
        this.currentVersion = android.os.Build.getRadioVersion();

        // Baseband version is often the same as radio version on Android
        this.currentBasebandVersion = android.os.Build.getRadioVersion();

        this.androidVersion = android.os.Build.VERSION.RELEASE;
        this.androidSdkLevel = android.os.Build.VERSION.SDK_INT;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            this.securityPatch = android.os.Build.VERSION.SECURITY_PATCH;
        }

        this.buildFingerprint = android.os.Build.FINGERPRINT;
        this.buildNumber = android.os.Build.DISPLAY;
        this.manufacturer = android.os.Build.MANUFACTURER;
        this.model = android.os.Build.MODEL;
        this.product = android.os.Build.PRODUCT;
        this.bootloaderVersion = android.os.Build.BOOTLOADER;
        this.hardware = android.os.Build.HARDWARE;
        this.board = android.os.Build.BOARD;
    }

    /**
     * Check if this represents a version change from a previous event.
     */
    @Ignore
    public void compareWith(ModemFirmwareEvent previous) {
        if (previous != null) {
            this.previousVersion = previous.currentVersion;
            this.previousBasebandVersion = previous.currentBasebandVersion;
            this.timeSinceLastEventMs = this.timestamp - previous.timestamp;

            this.isVersionChange = hasFirmwareChanged();

            // Check if OTA update (security patch or build fingerprint changed)
            this.isOtaUpdate = (previous.securityPatch != null &&
                    this.securityPatch != null &&
                    !previous.securityPatch.equals(this.securityPatch)) ||
                    (previous.buildFingerprint != null &&
                            this.buildFingerprint != null &&
                            !previous.buildFingerprint.equals(this.buildFingerprint));

            if (this.isVersionChange) {
                this.eventType = "VERSION_CHANGE";
            } else if (this.isOtaUpdate) {
                this.eventType = "OTA_UPDATE";
            }
        }
    }

    @Override
    public String toString() {
        return "ModemFirmwareEvent{" +
                "id=" + id +
                ", ts=" + timestamp +
                ", type=" + eventType +
                ", version=" + getShortVersion() +
                ", changed=" + isVersionChange +
                ", ota=" + isOtaUpdate +
                ", android=" + androidVersion +
                ", patch=" + securityPatch +
                '}';
    }
}
