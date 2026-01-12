package com.example.hexnil.manager;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.AppDrainRank;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.RollupMetrics;
import com.example.hexnil.model.ThermalHeatBucket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Supplies persisted datasets for charts without recomputation in the UI layer.
 */
public class ChartDataProvider {

    public static class ChartData {
        public RollupMetrics daily;
        public RollupMetrics weekly;
        public List<ThermalHeatBucket> heatBuckets = Collections.emptyList();
        public List<AppDrainRank> topApps = Collections.emptyList();
        public ChargingSession latestCharging;
    }

    private final HexNilDatabase db;
    private final MetricsRollupManager rollups;
    private final RiskEvaluator riskEvaluator;

    public ChartDataProvider(HexNilDatabase db) {
        this.db = db;
        this.rollups = new MetricsRollupManager(db);
        this.riskEvaluator = new RiskEvaluator();
    }

    public ChartData load(long now) {
        ChartData data = new ChartData();
        long dayStart = now - 24 * 60 * 60 * 1000L;
        long weekStart = now - 7L * 24 * 60 * 60 * 1000L;

        data.daily = riskEvaluator.evaluate(rollups.rollup("24h", dayStart, now));
        data.weekly = riskEvaluator.evaluate(rollups.rollup("7d", weekStart, now));
        data.heatBuckets = db.thermalHeatBucketDao().getBucketsSince(weekStart);
        data.topApps = db.appDrainRankDao().getByWindow("24h");
        data.latestCharging = db.chargingSessionDao().getLatest();
        return data;
    }

    public String summarizeRollups(RollupMetrics daily, RollupMetrics weekly) {
        if (daily == null || weekly == null) return "No rollup data";
        return "24h so=" + daily.screenOnDrain + "%/h idle=" + daily.idleDrain + "%/h risk=" + daily.riskScore +
                " | 7d so=" + weekly.screenOnDrain + "%/h idle=" + weekly.idleDrain + "%/h risk=" + weekly.riskScore;
    }

    public String summarizeThermals(List<ThermalHeatBucket> buckets) {
        if (buckets == null || buckets.isEmpty()) return "No thermal buckets";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (ThermalHeatBucket b : buckets) {
            if (shown++ >= 6) break;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(b.tempBucket).append("@h").append(b.hourBlock)
              .append(" cnt=").append(b.sampleCount)
              .append(" max=").append(b.maxTemp);
        }
        return sb.toString();
    }

    public String summarizeApps(List<AppDrainRank> apps) {
        if (apps == null || apps.isEmpty()) return "No app ranks";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (AppDrainRank a : apps) {
            if (shown++ >= 5) break;
            if (sb.length() > 0) sb.append(" | ");
            sb.append('#').append(a.rank).append(' ').append(a.packageName)
              .append(" (").append(String.format("%.1f", a.contributionPct)).append("%)");
        }
        return sb.toString();
    }

    public String summarizeCharging(ChargingSession session) {
        if (session == null) return "No charging sessions";
        int voltagePoints = countPoints(session.voltageCurveJson);
        int currentPoints = countPoints(session.currentCurveJson);
        return "Curve pts v=" + voltagePoints + " c=" + currentPoints +
                " maxT=" + session.maxTemperature + "C stability=" + session.curveStabilityScore;
    }

    private int countPoints(String jsonArray) {
        if (jsonArray == null || jsonArray.length() < 2) return 0;
        int count = 0;
        for (int i = 0; i < jsonArray.length(); i++) {
            if (jsonArray.charAt(i) == '{') count++;
        }
        return count;
    }
}
