package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.BatterySnapshot;

import java.util.List;

/**
 * Updates long-term health records based on recent snapshots.
 */
public class BatteryHealthManager {

    private static final float DEFAULT_DESIGN_CAPACITY_MAH = 4000f;
    private static final float MAX_SAFE_TEMP_C = 45.0f;
    private static final long DISCHARGE_WINDOW_MS = 72L * 60L * 60L * 1000L;
    private static final long CYCLE_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L;

    private final HexNilDatabase database;

    public BatteryHealthManager(Context context, HexNilDatabase database) {
        this.database = database;
    }

    public void updateHealth(BatterySnapshot snapshot) {
        if (snapshot == null) return;

        BatteryHealth latest = database.batteryHealthDao().getLatest();

        float design = snapshot.designCapacityMah > 0
                ? snapshot.designCapacityMah
                : (latest != null && latest.designCapacityMah > 0
                ? latest.designCapacityMah
                : DEFAULT_DESIGN_CAPACITY_MAH);

        List<BatterySnapshot> recent = database.batterySnapshotDao().getSnapshotsSince(snapshot.timestamp - DISCHARGE_WINDOW_MS);
        float estimatedCapacity = estimateCapacityFromDischarge(recent, design);
        float currentCapacity = estimatedCapacity > 0
                ? estimatedCapacity
                : (snapshot.capacityMah > 0 ? snapshot.capacityMah : (snapshot.chargeCounter > 0 ? snapshot.chargeCounter / 1000f : design));

        float healthPct = design > 0 ? clamp((currentCapacity / design) * 100f, 0f, 110f) : 0f;
        float capacityFade = Math.max(0f, 100f - healthPct);
        float capacityFadePercent = capacityFade;

        CycleStats cycles = estimateCycles(design, snapshot.timestamp, latest);
        float cycleCount = cycles.cycleEquivalents;
        int estimatedCycles = Math.max(0, Math.round(cycleCount));

        float degradationRate = 0f;
        float fadePer100Cycles = 0f;
        float fadePerMonth = 0f;
        if (latest != null) {
            float deltaFade = capacityFadePercent - latest.capacityFade;
            float deltaMonths = Math.max(0.03f, (snapshot.timestamp - latest.timestamp) / (30f * 24f * 3_600_000f));
            degradationRate = deltaFade / deltaMonths;
            fadePerMonth = degradationRate;
            float deltaCycles = Math.max(0.01f, cycleCount - latest.cycleCount);
            fadePer100Cycles = (deltaFade / deltaCycles) * 100f;
        }

        float predictedEolMonths = degradationRate > 0
                ? Math.max(0f, (healthPct - 80f) / degradationRate)
                : 60f;

        BatteryHealth health = new BatteryHealth();
        health.timestamp = snapshot.timestamp;
        health.designCapacityMah = design;
        health.currentCapacityMah = currentCapacity;
        health.estimatedCapacityMah = estimatedCapacity;
        health.capacityFade = capacityFade;
        health.healthPercentage = healthPct;
        health.capacityFadePercent = capacityFadePercent;
        health.estimatedCycles = estimatedCycles;
        health.cycleCount = cycleCount;
        health.totalChargeAccumulated = (long) cycles.totalChargeMah;
        health.totalDischargeAccumulated = (long) cycles.totalDischargeMah;
        health.internalResistance = latest != null ? latest.internalResistance : 0f;
        health.voltageDropUnderLoad = latest != null ? latest.voltageDropUnderLoad : 0f;
        health.selfDischargeRate = latest != null ? latest.selfDischargeRate : 0f;
        health.chargingEfficiency = latest != null ? latest.chargingEfficiency : 90f;
        health.degradationRate = degradationRate;
        health.fadePer100Cycles = fadePer100Cycles;
        health.fadePerMonth = fadePerMonth;
        health.degradationTrend = degradationRate > 3f ? "accelerating" : degradationRate > 1f ? "gradual" : "stable";
        health.predictedEOL = predictedEolMonths;
        health.hasAbnormalWear = healthPct < 80f || degradationRate > 5f;
        health.hasAbnormalAging = fadePer100Cycles > 3f || fadePerMonth > 2.5f;
        health.hasSwellingRisk = snapshot.temperature >= (int) (MAX_SAFE_TEMP_C * 10) && !snapshot.isCharging;
        health.hasCalibrationIssue = Math.abs(snapshot.level - healthPct) > 10f;
        health.abnormalityDetails = health.hasAbnormalWear ? "Capacity below expected baseline" : "";
        health.agingNotes = health.hasAbnormalAging ? "Fade rate exceeds baseline" : "";
        health.capacityScore = clamp(healthPct, 0f, 100f);
        health.resistanceScore = clamp(100f - (health.internalResistance / 10f), 60f, 100f);
        health.cycleScore = clamp(100f - (cycleCount / 800f * 100f), 0f, 100f);
        health.overallScore = clamp((health.capacityScore * 0.6f) + (health.cycleScore * 0.3f) + (health.resistanceScore * 0.1f), 0f, 100f);
        health.batteryModel = latest != null ? latest.batteryModel : null;
        health.manufacturer = latest != null ? latest.manufacturer : null;
        health.manufactureDate = latest != null ? latest.manufactureDate : null;
        health.batchId = latest != null ? latest.batchId : null;
        health.lastFullCharge = snapshot.level >= 99 ? snapshot.timestamp : (latest != null ? latest.lastFullCharge : 0);
        health.lastFullDischarge = snapshot.level <= 5 ? snapshot.timestamp : (latest != null ? latest.lastFullDischarge : 0);
        health.needsCalibration = health.hasCalibrationIssue || health.healthPercentage < 80f;

        // Supplier/batch rollups
        if (health.manufacturer != null) {
            health.supplierAverageHealth = database.batteryHealthDao().getAverageHealthBySupplier(health.manufacturer);
        }
        if (health.batchId != null) {
            health.batchAverageHealth = database.batteryHealthDao().getAverageHealthByBatch(health.batchId);
        }

        database.batteryHealthDao().insert(health);
    }

