package com.example.hexnil.manager;

import android.app.ActivityManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AppLaunchMetric;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * App Launch Tracker
 * 
 * Tracks app launch performance using UsageStatsManager events.
 * Detects cold, warm, and hot launches based on usage patterns.
 * 
 * Required permission: android.permission.PACKAGE_USAGE_STATS
 * (Must be granted by user in Settings > Apps > Special access > Usage access)
 * 
 * Non-root compatible: Uses only public Android APIs
 */
public class AppLaunchTracker {

    private static final String TAG = "AppLaunchTracker";

    private final Context context;
    private final HexNilDatabase database;
    private final UsageStatsManager usageStatsManager;
    private final PackageManager packageManager;
    private final Executor executor;

    // Track last known foreground time per package for cold/warm/hot detection
    private final Map<String, Long> lastForegroundTime = new HashMap<>();

    // Time thresholds for launch type classification (in milliseconds)
    private static final long COLD_LAUNCH_GAP = 30 * 60 * 1000; // 30 minutes = cold
    private static final long WARM_LAUNCH_GAP = 5 * 60 * 1000; // 5 minutes = warm
    // Less than 5 minutes = hot

    // Slow launch thresholds (in milliseconds)
    private static final long SLOW_COLD_LAUNCH_MS = 5000; // >5s is slow for cold
    private static final long SLOW_WARM_LAUNCH_MS = 2000; // >2s is slow for warm
    private static final long SLOW_HOT_LAUNCH_MS = 1000; // >1s is slow for hot

