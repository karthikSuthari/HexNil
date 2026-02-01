package com.example.hexnil.manager;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AnrEvent;
import com.example.hexnil.model.CrashEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Crash and ANR Collector
 * 
 * Collects app crash and ANR events using:
 * - ApplicationExitInfo (API 30+) for crash/ANR detection
 * - Process exit reasons from ActivityManager
 * 
 * This is the primary data source for crash/ANR tracking on Android 11+.
 * For older versions, we rely on app-level uncaught exception handlers.
 * 
 * Non-root compatible: Uses only public Android APIs
 */
public class CrashAnrCollector {

    private static final String TAG = "CrashAnrCollector";

    private final Context context;
    private final HexNilDatabase database;
    private final ActivityManager activityManager;
    private final PackageManager packageManager;
    private final Executor executor;

    // Track last processed timestamp to avoid duplicates
    private long lastProcessedTimestamp = 0;

    // Maximum length for description fields (privacy consideration)
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    // Exit reason codes from ApplicationExitInfo
    private static final int REASON_UNKNOWN = 0;
    private static final int REASON_EXIT_SELF = 1;
    private static final int REASON_SIGNALED = 2;
    private static final int REASON_LOW_MEMORY = 3;
    private static final int REASON_CRASH = 4;
    private static final int REASON_CRASH_NATIVE = 5;
    private static final int REASON_ANR = 6;
    private static final int REASON_INITIALIZATION_FAILURE = 7;
    private static final int REASON_PERMISSION_CHANGE = 8;
    private static final int REASON_EXCESSIVE_RESOURCE_USAGE = 9;
    private static final int REASON_USER_REQUESTED = 10;
    private static final int REASON_USER_STOPPED = 11;
    private static final int REASON_DEPENDENCY_DIED = 12;
    private static final int REASON_OTHER = 13;
    private static final int REASON_FREEZER = 14;
    private static final int REASON_PACKAGE_STATE_CHANGE = 15;
    private static final int REASON_PACKAGE_UPDATED = 16;

