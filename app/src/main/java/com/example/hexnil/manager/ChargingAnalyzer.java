package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ChargingSession;

/**
 * Handles charging session lifecycle and basic safety/stat calculations.
 */
public class ChargingAnalyzer {

    private final HexNilDatabase database;
    private ChargingSession activeSession;
    private float voltageSum;
    private float currentSum;
    private float tempSum;
    private int sampleCount;
    private float lastCurrentMa;
    private float lastVoltageMv;
    private long lastTimestamp;
    private long lastInterruptionTs;
    private long lastSampleIntervalSum;
    private int sampleIntervalCount;
    private StringBuilder voltageCurve;
    private StringBuilder currentCurve;
    private StringBuilder interruptionTimestamps;
    private StringBuilder interruptionDurations;

    private static final int INTERRUPTION_THRESHOLD_MA = 400;

    public ChargingAnalyzer(Context context, HexNilDatabase database) {
        this.database = database;
    }

    public synchronized void startChargingSession() {
        if (activeSession != null && activeSession.isComplete()) {
            activeSession = null;
        }
        if (activeSession != null) return;

        activeSession = new ChargingSession();
        activeSession.startTime = System.currentTimeMillis();
        resetAccumulators();
        database.chargingSessionDao().insert(activeSession);
    }

    public synchronized void endChargingSession() {
        if (activeSession == null) return;
        activeSession.endTime = System.currentTimeMillis();
        activeSession.chargedAmount = Math.max(0, activeSession.endLevel - activeSession.startLevel);
        activeSession.chargingDuration = activeSession.endTime - activeSession.startTime;
        if (activeSession.chargingDuration > 0) {
            float hours = activeSession.chargingDuration / 3_600_000f;
            activeSession.avgVoltage = sampleCount > 0 ? voltageSum / sampleCount : activeSession.avgVoltage;
            activeSession.avgCurrent = sampleCount > 0 ? currentSum / sampleCount : activeSession.avgCurrent;
            activeSession.avgTemperature = sampleCount > 0 ? tempSum / sampleCount : activeSession.avgTemperature;
            activeSession.chargingSpeed = hours > 0 ? activeSession.chargedAmount / hours : 0f;
            activeSession.chargingEfficiency = activeSession.energyDelivered > 0 && activeSession.chargedAmount > 0
                    ? Math.min(100f, (activeSession.chargedAmount * 10f) / activeSession.energyDelivered)
                    : activeSession.chargingEfficiency;
        }
        evaluateSafety();
        finalizeCurves();
        database.chargingSessionDao().update(activeSession);
        activeSession = null;
    }

    public synchronized void processChargingSnapshot(BatterySnapshot snapshot) {
        if (activeSession == null) {
            startChargingSession();
        }
        if (activeSession == null) return;

        if (activeSession.startLevel == 0) {
            activeSession.startLevel = snapshot.level;
            activeSession.startTemperature = snapshot.getTemperatureCelsius();
            activeSession.startTime = snapshot.timestamp;
        }

        activeSession.endLevel = snapshot.level;
        activeSession.endTemperature = snapshot.getTemperatureCelsius();
        activeSession.maxTemperature = Math.max(activeSession.maxTemperature, snapshot.getTemperatureCelsius());
        activeSession.maxCurrent = Math.max(activeSession.maxCurrent, snapshot.chargeRate);
        if (activeSession.minCurrent == 0 || snapshot.chargeRate < activeSession.minCurrent) {
            activeSession.minCurrent = snapshot.chargeRate;
        }
        activeSession.chargingType = snapshot.chargingType;
        activeSession.isFastCharging = snapshot.isFastCharging;
        activeSession.energyDelivered += accumulateEnergy(snapshot);

        activeSession.maxVoltage = Math.max(activeSession.maxVoltage, snapshot.voltage);
        if (activeSession.minVoltage == 0 || snapshot.voltage < activeSession.minVoltage) {
            activeSession.minVoltage = snapshot.voltage;
        }

        sampleCount++;
        voltageSum += snapshot.voltage;
        currentSum += snapshot.chargeRate;
        tempSum += snapshot.getTemperatureCelsius();
        activeSession.avgVoltage = voltageSum / sampleCount;
        activeSession.avgCurrent = currentSum / sampleCount;
        activeSession.avgTemperature = tempSum / sampleCount;

        activeSession.chargingSpeed = activeSession.getChargingRate();
        activeSession.safetyStatus = evaluateSafetyLevel(snapshot);
        activeSession.safetyNotes = activeSession.safetyStatus.equals("safe")
                ? ""
                : "Watch temperature/current stability";
        activeSession.chargerFingerprint = snapshot.chargerVendorId + ":" + snapshot.chargerProductId;
        activeSession.chargerQuality = activeSession.isFastCharging ? "fast" : "normal";

        detectFluctuations(snapshot);
        recordCurveSamples(snapshot);
        detectInterruptions(snapshot);
        lastCurrentMa = snapshot.chargeRate;
        lastVoltageMv = snapshot.voltage;
        lastTimestamp = snapshot.timestamp;

        database.chargingSessionDao().update(activeSession);
    }

