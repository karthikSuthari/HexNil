package com.example.hexnil.manager;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.DrainEvent;
import com.example.hexnil.model.IdleDrainSession;
import com.example.hexnil.model.ThermalEvent;
import com.example.hexnil.model.UpdateComparison;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes pre/post update comparisons using 7-day windows and deterministic verdict rules.
 */
public class UpdateComparisonManager {

    private static final long WINDOW_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    static final float REGRESSION_THRESHOLD_PCT = 10f;
    static final float IMPROVEMENT_THRESHOLD_PCT = -5f;

    private final HexNilDatabase database;

    public UpdateComparisonManager(HexNilDatabase database) {
        this.database = database;
    }

    public UpdateComparison computeAndPersist(String previousVersion, String currentVersion, long updateTimestamp) {
        long now = System.currentTimeMillis();
        long preStart = updateTimestamp - WINDOW_MS;
        long preEnd = updateTimestamp;
        long postStart = updateTimestamp;
        long postEnd = Math.min(now, updateTimestamp + WINDOW_MS);

        WindowMetrics pre = computeWindow(preStart, preEnd);
        WindowMetrics post = computeWindow(postStart, postEnd);

        UpdateComparison comparison = new UpdateComparison();
        comparison.previousVersion = previousVersion;
        comparison.currentVersion = currentVersion;
        comparison.updateTimestamp = updateTimestamp;
        comparison.updateType = inferUpdateType(previousVersion, currentVersion);

        comparison.preAvgDrainScreenOn = pre.screenOnDrain;
        comparison.preAvgDrainScreenOff = pre.screenOffDrain;
        comparison.preDrainRate = pre.overallDrain;
        comparison.preAvgChargingTime = pre.avgChargeMinutes;
        comparison.preAvgTemperature = pre.avgTemperature;
        comparison.preMaxTemperature = pre.maxTemperature;
        comparison.preIdleDrain = pre.idleDrain;
        comparison.preBatteryHealth = pre.health;

        comparison.postAvgDrainScreenOn = post.screenOnDrain;
        comparison.postAvgDrainScreenOff = post.screenOffDrain;
        comparison.postDrainRate = post.overallDrain;
        comparison.postAvgChargingTime = post.avgChargeMinutes;
        comparison.postAvgTemperature = post.avgTemperature;
        comparison.postMaxTemperature = post.maxTemperature;
        comparison.postIdleDrain = post.idleDrain;
        comparison.postBatteryHealth = post.health;

        comparison.calculateDeltas();

        List<DailyPoint> preSeries = buildDailySeries(preStart, preEnd);
        List<DailyPoint> postSeries = buildDailySeries(postStart, postEnd);
        comparison.preSeriesJson = toJson(preSeries);
        comparison.postSeriesJson = toJson(postSeries);

        Verdict verdict = scoreVerdict(comparison, pre, post, preSeries, postSeries);
        comparison.overallImpact = verdict.label;
        comparison.recommendation = verdict.recommendation;
        comparison.impactScore = verdict.score;
        comparison.confidenceLevel = verdict.confidence;

        comparison.analysisNotes = buildNotes(comparison, pre, post);

        database.miscDao().insertUpdateComparison(comparison);
        return comparison;
    }

    private WindowMetrics computeWindow(long start, long end) {
        WindowMetrics metrics = new WindowMetrics();

        List<DrainEvent> drains = database.drainEventDao().getEventsSince(start);
        for (DrainEvent d : drains) {
            if (d.startTime < start || d.startTime >= end) continue;
            metrics.overallDrainSum += d.drainRatePerHour;
            metrics.overallCount++;
            if (d.screenOn) {
                metrics.screenOnDrainSum += d.drainRatePerHour;
                metrics.screenOnCount++;
            } else {
                metrics.screenOffDrainSum += d.drainRatePerHour;
                metrics.screenOffCount++;
            }
        }

        List<IdleDrainSession> idleSessions = database.idleDrainSessionDao().getSessionsSince(start);
        for (IdleDrainSession s : idleSessions) {
            if (s.startTime < start || s.startTime >= end) continue;
            metrics.idleDrainSum += s.drainPerHour;
            metrics.idleCount++;
        }

        List<ChargingSession> sessions = database.chargingSessionDao().getSessionsSince(start);
        for (ChargingSession cs : sessions) {
            if (cs.startTime < start || cs.startTime >= end || cs.endTime == 0) continue;
            metrics.chargeMinutesSum += cs.getDurationMinutes();
            metrics.chargeCount++;
        }

        List<ThermalEvent> thermalEvents = database.thermalEventDao().getEventsSince(start);
        for (ThermalEvent te : thermalEvents) {
            if (te.timestamp < start || te.timestamp >= end) continue;
            metrics.temperatureSum += te.peakTemperature;
            metrics.temperatureCount++;
            metrics.maxTemperature = Math.max(metrics.maxTemperature, te.peakTemperature);
        }

        List<BatteryHealth> healthHistory = database.batteryHealthDao().getHealthHistorySince(start);
        for (BatteryHealth h : healthHistory) {
            if (h.timestamp < start || h.timestamp >= end) continue;
            metrics.healthSum += h.healthPercentage;
            metrics.healthCount++;
        }

        metrics.screenOnDrain = metrics.screenOnCount > 0 ? metrics.screenOnDrainSum / metrics.screenOnCount : 0f;
        metrics.screenOffDrain = metrics.screenOffCount > 0 ? metrics.screenOffDrainSum / metrics.screenOffCount : 0f;
        metrics.overallDrain = metrics.overallCount > 0 ? metrics.overallDrainSum / metrics.overallCount : 0f;
        metrics.idleDrain = metrics.idleCount > 0 ? metrics.idleDrainSum / metrics.idleCount : 0f;
        metrics.avgChargeMinutes = metrics.chargeCount > 0 ? metrics.chargeMinutesSum / metrics.chargeCount : 0f;
        metrics.avgTemperature = metrics.temperatureCount > 0 ? metrics.temperatureSum / metrics.temperatureCount : 0f;
        metrics.health = metrics.healthCount > 0 ? metrics.healthSum / metrics.healthCount : 0f;

        metrics.samples = metrics.screenOnCount + metrics.screenOffCount + metrics.idleCount
                + metrics.chargeCount + metrics.temperatureCount + metrics.healthCount;
        return metrics;
    }