    public CrashAnrCollector(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.packageManager = context.getPackageManager();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Check if ApplicationExitInfo API is available.
     * 
     * @return true if API 30+
     */
    public boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    /**
     * Collect crash and ANR events from the system.
     * This should be called periodically (every snapshot cycle).
     * 
     * @param batteryLevel Current battery level
     * @param temperature  Current temperature
     * @param isLowMemory  Whether system reports low memory
     */
    public void collectEvents(int batteryLevel, float temperature, boolean isLowMemory) {
        if (!isSupported()) {
            Log.d(TAG, "ApplicationExitInfo not supported (requires API 30+)");
            return;
        }

        executor.execute(() -> {
            try {
                collectExitInfoEvents(batteryLevel, temperature, isLowMemory);
            } catch (Exception e) {
                Log.e(TAG, "Error collecting crash/ANR events", e);
            }
        });
    }

    /**
     * Collect events using ApplicationExitInfo API.
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private void collectExitInfoEvents(int batteryLevel, float temperature, boolean isLowMemory) {
        if (activityManager == null) {
            Log.e(TAG, "ActivityManager not available");
            return;
        }

        // Query exit info for all packages (null = all, 0 = any pid, 50 = max results)
        List<ApplicationExitInfo> exitInfos = activityManager
                .getHistoricalProcessExitReasons(null, 0, 100);

        if (exitInfos == null || exitInfos.isEmpty()) {
            Log.d(TAG, "No exit info available");
            return;
        }

        int crashesRecorded = 0;
        int anrsRecorded = 0;

        for (ApplicationExitInfo info : exitInfos) {
            // Skip already processed events
            if (info.getTimestamp() <= lastProcessedTimestamp) {
                continue;
            }

            int reason = info.getReason();

            if (isCrashReason(reason)) {
                // Process as crash
                CrashEvent crashEvent = createCrashEvent(info, batteryLevel,
                        temperature, isLowMemory);
                if (crashEvent != null) {
                    database.crashEventDao().insert(crashEvent);
                    crashesRecorded++;
                    Log.d(TAG, "Recorded crash: " + crashEvent.packageName +
                            " reason=" + reason);
                }
            } else if (reason == REASON_ANR) {
                // Process as ANR
                AnrEvent anrEvent = createAnrEvent(info, batteryLevel, temperature);
                if (anrEvent != null) {
                    database.anrEventDao().insert(anrEvent);
                    anrsRecorded++;
                    Log.d(TAG, "Recorded ANR: " + anrEvent.packageName);
                }
            }
        }

        // Update last processed timestamp
        if (!exitInfos.isEmpty()) {
            long maxTimestamp = 0;
            for (ApplicationExitInfo info : exitInfos) {
                maxTimestamp = Math.max(maxTimestamp, info.getTimestamp());
            }
            lastProcessedTimestamp = maxTimestamp;
        }

        if (crashesRecorded > 0 || anrsRecorded > 0) {
            Log.i(TAG, String.format("Collected %d crashes, %d ANRs",
                    crashesRecorded, anrsRecorded));
        }
    }

    /**
     * Check if exit reason indicates a crash.
     */
    private boolean isCrashReason(int reason) {
        return reason == REASON_CRASH ||
                reason == REASON_CRASH_NATIVE ||
                reason == REASON_LOW_MEMORY ||
                reason == REASON_EXCESSIVE_RESOURCE_USAGE;
    }

    /**
     * Create a CrashEvent from ApplicationExitInfo.
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private CrashEvent createCrashEvent(ApplicationExitInfo info, int batteryLevel,
            float temperature, boolean isLowMemory) {
        try {
            String processName = info.getProcessName();
            // Process name is usually the package name, or package:process for
            // multi-process apps
            String packageName = processName;
            if (processName != null && processName.contains(":")) {
                packageName = processName.split(":")[0];
            }
            if (packageName == null)
                return null;

            CrashEvent event = new CrashEvent();
            event.timestamp = info.getTimestamp();
            event.packageName = packageName;
            event.pid = info.getPid();
            event.uid = info.getDefiningUid();
            event.processName = info.getProcessName();
            event.reasonCode = info.getReason();
            event.status = info.getStatus();
            event.importance = info.getImportance();
            // subReason left at default 0 - getSubReason() API 31+ has compile issues
            
            // Memory info - getPss/getRss return KB, convert to bytes
            // Note: These may return 0 for some crash types
            long pssKb = info.getPss();
            long rssKb = info.getRss();
            event.pss = pssKb > 0 ? pssKb * 1024L : 0;
            event.rss = rssKb > 0 ? rssKb * 1024L : 0;

            // Set crash type and human-readable reason
            event.crashType = mapReasonToCrashType(info.getReason());
            event.reason = mapReasonToString(info.getReason());

            // Get description (may contain exception info)
            String description = info.getDescription();
            if (description != null) {
                event.description = truncate(description, MAX_DESCRIPTION_LENGTH);

                // Try to extract exception type from description
                parseExceptionFromDescription(event, description);
            }

            // Try to get trace data and create hash for deduplication
            try {
                InputStream traceStream = info.getTraceInputStream();
                if (traceStream != null) {
                    String traceContent = readInputStream(traceStream, 4000);
                    if (traceContent != null && !traceContent.isEmpty()) {
                        event.stackTraceHash = computeHash(traceContent);
                        
                        // Try to extract exception info from trace if not in description
                        if (event.exceptionType == null) {
                            parseExceptionFromTrace(event, traceContent);
                        }
                    }
                    traceStream.close();
                }
            } catch (Exception e) {
                Log.d(TAG, "Could not read trace: " + e.getMessage());
            }
            
            // Generate fallback hash if trace unavailable
            if (event.stackTraceHash == null) {
                String fallbackContent = event.packageName + ":" + event.crashType + ":" +
                        event.reasonCode + ":" + (event.description != null ? event.description : "");
                event.stackTraceHash = computeHash(fallbackContent);
            }

            // Get app label and version
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                event.appLabel = packageManager.getApplicationLabel(appInfo).toString();

                event.appVersionCode = (int) packageManager
                        .getPackageInfo(packageName, 0).getLongVersionCode();
                event.appVersionName = packageManager
                        .getPackageInfo(packageName, 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                event.appLabel = packageName;
            }

            // Context data
            event.batteryLevel = batteryLevel;
            event.temperature = temperature;
            event.wasLowMemory = isLowMemory;

            // Check if this is a new crash type for this package
            // Always check since we now always generate a hash
            event.isNewCrashType = !database.crashEventDao()
                    .existsByHash(event.stackTraceHash, packageName);

            return event;
        } catch (Exception e) {
            Log.e(TAG, "Error creating crash event", e);
            return null;
        }
    }

    /**
     * Create an AnrEvent from ApplicationExitInfo.
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private AnrEvent createAnrEvent(ApplicationExitInfo info, int batteryLevel,
            float temperature) {
        try {
            String processName = info.getProcessName();
            // Process name is usually the package name, or package:process for
            // multi-process apps
            String packageName = processName;
            if (processName != null && processName.contains(":")) {
                packageName = processName.split(":")[0];
            }
            if (packageName == null)
                return null;

            AnrEvent event = new AnrEvent();
            event.timestamp = info.getTimestamp();
            event.packageName = packageName;
            event.pid = info.getPid();

            // ANR type from description
            String description = info.getDescription();
            if (description != null) {
                event.description = truncate(description, MAX_DESCRIPTION_LENGTH);
                event.anrType = parseAnrType(description);
            } else {
                event.anrType = "unknown";
            }

            // Try to get trace and compute hash
            try {
                InputStream traceStream = info.getTraceInputStream();
                if (traceStream != null) {
                    String traceContent = readInputStream(traceStream, 2000);
                    if (traceContent != null && !traceContent.isEmpty()) {
                        event.traceHash = computeHash(traceContent);

                        // Try to parse main thread state from trace
                        event.mainThreadState = parseMainThreadState(traceContent);
                    }
                    traceStream.close();
                }
            } catch (Exception e) {
                Log.d(TAG, "Could not read ANR trace: " + e.getMessage());
            }

            // Get app label and version
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                event.appLabel = packageManager.getApplicationLabel(appInfo).toString();

                event.appVersionCode = (int) packageManager
                        .getPackageInfo(packageName, 0).getLongVersionCode();
                event.appVersionName = packageManager
                        .getPackageInfo(packageName, 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                event.appLabel = packageName;
            }

            // Context data
            event.batteryLevel = batteryLevel;
            event.temperature = temperature;
            event.systemVersion = Build.VERSION.RELEASE;

            // Check if this is a new ANR type
            if (event.traceHash != null) {
                event.isNewAnrType = !database.anrEventDao()
                        .existsByHash(event.traceHash, packageName);
            }

            return event;
        } catch (Exception e) {
            Log.e(TAG, "Error creating ANR event", e);
            return null;
        }
    }

    /**
     * Map exit reason code to crash type string.
     */
    private String mapReasonToCrashType(int reason) {
        switch (reason) {
            case REASON_CRASH:
                return "java_crash";
            case REASON_CRASH_NATIVE:
                return "native_crash";
            case REASON_LOW_MEMORY:
                return "low_memory_kill";
            case REASON_EXCESSIVE_RESOURCE_USAGE:
                return "excessive_resource";
            case REASON_ANR:
                return "anr";
            case REASON_SIGNALED:
                return "signal_crash";
            case REASON_INITIALIZATION_FAILURE:
                return "init_failure";
            default:
                return "unknown";
        }
    }
    
    /**
     * Map exit reason code to human-readable string.
     */
    private String mapReasonToString(int reason) {
        switch (reason) {
            case REASON_UNKNOWN:
                return "unknown";
            case REASON_EXIT_SELF:
                return "exit_self";
            case REASON_SIGNALED:
                return "signal";
            case REASON_LOW_MEMORY:
                return "low_memory";
            case REASON_CRASH:
                return "crash";
            case REASON_CRASH_NATIVE:
                return "native_crash";
            case REASON_ANR:
                return "anr";
            case REASON_INITIALIZATION_FAILURE:
                return "init_failure";
            case REASON_PERMISSION_CHANGE:
                return "permission_change";
            case REASON_EXCESSIVE_RESOURCE_USAGE:
                return "excessive_resource";
            case REASON_USER_REQUESTED:
                return "user_requested";
            case REASON_USER_STOPPED:
                return "user_stopped";
            case REASON_DEPENDENCY_DIED:
                return "dependency_died";
            case REASON_OTHER:
                return "other";
            case REASON_FREEZER:
                return "freezer";
            case REASON_PACKAGE_STATE_CHANGE:
                return "package_state_change";
            case REASON_PACKAGE_UPDATED:
                return "package_updated";
            default:
                return "unknown_" + reason;
        }
    }

    /**
     * Parse ANR type from description string.
     */
    private String parseAnrType(String description) {
        if (description == null)
            return "unknown";

        String lower = description.toLowerCase();
        if (lower.contains("input dispatch") || lower.contains("input timeout")) {
            return "input_dispatching";
        } else if (lower.contains("broadcast")) {
            return "broadcast";
        } else if (lower.contains("service")) {
            return "service";
        } else if (lower.contains("content provider") || lower.contains("provider")) {
            return "content_provider";
        } else {
            return "unknown";
        }
    }

    /**
     * Parse exception type from description.
     */
    private void parseExceptionFromDescription(CrashEvent event, String description) {
        if (description == null)
            return;

        // Pattern 1: "java.lang.NullPointerException: message"
        // Pattern 2: "NullPointerException"
        // Pattern 3: "Crash: java.lang.RuntimeException"
        
        String[] lines = description.split("\n");
        for (String line : lines) {
            line = line.trim();
            
            // Look for fully qualified exception names
            if (line.contains("Exception") || line.contains("Error")) {
                // Try to extract: "java.lang.NullPointerException: message"
                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    String potentialException = line.substring(0, colonIdx).trim();
                    // Check if it looks like a class name (contains . or ends with Exception/Error)
                    if (potentialException.contains(".") || 
                        potentialException.endsWith("Exception") || 
                        potentialException.endsWith("Error")) {
                        // Extract just the class name if fully qualified
                        int lastDot = potentialException.lastIndexOf('.');
                        event.exceptionType = lastDot > 0 ? 
                                potentialException.substring(lastDot + 1) : potentialException;
                        // Message is after the colon
                        if (colonIdx + 1 < line.length()) {
                            event.exceptionMessage = truncate(line.substring(colonIdx + 1).trim(), 200);
                        }
                        return;
                    }
                } else {
                    // No colon, the whole line might be the exception
                    if (line.endsWith("Exception") || line.endsWith("Error") || line.contains(".")) {
                        int lastDot = line.lastIndexOf('.');
                        event.exceptionType = lastDot > 0 ? line.substring(lastDot + 1) : line;
                        return;
                    }
                }
            }
        }
    }
    
