package com.example.hexnil.manager;

import android.util.Log;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AppBatteryUsage;
import com.example.hexnil.model.AppDrainRank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes per-app drain ranking for multiple time windows and persists results.
 * 
 * Supports standard windows: 1h, 6h, 24h, 7d, 30d
 * Each window generates a complete ranking of all apps by drain contribution.
 */
public class AppDrainRanker {

    private static final String TAG = "AppDrainRanker";
    private static final int TOP_N = 20; // Increased to capture more apps
    
    // Standard time windows in milliseconds
    private static final long HOUR_MS = 60 * 60 * 1000L;
    private static final long DAY_MS = 24 * HOUR_MS;
    
    public static final String WINDOW_1H = "1h";
    public static final String WINDOW_6H = "6h";
    public static final String WINDOW_24H = "24h";
    public static final String WINDOW_7D = "7d";
    public static final String WINDOW_30D = "30d";
    
    private static final Map<String, Long> WINDOW_DURATIONS = new HashMap<>();
    static {
        WINDOW_DURATIONS.put(WINDOW_1H, HOUR_MS);
        WINDOW_DURATIONS.put(WINDOW_6H, 6 * HOUR_MS);
        WINDOW_DURATIONS.put(WINDOW_24H, DAY_MS);
        WINDOW_DURATIONS.put(WINDOW_7D, 7 * DAY_MS);
        WINDOW_DURATIONS.put(WINDOW_30D, 30 * DAY_MS);
    }
    
    private final HexNilDatabase database;

    public AppDrainRanker(HexNilDatabase database) {
        this.database = database;
    }

