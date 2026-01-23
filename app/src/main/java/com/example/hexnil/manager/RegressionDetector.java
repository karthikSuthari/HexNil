package com.example.hexnil.manager;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.DailyAggregate;
import com.example.hexnil.model.RegressionEvent;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Regression Detector
 * 
 * Detects performance regressions by comparing current metrics against
 * baselines.
 * 
 * Regression Types:
 * - launch_time: App launch time degraded >20% vs 7-day baseline
 * - jank: Frame jank increased >15% vs 7-day baseline
 * - memory: Sustained low-memory pressure increase
 * - crash: New or increased crash frequency (>50%)
 * - anr: Any new ANR after app/system update
 * - thermal: Sustained temperature increase >3°C
 * 
 * Triggers:
 * - app_update: Detected after app version change
 * - system_update: Detected after OS version change
 * - long_term_decay: Gradual degradation over 30+ days
 */
public class RegressionDetector {

    private static final String TAG = "RegressionDetector";

    private final Context context;
    private final HexNilDatabase database;
    private final PackageManager packageManager;
    private final Executor executor;

    // Thresholds for regression detection
    private static final float LAUNCH_TIME_THRESHOLD_PERCENT = 20.0f; // >20% slower
    private static final float JANK_THRESHOLD_PERCENT = 15.0f; // >15% more jank
    private static final float CRASH_INCREASE_THRESHOLD_PERCENT = 50.0f; // >50% more crashes
    private static final float THERMAL_INCREASE_THRESHOLD_C = 3.0f; // >3°C increase
    private static final int LOW_MEMORY_SUSTAINED_COUNT = 3; // 3+ low mem events/day

