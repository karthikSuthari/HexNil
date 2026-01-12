package com.example.hexnil.manager;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AppBatteryUsage;
import com.example.hexnil.model.AppDrainRank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Computes per-app drain ranking for a window and persists top-N.
 */
public class AppDrainRanker {

    private static final int TOP_N = 5;
    private final HexNilDatabase database;

    public AppDrainRanker(HexNilDatabase database) {
        this.database = database;
    }

    public List<AppDrainRank> rank(String label, long start, long end) {
        List<AppBatteryUsage> usageList = database.appBatteryUsageDao().getUsageSince(start);
        List<AppDrainRank> ranks = new ArrayList<>();
        float totalScore = 0f;

        for (AppBatteryUsage usage : usageList) {
            if (usage.timestamp < start || usage.timestamp > end) continue;
            float score = computeScore(usage);
            totalScore += score;
            AppDrainRank rank = new AppDrainRank();
            rank.windowLabel = label;
            rank.windowStart = start;
            rank.windowEnd = end;
            rank.packageName = usage.packageName;
            rank.drainScore = score;
            rank.screenTimeMs = usage.foregroundTime;
            rank.foregroundTimeMs = usage.foregroundTime;
            rank.backgroundTimeMs = usage.backgroundTime;
            ranks.add(rank);
        }

        Collections.sort(ranks, Comparator.comparingDouble((AppDrainRank r) -> -r.drainScore));
        for (int i = 0; i < ranks.size(); i++) {
            AppDrainRank r = ranks.get(i);
            r.rank = i + 1;
            r.contributionPct = totalScore > 0 ? (r.drainScore / totalScore) * 100f : 0f;
        }

        List<AppDrainRank> topN = ranks.subList(0, Math.min(TOP_N, ranks.size()));
        database.appDrainRankDao().insertAll(topN);
        return topN;
    }

    private float computeScore(AppBatteryUsage usage) {
        float screenWeight = 1.0f;
        float fgWeight = 0.7f;
        float bgWeight = 0.4f;
        float wakelockWeight = 0.3f;
        float drain = usage.batteryPercentUsed;
        return drain
                + (usage.foregroundTime / 60000f) * screenWeight
                + (usage.foregroundTime / 60000f) * fgWeight
                + (usage.backgroundTime / 60000f) * bgWeight
                + (usage.partialWakelockTime / 60000f) * wakelockWeight;
    }
}
