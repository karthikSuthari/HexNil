package com.example.hexnil.manager;

import com.example.hexnil.database.HexNilDatabase;

/**
 * Derives coarse trends for health and drain to inform AI analysis.
 */
public class TrendAnalyzer {

    public static class Trend {
        public boolean isDegrading;
        public float healthTrend;
        public float drainTrend;
        public float temperatureTrend;
        public String trendLabel;
    }

    private static final long DAY_MS = 24 * 60 * 60 * 1000L;

    private final HexNilDatabase database;

    public TrendAnalyzer(HexNilDatabase database) {
        this.database = database;
    }

    public Trend analyze(long now) {
        long weekAgo = now - 7 * DAY_MS;
        long dayAgo = now - DAY_MS;

        float healthWeek = database.batteryHealthDao().getAverageHealth(weekAgo);
        float healthDay = database.batteryHealthDao().getAverageHealth(dayAgo);
        float screenOnWeek = database.drainEventDao().getAverageScreenOnDrain(weekAgo);
        float screenOnDay = database.drainEventDao().getAverageScreenOnDrain(dayAgo);
        float tempWeek = database.thermalEventDao().getAveragePeakTemperature(weekAgo);
        float tempDay = database.thermalEventDao().getAveragePeakTemperature(dayAgo);

        Trend trend = new Trend();
        trend.healthTrend = healthDay - healthWeek;
        trend.drainTrend = screenOnDay - screenOnWeek;
        trend.temperatureTrend = tempDay - tempWeek;

        trend.isDegrading = trend.healthTrend < -1.5f || trend.drainTrend > 2.0f || trend.temperatureTrend > 1.5f;
        trend.trendLabel = label(trend);
        return trend;
    }

    private String label(Trend t) {
        if (t == null) return "unknown";
        if (t.isDegrading) return "degrading";
        if (t.healthTrend > 0.5f && t.drainTrend < -1f) return "improving";
        return "stable";
    }
}