    /**
     * Parse exception info from stack trace content.
     */
    private void parseExceptionFromTrace(CrashEvent event, String traceContent) {
        if (traceContent == null || traceContent.isEmpty()) return;
        
        String[] lines = traceContent.split("\n");
        for (String line : lines) {
            line = line.trim();
            
            // Skip "at" lines (stack frames)
            if (line.startsWith("at ")) continue;
            
            // Look for exception patterns: "java.lang.NullPointerException: message"
            if ((line.contains("Exception") || line.contains("Error")) && 
                !line.startsWith("Caused by:")) {
                
                int colonIdx = line.indexOf(':');
                String exceptionPart = colonIdx > 0 ? line.substring(0, colonIdx).trim() : line.trim();
                
                // Extract simple class name
                int lastDot = exceptionPart.lastIndexOf('.');
                if (lastDot > 0) {
                    event.exceptionType = exceptionPart.substring(lastDot + 1);
                } else if (exceptionPart.endsWith("Exception") || exceptionPart.endsWith("Error")) {
                    event.exceptionType = exceptionPart;
                }
                
                // Get message if present
                if (colonIdx > 0 && colonIdx + 1 < line.length()) {
                    event.exceptionMessage = truncate(line.substring(colonIdx + 1).trim(), 200);
                }
                
                if (event.exceptionType != null) return;
            }
        }
    }