    private float estimateCapacityFromDischarge(List<BatterySnapshot> snaps, float design) {
        if (snaps == null || snaps.size() < 2) return 0f;
        BatterySnapshot start = null;
        BatterySnapshot end = null;
        for (BatterySnapshot s : snaps) {
            if (s.level >= 95) {
                start = s;
            }
            if (start != null && s.level <= 20) {
                end = s;
                break;
            }
        }
        if (start == null || end == null) return 0f;
        float dropFrac = (start.level - end.level) / 100f;
        if (dropFrac <= 0f) return 0f;
        long deltaCharge = start.chargeCounter - end.chargeCounter;
        if (deltaCharge > 0) {
            return (deltaCharge / 1000f) / dropFrac;
        }
        long deltaEnergy = start.energyCounter - end.energyCounter;
        if (deltaEnergy > 0) {
            float avgVoltage = (start.voltage + end.voltage) / 2f / 1000f;
            float wattHours = deltaEnergy / 1_000_000_000f; // nanowatt-hours to Wh
            float milliampHours = avgVoltage > 0 ? (wattHours * 1000f) / avgVoltage : 0f;
            return milliampHours / dropFrac;
        }
        return 0f;
    }

    private CycleStats estimateCycles(float design, long nowTs, BatteryHealth latest) {
        CycleStats stats = new CycleStats();
        if (latest != null) {
            stats.totalChargeMah = latest.totalChargeAccumulated;
            stats.totalDischargeMah = latest.totalDischargeAccumulated;
            stats.cycleEquivalents = latest.cycleCount;
        }
        if (design <= 0) return stats;
        List<BatterySnapshot> snaps = database.batterySnapshotDao().getSnapshotsSince(nowTs - CYCLE_WINDOW_MS);
        if (snaps == null || snaps.size() < 2) return stats;
        BatterySnapshot prev = null;
        for (BatterySnapshot s : snaps) {
            if (prev != null) {
                long deltaCharge = s.chargeCounter - prev.chargeCounter;
                if (deltaCharge > 0) {
                    stats.totalChargeMah += deltaCharge / 1000f;
                    stats.cycleEquivalents += (deltaCharge / 1000f) / design;
                } else if (deltaCharge < 0) {
                    stats.totalDischargeMah += (-deltaCharge) / 1000f;
                }
            }
            prev = s;
        }
        return stats;
    }

    private static class CycleStats {
        float totalChargeMah = 0f;
        float totalDischargeMah = 0f;
        float cycleEquivalents = 0f;
    }

    private float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
