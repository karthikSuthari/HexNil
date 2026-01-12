package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.CalibrationData;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;

/**
 * Maintains calibration records to detect SOC drift and guide recalibration steps.
 */
public class CalibrationManager {

    private final HexNilDatabase database;
    private int lastLevel = -1;
    private long lastTimestamp = 0L;
    private long lastPersistTimestamp = 0L;
    private int suddenDropCount = 0;
    private int sampleCount = 0;

    public CalibrationManager(Context context, HexNilDatabase database) {
        this.database = database;
    }

    public void recordCalibrationPoint(int reportedLevel, float estimatedActualLevel) {
        CalibrationData data = new CalibrationData();
        data.reportedLevel = reportedLevel;
        data.estimatedActualLevel = estimatedActualLevel;
        data.levelDrift = estimatedActualLevel - reportedLevel;
        data.driftExceeded = Math.abs(data.levelDrift) > 8f;
        data.socMismatch = Math.abs(data.levelDrift) > 5f;
        data.socAccuracy = 100 - Math.abs(data.levelDrift);
        data.driftDirection = data.levelDrift > 0 ? "under_reporting" : data.levelDrift < 0 ? "over_reporting" : "stable";
        data.needsCalibration = Math.abs(data.levelDrift) > 10;
        data.calibrationQuality = Math.max(0, 100 - Math.abs(data.levelDrift) * 2);
        data.correctedSoc = clamp(reportedLevel + data.levelDrift, 0f, 110f);
        data.correctionConfidence = Math.max(0, Math.min(100, data.socAccuracy));
        data.recommendationReason = data.needsCalibration ? "Drift exceeded threshold" : "Tracking";
        database.miscDao().insertCalibration(data);
    }

    public void processSnapshot(BatterySnapshot snapshot) {
        if (snapshot == null) return;

        if (lastTimestamp > 0 && lastLevel > 0) {
            int deltaLevel = lastLevel - snapshot.level;
            long deltaMs = Math.max(1, snapshot.timestamp - lastTimestamp);
            float deltaMinutes = deltaMs / 60000f;
            if (deltaLevel >= 5 && deltaMinutes <= 15f) {
                suddenDropCount += 1;
            }
        }

        float designCapacity = resolveDesignCapacity();
        float estimatedActualLevel = estimateActualLevel(snapshot, designCapacity);
        float levelDrift = estimatedActualLevel - snapshot.level;

        boolean shouldPersist = (snapshot.timestamp - lastPersistTimestamp) > 30 * 60 * 1000
                || Math.abs(levelDrift) > 8
                || suddenDropCount > 0;

        if (shouldPersist) {
            CalibrationData latest = database.miscDao().getLatestCalibration();
            CalibrationData data = new CalibrationData();
            data.reportedLevel = snapshot.level;
            data.estimatedActualLevel = estimatedActualLevel;
            data.levelDrift = levelDrift;
            data.avgDrift = latest != null ? (float) ((latest.avgDrift + levelDrift) / 2.0) : levelDrift;
            data.maxDrift = latest != null ? Math.max(latest.maxDrift, Math.abs(levelDrift)) : Math.abs(levelDrift);
            data.driftDirection = levelDrift > 0 ? "under_reporting" : levelDrift < 0 ? "over_reporting" : "stable";
            data.driftExceeded = Math.abs(levelDrift) > 8f;
            data.socMismatch = Math.abs(levelDrift) > 5f;
            data.hasSuddenDrops = suddenDropCount > 0;
            data.suddenDropCount = suddenDropCount;
            data.avgDropSize = suddenDropCount > 0 ? Math.abs(levelDrift) : 0f;
            data.worstDrop = suddenDropCount > 0 ? Math.abs(lastLevel - snapshot.level) : 0;
            data.lastFullCharge = snapshot.level >= 99 ? snapshot.timestamp : (latest != null ? latest.lastFullCharge : 0);
            data.lastLowBattery = snapshot.level <= 10 ? snapshot.timestamp : (latest != null ? latest.lastLowBattery : 0);
            data.lastDeepDischarge = snapshot.level <= 5 ? snapshot.timestamp : (latest != null ? latest.lastDeepDischarge : 0);
            data.fullChargeCount = latest != null ? latest.fullChargeCount + (snapshot.level >= 99 ? 1 : 0) : (snapshot.level >= 99 ? 1 : 0);
            data.deepDischargeCount = latest != null ? latest.deepDischargeCount + (snapshot.level <= 5 ? 1 : 0) : (snapshot.level <= 5 ? 1 : 0);
            data.calibrationQuality = clamp(100f - (Math.abs(levelDrift) * 2f) - (suddenDropCount * 5f), 0f, 100f);
            data.isCalibrated = data.calibrationQuality >= 70 && !data.hasSuddenDrops;
            data.needsCalibration = Math.abs(levelDrift) > 8 || data.hasSuddenDrops;
            data.calibrationRecommendation = data.needsCalibration
                    ? "Discharge to 5% then charge to 100% without interruption"
                    : "No calibration needed";
            data.calibrationSchedule = data.needsCalibration ? "Run calibration this week" : "Recheck monthly";
            data.daysSinceCalibration = latest != null ? (int) ((snapshot.timestamp - latest.lastCalibration) / (24 * 60 * 60 * 1000)) : 0;
            data.lastCalibration = data.needsCalibration ? snapshot.timestamp : (latest != null ? latest.lastCalibration : snapshot.timestamp);
            data.healthImpactScore = Math.min(100f, Math.abs(levelDrift) * 0.5f + suddenDropCount * 2f);
            data.socAccuracy = clamp(100f - Math.abs(levelDrift), 0f, 100f);
            sampleCount++;
                data.correctedSoc = clamp(snapshot.level + levelDrift, 0f, 110f);
                data.correctionConfidence = computeConfidence(latest, data, sampleCount);
                data.recommendationReason = buildRecommendationReason(data);

            database.miscDao().insertCalibration(data);
            lastPersistTimestamp = snapshot.timestamp;
            suddenDropCount = 0;
        }

        lastLevel = snapshot.level;
        lastTimestamp = snapshot.timestamp;
    }