    /**
     * Generate rankings for all standard time windows.
     * Call this periodically (e.g., hourly) to keep rankings up to date.
     */
    public void generateAllWindowRankings() {
        long now = System.currentTimeMillis();
        Log.d(TAG, "Generating drain rankings for all standard windows");
        
        for (String windowLabel : new String[]{WINDOW_1H, WINDOW_6H, WINDOW_24H, WINDOW_7D}) {
            try {
                Long duration = WINDOW_DURATIONS.get(windowLabel);
                if (duration != null) {
                    long windowStart = now - duration;
                    rank(windowLabel, windowStart, now);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error generating " + windowLabel + " rankings", e);
            }
        }
    }

    /**
     * Generate rankings for a specific standard window label.
     * @param windowLabel One of: "1h", "6h", "24h", "7d", "30d"
     */
    public List<AppDrainRank> rankForWindow(String windowLabel) {
        Long duration = WINDOW_DURATIONS.get(windowLabel);
        if (duration == null) {
            Log.w(TAG, "Unknown window label: " + windowLabel);
            return new ArrayList<>();
        }
        
        long now = System.currentTimeMillis();
        long windowStart = now - duration;
        return rank(windowLabel, windowStart, now);
    }

    /**
     * Core ranking method - ranks apps by drain for a specific time window.
     * 
     * @param label Human-readable window label
     * @param start Window start timestamp (UTC ms)
     * @param end Window end timestamp (UTC ms)
     * @return List of ranked apps, sorted by drainScore descending
     */
    public List<AppDrainRank> rank(String label, long start, long end) {
        List<AppBatteryUsage> usageList = database.appBatteryUsageDao().getUsageSince(start);
        
        // Aggregate usage by package (multiple records may exist for same app)
        Map<String, AggregatedUsage> aggregatedMap = new HashMap<>();
        
        for (AppBatteryUsage usage : usageList) {
            if (usage.timestamp < start || usage.timestamp > end) continue;
            
            AggregatedUsage agg = aggregatedMap.get(usage.packageName);
            if (agg == null) {
                agg = new AggregatedUsage(usage.packageName);
                aggregatedMap.put(usage.packageName, agg);
            }
            agg.accumulate(usage);
        }
        
        // Convert to ranks and calculate scores
        List<AppDrainRank> ranks = new ArrayList<>();
        float totalScore = 0f;
        
        for (AggregatedUsage agg : aggregatedMap.values()) {
            float score = computeScore(agg);
            totalScore += score;
            
            AppDrainRank rank = new AppDrainRank();
            rank.windowLabel = label;
            rank.windowStart = start;
            rank.windowEnd = end;
            rank.packageName = agg.packageName;
            rank.drainScore = score;
            
            // Group C: Usage Context - use aggregated times
            rank.screenTimeMs = agg.totalForegroundTime; // Screen time ≈ foreground time
            rank.foregroundTimeMs = agg.totalForegroundTime;
            rank.backgroundTimeMs = agg.totalBackgroundTime;
            
            ranks.add(rank);
        }

        // Sort by drainScore descending (highest drainer = rank 1)
        Collections.sort(ranks, Comparator.comparingDouble((AppDrainRank r) -> -r.drainScore));
        
        // Assign ranks and calculate contribution percentages
        for (int i = 0; i < ranks.size(); i++) {
            AppDrainRank r = ranks.get(i);
            r.rank = i + 1;
            // Group B: contributionPct - should add up to ~100% across all apps
            r.contributionPct = totalScore > 0 ? (r.drainScore / totalScore) * 100f : 0f;
        }

        // Store top N in database
        List<AppDrainRank> topN = ranks.subList(0, Math.min(TOP_N, ranks.size()));
        if (!topN.isEmpty()) {
            database.appDrainRankDao().insertAll(topN);
            Log.d(TAG, "Stored " + topN.size() + " drain rankings for window: " + label + 
                       " (totalScore=" + totalScore + ", apps=" + ranks.size() + ")");
        }
        
        return topN;
    }

    /**
     * Compute drain score from aggregated usage data.
     * Higher score = more battery drain.
     */
    private float computeScore(AggregatedUsage agg) {
        // Base drain from battery percentage used
        float baseDrain = agg.totalBatteryUsed;
        
        // Weight factors for different activity types
        float screenWeight = 1.0f;      // Active screen time is primary drain
        float bgWeight = 0.5f;          // Background activity contributes less visibly
        float wakelockWeight = 0.4f;    // Wakelocks prevent sleep
        float networkWeight = 0.1f;     // Network activity per MB
        
        // Convert ms to minutes for readable weights
        float fgMinutes = agg.totalForegroundTime / 60000f;
        float bgMinutes = agg.totalBackgroundTime / 60000f;
        float wakelockMinutes = agg.totalWakelockTime / 60000f;
        float networkMB = agg.totalNetworkBytes / (1024f * 1024f);
        
        return baseDrain
                + fgMinutes * screenWeight
                + bgMinutes * bgWeight
                + wakelockMinutes * wakelockWeight
                + networkMB * networkWeight;
    }

    /**
     * Helper class to aggregate multiple AppBatteryUsage records for the same package
     */
    private static class AggregatedUsage {
        final String packageName;
        long totalForegroundTime = 0;
        long totalBackgroundTime = 0;
        float totalBatteryUsed = 0;
        long totalWakelockTime = 0;
        long totalNetworkBytes = 0;
        int recordCount = 0;
        
        AggregatedUsage(String packageName) {
            this.packageName = packageName;
        }
        
        void accumulate(AppBatteryUsage usage) {
            // For time-based metrics, take the maximum (they represent cumulative time)
            // This handles overlapping measurement periods
            totalForegroundTime = Math.max(totalForegroundTime, usage.foregroundTime);
            totalBackgroundTime = Math.max(totalBackgroundTime, usage.backgroundTime);
            totalWakelockTime = Math.max(totalWakelockTime, usage.wakelockTime);
            
            // For network bytes, take the maximum as well (cumulative)
            totalNetworkBytes = Math.max(totalNetworkBytes, usage.networkBytes);
            
            // For battery percentage, sum up contributions (additive across periods)
            totalBatteryUsed += usage.batteryPercentUsed;
            
            recordCount++;
        }
    }
}