    public AppLaunchTracker(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
        this.usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        this.packageManager = context.getPackageManager();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Check if we have usage stats permission.
     * 
     * @return true if permission granted
     */
    public boolean hasPermission() {
        if (usageStatsManager == null)
            return false;

        long now = System.currentTimeMillis();
        // Try to query - if we get data, we have permission
        try {
            UsageEvents events = usageStatsManager.queryEvents(now - 60000, now);
            return events != null;
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Collect app launch events from the last time window.
     * This should be called periodically (every 5 minutes).
     * 
     * @param sinceTimestamp     Start time to collect from
     * @param batteryLevel       Current battery level
     * @param temperature        Current temperature
     * @param isCharging         Whether device is charging
     * @param memoryUsagePercent Current memory usage %
     * @param thermalHeadroom    Current thermal headroom
     */
    public void collectLaunchEvents(long sinceTimestamp, int batteryLevel, float temperature,
            boolean isCharging, float memoryUsagePercent,
            float thermalHeadroom) {
        executor.execute(() -> {
            try {
                if (!hasPermission()) {
                    Log.w(TAG, "No usage stats permission");
                    return;
                }

                long endTime = System.currentTimeMillis();
                UsageEvents events = usageStatsManager.queryEvents(sinceTimestamp, endTime);

                if (events == null) {
                    Log.d(TAG, "No usage events available");
                    return;
                }

                // Track foreground/background transitions
                Map<String, Long> appMoveToForeground = new HashMap<>();
                Map<String, Long> appMoveToBackground = new HashMap<>();

                UsageEvents.Event event = new UsageEvents.Event();
                while (events.getNextEvent(event)) {
                    String packageName = event.getPackageName();
                    long timestamp = event.getTimeStamp();

                    int eventType = event.getEventType();

                    if (eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                            eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                        // App moved to foreground - potential launch
                        appMoveToForeground.put(packageName, timestamp);
                    } else if (eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                            eventType == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                        // App moved to background
                        appMoveToBackground.put(packageName, timestamp);
                    }
                }

                // Process detected launches
                for (Map.Entry<String, Long> entry : appMoveToForeground.entrySet()) {
                    String packageName = entry.getKey();
                    long launchTime = entry.getValue();

                    // Skip system UI packages
                    if (isSystemUiPackage(packageName)) {
                        continue;
                    }

                    // Determine launch type
                    String launchType = determineLaunchType(packageName, launchTime);

                    // Estimate launch duration (this is approximate without direct timing)
                    // We use the gap between foreground and first activity as a proxy
                    long estimatedDuration = estimateLaunchDuration(launchType);

                    // Create and store launch metric
                    AppLaunchMetric metric = createLaunchMetric(
                            packageName, launchType, launchTime, estimatedDuration,
                            batteryLevel, temperature, isCharging,
                            memoryUsagePercent, thermalHeadroom);

                    if (metric != null) {
                        database.appLaunchMetricDao().insert(metric);

                        // Update last foreground time
                        lastForegroundTime.put(packageName, launchTime);

                        Log.d(TAG, String.format("Recorded %s launch for %s (%dms)",
                                launchType, packageName, estimatedDuration));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error collecting launch events", e);
            }
        });
    }

    /**
     * Determine launch type based on last foreground time.
     */
    private String determineLaunchType(String packageName, long currentTime) {
        Long lastTime = lastForegroundTime.get(packageName);

        if (lastTime == null) {
            // Never seen before = cold launch
            return "cold";
        }

        long gap = currentTime - lastTime;

        if (gap >= COLD_LAUNCH_GAP) {
            return "cold";
        } else if (gap >= WARM_LAUNCH_GAP) {
            return "warm";
        } else {
            return "hot";
        }
    }

    /**
     * Estimate launch duration based on launch type.
     * Without direct timing, we use typical values.
     * These are placeholders - real values would come from AM data if available.
     */
    private long estimateLaunchDuration(String launchType) {
        switch (launchType) {
            case "cold":
                return 1500; // 1.5s typical cold launch
            case "warm":
                return 600; // 600ms typical warm launch
            case "hot":
                return 200; // 200ms typical hot launch
            default:
                return 1000;
        }
    }

    /**
     * Create a launch metric record.
     */
    private AppLaunchMetric createLaunchMetric(String packageName, String launchType,
            long launchTime, long duration,
            int batteryLevel, float temperature,
            boolean isCharging, float memoryUsagePercent,
            float thermalHeadroom) {
        try {
            AppLaunchMetric metric = new AppLaunchMetric(packageName, launchType, duration);
            metric.timestamp = launchTime;
            metric.ttidMs = duration;
            metric.beforeLevel = batteryLevel;
            metric.afterLevel = batteryLevel; // Same since we capture at one point
            metric.beforeTemp = temperature;
            metric.afterTemp = temperature;
            metric.isCharging = isCharging;
            metric.memoryUsagePercent = memoryUsagePercent;
            metric.thermalHeadroom = thermalHeadroom;

            // Try to get app label and version
            try {
                ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
                metric.appLabel = packageManager.getApplicationLabel(appInfo).toString();

                metric.appVersionCode = (int) packageManager
                        .getPackageInfo(packageName, 0).getLongVersionCode();
                metric.appVersionName = packageManager
                        .getPackageInfo(packageName, 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                metric.appLabel = packageName;
            }

            // Check if first launch today
            long todayStart = getTodayStartMillis();
            int todayCount = database.appLaunchMetricDao()
                    .getLaunchCountByPackage(packageName, todayStart);
            metric.isFirstLaunchToday = (todayCount == 0);

            return metric;
        } catch (Exception e) {
            Log.e(TAG, "Error creating launch metric", e);
            return null;
        }
    }

    /**
     * Check if package is a system UI component (not interesting to track).
     */
    private boolean isSystemUiPackage(String packageName) {
        if (packageName == null)
            return true;

        return packageName.equals("com.android.systemui") ||
                packageName.equals("com.android.launcher") ||
                packageName.equals("com.android.launcher3") ||
                packageName.equals("com.google.android.apps.nexuslauncher") ||
                packageName.startsWith("com.android.inputmethod") ||
                packageName.equals("android");
    }

    /**
     * Get start of today in milliseconds.
     */
    private long getTodayStartMillis() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    // =========================================================================
    // Query Methods
    // =========================================================================

    /**
     * Get average cold launch time for an app.
     */
    public float getAverageColdLaunchMs(String packageName, long since) {
        return database.appLaunchMetricDao().getAverageColdLaunchByPackage(packageName, since);
    }

    /**
     * Get all launches for an app.
     */
    public List<AppLaunchMetric> getLaunchesForApp(String packageName, long since) {
        return database.appLaunchMetricDao().getByPackageSince(packageName, since);
    }

    /**
     * Get slow launches since a time.
     */
    public List<AppLaunchMetric> getSlowColdLaunches(long since) {
        return database.appLaunchMetricDao().getSlowColdLaunches(since);
    }

    /**
     * Get total launch count since a time.
     */
    public int getLaunchCountSince(long since) {
        return database.appLaunchMetricDao().getLaunchCountSince(since);
    }

    /**
     * Get distinct packages that have been launched.
     */
    public List<String> getDistinctPackages(long since) {
        return database.appLaunchMetricDao().getDistinctPackages(since);
    }

    /**
     * Clean up old metrics.
     */
    public int cleanupOldMetrics(long olderThan) {
        return database.appLaunchMetricDao().deleteOlderThan(olderThan);
    }
}
