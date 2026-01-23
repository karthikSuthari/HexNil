package com.example.hexnil.model;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * ANR (Application Not Responding) Event Entity
 * 
 * Records ANR events collected via ApplicationExitInfo (REASON_ANR)
 * and DropBoxManager ("system_app_anr", "data_app_anr" tags).
 * 
 * ANR Types:
 * - Input dispatching timeout: User touch event not processed in 5s
 * - Broadcast receiver timeout: Receiver didn't complete in 10s
 * - Service timeout: Service didn't complete start in 20s
 * - Content provider timeout: Provider didn't respond in time
 */
@Entity(tableName = "anr_events", indices = {
        @Index(value = { "timestamp" }),
        @Index(value = { "packageName", "timestamp" }),
        @Index(value = { "anrType" }),
        @Index(value = { "traceHash" })
})
public class AnrEvent {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /**
     * Timestamp when the ANR occurred (System.currentTimeMillis())
     */
    public long timestamp;

    /**
     * Package name of the ANR-ing app
     */
    public String packageName;

    /**
     * Human-readable app label/name
     */
    public String appLabel;

    /**
     * Type of ANR:
     * - "input_dispatching": Input event not handled
     * - "broadcast": Broadcast receiver timeout
     * - "service": Service start timeout
     * - "content_provider": Provider timeout
     * - "unknown": Type not determined
     */
    public String anrType;

    /**
     * Duration of unresponsiveness in milliseconds
     */
    public long durationMs;

    /**
     * SHA-256 hash of ANR trace for deduplication
     */
    public String traceHash;

    /**
     * Main thread state at ANR time.
     * Examples: "BLOCKED", "WAITING", "RUNNABLE", "NATIVE"
     */
    public String mainThreadState;

    /**
     * Component that was blocking (if identified).
     * Could be a lock holder, DB query, network call, etc.
     */
    public String blockingComponent;

    /**
     * Process ID at ANR time
     */
    public int pid;

    /**
     * Description from ApplicationExitInfo or DropBox
     */
    public String description;

    /**
     * App version name at ANR time
     */
    public String appVersionName;

    /**
     * App version code at ANR time
     */
    public int appVersionCode;

    /**
     * Whether this ANR resulted in the app being killed
     */
    public boolean wasKilled;

    /**
     * Whether user chose to wait (vs force close)
     */
    public boolean userWaited;

    /**
     * Battery level at ANR time
     */
    public int batteryLevel;

    /**
     * Temperature at ANR time
     */
    public float temperature;

    /**
     * Memory usage percent at ANR time
     */
    public float memoryUsagePercent;

    /**
     * CPU load at ANR time (if available, -1 if not)
     */
    public float cpuLoad;

    /**
     * Whether this is a new ANR type for this app/version
     */
    public boolean isNewAnrType;

    /**
     * System version at ANR time
     */
    public String systemVersion;

    /**
     * Foreground activity at ANR time (if available)
     */
    public String foregroundActivity;

    // =========================================================================
    // Constructors
    // =========================================================================

    public AnrEvent() {
    }

    public AnrEvent(String packageName, String anrType, long durationMs) {
        this.timestamp = System.currentTimeMillis();
        this.packageName = packageName;
        this.anrType = anrType;
        this.durationMs = durationMs;
    }

    // =========================================================================
    // Utility Methods
    // =========================================================================

    /**
     * Returns true if this is an input-related ANR (most visible to users)
     */
    public boolean isInputAnr() {
        return "input_dispatching".equals(anrType);
    }

    /**
     * Returns true if this is a background ANR (less visible but still bad)
     */
    public boolean isBackgroundAnr() {
        return "broadcast".equals(anrType) || "service".equals(anrType);
    }

    /**
     * Returns severity based on ANR type and whether it's new
     */
    public String getSeverity() {
        if (isNewAnrType) {
            return "critical";
        } else if (isInputAnr()) {
            return "high";
        } else if (isBackgroundAnr()) {
            return "medium";
        } else {
            return "low";
        }
    }

    /**
     * Returns likely blocking reason based on thread state
     */
    public String getLikelyBlockingReason() {
        if (mainThreadState == null) {
            return "unknown";
        }
        switch (mainThreadState.toUpperCase()) {
            case "BLOCKED":
                return "Waiting for lock (possible deadlock)";
            case "WAITING":
            case "TIMED_WAITING":
                return "Waiting for I/O or synchronization";
            case "NATIVE":
                return "Blocked in native code (JNI)";
            case "RUNNABLE":
                return "CPU-bound work on main thread";
            default:
                return "Unknown blocking reason";
        }
    }

    /**
     * Returns ANR duration in seconds
     */
    public float getDurationSeconds() {
        return durationMs / 1000f;
    }

    /**
     * Returns a dedup key for grouping same ANRs
     */
    public String getDedupKey() {
        return packageName + ":" + anrType + ":" +
                (traceHash != null ? traceHash : mainThreadState);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.US,
                "AnrEvent{%s, type=%s, duration=%.1fs, severity=%s}",
                packageName, anrType, getDurationSeconds(), getSeverity());
    }
}