    // Minimum data points required for reliable detection
    private static final int MIN_BASELINE_DAYS = 3;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    public RegressionDetector(Context context, HexNilDatabase database) {
        this.context = context.getApplicationContext();
        this.database = database;
        this.packageManager = context.getPackageManager();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Run full regression detection.
     * Should be called after daily aggregation completes.
     */
    public void runDetection() {
        executor.execute(this::runDetectionSync);
    }

    /**
     * Synchronous version of regression detection.
     */
    public void runDetectionSync() {
        try {
            Log.i(TAG, "Starting regression detection...");

            List<RegressionEvent> detected = new ArrayList<>();

            // Get latest aggregate
            DailyAggregate latest = database.dailyAggregateDao().getLatest();
            if (latest == null) {
                Log.d(TAG, "No aggregates available for detection");
                return;
            }

            // Device-level regressions
            detected.addAll(detectDeviceLevelRegressions(latest));

            // Per-app regressions
            List<String> packages = database.dailyAggregateDao().getDistinctPackages();
            for (String pkg : packages) {
                if (pkg != null) {
                    detected.addAll(detectAppRegressions(pkg, latest.date));
                }
            }

            // Store detected regressions
            int newCount = 0;
            for (RegressionEvent event : detected) {
                // Check if already exists (avoid duplicates)
                if (!database.regressionEventDao().hasActiveRegression(
                        event.packageName, event.regressionType)) {
                    database.regressionEventDao().insert(event);
                    newCount++;

                    Log.w(TAG, "New regression detected: " + event.getSummary());
                }
            }

            Log.i(TAG, String.format("Regression detection complete. %d new regressions.",
                    newCount));

        } catch (Exception e) {
            Log.e(TAG, "Error in regression detection", e);
        }
    }

    /**
     * Detect device-level regressions.
     */
    private List<RegressionEvent> detectDeviceLevelRegressions(DailyAggregate latest) {
        List<RegressionEvent> regressions = new ArrayList<>();

        try {
            // Memory pressure regression
            if (latest.lowMemoryEventCount >= LOW_MEMORY_SUSTAINED_COUNT) {
                // Check if this is new (compare to baseline)
                String date7DaysAgo = getDateNDaysAgo(latest.date, 7);
                List<DailyAggregate> history = database.dailyAggregateDao()
                        .getDeviceLevelInRange(date7DaysAgo, latest.date);

                if (history.size() >= MIN_BASELINE_DAYS) {
                    float avgLowMemEvents = 0;
                    for (DailyAggregate agg : history) {
                        avgLowMemEvents += agg.lowMemoryEventCount;
                    }
                    avgLowMemEvents /= history.size();

                    if (latest.lowMemoryEventCount > avgLowMemEvents * 1.5f &&
                            avgLowMemEvents < LOW_MEMORY_SUSTAINED_COUNT) {

                        RegressionEvent event = createRegression(
                                null, "memory",
                                avgLowMemEvents, latest.lowMemoryEventCount,
                                50.0f, "count");
                        event.explanation = String.format(Locale.US,
                                "Low memory events increased from %.1f/day to %d/day",
                                avgLowMemEvents, latest.lowMemoryEventCount);
                        regressions.add(event);
                    }
                }
            }

            // Thermal regression
            if (latest.baseline7DayTemp > 0 && latest.avgTemp > 0) {
                float tempDelta = latest.avgTemp - latest.baseline7DayTemp;
                if (tempDelta > THERMAL_INCREASE_THRESHOLD_C) {
                    RegressionEvent event = createRegression(
                            null, "thermal",
                            latest.baseline7DayTemp, latest.avgTemp,
                            THERMAL_INCREASE_THRESHOLD_C, "°C");
                    event.explanation = String.format(Locale.US,
                            "Average temperature increased by %.1f°C (from %.1f°C to %.1f°C)",
                            tempDelta, latest.baseline7DayTemp, latest.avgTemp);
                    regressions.add(event);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error detecting device regressions", e);
        }

        return regressions;
    }

    /**
     * Detect app-specific regressions.
     */
    private List<RegressionEvent> detectAppRegressions(String packageName, String date) {
        List<RegressionEvent> regressions = new ArrayList<>();

        try {
            // Get latest aggregate for this app
            DailyAggregate latest = database.dailyAggregateDao()
                    .getByDateAndPackage(date, packageName);
            if (latest == null)
                return regressions;

            // Get 7-day history for baseline
            String date7DaysAgo = getDateNDaysAgo(date, 7);
            List<DailyAggregate> history = database.dailyAggregateDao()
                    .getByPackageInRange(packageName, date7DaysAgo, date);

            if (history.size() < MIN_BASELINE_DAYS) {
                // Not enough data for reliable detection
                return regressions;
            }

            // Check for app update
            boolean isAppUpdate = detectAppUpdate(packageName, history);
            String triggerEvent = isAppUpdate ? "app_update" : "long_term_decay";

            // Launch time regression
            if (latest.baseline7DayLaunchMs > 0 && latest.avgColdLaunchMs > 0) {
                float deltaPercent = (latest.avgColdLaunchMs - latest.baseline7DayLaunchMs)
                        / latest.baseline7DayLaunchMs * 100;

                if (deltaPercent > LAUNCH_TIME_THRESHOLD_PERCENT) {
                    RegressionEvent event = createRegression(
                            packageName, "launch_time",
                            latest.baseline7DayLaunchMs, latest.avgColdLaunchMs,
                            LAUNCH_TIME_THRESHOLD_PERCENT, "ms");
                    event.triggerEvent = triggerEvent;
                    event.explanation = String.format(Locale.US,
                            "Cold launch time increased by %.1f%% (from %.0fms to %.0fms)",
                            deltaPercent, latest.baseline7DayLaunchMs, latest.avgColdLaunchMs);
                    regressions.add(event);
                }
            }

            // Jank regression
            if (latest.baseline7DayJankPercent > 0 && latest.avgJankPercent > 0) {
                float deltaPercent = (latest.avgJankPercent - latest.baseline7DayJankPercent)
                        / latest.baseline7DayJankPercent * 100;

                if (deltaPercent > JANK_THRESHOLD_PERCENT) {
                    RegressionEvent event = createRegression(
                            packageName, "jank",
                            latest.baseline7DayJankPercent, latest.avgJankPercent,
                            JANK_THRESHOLD_PERCENT, "%");
                    event.triggerEvent = triggerEvent;
                    event.explanation = String.format(Locale.US,
                            "Frame jank increased by %.1f%% (from %.1f%% to %.1f%%)",
                            deltaPercent, latest.baseline7DayJankPercent, latest.avgJankPercent);
                    regressions.add(event);
                }
            }

            // Crash regression
            if (latest.baseline7DayCrashCount > 0 && latest.crashCount > 0) {
                float deltaPercent = (latest.crashCount - latest.baseline7DayCrashCount)
                        / latest.baseline7DayCrashCount * 100;

                if (deltaPercent > CRASH_INCREASE_THRESHOLD_PERCENT) {
                    RegressionEvent event = createRegression(
                            packageName, "crash",
                            latest.baseline7DayCrashCount, latest.crashCount,
                            CRASH_INCREASE_THRESHOLD_PERCENT, "count");
                    event.triggerEvent = triggerEvent;
                    event.explanation = String.format(Locale.US,
                            "Crash frequency increased by %.1f%% (from %.1f to %d per day)",
                            deltaPercent, latest.baseline7DayCrashCount, latest.crashCount);
                    regressions.add(event);
                }
            }

            // New crash type after update
            if (latest.hasNewCrashType && isAppUpdate) {
                RegressionEvent event = createRegression(
                        packageName, "crash",
                        0, 1, 0, "new_type");
                event.triggerEvent = "app_update";
                event.explanation = "New crash type detected after app update";
                event.severity = "high";
                regressions.add(event);
            }

            // ANR after update (any ANR after update is critical)
            if (latest.anrCount > 0 && isAppUpdate) {
                RegressionEvent event = createRegression(
                        packageName, "anr",
                        0, latest.anrCount, 0, "count");
                event.triggerEvent = "app_update";
                event.explanation = String.format(Locale.US,
                        "%d ANR(s) detected after app update", latest.anrCount);
                event.severity = "critical";
                regressions.add(event);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error detecting app regressions for " + packageName, e);
        }

        return regressions;
    }

    /**
     * Create a regression event with common fields populated.
     */
    private RegressionEvent createRegression(String packageName, String type,
            float baseline, float current,
            float threshold, String unit) {
        RegressionEvent event = new RegressionEvent(packageName, type,
                baseline, current, threshold);

        event.unit = unit;
        event.systemVersion = Build.VERSION.RELEASE;
        event.evidenceTableName = getEvidenceTable(type);

        // Set app version if package specified
        if (packageName != null) {
            try {
                PackageInfo info = packageManager.getPackageInfo(packageName, 0);
                event.currentVersion = info.versionName;
                event.appLabel = packageManager.getApplicationLabel(
                        info.applicationInfo).toString();
            } catch (PackageManager.NameNotFoundException e) {
                event.appLabel = packageName;
            }
        }

        // Calculate evidence time range
        long now = System.currentTimeMillis();
        event.evidenceEndTime = now;
        event.evidenceStartTime = now - (7 * 24 * 60 * 60 * 1000L); // 7 days

        return event;
    }

    /**
     * Get the evidence table for a regression type.
     */
    private String getEvidenceTable(String type) {
        switch (type) {
            case "launch_time":
                return "app_launch_metrics";
            case "jank":
                return "frame_metrics";
            case "memory":
                return "memory_snapshots";
            case "crash":
                return "crash_events";
            case "anr":
                return "anr_events";
            case "thermal":
                return "daily_aggregates";
            default:
                return "daily_aggregates";
        }
    }

    /**
     * Detect if app was recently updated.
     */
    private boolean detectAppUpdate(String packageName, List<DailyAggregate> history) {
        if (history.size() < 2)
            return false;

        // Check if version changed between first and last aggregate
        String firstVersion = null;
        String lastVersion = null;

        for (DailyAggregate agg : history) {
            if (agg.appVersion != null) {
                if (firstVersion == null) {
                    firstVersion = agg.appVersion;
                }
                lastVersion = agg.appVersion;
            }
        }

        if (firstVersion != null && lastVersion != null && !firstVersion.equals(lastVersion)) {
            return true;
        }

        // Also try to detect from package manager
        try {
            PackageInfo info = packageManager.getPackageInfo(packageName, 0);
            long updateTime = info.lastUpdateTime;
            long weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);
            return updateTime > weekAgo;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get date string N days ago.
     */
    private String getDateNDaysAgo(String dateStr, int n) {
        try {
            Date date = DATE_FORMAT.parse(dateStr);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(Calendar.DAY_OF_YEAR, -n);
            return DATE_FORMAT.format(cal.getTime());
        } catch (Exception e) {
            return dateStr;
        }
    }

    // =========================================================================
    // Query Methods
    // =========================================================================

    /**
     * Get all active (unresolved) regressions.
     */
    public List<RegressionEvent> getActiveRegressions() {
        return database.regressionEventDao().getActiveRegressions();
    }

    /**
     * Get high-severity active regressions.
     */
    public List<RegressionEvent> getHighSeverityRegressions() {
        return database.regressionEventDao().getHighSeverityActive();
    }

    /**
     * Get regressions requiring user attention.
     */
    public List<RegressionEvent> getRegressionsRequiringAttention() {
        return database.regressionEventDao().getRequiringAttention();
    }

    /**
     * Mark a regression as resolved.
     */
    public void markResolved(long regressionId, String resolutionMethod, float resolvedValue) {
        database.regressionEventDao().markResolved(
                regressionId, System.currentTimeMillis(), resolutionMethod, resolvedValue);
    }

    /**
     * Mark a regression as notified to user.
     */
    public void markNotified(long regressionId) {
        database.regressionEventDao().markNotified(regressionId);
    }

    /**
     * Dismiss a regression.
     */
    public void dismissRegression(long regressionId) {
        database.regressionEventDao().markDismissed(regressionId);
    }

    /**
     * Get active regression count.
     */
    public int getActiveRegressionCount() {
        return database.regressionEventDao().getActiveCount();
    }
}
