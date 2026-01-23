package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Crash Event Entity
 * 
 * Records app crash events collected via ApplicationExitInfo (API 30+)
 * and DropBoxManager. Stores metadata for crash analysis without full
 * stack traces (privacy-safe approach).
 * 
 * Exit Reasons from ApplicationExitInfo:
 * - REASON_CRASH (4): Java/native crash
 * - REASON_CRASH_NATIVE (6): Native crash specifically
 * - REASON_ANR (7): Application Not Responding
 * - REASON_LOW_MEMORY (3): Killed due to low memory
 * - REASON_EXCESSIVE_RESOURCE_USAGE (9): Resource abuse
 */
@Entity(tableName = "crash_events", indices = {
        @Index(value = { "timestamp" }),
        @Index(value = { "packageName", "timestamp" }),
        @Index(value = { "crashType" }),
        @Index(value = { "stackTraceHash" })
})
public class CrashEvent {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Timestamp when the crash occurred (System.currentTimeMillis())
     */
    public long timestamp;

    /**
     * Package name of the crashed app
     */
    public String packageName;

    /**
     * Human-readable app label/name
     */
    public String appLabel;

    /**
     * Type of crash: "java_crash", "native_crash", "low_memory_kill",
     * "excessive_resource", "unknown"
     */
    public String crashType;

    /**
     * Exception class name (e.g., "NullPointerException")
     * From ApplicationExitInfo description parsing.
     */
    public String exceptionType;

    /**
     * Exception message (truncated to 500 chars for privacy)
     */
    public String exceptionMessage;

    /**
     * SHA-256 hash of stack trace for deduplication.
     * Allows grouping same crashes without storing raw traces.
     */
    public String stackTraceHash;

    /**
     * Process ID at crash time
     */
    public int pid;

    /**
     * User ID of the app
     */
    public int uid;

    /**
     * Process name (may differ from package for multi-process apps)
     */
    public String processName;

    /**
     * Process importance at crash time.
     * From RunningAppProcessInfo.importance
     */
    public int importance;

    /**
     * Exit reason string from ApplicationExitInfo.getReason()
     */
    public String reason;

    /**
     * Exit reason code (REASON_CRASH, REASON_ANR, etc.)
     */
    public int reasonCode;

    /**
     * Human-readable description from ApplicationExitInfo.getDescription()
     */
    public String description;

    /**
     * Proportional set size (memory) at crash time in bytes
     */
    public long pss;

    /**
     * Resident set size (memory) at crash time in bytes
     */
    public long rss;

    /**
     * App version name at crash time
     */
    public String appVersionName;

    /**
     * App version code at crash time
     */
    public int appVersionCode;

    /**
     * Battery level at crash time (if available)
     */
    public int batteryLevel;

    /**
     * Temperature at crash time (if available)
     */
    public float temperature;

    /**
     * Whether device was low on memory when crash occurred
     */
    public boolean wasLowMemory;

    /**
     * Whether this is a new crash type (not seen before for this app/version)
     */
    public boolean isNewCrashType;

    /**
     * Sub-reason code for more detailed classification
     */
    public int subReason;

    /**
     * Status (e.g., process state at exit)
     */
    public int status;

    // =========================================================================
    // Constructors
    // =========================================================================

    public CrashEvent() {
    }

    public CrashEvent(String packageName, String crashType, int reasonCode) {
        this.timestamp = System.currentTimeMillis();
        this.packageName = packageName;
        this.crashType = crashType;
        this.reasonCode = reasonCode;
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * Returns true if this is a native (C/C++) crash
     */
    public boolean isNativeCrash() {
        return "native_crash".equals(crashType) || reasonCode == 6;
    }

    /**
     * Returns true if this is a memory-related kill
     */
    public boolean isMemoryRelated() {
        return "low_memory_kill".equals(crashType) || reasonCode == 3 || wasLowMemory;
    }

    /**
     * Returns severity: "low", "medium", "high", "critical"
     */
    public String getSeverity() {
        if (isNativeCrash()) {
            return "critical";
        } else if (isNewCrashType) {
            return "high";
        } else if (isMemoryRelated()) {
            return "medium";
        } else {
            return "low";
        }
    }

    /**
     * Returns memory usage at crash in MB
     */
    public float getPssMB() {
        return pss / 1024f / 1024f;
    }

    /**
     * Returns a dedup key for grouping same crashes
     */
    public String getDedupKey() {
        return packageName + ":" + crashType + ":" +
                (stackTraceHash != null ? stackTraceHash : exceptionType);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US,
                "CrashEvent{%s, type=%s, reason=%d, severity=%s}",
                packageName, crashType, reasonCode, getSeverity());
    }
}
