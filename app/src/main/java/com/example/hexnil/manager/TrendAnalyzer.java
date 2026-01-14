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

        // Get values with null safety
        Float healthWeekObj = database.batteryHealthDao().getAverageHealth(weekAgo);
        Float healthDayObj = database.batteryHealthDao().getAverageHealth(dayAgo);
        Float screenOnWeekObj = database.drainEventDao().getAverageScreenOnDrain(weekAgo);
        Float screenOnDayObj = database.drainEventDao().getAverageScreenOnDrain(dayAgo);
        Float tempWeekObj = database.thermalEventDao().getAveragePeakTemperature(weekAgo);
        Float tempDayObj = database.thermalEventDao().getAveragePeakTemperature(dayAgo);

        // Convert to primitives with defaults
        float healthWeek = healthWeekObj != null ? healthWeekObj : 100f;
        float healthDay = healthDayObj != null ? healthDayObj : 100f;
        float screenOnWeek = screenOnWeekObj != null ? screenOnWeekObj : 0f;
        float screenOnDay = screenOnDayObj != null ? screenOnDayObj : 0f;
        float tempWeek = tempWeekObj != null ? tempWeekObj : 25f;
        float tempDay = tempDayObj != null ? tempDayObj : 25f;

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
