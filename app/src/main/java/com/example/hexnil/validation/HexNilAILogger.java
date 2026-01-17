package com.example.hexnil.validation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * HexNil-AI Logger - Centralized Logging per HexNil-AI Specification
 * 
 * Provides structured logging with:
 * - TAG: HexNil-AI
 * - LEVEL: WARN / ERROR / DEBUG / INFO
 * - REASON: Exact cause of the issue
 * - ENTITY: Entity type that caused the issue
 * 
 * All HexNil-AI data validation and processing errors should be logged through
 * this class.
 */
public class HexNilAILogger {

    private static final String TAG = "HexNil-AI";

    // ═══════════════════════════════════════════════════════════════════════════════
    // LOG LEVELS
    // ═══════════════════════════════════════════════════════════════════════════════

    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // CORE LOGGING METHODS
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Log a validation issue with full context.
     */
    public static void log(@NonNull Level level, @NonNull String entity,
            @NonNull String reason, @Nullable String details) {
        String message = formatMessage(entity, reason, details);

        switch (level) {
            case DEBUG:
                Log.d(TAG, message);
                break;
            case INFO:
                Log.i(TAG, message);
                break;
            case WARN:
                Log.w(TAG, message);
                break;
            case ERROR:
                Log.e(TAG, message);
                break;
        }
    }

    /**
     * Log a warning with entity context.
     */
    public static void warn(@NonNull String entity, @NonNull String reason) {
        log(Level.WARN, entity, reason, null);
    }

    /**
     * Log an error with entity context.
     */
    public static void error(@NonNull String entity, @NonNull String reason) {
        log(Level.ERROR, entity, reason, null);
    }

    /**
     * Log an error with entity context and exception.
     */
    public static void error(@NonNull String entity, @NonNull String reason,
            @Nullable Throwable throwable) {
        String message = formatMessage(entity, reason, null);
        if (throwable != null) {
            Log.e(TAG, message, throwable);
        } else {
            Log.e(TAG, message);
        }
    }

    /**
     * Log debug info.
     */
    public static void debug(@NonNull String entity, @NonNull String reason) {
        log(Level.DEBUG, entity, reason, null);
    }

