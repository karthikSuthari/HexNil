package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.ThermalEvent;
import com.example.hexnil.model.ThermalHeatBucket;

/**
 * Monitors snapshot data for thermal anomalies and logs ThermalEvent records.
 */
public class ThermalMonitor {

    private static final float CRITICAL_TEMP_C = 45.0f;
    private static final float WARNING_TEMP_C = 42.0f;

    private final HexNilDatabase database;
    private ThermalEvent activeEvent;
    private ThermalHeatBucket activeBucket;

    public ThermalMonitor(Context context, HexNilDatabase database) {
        this.database = database;
    }

    public void analyzeSnapshot(BatterySnapshot snapshot) {
        float tempC = snapshot.getTemperatureCelsius();
        String severity = tempC >= CRITICAL_TEMP_C ? "critical" : tempC >= WARNING_TEMP_C ? "warning" : "normal";

        if ("normal".equals(severity)) {
            closeActiveEvent(snapshot.timestamp);
            updateBucket(snapshot, tempC);
            return;
        }

        if (activeEvent == null) {
            activeEvent = new ThermalEvent();
            activeEvent.timestamp = snapshot.timestamp;
            activeEvent.eventType = tempC >= CRITICAL_TEMP_C ? "threshold_violation" : "spike";
            activeEvent.severity = severity;
            activeEvent.batteryTemp = tempC;
            activeEvent.cpuTemp = 0;
            activeEvent.ambientTemp = 0;
            activeEvent.peakTemperature = tempC;
            activeEvent.temperatureRise = tempC - WARNING_TEMP_C;
            activeEvent.foregroundApp = snapshot.topAppPackage != null ? snapshot.topAppPackage : snapshot.foregroundApp;
            activeEvent.cpuUsage = snapshot.cpuLoadTotal > 0 ? snapshot.cpuLoadTotal : snapshot.cpuUsage;
            activeEvent.gpuUsage = snapshot.gpuLoad;
            activeEvent.isCharging = snapshot.isCharging;
            activeEvent.screenOn = snapshot.screenOn;
            activeEvent.batteryLevel = snapshot.level;
            activeEvent.currentDraw = snapshot.dischargeRate;
            activeEvent.networkType = snapshot.networkState;
            activeEvent.triggerActivity = snapshot.screenOn ? "display_load" : "background_load";
            activeEvent.id = database.thermalEventDao().insert(activeEvent);
            return;
        }

        activeEvent.severity = "critical".equals(severity) ? "critical" : activeEvent.severity;
        activeEvent.peakTemperature = Math.max(activeEvent.peakTemperature, tempC);
        activeEvent.temperatureRise = activeEvent.peakTemperature - WARNING_TEMP_C;
        activeEvent.duration = snapshot.timestamp - activeEvent.timestamp;
        activeEvent.cpuUsage = snapshot.cpuLoadTotal > 0 ? snapshot.cpuLoadTotal : snapshot.cpuUsage;
        activeEvent.gpuUsage = snapshot.gpuLoad;
        activeEvent.isCharging = snapshot.isCharging;
        activeEvent.screenOn = snapshot.screenOn;
        activeEvent.networkType = snapshot.networkState;
        activeEvent.batteryLevel = snapshot.level;
        activeEvent.currentDraw = snapshot.dischargeRate;
        activeEvent.eventType = activeEvent.duration > 120_000 ? "sustained_high" : activeEvent.eventType;
        database.thermalEventDao().update(activeEvent);

        updateBucket(snapshot, tempC);
    }

    private void closeActiveEvent(long timestamp) {
        if (activeEvent == null) return;
        activeEvent.duration = Math.max(0, timestamp - activeEvent.timestamp);
        activeEvent.cooldownTime = activeEvent.duration;
        activeEvent.resolution = "cooled";
        database.thermalEventDao().update(activeEvent);
        activeEvent = null;
    }

    private void updateBucket(BatterySnapshot snapshot, float tempC) {
        String bucket = tempBucket(tempC);
        int hourBlock = (int) ((snapshot.timestamp / 1000 / 60 / 60) % 24);

        long now = snapshot.timestamp;
        if (activeBucket == null || !bucket.equals(activeBucket.tempBucket) || activeBucket.hourBlock != hourBlock) {
            persistBucket();
            activeBucket = new ThermalHeatBucket();
            activeBucket.startTime = now;
            activeBucket.tempBucket = bucket;
            activeBucket.hourBlock = hourBlock;
            activeBucket.maxTemp = tempC;
            activeBucket.avgTemp = tempC;
            activeBucket.sampleCount = 0;
            activeBucket.durationMs = 0;
        }

        if (activeBucket != null) {
            activeBucket.sampleCount += 1;
            activeBucket.avgTemp = ((activeBucket.avgTemp * (activeBucket.sampleCount - 1)) + tempC) / activeBucket.sampleCount;
            activeBucket.maxTemp = Math.max(activeBucket.maxTemp, tempC);
            activeBucket.chargingCount += snapshot.isCharging ? 1 : 0;
            if (snapshot.topAppPackage != null) {
                activeBucket.topForegroundApp = snapshot.topAppPackage;
            }
            if (activeBucket.sampleCount > 1) {
                long lastTs = activeBucket.endTime == 0 ? activeBucket.startTime : activeBucket.endTime;
                activeBucket.durationMs += Math.max(0, now - lastTs);
            }
            activeBucket.endTime = now;
        }
    }

    private void persistBucket() {
        if (activeBucket == null) return;
        database.thermalHeatBucketDao().insert(activeBucket);
        activeBucket = null;
    }

    private String tempBucket(float tempC) {
        if (tempC < 35f) return "<35";
        if (tempC < 40f) return "35-40";
        if (tempC < 45f) return "40-45";
        return ">45";
    }
}
