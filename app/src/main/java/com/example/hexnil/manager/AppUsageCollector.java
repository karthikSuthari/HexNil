package com.example.hexnil.manager;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.text.format.DateUtils;

import com.example.hexnil.model.AppBatteryUsage;

import java.util.ArrayList;
import java.util.List;

/**
 * Best-effort app usage collector leveraging UsageStats. Network bytes and wakelocks are placeholders.
 */
public class AppUsageCollector {

    private final Context context;
    private final UsageStatsManager usageStatsManager;

    public AppUsageCollector(Context context) {
        this.context = context.getApplicationContext();
        this.usageStatsManager = (UsageStatsManager) this.context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    /**
     * Collect usage for the last windowMs (default 15 minutes if not provided by caller).
     */
    public List<AppBatteryUsage> collect(long windowMs) {
        long now = System.currentTimeMillis();
        long start = now - (windowMs > 0 ? windowMs : 15 * DateUtils.MINUTE_IN_MILLIS);
        List<AppBatteryUsage> result = new ArrayList<>();
        if (usageStatsManager == null) return result;

        List<UsageStats> stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now);
        if (stats == null) return result;

        for (UsageStats s : stats) {
            if (s.getTotalTimeInForeground() <= 0) continue;
            AppBatteryUsage usage = new AppBatteryUsage();
            usage.timestamp = now;
            usage.periodStart = start;
            usage.periodEnd = now;
            usage.packageName = s.getPackageName();
            usage.foregroundTime = s.getTotalTimeInForeground();
            usage.backgroundTime = 0; // UsageStats does not expose background; placeholder.
            // Placeholders until NetworkStatsManager / wakelock data is wired
            usage.networkBytes = 0;
            usage.wakelockTime = 0;
            usage.partialWakelockTime = 0;
            usage.wakelockCount = 0;
            result.add(usage);
        }
        return result;
    }
}
