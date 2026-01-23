package com.example.hexnil.manager;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.DailyAggregate;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Performance Aggregator
 * 
 * Aggregates raw performance metrics into daily summaries for:
 * - Trend analysis over time
 * - 7-day and 30-day baseline calculation
 * - Regression detection
 * - Device health scoring
 * 
 * Should be scheduled to run once daily (e.g., via WorkManager)
 */
public class PerformanceAggregator {

        private static final String TAG = "PerformanceAggregator";

        private final Context context;
        private final HexNilDatabase database;
        private final Executor executor;

        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        public PerformanceAggregator(Context context, HexNilDatabase database) {
                this.context = context.getApplicationContext();
                this.database = database;
                this.executor = Executors.newSingleThreadExecutor();
        }

        /**
         * Run daily aggregation for the previous day.
         * Call this once per day (typically in the morning via WorkManager).
         */
        public void runDailyAggregation() {
                executor.execute(this::runDailyAggregationSync);
        }

        /**
         * Synchronous version of daily aggregation.
         */
        public void runDailyAggregationSync() {
                try {
                        // Get yesterday's date
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.DAY_OF_YEAR, -1);
                        String yesterdayDate = DATE_FORMAT.format(cal.getTime());

                        Log.i(TAG, "Running daily aggregation for: " + yesterdayDate);

                        // Calculate time range for yesterday
                        cal.set(Calendar.HOUR_OF_DAY, 0);
                        cal.set(Calendar.MINUTE, 0);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        long dayStart = cal.getTimeInMillis();

                        cal.add(Calendar.DAY_OF_YEAR, 1);
                        long dayEnd = cal.getTimeInMillis();

                        // Create device-level aggregate
                        DailyAggregate deviceAggregate = createDeviceLevelAggregate(
                                        yesterdayDate, dayStart, dayEnd);
                        if (deviceAggregate != null) {
                                // Calculate baselines
                                calculateBaselines(deviceAggregate);
                                // Calculate health score
                                deviceAggregate.calculateHealthScore();

                                database.dailyAggregateDao().insertOrReplace(deviceAggregate);
                                Log.i(TAG, "Device aggregate saved: " + deviceAggregate);
                        }

                        // Create per-app aggregates for top apps
                        createPerAppAggregates(yesterdayDate, dayStart, dayEnd);

                        Log.i(TAG, "Daily aggregation complete for: " + yesterdayDate);
                } catch (Exception e) {
                        Log.e(TAG, "Error in daily aggregation", e);
                }
        }

        /**
         * Create device-level aggregate for a day.
         */
        private DailyAggregate createDeviceLevelAggregate(String date, long dayStart, long dayEnd) {
                try {
                        DailyAggregate agg = new DailyAggregate(date, null);
                        agg.systemVersion = Build.VERSION.RELEASE;

                        // Memory aggregates
                        agg.avgMemoryUsedPercent = database.memorySnapshotDao()
                                        .getAverageMemoryUsageInRange(dayStart, dayEnd);
                        agg.maxMemoryUsedPercent = database.memorySnapshotDao()
                                        .getMaxMemoryUsageInRange(dayStart, dayEnd);
                        agg.lowMemoryEventCount = database.memorySnapshotDao()
                                        .getLowMemoryEventCountInRange(dayStart, dayEnd);
                        agg.memorySampleCount = database.memorySnapshotDao()
                                        .getSampleCountSince(dayStart);

                        // Launch aggregates
                        agg.launchCount = database.appLaunchMetricDao()
                                        .getLaunchCountSince(dayStart);
                        agg.coldLaunchCount = database.appLaunchMetricDao()
                                        .getColdLaunchCountSince(dayStart);
                        agg.avgColdLaunchMs = database.appLaunchMetricDao()
                                        .getAverageColdLaunchSince(dayStart);
                        agg.avgWarmLaunchMs = database.appLaunchMetricDao()
                                        .getAverageWarmLaunchSince(dayStart);
                        agg.avgHotLaunchMs = database.appLaunchMetricDao()
                                        .getAverageHotLaunchSince(dayStart);
                        agg.maxLaunchMs = database.appLaunchMetricDao()
                                        .getMaxLaunchTimeSince(dayStart);
                        agg.slowLaunchCount = database.appLaunchMetricDao()
                                        .getSlowLaunchCount(dayStart);

                        // Frame metrics aggregates
                        agg.totalFrames = database.frameMetricDao()
                                        .getTotalFramesInRange(dayStart, dayEnd);
                        agg.totalSlowFrames = database.frameMetricDao()
                                        .getTotalSlowFramesInRange(dayStart, dayEnd);
                        agg.totalFrozenFrames = database.frameMetricDao()
                                        .getTotalFrozenFramesInRange(dayStart, dayEnd);
                        agg.avgJankPercent = database.frameMetricDao()
                                        .getAverageJankSince(dayStart);
                        agg.maxJankPercent = database.frameMetricDao()
                                        .getMaxJankSince(dayStart);
                        agg.avgFps = database.frameMetricDao()
                                        .getAverageFpsSince(dayStart);
                        agg.frameSessionCount = database.frameMetricDao()
                                        .getSessionCountInRange(dayStart, dayEnd);

                        // Crash/ANR aggregates
                        agg.crashCount = database.crashEventDao()
                                        .getCountInRange(dayStart, dayEnd);
                        agg.uniqueCrashTypes = database.crashEventDao()
                                        .getUniqueCrashTypeCount(dayStart);
                        agg.anrCount = database.anrEventDao()
                                        .getCountInRange(dayStart, dayEnd);
                        agg.uniqueAnrTypes = database.anrEventDao()
                                        .getUniqueAnrTypeCount(dayStart);

                        // Check for new crash/ANR types
                        agg.hasNewCrashType = !database.crashEventDao()
                                        .getNewCrashTypes(dayStart).isEmpty();
                        agg.hasNewAnrType = !database.anrEventDao()
                                        .getNewAnrTypes(dayStart).isEmpty();

                        agg.updatedAt = System.currentTimeMillis();

                        return agg;
                } catch (Exception e) {
                        Log.e(TAG, "Error creating device aggregate", e);
                        return null;
                }
        }

        /**
         * Create per-app aggregates for packages that have activity.
         */
        private void createPerAppAggregates(String date, long dayStart, long dayEnd) {
                try {
                        // Get distinct packages with launches
                        List<String> packages = database.appLaunchMetricDao()
                                        .getDistinctPackages(dayStart);

                        if (packages == null || packages.isEmpty()) {
                                Log.d(TAG, "No per-app data to aggregate");
                                return;
                        }

                        int count = 0;
                        for (String packageName : packages) {
                                if (packageName == null)
                                        continue;

                                DailyAggregate appAgg = createAppAggregate(
                                                date, packageName, dayStart, dayEnd);
                                if (appAgg != null) {
                                        calculateBaselines(appAgg);
                                        appAgg.calculateHealthScore();
                                        database.dailyAggregateDao().insertOrReplace(appAgg);
                                        count++;
                                }
                        }

                        Log.d(TAG, "Created " + count + " per-app aggregates");
                } catch (Exception e) {
                        Log.e(TAG, "Error creating per-app aggregates", e);
                }
        }

        /**
         * Create aggregate for a specific app.
         */
        private DailyAggregate createAppAggregate(String date, String packageName,
                        long dayStart, long dayEnd) {
                try {
                        DailyAggregate agg = new DailyAggregate(date, packageName);

                        // Launch metrics for this app
                        agg.launchCount = database.appLaunchMetricDao()
                                        .getLaunchCountByPackage(packageName, dayStart);
                        agg.coldLaunchCount = database.appLaunchMetricDao()
                                        .getColdLaunchCountByPackageInRange(packageName, dayStart, dayEnd);
                        agg.avgColdLaunchMs = database.appLaunchMetricDao()
                                        .getAverageColdLaunchByPackageInRange(packageName, dayStart, dayEnd);
                        agg.maxLaunchMs = database.appLaunchMetricDao()
                                        .getMaxLaunchTimeByPackage(packageName, dayStart);

                        // Frame metrics for this app
                        agg.avgJankPercent = database.frameMetricDao()
                                        .getAverageJankByPackageInRange(packageName, dayStart, dayEnd);

                        // Crash/ANR for this app
                        agg.crashCount = database.crashEventDao()
                                        .getCountByPackageInRange(packageName, dayStart, dayEnd);
                        agg.uniqueCrashTypes = database.crashEventDao()
                                        .getUniqueCrashTypeCountByPackage(packageName, dayStart);
                        agg.anrCount = database.anrEventDao()
                                        .getCountByPackageInRange(packageName, dayStart, dayEnd);

                        agg.systemVersion = Build.VERSION.RELEASE;
                        agg.updatedAt = System.currentTimeMillis();

                        return agg;
                } catch (Exception e) {
                        Log.e(TAG, "Error creating app aggregate for " + packageName, e);
                        return null;
                }
        }

        /**
         * Calculate 7-day and 30-day rolling baselines.
         */
        private void calculateBaselines(DailyAggregate agg) {
                try {
                        String currentDate = agg.date;

                        // Calculate date ranges
                        String date7DaysAgo = getDateNDaysAgo(currentDate, 7);
                        String date30DaysAgo = getDateNDaysAgo(currentDate, 30);

                        if (agg.isDeviceLevel()) {
                                // Device-level baselines
                                agg.baseline7DayLaunchMs = database.dailyAggregateDao()
                                                .getDeviceAvgLaunchInRange(date7DaysAgo, currentDate);
                                agg.baseline7DayTemp = database.dailyAggregateDao()
                                                .get7DayAvgTemp(date7DaysAgo, currentDate);

                                agg.baseline30DayLaunchMs = database.dailyAggregateDao()
                                                .getDeviceAvgLaunchInRange(date30DaysAgo, currentDate);
                                agg.baseline30DayTemp = database.dailyAggregateDao()
                                                .get30DayAvgTemp(date30DaysAgo, currentDate);
                        } else {
                                // App-level baselines
                                String pkg = agg.packageName;

                                agg.baseline7DayLaunchMs = database.dailyAggregateDao()
                                                .get7DayAvgLaunchMs(pkg, date7DaysAgo, currentDate);
                                agg.baseline7DayJankPercent = database.dailyAggregateDao()
                                                .get7DayAvgJankPercent(pkg, date7DaysAgo, currentDate);
                                agg.baseline7DayCrashCount = database.dailyAggregateDao()
                                                .get7DayAvgCrashCount(pkg, date7DaysAgo, currentDate);

                                agg.baseline30DayLaunchMs = database.dailyAggregateDao()
                                                .get30DayAvgLaunchMs(pkg, date30DaysAgo, currentDate);
                                agg.baseline30DayJankPercent = database.dailyAggregateDao()
                                                .get30DayAvgJankPercent(pkg, date30DaysAgo, currentDate);
                        }
                } catch (Exception e) {
                        Log.e(TAG, "Error calculating baselines", e);
                }
        }

        /**
         * Get date string N days ago from a given date.
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

        /**
         * Get the date string for today.
         */
        public String getTodayDate() {
                return DATE_FORMAT.format(new Date());
        }

        /**
         * Get aggregate for a specific date.
         */
        public DailyAggregate getDeviceAggregate(String date) {
                return database.dailyAggregateDao().getDeviceLevelByDate(date);
        }

        /**
         * Get recent device-level aggregates.
         */
        public List<DailyAggregate> getRecentDeviceAggregates(int days) {
                return database.dailyAggregateDao().getRecentDeviceLevel(days);
        }

        /**
         * Get average health score for a date range.
         */
        public float getAverageHealthScore(String startDate, String endDate) {
                return database.dailyAggregateDao().getAverageHealthScore(startDate, endDate);
        }

        /**
         * Get days with failing health grades.
         */
        public List<DailyAggregate> getFailingDays() {
                return database.dailyAggregateDao().getFailingDays();
        }

        /**
         * Clean up old aggregates.
         */
        public int cleanupOldAggregates(String beforeDate) {
                return database.dailyAggregateDao().deleteOlderThan(beforeDate);
        }
}
