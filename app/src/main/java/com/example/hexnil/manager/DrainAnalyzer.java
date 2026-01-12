package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.DrainEvent;

/**
 * Tracks discharging periods and records drain events for later attribution.
 */
public class DrainAnalyzer {

    private final HexNilDatabase database;
    private DrainEvent activeEvent;
    private int lastLevel = -1;

    public DrainAnalyzer(Context context, HexNilDatabase database) {
        this.database = database;
    }

    public synchronized void startDrainTracking() {
        if (activeEvent != null && activeEvent.endTime == 0) return;
        activeEvent = new DrainEvent();
        database.drainEventDao().insert(activeEvent);
    }

    public synchronized void processSnapshot(BatterySnapshot snapshot) {
        if (snapshot.isCharging) {
            // Close active drain event when charging resumes
            if (activeEvent != null) {
                activeEvent.endTime = snapshot.timestamp;
                activeEvent.endLevel = snapshot.level;
                finalizeDrain(activeEvent);
                database.drainEventDao().update(activeEvent);
                activeEvent = null;
            }
            return;
        }

        if (activeEvent == null) {
            startDrainTracking();
        }
        if (activeEvent == null) return;

        if (activeEvent.startLevel == 0) {
            activeEvent.startLevel = snapshot.level;
            activeEvent.startTime = snapshot.timestamp;
        }

        activeEvent.endLevel = snapshot.level;
        activeEvent.endTime = snapshot.timestamp;
        activeEvent.screenOn = snapshot.screenOn;
        activeEvent.foregroundApp = snapshot.foregroundApp;
        activeEvent.isCharging = false;
        activeEvent.drainAmount = Math.max(0, activeEvent.startLevel - activeEvent.endLevel);
        activeEvent.drainRatePerHour = activeEvent.getDurationHours() > 0
                ? activeEvent.drainAmount / activeEvent.getDurationHours()
                : 0;
        activeEvent.screenState = snapshot.isDozeMode ? "doze" : snapshot.screenOn ? "on" : "off";
        activeEvent.isIdle = !snapshot.screenOn;
        classifyDrain(snapshot);
        activeEvent.isAnomalous = activeEvent.drainRatePerHour > expectedDrainRate(snapshot) * 1.5f;
        activeEvent.expectedDrain = expectedDrainRate(snapshot) * activeEvent.getDurationHours();
        activeEvent.anomalyScore = activeEvent.expectedDrain > 0
                ? Math.max(0, (activeEvent.drainAmount - activeEvent.expectedDrain) / activeEvent.expectedDrain)
                : 0;

        database.drainEventDao().update(activeEvent);
        lastLevel = snapshot.level;
    }

    private void finalizeDrain(DrainEvent event) {
        event.drainAmount = Math.max(0, event.startLevel - event.endLevel);
        event.drainRatePerHour = event.getDurationHours() > 0
                ? event.drainAmount / event.getDurationHours()
                : 0;
        event.isAnomalous = event.isExcessiveDrain();
    }

    private void classifyDrain(BatterySnapshot snapshot) {
        if (snapshot.screenOn) {
            activeEvent.drainType = activeEvent.drainRatePerHour > 18 ? "heavy" : "normal";
            activeEvent.drainCategory = "display";
        } else if (snapshot.isDozeMode) {
            activeEvent.drainType = activeEvent.drainRatePerHour > 1.0f ? "idle" : "standby";
            activeEvent.drainCategory = "idle";
        } else {
            activeEvent.drainType = activeEvent.drainRatePerHour > 1.5f ? "heavy" : "normal";
            activeEvent.drainCategory = "system";
        }

        activeEvent.primaryCause = snapshot.topAppPackage != null ? snapshot.topAppPackage : snapshot.foregroundApp;
        activeEvent.networkType = snapshot.networkState;
        activeEvent.networkDrainEstimate = "mobile".equals(snapshot.networkState) ? activeEvent.drainRatePerHour * 0.4f : activeEvent.drainRatePerHour * 0.2f;
        activeEvent.displayDrainEstimate = snapshot.screenOn
                ? (snapshot.screenBrightness / 255f) * activeEvent.drainRatePerHour
                : 0f;
        activeEvent.cpuDrainEstimate = snapshot.cpuLoadTotal > 0
                ? (snapshot.cpuLoadTotal / 100f) * activeEvent.drainRatePerHour
                : 0f;
        activeEvent.avgBrightness = snapshot.screenBrightness;
        activeEvent.screenOnTime = activeEvent.screenOn ? Math.max(0, snapshot.timestamp - activeEvent.startTime) : activeEvent.screenOnTime;
        activeEvent.analysisNotes = "Deterministic drain attribution";
        activeEvent.recommendations = activeEvent.isAnomalous ? "Reduce display/network usage" : "";
    }

    private float expectedDrainRate(BatterySnapshot snapshot) {
        if (snapshot.screenOn) return 12f;
        if (snapshot.isDozeMode) return 0.4f;
        return 0.7f;
    }
}