    private float resolveDesignCapacity() {
        BatteryHealth latest = database.batteryHealthDao().getLatest();
        if (latest != null && latest.designCapacityMah > 0) {
            return latest.designCapacityMah;
        }
        return 4000f;
    }

    private float estimateActualLevel(BatterySnapshot snapshot, float designCapacityMah) {
        if (snapshot.chargeCounter > 0 && designCapacityMah > 0) {
            float currentMah = snapshot.chargeCounter / 1000f;
            return clamp((currentMah / designCapacityMah) * 100f, 0f, 110f);
        }
        return snapshot.level;
    }

    private float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private float computeConfidence(CalibrationData latest, CalibrationData current, int samples) {
        float recency = 30f;
        float driftPenalty = Math.min(40f, Math.abs(current.levelDrift) * 2f);
        float dropPenalty = current.hasSuddenDrops ? Math.min(20f, current.suddenDropCount * 5f) : 0f;
        float sampleScore = Math.min(30f, samples * 3f);
        float trendBonus = latest != null && Math.abs(latest.levelDrift) < 5f ? 10f : 0f;
        return clamp(recency + sampleScore + trendBonus - driftPenalty - dropPenalty, 0f, 100f);
    }

    private String buildRecommendationReason(CalibrationData data) {
        StringBuilder sb = new StringBuilder();
        if (data.driftExceeded) sb.append("Drift>8% ");
        if (data.hasSuddenDrops) sb.append("Sudden drops ");
        if (data.socMismatch) sb.append("SOC mismatch");
        String reason = sb.toString().trim();
        return reason.isEmpty() ? "Monitoring" : reason;
    }

    public CalibrationData getLatestCalibration() {
        return database.miscDao().getLatestCalibration();
    }
}