    /**
     * Parse main thread state from ANR trace.
     */
    private String parseMainThreadState(String traceContent) {
        if (traceContent == null)
            return null;

        // Look for main thread state line
        // e.g., "main" prio=5 tid=1 BLOCKED
        String[] lines = traceContent.split("\n");
        for (String line : lines) {
            if (line.contains("\"main\"") || line.contains("tid=1")) {
                // Extract state (BLOCKED, WAITING, etc.)
                String[] states = { "BLOCKED", "WAITING", "TIMED_WAITING",
                        "RUNNABLE", "NATIVE", "SUSPENDED" };
                for (String state : states) {
                    if (line.contains(state)) {
                        return state;
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Read input stream to string (limited length).
     */
    private String readInputStream(InputStream is, int maxLength) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && sb.length() < maxLength) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Compute SHA-256 hash of content.
     */
    private String computeHash(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(content.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16); // Use first 16 chars
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Truncate string to max length.
     */
    private String truncate(String s, int maxLength) {
        if (s == null)
            return null;
        return s.length() <= maxLength ? s : s.substring(0, maxLength);
    }

    // =========================================================================
    // Query Methods
    // =========================================================================

    /**
     * Get crash count since a timestamp.
     */
    public int getCrashCountSince(long since) {
        return database.crashEventDao().getCountSince(since);
    }

    /**
     * Get ANR count since a timestamp.
     */
    public int getAnrCountSince(long since) {
        return database.anrEventDao().getCountSince(since);
    }

    /**
     * Get crashes for a specific package.
     */
    public List<CrashEvent> getCrashesForPackage(String packageName, long since) {
        return database.crashEventDao().getByPackageSince(packageName, since);
    }

    /**
     * Get ANRs for a specific package.
     */
    public List<AnrEvent> getAnrsForPackage(String packageName, long since) {
        return database.anrEventDao().getByPackageSince(packageName, since);
    }

    /**
     * Get new crash types since a timestamp.
     */
    public List<CrashEvent> getNewCrashTypes(long since) {
        return database.crashEventDao().getNewCrashTypes(since);
    }

    /**
     * Get new ANR types since a timestamp.
     */
    public List<AnrEvent> getNewAnrTypes(long since) {
        return database.anrEventDao().getNewAnrTypes(since);
    }

    /**
     * Clean up old events.
     */
    public void cleanupOldEvents(long olderThan) {
        database.crashEventDao().deleteOlderThan(olderThan);
        database.anrEventDao().deleteOlderThan(olderThan);
    }
}