    private Verdict scoreVerdict(UpdateComparison comparison, WindowMetrics pre, WindowMetrics post,
                                 List<DailyPoint> preSeries, List<DailyPoint> postSeries) {
        Verdict verdict = new Verdict();

        float score = 0f;
        score -= comparison.drainRateChange * 2f;
        score -= comparison.idleDrainChange * 1.5f;
        score -= comparison.chargingTimeChange * 1f;
        score -= comparison.thermalPeakChange * 1f;
        verdict.score = score;

        boolean degraded = comparison.hasDrainRegression || comparison.hasChargingRegression
                || comparison.hasThermalRegression || comparison.hasIdleRegression
                || comparison.drainRateDelta > 1.0f || comparison.idleDrainDelta > 0.5f;

        int improveSignals = 0;
        if (comparison.drainRateChange < IMPROVEMENT_THRESHOLD_PCT) improveSignals++;
        if (comparison.idleDrainChange < IMPROVEMENT_THRESHOLD_PCT) improveSignals++;
        if (comparison.chargingTimeChange < -5f) improveSignals++;
        if (comparison.thermalPeakChange < -5f) improveSignals++;
        boolean improved = improveSignals >= 2;

        if (degraded) {
            verdict.label = "degraded";
            verdict.recommendation = "investigate";
        } else if (improved) {
            verdict.label = "improved";
            verdict.recommendation = "approve";
        } else {
            verdict.label = "neutral";
            verdict.recommendation = "monitor";
        }

        verdict.confidence = computeConfidence(pre, post, preSeries, postSeries);
        return verdict;
    }

    private float computeConfidence(WindowMetrics pre, WindowMetrics post,
                                    List<DailyPoint> preSeries, List<DailyPoint> postSeries) {
        int totalSamples = pre.samples + post.samples;
        int coveredDays = countDaysWithSamples(preSeries) + countDaysWithSamples(postSeries);
        int expectedDays = preSeries.size() + postSeries.size();
        float coverageScore = expectedDays == 0 ? 0 : (coveredDays * 100f / expectedDays);
        float sampleScore = Math.min(60f, totalSamples * 2f);
        float recencyBonus = postSeries.isEmpty() ? 0f : 10f;
        return Math.min(100f, 20f + sampleScore + coverageScore * 0.2f + recencyBonus);
    }

    private int countDaysWithSamples(List<DailyPoint> series) {
        int count = 0;
        for (DailyPoint p : series) {
            if (p.samples > 0) count++;
        }
        return count;
    }

