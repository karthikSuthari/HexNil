package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.IdleDrainSession;

/**
 * Observes idle state snapshots to quantify standby drain and Doze effectiveness.
 */
public class IdleDrainAnalyzer {

    private final HexNilDatabase database;
    private IdleDrainSession activeSession;

    public IdleDrainAnalyzer(Context context, HexNilDatabase database) {
        this.database = database;
    }

    public synchronized void beginIdleIfNeeded(BatterySnapshot snapshot) {
        if (snapshot == null || snapshot.screenOn || snapshot.isCharging) return;
        if (activeSession != null && activeSession.endTime == 0) return;

        activeSession = new IdleDrainSession();
        activeSession.startLevel = snapshot.level;
        activeSession.startTime = snapshot.timestamp;
        activeSession.wasScreenOff = true;
        activeSession.wasUnplugged = !snapshot.isCharging;
        activeSession.dozeEntryTime = snapshot.isDozeMode ? snapshot.timestamp : 0;
        database.idleDrainSessionDao().insert(activeSession);
    }

    public synchronized void processSnapshot(BatterySnapshot snapshot) {
        if (snapshot == null) return;

        if (snapshot.screenOn || snapshot.isCharging) {
            closeIfNeeded(snapshot);
            return;
        }

        if (activeSession == null) {
            beginIdleIfNeeded(snapshot);
        }
        if (activeSession == null) return;

        activeSession.endLevel = snapshot.level;
        activeSession.endTime = snapshot.timestamp;
        activeSession.drainAmount = Math.max(0, activeSession.startLevel - activeSession.endLevel);
        activeSession.drainPerHour = activeSession.getDurationHours() > 0
            ? activeSession.drainAmount / activeSession.getDurationHours()
            : 0;
        updateIdleMetrics(snapshot);
        activeSession.benchmarkResult = benchmark(activeSession.drainPerHour);

        database.idleDrainSessionDao().update(activeSession);
    }

    private void closeIfNeeded(BatterySnapshot snapshot) {
        if (activeSession == null) return;
        if (activeSession.dozeEntryTime > 0 && activeSession.dozeExitTime == 0) {
            activeSession.dozeExitTime = snapshot.timestamp;
        }
        activeSession.endTime = snapshot.timestamp;
        activeSession.endLevel = snapshot.level;
        activeSession.drainAmount = Math.max(0, activeSession.startLevel - activeSession.endLevel);
        activeSession.drainPerHour = activeSession.getDurationHours() > 0
            ? activeSession.drainAmount / activeSession.getDurationHours()
            : 0;
        updateIdleMetrics(snapshot);
        database.idleDrainSessionDao().update(activeSession);
        activeSession = null;
    }

    // Instrumentation hooks for wake reasons
    public synchronized void recordAlarmWakeup() {
        if (activeSession != null) {
            activeSession.alarmWakeups += 1;
            database.idleDrainSessionDao().update(activeSession);
        }
    }

    public synchronized void recordNetworkWakeup() {
        if (activeSession != null) {
            activeSession.networkWakeups += 1;
            database.idleDrainSessionDao().update(activeSession);
        }
    }

    private void updateIdleMetrics(BatterySnapshot snapshot) {
        if (activeSession == null) return;

        activeSession.dozeActive = snapshot.isDozeMode;
        if (snapshot.isDozeMode && activeSession.dozeEntryTime == 0) {
            activeSession.dozeEntryTime = snapshot.timestamp;
        }
        if (!snapshot.isDozeMode && activeSession.dozeEntryTime > 0 && activeSession.dozeExitTime == 0) {
            activeSession.dozeExitTime = snapshot.timestamp;
        }

        long dozeDuration = activeSession.dozeEntryTime > 0
            ? (activeSession.dozeExitTime > 0 ? activeSession.dozeExitTime : snapshot.timestamp) - activeSession.dozeEntryTime
            : 0;
        long totalDuration = Math.max(1, snapshot.timestamp - activeSession.startTime);
        activeSession.deepSleepPercentage = dozeDuration > 0 ? (dozeDuration * 100f) / totalDuration : 0f;
        activeSession.dozeEffectiveness = Math.max(0f, 100f - (activeSession.drainPerHour * 200f));
        activeSession.deepSleepSuccessRate = totalDuration > 0 ? (float) dozeDuration / totalDuration : 0f;
        activeSession.dozeEffectivenessScore = activeSession.dozeEffectiveness;
        activeSession.sessionType = snapshot.isDozeMode ? "deep_doze" : "idle";
        activeSession.expectedDrain = 0.5f * activeSession.getDurationHours();
        activeSession.drainDelta = activeSession.drainAmount - activeSession.expectedDrain;
        activeSession.hasLeakage = activeSession.drainPerHour > 1.0f || activeSession.alarmWakeups + activeSession.networkWakeups > 3;
        activeSession.leakageSources = activeSession.hasLeakage ? "alarms/network" : "";
        activeSession.leakageClass = classifyLeakage(activeSession.drainPerHour, activeSession.deepSleepSuccessRate, activeSession.alarmWakeups + activeSession.networkWakeups);
        activeSession.recommendations = activeSession.hasLeakage ? "Disable aggressive background tasks" : "";
    }

    private String classifyLeakage(float drainPerHour, float deepSleepRate, int wakes) {
        if (drainPerHour > 2.0f || wakes > 6 || deepSleepRate < 0.3f) return "LEAK";
        if (drainPerHour > 1.0f || wakes > 3 || deepSleepRate < 0.5f) return "WARNING";
        return "NORMAL";
    }

    private String benchmark(float drainPerHour) {
        if (drainPerHour < 0.3f) return "excellent";
        if (drainPerHour < 0.5f) return "good";
        if (drainPerHour < 1.0f) return "fair";
        if (drainPerHour < 2.0f) return "poor";
        return "failing";
    }
}