    private void detectFluctuations(BatterySnapshot snapshot) {
        if (lastCurrentMa != 0 && Math.abs(snapshot.chargeRate - lastCurrentMa) > 500) {
            activeSession.currentFluctuations += 1;
        }
        if (lastVoltageMv != 0 && Math.abs(snapshot.voltage - lastVoltageMv) > 80) {
            activeSession.voltageFluctuations += 1;
        }

        if (snapshot.chargeRate > 2500) {
            activeSession.overCurrentEvents += 1;
        }
        if (snapshot.voltage > 4_450) {
            activeSession.overVoltageEvents += 1;
        }
        if (snapshot.getTemperatureCelsius() > 44f) {
            activeSession.overTempEvents += 1;
        }
    }

    private float accumulateEnergy(BatterySnapshot snapshot) {
        if (lastTimestamp == 0) {
            lastTimestamp = snapshot.timestamp;
            return 0f;
        }
        long deltaMs = Math.max(0, snapshot.timestamp - lastTimestamp);
        float deltaHours = deltaMs / 3_600_000f;
        float chargeMa = Math.max(0f, snapshot.chargeRate);
        return chargeMa * deltaHours; // mAh
    }

    private String evaluateSafetyLevel(BatterySnapshot snapshot) {
        if (snapshot.getTemperatureCelsius() >= 46f || snapshot.chargeRate > 3000) {
            return "danger";
        }
        if (snapshot.getTemperatureCelsius() >= 43f || snapshot.chargeRate > 2200 || activeSession.currentFluctuations > 2) {
            return "warning";
        }
        return "safe";
    }

    private void evaluateSafety() {
        if (activeSession == null) return;
        String level = activeSession.safetyStatus != null ? activeSession.safetyStatus : "safe";
        if (activeSession.maxTemperature > 46f || activeSession.overCurrentEvents > 2) {
            level = "danger";
        } else if (activeSession.maxTemperature > 43f || activeSession.overVoltageEvents > 0) {
            level = "warning";
        }
        activeSession.safetyStatus = level;
        activeSession.safetyNotes = level.equals("safe") ? "" : "Check cable/charger quality";
    }

    private void resetAccumulators() {
        voltageSum = 0f;
        currentSum = 0f;
        tempSum = 0f;
        sampleCount = 0;
        lastCurrentMa = 0f;
        lastVoltageMv = 0f;
        lastTimestamp = 0L;
        lastInterruptionTs = 0L;
        lastSampleIntervalSum = 0L;
        sampleIntervalCount = 0;
        voltageCurve = new StringBuilder("[");
        currentCurve = new StringBuilder("[");
        interruptionTimestamps = new StringBuilder("[");
        interruptionDurations = new StringBuilder("[");
    }

    private void recordCurveSamples(BatterySnapshot snapshot) {
        appendPair(voltageCurve, snapshot.timestamp, snapshot.voltage);
        appendPair(currentCurve, snapshot.timestamp, snapshot.chargeRate);

        if (lastTimestamp > 0) {
            long interval = snapshot.timestamp - lastTimestamp;
            if (interval > 0) {
                lastSampleIntervalSum += interval;
                sampleIntervalCount++;
            }
        }
        activeSession.curveSampleIntervalMs = sampleIntervalCount > 0
                ? (int) (lastSampleIntervalSum / sampleIntervalCount)
                : 0;
    }

    private void detectInterruptions(BatterySnapshot snapshot) {
        if (lastCurrentMa > 0) {
            float drop = lastCurrentMa - snapshot.chargeRate;
            if (drop > INTERRUPTION_THRESHOLD_MA) {
                activeSession.interruptionCount += 1;
                long now = snapshot.timestamp;
                long duration = lastInterruptionTs > 0 ? now - lastInterruptionTs : 0;
                appendValue(interruptionTimestamps, now);
                appendValue(interruptionDurations, duration);
                lastInterruptionTs = now;
            }
        }

        // Compute stability: penalize interruptions and fluctuations
        float penalties = activeSession.interruptionCount * 5f + activeSession.currentFluctuations * 2f + activeSession.voltageFluctuations * 1.5f;
        activeSession.curveStabilityScore = Math.max(0f, 100f - penalties);
        if (activeSession.interruptionCount > 0) {
            activeSession.chargerQuality = activeSession.curveStabilityScore >= 80 ? "certified" : activeSession.curveStabilityScore >= 60 ? "questionable" : "poor";
        }
    }

    private void appendPair(StringBuilder sb, long t, float v) {
        if (sb == null) return;
        if (sb.length() > 1) sb.append(',');
        sb.append('{').append("\"t\":").append(t).append(',').append("\"v\":").append(v).append('}');
    }

    private void appendValue(StringBuilder sb, long v) {
        if (sb == null) return;
        if (sb.length() > 1) sb.append(',');
        sb.append(v);
    }

    private String closeArray(StringBuilder sb) {
        if (sb == null) return "[]";
        if (sb.charAt(sb.length() - 1) != '[') {
            sb.append(']');
            return sb.toString();
        }
        sb.append(']');
        return sb.toString();
    }

    private void finalizeCurves() {
        activeSession.voltageCurveJson = closeArray(voltageCurve);
        activeSession.currentCurveJson = closeArray(currentCurve);
        activeSession.interruptionTimestamps = closeArray(interruptionTimestamps);
        activeSession.interruptionDurations = closeArray(interruptionDurations);
    }
}