    private List<DailyPoint> buildDailySeries(long start, long end) {
        int days = (int) Math.ceil((double) (end - start) / DAY_MS);
        List<DailyPoint> series = new ArrayList<>();
        if (days <= 0) return series;

        DayBucket[] buckets = new DayBucket[days];
        for (int i = 0; i < days; i++) {
            buckets[i] = new DayBucket();
            buckets[i].dayStart = start + (i * DAY_MS);
        }

        List<DrainEvent> drains = database.drainEventDao().getEventsSince(start);
        for (DrainEvent d : drains) {
            if (d.startTime < start || d.startTime >= end) continue;
            int idx = (int) ((d.startTime - start) / DAY_MS);
            if (idx < 0 || idx >= days) continue;
            buckets[idx].drainSum += d.drainRatePerHour;
            buckets[idx].drainCount++;
        }

        List<IdleDrainSession> idleSessions = database.idleDrainSessionDao().getSessionsSince(start);
        for (IdleDrainSession s : idleSessions) {
            if (s.startTime < start || s.startTime >= end) continue;
            int idx = (int) ((s.startTime - start) / DAY_MS);
            if (idx < 0 || idx >= days) continue;
            buckets[idx].idleSum += s.drainPerHour;
            buckets[idx].idleCount++;
        }

        List<ChargingSession> sessions = database.chargingSessionDao().getSessionsSince(start);
        for (ChargingSession cs : sessions) {
            if (cs.startTime < start || cs.startTime >= end || cs.endTime == 0) continue;
            int idx = (int) ((cs.startTime - start) / DAY_MS);
            if (idx < 0 || idx >= days) continue;
            buckets[idx].chargeMinutesSum += cs.getDurationMinutes();
            buckets[idx].chargeCount++;
        }

        List<ThermalEvent> thermalEvents = database.thermalEventDao().getEventsSince(start);
        for (ThermalEvent te : thermalEvents) {
            if (te.timestamp < start || te.timestamp >= end) continue;
            int idx = (int) ((te.timestamp - start) / DAY_MS);
            if (idx < 0 || idx >= days) continue;
            buckets[idx].thermalMax = Math.max(buckets[idx].thermalMax, te.peakTemperature);
            buckets[idx].thermalCount++;
        }

        for (DayBucket b : buckets) {
            DailyPoint p = new DailyPoint();
            p.ts = b.dayStart;
            p.drainRate = b.drainCount > 0 ? b.drainSum / b.drainCount : 0f;
            p.chargingMinutes = b.chargeCount > 0 ? b.chargeMinutesSum / b.chargeCount : 0f;
            p.thermalPeak = b.thermalCount > 0 ? b.thermalMax : 0f;
            p.idleDrain = b.idleCount > 0 ? b.idleSum / b.idleCount : 0f;
            p.samples = b.drainCount + b.idleCount + b.chargeCount + b.thermalCount;
            series.add(p);
        }

        return series;
    }

    private String toJson(List<DailyPoint> points) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < points.size(); i++) {
            DailyPoint p = points.get(i);
            if (i > 0) sb.append(',');
            sb.append('{');
            sb.append("\"ts\":").append(p.ts).append(',');
            sb.append("\"drainRate\":").append(formatFloat(p.drainRate)).append(',');
            sb.append("\"chargingMinutes\":").append(formatFloat(p.chargingMinutes)).append(',');
            sb.append("\"thermalPeak\":").append(formatFloat(p.thermalPeak)).append(',');
            sb.append("\"idleDrain\":").append(formatFloat(p.idleDrain)).append(',');
            sb.append("\"samples\":").append(p.samples);
            sb.append('}');
        }
        sb.append(']');
        return sb.toString();
    }

    private String buildNotes(UpdateComparison comparison, WindowMetrics pre, WindowMetrics post) {
        StringBuilder sb = new StringBuilder();
        sb.append("Overall drain change: ").append(formatDelta(comparison.drainRateDelta)).append("%/hr. ");
        sb.append("Idle drain change: ").append(formatDelta(comparison.idleDrainDelta)).append("%/hr. ");
        sb.append("Charging time change: ").append(formatDelta(comparison.chargingTimeDelta)).append(" min. ");
        sb.append("Thermal peak change: ").append(formatDelta(comparison.thermalPeakDelta)).append("°C. ");
        sb.append("Verdict: ").append(comparison.overallImpact).append(" @ conf ")
          .append(String.format("%.0f", comparison.confidenceLevel)).append("%. ");
        sb.append("Samples pre/post: ").append(pre.samples).append("/").append(post.samples).append(".");
        return sb.toString();
    }

    private String formatFloat(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return "0";
        return String.format("%.4f", value);
    }

    private String formatDelta(float delta) {
        return String.format("%.2f", delta);
    }

    private String inferUpdateType(String prev, String curr) {
        if (prev == null || curr == null) return "unknown";
        if (!prev.equals(curr) && curr.contains("-beta")) return "beta";
        if (!prev.equals(curr) && curr.split("\\.").length > 2) return "minor";
        if (!prev.equals(curr)) return "major";
        return "none";
    }

    private static class WindowMetrics {
        float screenOnDrainSum;
        float screenOffDrainSum;
        float overallDrainSum;
        int screenOnCount;
        int screenOffCount;
        int overallCount;
        float idleDrainSum;
        int idleCount;
        float chargeMinutesSum;
        int chargeCount;
        float temperatureSum;
        int temperatureCount;
        float maxTemperature;
        float healthSum;
        int healthCount;

        float screenOnDrain;
        float screenOffDrain;
        float overallDrain;
        float idleDrain;
        float avgChargeMinutes;
        float avgTemperature;
        float health;
        int samples;
    }

    private static class DailyPoint {
        long ts;
        float drainRate;
        float chargingMinutes;
        float thermalPeak;
        float idleDrain;
        int samples;
    }

    private static class DayBucket {
        long dayStart;
        float drainSum;
        int drainCount;
        float idleSum;
        int idleCount;
        float chargeMinutesSum;
        int chargeCount;
        float thermalMax;
        int thermalCount;
    }

    private static class Verdict {
        String label;
        String recommendation;
        float score;
        float confidence;
    }
}
