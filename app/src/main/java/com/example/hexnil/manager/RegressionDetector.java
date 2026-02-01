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
                        
                // Get previous version from history
                event.previousVersion = getPreviousAppVersion(packageName, info.versionName);
            } catch (PackageManager.NameNotFoundException e) {
                event.appLabel = packageName;
            }
        }

        // Calculate evidence time range
        long now = System.currentTimeMillis();
        event.evidenceEndTime = now;
        event.evidenceStartTime = now - (7 * 24 * 60 * 60 * 1000L); // 7 days

        // Populate additional fields
        event.previousSystemVersion = getPreviousSystemVersion();
        event.evidenceCount = calculateEvidenceCount(type, event.evidenceStartTime, event.evidenceEndTime, packageName);
        event.confidenceScore = calculateConfidenceScore(event);
        event.rootCause = inferRootCause(type, event);
        event.trend = calculateTrend(type, packageName, event.evidenceStartTime);
        event.evidenceDataJson = buildEvidenceDataJson(type, packageName, event.evidenceStartTime);
        event.evidenceQuery = buildEvidenceQuery(type, packageName, event.evidenceStartTime, event.evidenceEndTime);
        
        // Set recommendations
        event.userRecommendation = getUserRecommendation(type, event);
        event.engineerRecommendation = getEngineerRecommendation(type, event);

        return event;
    }
    
    /**
     * Get previous app version from history.
     */
    private String getPreviousAppVersion(String packageName, String currentVersion) {
        try {
            List<DailyAggregate> history = database.dailyAggregateDao()
                    .getByPackageInRange(packageName, getDateNDaysAgo(DATE_FORMAT.format(new Date()), 30), 
                            DATE_FORMAT.format(new Date()));
            
            for (DailyAggregate agg : history) {
                if (agg.appVersion != null && !agg.appVersion.equals(currentVersion)) {
                    return agg.appVersion;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting previous app version", e);
        }
        return null;
    }
    
    /**
     * Get previous system version from stored state or detect OS update.
     */
    private String getPreviousSystemVersion() {
        try {
            // Check if we stored previous version
            String storedPrev = context.getSharedPreferences("hexnil_prefs", Context.MODE_PRIVATE)
                    .getString("previous_system_version", null);
            
            // Also check daily aggregates for system version changes
            if (storedPrev == null) {
                List<DailyAggregate> history = database.dailyAggregateDao()
                        .getDeviceLevelInRange(getDateNDaysAgo(DATE_FORMAT.format(new Date()), 30),
                                DATE_FORMAT.format(new Date()));
                
                String currentVer = Build.VERSION.RELEASE;
                for (DailyAggregate agg : history) {
                    // Could track system version in aggregates - for now return null if not stored
                }
            }
            return storedPrev;
        } catch (Exception e) {
            Log.e(TAG, "Error getting previous system version", e);
            return null;
        }
    }
    
    /**
     * Calculate evidence count based on regression type.
     */
    private int calculateEvidenceCount(String type, long startTime, long endTime, String packageName) {
        try {
            switch (type) {
                case "launch_time":
                    return database.dailyAggregateDao().getAggregateCountSince(startTime);
                case "jank":
                    return database.dailyAggregateDao().getAggregateCountSince(startTime);
                case "memory":
                    // Count low memory events
                    List<DailyAggregate> memHistory = database.dailyAggregateDao()
                            .getDeviceLevelInRange(getDateNDaysAgo(DATE_FORMAT.format(new Date(endTime)), 7),
                                    DATE_FORMAT.format(new Date(endTime)));
                    int memEvents = 0;
                    for (DailyAggregate agg : memHistory) {
                        memEvents += agg.lowMemoryEventCount;
                    }
                    return memEvents;
                case "crash":
                    if (packageName != null) {
                        List<DailyAggregate> crashHistory = database.dailyAggregateDao()
                                .getByPackageInRange(packageName, 
                                        getDateNDaysAgo(DATE_FORMAT.format(new Date(endTime)), 7),
                                        DATE_FORMAT.format(new Date(endTime)));
                        int crashes = 0;
                        for (DailyAggregate agg : crashHistory) {
                            crashes += agg.crashCount;
                        }
                        return crashes;
                    }
                    return 0;
                case "anr":
                    if (packageName != null) {
                        List<DailyAggregate> anrHistory = database.dailyAggregateDao()
                                .getByPackageInRange(packageName,
                                        getDateNDaysAgo(DATE_FORMAT.format(new Date(endTime)), 7),
                                        DATE_FORMAT.format(new Date(endTime)));
                        int anrs = 0;
                        for (DailyAggregate agg : anrHistory) {
                            anrs += agg.anrCount;
                        }
                        return anrs;
                    }
                    return 0;
                case "thermal":
                    return database.batterySnapshotDao().getCountSince(startTime);
                default:
                    return database.dailyAggregateDao().getAggregateCountSince(startTime);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error calculating evidence count", e);
            return 0;
        }
    }
    
    /**
     * Calculate confidence score based on evidence strength.
     */
    private float calculateConfidenceScore(RegressionEvent event) {
        float confidence = 0.5f; // Base confidence
        
        // More evidence = higher confidence
        if (event.evidenceCount >= 10) confidence += 0.2f;
        else if (event.evidenceCount >= 5) confidence += 0.1f;
        
        // Larger delta = higher confidence (more obvious regression)
        if (Math.abs(event.deltaPercent) > 50) confidence += 0.15f;
        else if (Math.abs(event.deltaPercent) > 30) confidence += 0.1f;
        else if (Math.abs(event.deltaPercent) > 20) confidence += 0.05f;
        
        // Known trigger increases confidence
        if ("app_update".equals(event.triggerEvent) || "system_update".equals(event.triggerEvent)) {
            confidence += 0.15f;
        }
        
        // Type-specific confidence adjustments
        switch (event.regressionType) {
            case "anr":
                confidence += 0.2f; // ANRs are clear regressions
                break;
            case "crash":
                confidence += 0.15f;
                break;
            case "launch_time":
            case "jank":
                // These are statistical so slightly lower confidence
                break;
        }
        
        return Math.min(1.0f, confidence);
    }
    
    /**
     * Infer root cause based on regression type and context.
     */
    private String inferRootCause(String type, RegressionEvent event) {
        StringBuilder cause = new StringBuilder();
        
        boolean isAppUpdate = "app_update".equals(event.triggerEvent);
        boolean isSystemUpdate = "system_update".equals(event.triggerEvent);
        
        switch (type) {
            case "launch_time":
                if (isAppUpdate) {
                    cause.append("App update introduced slower initialization. ");
                    cause.append("Possible causes: new dependencies, larger assets, heavier startup logic.");
                } else if (isSystemUpdate) {
                    cause.append("System update may have affected app compatibility or startup sequence.");
                } else {
                    cause.append("Gradual launch time degradation. ");
                    cause.append("Possible causes: accumulated cache, data growth, background services.");
                }
                break;
                
            case "jank":
                if (isAppUpdate) {
                    cause.append("App update introduced UI performance issues. ");
                    cause.append("Possible causes: heavier animations, inefficient layouts, main thread work.");
                } else {
                    cause.append("Frame rendering degradation. ");
                    cause.append("Possible causes: memory pressure, thermal throttling, competing processes.");
                }
                break;
                
            case "memory":
                cause.append("Increased memory pressure detected. ");
                cause.append("Possible causes: memory leaks, large allocations, insufficient device RAM.");
                break;
                
            case "crash":
                if (isAppUpdate) {
                    cause.append("New crashes detected after app update. ");
                    cause.append("Possible causes: new code bugs, API compatibility issues, missing resources.");
                } else {
                    cause.append("Increased crash frequency. ");
                    cause.append("Possible causes: edge case bugs, resource exhaustion, external dependencies.");
                }
                break;
                
            case "anr":
                cause.append("Application Not Responding - main thread blocked. ");
                if (isAppUpdate) {
                    cause.append("App update likely introduced main thread blocking operations.");
                } else {
                    cause.append("Possible causes: slow I/O, network on main thread, deadlocks, heavy computation.");
                }
                break;
                
            case "thermal":
                cause.append("Sustained temperature increase. ");
                cause.append("Possible causes: CPU-intensive operations, poor efficiency, external heat source.");
                break;
                
            default:
                cause.append("Performance regression detected. Root cause requires further investigation.");
        }
        
        return cause.toString();
    }
    
    /**
     * Calculate trend based on recent values.
     */
    private String calculateTrend(String type, String packageName, long startTime) {
        try {
            String startDate = DATE_FORMAT.format(new Date(startTime));
            String endDate = DATE_FORMAT.format(new Date());
            
            List<DailyAggregate> history;
            if (packageName != null) {
                history = database.dailyAggregateDao().getByPackageInRange(packageName, startDate, endDate);
            } else {
                history = database.dailyAggregateDao().getDeviceLevelInRange(startDate, endDate);
            }
            
            if (history.size() < 3) return "insufficient_data";
            
            // Get metric values based on type
            List<Float> values = new ArrayList<>();
            for (DailyAggregate agg : history) {
                float value = getMetricValue(agg, type);
                if (value > 0) values.add(value);
            }
            
            if (values.size() < 3) return "insufficient_data";
            
            // Calculate trend using simple linear regression slope direction
            int n = values.size();
            float firstHalfAvg = 0;
            float secondHalfAvg = 0;
            int halfPoint = n / 2;
            
            for (int i = 0; i < halfPoint; i++) {
                firstHalfAvg += values.get(i);
            }
            firstHalfAvg /= halfPoint;
            
            for (int i = halfPoint; i < n; i++) {
                secondHalfAvg += values.get(i);
            }
            secondHalfAvg /= (n - halfPoint);
            
            float changePct = (secondHalfAvg - firstHalfAvg) / Math.max(1f, firstHalfAvg) * 100f;
            
            if (changePct > 10f) return "worsening";
            if (changePct < -10f) return "improving";
            return "stable";
            
        } catch (Exception e) {
            Log.e(TAG, "Error calculating trend", e);
            return "unknown";
        }
    }
    
    /**
     * Get metric value from aggregate based on regression type.
     */
    private float getMetricValue(DailyAggregate agg, String type) {
        switch (type) {
            case "launch_time": return agg.avgColdLaunchMs;
            case "jank": return agg.avgJankPercent;
            case "memory": return agg.lowMemoryEventCount;
            case "crash": return agg.crashCount;
            case "anr": return agg.anrCount;
            case "thermal": return agg.avgTemp;
            default: return 0;
        }
    }
    
    /**
     * Build JSON summary of key evidence data points.
     */
    private String buildEvidenceDataJson(String type, String packageName, long startTime) {
        try {
            String startDate = DATE_FORMAT.format(new Date(startTime));
            String endDate = DATE_FORMAT.format(new Date());
            
            List<DailyAggregate> history;
            if (packageName != null) {
                history = database.dailyAggregateDao().getByPackageInRange(packageName, startDate, endDate);
            } else {
                history = database.dailyAggregateDao().getDeviceLevelInRange(startDate, endDate);
            }
            
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (DailyAggregate agg : history) {
                float value = getMetricValue(agg, type);
                if (value > 0) {
                    if (!first) json.append(",");
                    json.append(String.format(Locale.US, "{\"date\":\"%s\",\"value\":%.2f}", agg.date, value));
                    first = false;
                }
            }
            json.append("]");
            return json.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error building evidence JSON", e);
            return "[]";
        }
    }
    
    /**
     * Build SQL query for evidence retrieval.
     */
    private String buildEvidenceQuery(String type, String packageName, long startTime, long endTime) {
        String table = getEvidenceTable(type);
        String startDate = DATE_FORMAT.format(new Date(startTime));
        String endDate = DATE_FORMAT.format(new Date(endTime));
        
        if (packageName != null) {
            return String.format(Locale.US, 
                    "SELECT * FROM %s WHERE packageName='%s' AND date BETWEEN '%s' AND '%s' ORDER BY date DESC",
                    table, packageName, startDate, endDate);
        } else {
            return String.format(Locale.US,
                    "SELECT * FROM %s WHERE date BETWEEN '%s' AND '%s' ORDER BY date DESC",
                    table, startDate, endDate);
        }
    }
    
    /**
     * Get user-facing recommendation.
     */
    private String getUserRecommendation(String type, RegressionEvent event) {
        switch (type) {
            case "launch_time":
                return "Try clearing app cache or reinstalling the app if startup is too slow.";
            case "jank":
                return "Reduce animations in accessibility settings if the UI feels sluggish.";
            case "memory":
                return "Close unused apps and clear cache to free up memory.";
            case "crash":
                if ("app_update".equals(event.triggerEvent)) {
                    return "Report crashes to the app developer. Consider rolling back to previous version.";
                }
                return "Report crashes to the app developer and ensure app is up to date.";
            case "anr":
                return "The app is freezing. Force stop and restart it, or reinstall if issue persists.";
            case "thermal":
                return "Reduce usage to allow device to cool down. Avoid using while charging.";
            default:
                return "Monitor the app and report issues to the developer.";
        }
    }
    
    /**
     * Get engineer/developer recommendation.
     */
    private String getEngineerRecommendation(String type, RegressionEvent event) {
        switch (type) {
            case "launch_time":
                return "Profile app startup with Android Studio. Check for main thread blocking, heavy initialization, and deferred work opportunities.";
            case "jank":
                return "Use Frame Metrics API to identify slow frames. Check for overdraw, layout complexity, and main thread work during rendering.";
            case "memory":
                return "Profile memory with Android Profiler. Check for leaks, large bitmap allocations, and object retention.";
            case "crash":
                return "Analyze crash logs and stack traces. Review recent code changes and test edge cases.";
            case "anr":
                return "Review ANR traces for main thread blocking. Move I/O and network calls off main thread. Check for deadlocks.";
            case "thermal":
                return "Profile CPU usage and optimize hot paths. Consider work deferral and efficient algorithms.";
            default:
                return "Profile and analyze performance metrics to identify root cause.";
        }
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