    /**
     * Log info message.
     */
    public static void info(@NonNull String entity, @NonNull String reason) {
        log(Level.INFO, entity, reason, null);
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // SPECIALIZED LOGGING FOR HEXNIL-AI SPEC
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Log when data is skipped because it doesn't map to any entity.
     * Per HexNil-AI spec: "If a data point does not match any existing entity,
     * DO NOT store it. Log it internally as UNMAPPED_DATA_SKIPPED."
     */
    public static void logUnmappedData(@NonNull String dataType, @Nullable String details) {
        Log.w(TAG, formatMessage("UNMAPPED_DATA_SKIPPED",
                "Data type '" + dataType + "' does not match any RoomDB entity", details));
    }

    /**
     * Log a schema mismatch error.
     * Per HexNil-AI spec: "Schema mismatch → Abort insert"
     */
    public static void logSchemaMismatch(@NonNull String entity, @NonNull String fieldName,
            @NonNull String expectedType, @NonNull String actualType) {
        Log.e(TAG, formatMessage(entity, "SCHEMA_MISMATCH",
                "Field '" + fieldName + "' expected " + expectedType + " but got " + actualType));
    }

    /**
     * Log when a required field is missing.
     * Per HexNil-AI spec: "Missing required field → Skip record"
     */
    public static void logMissingField(@NonNull String entity, @NonNull String fieldName) {
        Log.w(TAG, formatMessage(entity, "MISSING_REQUIRED_FIELD",
                "Required field '" + fieldName + "' is missing - record skipped"));
    }

    /**
     * Log when a value is outside valid range.
     * Per HexNil-AI spec: "Invalid value → Clamp or reject based on entity rules"
     */
    public static void logInvalidValue(@NonNull String entity, @NonNull String fieldName,
            @NonNull String value, @NonNull String validRange,
            boolean wasClamped) {
        String action = wasClamped ? "clamped" : "rejected";
        Log.w(TAG, formatMessage(entity, "INVALID_VALUE",
                "Field '" + fieldName + "' value " + value + " outside range " + validRange +
                        " - " + action));
    }

    /**
     * Log a DAO failure.
     * Per HexNil-AI spec: "DAO failure → Retry once, then fail silently"
     */
    public static void logDaoFailure(@NonNull String entity, @NonNull String operation,
            boolean willRetry, @Nullable Throwable error) {
        String action = willRetry ? "will retry" : "failed silently";
        String message = formatMessage(entity, "DAO_FAILURE",
                "Operation '" + operation + "' failed - " + action);
        if (error != null) {
            Log.e(TAG, message, error);
        } else {
            Log.e(TAG, message);
        }
    }

    /**
     * Log when uncertainty prevents storage.
     * Per HexNil-AI spec: "Uncertain mapping → DO NOT GUESS"
     */
    public static void logUncertainMapping(@NonNull String entity, @NonNull String reason) {
        Log.w(TAG, formatMessage(entity, "UNCERTAIN_MAPPING",
                "Cannot determine correct mapping: " + reason + " - data not stored"));
    }

    /**
     * Log a validation result.
     */
    public static void logValidationResult(@NonNull EntityContracts.ContractValidationResult result) {
        if (!result.isValid()) {
            for (String violation : result.getViolations()) {
                Log.e(TAG, formatMessage(result.getEntityType(), "CONTRACT_VIOLATION", violation));
            }
        }
        if (result.hasWarnings()) {
            for (String warning : result.getWarnings()) {
                Log.w(TAG, formatMessage(result.getEntityType(), "CONTRACT_WARNING", warning));
            }
        }
    }

    /**
     * Log duplicate detection.
     * Per HexNil-AI spec: "Idempotent Inserts: Never duplicate same timestamp +
     * context"
     */
    public static void logDuplicateSkipped(@NonNull String entity, long timestamp,
            @Nullable String context) {
        Log.i(TAG, formatMessage(entity, "DUPLICATE_SKIPPED",
                "Duplicate detected at timestamp " + timestamp +
                        (context != null ? " with context '" + context + "'" : "") + " - skipped"));
    }

    /**
     * Log session integrity issue.
     * Per HexNil-AI spec: "startTime MUST exist before endTime, No orphaned
     * sessions"
     */
    public static void logSessionIntegrityIssue(@NonNull String entity, @NonNull String issue) {
        Log.w(TAG, formatMessage(entity, "SESSION_INTEGRITY", issue));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // AI-SPECIFIC LOGGING
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Log AI result storage.
     * Per HexNil-AI spec: "AI insights → stored ONLY in AIAnalysisResult"
     */
    public static void logAIResultStored(@NonNull String analysisType, float confidence) {
        Log.i(TAG, formatMessage("AIAnalysisResult", "AI_RESULT_STORED",
                "Analysis type '" + analysisType + "' stored with confidence " +
                        String.format("%.2f", confidence)));
    }

    /**
     * Log when AI produces insufficient data result.
     * Per HexNil-AI spec: "'UNKNOWN' if data is insufficient"
     */
    public static void logInsufficientData(@NonNull String analysisType, @NonNull String reason) {
        Log.i(TAG, formatMessage("AIAnalysisResult", "INSUFFICIENT_DATA",
                "Analysis '" + analysisType + "' returned UNKNOWN: " + reason));
    }

    // ═══════════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════════

    private static String formatMessage(@NonNull String entity, @NonNull String reason,
            @Nullable String details) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ENTITY:").append(entity).append("] ");
        sb.append("[REASON:").append(reason).append("]");
        if (details != null && !details.isEmpty()) {
            sb.append(" ").append(details);
        }
        return sb.toString();
    }
}
