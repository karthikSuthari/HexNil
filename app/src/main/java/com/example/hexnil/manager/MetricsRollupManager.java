package com.example.hexnil.manager;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatteryHealth;
import com.example.hexnil.model.ChargingSession;
import com.example.hexnil.model.DrainEvent;
import com.example.hexnil.model.IdleDrainSession;
import com.example.hexnil.model.AppBatteryUsage;
import com.example.hexnil.model.RollupMetrics;
import com.example.hexnil.model.StressTestResult;

import java.util.List;

/**
 * Computes daily/weekly rollups from Room data for AI and reporting.
 */
public class MetricsRollupManager {

    private final HexNilDatabase database;

    public MetricsRollupManager(HexNilDatabase database) {
        this.database = database;
    }

    public RollupMetrics rollup(String label, long start, long end) {
        RollupMetrics r = new RollupMetrics();
        r.label = label;
        r.periodStart = start;
        r.periodEnd = end;

        List<DrainEvent> drains = database.drainEventDao().getEventsSince(start);
        float systemDrain = 0f, userDrain = 0f, wakelockDrain = 0f, networkDrain = 0f;
        for (DrainEvent d : drains) {
            if (d.startTime < start || d.startTime > end) continue;
            if (d.screenOn) {
                r.screenOnDrain += d.drainRatePerHour;
                r.sampleCount++;
            } else {
                r.screenOffDrain += d.drainRatePerHour;
                r.sampleCount++;
            }
            wakelockDrain += d.wakelockCount * 0.05f;
            networkDrain += d.networkDrainEstimate;
            if ("system".equalsIgnoreCase(d.drainCategory)) systemDrain += d.drainAmount;
            if ("app".equalsIgnoreCase(d.drainCategory)) userDrain += d.drainAmount;
        }
        r.screenOnDrain = countScreenOn(drains, start, end) > 0 ? r.screenOnDrain / countScreenOn(drains, start, end) : 0f;
        r.screenOffDrain = countScreenOff(drains, start, end) > 0 ? r.screenOffDrain / countScreenOff(drains, start, end) : 0f;
        r.wakelockDrain = wakelockDrain;
        r.networkDrain = networkDrain;
        r.systemDrain = systemDrain;
        r.userDrain = userDrain;

        List<IdleDrainSession> idle = database.idleDrainSessionDao().getSessionsSince(start);
        float idleSum = 0f; int idleCount = 0;
        for (IdleDrainSession s : idle) {
            if (s.startTime < start || s.startTime > end) continue;
            idleSum += s.drainPerHour;
            idleCount++;
            r.overDischargeEvents += s.endLevel <= 10 ? 1 : 0;
        }
        r.idleDrain = idleCount > 0 ? idleSum / idleCount : 0f;

        List<ChargingSession> csList = database.chargingSessionDao().getSessionsSince(start);
        float chargeMinutesSum = 0f; int chargeCount = 0; float chargeSpeedSum = 0f; float maxTemp = 0f;
        for (ChargingSession cs : csList) {
            if (cs.startTime < start || cs.startTime > end || cs.endTime == 0) continue;
            chargeMinutesSum += cs.getDurationMinutes();
            chargeSpeedSum += cs.chargingSpeed;
            maxTemp = Math.max(maxTemp, cs.maxTemperature);
            chargeCount++;
            if (cs.isFastCharging && (cs.maxCurrent > 2500 || cs.maxTemperature > 44f)) {
                r.fastChargeAbuse += 1;
            }
        }
        r.avgChargeMinutes = chargeCount > 0 ? chargeMinutesSum / chargeCount : 0f;
        r.avgChargeSpeed = chargeCount > 0 ? chargeSpeedSum / chargeCount : 0f;
        r.maxChargeTemp = maxTemp;

        float tempSum = 0f; int tempCount = 0; float maxTempPeak = 0f;
        List<com.example.hexnil.model.ThermalEvent> thermals = database.thermalEventDao().getEventsSince(start);
        for (com.example.hexnil.model.ThermalEvent t : thermals) {
            if (t.timestamp < start || t.timestamp > end) continue;
            tempSum += t.peakTemperature;
            tempCount++;
            maxTempPeak = Math.max(maxTempPeak, t.peakTemperature);
        }
        r.avgTemp = tempCount > 0 ? tempSum / tempCount : 0f;
        r.maxTemp = maxTempPeak;

        List<BatteryHealth> health = database.batteryHealthDao().getHealthHistorySince(start);
        float healthSum = 0f; int healthCount = 0; float fade = 0f;
        for (BatteryHealth h : health) {
            if (h.timestamp < start || h.timestamp > end) continue;
            healthSum += h.healthPercentage;
            fade = Math.max(fade, h.capacityFade);
            healthCount++;
        }
        r.avgHealth = healthCount > 0 ? healthSum / healthCount : 0f;
        r.capacityFade = fade;

        List<StressTestResult> stress = database.miscDao().getAllStressTests();
        float stressSum = 0f; int stressCount = 0;
        for (StressTestResult s : stress) {
            if (s.timestamp < start || s.timestamp > end) continue;
            stressSum += s.overallScore;
            stressCount++;
        }
        r.stressScore = stressCount > 0 ? stressSum / stressCount : 0f;

        r.swellingRisk = r.capacityFade > 20;
        return r;
    }

    private int countScreenOn(List<DrainEvent> drains, long start, long end) {
        int c = 0;
        for (DrainEvent d : drains) {
            if (d.startTime < start || d.startTime > end) continue;
            if (d.screenOn) c++;
        }
        return c;
    }

    private int countScreenOff(List<DrainEvent> drains, long start, long end) {
        int c = 0;
        for (DrainEvent d : drains) {
            if (d.startTime < start || d.startTime > end) continue;
            if (!d.screenOn) c++;
        }
        return c;
    }
}
