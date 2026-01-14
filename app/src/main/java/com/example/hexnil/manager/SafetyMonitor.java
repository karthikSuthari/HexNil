package com.example.hexnil.manager;

import android.content.Context;

import com.example.hexnil.database.HexNilDatabase;
import com.example.hexnil.model.BatterySnapshot;
import com.example.hexnil.model.SafetyEvent;

/**
 * Performs lightweight safety checks and persists SafetyEvent records when thresholds are exceeded.
 */
public class SafetyMonitor {

    private static final float OVER_TEMP_C = 45.0f;
    private static final float OVER_VOLTAGE_MV = 4500f;
    private static final float OVER_CURRENT_MA = 2500f;

    private final HexNilDatabase database;

    public SafetyMonitor(Context context, HexNilDatabase database) {
        this.database = database;
    }

    public void checkSafety(BatterySnapshot snapshot) {
        if (snapshot == null) return;

        boolean overTemp = snapshot.getTemperatureCelsius() >= OVER_TEMP_C;
        boolean overVoltage = snapshot.voltage >= OVER_VOLTAGE_MV;
        boolean overCurrent = Math.abs(snapshot.chargeRate) >= OVER_CURRENT_MA;

        if (!overTemp && !overVoltage && !overCurrent) {
            return;
        }

        SafetyEvent event = new SafetyEvent();
        event.timestamp = snapshot.timestamp;
        event.batteryLevel = snapshot.level;
        event.wasCharging = snapshot.isCharging;
        event.chargingType = snapshot.chargingType;

        if (overTemp) {
            event.eventType = "over_temp";
            event.severity = snapshot.getTemperatureCelsius() >= OVER_TEMP_C + 3 ? "critical" : "warning";
            event.batteryTemperature = snapshot.getTemperatureCelsius();
            event.thresholdValue = OVER_TEMP_C;
            event.measurementUnit = "°C";
            event.triggerValue = event.batteryTemperature;
        } else if (overVoltage) {
            event.eventType = "over_voltage";
            event.severity = "warning";
            event.voltage = snapshot.voltage;
            event.expectedVoltage = OVER_VOLTAGE_MV;
            event.thresholdValue = OVER_VOLTAGE_MV;
            event.measurementUnit = "mV";
            event.triggerValue = event.voltage;
        } else if (overCurrent) {
            event.eventType = "over_current";
            event.severity = "warning";
            event.current = snapshot.chargeRate;
            event.expectedCurrent = OVER_CURRENT_MA;
            event.thresholdValue = OVER_CURRENT_MA;
            event.measurementUnit = "mA";
            event.triggerValue = event.current;
        }

        database.safetyEventDao().insert(event);
    }

    public void checkLowBatterySafety() {
        SafetyEvent event = new SafetyEvent();
        event.eventType = "over_discharge";
        event.severity = "warning";
        event.wasCharging = false;
        event.measurementUnit = "%";
        event.triggerValue = 15; // triggered at system low battery
        event.thresholdValue = 15;
        event.batteryLevel = 15;
        database.safetyEventDao().insert(event);
    }

    /**
     * Check for high temperature safety event when critical temperature is detected.
     * @param tempTenths Temperature in tenths of degrees Celsius
     */
    public void checkHighTemperatureSafety(int tempTenths) {
        float tempC = tempTenths / 10.0f;
        if (tempC < OVER_TEMP_C) return;

        SafetyEvent event = new SafetyEvent();
        event.timestamp = System.currentTimeMillis();
        event.eventType = "over_temp";
        event.severity = tempC >= OVER_TEMP_C + 5 ? "critical" : (tempC >= OVER_TEMP_C + 3 ? "high" : "warning");
        event.batteryTemperature = tempC;
        event.thresholdValue = OVER_TEMP_C;
        event.measurementUnit = "°C";
        event.triggerValue = tempC;
        event.wasCharging = false; // Unknown at this point, will be updated if available
        database.safetyEventDao().insert(event);
    }
}
