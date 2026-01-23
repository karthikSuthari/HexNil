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
            event.pss = info.getPss() * 1024L; // Convert KB to bytes
            event.rss = info.getRss() * 1024L;

            // Set crash type
            event.crashType = mapReasonToCrashType(info.getReason());

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
                    String traceContent = readInputStream(traceStream, 2000);
                    if (traceContent != null && !traceContent.isEmpty()) {
                        event.stackTraceHash = computeHash(traceContent);
                    }
                    traceStream.close();
                }
            } catch (Exception e) {
                Log.d(TAG, "Could not read trace: " + e.getMessage());
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
            if (event.stackTraceHash != null) {
                event.isNewCrashType = !database.crashEventDao()
                        .existsByHash(event.stackTraceHash, packageName);
            }

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
            default:
                return "unknown";
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

        // Common patterns to extract exception type
        // e.g., "java.lang.NullPointerException: ..."
        String[] parts = description.split("[:\\n]");
        if (parts.length > 0) {
            String firstPart = parts[0].trim();
            if (firstPart.contains("Exception") || firstPart.contains("Error")) {
                event.exceptionType = firstPart;
                if (parts.length > 1) {
                    event.exceptionMessage = truncate(parts[1].trim(), 200);
                }
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
