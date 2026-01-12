package com.example.hexnil.manager;

import com.example.hexnil.database.HexNilDatabase;

/**
 * Small aggregator for OEM dashboard metrics.
 */
public class OEMDashboardData {

    public float averageHealth;
    public float averageScreenOnDrain;
    public float averageScreenOffDrain;
    public float averagePeakTemp;
    public int criticalSafetyEvents;
    public int totalSnapshots;

    public static OEMDashboardData fromDatabase(HexNilDatabase db, long startTime) {
        OEMDashboardData data = new OEMDashboardData();
        data.averageHealth = db.batteryHealthDao().getAverageHealth(startTime);
        data.averageScreenOnDrain = db.drainEventDao().getAverageScreenOnDrain(startTime);
        data.averageScreenOffDrain = db.drainEventDao().getAverageScreenOffDrain(startTime);
        data.averagePeakTemp = db.thermalEventDao().getAveragePeakTemperature(startTime);
        data.criticalSafetyEvents = db.safetyEventDao().getCriticalEventCount();
        data.totalSnapshots = db.batterySnapshotDao().getCountSince(startTime);
        return data;
    }
}
